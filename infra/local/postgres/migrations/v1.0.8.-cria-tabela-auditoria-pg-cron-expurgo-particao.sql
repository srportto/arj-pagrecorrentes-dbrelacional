-----------------------------------------------------------------------------
--- Tabela de auditoria dos jobs pg_cron da reclamacao de particao de expurgo
---
--- Motivacao (change `reclamar-particao-expurgo-ciclo`, design.md D5):
---
--- Esta tabela e' o registro FORENSE, nao um alarme -- ela nao notifica ninguem por
--- conta propria. `pg_cron` nao tem canal de saida (nao publica em SNS, nao chama
--- webhook), entao o valor dela e' responder "desde quando isto esta quebrado?" quando
--- alguem finalmente perguntar, nao avisar na hora.
---
--- Dois tipos de verificacao, independentes entre si:
---
---   CONFIRMACAO_REGISTRO (job diario) -- so confere se `expurgo_particao_registro` tem
---     entrada recente. NAO recalcula a formula da particao alvo -- audita a AFIRMACAO
---     da rotina, nunca uma segunda fonte da verdade sobre qual particao deveria ter
---     sido esvaziada (ver v1.0.9 e a spec `reclamacao-particao-expurgo`).
---
---   INVARIANTE_SEMANAL (job semanal, apos a virada de quinta-feira) -- confere um fato
---     estrutural do banco, nao uma decisao da rotina: toda linha numa particao de
---     expurgo tem `data_hora_ultima_atlz` cuja semana, pela formula de particionamento,
---     mapeia de volta para aquela mesma particao. Isso pega contaminacao (dado velho e
---     novo misturados) independente de qualquer coisa que a rotina tenha ou nao feito.
-----------------------------------------------------------------------------

CREATE TABLE expurgo_particao_auditoria_pg_cron (
    id BIGSERIAL PRIMARY KEY,
    tipo_verificacao TEXT NOT NULL
        CHECK (tipo_verificacao IN ('CONFIRMACAO_REGISTRO', 'INVARIANTE_SEMANAL')),
    -- particao_alvo: preenchida so em CONFIRMACAO_REGISTRO (a particao que a ultima
    -- execucao da rotina afirmou ter mirado). NULL em INVARIANTE_SEMANAL, que varre
    -- todas as 100 particoes numa unica execucao.
    particao_alvo INT,
    -- particao_com_divergencia: preenchida so quando resultado = DIVERGENCIA em
    -- INVARIANTE_SEMANAL -- qual particao especifica contém dado incoerente com ela.
    particao_com_divergencia INT,
    resultado TEXT NOT NULL
        CHECK (resultado IN ('OK', 'DIVERGENCIA', 'SEM_REGISTRO_DA_ROTINA')),
    detalhe TEXT,
    verificado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_expurgo_particao_auditoria_pg_cron_verificado_em
    ON expurgo_particao_auditoria_pg_cron (verificado_em DESC);

-----------------------------------------------------------------------------
--- REVERSAO
-----------------------------------------------------------------------------
--- DROP TABLE IF EXISTS expurgo_particao_auditoria_pg_cron;
-----------------------------------------------------------------------------
