# publicacao-eventos-kafka

## ADDED Requirements

### Requirement: Evento Avro governado por Schema Registry

A `autorizacaostatus-producer` SHALL produzir cada evento consumido da fila SQS no
tópico Kafka `eventos-autorizacao` como Avro `SpecificRecord` gerado por
`avro-maven-plugin` a partir do schema `EventoAutorizacao` (namespace
`br.com.srportto.eventos.autorizacao`), serializado com o `KafkaAvroSerializer` da
Confluent contra o Schema Registry (subject `eventos-autorizacao-value`,
`auto.register.schemas=true` no profile `local`).

O schema SHALL espelhar a linha da tabela `autorizacoes`: campos em snake_case com os
nomes das colunas; nulabilidade conforme o DDL (`["null", X]` com `"default": null`
para colunas anuláveis); `local-timestamp-micros` para colunas `timestamp` sem fuso;
`date` para colunas DATE; `string` com logicalType `uuid` para UUIDs;
`decimal(17,2)` (bytes) para `valor` e `valor_limite`, aplicando `setScale(2)` na
conversão; `long` para `tipo_produto`; `int` para os indicadores; e `string` com o
JSON serializado para `metadados`.

#### Scenario: Evento publicado em Avro válido
- **WHEN** uma mensagem JSON da fila é processada com sucesso
- **THEN** um evento Avro `EventoAutorizacao` é produzido no tópico
  `eventos-autorizacao`, decodificável via Schema Registry
- **AND** os campos espelham as chaves/valores do JSON consumido

#### Scenario: Decimal com scale divergente não estoura
- **WHEN** o JSON traz `valor` com scale diferente de 2 (ex.: `150.5`)
- **THEN** a conversão aplica scale 2 e o evento é produzido normalmente

#### Scenario: Timestamps sem fuso preservados
- **WHEN** o JSON traz `data_hora_ultima_atlz` com precisão de microssegundos
- **THEN** o campo Avro `local-timestamp-micros` preserva o valor sem truncamento nem
  conversão de fuso

### Requirement: Key de idempotência por transição de estado

A key da mensagem Kafka SHALL ser o hash SHA-256 (hex) da concatenação de
`id_autorizacao` com `data_hora_ultima_atlz`, calculado a partir dos campos tipados do
payload desserializado usando formatter fixo (`ISO_LOCAL_DATE_TIME`) — nunca a partir
da string JSON crua. O producer SHALL usar `enable.idempotence=true` e `acks=all`.

#### Scenario: Reentrega gera a mesma key
- **WHEN** a mesma mensagem SQS é entregue duas vezes (at-least-once) e produzida duas
  vezes no Kafka
- **THEN** as duas mensagens Kafka possuem key idêntica, permitindo deduplicação por
  consumidores

#### Scenario: Transições distintas geram keys distintas
- **WHEN** a criação e o cancelamento da mesma autorização são processados
- **THEN** os dois eventos possuem keys diferentes (o par id + data de última
  atualização identifica a transição, não a autorização)

### Requirement: Header tipoEvento propagado

A ponte SHALL repassar o valor do message attribute SQS `tipoEvento` (`CRIACAO` ou
`CANCELAMENTO`) como header Kafka `tipoEvento`, mantendo o body como representação pura
da linha. Se o attribute não estiver presente na mensagem, o header SHALL ser omitido
sem falhar o processamento.

#### Scenario: Header presente no evento produzido
- **WHEN** uma mensagem com attribute `tipoEvento=CRIACAO` é processada
- **THEN** o evento Kafka carrega o header `tipoEvento` com valor `CRIACAO`

#### Scenario: Attribute ausente não bloqueia
- **WHEN** uma mensagem sem o attribute `tipoEvento` é processada
- **THEN** o evento é produzido normalmente sem o header

### Requirement: Produce síncrono com timeouts abaixo do visibility timeout

A produção SHALL ser síncrona (aguardar a confirmação do broker antes do ack no SQS) e
os timeouts do producer SHALL somar menos que o visibility timeout da fila (30s) — na
ordem de `max.block.ms=5s`, `request.timeout.ms=5s` e `delivery.timeout.ms=15s` — para
que uma falha de produção se resolva (sucesso ou exceção) antes de o SQS reentregar a
mensagem.

#### Scenario: Falha rápida com broker fora do ar
- **WHEN** o Kafka está indisponível e uma mensagem é processada
- **THEN** o produce falha com exceção antes de 30s decorridos
- **AND** nenhuma segunda entrega da mesma mensagem encontra a primeira ainda em voo
