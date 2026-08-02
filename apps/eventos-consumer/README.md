# Eventos Consumer

Java 25 / Spring Boot 4.0.7, em **arquitetura hexagonal**. Consome o tópico Kafka
`eventos-autorizacao` (Avro, governado por Schema Registry), publicado pela ponte
`autorizacaostatus-producer`, loga o consumo com sucesso incluindo a representação do
evento e comita o offset (ack) somente após o log. Mensagens que esgotam as tentativas de
processamento vão para a DLT (`eventos-autorizacao.DLT`).

## Funcionalidades

- **Consumo via spring-kafka**: `@KafkaListener` com `AckMode.RECORD` — o offset avança
  automaticamente após o processamento retornar sem exceção, sem `Acknowledgment` manual
- **Desserialização Avro resiliente**: `KafkaAvroDeserializer` (`specific.avro.reader=true`)
  envolvido por `ErrorHandlingDeserializer` contra o Schema Registry
- **Tipo do evento derivado**: `TipoEventoAutorizacao.porStatus(evento.getStatus())` — não
  lê o header Kafka; o body Avro é a fonte única da verdade
- **DLT para mensagens não-processáveis**: 3 tentativas com 1s de intervalo
  (`DefaultErrorHandler` + `FixedBackOff`); esgotadas, a mensagem original é publicada em
  `eventos-autorizacao.DLT` via `DeadLetterPublishingRecoverer`
- **Log de sucesso**: cada evento consumido é logado com sua representação completa
- **Health-check**: `GET /actuator/health` via Spring Actuator

## Stack Técnico

| Componente | Versão | Descrição |
|---|---|---|
| **Java** | 25 | `void main()` pendente de suporte do maven plugin |
| **Spring Boot** | 4.0.7 | Web MVC (Actuator), IoC |
| **spring-boot-starter-kafka** | gerenciado pelo Spring Boot BOM | `@KafkaListener`, `AckMode.RECORD` |
| **Avro** | 1.11.4 | `avro-maven-plugin` gera `EventoAutorizacao` a partir de `src/main/resources/avro/EventoAutorizacao.avsc` |
| **kafka-avro-serializer** | 7.7.1 (Confluent) | `KafkaAvroDeserializer` (via `ErrorHandlingDeserializer`) + Schema Registry |
| **Lombok** | 1.18.40 | uso mínimo — sem entidades JPA |
| **Maven** | 3.9+ | Build e gerenciamento de dependências |

## Estrutura do Projeto

```
src/main/resources/avro/
└── EventoAutorizacao.avsc                       # schema Avro consumido (espelho do producer)
src/main/java/br/com/srportto/eventosconsumer/
├── EventosConsumerApplication.java
├── entrypoint/
│   └── kafka/
│       └── EventoAutorizacaoKafkaListener.java     # @KafkaListener (AckMode.RECORD)
├── application/
│   └── eventos/
│       └── ProcessarEventoAutorizacaoUseCase.java  # deriva o tipo e loga o evento consumido
├── domain/
│   └── enums/
│       ├── StatusAutorizacao.java                  # espelho do enum (8 estados + transições)
│       └── TipoEventoAutorizacao.java              # espelho do enum (8 valores, porStatus)
└── shared/
    └── config/
        ├── KafkaProperties.java
        └── KafkaConsumerConfig.java                # ConsumerFactory, container factory, DLT
```

Sem persistência (sem `JPA`/`PostgreSQL`) e sem API REST de negócio, mas o app **tem**
`domain/` (o grafo de transições de `StatusAutorizacao` é regra de negócio pura) e
`entrypoint/` (o listener, adaptador de entrada) — alinhado com a convenção hexagonal do
monorepo.

## Arquitetura Hexagonal

| Camada | Pacote | Responsabilidade |
|--------|--------|-----------------|
| **Entrypoint** | `entrypoint/kafka/` | Adapter de consumo Kafka (porta de entrada) |
| **Application** | `application/eventos/` | Orquestra: deriva o tipo e loga o evento consumido |
| **Domain** | `domain/enums/` | Regra de negócio pura: grafo de transições de estado |
| **Shared** | `shared/config/` | `ConsumerFactory`, container factory, DLT |

### Fluxo de consumo

```
EventoAutorizacaoKafkaListener.escutar()  (@KafkaListener, AckMode.RECORD)
  → recebe EventoAutorizacao desserializado (via ErrorHandlingDeserializer)
  → ProcessarEventoAutorizacaoUseCase.processar(evento)
      → TipoEventoAutorizacao.porStatus(evento.getStatus()) → deriva o tipo do evento
      → loga sucesso com a representação do evento e o tipo derivado
  → offset avança automaticamente — só se o método retornar sem lançar exceção
```

Erro no processamento (ex.: `status` desconhecido) ou falha de desserialização Avro não
avançam o offset. A reentrega segue o `DefaultErrorHandler` (3 tentativas, 1s de
intervalo) — não a semântica de visibility timeout do SQS. Esgotadas as tentativas, a
mensagem original é publicada em `eventos-autorizacao.DLT` via
`DeadLetterPublishingRecoverer` (o offset avança nesse ponto; a mensagem não é
reentregue indefinidamente).

## Como Executar

### Pré-requisitos

- **Java 25** (JDK 25+)
- **Maven 3.9+** (use `mvn` diretamente — este app não tem `mvnw`)
- **Kafka local no ar** (broker + Schema Registry) via
  [`infra/local/kafka/`](../../infra/local/kafka/) — a conexão é lazy: a app sobe sem
  o broker, mas só consome quando ele existir

### Variáveis de Ambiente

```bash
# Obrigatórias apenas em prod (o profile local já tem defaults do Kafka local)
KAFKA_BOOTSTRAP_SERVERS=kafka.exemplo:9092
KAFKA_SCHEMA_REGISTRY_URL=https://schema-registry.exemplo
KAFKA_TOPIC=eventos-autorizacao      # opcional, default já é este valor
KAFKA_GROUP_ID=eventos-consumer      # opcional, default já é este valor

# Spring Profiles (opcional; padrão de desenvolvimento é "local" quando omitido)
SPRING_PROFILES_ACTIVE=local    # local ou prod — produção DEVE setar explicitamente "prod"
```

### Build & Execução

```bash
# Compilar + testes + JAR (gera as classes Avro em generate-sources)
mvn clean package

# Rodar localmente (porta 8083)
mvn spring-boot:run

# Via JAR
java -jar target/eventos-consumer-0.0.1-SNAPSHOT.jar
```

### Testar o fluxo local

```bash
# 1. Suba o Kafka local (infra/local/kafka) e a autorizacaostatus-producer
# 2. Suba esta app (mvn spring-boot:run)
# 3. Crie ou cancele uma autorização via arj-contratocommand (POST/PATCH /api/autorizacoes)
# 4. Confira o log desta app: deve aparecer "consumida com sucesso"
# 5. Confira o Kafbat UI (http://localhost:8090): o consumer group eventos-consumer
#    deve aparecer com lag zerando
```

## Testes

```bash
# Todos os testes
mvn test

# Com relatório de cobertura (JaCoCo)
mvn clean verify
# Abrir: target/site/jacoco/index.html
```

> Testes unitários rodam sem infraestrutura externa. `KafkaConsumerConfigTest` cobre os
> dois caminhos da DLT: falha de negócio (record já desserializado, roteado pelo template
> Avro) e falha de desserialização (via `ErrorHandlingDeserializer` real +
> `MockSchemaRegistryClient`, roteado pelo template de bytes) — sem broker nem Schema
> Registry reais. O `@SpringBootTest` também não exige um broker real (a conexão do
> consumer é lazy).

## Armadilhas Críticas

1. **Porta 8083**, não 8080 (`arj-contratocommand`), 8081 (`arj-contratoquery`) nem
   8082 (`autorizacaostatus-producer`).
2. **Sem banco de dados** — não há JPA/Postgres nesta app.
3. **`EventoAutorizacao.avsc` é um espelho manual** do schema equivalente em
   `apps/autorizacaostatus-producer` (ambos em `src/main/resources/avro`) — sem módulo
   Avro compartilhado no monorepo.
4. **spring-kafka, não cliente puro** — diferente do padrão "AWS SDK puro" do listener
   SQS; decisão deliberada (ver `design.md` da mudança `add-eventos-autorizacao-kafka`).
5. **Retry via `DefaultErrorHandler`**, não visibility timeout — a semântica de
   reentrega é diferente da fila SQS; esgotadas as tentativas, vai para a DLT.
6. **`tipoEvento` não vem do header Kafka** — é derivado do campo `status` do
   próprio `EventoAutorizacao` (`TipoEventoAutorizacao.porStatus`).
7. **`AckMode.RECORD` é definido em código** (`KafkaConsumerConfig`), não em
   `application.yaml` — o factory é fortemente tipado e incompatível com o
   `ConcurrentKafkaListenerContainerFactoryConfigurer` do Boot (ver `design.md` da
   mudança `refactor-eventos-consumer`, decisão D1).
8. **DLT usa dois `KafkaTemplate`** — um `<String, byte[]>` para falha de
   desserialização, outro `<String, EventoAutorizacao>` para falha de negócio; o
   `DeadLetterPublishingRecoverer` roteia entre os dois pelo tipo do valor do record.

## Informações do Projeto

**Grupo:** br.com.srportto
**Artifact:** eventos-consumer
**Versão:** 0.0.1-SNAPSHOT
**Java:** 25 | **Spring Boot:** 4.0.7 | **Porta:** 8083
