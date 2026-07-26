# publicacao-eventos-kafka

## Purpose

TBD — capability criada a partir da mudança `add-eventos-autorizacao-kafka`. Descreve
como a `autorizacaostatus-producer` produz eventos Avro no tópico Kafka
`eventos-autorizacao` a partir das mensagens consumidas da fila SQS.

## Requirements

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

A ponte SHALL preencher o header Kafka `tipoEvento` com o valor derivado do campo
`status` do payload consumido, via `TipoEventoAutorizacao.porStatus(status)` — não mais
repassando o message attribute SQS. Como o campo `status` é obrigatório no payload, o
header SHALL estar presente em todo evento produzido. Um `status` que não corresponda a
nenhum estado conhecido SHALL classificar a mensagem como inválida (não-retryable:
log de erro + ack/descarte, mesma classificação de payload inválido).

#### Scenario: Header derivado do status do payload
- **WHEN** uma mensagem cujo body tem `status=4` (`ATIVA`) é processada
- **THEN** o evento Kafka carrega o header `tipoEvento` com valor `ATIVACAO`

#### Scenario: Attribute SQS é ignorado
- **WHEN** uma mensagem chega com attribute SQS `tipoEvento` divergente do `status` do
  body (ou sem attribute algum)
- **THEN** o header Kafka reflete exclusivamente o valor derivado do `status` do body

#### Scenario: Status desconhecido é descartado como inválido
- **WHEN** uma mensagem cujo body tem `status` fora da faixa 1–8 é processada
- **THEN** a mensagem é classificada como inválida (log de erro + ack), sem produção
  no Kafka

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
