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