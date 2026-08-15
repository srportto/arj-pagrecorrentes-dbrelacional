## Why

O `infra/` do monorepo é hoje apenas um esqueleto: os diretórios `modules/` e `envs/`
existem só com READMEs, sem nenhum Terraform funcional, e o desenvolvimento local usa
Docker puro. Para evoluir rumo a AWS ECS + Fargate precisamos da fundação de rede e do
plano de compute, mas validá-la contra a AWS real custa dinheiro e exige conta. Com o
Floci (emulador AWS local, drop-in do LocalStack Community que foi descontinuado em
mar/2026) conseguimos escrever e rodar esse Terraform localmente, sem custo e sem conta,
provando a topologia antes de qualquer `envs/prod`.

## What Changes

- **Novo módulo `modules/networking`**: VPC `vpc-arj` (`10.0.0.0/16`), 6 subnets (3 públicas
  `/24` + 3 privadas `/20`, uma por AZ), Internet Gateway, 3 NAT Gateways + 3 EIPs (um por
  AZ), route table pública e 3 privadas (uma por AZ), security groups base, e exportação dos
  IDs via SSM Parameter Store (`/vpc-arj/vpc/...`) e outputs. Segue o padrão da infra de
  referência do LinuxTips, **sem** o 3º tier de subnets de `databases` (escopo é 6 subnets).
- **Novo módulo `modules/ecs-cluster`**: cluster ECS em modo Fargate + Application Load
  Balancer **internet-facing** nas subnets públicas (listener `:80`, target group, security
  group). Comporta os dois serviços do monorepo como ECS Services independentes.
- **Novo módulo `modules/ecs-service`**: módulo parametrizável de ECS Service em Fargate
  (imagem, porta, env, CPU/memória, health check), instanciado duas vezes —
  `contratocommand` (`:8080`) e `contratoquery` (`:8081`) — com tasks nas subnets
  privadas e health check em `/actuator/health`.
- **Novo ambiente `envs/local`**: composição dos três módulos apontando para o Floci
  (`http://localhost:4566`), com provider AWS configurado para emulador (credenciais fake,
  `skip_*`, bloco `endpoints{}`), state local e imagens publicadas no ECR do Floci.
- **Sem impacto em produção**: `envs/prod`, `bootstrap`, `modules/rds-postgres` e
  `modules/observability` continuam placeholders — nada de AWS real nesta fase.

## Capabilities

### New Capabilities
- `aws-network-foundation`: módulo Terraform de rede (VPC `vpc-arj`, 6 subnets em 3 AZs,
  IGW, 3 NAT Gateways, route tables, security groups base e publicação de IDs em SSM
  Parameter Store) reutilizável entre ambientes.
- `ecs-fargate-cluster`: módulo Terraform do cluster ECS Fargate e do Application Load
  Balancer internet-facing que roteia o tráfego público para os serviços.
- `ecs-fargate-service`: módulo Terraform parametrizável de ECS Service em Fargate,
  instanciado para `contratocommand` e `contratoquery`.
- `local-aws-environment`: ambiente `envs/local` que compõe os módulos contra o emulador
  Floci, com o provider AWS apontado para `localhost:4566` e state local, permitindo
  `terraform plan`/`apply` sem conta AWS.

### Modified Capabilities
<!-- Nenhuma capability existente tem seus requisitos alterados. -->

## Impact

- **Código de infra**: preenche `infra/modules/networking`, `infra/modules/ecs-cluster`,
  `infra/modules/ecs-service` e `infra/envs/local` (hoje só READMEs). `infra/README.md`
  pode ser atualizado para refletir que estes deixaram de ser placeholders.
- **Aplicações**: consome as imagens de `apps/contratocommand/Dockerfile` e
  `apps/contratoquery/Dockerfile`; exige publicá-las no ECR do Floci antes do deploy.
  Nenhuma alteração no código das apps.
- **Dependências / ferramentas**: Terraform `>= 1.10`, provider AWS `~> 5.x`, Docker (para o
  Floci e para o ECS via Docker real), e o Floci rodando localmente (`floci start` ou
  `docker compose` com `floci/floci:latest`).
- **Fora de escopo**: `envs/prod`, `bootstrap` (S3+DynamoDB de state remoto),
  `modules/rds-postgres`, `modules/observability` e pipelines de CI/CD.
