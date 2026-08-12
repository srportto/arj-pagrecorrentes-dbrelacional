-- Massa sintetica para medir custo de planejamento/execucao com volume representativo.
-- Reproduz a distribuicao real: cada conta cai numa unica particao quente (0-888),
-- com skew realista de quantidade de autorizacoes por conta (poucas contas "pesadas").
-- Ver openspec/changes/reduzir-custo-planejamento-consultas/design.md (secao de medicoes).
--
-- Uso: docker exec -i postgres18-kiq psql -U docker -d db-csp-postgres < gerar-massa-sintetica-representativa.sql
-- Gera ~276 mil linhas (80 mil contas). Para restaurar o banco local ao estado vazio depois:
--   TRUNCATE autorizacoes;

\timing on

CREATE TEMP TABLE contas_sinteticas AS
SELECT
    gen_random_uuid() AS id_unico_conta_contratante,
    (floor(random() * 889))::int AS id_particao_conta,
    CASE
        WHEN r < 0.90 THEN 1 + floor(random() * 3)::int        -- 90% das contas: 1-3 autorizacoes
        WHEN r < 0.99 THEN 4 + floor(random() * 12)::int       -- 9% das contas: 4-15 autorizacoes
        ELSE 16 + floor(random() * 135)::int                   -- 1% das contas ("pesadas"): 16-150
    END AS qtd_autorizacoes
FROM (
    SELECT random() AS r FROM generate_series(1, 80000)
) sorteio;

CREATE INDEX ON contas_sinteticas (id_particao_conta);

INSERT INTO autorizacoes (
    id_autorizacao, id_particao_conta, tipo_produto, tipo_jornada, status, motivo_status,
    data_hora_inclusao, data_hora_ultima_atlz, data_inicio_vigencia, data_fim_vigencia,
    valor, id_autorizacao_empresa, valor_limite, frequencia, quantidade_dividas_ciclo,
    indicador_uso_limite_conta, indicador_tipo_mensageria, codigo_canal_contratacao,
    descricao, id_unico_conta_contratante, id_pessoa_pagadora, id_pessoa_devedora,
    id_pessoa_recebedora, metadados
)
SELECT
    gen_random_uuid(),
    c.id_particao_conta,
    (1 + floor(random() * 2))::numeric(6,0),                            -- 1=PIX_AUTO, 2=DDA_AUTO
    0,
    (ARRAY[4,4,4,5,6,1])[1 + floor(random() * 6)::int],                 -- maioria ATIVA, resto variado
    'Autorizacao criada (massa sintetica)',
    now() - (random() * interval '730 days'),
    now() - (random() * interval '30 days'),
    current_date - (random() * 60)::int,
    current_date + (30 + random() * 700)::int,
    (random() * 5000)::numeric(17,2),
    gen_random_uuid()::text,
    (random() * 10000)::numeric(17,2),
    1 + floor(random() * 4)::int,
    floor(random() * 12)::int,
    floor(random() * 2)::int,
    floor(random() * 2)::int,
    'CANAL_SINTETICO',
    'gerado para medicao de planejamento',
    c.id_unico_conta_contratante,
    gen_random_uuid(),
    gen_random_uuid(),
    gen_random_uuid(),
    '{}'
FROM contas_sinteticas c
CROSS JOIN LATERAL generate_series(1, c.qtd_autorizacoes) AS n;

SELECT count(*) AS total_linhas FROM autorizacoes;
SELECT count(DISTINCT id_particao_conta) AS particoes_atingidas FROM autorizacoes WHERE id_particao_conta < 900;
SELECT max(qtd) AS maior_conta, avg(qtd)::numeric(10,2) AS media_por_conta FROM (
    SELECT count(*) AS qtd FROM autorizacoes GROUP BY id_unico_conta_contratante
) x;
