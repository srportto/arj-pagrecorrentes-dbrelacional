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
aplicação. O componente listener SHALL residir em `entrypoint/sqs/` — adaptador de
ENTRADA, no mesmo nível arquitetural de um `@RestController` — e NÃO SHALL residir em
um pacote `infrastructure/`, que não faz parte do modelo hexagonal do monorepo. O
`ReceiveMessage` NÃO SHALL solicitar message attributes: o tipo do evento é derivado
pela ponte a partir do campo `status` do payload JSON
(`TipoEventoAutorizacao.porStatus`), tornando o attribute SQS `tipoEvento`
desnecessário para o processamento.

O encerramento SHALL ser efetivamente gracioso: `stop()` SHALL sinalizar a parada,
interromper a thread de polling e **aguardar seu término** (`join`) por um tempo
limitado antes de retornar, de modo que o contexto Spring não destrua o `SqsClient` nem
o `Producer` Kafka com uma mensagem ainda em processamento. Esgotado o tempo de espera,
o encerramento SHALL prosseguir registrando log de aviso.

O loop SHALL ser resiliente a `Throwable` — não apenas a `Exception` — para que um
`Error` não mate silenciosamente a thread de polling. Erros consecutivos de recebimento
(ex.: Floci fora do ar) SHALL aplicar backoff entre tentativas, com log claro da causa.

#### Scenario: Loop inicia e para com a aplicação
- **WHEN** a aplicação inicia
- **THEN** o loop de polling começa a receber mensagens da fila
- **AND** no shutdown da aplicação o loop encerra sem deixar thread pendurada

#### Scenario: Shutdown aguarda a mensagem em voo
- **WHEN** a aplicação recebe o sinal de shutdown enquanto uma mensagem está sendo
  produzida no Kafka
- **THEN** `stop()` aguarda a thread de polling terminar antes de retornar
- **AND** o `SqsClient` e o `Producer` Kafka só são fechados depois disso

#### Scenario: Shutdown não trava indefinidamente
- **WHEN** a thread de polling não termina dentro do tempo limite de espera do `stop()`
- **THEN** o encerramento prossegue mesmo assim
- **AND** um log de aviso registra que o tempo de espera foi esgotado

#### Scenario: Emulador indisponível
- **WHEN** o Floci está fora do ar durante o polling
- **THEN** a aplicação não encerra: loga o erro e tenta novamente após backoff

#### Scenario: Error não mata o loop em silêncio
- **WHEN** um `Error` (não uma `Exception`) ocorre durante um ciclo de polling
- **THEN** o loop registra a falha e continua operando após backoff, sem encerrar a
  thread de polling silenciosamente

#### Scenario: Processamento independe do attribute tipoEvento
- **WHEN** uma mensagem é recebida — com ou sem o attribute SQS `tipoEvento`
- **THEN** o processamento segue normalmente, com o tipo do evento derivado do campo
  `status` do body pela ponte

### Requirement: Log de consumo com sucesso e ack da mensagem

O processamento de cada mensagem consumida SHALL consistir em produzir o evento
correspondente no tópico Kafka `eventos-autorizacao` (conforme
`publicacao-eventos-kafka`). O ack (`DeleteMessage`) SHALL ocorrer somente após a
confirmação do broker Kafka, precedido de um log de sucesso.

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

Falhas SHALL ser classificadas em duas categorias com tratamentos distintos:
- **Retryable** (Kafka ou Schema Registry indisponível, timeout de produção): o ack NÃO
  SHALL ser enviado — a mensagem retorna à fila após o visibility timeout (semântica
  at-least-once, retry por conta da fila).
- **Não-retryable** (body JSON malformado, campo obrigatório do schema Avro ausente ou
  nulo, `status` desconhecido, conversão para Avro impossível): a aplicação SHALL
  registrar log ERROR identificando a mensagem e a causa — sem o body — e em seguida
  SHALL dar ack, descartando a mensagem conscientemente. O retry seria inútil e, sem
  redrive policy na fila, causaria loop infinito de reentrega.

#### Scenario: Consumo com sucesso vira produção Kafka
- **WHEN** uma mensagem com o JSON da autorização chega à fila e o broker Kafka
  confirma a produção do evento
- **THEN** a aplicação loga o sucesso com `idAutorizacao`, `key` e `tipoEvento`
- **AND** o log NÃO contém o body da mensagem
- **AND** remove a mensagem da fila (`DeleteMessage`)

#### Scenario: Falha retryable não dá ack
- **WHEN** a produção no Kafka falha por indisponibilidade ou timeout
- **THEN** a mensagem não é removida da fila
- **AND** volta a ficar disponível após o visibility timeout

#### Scenario: Falha não-retryable descarta com log sem body
- **WHEN** o body da mensagem não pode ser desserializado ou convertido para Avro
- **THEN** um log ERROR registra o `messageId` e a causa da rejeição
- **AND** o log NÃO contém o body da mensagem
- **AND** a mensagem recebe ack e é removida da fila

#### Scenario: Valor malformado em campo PII não vaza pela cadeia de causas
- **WHEN** uma mensagem traz valor inválido num campo tipado como `UUID` ou `BigDecimal`
  (ex.: `id_pessoa_pagadora`) e a biblioteca de desserialização rejeita a coerção
- **THEN** a exceção resultante identifica o **caminho do campo** e a classe da exceção
  original
- **AND** nem a mensagem nem qualquer causa encadeada contêm o conteúdo do campo

### Requirement: Saúde do consumidor refletida no health-check

A aplicação SHALL expor um `HealthIndicator` que reflita a liveness da thread de
polling do listener SQS. Enquanto o listener estiver ativo e sua thread viva,
`/actuator/health` SHALL responder `200 (UP)`. Se a thread de polling tiver morrido
enquanto o listener ainda se considera ativo, o indicador SHALL reportar `DOWN`,
derrubando o health geral da aplicação — um consumidor morto NÃO SHALL ser reportado
como saudável.

#### Scenario: Consumidor ativo reporta UP
- **WHEN** a aplicação está no ar com o loop de polling em execução
- **THEN** `/actuator/health` responde `200 (UP)`
- **AND** o indicador do listener reporta `UP`

#### Scenario: Thread de polling morta derruba o health
- **WHEN** o listener está marcado como ativo mas sua thread de polling não está mais
  viva
- **THEN** o indicador do listener reporta `DOWN`
- **AND** `/actuator/health` responde `503 (DOWN)`

#### Scenario: Listener parado não é falha
- **WHEN** a aplicação está em processo de shutdown e o listener já foi parado
- **THEN** o indicador não reporta `DOWN` por esse motivo — parada intencional não é
  outage
