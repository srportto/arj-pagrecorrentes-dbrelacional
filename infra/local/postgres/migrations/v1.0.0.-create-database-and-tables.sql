-----------------------------------------------------------------------------
--- Criacao das extensions necessarias (pg_cron, pg_partman e pgvector)
-----------------------------------------------------------------------------

CREATE EXTENSION IF NOT EXISTS pg_cron;
CREATE EXTENSION IF NOT EXISTS pg_partman;
CREATE EXTENSION IF NOT EXISTS vector;

-----------------------------------------------------------------------------
--- Criacao da tabela de autorizacao particionada por LISTA
-----------------------------------------------------------------------------

CREATE TABLE autorizacoes (
    id_autorizacao UUID NOT NULL,
    id_particao_conta INT NOT NULL,
	tipo_produto NUMERIC(6,0) NOT NULL,
    tipo_jornada NUMERIC(6,0) NOT NULL DEFAULT 0, -- 0 = jornada desconhecida (linhas anteriores a esta coluna)
    status INT NOT null,
    motivo_status TEXT,
	data_hora_inclusao timestamp  not null,
    data_hora_ultima_atlz timestamp  not null,
    data_inicio_vigencia DATE,
	data_fim_vigencia DATE NOT NULL,
    valor NUMERIC(17, 2), 
    id_autorizacao_empresa TEXT,
    valor_limite NUMERIC(17, 2),
    frequencia INT CHECK (frequencia IN (1, 2, 3, 4)),
    quantidade_dividas_ciclo INT,
    indicador_uso_limite_conta INT,
    indicador_tipo_mensageria INT,
    codigo_canal_contratacao TEXT NOT NULL,
    descricao TEXT,
    id_unico_conta_contratante UUID,
    id_pessoa_pagadora UUID,
    id_pessoa_devedora UUID,
    id_pessoa_recebedora UUID,    
    codigo_canal_cancelamento TEXT,
    id_pessoa_cancelamento UUID,
    data_hora_cancelamento timestamp,
    motivo_cancelamento TEXT,    
    metadados JSON,    
    -- A PK precisa conter a coluna de particionamento
    CONSTRAINT pk_autorizacoees PRIMARY KEY (id_autorizacao, id_particao_conta)
   ) PARTITION BY LIST (id_particao_conta);
