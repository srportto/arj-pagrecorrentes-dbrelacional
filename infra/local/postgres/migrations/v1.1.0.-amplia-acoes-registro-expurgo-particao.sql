-----------------------------------------------------------------------------
--- Amplia o CHECK de `acao` em expurgo_particao_registro
---
--- Motivacao (change `elevar-qualidade-codigo-expurgo-particao`, design.md D2/D3):
---
--- A v1.0.7 fechou `acao` em quatro valores. Duas situacoes reais nao cabiam em
--- nenhum deles e por isso nao deixavam rastro algum:
---
---   FALHA             -- erro nao previsto durante a execucao (tabela de particao
---                        inexistente, permissao insuficiente, conexao morta). Antes,
---                        a excecao propagava e a execucao terminava SEM registro.
---                        Como a ausencia de registro e' o proprio sinal de "rotina
---                        parada" (ver v1.0.10, job CONFIRMACAO_REGISTRO), uma rotina
---                        que falha a cada ciclo era indistinguivel de uma rotina que
---                        nao esta sendo invocada.
---
---   RECUSA_DESARMADO  -- o interruptor EXPURGO_PARTICAO_DESARMAR_TRUNCATE impediu o
---                        esvaziamento. Antes gravava NENHUMA, obrigando o auditor a
---                        deduzir o desarme do cruzamento entre `estado` e `acao` em
---                        vez de simplesmente ler.
---
--- NAO altera a v1.0.7 (ja aplicada): substitui o CHECK por um mais amplo. Nenhuma
--- linha existente e' invalidada -- os quatro valores originais continuam aceitos.
---
--- O job pg_cron da v1.0.10 nao le a coluna `acao` (so `executado_em` e
--- `particao_alvo`), entao nao precisa acompanhar esta mudanca.
---
--- NUMERACAO: v1.1.0, nao v1.0.11. O entrypoint do Postgres executa
--- /docker-entrypoint-initdb.d em ordem ALFABETICA, e 'v1.0.11' ordenaria logo apos
--- 'v1.0.1' -- ou seja, ANTES da v1.0.7 que cria a tabela alterada aqui. 'v1.1.0'
--- ordena depois de 'v1.0.9'. (A v1.0.10 ja sofre desse problema hoje: ela roda em
--- 3o lugar, antes da v1.0.7/v1.0.8 que criam as tabelas que ela referencia.)
-----------------------------------------------------------------------------

-- Natureza do erro, quando acao = FALHA. NULL em todas as demais acoes -- a spec
-- exige que o registro identifique o erro encontrado, e nenhuma coluna existente
-- comporta esse texto. Guarda a CLASSE e a mensagem do erro, nunca dado de linha
-- de `autorizacoes`.
ALTER TABLE expurgo_particao_registro
    ADD COLUMN IF NOT EXISTS detalhe TEXT;

ALTER TABLE expurgo_particao_registro
    DROP CONSTRAINT expurgo_particao_registro_acao_check;

ALTER TABLE expurgo_particao_registro
    ADD CONSTRAINT expurgo_particao_registro_acao_check CHECK (
        acao IN (
            'NENHUMA',
            'TRUNCATE',
            'RECUSA_DADO_RECENTE',
            'RECUSA_LOCK_TIMEOUT',
            'FALHA',
            'RECUSA_DESARMADO'
        )
    );

-----------------------------------------------------------------------------
--- REVERSAO
-----------------------------------------------------------------------------
--- Só e' reversivel se nao houver linha com os dois valores novos; apague-as antes:
---   DELETE FROM expurgo_particao_registro WHERE acao IN ('FALHA', 'RECUSA_DESARMADO');
---   ALTER TABLE expurgo_particao_registro DROP COLUMN IF EXISTS detalhe;
---   ALTER TABLE expurgo_particao_registro
---       DROP CONSTRAINT expurgo_particao_registro_acao_check;
---   ALTER TABLE expurgo_particao_registro
---       ADD CONSTRAINT expurgo_particao_registro_acao_check CHECK (
---           acao IN ('NENHUMA', 'TRUNCATE', 'RECUSA_DADO_RECENTE', 'RECUSA_LOCK_TIMEOUT')
---       );
-----------------------------------------------------------------------------
