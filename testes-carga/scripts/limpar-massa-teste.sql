-----------------------------------------------------------------------------
--- Limpeza da massa de teste de carga (change testes-de-carga-tps, design.md D6)
---
--- Remove toda autorizacao cujo id_autorizacao_empresa comeca com o prefixo LOADTEST- --
--- convencao usada por todos os cenarios Gatling (ver carga.support.MassaTeste). O DELETE
--- entra pela tabela-pai `autorizacoes`, entao o Postgres localiza as linhas em QUALQUER
--- particao -- tanto as quentes (0-888, onde a autorizacao nasce) quanto a faixa de expurgo
--- (900-999, para onde uma autorizacao em estado terminal pode ter sido movida durante o
--- proprio teste). Funciona independente de o cenario ter terminado por conclusao normal,
--- abort de kill switch, ou crash -- nao depende de nenhum passo de cleanup do lado do
--- Gatling (tasks.md 3.3).
---
--- Uso:
---   docker exec -i postgres18-kiq psql -U docker -d db-csp-postgres \
---     < testes-carga/scripts/limpar-massa-teste.sql
-----------------------------------------------------------------------------

DO $$
DECLARE
    qtd_removida BIGINT;
BEGIN
    DELETE FROM autorizacoes WHERE id_autorizacao_empresa LIKE 'LOADTEST-%';
    GET DIAGNOSTICS qtd_removida = ROW_COUNT;
    RAISE NOTICE 'Massa de teste de carga removida: % linha(s) (prefixo LOADTEST-)', qtd_removida;
END $$;
