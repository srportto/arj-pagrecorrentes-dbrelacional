# infra/local/postgres

PostgreSQL 18 local com `pg_partman`, `pg_cron` e `pgvector`, usado pelas duas aplicações que
leem/escrevem a tabela particionada `autorizacoes` (`apps/contratocommand`,
`apps/contratoquery`). É a **fonte única** do serviço Postgres do ambiente local — nenhum
outro compose do repositório declara este serviço (ver a change `unificar-orquestracao-docker-local`).

## Subir

```bash
docker compose --env-file ../../../.env -f postgres-db-v18.yml up -d
```

Requer `DB_PASSWORD` (e opcionalmente `DB_NAME`/`DB_USER_NAME`) — copie `.env.example` da raiz
para `.env` antes da primeira subida.

Na primeira subida com volume vazio, os scripts de `migrations/` rodam em ordem alfabética
(`v1.0.0`, `v1.0.1`, ...) e criam a tabela `autorizacoes` particionada.

## Validar que está no ar

```bash
docker exec postgres18-kiq pg_isready -U docker -d db-csp-postgres
docker exec postgres18-kiq psql -U docker -d db-csp-postgres -c "SHOW shared_preload_libraries;"
# pg_partman_bgw,pg_cron
```

## Parar

```bash
docker compose --env-file ../../../.env -f postgres-db-v18.yml down
```

`down` sem `-v` preserva o volume nomeado `postgres_pg_data` — os dados sobrevivem à troca de
projeto Compose (subida isolada aqui vs. via `compose.yaml` da raiz).
