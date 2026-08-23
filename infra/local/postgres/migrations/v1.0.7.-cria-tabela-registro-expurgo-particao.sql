-----------------------------------------------------------------------------
--- Tabela de registro da rotina de reclamacao da particao de expurgo
---
--- Motivacao (change `reclamar-particao-expurgo-ciclo`, design.md D3/D5):
---
--- O ring buffer de expurgo (particoes 900..999, ver v1.0.1) tem produtor
--- (transferirParaExpurgo, no contratocommand) mas nao tinha consumidor ate esta change --
--- a app apps/expurgo-particao fecha esse ciclo, esvaziando a cada 30 minutos a particao
--- que o ciclo permite (escrita + 2). Essa rotina roda SEM efeito por ~87 semanas (o anel
--- so completa a primeira volta por volta de 2028-04-20), entao uma execucao sem efeito
--- precisa ser indistinguivel de rotina saudavel -- nao de rotina quebrada. E' por isso
--- que TODA execucao grava aqui, inclusive quando a acao foi NENHUMA.
---
--- Esta tabela e' consultada tanto pela propria rotina (para nao recalcular a formula
--- duas vezes) quanto pelo job pg_cron de verificacao independente (v1.0.9), que confere
--- o estado da particao que a rotina AFIRMOU ter mirado, sem recalcular a formula por
--- conta propria -- para nao existir uma segunda fonte da verdade sobre qual particao
--- deveria ter sido esvaziada.
---
--- NAO fica dentro da tabela `autorizacoes` nem participa do particionamento dela --
--- e' metadado operacional, nao dado de negocio.
-----------------------------------------------------------------------------

CREATE TABLE expurgo_particao_registro (
    id BIGSERIAL PRIMARY KEY,
    semana INT NOT NULL,
    particao_escrita INT NOT NULL,
    particao_alvo INT NOT NULL,
    -- NULL apenas quando acao = RECUSA_LOCK_TIMEOUT: a verificacao nem chegou a rodar,
    -- entao nao ha estado observado para relatar.
    estado TEXT CHECK (estado IN ('VAZIA', 'DADO_CICLO_ANTERIOR', 'DADO_RECENTE')),
    acao TEXT NOT NULL CHECK (
        acao IN ('NENHUMA', 'TRUNCATE', 'RECUSA_DADO_RECENTE', 'RECUSA_LOCK_TIMEOUT')
    ),
    modo_consulta BOOLEAN NOT NULL DEFAULT false,
    executado_em TIMESTAMPTZ NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- A consulta mais comum e' "qual foi a ultima execucao (e o que ela afirmou) para a
-- particao alvo X" -- tanto para a rotina quanto para a verificacao pg_cron.
CREATE INDEX idx_expurgo_particao_registro_particao_alvo_executado_em
    ON expurgo_particao_registro (particao_alvo, executado_em DESC);

-----------------------------------------------------------------------------
--- REVERSAO
-----------------------------------------------------------------------------
--- DROP TABLE IF EXISTS expurgo_particao_registro;
-----------------------------------------------------------------------------
