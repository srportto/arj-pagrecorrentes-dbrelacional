# Movido para infra/local/postgres/

A infraestrutura de banco de dados local (Dockerfile do PostgreSQL 16 com
`pg_partman` + `pg_cron`, compose e instruções de uso) foi movida para
[`infra/local/postgres/`](../../infra/local/postgres/), como parte da
reorganização do monorepo em `apps/` (aplicações) + `infra/` (infraestrutura).

Este diretório permanece apenas como ponteiro, para não quebrar links antigos.
