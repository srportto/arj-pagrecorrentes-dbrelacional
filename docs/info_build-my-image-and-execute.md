# PostgreSQL 18 com pg_partman, pg_cron e pgvector

## Buildar a imagem Docker

Na pasta `infra/local/postgres/`:

```bash
docker build -t postgres18-kiq-extras-partman_cron_vector:1.0 .
```

## Subir a composição via docker-compose

```bash
cd infra/local/postgres

# Subir
docker-compose -f postgres-db-v18.yml up -d

# Parar
docker-compose -f postgres-db-v18.yml down -v

# Reiniciar
docker-compose -f postgres-db-v18.yml restart
```

## Usando pg_cron

Agendar uma limpeza (vacuum) todo dia à meia-noite:

```sql
SELECT cron.schedule('limpeza-diaria', '0 0 * * *', 'VACUUM');
```

## Exemplo: Particionamento automático diário

Criar particionamento automático com pg_partman:

```sql
SELECT partman.create_parent(
    p_parent_table := 'public.autorizacoes',
    p_control := 'data_fim_vigencia',
    p_type := 'native',
    p_interval := 'daily',
    p_premake := 7 -- Cria 7 dias de partições à frente
);
```

Agendar a manutenção diária via pg_cron:

```sql
SELECT cron.schedule(
    'manutencao-diaria-partman',
    '0 1 * * *', -- 1h da manhã todo dia
    'SELECT partman.run_maintenance();'
);
```

Para rodar a cada 10 minutos (útil em testes):

```sql
SELECT cron.schedule(
    'manutencao-diaria-partman',
    '0/10 * * * *',
    'SELECT partman.run_maintenance();'
);
```

## Verificar status do pg_cron

```sql
SELECT * FROM cron.job_run_details;
```

## Exemplo completo: Criar partições retroativamente até hoje

```sql
SELECT partman.create_parent(
    p_parent_table := 'public.autorizacoes',
    p_control := 'data_fim_vigencia',
    p_type := 'native',
    p_interval := 'daily',
    p_premake := 7,
    p_start_partition := '2026-01-01'
);
```
