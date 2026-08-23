-----------------------------------------------------------------------------
--- Jobs pg_cron de auditoria da reclamacao de particao de expurgo
---
--- Motivacao (change `reclamar-particao-expurgo-ciclo`, design.md D5): ver
--- v1.0.8.-cria-tabela-auditoria-pg-cron-expurgo-particao.sql para o racional completo.
---
--- Os dois jobs rodam via `cron.schedule_in_database(..., username := ...)` como
--- `expurgo_particao_auditoria` (v1.0.9) -- NAO como o superuser que aplica esta
--- migration. `pg_cron` (>= 1.3) suporta essa forma explicita de especificar o usuario
--- de execucao, o que evita que os jobs herdem privilegio de quem os agenda.
---
--- Horarios em UTC (cron.timezone padrao e' UTC neste cluster, ver
--- `SHOW cron.timezone;`) -- a mesma convencao de fuso fixo que a rotina Python usa
--- (ver expurgo_particao/calculo.py, agora_utc_date), para que auditoria e rotina nunca
--- discordem sobre "que dia e hoje" perto da virada de quinta-feira.
-----------------------------------------------------------------------------

-----------------------------------------------------------------------------
--- 1. Funcao do job diario: CONFIRMACAO_REGISTRO
---
--- So confere se a rotina deixou rastro nas ultimas horas -- NAO recalcula a formula da
--- particao alvo. A auditoria audita a AFIRMACAO da rotina (existe um registro recente?),
--- nunca decide por conta propria qual particao deveria ter sido esvaziada.
-----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION expurgo_particao_auditar_confirmacao_registro()
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
    ultima_execucao TIMESTAMPTZ;
    particao_ultima_execucao INT;
BEGIN
    SELECT executado_em, particao_alvo
      INTO ultima_execucao, particao_ultima_execucao
      FROM expurgo_particao_registro
     ORDER BY executado_em DESC
     LIMIT 1;

    IF ultima_execucao IS NULL OR ultima_execucao < now() - INTERVAL '1 hour' THEN
        INSERT INTO expurgo_particao_auditoria_pg_cron (tipo_verificacao, resultado, detalhe)
        VALUES (
            'CONFIRMACAO_REGISTRO',
            'SEM_REGISTRO_DA_ROTINA',
            format(
                'Nenhum registro da rotina nos ultimos 60 minutos (ultima execucao: %s). '
                'A rotina roda a cada 30 minutos -- ausencia de registro e o proprio sinal '
                'de que ela parou.',
                COALESCE(ultima_execucao::text, 'nunca')
            )
        );
    ELSE
        INSERT INTO expurgo_particao_auditoria_pg_cron
            (tipo_verificacao, particao_alvo, resultado, detalhe)
        VALUES (
            'CONFIRMACAO_REGISTRO',
            particao_ultima_execucao,
            'OK',
            format('Ultima execucao da rotina em %s, particao alvo %s', ultima_execucao, particao_ultima_execucao)
        );
    END IF;
END;
$$;

-----------------------------------------------------------------------------
--- 2. Funcao do job semanal: INVARIANTE_SEMANAL
---
--- Verifica um FATO ESTRUTURAL do banco (nao uma decisao da rotina): toda linha numa
--- particao de expurgo tem `data_hora_ultima_atlz` cuja semana mapeia de volta, pela
--- formula de particionamento, para aquela mesma particao. Contaminacao (dado velho e
--- novo misturados na mesma particao) aparece aqui independente do que a rotina fez.
-----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION expurgo_particao_auditar_invariante_semanal()
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
    particao INT;
    offset_esperado INT;
    qtd_linhas_incoerentes BIGINT;
    encontrou_divergencia BOOLEAN := false;
BEGIN
    FOR particao IN 900..999 LOOP
        offset_esperado := particao - 900;

        EXECUTE format(
            'SELECT count(*) FROM autorizacoes_pe%1$s '
            'WHERE (floor(extract(epoch FROM data_hora_ultima_atlz - timestamp ''1970-01-01'') '
            '  / (7 * 86400))::bigint %% 100) <> %2$s',
            particao, offset_esperado
        ) INTO qtd_linhas_incoerentes;

        IF qtd_linhas_incoerentes > 0 THEN
            encontrou_divergencia := true;
            INSERT INTO expurgo_particao_auditoria_pg_cron
                (tipo_verificacao, particao_com_divergencia, resultado, detalhe)
            VALUES (
                'INVARIANTE_SEMANAL',
                particao,
                'DIVERGENCIA',
                format(
                    '%s linha(s) na particao %s tem data_hora_ultima_atlz incoerente com o '
                    'offset esperado (%s) -- possivel ciclo de reclamacao perdido ou escrita '
                    'fora do fluxo esperado.',
                    qtd_linhas_incoerentes, particao, offset_esperado
                )
            );
        END IF;
    END LOOP;

    IF NOT encontrou_divergencia THEN
        INSERT INTO expurgo_particao_auditoria_pg_cron (tipo_verificacao, resultado, detalhe)
        VALUES (
            'INVARIANTE_SEMANAL', 'OK',
            'Todas as particoes de expurgo com dado sao coerentes com seu proprio offset semanal.'
        );
    END IF;
END;
$$;

-----------------------------------------------------------------------------
--- 3. Agendamento -- roda como expurgo_particao_auditoria (v1.0.9), nao como o
---    superuser que aplica esta migration.
-----------------------------------------------------------------------------

SELECT cron.schedule_in_database(
    'expurgo-particao-confirma-registro',
    '0 6 * * *',
    'SELECT expurgo_particao_auditar_confirmacao_registro();',
    current_database(),
    'expurgo_particao_auditoria',
    true
);

SELECT cron.schedule_in_database(
    'expurgo-particao-invariante-semanal',
    '0 7 * * 4',
    'SELECT expurgo_particao_auditar_invariante_semanal();',
    current_database(),
    'expurgo_particao_auditoria',
    true
);

-----------------------------------------------------------------------------
--- REVERSAO
-----------------------------------------------------------------------------
--- SELECT cron.unschedule('expurgo-particao-confirma-registro');
--- SELECT cron.unschedule('expurgo-particao-invariante-semanal');
--- DROP FUNCTION IF EXISTS expurgo_particao_auditar_confirmacao_registro();
--- DROP FUNCTION IF EXISTS expurgo_particao_auditar_invariante_semanal();
-----------------------------------------------------------------------------
