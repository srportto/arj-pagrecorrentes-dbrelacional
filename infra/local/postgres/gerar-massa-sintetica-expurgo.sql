-- Massa sintetica para exercitar o caminho de reclamacao do ring buffer de expurgo
-- (particoes 900..999) sem depender da passagem real do tempo -- o anel so completa a
-- primeira volta e passa a ter dado nas gavetas de expurgo por volta de 2028-04-20 (ver
-- openspec/changes/reclamar-particao-expurgo-ciclo/design.md, secao Context). Ate la, o
-- unico lugar onde o caminho de TRUNCATE existe de verdade e este.
--
-- Semeia tres particoes coerentemente com a formula de particionamento
-- (ControleExpurgoAutorizacao.obterParticaoExpurgoWrite: 900 + (semanas desde o Epoch % 100)):
--   - a particao ALVO (particao de escrita do :data_referencia, + 2, com wraparound)
--   - as duas particoes VIZINHAS (alvo-1 e alvo+1, com wraparound) -- usadas para afirmar que
--     o esvaziamento da alvo nao afeta quem esta ao lado
--
-- Parametros obrigatorios (via -v), sem default -- o comportamento errado por omissao
-- silenciosa e pior que a falha explicita por variavel ausente. IMPORTANTE: passe os
-- valores SEM aspas simples no shell -- o script referencia as variaveis como :'nome',
-- forma do psql que ja adiciona a quotacao SQL sozinha; incluir aspas no valor do -v
-- produz uma string com aspas DENTRO dela ('recente' em vez de recente), e a comparacao
-- de cenario deixa de bater silenciosamente (bug real, encontrado e corrigido ao validar
-- este script contra o banco).
--   data_referencia  data no formato AAAA-MM-DD, o "hoje" simulado para o calculo do alvo
--   cenario          ciclo_anterior (dado com ~98 semanas, o caso normal a esvaziar) ou
--                     recente       (dado recente demais, a anomalia que a rotina deve recusar)
--   qtd_linhas_por_particao  quantas linhas gerar em cada uma das 3 particoes
--
-- Uso (cenario normal, dado do ciclo anterior na alvo):
--   docker exec -i postgres18-kiq psql -U docker -d db-csp-postgres \
--     -v data_referencia=2026-08-22 -v cenario=ciclo_anterior -v qtd_linhas_por_particao=200 \
--     < gerar-massa-sintetica-expurgo.sql
--
-- Uso (cenario de anomalia, dado recente demais na alvo -- deve ser recusado, nao expurgado):
--   docker exec -i postgres18-kiq psql -U docker -d db-csp-postgres \
--     -v data_referencia=2026-08-22 -v cenario=recente -v qtd_linhas_por_particao=200 \
--     < gerar-massa-sintetica-expurgo.sql
--
-- Para descobrir quais particoes esta massa atingiu e limpa-las depois:
--   SELECT DISTINCT id_particao_conta FROM autorizacoes WHERE id_particao_conta >= 900 ORDER BY 1;
--   SELECT format('TRUNCATE autorizacoes_pe%s;', id_particao_conta)
--     FROM (SELECT DISTINCT id_particao_conta FROM autorizacoes WHERE id_particao_conta >= 900) x;

\timing on

-- Semana do :data_referencia e particao de escrita correspondente (mesma formula de
-- ControleExpurgoAutorizacao.obterParticaoExpurgoWrite).
SELECT
    floor(((:'data_referencia')::date - DATE '1970-01-01') / 7)::int AS semanas_referencia,
    900 + (floor(((:'data_referencia')::date - DATE '1970-01-01') / 7)::int % 100) AS particao_escrita
\gset

-- Particao alvo = escrita + 2, com retorno ciclico ao inicio da faixa (900..999).
SELECT
    (CASE WHEN :particao_escrita + 2 > 999 THEN :particao_escrita + 2 - 100 ELSE :particao_escrita + 2 END)
        AS particao_alvo
\gset

-- Vizinhas da alvo, com o mesmo retorno ciclico.
SELECT
    (CASE WHEN :particao_alvo - 1 < 900 THEN :particao_alvo - 1 + 100 ELSE :particao_alvo - 1 END)
        AS particao_vizinha_anterior,
    (CASE WHEN :particao_alvo + 1 > 999 THEN :particao_alvo + 1 - 100 ELSE :particao_alvo + 1 END)
        AS particao_vizinha_posterior
\gset

\echo Particao de escrita simulada para :data_referencia : :particao_escrita
\echo Particao ALVO (escrita + 2)                        : :particao_alvo
\echo Vizinha anterior (alvo - 1)                         : :particao_vizinha_anterior
\echo Vizinha posterior (alvo + 1)                        : :particao_vizinha_posterior
\echo Cenario da particao alvo                             : :cenario

-----------------------------------------------------------------------------
--- Particao ALVO
---
--- 'ciclo_anterior': dado com a idade correta que a formula produziria para essa
---   particao -- exatamente 98 semanas antes de :data_referencia (a semana em que ela foi
---   pela ultima vez a particao de escrita, antes do wraparound). E o caso normal: a rotina
---   deve encontrar isso e esvaziar.
--- 'recente': dado com poucos dias de idade, que NAO poderia legitimamente estar numa
---   particao de expurgo (a formula nunca produziria isso). E a anomalia: a rotina deve
---   recusar o esvaziamento e registrar a divergencia, nunca truncar.
-----------------------------------------------------------------------------

INSERT INTO autorizacoes (
    id_autorizacao, id_particao_conta, tipo_produto, tipo_jornada, status, motivo_status,
    data_hora_inclusao, data_hora_ultima_atlz, data_inicio_vigencia, data_fim_vigencia,
    valor, id_autorizacao_empresa, valor_limite, frequencia, quantidade_dividas_ciclo,
    indicador_uso_limite_conta, indicador_tipo_mensageria, codigo_canal_contratacao,
    descricao, id_unico_conta_contratante, id_pessoa_pagadora, id_pessoa_devedora,
    id_pessoa_recebedora, codigo_canal_cancelamento, id_pessoa_cancelamento,
    data_hora_cancelamento, motivo_cancelamento, metadados
)
SELECT
    gen_random_uuid(),
    :particao_alvo,
    (1 + floor(random() * 2))::numeric(6,0),                            -- 1=PIX_AUTO, 2=DDA_AUTO
    0,
    5,                                                                    -- CANCELADA
    'Autorizacao cancelada (massa sintetica de expurgo)',
    data_atribuida - ((random() * 3) * interval '1 day'),
    data_atribuida,
    (data_atribuida - interval '90 days')::date,
    (data_atribuida + interval '365 days')::date,
    (random() * 5000)::numeric(17,2),
    gen_random_uuid()::text,
    (random() * 10000)::numeric(17,2),
    1 + floor(random() * 4)::int,
    floor(random() * 12)::int,
    floor(random() * 2)::int,
    floor(random() * 2)::int,
    'CANAL_SINTETICO',
    'gerado para exercitar reclamacao da particao de expurgo (cenario: ' || :'cenario' || ')',
    gen_random_uuid(),
    gen_random_uuid(),
    gen_random_uuid(),
    gen_random_uuid(),
    'CANAL_SINTETICO',
    gen_random_uuid(),
    data_atribuida,
    'expurgo sintetico',
    '{}'
FROM (
    -- CASE inline (nao um subselect LATERAL sem correlacao real com "n"): o Postgres
    -- pode -- e, empiricamente, fez -- avaliar uma subquery LATERAL nao correlacionada
    -- uma unica vez e reaproveitar o resultado em todas as linhas, o que produziria
    -- a MESMA data_hora_ultima_atlz para as :qtd_linhas_por_particao linhas inteiras.
    -- Aqui a expressao volatil (random()) fica na propria lista de projecao, garantindo
    -- reavaliacao por linha.
    SELECT
        n,
        CASE
            WHEN (:'cenario') = 'recente'
                THEN (:'data_referencia')::timestamp - (random() * interval '10 days')
            ELSE
                -- semana correta = semanas_referencia - 98 (ver comentario de cabecalho),
                -- com um instante aleatorio dentro dessa janela de 7 dias
                (DATE '1970-01-01' + ((:semanas_referencia - 98) * 7))::timestamp
                    + (random() * (7 * 86400 - 1) * interval '1 second')
        END AS data_atribuida
    FROM generate_series(1, :qtd_linhas_por_particao) AS n
) linhas;

-----------------------------------------------------------------------------
--- Vizinhas: sempre com a idade correta para a propria particao, independente do
--- cenario escolhido para a alvo -- e o que permite ao teste afirmar que elas ficam
--- intactas depois do esvaziamento da alvo.
-----------------------------------------------------------------------------

INSERT INTO autorizacoes (
    id_autorizacao, id_particao_conta, tipo_produto, tipo_jornada, status, motivo_status,
    data_hora_inclusao, data_hora_ultima_atlz, data_inicio_vigencia, data_fim_vigencia,
    valor, id_autorizacao_empresa, valor_limite, frequencia, quantidade_dividas_ciclo,
    indicador_uso_limite_conta, indicador_tipo_mensageria, codigo_canal_contratacao,
    descricao, id_unico_conta_contratante, id_pessoa_pagadora, id_pessoa_devedora,
    id_pessoa_recebedora, codigo_canal_cancelamento, id_pessoa_cancelamento,
    data_hora_cancelamento, motivo_cancelamento, metadados
)
SELECT
    gen_random_uuid(),
    linhas.particao,
    (1 + floor(random() * 2))::numeric(6,0),
    0,
    5,
    'Autorizacao cancelada (massa sintetica de expurgo - vizinha)',
    data_atribuida - ((random() * 3) * interval '1 day'),
    data_atribuida,
    (data_atribuida - interval '90 days')::date,
    (data_atribuida + interval '365 days')::date,
    (random() * 5000)::numeric(17,2),
    gen_random_uuid()::text,
    (random() * 10000)::numeric(17,2),
    1 + floor(random() * 4)::int,
    floor(random() * 12)::int,
    floor(random() * 2)::int,
    floor(random() * 2)::int,
    'CANAL_SINTETICO',
    'gerado para exercitar reclamacao da particao de expurgo (vizinha, deve permanecer intacta)',
    gen_random_uuid(),
    gen_random_uuid(),
    gen_random_uuid(),
    gen_random_uuid(),
    'CANAL_SINTETICO',
    gen_random_uuid(),
    data_atribuida,
    'expurgo sintetico',
    '{}'
FROM (
    -- Mesmo cuidado do bloco ALVO: a expressao volatil fica na projecao da subquery
    -- correlacionada com p.particao e n, nunca isolada num LATERAL sem uso de "n".
    SELECT
        p.particao,
        n,
        -- semana correta para a particao p.particao, pela mesma formula:
        -- offset = (p - 900) % 100; resto = (semanas_referencia - offset) % 100;
        -- semana_correta = semanas_referencia - resto
        (DATE '1970-01-01' + (
            (:semanas_referencia - (
                (:semanas_referencia - ((p.particao - 900) % 100)) % 100
            )) * 7
        ))::timestamp + (random() * (7 * 86400 - 1) * interval '1 second') AS data_atribuida
    FROM (
        VALUES (:particao_vizinha_anterior), (:particao_vizinha_posterior)
    ) AS p(particao)
    CROSS JOIN generate_series(1, :qtd_linhas_por_particao) AS n
) linhas;

-----------------------------------------------------------------------------
--- Resumo
-----------------------------------------------------------------------------

SELECT
    id_particao_conta,
    count(*) AS total_linhas,
    min(data_hora_ultima_atlz) AS mais_antiga,
    max(data_hora_ultima_atlz) AS mais_recente
FROM autorizacoes
WHERE id_particao_conta IN (:particao_alvo, :particao_vizinha_anterior, :particao_vizinha_posterior)
GROUP BY id_particao_conta
ORDER BY id_particao_conta;
