## MODIFIED Requirements

### Requirement: Tópico eventos-autorizacao criado explicitamente

O compose SHALL criar os tópicos `eventos-autorizacao` e `eventos-autorizacao.DLT`, ambos com 3
partições, por meio de um init-container (ex.: `kafka-topics --create --if-not-exists`), com a
criação automática de tópicos (`auto.create.topics.enable`) desabilitada no broker. Os tópicos são
contrato explícito, não efeito colateral do primeiro produce.

O tópico `eventos-autorizacao.DLT` é destino do `DeadLetterPublishingRecoverer` da
`eventos-consumer`. Sua ausência NÃO SHALL ser suprida por auto-create — com `auto.create.topics.enable`
desabilitado, a publicação na DLT falharia, o offset não avançaria e a partição ficaria bloqueada
indefinidamente pela mesma mensagem que a DLT deveria isolar.

#### Scenario: Tópicos existem após subir o ambiente
- **WHEN** o compose termina de subir
- **THEN** `kafka-topics --list` no broker inclui `eventos-autorizacao` e `eventos-autorizacao.DLT`
- **AND** ambos os tópicos possuem 3 partições

#### Scenario: Tópico inexistente não é criado por engano
- **WHEN** um producer tenta publicar em um tópico que não existe
- **THEN** o broker rejeita a operação em vez de criar o tópico automaticamente

#### Scenario: Mensagem venenosa alcança a DLT sem travar a partição
- **WHEN** uma mensagem falha o processamento no `eventos-consumer` em todas as tentativas
  configuradas
- **THEN** o `DeadLetterPublishingRecoverer` SHALL publicá-la em `eventos-autorizacao.DLT` com
  sucesso
- **AND** o offset SHALL avançar, liberando a partição para as mensagens seguintes

### Requirement: Compose Kafka dedicado e isolado

A infraestrutura Kafka local SHALL residir em um Docker Compose próprio
(`infra/local/kafka/compose.yaml`), com broker `cp-kafka` em modo KRaft (nó
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
