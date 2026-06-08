
## Para buildar a imagem
<p> Para buildar a imagem na mesma pasta do arquivo dockerfile </p>

```
  docker build -t postgres18-kiq-extras-partman_cron:1.0 .
```

para buildar a imagem
  docker build -t postgres16-extras-partman_cron:1.0 .
  docker build -t postgres16-extras-partman_cron:2.0 .


  docker-compose -f postgres-db-v18-v3.yml up -d


Pra rodar/iniciar composicao
   docker-compose -f postgres-db-v16-v1.yml up -d

Para parar 
  docker-compose -f postgres-db-v16-v1.yml down -v
  docker-compose -f postgres-db-v16-v1.yml restart


## Para subir a composicao via docker-compese 

```   
docker-compose -f postgres-db-v18.yml up -d      #subir composicão
docker-compose -f postgres-db-v18.yml down -v    #baixar composicao 
docker-compose -f postgres-db-v18.yml restart    #restartr composicao
```


## Utilizando o pg_cron
<br/>

P/ Agendar uma limpeza (vacuum) todo dia à meia-noite:

```
SELECT cron.schedule('limpeza-diaria', '0 0 * * *', 'VACUUM');
```



---



##### Definicao da tabela ja particionada

CREATE TABLE autorizacoes (
    id_autorizacao UUID NOT NULL,
    data_fim_vigencia DATE NOT NULL, -- Coluna de partição deve ser NOT NULL
    data_inicio_vigencia DATE,
    valor NUMERIC(17, 2), 
    id_autorizacao_empresa UUID,
    valor_limite NUMERIC(17, 2),
    frequencia INT CHECK (frequencia IN (1, 2, 3, 4)),
    quantidade_dividas_ciclo INT,
    indicador_uso_limite_conta INT,
    id_unico_conta_contratante UUID,
    id_pessoa_pagadora UUID,
    id_pessoa_devedora UUID,
    id_pessoa_recebedora UUID,
    metadado JSON,
    -- A PK precisa conter a coluna de particionamento
    CONSTRAINT pk_autorizacoes PRIMARY KEY (id_autorizacao, data_fim_vigencia)
) PARTITION BY RANGE (data_fim_vigencia);

---
#####  Criacao de particao na mao anual

-- Partição para dados de 2024
CREATE TABLE autorizacoes_y2024 PARTITION OF autorizacoes
    FOR VALUES FROM ('2024-01-01') TO ('2025-01-01');

-- Partição para dados de 2025
CREATE TABLE autorizacoes_y2025 PARTITION OF autorizacoes
    FOR VALUES FROM ('2025-01-01') TO ('2026-01-01');

-- Partição "Default" para evitar erros caso entre uma data fora dos ranges acima
CREATE TABLE autorizacoes_default PARTITION OF autorizacoes DEFAULT;


---
##### Criacao de particao na mao diaria

-- Partição para o dia 01/11/2023
CREATE TABLE autorizacoes_2023_11_01 PARTITION OF autorizacoes
    FOR VALUES FROM ('2023-11-01') TO ('2023-11-02');

-- Partição para o dia 02/11/2023
CREATE TABLE autorizacoes_2023_11_02 PARTITION OF autorizacoes
    FOR VALUES FROM ('2023-11-02') TO ('2023-11-03');

-- Partição para o dia 03/11/2023
CREATE TABLE autorizacoes_2023_11_03 PARTITION OF autorizacoes
    FOR VALUES FROM ('2023-11-03') TO ('2023-11-04');

-- Partição "Default" para evitar erros caso entre uma data fora dos ranges acima
CREATE TABLE autorizacoes_default PARTITION OF autorizacoes DEFAULT;

---
##### Criacao automacao, particionamento mensal 

```
SELECT partman.create_parent(
    p_parent_table := 'public.autorizacoes',
    p_control := 'data_fim_vigencia',
    p_type := 'native',
    p_interval := 'monthly',
    p_premake := 3 -- Já deixa 3 meses criados na frente por segurança
);
```

---
##### Criacao automacao, particionamento diario

```
SELECT partman.create_parent(
    p_parent_table := 'public.autorizacoes',
    p_control := 'data_fim_vigencia',
    p_type := 'native',
    p_interval := 'daily',
    p_premake := 7 -- Já deixa as partições de 7 dias pra frente criadas
);
```


---
##### Habilitando o particionamento diario comando do pg_partman rodando schedulado via pg_cron

```
SELECT cron.schedule(
    'manutencao-diaria-partman',        -- Um nome para identificar o seu job
    '0 1 * * *',                        -- A expressão cron (Minuto Hora Dia Mês DiaDaSemana)
    'SELECT partman.run_maintenance();' -- O comando que será executado
);
```
> 
> Obs.: o cron '0/10 * * * *'permite rodar o job a cada 10 min e todo dia vide abaixo
> 


```
SELECT cron.schedule(
    'manutencao-diaria-partman', 
    '0/10 * * * *',
    'SELECT partman.run_maintenance();' 
);
```


---
##### Conferir se o cron está funcionando 


SELECT * from cron.job_run_details;

---
##### Criar particoes de forma retroativa até o dia presente e depois manter executado diariamente


SELECT partman.create_parent(
    p_parent_table := 'public.autorizacoes',
    p_control := 'data_fim_vigencia',
    p_type := 'native',
    p_interval := 'daily',
    p_premake := 7,
    p_start_partition := '2026-01-01' 
);







