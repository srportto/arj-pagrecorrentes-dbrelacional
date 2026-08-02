# CLAUDE.md

> Guia para agentes de IA (Claude Code, Copilot, etc.) trabalharem neste repositório.
> **Este arquivo e `AGENTS.md` são espelhos — mantenha-os idênticos ao editar.**

Ponte SQS → Kafka, em **arquitetura hexagonal**. Consome os eventos de estado de
autorização publicados pelo `arj-contratocommand` (via `sns-estados-autorizacao` →
SQS `SQS-eventos-autorizacao`), converte cada evento para Avro e produz no tópico Kafka
`eventos-autorizacao` (Schema Registry), de forma idempotente. O ack no SQS só ocorre
após a confirmação do broker Kafka.

## Comece por aqui

Leia nesta ordem:
1. [SqsEventoAutorizacaoListener.java](src/main/java/br/com/srportto/autorizacaostatusproducer/entrypoint/sqs/SqsEventoAutorizacaoListener.java) — adapter de ENTRADA (long polling + classificação de falha + ack + shutdown gracioso)
2. [ProcessarEventoAutorizacaoUseCase.java](src/main/java/br/com/srportto/autorizacaostatusproducer/application/eventos/ProcessarEventoAutorizacaoUseCase.java) — orquestra: desserializa, valida, converte para Avro, produz no Kafka
3. [AutorizacaoEventoPayloadValidator.java](src/main/java/br/com/srportto/autorizacaostatusproducer/application/eventos/AutorizacaoEventoPayloadValidator.java) — valida os campos obrigatórios do `.avsc` antes de converter/produzir
4. [EventoAutorizacaoConverter.java](src/main/java/br/com/srportto/autorizacaostatusproducer/application/eventos/EventoAutorizacaoConverter.java) — payload JSON → record Avro `EventoAutorizacao`
5. [IdempotenciaKeyGenerator.java](src/main/java/br/com/srportto/autorizacaostatusproducer/application/eventos/IdempotenciaKeyGenerator.java) — key SHA-256 (id_autorizacao + data_hora_ultima_atlz)
6. [PublicadorEventoAutorizacao.java](src/main/java/br/com/srportto/autorizacaostatusproducer/application/eventos/PublicadorEventoAutorizacao.java) — porta de SAÍDA da ponte
7. [KafkaEventoAutorizacaoProducer.java](src/main/java/br/com/srportto/autorizacaostatusproducer/application/eventos/KafkaEventoAutorizacaoProducer.java) — adapter de SAÍDA que implementa a porta (produce síncrono)
8. [AutorizacaoEventoPayload.java](src/main/java/br/com/srportto/autorizacaostatusproducer/application/eventos/AutorizacaoEventoPayload.java) — espelho do payload publicado pelo `arj-contratocommand`
9. [SqsListenerHealthIndicator.java](src/main/java/br/com/srportto/autorizacaostatusproducer/entrypoint/sqs/SqsListenerHealthIndicator.java) — liveness da thread de polling no `/actuator/health`
10. [EventoAutorizacao.avsc](src/main/resources/avro/EventoAutorizacao.avsc) — schema Avro produzido no Kafka

## Build & Testes

```bash
mvn clean package                            # Compilar + testes + JAR (gera classes Avro em generate-sources)
mvn spring-boot:run                          # Rodar localmente (porta 8082)
mvn test                                     # Todos os testes
```

> **Maven Wrapper**: este app não possui `mvnw`/`mvnw.cmd` — use `mvn` diretamente
> (mesma orientação do `arj-contratocommand` no Windows).

## Pré-requisitos

- **Java 25** (JDK 25+) — usa `public static void main()`; a forma `void main()` do Java 25 está pendente de suporte do maven plugin
- **Sem banco de dados** — esta app não usa JPA/PostgreSQL
- **Floci no ar** com o tópico `sns-estados-autorizacao`, a fila `SQS-eventos-autorizacao`
  e a subscription entre eles já aplicados (`infra/envs/local-messaging/`, ver
  [README](../../infra/envs/local-messaging/README.md))
- **Kafka local no ar** (broker + Schema Registry) via
  [`infra/local/kafka/`](../../infra/local/kafka/README.md) — sem ele, o produce falha
  (retryable) e a mensagem SQS fica retida até o Kafka voltar
- Variáveis de ambiente obrigatórias em `prod`: `AWS_REGION`, `AWS_SQS_QUEUE_URL`,
  `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_SCHEMA_REGISTRY_URL` (no profile `local` há defaults
  apontando para o Floci e para `infra/local/kafka/`)
- Profiles Spring: `local` (padrão de desenvolvimento) e `prod` (deve ser setado
  explicitamente via `SPRING_PROFILES_ACTIVE=prod`)

## Stack

| Componente | Versão | Notas |
|---|---|---|
| Java | 25 | `void main()` pendente do maven plugin |
| Spring Boot | 4.0.7 | Web MVC (só para o Actuator), Actuator |
| AWS SDK v2 | 2.49.0 | `software.amazon.awssdk:sqs` — sem Spring Cloud AWS |
| kafka-clients | 3.7.1 | Producer Kafka puro — sem spring-kafka |
| Avro | 1.11.3 | `avro-maven-plugin` gera `EventoAutorizacao` a partir de `src/main/resources/avro/EventoAutorizacao.avsc` |
| kafka-avro-serializer | 7.7.1 (Confluent) | Serialização Avro + integração com o Schema Registry |
| Lombok | 1.18.40 | **sem nenhum uso no código-fonte** — a dependência ainda está no `pom.xml`, mas nenhuma classe a importa; candidata a remoção |

## Endpoints reais

| Método | Caminho | Descrição |
|--------|---------|-----------|
| GET | `/actuator/health` | Health-check (Actuator). → 200 (UP) |

> **Não há endpoints REST de negócio** — esta app não expõe API própria, apenas consome
> a fila SQS e produz no Kafka em background.

## Arquitetura (hexagonal)

```
entrypoint/sqs/         → SqsEventoAutorizacaoListener (adapter de ENTRADA, SmartLifecycle),
                           SqsListenerHealthIndicator
application/eventos/    → ProcessarEventoAutorizacaoUseCase (orquestra), AutorizacaoEventoPayloadValidator,
                           EventoAutorizacaoConverter, IdempotenciaKeyGenerator, AutorizacaoEventoPayload,
                           PublicadorEventoAutorizacao (porta de SAÍDA),
                           KafkaEventoAutorizacaoProducer (adapter de SAÍDA)
domain/enums/           → StatusAutorizacao, TipoEventoAutorizacao (regra de negócio pura)
shared/exceptions/      → EventoAutorizacaoInvalidoException, EventoAutorizacaoKafkaIndisponivelException
shared/config/          → AwsProperties, SqsClientConfig, KafkaProperties, KafkaProducerClientConfig
```

**Não existe pacote `infrastructure/`** — o modelo hexagonal do monorepo é
`entrypoint` / `application` / `domain` / `shared`, e as setas só apontam para dentro. O
listener SQS é adapter de ENTRADA (`entrypoint/`); o producer Kafka é adapter de SAÍDA e
vive em `application/`, atrás da porta `PublicadorEventoAutorizacao` — o use case depende
da interface, não da classe concreta, e não conhece `org.apache.kafka.*`.

`AutorizacaoEventoPayload` fica em `application/eventos/` (não em `domain/`) porque é o
contrato do evento consumido, não uma regra de negócio pura. Já `StatusAutorizacao` e
`TipoEventoAutorizacao` são regra de negócio (grafo de transições) e ficam em
`domain/enums/`, como nas outras três aplicações do monorepo.

### Fluxo de consumo → produção (ponte)

```
SqsEventoAutorizacaoListener (SmartLifecycle, entrypoint/sqs/)
  └─ start(): inicia virtual thread → loopDeConsumo()
       └─ while (running) { pollOnce() }  ← envolto em catch (Throwable): Error não mata a thread
            └─ pollOnce(): ReceiveMessage (long polling, WaitTimeSeconds=20, MaxNumberOfMessages=10)
                 └─ processarEDarAck() por mensagem:
                      ├─ ProcessarEventoAutorizacaoUseCase.processar(body)
                      │    ├─ desserializa em AutorizacaoEventoPayload
                      │    ├─ AutorizacaoEventoPayloadValidator → exige os 8 campos obrigatórios do .avsc
                      │    ├─ TipoEventoAutorizacao.porStatus(payload.status()) → deriva o tipo do evento
                      │    ├─ EventoAutorizacaoConverter → record Avro EventoAutorizacao (setScale defensivo)
                      │    ├─ IdempotenciaKeyGenerator → key SHA-256(id_autorizacao + data_hora_ultima_atlz)
                      │    ├─ PublicadorEventoAutorizacao.produzir() → send() SÍNCRONO (get com timeout)
                      │    │    header Kafka "tipoEvento" = tipo derivado do status (sempre presente)
                      │    └─ loga sucesso com idAutorizacao, key e tipoEvento — NUNCA com o body
                      └─ ack (DeleteMessage) OU descarte, conforme classificação da falha:
                           ├─ sucesso → ack
                           ├─ EventoAutorizacaoInvalidoException (JSON inválido, campo obrigatório
                           │    ausente/nulo, status desconhecido) → log ERROR (messageId) + ack
                           └─ qualquer outra exceção (Kafka/SR indisponível) → SEM ack (retry via visibility timeout)
  └─ stop(): sinaliza parada, interrompe E AGUARDA (join, até 30s) a thread encerrar

SqsListenerHealthIndicator → /actuator/health
  ├─ listener ativo + thread viva  → UP
  ├─ listener ativo + thread morta → DOWN  (outage não passa despercebido)
  └─ listener parado (shutdown)    → UP    (parada intencional não é falha)
```

O produce é **síncrono**: `KafkaEventoAutorizacaoProducer` aguarda a confirmação do
broker (`Future.get()`) antes de retornar. Os timeouts do producer
(`max.block.ms=5s`, `request.timeout.ms=5s`, `delivery.timeout.ms=15s`) ficam abaixo do
visibility timeout da fila SQS (30s, default) — uma falha de produção se resolve
(sucesso ou exceção) antes de o SQS reentregar a mensagem, evitando duplicidade
sistemática de mensagens "em voo".

### Exceções e tratamento de erros

Esta app não tem `ApiExceptionHandler` — não há API REST de negócio para tratar erros
HTTP. Duas exceções orientam a classificação de falha no listener:

- **`EventoAutorizacaoInvalidoException`** (`shared/exceptions/`) — não-retryable.
  JSON malformado, **campo obrigatório do schema Avro ausente ou nulo**, conversão para
  Avro impossível, ou `status` desconhecido no payload (usado para derivar `tipoEvento`
  via `TipoEventoAutorizacao.porStatus`). O listener loga ERROR com o `messageId` — nunca
  com o body — e dá ack (descarta conscientemente: retry seria inútil e, sem redrive
  policy na fila, causaria loop infinito).
- **`EventoAutorizacaoKafkaIndisponivelException`** (`shared/exceptions/`) —
  retryable. Broker/Schema Registry indisponível ou timeout. O listener loga ERROR e
  **não** dá ack — a mensagem volta à fila após o visibility timeout.

**Nenhum log nem mensagem de exceção carrega o body da mensagem.** O payload contém dado
pessoal (`id_pessoa_pagadora`, `id_pessoa_devedora`, `id_pessoa_recebedora`, `valor`,
`descricao`, `metadados`); a mensagem é identificada por `messageId`, `idAutorizacao`,
`key` e `tipoEvento`.

## Armadilhas críticas

1. **Porta 8082** — diferente de `arj-contratocommand` (8080), `arj-contratoquery`
   (8081) e `eventos-consumer` (8083).
2. **Sem banco de dados** — não adicione JPA/Postgres aqui; se precisar persistir algo,
   isso é uma mudança de escopo desta app.
3. **`AutorizacaoEventoPayload` é um espelho manual** do payload equivalente em
   `arj-contratocommand` (`application/eventos/AutorizacaoEventoPayload.java`) — os dois
   não compartilham código; se o schema do evento mudar lá, replique aqui.
4. **`EventoAutorizacao.avsc` também é um espelho manual**, replicado em
   `apps/eventos-consumer/src/main/resources/avro/`. Mudou o schema aqui? Replique lá
   também — não há módulo Avro compartilhado no monorepo (mesma decisão do payload
   JSON). Ambos ficam em `src/main/resources/avro` (não `src/main/avro`), empacotados
   no JAR como insumo de documentação — o runtime não os lê, quem governa o schema é o
   Schema Registry.
5. **`pollOnce()` e `processarEDarAck()` são package-private de propósito** — permitem
   testar o adapter sem precisar rodar a thread real de polling.
6. **Ack no SQS depende do Kafka, não só do parsing** — diferente da fase anterior
   (log + ack), agora uma mensagem só é confirmada na fila após o Kafka aceitar o
   evento. Sem Kafka no ar, a fila acumula mensagens não confirmadas (retry automático).
7. **Mensagem inválida é descartada, não retida para sempre** — comportamento mudou em
   relação à fase anterior: JSON malformado/dado incompleto agora recebe ack após o log
   de erro (a fila não tem redrive policy; reter para sempre só gera ruído).
8. **`enableDecimalLogicalType=true`** no `avro-maven-plugin` — sem isso, os campos
   decimais são gerados como `ByteBuffer`, não `BigDecimal`, quebrando o conversor.
9. **`tipoEvento` não é mais lido do attribute SQS** — o listener não solicita
   `messageAttributeNames` no `ReceiveMessage`; `ProcessarEventoAutorizacaoUseCase`
   deriva o header Kafka `tipoEvento` do campo `status` do payload
   (`TipoEventoAutorizacao.porStatus`), sempre presente. `StatusAutorizacao` e
   `TipoEventoAutorizacao` (`domain/enums/`) são espelhos manuais dos mesmos enums do
   `arj-contratocommand`.
10. **O builder Avro NÃO valida `null` explícito** — ele só valida a *ausência* de `set`
    (`fieldSetFlags()`). Como o converter sempre chama os setters, um campo obrigatório
    nulo produziria um `SpecificRecord` inválido em silêncio, que só falharia adiante e
    fora da classificação (NPE na key, ou `SerializationException` **síncrona** dentro de
    `send()`, que o `catch` do producer não alcança) — resultando em reentrega infinita.
    Por isso `AutorizacaoEventoPayloadValidator` roda **antes** de converter, gerar key ou
    produzir. Não remova essa validação nem a mova para depois.
11. **`/actuator/health` reflete o consumidor** — `SqsListenerHealthIndicator` reporta
    DOWN quando o listener está ativo mas a thread de polling morreu. Sem ele, um outage
    total do consumo passaria despercebido (a flag `running` continua `true`).

## Documentação relacionada

- [design.md da mudança](../../openspec/changes/archive/2026-07-25-add-eventos-autorizacao-sns-sqs/design.md) — decisões do fluxo original SNS/SQS (log + ack)
- [infra/envs/local-messaging/README.md](../../infra/envs/local-messaging/README.md) — como provisionar o tópico/fila SNS/SQS no Floci
- [infra/local/kafka/README.md](../../infra/local/kafka/README.md) — como subir o Kafka local (broker, Schema Registry, dashboard)

## Checklist antes do commit

- [ ] `mvn test` passa
- [ ] `mvn clean compile` sem erros (gera as classes Avro em `generate-sources`)
- [ ] Se mudou o payload JSON, conferir consistência com `arj-contratocommand`
- [ ] Se mudou `EventoAutorizacao.avsc`, replicar em `apps/eventos-consumer`
- [ ] Falha retryable continua sem dar ack; falha não-retryable continua dando ack após
      o log de erro (não confundir as duas classificações)
- [ ] Nenhum log novo nem mensagem de exceção carrega o body da mensagem (PII)
- [ ] Se acrescentou campo obrigatório ao `.avsc`, incluí-lo em
      `AutorizacaoEventoPayloadValidator`
- [ ] Nenhuma classe nova em `infrastructure/` — o modelo é
      `entrypoint`/`application`/`domain`/`shared`
