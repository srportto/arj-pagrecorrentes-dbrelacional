# Proposal: add-eventos-autorizacao-kafka

## Why

O fluxo de eventos de autorização hoje termina na `autorizacaostatus-producer`, que apenas
consome a fila SQS e loga — o nome "producer" ainda não corresponde a nada. Esta mudança
introduz o Kafka como backbone de eventos do monorepo: a app passa a ser uma ponte
SQS → Kafka produzindo eventos Avro governados por Schema Registry, com uma consumidora
downstream e um ambiente local completo (broker, registry, dashboard) para desenvolver e
observar o fluxo de ponta a ponta (mensagens produzidas, consumidas e lag).

## What Changes

- Novo ambiente Kafka local standalone via Docker Compose dedicado: broker cp-kafka
  (KRaft, nó único), cp-schema-registry, dashboard Kafbat UI em localhost e tópico
  `eventos-autorizacao` (3 partições) criado explicitamente por init-container.
- A `autorizacaostatus-producer` mantém o consumo da fila `SQS-eventos-autorizacao` e
  passa a produzir cada evento consumido no tópico Kafka `eventos-autorizacao`, em Avro
  (SpecificRecord + Schema Registry), com produce síncrono e ack no SQS somente após a
  confirmação do broker.
- Idempotência de negócio: a key da mensagem Kafka é o hash SHA-256 de
  `id_autorizacao` + `data_hora_ultima_atlz`, que identifica unicamente cada transição
  de estado; `enable.idempotence=true` cobre a camada de transporte.
- O message attribute `tipoEvento` (CRIACAO/CANCELAMENTO), hoje ignorado pela listener
  SQS, passa a ser lido e propagado como header Kafka.
- **BREAKING** (comportamento): mensagens não-retryable (JSON malformado, conversão Avro
  impossível) passam a ser descartadas com log ERROR + ack, em vez de retornarem à fila
  indefinidamente. Falhas retryable (Kafka/Schema Registry indisponível) continuam sem
  ack — a fila faz o retry.
- Nova aplicação `apps/eventos-consumer` (porta 8083): consome o tópico via spring-kafka
  com ack manual, loga o corpo do evento consumido e comita o offset.

## Capabilities

### New Capabilities

- `local-kafka-environment`: ambiente Kafka local standalone (broker KRaft, Schema
  Registry, Kafbat UI, tópico `eventos-autorizacao`) provisionado por Docker Compose
  dedicado, isolado do compose de apps e do Terraform de mensageria SQS.
- `publicacao-eventos-kafka`: produção de eventos Avro no tópico `eventos-autorizacao`
  pela ponte `autorizacaostatus-producer` — schema espelhando a linha da tabela,
  key de idempotência, header `tipoEvento`, semântica de falha retryable/não-retryable.
- `consumo-eventos-kafka`: aplicação `apps/eventos-consumer` que consome o tópico com
  spring-kafka, loga o corpo do evento e comita o offset manualmente após o log.

### Modified Capabilities

- `consumo-eventos-autorizacao`: a `autorizacaostatus-producer` deixa de ser consumidora
  terminal (log + ack) e vira ponte — o ack passa a depender da produção no Kafka; o
  `ReceiveMessage` passa a solicitar o attribute `tipoEvento`; falhas não-retryable
  passam a descartar com log em vez de reter a mensagem na fila.
- `monorepo-organization`: o monorepo ganha a aplicação `apps/eventos-consumer` e o
  compose dedicado de Kafka em `infra/local/kafka/`.

## Impact

- **Apps**: `apps/autorizacaostatus-producer` (novas dependências kafka-clients +
  kafka-avro-serializer + avro-maven-plugin; novo adapter de produção; mudança na
  semântica de ack); novo `apps/eventos-consumer` (Spring Boot 4, spring-kafka, Avro).
- **Infra local**: novo `infra/local/kafka/docker-compose.yml` (3 containers + init de
  tópico). O Terraform `infra/envs/local-messaging` (SNS/SQS) permanece intocado.
- **Contratos**: novo schema Avro `EventoAutorizacao` (namespace
  `br.com.srportto.eventos.autorizacao`), espelhado manualmente nas duas apps (mesmo
  precedente do espelho do payload JSON); subject `eventos-autorizacao-value`.
- **Fluxo fim a fim**: contratocommand → SNS → SQS → ponte → Kafka → eventos-consumer.
  Nada do fluxo SNS/SQS existente é removido.
- **Docs**: CLAUDE.md/AGENTS.md e README da producer refletem o papel de ponte; nova
  app documentada no padrão do monorepo.
