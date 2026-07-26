# Eventos Consumer

Java 25 / Spring Boot 4.0.7, em **arquitetura hexagonal**. Consome o tópico Kafka
`eventos-autorizacao` (Avro, governado por Schema Registry), publicado pela ponte
`autorizacaostatus-producer`, loga o consumo com sucesso incluindo a representação do
evento e comita o offset (ack) somente após o log.

## Funcionalidades

- **Consumo via spring-kafka**: `@KafkaListener` com `AckMode.MANUAL` — o offset só
  avança após o processamento confirmar sucesso
- **Desserialização Avro**: `KafkaAvroDeserializer` (`specific.avro.reader=true`) contra
  o Schema Registry
- **Tipo do evento derivado**: `TipoEventoAutorizacao.porStatus(evento.getStatus())` — não lê mais o header Kafka
- **Log de sucesso**: cada evento consumido é logado com sua representação completa
- **Health-check**: `GET /actuator/health` via Spring Actuator

## Stack Técnico

| Componente | Versão | Descrição |
|---|---|---|
| **Java** | 25 | `void main()` pendente de suporte do maven plugin |
| **Spring Boot** | 4.0.7 | Web MVC (Actuator), IoC |
| **spring-kafka** | gerenciado pelo Spring Boot BOM | `@KafkaListener`, ack manual |
| **Avro** | 1.11.3 | `avro-maven-plugin` gera `EventoAutorizacao` a partir de `src/main/resources/avro/EventoAutorizacao.avsc` |
| **kafka-avro-serializer** | 7.7.1 (Confluent) | `KafkaAvroDeserializer` + Schema Registry |
| **Lombok** | 1.18.40 | uso mínimo — sem entidades JPA |
| **Maven** | 3.9+ | Build e gerenciamento de dependências |

## Estrutura do Projeto

```
src/main/resources/avro/
└── EventoAutorizacao.avsc                       # schema Avro consumido (espelho do producer)
src/main/java/br/com/srportto/eventosconsumer/
├── EventosConsumerApplication.java
├── application/
│   └── eventos/
│       ├── StatusAutorizacao.java                  # espelho do enum (8 estados + transições)
│       ├── TipoEventoAutorizacao.java              # espelho do enum (8 valores, porStatus)
│       └── ProcessarEventoAutorizacaoUseCase.java  # deriva o tipo e loga o evento consumido
├── infrastructure/
│   └── kafka/
│       └── EventoAutorizacaoKafkaListener.java     # @KafkaListener (ack manual)
└── shared/
    └── config/
        ├── KafkaProperties.java
        └── KafkaConsumerConfig.java                # ConsumerFactory + container factory
```

Sem `entrypoint/` nem `domain/`: esta app não expõe API REST de negócio e não tem
entidades persistidas — apenas consome o tópico e loga.

## Arquitetura Hexagonal

| Camada | Pacote | Responsabilidade |
|--------|--------|-----------------|
| **Application** | `application/eventos/` | Loga o evento consumido com sucesso |
| **Infrastructure** | `infrastructure/kafka/` | Adapter de consumo Kafka (porta de entrada) |
| **Shared** | `shared/config/` | Configuração do `ConsumerFactory` e propriedades Kafka |

### Fluxo de consumo

```
EventoAutorizacaoKafkaListener.escutar()  (@KafkaListener, AckMode.MANUAL)
  → recebe EventoAutorizacao desserializado
  → ProcessarEventoAutorizacaoUseCase.processar(evento)
      → TipoEventoAutorizacao.porStatus(evento.getStatus()) → deriva o tipo do evento
      → loga sucesso com a representação do evento e o tipo derivado
  → Acknowledgment.acknowledge() — só se processar() não lançar exceção
```

Erro no processamento não comita o offset. A reentrega segue o `DefaultErrorHandler`
padrão do spring-kafka (não a semântica de visibility timeout do SQS).

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

> Testes unitários rodam sem infraestrutura externa — `ProcessarEventoAutorizacaoUseCase`
> e `Acknowledgment` são exercitados diretamente/mockados. O `@SpringBootTest` também
> não exige um broker real (a conexão do consumer é lazy).

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
   reentrega é diferente da fila SQS.
6. **`tipoEvento` não vem mais do header Kafka** — é derivado do campo `status` do
   próprio `EventoAutorizacao` (`TipoEventoAutorizacao.porStatus`).

## Informações do Projeto

**Grupo:** br.com.srportto
**Artifact:** eventos-consumer
**Versão:** 0.0.1-SNAPSHOT
**Java:** 25 | **Spring Boot:** 4.0.7 | **Porta:** 8083
