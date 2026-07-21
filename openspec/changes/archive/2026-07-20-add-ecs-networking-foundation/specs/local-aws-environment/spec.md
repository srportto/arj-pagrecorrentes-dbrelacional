## ADDED Requirements

### Requirement: Provider AWS apontado para o Floci

O ambiente `envs/local` SHALL configurar o provider AWS para o emulador Floci,
apontando os endpoints de serviço para `http://localhost:4566`, usando credenciais fake
e desabilitando as validações que exigem AWS real (`skip_credentials_validation`,
`skip_requesting_account_id`, `skip_metadata_api_check`) e habilitando
`s3_use_path_style`.

#### Scenario: Endpoints direcionados ao emulador
- **WHEN** o Terraform de `envs/local` é inicializado
- **THEN** os endpoints dos serviços AWS usados (ec2, ecs, elbv2, ssm, iam, sts, ecr,
  logs) apontam para `http://localhost:4566`

#### Scenario: Sem credenciais reais
- **WHEN** o Terraform de `envs/local` é aplicado
- **THEN** ele autentica com credenciais fake e não requer conta AWS real nem chamadas
  ao metadata service

### Requirement: Composição dos módulos

O ambiente `envs/local` SHALL compor os módulos `networking`, `ecs-cluster` e
`ecs-service` (duas instâncias), passando os outputs de rede como entradas dos módulos
de compute.

#### Scenario: Rede alimenta o compute
- **WHEN** o ambiente é aplicado
- **THEN** o cluster, o ALB e os serviços consomem o `vpc_id` e as subnets produzidos
  pelo módulo `networking`

### Requirement: State local

O ambiente `envs/local` SHALL usar backend de state local (arquivo), sem depender de
backend remoto (S3/DynamoDB) nesta fase.

#### Scenario: State em arquivo local
- **WHEN** o ambiente é inicializado
- **THEN** o Terraform state é mantido localmente, sem exigir bucket S3 ou tabela
  DynamoDB

### Requirement: Publicação de imagens no ECR do Floci

O ambiente `envs/local` SHALL prever a publicação das imagens de
`arj-contratocommand` e `arj-contratoquery` em um repositório ECR emulado pelo Floci
antes do deploy dos serviços, de modo que o ECS consiga puxá-las.

#### Scenario: Imagens disponíveis para o ECS
- **WHEN** os serviços são implantados no ambiente local
- **THEN** as imagens referenciadas pelas task definitions existem no ECR do Floci

### Requirement: Fluxo terraform plan/apply sem conta AWS

O ambiente `envs/local` SHALL permitir executar `terraform init`, `plan` e `apply`
contra o Floci em execução, exigindo versões Terraform `>= 1.10` e provider AWS
`~> 5.x`.

#### Scenario: Apply contra o Floci
- **WHEN** o Floci está em execução em `localhost:4566` e `terraform apply` é executado
  em `envs/local`
- **THEN** a rede, o cluster, o ALB e os serviços são provisionados no emulador sem erro
