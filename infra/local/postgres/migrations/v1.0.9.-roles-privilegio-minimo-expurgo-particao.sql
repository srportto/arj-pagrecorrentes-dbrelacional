-----------------------------------------------------------------------------
--- Roles de privilegio minimo para a reclamacao da particao de expurgo
---
--- Motivacao (change `reclamar-particao-expurgo-ciclo`, design.md D1/D5):
---
--- A Lambda que esvazia a particao alvo NAO precisa (e nao deve) ser dona da tabela
--- `autorizacoes`: `TRUNCATE` e' um privilegio GRANULAVEL no Postgres, distinto de
--- ownership. `expurgo_particao_rotina` so sabe SELECT (para verificar estado) e
--- TRUNCATE (nas 100 particoes de expurgo, nunca nas quentes nem na tabela-pai).
---
--- Os jobs `pg_cron` de auditoria (v1.0.9) precisam de MENOS ainda: so leem e gravam o
--- proprio registro de auditoria. `expurgo_particao_auditoria` nao tem NENHUM privilegio
--- de escrita sobre `autorizacoes` -- por design, ela e' quem confere, nunca quem apaga
--- (ver design.md D5: "pg_cron como caixa-preta, nao como segundo expurgador").
---
--- IMPORTANTE -- esta migration exige uma variavel de sessao do psql (nao tem valor
--- default: falha explicita e' preferivel a senha fraca por omissao, mesmo padrao de
--- `gestao-de-segredos`):
---
---   docker exec -i postgres18-kiq psql -U docker -d db-csp-postgres \
---     -v senha_expurgo_particao_rotina=<defina-uma-senha-local> \
---     < v1.0.8.-roles-privilegio-minimo-expurgo-particao.sql
-----------------------------------------------------------------------------

-----------------------------------------------------------------------------
--- 1. Role da rotina (usada pela conexao da Lambda) -- LOGIN, privilegio minimo
-----------------------------------------------------------------------------

CREATE ROLE expurgo_particao_rotina LOGIN PASSWORD :'senha_expurgo_particao_rotina';

GRANT CONNECT ON DATABASE "db-csp-postgres" TO expurgo_particao_rotina;
GRANT USAGE ON SCHEMA public TO expurgo_particao_rotina;

-- GRANT SELECT na tabela-pai nao basta: persistencia.py
-- (max_data_hora_ultima_atlz) consulta a particao pelo nome direto
-- (`SELECT ... FROM autorizacoes_pe<n>`), nao atraves da tabela-pai -- o Postgres so
-- roteia herdando privilegio quando a consulta entra pela tabela-pai. Por isso SELECT
-- e' concedido particao a particao (900..999), igual ao TRUNCATE abaixo -- nunca na
-- tabela-pai isoladamente, nem nas particoes quentes (0..888). Confirmado por falha real
-- em execucao local (permission denied for table autorizacoes_pe957) antes desta correcao.
GRANT SELECT ON autorizacoes TO expurgo_particao_rotina;

DO $$
DECLARE
    i INT;
BEGIN
    FOR i IN 900..999 LOOP
        EXECUTE format('GRANT SELECT ON autorizacoes_pe%s TO expurgo_particao_rotina;', i);
        EXECUTE format('GRANT TRUNCATE ON autorizacoes_pe%s TO expurgo_particao_rotina;', i);
    END LOOP;
END $$;

GRANT SELECT, INSERT ON expurgo_particao_registro TO expurgo_particao_rotina;
GRANT USAGE, SELECT ON SEQUENCE expurgo_particao_registro_id_seq TO expurgo_particao_rotina;

-----------------------------------------------------------------------------
--- 2. Role de auditoria (usada pelos jobs pg_cron, v1.0.10)
---
--- `pg_cron` (>= 1.5) EXIGE que o role de execucao tenha o atributo LOGIN, mesmo rodando
--- via background worker interno -- "Jobs may only be run by roles that have the LOGIN
--- attribute" (confirmado empiricamente ao aplicar esta migration; NOLOGIN nao funciona,
--- ao contrario do que se poderia supor). Por isso o role tem LOGIN, mas SEM PASSWORD
--- definida: nenhum metodo de autenticacao por senha (scram-sha-256/md5) consegue
--- autentica-lo por rede, e pg_cron nao depende de rede para rodar o job internamente.
-----------------------------------------------------------------------------

CREATE ROLE expurgo_particao_auditoria LOGIN;

GRANT CONNECT ON DATABASE "db-csp-postgres" TO expurgo_particao_auditoria;
GRANT USAGE ON SCHEMA public TO expurgo_particao_auditoria;

-- So leitura -- nenhum GRANT TRUNCATE, nenhum GRANT em cima de autorizacoes que nao seja
-- SELECT. Confere o que a rotina afirmou; nunca expurga.
GRANT SELECT ON autorizacoes TO expurgo_particao_auditoria;
GRANT SELECT ON expurgo_particao_registro TO expurgo_particao_auditoria;

GRANT SELECT, INSERT ON expurgo_particao_auditoria_pg_cron TO expurgo_particao_auditoria;
GRANT USAGE, SELECT ON SEQUENCE expurgo_particao_auditoria_pg_cron_id_seq
    TO expurgo_particao_auditoria;

-----------------------------------------------------------------------------
--- REVERSAO
-----------------------------------------------------------------------------
--- REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM expurgo_particao_rotina;
--- REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM expurgo_particao_auditoria;
--- DROP ROLE IF EXISTS expurgo_particao_rotina;
--- DROP ROLE IF EXISTS expurgo_particao_auditoria;
-----------------------------------------------------------------------------
