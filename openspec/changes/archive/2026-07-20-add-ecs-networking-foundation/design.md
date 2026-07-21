## Context

O `infra/` do monorepo é um esqueleto (`modules/` e `envs/` só com READMEs). O
`infra/README.md` já define a topologia-alvo: módulos reutilizáveis compostos por
ambiente, com `envs/local` apontando para um emulador AWS (Floci) e `envs/prod` para a
AWS real. Esta mudança preenche a primeira fatia dessa topologia — rede + compute ECS —
validável localmente.

Insumos concretos:
- **Referência de conteúdo**: `E:\projetos\workspace-linuxtips-ecs\linuxtips-curso-containers-vpc`,
  um Terraform *flat* (AWS provider 5.89) com VPC `10.0.0.0/16`, subnets públicas `/24`,
  privadas `/20`, databases `/24`, IGW, 3 NAT+EIP por AZ, route tables por AZ, e
  publicação de IDs em SSM Parameter Store. Usamos o padrão, não a estrutura flat.
- **Floci** (`docs/floci-aws-local/floci-aws-local.md`): emulador AWS local em
  `http://localhost:4566`, drop-in do LocalStack Community. Emula ECS via **Docker real**,
  ELB v2/ALB in-process, EC2/VPC e SSM Parameter Store. Suporta Terraform `>= 1.10`.
- **Apps**: `arj-contratocommand` (`:8080`) e `arj-contratoquery` (`:8081`), Spring Boot
  REST com `Dockerfile`, hoje rodando via `apps/docker-compose.yml` + Postgres local.

## Goals / Non-Goals

**Goals:**
- Preencher `modules/networking`, `modules/ecs-cluster`, `modules/ecs-service` e
  `envs/local` com Terraform funcional.
- Rede: VPC `vpc-arj`, 6 subnets (3 pub + 3 priv) em 3 AZs, IGW, 3 NAT (1/AZ), roteamento
  e publicação de IDs em SSM.
- Compute: cluster ECS Fargate + ALB internet-facing; as duas apps como ECS Services.
- Rodar `terraform plan`/`apply` contra o Floci, sem conta AWS e sem custo.
- Módulos escritos de forma parametrizável, reutilizáveis por um futuro `envs/prod`.

**Non-Goals:**
- `envs/prod` (AWS real) e `bootstrap` (state remoto S3+DynamoDB).
- Módulos `rds-postgres` e `observability`; o tier de subnets de `databases` da referência.
- Pipelines de CI/CD para aplicar a infra.
- HTTPS/ACM no ALB, autoscaling, Secrets Manager (apenas env vars nesta fase).

## Decisions

### D1. Módulos + envs em vez do flat da referência
A referência é flat, mas o `infra/README.md` mandata módulos + envs. Escolhemos honrar o
repo: o conteúdo da referência entra **dentro** dos módulos, e `envs/local` apenas compõe.
- **Por quê**: reutilização entre `local` e `prod` variando só inputs; alinhamento com a
  topologia já documentada.
- **Alternativa descartada**: copiar flat para `envs/local` — rápido, mas duplicaria tudo
  ao criar `prod` e contrariaria o README.

### D2. AZs via `format("%s%s", region, ["a","b","c"])`, não `data aws_availability_zones`
Mantemos o "hack" da referência para derivar AZs a partir da região.
- **Por quê**: determinístico e independente da fidelidade do `DescribeAvailabilityZones`
  do Floci; garante exatamente 3 AZs `a/b/c`.
- **Alternativa descartada**: `data "aws_availability_zones"` — idiomático em AWS real, mas
  frágil contra o emulador.

### D3. Três NAT Gateways (1 por AZ), como a referência
Cada subnet privada tem sua route table apontando para o NAT da própria AZ.
- **Por quê**: fidelidade ao padrão de HA da referência; em Floci são objetos de API sem
  custo. Deixa o módulo pronto para HA real em prod.
- **Trade-off**: em prod isso é caro (3 NAT). Mitigado parametrizando a contagem no módulo
  (default 1 para ambientes que optarem por economia), mesmo que `envs/local` use 3.

### D4. `ecs-service` parametrizável, instanciado 2x
Um único módulo recebe imagem, porta, CPU/memória, env e health path; `envs/local` o chama
para command (`:8080`) e query (`:8081`).
- **Por quê**: evita duplicação; o README do módulo já previa esse formato.
- **Alternativa descartada**: um módulo por app — mais duplicação, menos coeso.

### D5. ALB no `ecs-cluster`; target group por serviço
O ALB internet-facing e o listener `:80` vivem no `ecs-cluster` (compartilhado); cada
`ecs-service` cria seu target group e regra de roteamento.
- **Por quê**: um ALB para o cluster inteiro (padrão do README); roteamento por
  path/host-rule diferencia command de query.
- **Aberto**: regra de roteamento exata (path-based `/command/*` vs `/query/*`, ou
  host-based) — ver Open Questions.

### D6. Provider AWS para Floci via `endpoints{}` + `skip_*`
`envs/local` configura region `us-east-1`, credenciais `test/test`,
`skip_credentials_validation`, `skip_requesting_account_id`, `skip_metadata_api_check`,
`s3_use_path_style`, e `endpoints{}` para ec2/ecs/elbv2/ssm/iam/sts/ecr/logs →
`localhost:4566`.
- **Por quê**: é o contrato do Floci (idêntico ao LocalStack) para IaC local.

### D7. State local nesta fase
Backend local (arquivo) em `envs/local`.
- **Por quê**: `bootstrap` (S3+DynamoDB) está fora de escopo; simplicidade para dev local.
- **Evolução**: quando `bootstrap` existir, o backend pode apontar para o S3 do Floci.

### D8. Imagens via ECR do Floci
As apps são publicadas em um repositório ECR emulado (Floci usa `registry:2`) antes do
deploy; as task definitions referenciam a URI do ECR local.
- **Por quê**: o ECS do Floci puxa imagens como a AWS real; um `docker build` + `push` para
  o ECR emulado fecha o ciclo. Um script/passo de bootstrap de imagem acompanha o ambiente.

## Risks / Trade-offs

- **Fidelidade do ECS/Fargate no Floci** → o ECS roda via Docker real, mas nuances de
  Fargate (networking `awsvpc`, ENIs, IAM task role) podem divergir da AWS. Mitigação:
  tratar `envs/local` como validação de *forma* do Terraform e fumaça de deploy, não como
  paridade total; a validação definitiva fica para `envs/prod` numa fase futura.
- **ALB in-process do Floci** → o roteamento HTTP real pode ter limites; o health check
  `/actuator/health` depende de a task subir de fato. Mitigação: validar primeiro com um
  serviço mais simples se necessário, e checar `ecs list-tasks`/logs.
- **NAT/egress no emulador** → tasks em subnet privada podem não ter egress real no Floci;
  puxar imagem do ECR local pode depender de rede Docker, não do NAT. Mitigação: o pull de
  imagem no Floci ocorre via Docker API, não pelo NAT emulado.
- **`s3_use_path_style` e endpoints parciais** → se algum serviço não estiver no
  `endpoints{}`, o provider tenta a AWS real e falha. Mitigação: enumerar explicitamente
  todos os serviços usados.
- **Drift entre CIDRs da referência e o escopo de 6 subnets** → ao remover o tier de
  databases, os CIDRs `10.0.51-53` ficam livres. Mitigação: documentar a reserva para o
  futuro `rds-postgres`.

## Migration Plan

1. Subir o Floci localmente (`floci start` ou `docker compose` com `floci/floci:latest`,
   com o socket do Docker montado para os serviços Docker-real).
2. `modules/networking` → `terraform apply` em `envs/local`; validar VPC/subnets/NAT/SSM.
3. Build + push das imagens das apps para o ECR do Floci.
4. `modules/ecs-cluster` + `modules/ecs-service` (2x) → aplicar; validar cluster, ALB,
   services e health checks.
5. Fumaça: `curl` no DNS/endpoint do ALB e inspeção via AWS CLI apontando para
   `localhost:4566`.

**Rollback**: `terraform destroy` em `envs/local` (ambiente descartável); nenhum recurso
real de nuvem é tocado. State local pode ser apagado para recomeçar.

## Open Questions

- Regra de roteamento do ALB entre command e query: path-based (`/command/*`, `/query/*`)
  ou host-based? Default proposto: path-based, com `/actuator/health` liberado por serviço.
- As apps precisam de banco (Postgres) no ambiente local do ECS? Opções: apontar para o
  Postgres do `infra/local/postgres` (rede Docker) ou subir RDS emulado no Floci. Fora do
  escopo estrito desta mudança, mas afeta o boot das tasks — resolver antes do passo 4.
- `SPRING_PROFILES_ACTIVE` no ambiente Floci: `local` (reaproveita config das apps) vs
  `prod` (mais fiel ao alvo). Default proposto: `local` para casar com as apps hoje.
