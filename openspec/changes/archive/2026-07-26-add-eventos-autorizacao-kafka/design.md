# Design: add-eventos-autorizacao-kafka

## Context

O fluxo de eventos hoje é: `contratocommand` publica no SNS
`sns-estados-autorizacao` após cada commit (criação/cancelamento), o SNS entrega cru na
fila `SQS-eventos-autorizacao`, e a `autorizacaostatus-producer` consome a fila em long
polling (SDK v2 puro, `SmartLifecycle` + virtual thread), loga e dá ack. O message
attribute `tipoEvento` (CRIACAO/CANCELAMENTO) chega à fila mas nunca é lido — o
`ReceiveMessage` atual não solicita `messageAttributeNames`.

Esta mudança estende o fluxo com Kafka: a `autorizacaostatus-producer` vira ponte
SQS → Kafka (finalmente fazendo jus ao nome), produzindo eventos Avro governados por
Schema Registry no tópico `eventos-autorizacao`, e uma nova app `eventos-consumer`
consome o tópico. O ambiente local ganha broker, registry e dashboard via Docker
Compose. Nada do fluxo SNS/SQS é removido.

Restrições herdadas do terreno:
- A fila SQS não tem `visibility_timeout_seconds` configurado (default **30s**) nem
  redrive policy/DLQ — mensagem sem ack retorna indefinidamente a cada 30s.
- O payload JSON espelha exatamente a linha da tabela `autorizacoes` (chaves = colunas);
  o DDL define nulabilidade, `timestamp` sem fuso e `NUMERIC(17,2)` nos valores.
- Apps do monorepo: Java 25, Spring Boot 4.0.7, sem banco na producer, portas
  8080/8081/8082 ocupadas.

## Goals / Non-Goals

**Goals:**
- Ambiente Kafka local standalone com Schema Registry e dashboard (mensagens, consumer
  groups e lag visíveis em localhost).
- Ponte SQS → Kafka com entrega at-least-once fim a fim e idempotência de negócio
  rastreável pela key da mensagem.
- Contrato Avro versionado no Schema Registry espelhando a linha da tabela.
- Consumidora downstream mínima (log + ack) exercitando o caminho completo.

**Non-Goals:**
- Remover ou alterar o fluxo SNS/SQS existente (o Terraform `local-messaging` não muda).
- DLQ/redrive na fila SQS (trade-off aceito; ver Risks).
- Processamento de negócio na `eventos-consumer` (apenas log + commit nesta fase).
- Kafka em ambiente prod/AWS (MSK etc.) — escopo é ambiente local.
- Exactly-once semantics (transações Kafka) — at-least-once + dedup por key basta.

## Decisions

### D1. Stack local: cp-kafka (KRaft) + cp-schema-registry + Kafbat UI

Compose dedicado em `infra/local/kafka/docker-compose.yml` com 3 containers + um
init-container que cria o tópico `eventos-autorizacao` explicitamente (auto-create
desabilitado — tópico é contrato, não efeito colateral).

- *Por que Confluent e não Redpanda?* Fidelidade ao ecossistema de produção (broker
  Kafka real + Schema Registry da Confluent, os mesmos serializers). Redpanda seria mais
  leve (2 containers), mas é reimplementação API-compatível.
- *Por que Kafbat UI?* Decodifica mensagens Avro via Schema Registry e mostra consumer
  groups e lag por partição num painel só — cobre os três requisitos de observação.
- *Por que compose separado do `apps/docker-compose.yml`?* Mesmo precedente de
  isolamento do root Terraform `local-messaging`: subir/derrubar Kafka não deve tocar
  Postgres/apps.

### D2. Tópico com 3 partições

Com key = hash por transição (D4), eventos da mesma autorização podem cair em partições
diferentes — ordenação por autorização **não** é garantida. Três partições tornam esse
trade-off visível no dashboard (distribuição e lag por partição) em vez de mascará-lo
com partição única. Localmente o volume é trivial; o valor é didático e honesto.

### D3. Schema Avro espelhando a linha da tabela

Record `EventoAutorizacao`, namespace `br.com.srportto.eventos.autorizacao`, campos em
snake_case idênticos às colunas (mesma filosofia do payload JSON: o contrato é a linha,
não um objeto Java). Subject `eventos-autorizacao-value` (TopicNameStrategy),
`auto.register.schemas=true` no profile local, compatibilidade BACKWARD (default do SR).

Mapeamentos que exigem cuidado:
- **Nulabilidade vem do DDL**: `id_autorizacao`, `id_particao_conta`, `tipo_produto`,
  `status`, `data_hora_inclusao`, `data_hora_ultima_atlz`, `data_fim_vigencia` e
  `codigo_canal_contratacao` são obrigatórios; todo o resto é `["null", X]` com
  `"default": null` (obrigatório para evolução compatível).
- **`local-timestamp-micros`** para os `timestamp` (sem fuso → `LocalDateTime`), nunca
  `timestamp-millis` (que é instante UTC). Micros = precisão nativa do Postgres, sem
  truncamento no round-trip.
- **`date`** (int) para as colunas DATE; **UUID** como `string` com logicalType `uuid`.
- **`decimal(17,2)`** (bytes) para `valor`/`valor_limite`, com `setScale(2)` defensivo
  na conversão payload → Avro: o serializer exige scale exata e o JSON pode entregar
  `150.5` (scale 1).
- **`tipo_produto`** NUMERIC(6,0) → `long`; INTs de indicador/frequência (`Short` no
  Java) → `int` (Avro não tem short).
- **`metadados`** (coluna JSON livre) → `string` com o JSON serializado. Avro não tem
  tipo JSON arbitrário; custo aceito: aparece como string escapada no dashboard.

Codegen via `avro-maven-plugin` (SpecificRecord) nas **duas** apps, cada uma com sua
cópia do `.avsc` — mesmo precedente do espelho manual do payload JSON entre
contratocommand e producer (decisão deliberada do monorepo: contratos duplicados
explicitamente, sem módulo compartilhado). Alternativa rejeitada: GenericRecord no
consumer (zero duplicação, mas sem tipagem para a evolução da app).

### D4. Idempotência: key = SHA-256(id_autorizacao + data_hora_ultima_atlz)

Cada transição de estado atualiza `data_hora_ultima_atlz`, então o par identifica
unicamente o *evento* (não a autorização). Reentrega do SQS ⇒ mesmo hash ⇒ mesma key —
duplicatas são identificáveis por qualquer consumidor e visíveis no dashboard.

- O hash é calculado dos **campos tipados** após desserialização, com formatter fixo
  (`ISO_LOCAL_DATE_TIME`), nunca da string JSON crua (imune a variação de formatação).
- `enable.idempotence=true` + `acks=all` no producer cobrem a camada de transporte
  (duplicatas de retry interno do client).
- Trade-off aceito: key por transição sacrifica ordenação por autorização em tópico
  multi-partição (criação e cancelamento da mesma autorização podem ser consumidos fora
  de ordem). Alternativa rejeitada: key = `id_autorizacao` + hash em header preservaria
  ordem, mas a decisão foi manter o hash como identidade da mensagem.

### D5. Ponte: produce síncrono, ack no SQS só após confirmação do broker

`send().get(timeout)` dentro do `processarEDarAck` existente; o `DeleteMessage` só
acontece se o broker confirmou. Divergência **deliberada** da filosofia do publisher SNS
do contratocommand ("loga e segue"): lá o publish é acessório à operação REST; na ponte,
entregar o evento é o único trabalho — engolir falha destruiria a razão de existir.

Classificação de falhas:
- **Retryable** (Kafka/Schema Registry indisponível, timeout): sem ack → a mensagem
  volta em 30s (visibility timeout) → retry grátis, com backoff e durabilidade por conta
  da fila. Duplicata produzida no meio (produce ok, crash antes do ack) tem a mesma key.
- **Não-retryable** (JSON malformado, conversão Avro impossível): log ERROR com o body
  completo + ack (**descarte consciente**). Retry seria comprovadamente inútil e, sem
  redrive policy, geraria loop infinito a cada 30s. Muda o comportamento atual da
  listener (que nunca dava ack em erro) — registrado como BREAKING no proposal.

**Timeouts do producer abaixo do visibility timeout (30s)**: `max.block.ms≈5s`,
`request.timeout.ms≈5s`, `delivery.timeout.ms≈15s`. Com os defaults (60s/120s), um
`send()` pendurado ultrapassaria os 30s e o SQS reentregaria a mensagem com a primeira
ainda em voo — produce duplicado sistemático. A ponte falha rápido; o retry lento é
papel da fila.

### D6. tipoEvento propagado como header Kafka

O `ReceiveMessage` passa a solicitar `messageAttributeNames("tipoEvento")` e a ponte
repassa o valor como header Kafka `tipoEvento`. Simetria com o papel do message
attribute no SNS: o body permanece a linha pura (a spec de publicação consagra isso);
metadado de operação viaja fora do payload. Alternativa rejeitada: enum no schema Avro
(quebraria a pureza do body).

### D7. eventos-consumer com spring-kafka e ack manual

Nova app `apps/eventos-consumer` (porta 8083, pacote `br.com.srportto.eventosconsumer`,
sem banco, sem REST de negócio, só Actuator — modelo da casa). Consumo via
`@KafkaListener` com `AckMode.MANUAL`: loga o corpo do evento e então comita o offset.
Group id `eventos-consumer`.

- *Por que spring-kafka e não KafkaConsumer puro?* Quebra deliberada da jurisprudência
  "cliente puro" do listener SQS: menos código, error handling e retry prontos
  (`DefaultErrorHandler`), e o par listener/container é o idioma dominante do
  ecossistema — vale conhecê-lo no monorepo.
- Semântica de erro honesta com o Kafka: offset não comitado **não** devolve a mensagem
  como no SQS — quem reentrega é o `DefaultErrorHandler` (N tentativas via seek, depois
  loga e segue). A spec descreve essa semântica, não a do visibility timeout.
- Desserialização com `specific.avro.reader=true` usando as classes geradas da cópia
  local do `.avsc`.

## Risks / Trade-offs

- [Perda de ordenação por autorização (D4)] → aceito e tornado visível (3 partições no
  dashboard); consumidores que precisarem de ordem deverão reordenar por
  `data_hora_ultima_atlz` ou a key deverá ser revista em mudança futura.
- [Descarte de poison messages (D5)] → o evento não-retryable é perdido; mitigação: log
  ERROR com o body completo permite reprocessamento manual. DLQ real fica como evolução.
- [Sem outbox na origem] → herdado do fluxo SNS existente: se o publish SNS falhar, o
  evento nunca chega ao Kafka. Fora do escopo desta mudança.
- [Espelho manual do .avsc entre as duas apps] → risco de divergência silenciosa;
  mitigação: armadilha documentada nos CLAUDE.md das duas apps (mesmo tratamento do
  espelho do payload JSON hoje).
- [Compose Kafka separado do compose de apps] → dois comandos para subir o ambiente
  completo; mitigação: READMEs com a ordem de subida.
- [Stack Confluent pesada (~1.5 GB RAM)] → aceito em prol da fidelidade; Redpanda fica
  como alternativa documentada se a máquina local sofrer.

## Migration Plan

1. Subir a infra Kafka (compose novo) — independente de tudo.
2. Alterar a ponte (producer) — o fluxo antigo continua funcionando durante o
   desenvolvimento; sem Kafka no ar, mensagens ficam retidas na fila (retryable).
3. Criar a `eventos-consumer` — consome do earliest; nada quebra se subir depois.
4. Rollback: reverter a ponte ao comportamento log + ack (o tópico e a consumer podem
   ficar no ar sem receber nada); o compose Kafka é descartável (`docker compose down -v`).

## Open Questions

- Nenhuma pendente — as decisões D1–D7 foram fechadas em exploração com o usuário.
