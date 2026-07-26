# local-kafka-environment

## ADDED Requirements

### Requirement: Compose Kafka dedicado e isolado

A infraestrutura Kafka local SHALL residir em um Docker Compose próprio
(`infra/local/kafka/docker-compose.yml`), com broker `cp-kafka` em modo KRaft (nó
único, sem ZooKeeper), `cp-schema-registry` e o dashboard Kafbat UI. Subir ou derrubar
esse compose NÃO SHALL criar, alterar ou depender de recursos do compose de apps
(`apps/docker-compose.yml`) nem do Terraform de mensageria SQS
(`infra/envs/local-messaging/`).

#### Scenario: Ambiente sobe de forma independente
- **WHEN** `docker compose up -d` é executado em `infra/local/kafka/`
- **THEN** broker, Schema Registry e Kafbat UI iniciam sem exigir Postgres, apps ou
  Floci no ar
- **AND** nenhum recurso do compose de apps ou do Terraform de mensageria é criado ou
  modificado

#### Scenario: Ambiente é descartável
- **WHEN** `docker compose down -v` é executado em `infra/local/kafka/`
- **THEN** todos os containers e volumes do ambiente Kafka são removidos sem afetar os
  demais ambientes locais

### Requirement: Tópico eventos-autorizacao criado explicitamente

O compose SHALL criar o tópico `eventos-autorizacao` com 3 partições por meio de um
init-container (ex.: `kafka-topics --create --if-not-exists`), com a criação automática
de tópicos (`auto.create.topics.enable`) desabilitada no broker. O tópico é contrato
explícito, não efeito colateral do primeiro produce.

#### Scenario: Tópico existe após subir o ambiente
- **WHEN** o compose termina de subir
- **THEN** `kafka-topics --list` no broker inclui `eventos-autorizacao`
- **AND** o tópico possui 3 partições

#### Scenario: Tópico inexistente não é criado por engano
- **WHEN** um producer tenta publicar em um tópico que não existe
- **THEN** o broker rejeita a operação em vez de criar o tópico automaticamente

### Requirement: Schema Registry acessível para as aplicações

O Schema Registry SHALL estar acessível em porta fixa de localhost para registro e
consulta de schemas Avro, usando o broker do próprio compose como storage. O subject
`eventos-autorizacao-value` SHALL ser registrável pelo producer com a estratégia de
compatibilidade default (BACKWARD).

#### Scenario: Registry responde em localhost
- **WHEN** o ambiente está no ar
- **THEN** `GET /subjects` no Schema Registry responde `200`

#### Scenario: Schema registrado aparece no registry
- **WHEN** o producer publica o primeiro evento com `auto.register.schemas=true`
- **THEN** o subject `eventos-autorizacao-value` passa a existir com a versão 1 do
  schema `EventoAutorizacao`

### Requirement: Dashboard de observação em localhost

O Kafbat UI SHALL estar disponível em porta fixa de localhost, conectado ao broker e ao
Schema Registry, permitindo observar: as mensagens do tópico `eventos-autorizacao`
decodificadas via Avro, os consumer groups ativos e o lag por partição.

#### Scenario: Mensagens produzidas são legíveis
- **WHEN** um evento Avro é produzido no tópico e o dashboard é consultado
- **THEN** a mensagem aparece com o payload decodificado (campos legíveis, não bytes)
- **AND** a key e os headers da mensagem são visíveis

#### Scenario: Lag do consumidor é visível
- **WHEN** a `eventos-consumer` está consumindo o tópico
- **THEN** o dashboard lista o group `eventos-consumer` com o lag por partição
