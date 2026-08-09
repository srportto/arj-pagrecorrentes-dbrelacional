# CLAUDE.md

> Guia para agentes de IA (Claude Code, Copilot, etc.) trabalharem neste repositório.
> **Este arquivo e `AGENTS.md` são espelhos — mantenha-os idênticos ao editar.**

Consumidora do tópico Kafka `eventos-autorizacao`, em **arquitetura hexagonal**. Recebe
os eventos Avro produzidos pela `autorizacaostatus-producer` (ponte SQS → Kafka), loga o
consumo com sucesso (identificadores apenas: `idAutorizacao`, `tipoEvento`) e comita o offset (ack) somente
após o log. Nesta fase não há processamento de negócio — apenas log + ack. Mensagens que
esgotam as tentativas de processamento (falha de negócio ou de desserialização) vão para
a DLT (`eventos-autorizacao.DLT`).

## Comece por aqui

Leia nesta ordem:
1. [EventoAutorizacaoKafkaListener.java](src/main/java/br/com/srportto/eventosconsumer/entrypoint/kafka/EventoAutorizacaoKafkaListener.java) — `@KafkaListener` (AckMode.RECORD)
2. [ProcessarEventoAutorizacaoUseCase.java](src/main/java/br/com/srportto/eventosconsumer/application/eventos/ProcessarEventoAutorizacaoUseCase.java) — loga o evento consumido
3. [StatusAutorizacao.java](src/main/java/br/com/srportto/eventosconsumer/domain/enums/StatusAutorizacao.java) / [TipoEventoAutorizacao.java](src/main/java/br/com/srportto/eventosconsumer/domain/enums/TipoEventoAutorizacao.java) — enum de negócio (grafo de transições) e sua derivação
4. [KafkaConsumerConfig.java](src/main/java/br/com/srportto/eventosconsumer/shared/config/KafkaConsumerConfig.java) — `ConsumerFactory`, container factory (AckMode.RECORD), `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` (DLT)
5. [EventoAutorizacao.avsc](src/main/resources/avro/EventoAutorizacao.avsc) — schema Avro consumido (espelho manual do da `autorizacaostatus-producer`)

## Build & Testes

```bash
mvn clean package                            # Compilar + testes + JAR (gera classes Avro em generate-sources)
mvn spring-boot:run                          # Rodar localmente (porta 8083)
mvn test                                     # Todos os testes
```

> **Maven Wrapper**: este app não possui `mvnw`/`mvnw.cmd` — use `mvn` diretamente.

## Pré-requisitos

- **Java 25** (JDK 25+) — usa `public static void main()`; a forma `void main()` do Java 25 está pendente de suporte do maven plugin
- **Sem banco de dados** — esta app não usa JPA/PostgreSQL
- **Kafka local no ar** (broker + Schema Registry) via
  [`infra/local/kafka/`](../../infra/local/kafka/README.md) — a conexão do consumer
  Kafka é lazy: a app sobe normalmente sem o broker no ar, mas não consome nada até ele
  existir
- Variáveis de ambiente obrigatórias em `prod`: `KAFKA_BOOTSTRAP_SERVERS`,
  `KAFKA_SCHEMA_REGISTRY_URL` (no profile `local` há defaults apontando para
  `infra/local/kafka/`)
- Profiles Spring: `local` (padrão de desenvolvimento) e `prod` (deve ser setado
  explicitamente via `SPRING_PROFILES_ACTIVE=prod`)
- **`auto.register.schemas` é `true` só no profile `local`** (`kafka.auto-register-schemas`,
  `KafkaProperties`/`KafkaConsumerConfig` — usado pelos templates da DLT) — em `prod` é `false`.
  Antes do primeiro deploy de um schema novo/alterado em produção, registre manualmente o subject
  `eventos-autorizacao-value` no Schema Registry (CLI ou API REST do Registry, com o `.avsc`
  atualizado) e confirme compatibilidade. Ver
  `openspec/changes/rede-seguranca-contrato-evento/design.md` (D5).

## Stack

| Componente | Versão | Notas |
|---|---|---|
| Java | 25 | `void main()` pendente do maven plugin |
| Spring Boot | 4.0.7 | Web MVC (só para o Actuator), Actuator |
| spring-boot-starter-kafka | gerenciado pelo Spring Boot BOM | `@KafkaListener` com `AckMode.RECORD` (sem `Acknowledgment` manual) |
| Avro | 1.11.4 | `avro-maven-plugin` gera `EventoAutorizacao` a partir de `src/main/resources/avro/EventoAutorizacao.avsc` |
| kafka-avro-serializer | 7.7.1 (Confluent) | `KafkaAvroDeserializer` (envolvido por `ErrorHandlingDeserializer`) + integração com o Schema Registry |
| Lombok | 1.18.40 | uso mínimo (sem entidades JPA) |

## Endpoints reais

| Método | Caminho | Descrição |
|--------|---------|-----------|
| GET | `/actuator/health` | Health-check (Actuator). → 200 (UP) |

> **Não há endpoints REST de negócio** — esta app não expõe API própria, apenas consome
> o tópico Kafka em background.

## Arquitetura (hexagonal)

```
entrypoint/kafka/       → EventoAutorizacaoKafkaListener (adapter de consumo, @KafkaListener)
application/eventos/    → ProcessarEventoAutorizacaoUseCase (loga o evento)
domain/enums/           → StatusAutorizacao (grafo de transições), TipoEventoAutorizacao (derivado do status)
shared/config/          → KafkaProperties, KafkaConsumerConfig (ConsumerFactory, container factory, DLT)
```

Sem persistência (sem `JPA`/`PostgreSQL`) e sem API REST de negócio, mas o app **tem**
`domain/` (regra de negócio pura: o grafo de transições de `StatusAutorizacao`) e
`entrypoint/` (o listener, adaptador de entrada) — alinhado com a tabela "Que classe vai
em qual camada" da skill `arquitetura-limpa-java` do monorepo.

### Fluxo de consumo

```
EventoAutorizacaoKafkaListener.escutar()  (@KafkaListener, containerFactory com AckMode.RECORD)
  ├─ recebe o EventoAutorizacao desserializado (specific.avro.reader=true, via ErrorHandlingDeserializer)
  ├─ ProcessarEventoAutorizacaoUseCase.processar(evento)
  │    ├─ TipoEventoAutorizacao.porStatus(evento.getStatus()) → deriva o tipo do evento
  │    └─ loga sucesso: idAutorizacao e tipo derivado (nunca o record Avro inteiro — ele contém PII)
  └─ offset avança automaticamente — só se o método retornar sem lançar exceção
```

Erro em `processar()` (ex.: `status` desconhecido) ou falha de desserialização Avro não
avançam o offset. A reentrega **não** segue a semântica de visibility timeout do SQS:
quem trata a repetição é o `DefaultErrorHandler` do spring-kafka — 3 tentativas com 1s de
intervalo (`FixedBackOff`) e, esgotadas, a mensagem original é publicada em
`eventos-autorizacao.DLT` via `DeadLetterPublishingRecoverer` (o offset da mensagem
original avança nesse ponto — ela não é reentregue indefinidamente).

### Diferença deliberada de padrão em relação ao SQS

Ao contrário do listener SQS da `autorizacaostatus-producer` (cliente AWS SDK v2 puro,
sem framework), esta app usa **spring-kafka** (`@KafkaListener`) — quebra consciente da
jurisprudência "cliente puro" do monorepo: menos código, error handling e retry prontos,
e é o idioma dominante do ecossistema Kafka. Ver `design.md` da mudança
`add-eventos-autorizacao-kafka` para a decisão original, e da mudança
`refactor-eventos-consumer` para a limpeza de `AckMode`, DLT e camadas.

## Armadilhas críticas

1. **Porta 8083** — diferente de `arj-contratocommand` (8080), `arj-contratoquery`
   (8081) e `autorizacaostatus-producer` (8082).
2. **Sem banco de dados** — não adicione JPA/Postgres aqui; se precisar persistir algo,
   isso é uma mudança de escopo desta app.
3. **`EventoAutorizacao.avsc` é um espelho manual** do schema equivalente em
   `apps/autorizacaostatus-producer` — os dois não compartilham código; se o schema
   mudar lá, replique aqui. Ambos ficam em `src/main/resources/avro` (não
   `src/main/avro`), empacotados no JAR como insumo de documentação — o runtime não os
   lê, quem governa o schema é o Schema Registry. Inclui `tipo_jornada` (nullable) desde
   a mudança `temporizacao-jornada-01-pix-auto`.
4. **Conexão Kafka é lazy** — `@SpringBootTest` sobe normalmente sem broker real (o
   `KafkaConsumer` só toca a rede ao fazer poll, em thread de background do container);
   não confunda "contexto sobe" com "está consumindo".
5. **Semântica de retry é do spring-kafka, não do SQS** — não há visibility timeout;
   quem decide reentrega é o `DefaultErrorHandler` do container.
6. **`tipoEvento` não é lido do header Kafka** — `EventoAutorizacaoKafkaListener` não
   declara o parâmetro `@Header`; `ProcessarEventoAutorizacaoUseCase` deriva o tipo do
   campo `status` do próprio `EventoAutorizacao` recebido (`TipoEventoAutorizacao.porStatus`)
   — decisão deliberada: o body Avro é a fonte única da verdade, não um header que
   poderia divergir dele. `StatusAutorizacao` e `TipoEventoAutorizacao` (`domain/enums/`)
   são espelhos manuais dos mesmos enums do `arj-contratocommand`.
7. **`AckMode.RECORD` é definido em código, não em `application.yaml`** —
   `factory.getContainerProperties().setAckMode(...)` em `KafkaConsumerConfig`. Não dá
   para usar `spring.kafka.listener.ack-mode` + `ConcurrentKafkaListenerContainerFactoryConfigurer`
   aqui porque o factory é fortemente tipado (`<String, EventoAutorizacao>`) e o
   configurer do Boot só aceita `<Object, Object>` (invariância de generics).
8. **DLT usa dois `KafkaTemplate` diferentes** — falha de desserialização (bytes crus,
   capturados pelo `ErrorHandlingDeserializer`) vai por um `KafkaTemplate<String, byte[]>`;
   falha de negócio após desserialização com sucesso (ex.: `status` desconhecido) vai por
   um `KafkaTemplate<String, EventoAutorizacao>` com `KafkaAvroSerializer`. O
   `DeadLetterPublishingRecoverer` roteia entre os dois pelo tipo do valor do record — se
   um novo tipo de falha aparecer, pode ser necessário um terceiro template.
9. **Tópico `eventos-autorizacao.DLT` é provisionado explicitamente** no
   `kafka-topic-init` de `infra/local/kafka/compose.yaml`, com 3 partições — mesmo
   critério do tópico principal, que também tem auto-create desabilitado
   (`auto.create.topics.enable: false`). Sem essa criação explícita, a publicação na DLT
   falha com `UnknownTopicOrPartitionException`, o offset não avança e a partição trava
   na mensagem venenosa que a DLT deveria isolar — exatamente o cenário que a DLT existe
   para evitar (ver `openspec/changes/rede-seguranca-contrato-evento`).
10. **O destino da DLT é resolvido explicitamente para `<tópico>.DLT`** em
    `KafkaConsumerConfig.eventoAutorizacaoDeadLetterRecoverer` (`BiFunction` passado ao
    `DeadLetterPublishingRecoverer`) — **não confie no destino default do spring-kafka**:
    nesta versão (4.0.6) ele resolve para `<tópico>-dlt` (hífen, minúsculo), não
    `<tópico>.DLT`. Usar o default faria a publicação na DLT falhar contra um tópico
    inexistente, mesmo com `eventos-autorizacao.DLT` provisionado no compose. Verificado
    ao vivo antes desta correção: reproduziu exatamente a partição travada.

## Regra de logs — proteção de dado sensível

**Nunca interpole um objeto de domínio, record Avro, payload ou DTO em log.** O `EventoAutorizacao`
carrega dado pessoal (`id_pessoa_pagadora`, `id_pessoa_devedora`, `id_pessoa_recebedora`) e
financeiro (`valor`, `descricao`, `metadados`). Identifique a mensagem por campos nominalmente:
apenas `idAutorizacao` e `tipoEvento` em log é seguro; o record inteiro não é.

**Padrão correto:**
```java
log.info("Autorização {} consumida com sucesso (tipoEvento={})",
        evento.getIdAutorizacao(), tipoEvento);
```

**Padrão proibido:**
```java
log.info("Autorização {} consumida: {}", evento.getIdAutorizacao(), evento);  // ❌ vaza PII
```

Ver a mudança `parar-vazamento-dado-sensivel` no `openspec/changes/` para contexto completo.

## Documentação relacionada

- [design.md de add-eventos-autorizacao-kafka](../../openspec/changes/archive/2026-07-26-add-eventos-autorizacao-kafka/design.md) — decisões técnicas originais do fluxo Kafka (schema, idempotência, spring-kafka vs. cliente puro)
- [design.md de refactor-eventos-consumer](../../openspec/changes/refactor-eventos-consumer/design.md) — AckMode.RECORD, DLT e realinhamento de camadas (entrypoint/domain)
- [infra/local/kafka/README.md](../../infra/local/kafka/README.md) — como subir o Kafka local (broker, Schema Registry, dashboard)

## Checklist antes do commit

- [ ] `mvn test` passa
- [ ] `mvn clean compile` sem erros (gera as classes Avro em `generate-sources`)
- [ ] Se mudou `EventoAutorizacao.avsc`, replicar em `apps/autorizacaostatus-producer`
- [ ] Erros de processamento continuam sem avançar o offset antes de esgotar as tentativas
- [ ] Mensagem que esgota as tentativas continua indo para `eventos-autorizacao.DLT`
