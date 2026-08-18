# CLAUDE.md

> Guia para agentes de IA (Claude Code, Copilot, etc.) trabalharem neste repositório.
> **Este arquivo e `AGENTS.md` são espelhos — mantenha-os idênticos ao editar.**

Ponte SQS → Kafka, em **arquitetura hexagonal clássica** (`domain`/`application`/`infrastructure`,
ver `openspec/changes/archive/2026-08-16-hexagonal-classico-autorizacaostatus-producer/`). Consome os eventos de
estado de autorização publicados pelo `contratocommand` (via `sns-estados-autorizacao` →
SQS `SQS-eventos-autorizacao`), converte cada evento para Avro e produz no tópico Kafka
`eventos-autorizacao` (Schema Registry), com uma key SHA-256 determinística que **permite**
deduplicação a jusante — a produção em si não é idempotente ponta a ponta (`enable.idempotence`
só cobre retries internos do producer dentro de uma sessão; o tópico não é compactado por chave),
então a garantia de não processar o mesmo evento duas vezes é responsabilidade de quem consome
`eventos-autorizacao`, não desta app. O ack no SQS só ocorre após a confirmação do broker Kafka.

## Comece por aqui

Leia nesta ordem:
1. [SqsEventoAutorizacaoListener.java](src/main/java/br/com/srportto/autorizacaostatusproducer/infrastructure/messaging/SqsEventoAutorizacaoListener.java) — adapter de ENTRADA: método `@SqsListener` (Spring Cloud AWS); desserializa, valida e converte para o modelo de domínio, delega ao use case
2. [SqsListenerContainerFactoryConfig.java](src/main/java/br/com/srportto/autorizacaostatusproducer/infrastructure/config/SqsListenerContainerFactoryConfig.java) — concorrência (`maxConcurrentMessages`), shutdown gracioso do container e registro do error handler
3. [SqsEventoAutorizacaoErrorInterceptor.java](src/main/java/br/com/srportto/autorizacaostatusproducer/infrastructure/messaging/SqsEventoAutorizacaoErrorInterceptor.java) — ponto único de classificação de falha do consumo (retryable/não-retryable), equivalente ao `ApiExceptionHandler` do lado REST
4. [AutorizacaoEventoPayloadValidator.java](src/main/java/br/com/srportto/autorizacaostatusproducer/infrastructure/messaging/AutorizacaoEventoPayloadValidator.java) — valida os campos obrigatórios do `.avsc` antes de converter/produzir
5. [EventoAutorizacaoConverter.java](src/main/java/br/com/srportto/autorizacaostatusproducer/infrastructure/messaging/EventoAutorizacaoConverter.java) — payload JSON → `domain/model/EventoAutorizacao` (modelo de domínio puro)
6. [EventoAutorizacao.java (domain/model)](src/main/java/br/com/srportto/autorizacaostatusproducer/domain/model/EventoAutorizacao.java) — modelo de domínio puro do evento, sem nenhum import de `org.apache.avro.*` (D2-b)
7. [ProcessarEventoAutorizacaoUseCase.java (domain/port/in)](src/main/java/br/com/srportto/autorizacaostatusproducer/domain/port/in/ProcessarEventoAutorizacaoUseCase.java) — porta de entrada: recebe o evento já tipado
8. [ProcessarEventoAutorizacaoService.java](src/main/java/br/com/srportto/autorizacaostatusproducer/application/usecase/ProcessarEventoAutorizacaoService.java) — orquestra: deriva a chave de idempotência e publica pela porta
9. [IdempotenciaKeyGenerator.java](src/main/java/br/com/srportto/autorizacaostatusproducer/domain/service/IdempotenciaKeyGenerator.java) — key SHA-256 (id_autorizacao + data_hora_ultima_atlz)
10. [PublicadorEventoAutorizacao.java (domain/port/out)](src/main/java/br/com/srportto/autorizacaostatusproducer/domain/port/out/PublicadorEventoAutorizacao.java) — porta de SAÍDA da ponte, usa o modelo de domínio puro
11. [EventoAutorizacaoAvroMapper.java](src/main/java/br/com/srportto/autorizacaostatusproducer/infrastructure/messaging/EventoAutorizacaoAvroMapper.java) — `domain/model/EventoAutorizacao` → record Avro gerado (setScale defensivo)
12. [KafkaEventoAutorizacaoProducer.java](src/main/java/br/com/srportto/autorizacaostatusproducer/infrastructure/messaging/KafkaEventoAutorizacaoProducer.java) — adapter de SAÍDA que implementa a porta (produce síncrono, mapeia para Avro antes de enviar)
13. [AutorizacaoEventoPayload.java](src/main/java/br/com/srportto/autorizacaostatusproducer/infrastructure/messaging/AutorizacaoEventoPayload.java) — espelho do payload publicado pelo `contratocommand`
14. [SqsListenerHealthIndicator.java](src/main/java/br/com/srportto/autorizacaostatusproducer/infrastructure/web/SqsListenerHealthIndicator.java) — estado do consumo no `/actuator/health`, via `MessageListenerContainerRegistry`
15. [EventoAutorizacao.avsc](src/main/resources/avro/EventoAutorizacao.avsc) — schema Avro produzido no Kafka

## Build & Testes

```bash
mvn clean package                            # Compilar + testes + JAR (gera classes Avro em generate-sources)
mvn spring-boot:run                          # Rodar localmente (porta 8082)
mvn test                                     # Todos os testes
```

> **Maven Wrapper**: este app não possui `mvnw`/`mvnw.cmd` — use `mvn` diretamente
> (mesma orientação do `contratocommand` no Windows).

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
- **`auto.register.schemas` é `true` só no profile `local`** (`kafka.auto-register-schemas`,
  `KafkaProperties`/`KafkaProducerClientConfig`) — em `prod` é `false`. Antes do primeiro deploy
  de um schema novo/alterado em produção, registre manualmente o subject
  `eventos-autorizacao-value` no Schema Registry (CLI ou API REST do Registry, com o `.avsc`
  atualizado) e confirme compatibilidade — sem esse passo, o produce em `prod` falha com erro
  explícito de schema não registrado, em vez de registrar silenciosamente algo incompatível. Ver
  `openspec/changes/archive/2026-08-09-rede-seguranca-contrato-evento/design.md` (D5).

## Stack

| Componente | Versão | Notas |
|---|---|---|
| Java | 25 | `void main()` pendente do maven plugin |
| Spring Boot | 4.0.7 | Web MVC (só para o Actuator), Actuator |
| Spring Cloud AWS | 4.0.0 | `spring-cloud-aws-starter-sqs` — `@SqsListener`, autoconfigura `SqsAsyncClient` (AWS SDK v2 vem transitivo) |
| kafka-clients | 3.9.2 | Producer Kafka puro — sem spring-kafka |
| Avro | 1.11.4 | `avro-maven-plugin` gera `EventoAutorizacao` a partir de `src/main/resources/avro/EventoAutorizacao.avsc` |
| kafka-avro-serializer | 7.7.1 (Confluent) | Serialização Avro + integração com o Schema Registry |
| Lombok | 1.18.40 | **sem nenhum uso no código-fonte** — a dependência ainda está no `pom.xml`, mas nenhuma classe a importa; candidata a remoção |

## Endpoints reais

| Método | Caminho | Descrição |
|--------|---------|-----------|
| GET | `/actuator/health` | Health-check (Actuator). → 200 (UP) |

> **Não há endpoints REST de negócio** — esta app não expõe API própria, apenas consome
> a fila SQS e produz no Kafka em background.

## Arquitetura (hexagonal clássica)

```
domain/model/           → EventoAutorizacao (modelo de domínio puro, 28 campos, sem Avro/Jackson/AWS)
domain/port/in/         → ProcessarEventoAutorizacaoUseCase (porta de ENTRADA)
domain/port/out/        → PublicadorEventoAutorizacao (porta de SAÍDA, usa domain/model)
domain/service/         → IdempotenciaKeyGenerator (regra de negócio; @Component é exceção consciente)
domain/enums/           → StatusAutorizacao, TipoEventoAutorizacao (regra de negócio pura)
domain/exception/       → EventoAutorizacaoInvalidoException, EventoAutorizacaoKafkaIndisponivelException
application/usecase/    → ProcessarEventoAutorizacaoService (orquestra: chave de idempotência + publicação)
infrastructure/messaging/ → SqsEventoAutorizacaoListener (adapter de ENTRADA, @SqsListener; desserializa,
                             valida, converte), SqsEventoAutorizacaoErrorInterceptor (classificação
                             central de falha), AutorizacaoEventoPayload, AutorizacaoEventoPayloadValidator,
                             EventoAutorizacaoConverter (payload → domain/model), EventoAutorizacaoAvroMapper
                             (domain/model → Avro), KafkaEventoAutorizacaoProducer (adapter de SAÍDA)
infrastructure/web/     → SqsListenerHealthIndicator
infrastructure/config/  → SqsListenerContainerFactoryConfig, KafkaProperties, KafkaProducerClientConfig
```

Migração para este layout: `openspec/changes/archive/2026-08-16-hexagonal-classico-autorizacaostatus-producer/`. Camadas
e regra de dependência seguem a skill `arquitetura-limpa-java` do monorepo — `domain` não importa
Spring/Jakarta/Jackson/Avro/Kafka/AWS SDK (exceção documentada: `@Component` em
`IdempotenciaKeyGenerator`, D3 do design da migração); `application` não conhece HTTP/JPA/broker;
`infrastructure` concentra todo detalhe de framework.

Não há classe própria configurando o `SqsAsyncClient` — ele é autoconfigurado pelo
`spring-cloud-aws-starter-sqs` a partir de `spring.cloud.aws.*` (endpoint, região,
credenciais). `SqsListenerContainerFactoryConfig` só configura a factory do container
(concorrência, timeouts de shutdown, error handler), não o client em si.

`AutorizacaoEventoPayload` fica em `infrastructure/messaging/` (não em `domain/`) porque é o
contrato de fio do evento consumido (JSON), não uma regra de negócio pura. Já `StatusAutorizacao` e
`TipoEventoAutorizacao` são regra de negócio (grafo de transições) e ficam em
`domain/enums/`.

### D2-b: por que existem DOIS modelos de evento além do payload JSON

Esta app é uma ponte de formatos — seu "domínio" é a própria tradução. A decisão registrada em
`design.md` (D2, revisada em 2026-08-16) foi manter `domain/` **livre de qualquer tipo Avro**, ao
custo de um terceiro modelo espelhado:

```
SQS (JSON) ──▶ AutorizacaoEventoPayload ──▶ domain/model/EventoAutorizacao ──▶ Avro EventoAutorizacao ──▶ Kafka
                (infrastructure/messaging)   (application, via EventoAutorizacaoConverter) (infrastructure/messaging,
                                                                                              via EventoAutorizacaoAvroMapper)
```

`EventoAutorizacaoConverter` mapeia payload → domínio (sem `setScale`). `EventoAutorizacaoAvroMapper`
mapeia domínio → Avro, e é onde o `setScale(2)` defensivo vive hoje — é particularidade do formato de
fio Avro, não do modelo de domínio. Mudar um schema exige revisar os três (payload, domínio, Avro),
não dois.

### Fluxo de consumo → produção (ponte)

```
SqsEventoAutorizacaoListener.receber(String body)  — método anotado @SqsListener
  (queueNames = "${sqs.queue-url}", factory = eventosAutorizacaoSqsListenerContainerFactory)
  ├─ desserializa em AutorizacaoEventoPayload (ObjectMapper, tools.jackson) — responsabilidade do
  │    adaptador desde a migração hexagonal (D1); antes vivia no use case
  ├─ AutorizacaoEventoPayloadValidator → exige os 8 campos obrigatórios do .avsc
  ├─ TipoEventoAutorizacao.porStatus(payload.status()) → deriva o tipo do evento
  ├─ EventoAutorizacaoConverter → domain/model/EventoAutorizacao (sem setScale)
  └─ delega, sem try/catch:
       └─ ProcessarEventoAutorizacaoUseCase.processar(evento, tipoEvento)
            ├─ IdempotenciaKeyGenerator → key SHA-256(id_autorizacao + data_hora_ultima_atlz)
            ├─ PublicadorEventoAutorizacao.produzir() — implementação (KafkaEventoAutorizacaoProducer)
            │    mapeia domain/model → Avro via EventoAutorizacaoAvroMapper (setScale(2) aqui) e faz
            │    send() SÍNCRONO (get com timeout); header Kafka "tipoEvento" sempre presente
            └─ loga sucesso com idAutorizacao, key e tipoEvento — NUNCA com o body

  método retorna normalmente → container confirma (ack) a mensagem
  método lança exceção       → SqsEventoAutorizacaoErrorInterceptor.handle() decide:
       ├─ cadeia de causas contém EventoAutorizacaoInvalidoException (JSON inválido,
       │    campo obrigatório ausente/nulo, status desconhecido) → log ERROR (messageId,
       │    de MessageHeaders.ID — é o messageId real do SQS) + ENGOLE a exceção → ack
       └─ qualquer outra exceção (Kafka/SR indisponível) → log ERROR + RELANÇA →
            SEM ack, mensagem volta à fila após o visibility timeout

SqsListenerContainerFactoryConfig (infrastructure/config/) — configura o container:
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
broker (`Future.get(GET_TIMEOUT_SECONDS=20, TimeUnit.SECONDS)`) antes de retornar — esse
`get()` é o teto real do bloqueio da thread do listener. Os timeouts do producer
(`max.block.ms=5s`, `request.timeout.ms=5s`, `delivery.timeout.ms=15s`, mais o timeout
explícito do cliente do Schema Registry `http.connect/read.timeout.ms=3s`, mais os 20s do
`GET_TIMEOUT_SECONDS`) ficam abaixo
do visibility timeout da fila SQS (**60s** — `infra/envs/local-messaging/variables.tf`,
`sqs_visibility_timeout_seconds`) — uma falha de produção se resolve (sucesso ou
exceção) antes de o SQS reentregar a mensagem em processamento. Como o processamento
agora é concorrente (não mais um lote serial), o dimensionamento é sobre o pior caso de
**uma** mensagem, não do lote inteiro.

### Exceções e tratamento de erros

Esta app não tem API REST de negócio, mas tem o equivalente ao `ApiExceptionHandler`
para o escopo de mensageria: **`SqsEventoAutorizacaoErrorInterceptor`**
(`infrastructure/messaging/`), registrado como error handler da
`eventosAutorizacaoSqsListenerContainerFactory`, é o ponto único que classifica toda
exceção lançada pelo processamento de uma mensagem — o método `@SqsListener` não tem
`catch` por tipo de exceção, o container invoca o interceptor automaticamente quando o
método lança. O contrato é por comportamento, não por retorno: **engolir a exceção**
(retornar normalmente) faz o container tratar a mensagem como recuperada e confirmar o
ack; **relançar** mantém a mensagem sem ack. Duas exceções orientam essa classificação:

- **`EventoAutorizacaoInvalidoException`** (`domain/exception/`) — não-retryable.
  JSON malformado, **campo obrigatório do schema Avro ausente ou nulo**, conversão para
  o modelo de domínio impossível, ou `status` desconhecido no payload (usado para derivar `tipoEvento`
  via `TipoEventoAutorizacao.porStatus`). A classificação da etapa de consumo (desserialização,
  validação, conversão) nasce dentro do `SqsEventoAutorizacaoListener` (adaptador), não mais no
  use case — ver D1 e a armadilha #12. **Há uma segunda origem**, do lado da produção:
  `KafkaEventoAutorizacaoProducer.classificarFalhaDoProduce` também lança
  `EventoAutorizacaoInvalidoException` quando a cadeia de causas do `send()` contém
  `AvroRuntimeException`/`ClassCastException` (evento incompatível com o schema registrado) — esse
  caminho não passa pelo listener. O interceptor loga ERROR com o `messageId` — nunca com o body —
  e **engole** a exceção (descarta conscientemente: retry nunca corrigiria um payload malformado
  nem um schema incompatível).
- **`EventoAutorizacaoKafkaIndisponivelException`** (`domain/exception/`) —
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

1. **Porta 8082** — diferente de `contratocommand` (8080), `contratoquery`
   (8081) e `eventos-consumer` (8083).
2. **Sem banco de dados** — não adicione JPA/Postgres aqui; se precisar persistir algo,
   isso é uma mudança de escopo desta app.
3. **`AutorizacaoEventoPayload` é um espelho manual** do payload equivalente em
   `contratocommand` (lá, em `infrastructure/web/` ou pacote equivalente) — os dois
   não compartilham código; se o schema do evento mudar lá, replique aqui. Inclui
   `tipo_jornada` (nullable) desde a mudança `temporizacao-jornada-01-pix-auto`.
4. **`EventoAutorizacao.avsc` também é um espelho manual**, replicado em
   `apps/eventos-consumer/src/main/resources/avro/`. Mudou o schema aqui? Replique lá
   também — não há módulo Avro compartilhado no monorepo (mesma decisão do payload
   JSON). Ambos ficam em `src/main/resources/avro` (não `src/main/avro`), empacotados
   no JAR como insumo de documentação — o runtime não os lê, quem governa o schema é o
   Schema Registry.
5. **O teste de integração do adaptador exige o Floci no ar** —
   `SqsEventoAutorizacaoListenerIntegrationTest` (`infrastructure/messaging/`) envia mensagens
   reais à fila e observa ack/retenção através do pipeline real do `@SqsListener`, com
   `ProcessarEventoAutorizacaoUseCase` mockado (isola do Kafka; validator/converter são reais, então
   os bodies enviados precisam ser JSON de verdade). Sem o Floci, essa classe falha — mesmo
   pré-requisito de `mvn spring-boot:run`. A contagem de mensagens nos asserts é por **delta** em
   relação a uma baseline, não por valor absoluto: a mensagem do cenário retryable fica em voo pelo
   visibility timeout inteiro (60s) e não é drenável entre testes. **A cobertura fina de
   classificação de erro (JSON malformado, campo obrigatório nulo) não depende do Floci** — está em
   `SqsEventoAutorizacaoListenerTest` (unitário puro, sem Spring, sem fila).
6. **Ack no SQS depende do Kafka, não só do parsing** — diferente da fase anterior
   (log + ack), agora uma mensagem só é confirmada na fila após o Kafka aceitar o
   evento. Sem Kafka no ar, a fila acumula mensagens não confirmadas (retry automático).
7. **Mensagem inválida é descartada, não retida para sempre** — comportamento mudou em
   relação à fase anterior: JSON malformado/dado incompleto agora recebe ack após o log
   de erro, via `SqsEventoAutorizacaoErrorInterceptor` (retry nunca corrigiria um payload
   malformado).
8. **`enableDecimalLogicalType=true`** no `avro-maven-plugin` — sem isso, os campos
   decimais são gerados como `ByteBuffer`, não `BigDecimal`, quebrando `EventoAutorizacaoAvroMapper`.
9. **`tipoEvento` não é mais lido do attribute SQS** — o listener não solicita
   `messageAttributeNames` no `ReceiveMessage`; ele mesmo deriva o header Kafka `tipoEvento` do campo
   `status` do payload (`TipoEventoAutorizacao.porStatus`), sempre presente. `StatusAutorizacao` e
   `TipoEventoAutorizacao` (`domain/enums/`) são espelhos manuais dos mesmos enums do
   `contratocommand`.
10. **O builder Avro NÃO valida `null` explícito** — ele só valida a *ausência* de `set`
    (`fieldSetFlags()`). Como `EventoAutorizacaoAvroMapper` sempre chama os setters, um campo
    obrigatório nulo produziria um `SpecificRecord` inválido em silêncio, que só falharia adiante e
    fora da classificação (NPE na key, ou `SerializationException` **síncrona** dentro de
    `send()`, que o `catch` do producer não alcança) — resultando em reentrega infinita.
    Por isso `AutorizacaoEventoPayloadValidator` roda **antes** de converter, gerar key ou
    produzir, dentro do listener. Não remova essa validação nem a mova para depois.
11. **`/actuator/health` reflete o consumidor** — `SqsListenerHealthIndicator` reporta
    DOWN quando o `MessageListenerContainerRegistry` está ativo mas algum container
    parou fora de um shutdown intencional. Sem ele, um outage total do consumo passaria
    despercebido.
12. **Não adicione `catch` por tipo de exceção de volta no listener** — toda
    classificação retryable/não-retryable é responsabilidade exclusiva de
    `SqsEventoAutorizacaoErrorInterceptor` (`infrastructure/messaging/`), registrado como error
    handler da factory. `SqsEventoAutorizacaoListener.receber()` desserializa/valida/converte, mas
    não classifica: erros dessas etapas viram `EventoAutorizacaoInvalidoException` (via `throw new`,
    não `try/catch` de decisão) e sobem até o interceptor. Duplicar a classificação no listener
    reintroduz o espalhamento que o interceptor existe para evitar.
13. **A desserialização mora no listener, não no use case (D1 da migração hexagonal)** —
    `ProcessarEventoAutorizacaoService` (`application/usecase/`) recebe o evento **já** tipado
    (`domain/model/EventoAutorizacao` + `TipoEventoAutorizacao`); não conhece JSON, `ObjectMapper`
    nem `AutorizacaoEventoPayload`. Se precisar adicionar validação de campo novo, ela vai em
    `AutorizacaoEventoPayloadValidator`, chamada de dentro do listener — não no use case.
14. **A fila SQS tem DLQ** (`SQS-eventos-autorizacao-dlq`, `redrive_policy` em
    `infra/envs/local-messaging/`) — uma falha retryable persistente (Kafka fora do ar
    por muito tempo) esgota as 10 tentativas do `maxReceiveCount` (~10min, dado o
    visibility timeout de 60s) e a mensagem cai na DLQ para investigação manual, em vez
    de reentregar para sempre.
15. **`maxConcurrentMessages=10` roda em platform threads, não virtual threads** —
    diferente do restante da aplicação (`spring.threads.virtual.enabled=true`), o
    pipeline de execução do `@SqsListener` no Spring Cloud AWS 4.0.0 exige threads
    criadas pela sua própria factory interna (`MessageExecutionThreadFactory`); um
    `componentsTaskExecutor` customizado com virtual threads é rejeitado
    (`UnsupportedThreadFactoryException`). Não é uma limitação prática — 10 platform
    threads é um custo de recurso trivial — mas não assuma virtual threads ao analisar
    ou calibrar a concorrência deste listener.

## Documentação relacionada

- [consumo-eventos-autorizacao](../../openspec/specs/consumo-eventos-autorizacao/spec.md) — contrato vigente de visibility timeout, maxReceiveCount e concorrência desta arquitetura de consumo (decidido pela change arquivada `migrar-sqs-listener-spring-cloud-aws`)
- [design.md da migração hexagonal](../../openspec/changes/archive/2026-08-16-hexagonal-classico-autorizacaostatus-producer/design.md) — D1 (desserialização no listener), D2-b (modelo de domínio puro), D3-D5 (localização de key generator, validator/converter, health indicator)
- [design.md da mudança original](../../openspec/changes/archive/2026-07-25-add-eventos-autorizacao-sns-sqs/design.md) — decisões do fluxo original SNS/SQS (log + ack)
- [infra/envs/local-messaging/README.md](../../infra/envs/local-messaging/README.md) — como provisionar o tópico/fila SNS/SQS no Floci
- [infra/local/kafka/README.md](../../infra/local/kafka/README.md) — como subir o Kafka local (broker, Schema Registry, dashboard)

## Checklist antes do commit

- [ ] `mvn test` passa (Floci no ar — exigido pelo teste de integração do listener)
- [ ] `mvn verify` passa (gate de cobertura JaCoCo, mínimo 80%)
- [ ] `mvn clean compile` sem erros (gera as classes Avro em `generate-sources`)
- [ ] Se mudou o payload JSON, conferir consistência com `contratocommand`
- [ ] Se mudou `EventoAutorizacao.avsc`, replicar em `apps/eventos-consumer`
- [ ] Falha retryable continua sendo relançada (sem ack); falha não-retryable continua
      sendo engolida (ack) após o log de erro (não confundir as duas classificações)
- [ ] Nenhum log novo nem mensagem de exceção carrega o body da mensagem (PII)
- [ ] Se acrescentou campo obrigatório ao `.avsc`, incluí-lo em
      `AutorizacaoEventoPayloadValidator` **e** nos três modelos do evento (payload,
      `domain/model/EventoAutorizacao`, mapeamento em `EventoAutorizacaoAvroMapper`)
- [ ] Nova classe vai na camada certa — `domain`/`application`/`infrastructure`, conforme a
      skill `arquitetura-limpa-java`; `domain` continua sem import de Spring/Jakarta/Jackson/Avro/
      Kafka/AWS SDK (exceção documentada: `@Component` em `IdempotenciaKeyGenerator`)
