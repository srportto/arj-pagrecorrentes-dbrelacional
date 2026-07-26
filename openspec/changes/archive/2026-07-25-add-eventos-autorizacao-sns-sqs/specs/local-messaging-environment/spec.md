# local-messaging-environment

## ADDED Requirements

### Requirement: Root Terraform de mensageria isolado

A infraestrutura de mensageria local SHALL residir em um root Terraform próprio
(`infra/envs/local-messaging/`), com state local e provider AWS apontado para o Floci
(`http://localhost:4566`), seguindo o mesmo padrão de configuração de
`infra/envs/local/providers.tf` (credenciais fake, `skip_credentials_validation`,
`skip_requesting_account_id`, `skip_metadata_api_check`). Aplicar esse root NÃO SHALL
criar, alterar ou depender de recursos do root `infra/envs/local` (VPC/ECS).

#### Scenario: Apply independente do ambiente ECS
- **WHEN** `terraform init && terraform apply` é executado em
  `infra/envs/local-messaging/` com o Floci no ar
- **THEN** apenas os recursos de mensageria são criados
- **AND** nenhum container ECS, VPC ou ALB é provisionado ou modificado

#### Scenario: Endpoints direcionados ao emulador
- **WHEN** o Terraform de `local-messaging` é aplicado
- **THEN** os endpoints dos serviços usados (sns, sqs, sts) apontam para
  `http://localhost:4566` e nenhuma credencial AWS real é exigida

### Requirement: Tópico SNS de estados de autorização

O root `local-messaging` SHALL provisionar um tópico SNS standard chamado
`sns-estados-autorizacao`.

#### Scenario: Tópico existe após apply
- **WHEN** o apply é concluído
- **THEN** `aws --endpoint-url http://localhost:4566 sns list-topics` lista o ARN
  `arn:aws:sns:us-east-1:000000000000:sns-estados-autorizacao`

### Requirement: Fila SQS de eventos de autorização

O root `local-messaging` SHALL provisionar uma fila SQS standard chamada
`SQS-eventos-autorizacao`.

#### Scenario: Fila existe após apply
- **WHEN** o apply é concluído
- **THEN** `aws --endpoint-url http://localhost:4566 sqs list-queues` lista a URL
  `http://localhost:4566/000000000000/SQS-eventos-autorizacao`

### Requirement: Subscription SNS para SQS com entrega crua

O root `local-messaging` SHALL criar uma subscription do tópico
`sns-estados-autorizacao` com protocolo `sqs` apontando para a fila
`SQS-eventos-autorizacao`, com `raw_message_delivery = true`, e uma queue policy que
autorize o serviço SNS (`sns.amazonaws.com`) a fazer `sqs:SendMessage` na fila,
condicionada ao ARN do tópico.

#### Scenario: Mensagem publicada chega crua na fila
- **WHEN** uma mensagem JSON é publicada no tópico `sns-estados-autorizacao`
- **THEN** a mensagem aparece na fila `SQS-eventos-autorizacao`
- **AND** o body recebido é exatamente o JSON publicado, sem envelope SNS
  (`Type`, `TopicArn`, `Message`)

#### Scenario: Outputs expõem os identificadores
- **WHEN** o apply é concluído
- **THEN** o root expõe como outputs o ARN do tópico e a URL da fila, para uso na
  configuração das aplicações
