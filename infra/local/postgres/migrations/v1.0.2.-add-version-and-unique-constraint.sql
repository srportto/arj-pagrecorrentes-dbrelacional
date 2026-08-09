-----------------------------------------------------------------------------
--- Adiciona coluna de versao para lock otimista e constraint UNIQUE
--- em (id_particao_conta, id_autorizacao_empresa) para idempotencia de criacao
-----------------------------------------------------------------------------

-- Adiciona coluna de versao em autorizacoes
-- A coluna e' populada com default 0 para linhas existentes
ALTER TABLE autorizacoes ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Cria constraint UNIQUE em (id_particao_conta, id_autorizacao_empresa)
-- Esta constraint permite que o mesmo id_autorizacao_empresa nao seja replicado
-- dentro da mesma particao (que corresponde a mesma conta via hash determinístico).
-- O constraint inclui id_particao_conta porque a tabela e' particionada por esta coluna.
ALTER TABLE autorizacoes ADD CONSTRAINT uk_autorizacao_empresa_particao UNIQUE (id_particao_conta, id_autorizacao_empresa);
