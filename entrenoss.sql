-- ==========================================================
-- BANCO DE DADOS UNIFICADO - SISTEMA ENTRE NÓS (SQL SERVER)
-- ==========================================================

IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = N'entre_nos')
BEGIN
    CREATE DATABASE entre_nos;
END;
GO

USE entre_nos;
GO

-- 1. Tabela de Psicólogos
CREATE TABLE psicologos (
    id_psicologo INT IDENTITY(1,1) PRIMARY KEY,
    codigo_ref NVARCHAR(20) NOT NULL UNIQUE,
    nome NVARCHAR(120) NOT NULL,
    crp NVARCHAR(20) NOT NULL UNIQUE,
    cidade_polo NVARCHAR(80) NOT NULL,
    estilo_atendimento NVARCHAR(100) NOT NULL,
    descricao NVARCHAR(MAX),
    fila_atual INT NOT NULL DEFAULT 0,
    tempo_medio NVARCHAR(60) DEFAULT '1 a 2 dias',
    ativo BIT DEFAULT 1,
    criado_em DATETIME2 DEFAULT GETDATE()
);

-- 2. Tabela de Estudantes
CREATE TABLE estudantes (
    id_estudante INT IDENTITY(1,1) PRIMARY KEY,
    login_matricula NVARCHAR(100) NOT NULL UNIQUE,
    senha_hash NVARCHAR(255) NOT NULL,
    nome_social NVARCHAR(120) NOT NULL,
    idade INT CHECK (idade BETWEEN 10 AND 25),
    turma NVARCHAR(50),
    contato_lembrete NVARCHAR(120),
    criado_em DATETIME2 DEFAULT GETDATE()
);

-- 3. Tabela de Agendamentos e Fila por Gravidade
CREATE TABLE agendamentos (
    id_agendamento INT IDENTITY(1,1) PRIMARY KEY,
    protocolo NVARCHAR(20) NOT NULL UNIQUE,
    id_estudante INT NOT NULL,
    id_psicologo INT NOT NULL,
    tema_principal NVARCHAR(100) NOT NULL,
    nivel_urgencia NVARCHAR(20) NOT NULL DEFAULT 'Leve'
        CHECK (nivel_urgencia IN ('Alta', 'Moderada', 'Leve')),
    prioridade_numero TINYINT NOT NULL DEFAULT 3,
    pref_escuta NVARCHAR(120),
    pref_ritmo NVARCHAR(120),
    canal_lembrete NVARCHAR(80) DEFAULT 'E-mail',
    motivo_breve NVARCHAR(MAX),
    status_fila NVARCHAR(20) DEFAULT 'Na fila'
        CHECK (status_fila IN ('Na fila', 'Confirmado', 'Concluido', 'Cancelado')),
    criado_em DATETIME2 DEFAULT GETDATE(),
    CONSTRAINT fk_agendamento_estudante FOREIGN KEY (id_estudante)
        REFERENCES estudantes(id_estudante) ON DELETE CASCADE,
    CONSTRAINT fk_agendamento_psicologo FOREIGN KEY (id_psicologo)
        REFERENCES psicologos(id_psicologo)
);

-- 4. Tabela de Chat com o Psicólogo
CREATE TABLE mensagens_chat (
    id_mensagem INT IDENTITY(1,1) PRIMARY KEY,
    id_estudante INT NOT NULL,
    id_psicologo INT NOT NULL,
    remetente NVARCHAR(20) NOT NULL
        CHECK (remetente IN ('Estudante', 'Psicologo')),
    mensagem NVARCHAR(MAX) NOT NULL,
    lida BIT DEFAULT 0,
    enviado_em DATETIME2 DEFAULT GETDATE(),
    CONSTRAINT fk_chat_estudante FOREIGN KEY (id_estudante)
        REFERENCES estudantes(id_estudante) ON DELETE CASCADE,
    CONSTRAINT fk_chat_psicologo FOREIGN KEY (id_psicologo)
        REFERENCES psicologos(id_psicologo)
);

-- 5. Tabela de Canal Anônimo e Denúncias
CREATE TABLE denuncias_e_duvidas_anonimas (
    id_registro INT IDENTITY(1,1) PRIMARY KEY,
    protocolo NVARCHAR(20) NOT NULL UNIQUE,
    tipo_registro NVARCHAR(30) NOT NULL
        CHECK (tipo_registro IN ('Duvida Anonima', 'Denuncia')),
    categoria NVARCHAR(30) NOT NULL
        CHECK (categoria IN ('Bullying', 'Assedio', 'Violencia', 'Duvida Geral', 'Outro')),
    local_ocorrido NVARCHAR(120),
    relato_ou_pergunta NVARCHAR(MAX) NOT NULL,
    resposta_equipe NVARCHAR(MAX),
    status_registro NVARCHAR(20) DEFAULT 'Pendente'
        CHECK (status_registro IN ('Pendente', 'Em analise', 'Respondido')),
    criado_em DATETIME2 DEFAULT GETDATE()
);
GO

-- Dados iniciais dos psicólogos
INSERT INTO psicologos (codigo_ref, nome, crp, cidade_polo, estilo_atendimento, descricao, fila_atual, tempo_medio) VALUES
('psi-1', N'Dra. Camila Ferreira', 'CRP 08/24190', N'Curitiba', N'Acolhedor e calmo', N'Foco em escuta ativa sem pressa, ideal para quem prefere ir devagar nas primeiras conversas.', 2, '2 a 3 dias'),
('psi-2', N'Dr. Rafael Mendes', 'CRP 08/31045', N'Curitiba', N'Objetivo e orientador', N'Ajuda a organizar rotinas de estudo, ansiedade com provas e tomada de decisões.', 1, '1 a 2 dias'),
('psi-3', N'Dra. Juliana Costa', 'CRP 08/19872', N'Londrina', N'Empático e dialogado', N'Experiência com conflitos familiares, autoestima e adaptação escolar.', 3, '3 a 4 dias'),
('psi-4', N'Dr. Lucas Andrade', 'CRP 08/28511', N'Maringá', N'Reservado e seguro', N'Ambiente tranquilo com perguntas guiadas para estudantes que têm dificuldade em começar a falar.', 1, '1 a 2 dias'),
('psi-5', N'Dra. Beatriz Nogueira', 'CRP 08/22304', N'Cascavel', N'Acolhedor e prático', N'Apoio em situações de bullying, convivência escolar e regulação emocional.', 2, '2 dias'),
('psi-6', N'Dr. Tiago Ribeiro', 'CRP 08/33412', N'Ponta Grossa', N'Calmo e estruturado', N'Trabalho focado em ansiedade, pressão escolar e fortalecimento emocional.', 0, 'Atendimento na semana');
GO

-- Consulta de fila ordenada por gravidade
SELECT 
    a.protocolo,
    e.nome_social AS estudante,
    e.turma,
    p.nome AS psicologo,
    p.cidade_polo,
    a.nivel_urgencia,
    a.tema_principal,
    a.pref_escuta,
    a.pref_ritmo,
    a.criado_em
FROM agendamentos a
INNER JOIN estudantes e ON a.id_estudante = e.id_estudante
INNER JOIN psicologos p ON a.id_psicologo = p.id_psicologo
WHERE a.status_fila = 'Na fila'
ORDER BY a.prioridade_numero ASC, a.criado_em ASC;
GO