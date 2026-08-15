# consumo-eventos-autorizacao

## ADDED Requirements

### Requirement: Aplicação listener enxuta baseada no modelo do monorepo

O monorepo SHALL conter a aplicação `apps/autorizacaostatus-producer`, criada a partir
da `contratocommand` e do modelo arquitetural hexagonal de
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
aplicação. Erros consecutivos de recebimento (ex.: Floci fora do ar) SHALL aplicar
backoff entre tentativas, com log claro da causa.

#### Scenario: Loop inicia e para com a aplicação
- **WHEN** a aplicação inicia
- **THEN** o loop de polling começa a receber mensagens da fila
- **AND** no shutdown da aplicação o loop encerra sem deixar thread pendurada

#### Scenario: Emulador indisponível
- **WHEN** o Floci está fora do ar durante o polling
- **THEN** a aplicação não encerra: loga o erro e tenta novamente após backoff

### Requirement: Log de consumo com sucesso e ack da mensagem

Para cada mensagem consumida, a aplicação SHALL registrar um log de sucesso contendo a
representação da entidade recebida (o body JSON do evento) e, somente após o log, dar
ack via `DeleteMessage`. Em caso de erro no processamento, o ack NÃO SHALL ser enviado,
deixando a mensagem retornar à fila após o visibility timeout (semântica
at-least-once).

#### Scenario: Consumo com sucesso
- **WHEN** uma mensagem com o JSON da autorização chega à fila
- **THEN** a aplicação loga o consumo com sucesso incluindo a representação da entidade
- **AND** remove a mensagem da fila (`DeleteMessage`)

#### Scenario: Falha no processamento não dá ack
- **WHEN** ocorre um erro antes da conclusão do processamento da mensagem
- **THEN** a mensagem não é removida da fila
- **AND** volta a ficar disponível após o visibility timeout
