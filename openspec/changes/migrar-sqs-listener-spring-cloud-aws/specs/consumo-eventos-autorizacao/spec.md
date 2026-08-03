## ADDED Requirements

### Requirement: Consumo da fila via @SqsListener do Spring Cloud AWS

A aplicação SHALL consumir a fila `SQS-eventos-autorizacao` por meio da anotação
`@SqsListener` do Spring Cloud AWS 4.0.0 (`io.awspring.cloud:spring-cloud-aws-starter-sqs`),
compatível com Spring Boot 4.x. O método anotado SHALL residir em `entrypoint/sqs/` —
adaptador de ENTRADA, no mesmo nível arquitetural de um `@RestController` — e NÃO SHALL
residir em um pacote `infrastructure/`, que não faz parte do modelo hexagonal do monorepo.

O método anotado SHALL apenas delegar o body da mensagem ao
`ProcessarEventoAutorizacaoUseCase`, sem regra de negócio própria, sem gerenciamento de
ciclo de vida e sem `try/catch` por tipo de exceção.

A aplicação NÃO SHALL mais instanciar `SqsClient` manualmente nem manter loop de polling,
thread de polling própria, backoff manual ou rede de segurança contra `Throwable` — todos
esses passam a ser responsabilidade do container do framework. A conexão com o emulador
Floci no profile `local` SHALL ser configurada por propriedades `spring.cloud.aws.*`
(endpoint override e credenciais estáticas), não por bean de configuração próprio.

O `ReceiveMessage` NÃO SHALL solicitar message attributes: o tipo do evento é derivado
pela ponte a partir do campo `status` do payload JSON (`TipoEventoAutorizacao.porStatus`),
tornando o attribute SQS `tipoEvento` desnecessário para o processamento.

#### Scenario: Consumo inicia com a aplicação

- **WHEN** a aplicação inicia com o Floci no ar
- **THEN** o container do Spring Cloud AWS começa a receber mensagens da fila
  `SQS-eventos-autorizacao`
- **AND** nenhuma thread de polling é criada pelo código da aplicação

#### Scenario: Emulador indisponível não derruba a aplicação

- **WHEN** o Floci está fora do ar durante o consumo
- **THEN** a aplicação não encerra: o container registra o erro e continua tentando
- **AND** nenhuma lógica de backoff própria da aplicação é necessária

#### Scenario: Processamento independe do attribute tipoEvento

- **WHEN** uma mensagem é recebida — com ou sem o attribute SQS `tipoEvento`
- **THEN** o processamento segue normalmente, com o tipo do evento derivado do campo
  `status` do body pela ponte

#### Scenario: Método do listener não classifica exceção

- **WHEN** o corpo do método anotado com `@SqsListener` é inspecionado
- **THEN** ele apenas delega ao use case
- **AND** não contém `catch` por tipo de exceção nem decisão de ack inline

### Requirement: Processamento concorrente de mensagens por instância

O container SHALL processar mensagens de forma concorrente dentro de uma mesma instância,
com o número máximo de mensagens em processamento simultâneo configurável
(`maxConcurrentMessages`). A aplicação NÃO SHALL processar um lote recebido de forma
estritamente serial.

A execução concorrente SHALL usar virtual threads (Java 25), apropriadas porque cada
mensagem passa a maior parte de seu tempo bloqueada em I/O aguardando a confirmação
síncrona do broker Kafka.

O `Producer` Kafka SHALL ser compartilhado entre as execuções concorrentes — é thread-safe
por design e o compartilhamento permite o agrupamento de registros em batch, ausente no
processamento serial.

#### Scenario: Lote é processado concorrentemente

- **WHEN** um lote de mensagens é recebido da fila
- **THEN** múltiplas mensagens do lote são processadas simultaneamente, até o limite
  configurado
- **AND** uma mensagem lenta não impede o processamento das demais

#### Scenario: Concorrência é configurável

- **WHEN** o valor de `maxConcurrentMessages` é alterado na configuração
- **THEN** o número de mensagens em processamento simultâneo por instância reflete o novo
  valor, sem alteração de código

## MODIFIED Requirements

### Requirement: Log de consumo com sucesso e ack da mensagem

O processamento de cada mensagem consumida SHALL consistir em produzir o evento
correspondente no tópico Kafka `eventos-autorizacao` (conforme
`publicacao-eventos-kafka`). O ack SHALL ocorrer somente após a confirmação do broker
Kafka, precedido de um log de sucesso. A garantia oferecida SHALL permanecer
**at-least-once**: a aplicação NÃO SHALL adotar ack independente da confirmação do Kafka
(produce assíncrono seguido de ack no retorno), que trocaria falha visível e recuperável
por perda silenciosa de evento.

O ack SHALL ser expresso pelo **retorno normal** do método `@SqsListener`, e a retenção da
mensagem pela **propagação de exceção** a partir dele — a aplicação NÃO SHALL emitir
`DeleteMessage` explicitamente.

Nenhum log, em nenhum nível, SHALL conter o body bruto da mensagem: o payload carrega
dado pessoal (`id_pessoa_pagadora`, `id_pessoa_devedora`, `id_pessoa_recebedora`,
`valor`, `descricao`, `metadados`). Os logs SHALL identificar a mensagem por
`idAutorizacao`, `key` produzida, `tipoEvento` e `messageId` do SQS.

A proibição SHALL valer também para a **cadeia de causas** das exceções, que o log de
descarte imprime por inteiro na stack trace: exceções de bibliotecas de terceiros
(desserialização JSON, conversão e serialização Avro) embutem o *valor* do campo
rejeitado em suas mensagens, e os campos tipados como `UUID` e `BigDecimal` são
justamente os que carregam PII. A aplicação NÃO SHALL propagar essas exceções como
`cause` — SHALL registrar apenas o caminho do campo (sem o valor) e o nome da classe da
exceção original.

A classificação de falha SHALL permanecer concentrada em um **ponto único**
(`SqsEventoAutorizacaoErrorInterceptor`, registrado como error handler central do
container), nunca distribuída em `catch` dentro do método do listener. O contrato do
interceptor SHALL ser expresso por engolir ou relançar a exceção, e não por valor de
retorno booleano interpretado pelo listener:

- **Retryable** (Kafka ou Schema Registry indisponível, timeout de produção): o
  interceptor SHALL relançar a exceção — a mensagem não é confirmada e retorna à fila
  após o visibility timeout.
- **Não-retryable** (body JSON malformado, campo obrigatório do schema Avro ausente ou
  nulo, `status` desconhecido, conversão para Avro impossível): o interceptor SHALL
  registrar log ERROR identificando a mensagem e a causa — sem o body — e em seguida
  engolir a exceção, resultando em ack e descarte consciente. O retry seria inútil e
  consumiria o orçamento de tentativas até a DLQ sem chance de sucesso.

#### Scenario: Consumo com sucesso vira produção Kafka

- **WHEN** uma mensagem com o JSON da autorização chega à fila e o broker Kafka
  confirma a produção do evento
- **THEN** a aplicação loga o sucesso com `idAutorizacao`, `key` e `tipoEvento`
- **AND** o log NÃO contém o body da mensagem
- **AND** o método do listener retorna normalmente, resultando na remoção da mensagem da fila

#### Scenario: Falha retryable não dá ack

- **WHEN** a produção no Kafka falha por indisponibilidade ou timeout
- **THEN** o interceptor relança a exceção
- **AND** a mensagem não é removida da fila
- **AND** volta a ficar disponível após o visibility timeout

#### Scenario: Falha não-retryable descarta com log sem body

- **WHEN** o body da mensagem não pode ser desserializado ou convertido para Avro
- **THEN** um log ERROR registra o `messageId` e a causa da rejeição
- **AND** o log NÃO contém o body da mensagem
- **AND** o interceptor engole a exceção e a mensagem é removida da fila

#### Scenario: Payload inválido não consome o orçamento de retry

- **WHEN** uma mensagem com payload inválido é recebida
- **THEN** ela é confirmada na primeira tentativa de entrega
- **AND** seu `ApproximateReceiveCount` nunca alcança o `maxReceiveCount` da fila
- **AND** ela nunca é movida para a DLQ

#### Scenario: Valor malformado em campo PII não vaza pela cadeia de causas

- **WHEN** uma mensagem traz valor inválido num campo tipado como `UUID` ou `BigDecimal`
  (ex.: `id_pessoa_pagadora`) e a biblioteca de desserialização rejeita a coerção
- **THEN** a exceção resultante identifica o **caminho do campo** e a classe da exceção
  original
- **AND** nem a mensagem nem qualquer causa encadeada contêm o conteúdo do campo

### Requirement: Saúde do consumidor refletida no health-check

A aplicação SHALL expor um `HealthIndicator` que reflita o estado real do consumo da
fila, consultando o registro de containers do Spring Cloud AWS
(`MessageListenerContainerRegistry`). Enquanto o container do listener estiver em
execução, `/actuator/health` SHALL responder `200 (UP)`. Se o container tiver deixado de
executar fora de um shutdown intencional da aplicação, o indicador SHALL reportar `DOWN`,
derrubando o health geral — um consumidor morto NÃO SHALL ser reportado como saudável.

O indicador NÃO SHALL depender de liveness de thread própria da aplicação, que deixa de
existir com a adoção do `@SqsListener`.

#### Scenario: Consumidor ativo reporta UP

- **WHEN** a aplicação está no ar com o container do listener em execução
- **THEN** `/actuator/health` responde `200 (UP)`
- **AND** o indicador do listener reporta `UP`

#### Scenario: Container parado fora de shutdown derruba o health

- **WHEN** o container do listener não está mais em execução e a aplicação não está em
  processo de shutdown
- **THEN** o indicador do listener reporta `DOWN`
- **AND** `/actuator/health` responde `503 (DOWN)`

#### Scenario: Listener parado por shutdown não é falha

- **WHEN** a aplicação está em processo de shutdown e o container já foi parado
- **THEN** o indicador não reporta `DOWN` por esse motivo — parada intencional não é
  outage

## REMOVED Requirements

### Requirement: Consumo da fila via long polling com SDK v2

**Reason**: O requisito prescrevia explicitamente AWS SDK v2 puro ("sem Spring Cloud
AWS"), loop de long polling manual, virtual thread própria iniciada por `SmartLifecycle`,
`join` com timeout calibrado, backoff manual e resiliência a `Throwable`. Essa prescrição
existia porque não havia versão do Spring Cloud AWS compatível com Spring Boot 4; o
Spring Cloud AWS 4.0.0 removeu essa restrição. Toda a mecânica descrita passa a ser
responsabilidade do container do framework, e mantê-la especificada obrigaria a
reimplementar à mão o que o framework entrega por configuração.

**Migration**: Substituído por "Consumo da fila via @SqsListener do Spring Cloud AWS" e
"Processamento concorrente de mensagens por instância". O encerramento gracioso — antes
garantido pelo `join()` explícito antes da destruição dos beans — passa a ser garantido
pelos timeouts de shutdown do container (`listenerShutdownTimeout` e
`acknowledgementShutdownTimeout`), configurados na `SqsMessageListenerContainerFactory`. A
propriedade `spring.lifecycle.timeout-per-shutdown-phase`, calibrada manualmente contra o
timeout do listener, deixa de ser necessária.
