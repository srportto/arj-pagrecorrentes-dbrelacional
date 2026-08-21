# padronizacao-logging

## Purpose

Padronizar o log das 5 aplicações do monorepo em JSON estruturado, com correlação por `traceId`
propagado via MDC do SLF4J — tanto para requisições HTTP quanto para mensagens consumidas via
SQS/Kafka. Capability criada a partir da mudança `padronizar-logging-json-traceid`.

## Requirements

### Requirement: Log de aplicação sai em JSON estruturado em todo ambiente

Cada uma das 5 aplicações do monorepo (`contratocommand`, `contratoquery`, `autorizacaostatus-producer`, `eventos-consumer`, `temporiza-autorizacao`) SHALL configurar `logging.structured.format.console: logstash` no `application.yaml`, ativo em todo profile (incluindo `local`), sem introduzir dependência de logging além do já embutido no Spring Boot.

#### Scenario: Console emite JSON em qualquer profile
- **WHEN** qualquer uma das 5 aplicações é iniciada, em qualquer profile ativo (`local`, `hml`, `prod`)
- **THEN** cada linha de log emitida no console é um objeto JSON válido, com pelo menos os campos
  `@timestamp`, `level`, `logger_name` e `message`

#### Scenario: Nenhuma dependência de logging nova é adicionada
- **WHEN** o `pom.xml` de cada uma das 5 aplicações é inspecionado após a mudança
- **THEN** nenhuma dependência de encoder/formatter de log (ex.: `logstash-logback-encoder`) foi
  adicionada — o JSON estruturado vem do suporte nativo do Spring Boot

### Requirement: Requisição HTTP é correlacionada por traceId no MDC

Toda aplicação que expõe endpoint HTTP (`contratocommand`, `contratoquery`) SHALL popular um `traceId` no MDC do SLF4J no início de cada requisição, reaproveitando o valor recebido no cabeçalho `X-Trace-Id` quando presente e não vazio, ou gerando um novo `UUID` caso contrário. O `traceId` SHALL ser removido do MDC ao final da requisição, com sucesso ou falha.

#### Scenario: traceId gerado quando ausente no header
- **WHEN** uma requisição HTTP chega a `contratocommand` ou `contratoquery` sem o cabeçalho
  `X-Trace-Id`
- **THEN** todas as linhas de log emitidas durante o processamento dessa requisição carregam o mesmo
  campo `traceId`, com um valor UUID gerado pela própria aplicação

#### Scenario: traceId reaproveitado quando presente no header
- **WHEN** uma requisição HTTP chega com o cabeçalho `X-Trace-Id` preenchido
- **THEN** todas as linhas de log emitidas durante o processamento dessa requisição carregam o campo
  `traceId` com exatamente o valor recebido no cabeçalho

#### Scenario: MDC não vaza entre requisições
- **WHEN** duas requisições HTTP consecutivas são processadas pela mesma thread do pool do servidor
- **THEN** a segunda requisição não herda o `traceId` da primeira — cada uma tem seu próprio valor,
  correto para o seu próprio processamento

### Requirement: Mensagem consumida é correlacionada por traceId no MDC

Toda aplicação cujo ponto de entrada é um listener de mensageria (`autorizacaostatus-producer` e `temporiza-autorizacao` via SQS; `eventos-consumer` via Kafka) SHALL popular um `traceId` no MDC do SLF4J no início do processamento de cada mensagem, reaproveitando um identificador de correlação já presente no atributo/header da mensagem quando existir, ou gerando um novo `UUID` caso contrário. O `traceId` SHALL ser removido do MDC ao final do processamento da mensagem, com sucesso ou falha.

#### Scenario: traceId por mensagem SQS
- **WHEN** `autorizacaostatus-producer` ou `temporiza-autorizacao` processam uma mensagem SQS
- **THEN** todas as linhas de log emitidas durante o processamento dessa mensagem carregam o mesmo
  campo `traceId`
- **AND** o MDC é limpo ao final do processamento, com sucesso ou falha, antes da thread do pool de
  consumo ser reaproveitada

#### Scenario: traceId por registro Kafka
- **WHEN** `eventos-consumer` processa um `ConsumerRecord` do tópico Kafka
- **THEN** todas as linhas de log emitidas durante o processamento desse registro carregam o mesmo
  campo `traceId`
- **AND** o MDC é limpo ao final do processamento, com sucesso ou falha, antes da thread do listener
  ser reaproveitada
