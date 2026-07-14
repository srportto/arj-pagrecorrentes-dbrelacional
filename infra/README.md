# infra

Código de infraestrutura do monorepo, separado do código de aplicação (`code/`).

## Estado atual desta pasta

Este é um **esqueleto** que estabelece a topologia-alvo para a evolução do sistema
rumo a AWS ECS + Fargate, provisionado via Terraform. Nesta fase:

- **Não há nenhum arquivo `.tf` funcional.**
- **Nenhum recurso AWS é provisionado.**
- **Nenhuma credencial de cloud é usada ou armazenada aqui.**

O único conteúdo executável hoje é a infraestrutura de **desenvolvimento local**
em [`local/`](local/) (banco PostgreSQL com `pg_partman` + `pg_cron`).

## Topologia-alvo

```
infra/
├── modules/        # blocos Terraform reutilizáveis (networking, banco, ECS, observabilidade)
├── envs/
│   ├── local/       # ambiente local: provider apontando para o emulador AWS (Floci)
│   └── prod/        # ambiente de produção: provider AWS real, backend remoto
├── bootstrap/       # state bucket (S3) + lock table (DynamoDB) — pré-requisito dos envs
└── local/           # infraestrutura de desenvolvimento local (Postgres partman/cron)
```

- [`modules/`](modules/) — blocos Terraform reutilizáveis, um módulo por responsabilidade.
- [`envs/local/`](envs/local/) — composição dos módulos para rodar contra um emulador AWS local.
- [`envs/prod/`](envs/prod/) — composição dos módulos para a AWS real.
- [`bootstrap/`](bootstrap/) — infraestrutura mínima para existir o backend de state remoto.
- [`local/`](local/) — infraestrutura de desenvolvimento local que **não** é implantada na cloud.

## Fora de escopo nesta fase

- Terraform funcional (módulos, providers, state remoto).
- Provisionamento de qualquer recurso AWS (VPC, RDS, ECS, Fargate).
- Ambiente local via Floci (emulador AWS) — hoje o desenvolvimento local usa Docker puro.
- Pipelines de CI/CD (GitHub Actions) para aplicar esta infraestrutura.
- Migração de schema de banco (Flyway/Liquibase) para o RDS.

Essas frentes são propostas de mudança futuras, construídas sobre este esqueleto.
