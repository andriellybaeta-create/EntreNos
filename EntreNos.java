import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntreNos {

    static class Consulta implements Comparable<Consulta> {
        String aluno;
        String turma;
        int nivelGravidade; // 1 = Alta, 2 = Moderada, 3 = Leve
        String nivelNome;
        String descricao;

        Consulta(String aluno, String turma, String nivelNome, String descricao) {
            this.aluno = aluno;
            this.turma = turma;
            this.nivelNome = nivelNome;
            this.descricao = descricao;

            if ("Alta".equalsIgnoreCase(nivelNome)) {
                this.nivelGravidade = 1;
            } else if ("Moderada".equalsIgnoreCase(nivelNome)) {
                this.nivelGravidade = 2;
            } else {
                this.nivelGravidade = 3;
            }
        }

        @Override
        public int compareTo(Consulta outra) {
            return Integer.compare(this.nivelGravidade, outra.nivelGravidade);
        }
    }

    private static final List<Consulta> filaAtendimento =
            Collections.synchronizedList(new ArrayList<Consulta>());

    private static final List<String> listaDenuncias =
            Collections.synchronizedList(new ArrayList<String>());

    private static final List<String> mensagensChat =
            Collections.synchronizedList(new ArrayList<String>());

    public static void main(String[] args) throws IOException {
        int porta = 8080;
        ServerSocket servidorTemp = null;

        while (servidorTemp == null) {
            try {
                servidorTemp = new ServerSocket(porta);
            } catch (BindException e) {
                porta++;
            }
        }

        try (ServerSocket servidor = servidorTemp) {
            System.out.println("==========================================");
            System.out.println("  ENTRE NOS - SERVIDOR JAVA ATIVO");
            System.out.println("  Abra no navegador: http://localhost:" + porta);
            System.out.println("==========================================");

            while (true) {
                final Socket conexao = servidor.accept();
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        processarConexao(conexao);
                    }
                });
                thread.start();
            }
        }
    }

    private static void processarConexao(Socket conexao) {
        try (Socket socket = conexao;
             InputStream entrada = socket.getInputStream();
             OutputStream saida = socket.getOutputStream()) {

            String linhaInicial = lerLinhaHttp(entrada);
            if (linhaInicial == null || linhaInicial.trim().isEmpty()) {
                return;
            }

            String[] tokens = linhaInicial.split(" ");
            String metodo = tokens.length > 0 ? tokens[0] : "GET";
            String rota = tokens.length > 1 ? tokens[1] : "/";

            int tamanhoCorpo = 0;
            String cabecalho;
            while ((cabecalho = lerLinhaHttp(entrada)) != null && !cabecalho.isEmpty()) {
                if (cabecalho.toLowerCase().startsWith("content-length:")) {
                    tamanhoCorpo = Integer.parseInt(cabecalho.substring(15).trim());
                }
            }

            String corpoRequisicao = "";
            if (tamanhoCorpo > 0) {
                byte[] buffer = new byte[tamanhoCorpo];
                int lidos = 0;
                while (lidos < tamanhoCorpo) {
                    int r = entrada.read(buffer, lidos, tamanhoCorpo - lidos);
                    if (r == -1) {
                        break;
                    }
                    lidos += r;
                }
                corpoRequisicao = new String(buffer, 0, lidos, StandardCharsets.UTF_8);
            }

            if ("/".equals(rota) && "GET".equalsIgnoreCase(metodo)) {
                enviarHttp(saida, 200, montarPaginaWeb());
            } else if ("/api/agendar".equals(rota) && "POST".equalsIgnoreCase(metodo)) {
                enviarHttp(saida, 200, registrarConsulta(corpoRequisicao));
            } else if ("/api/chat".equals(rota) && "POST".equalsIgnoreCase(metodo)) {
                enviarHttp(saida, 200, responderChat(corpoRequisicao));
            } else if ("/api/denuncia".equals(rota) && "POST".equalsIgnoreCase(metodo)) {
                enviarHttp(saida, 200, registrarDenuncia(corpoRequisicao));
            } else {
                enviarHttp(saida, 200, "");
            }

        } catch (Exception e) {
            System.out.println("Aviso na requisicao: " + e.getMessage());
        }
    }

    private static String registrarConsulta(String corpo) throws IOException {
        Map<String, String> dados = extrairFormulario(corpo);
        String aluno = dados.getOrDefault("aluno", "Estudante");
        String turma = dados.getOrDefault("turma", "Nao informada");
        String gravidade = dados.getOrDefault("gravidade", "Leve");
        String descricao = dados.getOrDefault("descricao", "");

        Consulta nova = new Consulta(aluno, turma, gravidade, descricao);
        filaAtendimento.add(nova);

        StringBuilder resposta = new StringBuilder();
        synchronized (filaAtendimento) {
            Collections.sort(filaAtendimento);
            resposta.append("Agendamento confirmado! Fila organizada por gravidade: ");
            for (int i = 0; i < filaAtendimento.size(); i++) {
                Consulta c = filaAtendimento.get(i);
                resposta.append(i + 1)
                        .append("\u00ba ")
                        .append(c.aluno)
                        .append(" (")
                        .append(c.turma)
                        .append(" - Prioridade ")
                        .append(c.nivelNome)
                        .append(")");
                if (i < filaAtendimento.size() - 1) {
                    resposta.append(" | ");
                }
            }
        }
        return resposta.toString();
    }

    private static String responderChat(String corpo) throws IOException {
        Map<String, String> dados = extrairFormulario(corpo);
        String msg = dados.getOrDefault("mensagem", "").trim();
        mensagensChat.add("Aluno: " + msg);
        System.out.println("[Chat Escolar] " + msg);

        String textoMinusculo = msg.toLowerCase();
        if (textoMinusculo.contains("ansiedade") || textoMinusculo.contains("ansioso") || textoMinusculo.contains("nervoso")) {
            return "Respire fundo, voc\u00ea est\u00e1 em um lugar seguro. Se quiser, marque prioridade Alta ao lado para conversarmos hoje.";
        } else if (textoMinusculo.contains("bullying") || textoMinusculo.contains("medo") || textoMinusculo.contains("amea")) {
            return "Sinto muito que esteja passando por isso. Voc\u00ea pode registrar os detalhes no question\u00e1rio de den\u00fancia com total sigilo.";
        }
        return "Recebi sua mensagem! Estou aqui para te ouvir sem julgamentos. Em instantes continuo o atendimento.";
    }

    private static String registrarDenuncia(String corpo) throws IOException {
        Map<String, String> dados = extrairFormulario(corpo);
        String categoria = dados.getOrDefault("categoria", "Geral");
        String local = dados.getOrDefault("local", "Nao informado");
        String detalhes = dados.getOrDefault("detalhes", "");

        listaDenuncias.add("Categoria: " + categoria + " | Local: " + local + " | Relato: " + detalhes);
        int protocolo = 1000 + listaDenuncias.size();
        System.out.println("[Denuncia Sigilosa #" + protocolo + "] " + categoria);

        return "Den\u00fancia protegida enviada com sucesso! Protocolo sigiloso: #EN-" + protocolo;
    }

    private static Map<String, String> extrairFormulario(String corpo) throws IOException {
        Map<String, String> mapa = new HashMap<String, String>();
        if (corpo == null || corpo.isEmpty()) {
            return mapa;
        }
        String[] pares = corpo.split("&");
        for (String par : pares) {
            String[] chaveValor = par.split("=", 2);
            if (chaveValor.length == 2) {
                String chave = URLDecoder.decode(chaveValor[0], "UTF-8");
                String valor = URLDecoder.decode(chaveValor[1], "UTF-8");
                mapa.put(chave, valor);
            }
        }
        return mapa;
    }

    private static String lerLinhaHttp(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int byteLido;
        while ((byteLido = in.read()) != -1) {
            if (byteLido == '\n') {
                break;
            }
            if (byteLido != '\r') {
                buffer.write(byteLido);
            }
        }
        if (byteLido == -1 && buffer.size() == 0) {
            return null;
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void enviarHttp(OutputStream out, int codigo, String conteudo) throws IOException {
        byte[] dados = conteudo.getBytes(StandardCharsets.UTF_8);
        String cabecalho =
                "HTTP/1.1 " + codigo + " OK\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: " + dados.length + "\r\n" +
                "Connection: close\r\n\r\n";

        out.write(cabecalho.getBytes(StandardCharsets.UTF_8));
        out.write(dados);
        out.flush();
    }

    private static String montarPaginaWeb() {
        return
            "<!DOCTYPE html>\n" +
            "<html lang=\"pt-BR\">\n" +
            "<head>\n" +
            "  <meta charset=\"UTF-8\">\n" +
            "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
            "  <title>Entre N&oacute;s &bull; Apoio Psicol&oacute;gico Escolar</title>\n" +
            "  <style>\n" +
            "    :root {\n" +
            "      --azul-suave: #e0f7fa;\n" +
            "      --azul-claro: #b2ebf2;\n" +
            "      --ciano: #00acc1;\n" +
            "      --ciano-escuro: #006064;\n" +
            "      --fundo: #f2fbfc;\n" +
            "      --branco: #ffffff;\n" +
            "      --texto: #102a43;\n" +
            "      --texto-sec: #486581;\n" +
            "      --borda: #80deea;\n" +
            "    }\n" +
            "    * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Segoe UI', system-ui, sans-serif; }\n" +
            "    body { background-color: var(--fundo); color: var(--texto); line-height: 1.6; }\n" +
            "    header {\n" +
            "      background: linear-gradient(120deg, #4dd0e1, var(--ciano));\n" +
            "      color: var(--branco); padding: 1.2rem 5%;\n" +
            "      display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1rem;\n" +
            "      box-shadow: 0 2px 8px rgba(0, 172, 193, 0.25);\n" +
            "    }\n" +
            "    header h1 { font-size: 1.6rem; }\n" +
            "    nav { display: flex; gap: 0.8rem; flex-wrap: wrap; }\n" +
            "    nav a {\n" +
            "      color: var(--branco); text-decoration: none; font-weight: 600;\n" +
            "      padding: 0.45rem 0.9rem; border-radius: 20px; background: rgba(255,255,255,0.15);\n" +
            "    }\n" +
            "    nav a:hover { background: rgba(255,255,255,0.3); }\n" +
            "    .banner {\n" +
            "      background-color: var(--azul-suave); border-bottom: 2px solid var(--azul-claro);\n" +
            "      text-align: center; padding: 2.5rem 1.2rem;\n" +
            "    }\n" +
            "    .banner h2 { color: var(--ciano-escuro); font-size: clamp(1.4rem, 4vw, 2.1rem); margin-bottom: 0.6rem; }\n" +
            "    .banner p { max-width: 650px; margin: 0 auto 1.3rem; color: var(--texto-sec); }\n" +
            "    .botao {\n" +
            "      display: inline-block; background-color: var(--ciano); color: var(--branco);\n" +
            "      padding: 0.75rem 1.5rem; border-radius: 25px; border: none; font-weight: bold;\n" +
            "      text-decoration: none; cursor: pointer; font-size: 0.98rem;\n" +
            "    }\n" +
            "    .botao:hover { background-color: var(--ciano-escuro); }\n" +
            "    .grade {\n" +
            "      max-width: 1120px; margin: 2rem auto; padding: 0 1.2rem;\n" +
            "      display: grid; grid-template-columns: repeat(auto-fit, minmax(290px, 1fr)); gap: 1.5rem;\n" +
            "    }\n" +
            "    .painel {\n" +
            "      background: var(--branco); border: 1px solid var(--borda); border-radius: 16px;\n" +
            "      padding: 1.5rem; box-shadow: 0 4px 12px rgba(0, 96, 100, 0.06);\n" +
            "      display: flex; flex-direction: column; justify-content: space-between;\n" +
            "    }\n" +
            "    .painel h3 { color: var(--ciano-escuro); margin-bottom: 0.4rem; }\n" +
            "    .subtexto { color: var(--texto-sec); font-size: 0.9rem; margin-bottom: 1rem; }\n" +
            "    label { display: block; font-weight: 600; font-size: 0.88rem; margin-bottom: 0.3rem; }\n" +
            "    input, select, textarea {\n" +
            "      width: 100%; padding: 0.7rem; margin-bottom: 0.9rem;\n" +
            "      border: 1px solid var(--borda); border-radius: 10px; background: #f8feff; font-size: 0.95rem;\n" +
            "    }\n" +
            "    .janela-chat {\n" +
            "      background: var(--fundo); border: 1px solid var(--borda); border-radius: 12px;\n" +
            "      height: 210px; overflow-y: auto; padding: 0.8rem; margin-bottom: 0.8rem;\n" +
            "      display: flex; flex-direction: column; gap: 0.6rem;\n" +
            "    }\n" +
            "    .balao { padding: 0.6rem 0.9rem; border-radius: 14px; max-width: 85%; font-size: 0.9rem; overflow-wrap: anywhere; }\n" +
            "    .balao-psi { background: var(--azul-claro); color: var(--texto); align-self: flex-start; }\n" +
            "    .balao-alu { background: var(--ciano); color: var(--branco); align-self: flex-end; }\n" +
            "    .linha-chat { display: flex; gap: 0.5rem; }\n" +
            "    .linha-chat input { margin-bottom: 0; }\n" +
            "    .status { margin-top: 0.8rem; font-size: 0.88rem; font-weight: 600; color: var(--ciano-escuro); overflow-wrap: anywhere; }\n" +
            "    footer { text-align: center; padding: 1.4rem; margin-top: 2rem; background: var(--azul-suave); color: var(--ciano-escuro); font-size: 0.9rem; }\n" +
            "    @media (max-width: 600px) {\n" +
            "      header { flex-direction: column; text-align: center; }\n" +
            "      nav { justify-content: center; }\n" +
            "      .grade { grid-template-columns: 1fr; }\n" +
            "    }\n" +
            "  </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "  <header>\n" +
            "    <h1>&#129309; Entre N&oacute;s</h1>\n" +
            "    <nav>\n" +
            "      <a href=\"#agendar\">Agendar Consulta</a>\n" +
            "      <a href=\"#chat\">Chat Psic&oacute;logo</a>\n" +
            "      <a href=\"#denuncia\">Den&uacute;ncia Sigilosa</a>\n" +
            "    </nav>\n" +
            "  </header>\n" +
            "  <section class=\"banner\">\n" +
            "    <h2>Pedir ajuda &eacute; um ato de coragem.</h2>\n" +
            "    <p>O <strong>Entre N&oacute;s</strong> conecta voc&ecirc; ao psic&oacute;logo da escola com acolhimento, fila organizada por prioridade, chat direto e canal seguro de den&uacute;ncias.</p>\n" +
            "    <a href=\"#agendar\" class=\"botao\">Agendar agora</a>\n" +
            "  </section>\n" +
            "  <main class=\"grade\">\n" +
            "    <section class=\"painel\" id=\"agendar\">\n" +
            "      <div>\n" +
            "        <h3>&#128197; Agendamento &amp; Fila por Gravidade</h3>\n" +
            "        <p class=\"subtexto\">Casos urgentes s&atilde;o priorizados automaticamente na fila.</p>\n" +
            "        <form onsubmit=\"mandarConsulta(event)\">\n" +
            "          <label>Seu nome (ou apelido)</label>\n" +
            "          <input type=\"text\" id=\"aluno\" placeholder=\"Ex: Mariana\" required>\n" +
            "          <label>S&eacute;rie / Turma</label>\n" +
            "          <input type=\"text\" id=\"turma\" placeholder=\"Ex: 2o Ano B\" required>\n" +
            "          <label>Gravidade / Urg&ecirc;ncia do momento</label>\n" +
            "          <select id=\"gravidade\">\n" +
            "            <option value=\"Leve\">Leve - Quero conversar e me orientar</option>\n" +
            "            <option value=\"Moderada\">Moderada - Estou sobrecarregado(a)</option>\n" +
            "            <option value=\"Alta\">Alta - Preciso de acolhimento urgente</option>\n" +
            "          </select>\n" +
            "          <label>Motivo breve (opcional)</label>\n" +
            "          <textarea id=\"descricao\" rows=\"2\" placeholder=\"Escreva se sentir vontade...\"></textarea>\n" +
            "          <button type=\"submit\" class=\"botao\" style=\"width:100%\">Confirmar Agendamento</button>\n" +
            "        </form>\n" +
            "      </div>\n" +
            "      <div class=\"status\" id=\"status-consulta\"></div>\n" +
            "    </section>\n" +
            "    <section class=\"painel\" id=\"chat\">\n" +
            "      <div>\n" +
            "        <h3>&#128172; Chat com o Psic&oacute;logo</h3>\n" +
            "        <p class=\"subtexto\">Comunica&ccedil;&atilde;o direta e acolhedora com o respons&aacute;vel.</p>\n" +
            "        <div class=\"janela-chat\" id=\"janela-chat\">\n" +
            "          <div class=\"balao balao-psi\">Ol&aacute;! Bem-vindo ao Entre N&oacute;s. Como voc&ecirc; est&aacute; se sentindo hoje?</div>\n" +
            "        </div>\n" +
            "      </div>\n" +
            "      <div class=\"linha-chat\">\n" +
            "        <input type=\"text\" id=\"campo-msg\" placeholder=\"Escreva sua mensagem...\" onkeypress=\"if(event.key==='Enter') mandarMensagem()\">\n" +
            "        <button type=\"button\" class=\"botao\" onclick=\"mandarMensagem()\">Enviar</button>\n" +
            "      </div>\n" +
            "    </section>\n" +
            "    <section class=\"painel\" id=\"denuncia\">\n" +
            "      <div>\n" +
            "        <h3>&#128737; Question&aacute;rio de Den&uacute;ncia</h3>\n" +
            "        <p class=\"subtexto\">Espa&ccedil;o seguro para relatar bullying, viol&ecirc;ncia ou riscos.</p>\n" +
            "        <form onsubmit=\"mandarDenuncia(event)\">\n" +
            "          <label>Categoria da situa&ccedil;&atilde;o</label>\n" +
            "          <select id=\"categoria\">\n" +
            "            <option value=\"Bullying\">Bullying ou Cyberbullying</option>\n" +
            "            <option value=\"Assedio\">Ass&eacute;dio ou Discrimina&ccedil;&atilde;o</option>\n" +
            "            <option value=\"Violencia\">Agress&atilde;o F&iacute;sica ou Verbal</option>\n" +
            "            <option value=\"Outro\">Outro Risco na Escola</option>\n" +
            "          </select>\n" +
            "          <label>Onde aconteceu? (opcional)</label>\n" +
            "          <input type=\"text\" id=\"local\" placeholder=\"Ex: P&aacute;tio, corredor, redes sociais...\">\n" +
            "          <label>Relato do ocorrido</label>\n" +
            "          <textarea id=\"detalhes\" rows=\"3\" placeholder=\"Voc&ecirc; n&atilde;o precisa se identificar...\" required></textarea>\n" +
            "          <button type=\"submit\" class=\"botao\" style=\"width:100%\">Enviar Den&uacute;ncia Protegida</button>\n" +
            "        </form>\n" +
            "      </div>\n" +
            "      <div class=\"status\" id=\"status-denuncia\"></div>\n" +
            "    </section>\n" +
            "  </main>\n" +
            "  <footer>Entre N&oacute;s &bull; Plataforma Escolar de Acolhimento Psicol&oacute;gico</footer>\n" +
            "  <script>\n" +
            "    async function mandarConsulta(e) {\n" +
            "      e.preventDefault();\n" +
            "      const corpo = new URLSearchParams({\n" +
            "        aluno: document.getElementById('aluno').value,\n" +
            "        turma: document.getElementById('turma').value,\n" +
            "        gravidade: document.getElementById('gravidade').value,\n" +
            "        descricao: document.getElementById('descricao').value\n" +
            "      });\n" +
            "      const res = await fetch('/api/agendar', { method: 'POST', body: corpo });\n" +
            "      document.getElementById('status-consulta').textContent = await res.text();\n" +
            "      e.target.reset();\n" +
            "    }\n" +
            "    async function mandarMensagem() {\n" +
            "      const campo = document.getElementById('campo-msg');\n" +
            "      const janela = document.getElementById('janela-chat');\n" +
            "      if (!campo.value.trim()) return;\n" +
            "      const balaoAlu = document.createElement('div');\n" +
            "      balaoAlu.className = 'balao balao-alu';\n" +
            "      balaoAlu.textContent = campo.value;\n" +
            "      janela.appendChild(balaoAlu);\n" +
            "      const corpo = new URLSearchParams({ mensagem: campo.value });\n" +
            "      campo.value = '';\n" +
            "      janela.scrollTop = janela.scrollHeight;\n" +
            "      const res = await fetch('/api/chat', { method: 'POST', body: corpo });\n" +
            "      const balaoPsi = document.createElement('div');\n" +
            "      balaoPsi.className = 'balao balao-psi';\n" +
            "      balaoPsi.textContent = await res.text();\n" +
            "      janela.appendChild(balaoPsi);\n" +
            "      janela.scrollTop = janela.scrollHeight;\n" +
            "    }\n" +S
            "    async function mandarDenuncia(e) {\n" +
            "      e.preventDefault();\n" +
            "      const corpo = new URLSearchParams({\n" +
            "        categoria: document.getElementById('categoria').value,\n" +
            "        local: document.getElementById('local').value,\n" +
            "        detalhes: document.getElementById('detalhes').value\n" +
            "      });\n" +
            "      const res = await fetch('/api/denuncia', { method: 'POST', body: corpo });\n" +
            "      document.getElementById('status-denuncia').textContent = await res.text();\n" +
            "      e.target.reset();\n" +
            "    }\n" +
            "  </script>\n" +
            "</body>\n" +
            "</html>";
    }
}