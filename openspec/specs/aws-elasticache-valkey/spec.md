# aws-elasticache-valkey

## Purpose

Descreve o módulo Terraform reutilizável que provisiona o ElastiCache Valkey consumido pela
aplicação `temporiza-autorizacao` em ambientes AWS.

## Requirements

### Requirement: Módulo Terraform reutilizável de ElastiCache Valkey

A infraestrutura AWS SHALL prover um módulo reutilizável em `infra/modules/` que provisione
um cluster ElastiCache com engine **Valkey**, seguindo o padrão dos módulos existentes
(`variables.tf`, `outputs.tf`, `README.md`, sem valores de ambiente embutidos). O módulo
SHALL expor como saída o endpoint de conexão consumido pela aplicação
`temporiza-autorizacao`, e SHALL receber a rede na qual o cluster reside como entrada, sem
criar VPC ou subnets próprias.

#### Scenario: Módulo parametrizado, não específico de ambiente
- **WHEN** o módulo é inspecionado
- **THEN** ele declara variáveis para rede, dimensionamento e identificação
- **AND** não contém identificadores fixos de um ambiente específico

#### Scenario: Endpoint exposto como saída
- **WHEN** o módulo é aplicado por um root de ambiente
- **THEN** o endpoint de conexão do cluster é exposto como output

#### Scenario: Rede vem de fora
- **WHEN** o módulo é aplicado
- **THEN** nenhuma VPC, subnet ou internet gateway é criado pelo próprio módulo

### Requirement: Cluster com backup automático e acesso restrito à rede privada

O cluster provisionado SHALL ter `snapshot_retention_limit` maior que zero — o ElastiCache
gerenciado não expõe configuração de append-only file ao usuário, então a durabilidade entre
reinícios de nó é feita via **snapshot automático**, não AOF (essa é uma característica do
Valkey/Redis autogerenciado, coberta pela capacidade `local-valkey-environment` para o
ambiente local, não por este módulo). O acesso SHALL ser restrito à rede privada, sem
exposição pública: o security group do cluster SHALL aceitar tráfego na porta do Valkey
apenas a partir dos serviços da aplicação, e NÃO SHALL permitir origem irrestrita.

#### Scenario: Backup automático habilitado em produção
- **WHEN** a configuração do cluster provisionado é inspecionada
- **THEN** `snapshot_retention_limit` é maior que zero

#### Scenario: Sem exposição pública
- **WHEN** as regras de entrada do security group do cluster são inspecionadas
- **THEN** nenhuma regra permite origem irrestrita
- **AND** o cluster não recebe endereço público

#### Scenario: Nenhum recurso criado no ambiente local
- **WHEN** os roots de ambiente local são aplicados
- **THEN** este módulo não é instanciado
