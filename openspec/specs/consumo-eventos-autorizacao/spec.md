# consumo-eventos-autorizacao

## Purpose

TBD — capacidade criada a partir da mudança `add-eventos-autorizacao-sns-sqs`. Descreve
a aplicação `apps/autorizacaostatus-producer`, que consome eventos de estados de
autorização publicados pelo `arj-contratocommand` via SQS.

## Requirements

### Requirement: Aplicação listener enxuta baseada no modelo do monorepo

O monorepo SHALL conter a aplicação `apps/autorizacaostatus-producer`, criada a partir
da `arj-contratocommand` e do modelo arquitetural hexagonal de
`docs/arquitetura/based-java-aplication.md`, com Spring Boot 4.0.7, Java 25, pacote
raiz `br.com.srportto.autorizacaostatusproducer` e porta `8082`. A aplicação NÃO SHALL
depender de JPA/PostgreSQL nem expor endpoints REST de negócio — apenas o Actuator
(`/actuator/health`). Os profiles `local` (defaults do Floci) e `prod` (configuração
via variáveis de ambiente) SHALL ser suportados.

#### Scenario: Aplicação sobe sem banco
- **WHEN** `mvn spring-boot:run` é executado em `apps/autorizacaostatus-producer` sem
  nenhum PostgreSQL disponível
- **THEN** a aplicação inicia com sucesso na porta 8082
- **AND** `/actuator/health` responde `200 (UP)`

#### Scenario: Defaults locais do Floci
- **WHEN** a aplicação roda com o profile `local` (default de desenvolvimento)
- **THEN** ela consome a fila `http://localhost:4566/000000000000/SQS-eventos-autorizacao`
  na região `us-east-1` com credenciais estáticas de emulador, sem configuração manual

### Requirement: Consumo da fila via long polling com SDK v2

A aplicação SHALL consumir a fila `SQS-eventos-autorizacao` com o AWS SDK v2
(`SqsClient`), sem Spring Cloud AWS, em um loop de long polling
(`WaitTimeSeconds = 20`) executado em virtual thread iniciada por um componente de
ciclo de vida do Spring (`SmartLifecycle`), com encerramento gracioso no stop da
aplicação. O `ReceiveMessage` NÃO SHALL solicitar message attributes: o tipo do evento
é derivado pela ponte a partir do campo `status` do payload JSON
(`TipoEventoAutorizacao.porStatus`), tornando o attribute SQS `tipoEvento`
desnecessário para o processamento. Erros consecutivos de recebimento (ex.: Floci fora
do ar) SHALL aplicar backoff entre tentativas, com log claro da causa.

#### Scenario: Loop inicia e para com a aplicação
- **WHEN** a aplicação inicia
- **THEN** o loop de polling começa a receber mensagens da fila
- **AND** no shutdown da aplicação o loop encerra sem deixar thread pendurada

#### Scenario: Emulador indisponível
- **WHEN** o Floci está fora do ar durante o polling
- **THEN** a aplicação não encerra: loga o erro e tenta novamente após backoff

#### Scenario: Processamento independe do attribute tipoEvento
- **WHEN** uma mensagem é recebida — com ou sem o attribute SQS `tipoEvento`
- **THEN** o processamento segue normalmente, com o tipo do evento derivado do campo
  `status` do body pela ponte

### Requirement: Log de consumo com sucesso e ack da mensagem

O processamento de cada mensagem consumida SHALL consistir em produzir o evento
correspondente no tópico Kafka `eventos-autorizacao` (conforme
`publicacao-eventos-kafka`). O ack (`DeleteMessage`) SHALL ocorrer somente após a
confirmação do broker Kafka, precedido de um log de sucesso contendo o body do evento e
a key produzida.

Falhas SHALL ser classificadas em duas categorias com tratamentos distintos:
- **Retryable** (Kafka ou Schema Registry indisponível, timeout de produção): o ack NÃO
  SHALL ser enviado — a mensagem retorna à fila após o visibility timeout (semântica
  at-least-once, retry por conta da fila).
- **Não-retryable** (body JSON malformado, conversão para Avro impossível): a aplicação
  SHALL registrar log ERROR contendo o body completo da mensagem e em seguida SHALL dar
  ack, descartando a mensagem conscientemente — o retry seria inútil e, sem redrive
  policy na fila, causaria loop infinito de reentrega.

#### Scenario: Consumo com sucesso vira produção Kafka
- **WHEN** uma mensagem com o JSON da autorização chega à fila e o broker Kafka
  confirma a produção do evento
- **THEN** a aplicação loga o sucesso incluindo o body e a key produzida
- **AND** remove a mensagem da fila (`DeleteMessage`)

#### Scenario: Falha retryable não dá ack
- **WHEN** a produção no Kafka falha por indisponibilidade ou timeout
- **THEN** a mensagem não é removida da fila
- **AND** volta a ficar disponível após o visibility timeout

#### Scenario: Falha não-retryable descarta com log
- **WHEN** o body da mensagem não pode ser desserializado ou convertido para Avro
- **THEN** um log ERROR registra o body completo da mensagem
- **AND** a mensagem recebe ack e é removida da fila
