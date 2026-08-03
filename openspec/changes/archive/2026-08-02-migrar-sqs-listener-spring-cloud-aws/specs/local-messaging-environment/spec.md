## MODIFIED Requirements

### Requirement: Fila SQS de eventos de autorização

O root `local-messaging` SHALL provisionar uma fila SQS standard chamada
`SQS-eventos-autorizacao`, acompanhada de uma DLQ `SQS-eventos-autorizacao-dlq` e de uma
`redrive_policy` associando as duas. Nenhuma fila SHALL ser provisionada sem DLQ, nem em
ambiente local.

A fila SHALL declarar explicitamente seu `visibility_timeout_seconds`, com valor
**60 segundos** — dimensionado acima do pior caso de processamento de uma única mensagem
(produce síncrono no Kafka, com todos os caminhos de tempo limitados conforme
`publicacao-eventos-kafka`), com margem. O valor NÃO SHALL ficar implícito no default do
serviço: o dimensionamento é uma decisão de projeto acoplada aos timeouts do producer e
precisa ser legível no código de infraestrutura.

O `maxReceiveCount` da `redrive_policy` SHALL ser **10**, elevando o orçamento de retry de
~90 segundos (30s × 3, configuração anterior) para ~10 minutos. O orçamento maior é seguro
porque payload inválido é confirmado na primeira tentativa de entrega pela aplicação e
nunca o consome — apenas falhas de infraestrutura consomem tentativas, e para essas o
tempo adicional é justamente o que evita que uma indisponibilidade transitória de Kafka
esvazie a fila na DLQ.

Ambos os valores SHALL ser expostos como variáveis Terraform, e a mesma calibração SHALL
ser refletida no provisionamento de ambientes não-locais.

#### Scenario: Fila existe após apply

- **WHEN** o apply é concluído
- **THEN** `aws --endpoint-url http://localhost:4566 sqs list-queues` lista a URL
  `http://localhost:4566/000000000000/SQS-eventos-autorizacao`
- **AND** lista também a URL da DLQ `SQS-eventos-autorizacao-dlq`

#### Scenario: Visibility timeout explícito na fila

- **WHEN** os atributos da fila são consultados após o apply
- **THEN** `VisibilityTimeout` é `60`
- **AND** o valor provém de uma variável Terraform declarada, não do default do serviço

#### Scenario: Redrive policy com orçamento recalibrado

- **WHEN** os atributos da fila são consultados após o apply
- **THEN** a `RedrivePolicy` aponta para o ARN da DLQ `SQS-eventos-autorizacao-dlq`
- **AND** o `maxReceiveCount` é `10`

#### Scenario: Indisponibilidade transitória de Kafka não esvazia a fila na DLQ

- **WHEN** o broker Kafka fica indisponível por 5 minutos e mensagens permanecem na fila
  sendo reentregues
- **THEN** as mensagens não atingem o `maxReceiveCount`
- **AND** voltam a ser processadas com sucesso quando o broker retorna, sem intervenção
  manual de redrive

#### Scenario: Indisponibilidade prolongada ainda materializa o incidente na DLQ

- **WHEN** o broker Kafka permanece indisponível além do orçamento de retry
- **THEN** as mensagens são movidas para a DLQ
- **AND** o incidente fica visível para investigação, em vez de reentregar indefinidamente
