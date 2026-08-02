## Why

A `autorizacaostatus-producer` é a única aplicação do monorepo que usa um pacote
`infrastructure/` — que não existe no modelo hexagonal do projeto (`entrypoint` /
`application` / `domain` / `shared`). Isso produz uma violação da regra de dependência:
`application/eventos/ProcessarEventoAutorizacaoUseCase` importa a classe **concreta**
`infrastructure.kafka.KafkaEventoAutorizacaoProducer`, enquanto
`infrastructure/sqs/SqsEventoAutorizacaoListener` importa `application.eventos.*` — setas
nos dois sentidos, sem porta nem inversão de dependência. A app irmã `eventos-consumer`
foi realinhada nesta semana (`entrypoint/kafka/`, `domain/enums/`) e as duas apps hoje
divergem no modelo de camadas.

A auditoria do agent `java-especialista` confirmou o desalinhamento e encontrou **cinco
achados críticos que vivem exatamente nos arquivos a serem movidos** — corrigi-los depois
duplicaria revisão e teste dos mesmos arquivos. O mais grave: o builder gerado pelo Avro
**não valida `null` explícito** (só valida ausência de `set`), então um payload com campo
obrigatório nulo produz um `EventoAutorizacao` inválido silenciosamente, que depois falha
de forma **não classificada** — por `NullPointerException` no `IdempotenciaKeyGenerator`
ou por `SerializationException` síncrona dentro de `producer.send()` (que o `catch` do
producer não cobre, pois só trata `Execution`/`Timeout`/`Interrupted`). Ambos caem no
`catch (Exception)` genérico do listener → sem ack → **reentrega infinita**, numa fila que
não tem redrive policy.

## What Changes

### Realinhamento de camadas

- `infrastructure/sqs/SqsEventoAutorizacaoListener` → `entrypoint/sqs/` (adaptador de
  ENTRADA, mesmo nível de um `@RestController`)
- `infrastructure/kafka/KafkaEventoAutorizacaoProducer` → `application/eventos/`
  (adaptador de SAÍDA), acessado pelo use case através de uma **porta** (interface) em vez
  da classe concreta
- `StatusAutorizacao` e `TipoEventoAutorizacao` → `domain/enums/` (regra de negócio pura,
  sem Spring) — a app passa a ter camada `domain/`
- `EventoAutorizacaoInvalidoException` e `EventoAutorizacaoKafkaIndisponivelException` →
  `shared/exceptions/`
- O pacote `infrastructure/` deixa de existir

### Correções bloqueantes absorvidas (mesmos arquivos)

- **Campo obrigatório nulo vira falha classificada**: validação explícita dos cinco campos
  obrigatórios de tipo objeto do `.avsc` (`id_autorizacao`, `data_fim_vigencia`,
  `data_hora_inclusao`, `data_hora_ultima_atlz`, `codigo_canal_contratacao`) logo após a
  desserialização, e `IdempotenciaKeyGenerator.gerar()` sob o mesmo try/catch de
  classificação. Elimina a reentrega infinita.
- **BREAKING (log)**: o payload JSON bruto sai dos logs de `INFO` e `ERROR` — ele carrega
  PII (`id_pessoa_pagadora`, `id_pessoa_devedora`, `id_pessoa_recebedora`, `valor`,
  `descricao`, `metadados`). Passa a logar apenas `idAutorizacao`, `key` e `tipoEvento`.
  Ferramentas que hoje dependem do body no log deixam de encontrá-lo.
- **Shutdown realmente gracioso**: `SmartLifecycle.stop()` passa a fazer `join()` com
  timeout, para não fechar `SqsClient`/`Producer` com mensagem em voo (hoje: evento no
  Kafka sem ack no SQS → duplicata na próxima subida)
- **Outage deixa de ser silencioso**: `pollOnce()` captura `Throwable` (não só
  `Exception`) e um `HealthIndicator` novo reflete a liveness da thread de polling — hoje
  `/actuator/health` responde `UP` com o consumidor morto
- **Teste do cenário crítico**: cobertura do payload com campo obrigatório nulo no fluxo
  completo, travando a correção acima

### Explicitamente fora de escopo

Registrados para uma mudança seguinte, sem bloquear esta: `AUTO_REGISTER_SCHEMAS` por
profile; revisão de timeouts e paralelismo do lote de 10 mensagens contra o visibility
timeout; granularidade dos `catch (RuntimeException)`; duplicata por ack falho
pós-produce-OK; `ObjectMapper` injetado; MDC/`traceId`; auth SASL/mTLS do Kafka e Schema
Registry em prod; remoção do Lombok; `logging.structured.format.console`; atualização do
`CLAUDE.md`.

## Capabilities

### New Capabilities

Nenhuma. A mudança realinha e corrige capacidades existentes.

### Modified Capabilities

- `consumo-eventos-autorizacao`: o listener passa a residir em `entrypoint/sqs/`; o
  encerramento gracioso passa a aguardar a thread de polling; a resiliência do loop passa
  a cobrir `Throwable`; a saúde do consumidor passa a ser refletida em
  `/actuator/health`; o log de falha não-retryable deixa de conter o body completo.
- `publicacao-eventos-kafka`: o produtor passa a ser adaptador de saída em `application/`
  atrás de uma porta; payload com campo obrigatório nulo passa a ser classificado como
  não-retryable antes do produce; o log de sucesso deixa de conter o body completo.
- `maquina-estados-autorizacao`: remove a exceção que hoje coloca os enums desta app em
  `application/eventos/` — as quatro aplicações passam a usar `domain/enums/`.

## Impact

**Código** — `apps/autorizacaostatus-producer`:

- Movidos: `SqsEventoAutorizacaoListener`, `KafkaEventoAutorizacaoProducer`,
  `StatusAutorizacao`, `TipoEventoAutorizacao`, `EventoAutorizacaoInvalidoException`,
  `EventoAutorizacaoKafkaIndisponivelException` (+ os testes correspondentes)
- Novos: porta de saída do produtor, `HealthIndicator` do listener, validação de campos
  obrigatórios do payload
- Alterados: `ProcessarEventoAutorizacaoUseCase` (validação, porta, logs),
  `SqsEventoAutorizacaoListener` (`join()`, `Throwable`, logs)

**Sem impacto em**: `pom.xml` (nenhuma dependência entra ou sai), schema Avro (`.avsc`
permanece idêntico ao do `eventos-consumer`), contrato do payload SQS, contrato do tópico
Kafka, porta 8082, profiles.

**Documentação**: `apps/autorizacaostatus-producer/CLAUDE.md` e `AGENTS.md` descrevem a
estrutura de pacotes e o fluxo — precisam refletir a nova organização.

**Risco**: baixo. A movimentação é mecânica e coberta pelos 28 testes existentes; as cinco
correções têm teste dedicado. O gate JaCoCo de 80% já é cumprido hoje e deve permanecer.
