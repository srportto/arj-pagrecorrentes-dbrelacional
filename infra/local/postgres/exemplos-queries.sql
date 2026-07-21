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
	where id_autorizacao = '019e5b93-b07d-7d8f-9677-8573de470006';

-----------------------------------------------------------------------------
--- validando particao que caiu o select
-----------------------------------------------------------------------------
	EXPLAIN 
	select * 
	from autorizacoes 
	where id_autorizacao = '019e5c90-a97c-7832-8574-464c3bf80006';

-----------------------------------------------------------------------------
--- select com particao
-----------------------------------------------------------------------------
	EXPLAIN 
	select * 
	from autorizacoes 
	where id_autorizacao = '019ec233-2077-7578-b68d-2250859b0006'
	and id_particao_conta = 942;


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
    FOR VALUES IN (999);