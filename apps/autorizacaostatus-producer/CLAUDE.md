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
1. [SqsEventoAutorizacaoListener.java](src/main/java/br/com/srportto/autorizacaostatusproducer/entrypoint/sqs/SqsEventoAutorizacaoListener.java) — adapter de ENTRADA: método `@SqsListener` (Spring Cloud AWS), só delega ao use case
2. [SqsListenerContainerFactoryConfig.java](src/main/java/br/com/srportto/autorizacaostatusproducer/shared/config/SqsListenerContainerFactoryConfig.java) — concorrência (`maxConcurrentMessages`), shutdown gracioso do container e registro do error handler
3. [SqsEventoAutorizacaoErrorInterceptor.java](src/main/java/br/com/srportto/autorizacaostatusproducer/entrypoint/sqs/SqsEventoAutorizacaoErrorInterceptor.java) — ponto único de classificação de falha do consumo (retryable/não-retryable), equivalente ao `ApiExceptionHandler` do lado REST
4. [ProcessarEventoAutorizacaoUseCase.java](src/main/java/br/com/srportto/autorizacaostatusproducer/application/eventos/ProcessarEventoAutorizacaoUseCase.java) — orquestra: desserializa, valida, converte para Avro, produz no Kafka
5. [AutorizacaoEventoPayloadValidator.java](src/main/java/br/com/srportto/autorizacaostatusproducer/application/eventos/AutorizacaoEventoPayloadValidator.java) — valida os campos obrigatórios do `.avsc` antes de converter/produzir
6. [EventoAutorizacaoConverter.java](src/main/java/br/com/srportto/autorizacaostatusproducer/application/eventos/EventoAutorizacaoConverter.java) — payload JSON → record Avro `EventoAutorizacao`
7. [IdempotenciaKeyGenerator.java](src/main/java/br/com/srportto/autorizacaostatusproducer/application/eventos/IdempotenciaKeyGenerator.java) — key SHA-256 (id_autorizacao + data_hora_ultima_atlz)
8. [PublicadorEventoAutorizacao.java](src/main/java/br/com/srportto/autorizacaostatusproducer/application/eventos/PublicadorEventoAutorizacao.java) — porta de SAÍDA da ponte
9. [KafkaEventoAutorizacaoProducer.java](src/main/java/br/com/srportto/autorizacaostatusproducer/application/eventos/KafkaEventoAutorizacaoProducer.java) — adapter de SAÍDA que implementa a porta (produce síncrono)
10. [AutorizacaoEventoPayload.java](src/main/java/br/com/srportto/autorizacaostatusproducer/application/eventos/AutorizacaoEventoPayload.java) — espelho do payload publicado pelo `arj-contratocommand`
11. [SqsListenerHealthIndicator.java](src/main/java/br/com/srportto/autorizacaostatusproducer/entrypoint/sqs/SqsListenerHealthIndicator.java) — estado do consumo no `/actuator/health`, via `MessageListenerContainerRegistry`
12. [EventoAutorizacao.avsc](src/main/resources/avro/EventoAutorizacao.avsc) — schema Avro produzido no Kafka

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
| Spring Cloud AWS | 4.0.0 | `spring-cloud-aws-starter-sqs` — `@SqsListener`, autoconfigura `SqsAsyncClient` (AWS SDK v2 vem transitivo) |
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
entrypoint/sqs/         → SqsEventoAutorizacaoListener (adapter de ENTRADA, método @SqsListener),
                           SqsEventoAutorizacaoErrorInterceptor (classificação central de falha),
                           SqsListenerHealthIndicator
application/eventos/    → ProcessarEventoAutorizacaoUseCase (orquestra), AutorizacaoEventoPayloadValidator,
                           EventoAutorizacaoConverter, IdempotenciaKeyGenerator, AutorizacaoEventoPayload,
                           PublicadorEventoAutorizacao (porta de SAÍDA),
                           KafkaEventoAutorizacaoProducer (adapter de SAÍDA)
domain/enums/           → StatusAutorizacao, TipoEventoAutorizacao (regra de negócio pura)
shared/exceptions/      → EventoAutorizacaoInvalidoException, EventoAutorizacaoKafkaIndisponivelException
shared/config/          → SqsListenerContainerFactoryConfig, KafkaProperties, KafkaProducerClientConfig
```

**Não existe pacote `infrastructure/`** — o modelo hexagonal do monorepo é
`entrypoint` / `application` / `domain` / `shared`, e as setas só apontam para dentro. O
listener SQS é adapter de ENTRADA (`entrypoint/`); o producer Kafka é adapter de SAÍDA e
vive em `application/`, atrás da porta `PublicadorEventoAutorizacao` — o use case depende
da interface, não da classe concreta, e não conhece `org.apache.kafka.*`.

Não há classe própria configurando o `SqsAsyncClient` — ele é autoconfigurado pelo
`spring-cloud-aws-starter-sqs` a partir de `spring.cloud.aws.*` (endpoint, região,
credenciais). `SqsListenerContainerFactoryConfig` só configura a factory do container
(concorrência, timeouts de shutdown, error handler), não o client em si.

`AutorizacaoEventoPayload` fica em `application/eventos/` (não em `domain/`) porque é o
contrato do evento consumido, não uma regra de negócio pura. Já `StatusAutorizacao` e
`TipoEventoAutorizacao` são regra de negócio (grafo de transições) e ficam em
`domain/enums/`, como nas outras três aplicações do monorepo.

### Fluxo de consumo → produção (ponte)

```
SqsEventoAutorizacaoListener.receber(String body)  — método anotado @SqsListener
  (queueNames = "${sqs.queue-url}", factory = eventosAutorizacaoSqsListenerContainerFactory)
  └─ delega direto, sem try/catch:
       └─ ProcessarEventoAutorizacaoUseCase.processar(body)
            ├─ desserializa em AutorizacaoEventoPayload
            ├─ AutorizacaoEventoPayloadValidator → exige os 8 campos obrigatórios do .avsc
            ├─ TipoEventoAutorizacao.porStatus(payload.status()) → deriva o tipo do evento
            ├─ EventoAutorizacaoConverter → record Avro EventoAutorizacao (setScale defensivo)
            ├─ IdempotenciaKeyGenerator → key SHA-256(id_autorizacao + data_hora_ultima_atlz)
            ├─ PublicadorEventoAutorizacao.produzir() → send() SÍNCRONO (get com timeout)
            │    header Kafka "tipoEvento" = tipo derivado do status (sempre presente)
            └─ loga sucesso com idAutorizacao, key e tipoEvento — NUNCA com o body

  método retorna normalmente → container confirma (ack) a mensagem
  método lança exceção       → SqsEventoAutorizacaoErrorInterceptor.handle() decide:
       ├─ cadeia de causas contém EventoAutorizacaoInvalidoException (JSON inválido,
       │    campo obrigatório ausente/nulo, status desconhecido) → log ERROR (messageId,
       │    de MessageHeaders.ID — é o messageId real do SQS) + ENGOLE a exceção → ack
       └─ qualquer outra exceção (Kafka/SR indisponível) → log ERROR + RELANÇA →
            SEM ack, mensagem volta à fila após o visibility timeout

SqsListenerContainerFactoryConfig (shared/config/) — configura o container:
  maxConcurrentMessages=10 (concorrência real por instância — pool de platform threads
    dimensionado pelo próprio framework; não são virtual threads, ver design da migração),
  listenerShutdownTimeout=25s / acknowledgementShutdownTimeout=20s (shutdown gracioso:
    o contexto Spring só destrói SqsAsyncClient/Producer Kafka depois que as execuções em
    voo terminam ou esse tempo se esgota — substitui o join() manual de antes),
  errorHandler = SqsEventoAutorizacaoErrorInterceptor,
  acknowledgementMode = ON_SUCCESS (default: ack no retorno normal do método)

SqsListenerHealthIndicator → /actuator/health, via MessageListenerContainerRegistry
  ├─ registry ativo + container em execução → UP
  ├─ registry ativo + container parado      → DOWN  (outage não passa despercebido)
  └─ registry parado (shutdown)             → UP    (parada intencional não é falha)
```

O produce é **síncrono**: `KafkaEventoAutorizacaoProducer` aguarda a confirmação do
broker (`Future.get()`) antes de retornar. Os timeouts do producer
(`max.block.ms=5s`, `request.timeout.ms=5s`, `delivery.timeout.ms=15s`, mais o timeout
explícito do cliente do Schema Registry `http.connect/read.timeout.ms=3s`) ficam abaixo
do visibility timeout da fila SQS (**60s** — `infra/envs/local-messaging/variables.tf`,
`sqs_visibility_timeout_seconds`) — uma falha de produção se resolve (sucesso ou
exceção) antes de o SQS reentregar a mensagem em processamento. Como o processamento
agora é concorrente (não mais um lote serial), o dimensionamento é sobre o pior caso de
**uma** mensagem, não do lote inteiro.

### Exceções e tratamento de erros

Esta app não tem API REST de negócio, mas tem o equivalente ao `ApiExceptionHandler`
para o escopo de mensageria: **`SqsEventoAutorizacaoErrorInterceptor`**
(`entrypoint/sqs/`), registrado como error handler da
`eventosAutorizacaoSqsListenerContainerFactory`, é o ponto único que classifica toda
exceção lançada pelo processamento de uma mensagem — o método `@SqsListener` não tem
`catch` por tipo de exceção, o container invoca o interceptor automaticamente quando o
método lança. O contrato é por comportamento, não por retorno: **engolir a exceção**
(retornar normalmente) faz o container tratar a mensagem como recuperada e confirmar o
ack; **relançar** mantém a mensagem sem ack. Duas exceções orientam essa classificação:

- **`EventoAutorizacaoInvalidoException`** (`shared/exceptions/`) — não-retryable.
  JSON malformado, **campo obrigatório do schema Avro ausente ou nulo**, conversão para
  Avro impossível, ou `status` desconhecido no payload (usado para derivar `tipoEvento`
  via `TipoEventoAutorizacao.porStatus`). O interceptor loga ERROR com o `messageId` —
  nunca com o body — e **engole** a exceção (descarta conscientemente: retry nunca
  corrigiria um payload malformado).
- **`EventoAutorizacaoKafkaIndisponivelException`** (`shared/exceptions/`) —
  retryable, junto com qualquer outra exceção não mapeada. Broker/Schema Registry
  indisponível ou timeout. O interceptor loga ERROR e **relança** — a mensagem volta à
  fila após o visibility timeout (60s). Se a falha persistir além de
  `maxReceiveCount` tentativas (10), a fila move a mensagem para a DLQ
  `SQS-eventos-autorizacao-dlq` (`redrive_policy` em `infra/envs/local-messaging/`) em
  vez de reentregar para sempre — orçamento de retry de ~10min antes da DLQ.

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
5. **O teste de integração do adaptador exige o Floci no ar** —
   `SqsEventoAutorizacaoListenerIntegrationTest` (`entrypoint/sqs/`) envia mensagens
   reais à fila e observa ack/retenção através do pipeline real do `@SqsListener`, com
   `ProcessarEventoAutorizacaoUseCase` mockado (isola do Kafka). Sem o Floci, essa
   classe falha — mesmo pré-requisito de `mvn spring-boot:run`. A contagem de mensagens
   nos asserts é por **delta** em relação a uma baseline, não por valor absoluto: a
   mensagem do cenário retryable fica em voo pelo visibility timeout inteiro (60s) e não
   é drenável entre testes.
6. **Ack no SQS depende do Kafka, não só do parsing** — diferente da fase anterior
   (log + ack), agora uma mensagem só é confirmada na fila após o Kafka aceitar o
   evento. Sem Kafka no ar, a fila acumula mensagens não confirmadas (retry automático).
7. **Mensagem inválida é descartada, não retida para sempre** — comportamento mudou em
   relação à fase anterior: JSON malformado/dado incompleto agora recebe ack após o log
   de erro, via `SqsEventoAutorizacaoErrorInterceptor` (retry nunca corrigiria um payload
   malformado).
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
    DOWN quando o `MessageListenerContainerRegistry` está ativo mas algum container
    parou fora de um shutdown intencional. Sem ele, um outage total do consumo passaria
    despercebido.
12. **Não adicione `catch` por tipo de exceção de volta no listener** — toda
    classificação retryable/não-retryable é responsabilidade exclusiva de
    `SqsEventoAutorizacaoErrorInterceptor` (`entrypoint/sqs/`), registrado como error
    handler da factory; `SqsEventoAutorizacaoListener.receber()` só chama
    `useCase.processar()`, sem `try/catch`. Duplicar a classificação no listener
    reintroduz o espalhamento que o interceptor existe para evitar.
13. **A fila SQS tem DLQ** (`SQS-eventos-autorizacao-dlq`, `redrive_policy` em
    `infra/envs/local-messaging/`) — uma falha retryable persistente (Kafka fora do ar
    por muito tempo) esgota as 10 tentativas do `maxReceiveCount` (~10min, dado o
    visibility timeout de 60s) e a mensagem cai na DLQ para investigação manual, em vez
    de reentregar para sempre.
14. **`maxConcurrentMessages=10` roda em platform threads, não virtual threads** —
    diferente do restante da aplicação (`spring.threads.virtual.enabled=true`), o
    pipeline de execução do `@SqsListener` no Spring Cloud AWS 4.0.0 exige threads
    criadas pela sua própria factory interna (`MessageExecutionThreadFactory`); um
    `componentsTaskExecutor` customizado com virtual threads é rejeitado
    (`UnsupportedThreadFactoryException`). Não é uma limitação prática — 10 platform
    threads é um custo de recurso trivial — mas não assuma virtual threads ao analisar
    ou calibrar a concorrência deste listener.

## Documentação relacionada

- [design.md da migração para Spring Cloud AWS](../../openspec/changes/migrar-sqs-listener-spring-cloud-aws/design.md) — decisões, riscos e racional dos números (visibility timeout, maxReceiveCount, concorrência) desta arquitetura de consumo
- [design.md da mudança original](../../openspec/changes/archive/2026-07-25-add-eventos-autorizacao-sns-sqs/design.md) — decisões do fluxo original SNS/SQS (log + ack)
- [infra/envs/local-messaging/README.md](../../infra/envs/local-messaging/README.md) — como provisionar o tópico/fila SNS/SQS no Floci
- [infra/local/kafka/README.md](../../infra/local/kafka/README.md) — como subir o Kafka local (broker, Schema Registry, dashboard)

## Checklist antes do commit

- [ ] `mvn test` passa (Floci no ar — exigido pelo teste de integração do listener)
- [ ] `mvn verify` passa (gate de cobertura JaCoCo, mínimo 80%)
- [ ] `mvn clean compile` sem erros (gera as classes Avro em `generate-sources`)
- [ ] Se mudou o payload JSON, conferir consistência com `arj-contratocommand`
- [ ] Se mudou `EventoAutorizacao.avsc`, replicar em `apps/eventos-consumer`
- [ ] Falha retryable continua sendo relançada (sem ack); falha não-retryable continua
      sendo engolida (ack) após o log de erro (não confundir as duas classificações)
- [ ] Nenhum log novo nem mensagem de exceção carrega o body da mensagem (PII)
- [ ] Se acrescentou campo obrigatório ao `.avsc`, incluí-lo em
      `AutorizacaoEventoPayloadValidator`
- [ ] Nenhuma classe nova em `infrastructure/` — o modelo é
      `entrypoint`/`application`/`domain`/`shared`
