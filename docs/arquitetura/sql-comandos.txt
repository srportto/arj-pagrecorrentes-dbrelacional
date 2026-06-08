
-----------------------------------------------------------------------------
--- Criacao da tabela de autorizacao particionada por LISTA
-----------------------------------------------------------------------------

CREATE TABLE autorizacoes (
    id_autorizacao UUID NOT NULL,
    id_particao_conta INT NOT NULL,
    data_fim_vigencia DATE NOT NULL, -- Coluna de partição deve ser NOT NULL
    status INT NOT null,
    motivo_status TEXT, --- pendente
    data_inicio_vigencia DATE,
    data_hora_inclusao timestamp  not null,
    data_hora_ultima_atlz timestamp  not null,
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

-----------------------------------------------------------------------------
--- Criacao das particoes de dados quentes 
-----------------------------------------------------------------------------

DO $$
DECLARE
    i INT;
BEGIN
    FOR i IN 0..888 LOOP
        EXECUTE format(
            'CREATE TABLE autorizacoes_pa%s PARTITION OF public.autorizacoes FOR VALUES IN (%s);',
            i, i
        );
    END LOOP;
END $$;

-----------------------------------------------------------------------------
--- Criacao das particoes de dados para exprugo
-----------------------------------------------------------------------------

DO $$
DECLARE
    i INT;
BEGIN
    FOR i IN 900..999 LOOP
        EXECUTE format(
            'CREATE TABLE autorizacoes_pe%s PARTITION OF public.autorizacoes FOR VALUES IN (%s);',
            i, i
        );
    END LOOP;
END $$;


-----------------------------------------------------------------------------
--- Listar particoes todas as particoes 
-----------------------------------------------------------------------------
	---------------------------------------------------------------------------
	--- Ver onde comeca e onde termina as particoes que podem receber dados
	---------------------------------------------------------------------------

	SELECT
	    parent.relname AS tabela_pai,
	    child.relname AS nome_da_particao,
	    pg_get_expr(child.relpartbound, child.oid) AS limites_da_particao,
	    child.*,
	    parent.*
	FROM pg_inherits
	JOIN pg_class parent ON pg_inherits.inhparent = parent.oid
	JOIN pg_class child ON pg_inherits.inhrelid = child.oid
	WHERE parent.relname = 'autorizacoes';

	
-----------------------------------------------------------------------------
--- Select de dados
-----------------------------------------------------------------------------
	select * 
	from autorizacoes 
	where id_autorizacao = '019da240-3ee2-7e1a-81da-90f103ed0006';

-----------------------------------------------------------------------------
--- validando particao que caiu o select
-----------------------------------------------------------------------------
	EXPLAIN select * 
	from autorizacoes 
	where id_autorizacao = '019da240-3ee2-7e1a-81da-90f103ed0006';

-----------------------------------------------------------------------------
--- select com particao
-----------------------------------------------------------------------------
	EXPLAIN 
	select * 
	from autorizacoes 
	where id_autorizacao = '019db1c5-32b8-7adb-8b98-0a68817d0006'
	and id_particao_conta = 999;


	select *
	from autorizacoes 
	where id_autorizacao = '019da259-3572-749a-8139-1cdef2740006'
	and id_particao_conta = 910;
	
	select * 
	from autorizacoes ;
	
	
-----------------------------------------------------------------------------
--- Expurgo de particao - em 3 passos
-----------------------------------------------------------------------------	
	
-- PASSO 1: Desanexar a partição concorrentemente
-- (Disponível a partir do PostgreSQL 14. Isso NÃO trava as transações da tabela principal)
ALTER TABLE autorizacoes 
    DETACH PARTITION autorizacoes_pe999 CONCURRENTLY;

-- PASSO 2: Dropar a tabela que agora está isolada
-- (É neste momento que o espaço em disco é efetivamente liberado para o SO)
DROP TABLE autorizacoes_pe999;

-- PASSO 3: Recriar a partição vazia e já anexada à tabela principal
-- (Pronta para receber dados quando o Ring Buffer der a volta e chegar no 900 novamente)
CREATE TABLE autorizacoes_pe999
    PARTITION OF autorizacoes
    FOR VALUES IN (999;




