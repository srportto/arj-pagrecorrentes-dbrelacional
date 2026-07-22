# infra

Código de infraestrutura do monorepo, separado do código de aplicação (`apps/`).

## Estado atual desta pasta

`modules/networking`, `modules/ecs-cluster`, `modules/ecs-service`, `envs/local` e
`envs/local-messaging` têm Terraform funcional, validado com `terraform apply` real
contra o [Floci](../docs/floci-aws-local/floci-aws-local.md) (emulador AWS local — ver
`openspec/changes/add-ecs-networking-foundation` e
`openspec/changes/add-eventos-autorizacao-sns-sqs` para o histórico completo das
mudanças). Nesta fase:

- **Nenhum recurso de AWS real é provisionado** — `envs/local` só fala com o
  Floci (`localhost:4566`), com credenciais fake.
- **`envs/prod`, `bootstrap`, `modules/rds-postgres` e `modules/observability`
  continuam placeholders.**

A infraestrutura de **desenvolvimento local sem Terraform** em
[`local/`](local/) (Postgres com `pg_partman`/`pg_cron`/`pgvector`) continua
existindo em paralelo — `envs/local` aponta para esse mesmo Postgres via
`host.docker.internal`.

## Topologia

```
infra/
├── modules/
│   ├── networking/    # VPC vpc-arj, 6 subnets, IGW, NAT, rotas, SSM         [funcional]
│   ├── ecs-cluster/   # cluster ECS Fargate + ALB internet-facing            [funcional]
│   ├── ecs-service/   # ECS Service parametrizavel (instanciado 2x)          [funcional]
│   ├── rds-postgres/  # (futuro)
│   └── observability/ # (futuro)
├── envs/
│   ├── local/             # composicao dos modulos (VPC/ECS) contra o Floci      [funcional]
│   ├── local-messaging/   # SNS + SQS de eventos de autorizacao no Floci        [funcional]
│   └── prod/              # composicao dos modulos contra a AWS real            [placeholder]
├── bootstrap/          # state bucket (S3) + lock table (DynamoDB)           [placeholder]
└── local/               # infra de dev local sem Terraform (Postgres)       [funcional]
```

- [`modules/`](modules/) — blocos Terraform reutilizáveis, um módulo por responsabilidade.
- [`envs/local/`](envs/local/) — composição dos módulos (VPC/ECS) para rodar contra o
  Floci. Ver o README de lá para pré-requisitos e o passo a passo de `apply`.
- [`envs/local-messaging/`](envs/local-messaging/) — root independente que provisiona o
  tópico SNS `sns-estados-autorizacao` e a fila SQS `SQS-eventos-autorizacao` no Floci,
  usados pelo `arj-contratocommand` (publicação) e pelo `autorizacaostatus-producer`
  (consumo). Aplica em segundos e não interfere no `envs/local` (VPC/ECS).
- [`envs/prod/`](envs/prod/) — composição dos módulos para a AWS real (placeholder).
- [`bootstrap/`](bootstrap/) — infraestrutura mínima para existir o backend de state remoto
  (placeholder).
- [`local/`](local/) — infraestrutura de desenvolvimento local que **não** é implantada na
  cloud.

## Fora de escopo (ainda)

- `envs/prod` (AWS real) e `bootstrap` (state remoto S3+DynamoDB).
- Módulos `rds-postgres` e `observability`.
- Pipelines de CI/CD (GitHub Actions) para aplicar esta infraestrutura.
- Proxy HTTP de dados do ALB via `alb_dns_name` no Floci — limitação da edição
  gratuita testada (control-plane e health checks funcionam; ver
  [`envs/local/README.md`](envs/local/README.md)).

Essas frentes são propostas de mudança futuras.
