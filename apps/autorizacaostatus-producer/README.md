# Autorizacaostatus Producer

Java 25 / Spring Boot 4.0.7, em **arquitetura hexagonal**. Ponte SQS → Kafka: consome
os eventos de estado de autorização (criação e cancelamento) publicados pelo
`arj-contratocommand` via SQS `SQS-eventos-autorizacao`, converte cada evento para Avro
e produz no tópico Kafka `eventos-autorizacao` (governado por Schema Registry), de
forma idempotente. O ack no SQS só ocorre após o broker Kafka confirmar a produção.

## Funcionalidades

- **Consumo via long polling**: `SqsClient` (AWS SDK v2 puro, sem Spring Cloud AWS) em
  loop numa virtual thread dedicada
- **Conversão para Avro**: payload JSON → record `EventoAutorizacao` (Schema Registry),
  com `setScale(2)` defensivo nos campos decimais
- **Produção idempotente**: key = SHA-256(`id_autorizacao` + `data_hora_ultima_atlz`);
  `enable.idempotence=true` + `acks=all` no producer Kafka
- **Header `tipoEvento`**: derivado do campo `status` do payload (`TipoEventoAutorizacao.porStatus`), sempre presente
- **Ack condicionado ao Kafka**: `DeleteMessage` só ocorre após a confirmação síncrona
  do broker; falha retryable mantém a mensagem na fila, falha não-retryable (dado
  inválido) descarta com log de erro
- **Health-check**: `GET /actuator/health` via Spring Actuator

## Stack Técnico

| Componente | Versão | Descrição |
|---|---|---|
| **Java** | 25 | `void main()` pendente de suporte do maven plugin |
| **Spring Boot** | 4.0.7 | Web MVC (Actuator), IoC |
| **AWS SDK v2** | 2.49.0 | `software.amazon.awssdk:sqs` |
| **kafka-clients** | 3.7.1 | Producer Kafka puro — sem spring-kafka |
| **Avro** | 1.11.3 | `avro-maven-plugin` gera `EventoAutorizacao` a partir de `src/main/resources/avro/EventoAutorizacao.avsc` |
| **kafka-avro-serializer** | 7.7.1 (Confluent) | Serialização Avro + Schema Registry |
| **Lombok** | 1.18.40 | uso mínimo — sem entidades JPA |
| **Maven** | 3.9+ | Build e gerenciamento de dependências |

## Estrutura do Projeto

```
src/main/resources/avro/
└── EventoAutorizacao.avsc                      # schema Avro produzido no Kafka
src/main/java/br/com/srportto/autorizacaostatusproducer/
├── AutorizacaostatusProducerApplication.java
├── application/
│   └── eventos/
│       ├── AutorizacaoEventoPayload.java        # espelho do payload publicado pelo command
│       ├── StatusAutorizacao.java               # espelho do enum (8 estados + transições)
│       ├── TipoEventoAutorizacao.java           # espelho do enum (8 valores, porStatus)
│       ├── EventoAutorizacaoConverter.java      # payload -> record Avro
│       ├── IdempotenciaKeyGenerator.java        # key SHA-256 de idempotência
│       ├── EventoAutorizacaoInvalidoException.java
│       └── ProcessarEventoAutorizacaoUseCase.java
├── infrastructure/
│   ├── sqs/
│   │   └── SqsEventoAutorizacaoListener.java    # adapter de consumo (SmartLifecycle)
│   └── kafka/
│       ├── KafkaEventoAutorizacaoProducer.java  # adapter de produção (síncrono)
│       └── EventoAutorizacaoKafkaIndisponivelException.java
└── shared/
    └── config/
        ├── AwsProperties.java
        ├── SqsClientConfig.java
        ├── KafkaProperties.java
        └── KafkaProducerClientConfig.java
```

Sem `entrypoint/` nem `domain/`: esta app não expõe API REST de negócio e não tem
entidades persistidas — apenas consome uma fila, converte e produz no Kafka.

## Arquitetura Hexagonal

| Camada | Pacote | Responsabilidade |
|--------|--------|-----------------|
| **Application** | `application/eventos/` | Orquestra: desserializa, converte para Avro, calcula a key |
| **Infrastructure** | `infrastructure/sqs/` | Adapter de consumo SQS (porta de entrada) |
| **Infrastructure** | `infrastructure/kafka/` | Adapter de produção Kafka (porta de saída) |
| **Shared** | `shared/config/` | Configuração dos clients AWS/Kafka e propriedades |

### Fluxo de consumo → produção (ponte)

```
SqsEventoAutorizacaoListener (SmartLifecycle)
  start() → virtual thread → loopDeConsumo()
    pollOnce(): ReceiveMessage (long polling, WaitTimeSeconds=20, MaxNumberOfMessages=10)
      processarEDarAck() por mensagem:
        ProcessarEventoAutorizacaoUseCase.processar(body)
          → desserializa em AutorizacaoEventoPayload
          → TipoEventoAutorizacao.porStatus(payload.status()) → deriva o tipo do evento
          → EventoAutorizacaoConverter → record Avro EventoAutorizacao
          → IdempotenciaKeyGenerator → key SHA-256(id_autorizacao + data_hora_ultima_atlz)
          → KafkaEventoAutorizacaoProducer.produzir() → send() síncrono (header tipoEvento derivado)
          → loga sucesso com o body e a key produzida
        ack (DeleteMessage) OU descarte, conforme a falha:
          sucesso                              → ack
          EventoAutorizacaoInvalidoException   → log ERROR + ack (descarte consciente)
          EventoAutorizacaoKafkaIndisponivelException (ou outra) → sem ack (retry)
  stop() → sinaliza parada e interrompe a thread (shutdown gracioso)
```

Falha não-retryable (JSON malformado, conversão Avro impossível) é logada e a mensagem
recebe ack — descarte consciente, já que a fila não tem redrive policy. Falha retryable
(Kafka/Schema Registry indisponível) não recebe ack e volta à fila após o visibility
timeout (30s). Erro em `ReceiveMessage` (ex.: Floci fora do ar) aplica backoff de 5s
sem encerrar o loop.

## Como Executar

### Pré-requisitos

- **Java 25** (JDK 25+)
- **Maven 3.9+** (use `mvn` diretamente — este app não tem `mvnw`)
- **Floci no ar** com o tópico, a fila e a subscription já aplicados via
  [`infra/envs/local-messaging/`](../../infra/envs/local-messaging/)
- **Kafka local no ar** (broker + Schema Registry) via
  [`infra/local/kafka/`](../../infra/local/kafka/)

### Variáveis de Ambiente

```bash
# Obrigatórias apenas em prod (o profile local já tem defaults do Floci/Kafka local)
AWS_REGION=us-east-1
AWS_SQS_QUEUE_URL=https://sqs.us-east-1.amazonaws.com/<conta>/SQS-eventos-autorizacao
KAFKA_BOOTSTRAP_SERVERS=kafka.exemplo:9092
KAFKA_SCHEMA_REGISTRY_URL=https://schema-registry.exemplo
KAFKA_TOPIC=eventos-autorizacao   # opcional, default já é este valor

# Spring Profiles (opcional; padrão de desenvolvimento é "local" quando omitido)
SPRING_PROFILES_ACTIVE=local    # local ou prod — produção DEVE setar explicitamente "prod"
```

### Build & Execução

```bash
# Compilar + testes + JAR (gera as classes Avro em generate-sources)
mvn clean package

# Rodar localmente (porta 8082)
mvn spring-boot:run

# Via JAR
java -jar target/autorizacaostatus-producer-0.0.1-SNAPSHOT.jar
```

### Testar o fluxo local

```bash
# 1. Suba o Floci + infra/envs/local-messaging e o Kafka local (infra/local/kafka)
# 2. Suba esta app (mvn spring-boot:run)
# 3. Em outro terminal, publique um evento de teste diretamente no tópico SNS:
export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test
aws --endpoint-url http://localhost:4566 --region us-east-1 sns publish \
  --topic-arn arn:aws:sns:us-east-1:000000000000:sns-estados-autorizacao \
  --message '{"id_autorizacao":"00000000-0000-0000-0000-000000000000","id_particao_conta":950,"data_fim_vigencia":"2027-01-01","tipo_produto":1,"status":4,"data_hora_inclusao":"2026-07-26T10:00:00","data_hora_ultima_atlz":"2026-07-26T10:00:00","codigo_canal_contratacao":"canal"}'
# 4. Confira o log desta app: deve aparecer "produzida com sucesso"
# 5. Confira o Kafbat UI (http://localhost:8090): o evento aparece no tópico eventos-autorizacao
```

## Testes

```bash
# Todos os testes
mvn test

# Com relatório de cobertura (JaCoCo)
mvn clean verify
# Abrir: target/site/jacoco/index.html
```

> Testes unitários rodam sem infraestrutura externa — `SqsClient` e o `Producer` Kafka
> são mockados.

## Armadilhas Críticas

1. **Porta 8082**, não 8080 (`arj-contratocommand`), 8081 (`arj-contratoquery`) nem
   8083 (`eventos-consumer`).
2. **Sem banco de dados** — não há JPA/Postgres nesta app.
3. **`AutorizacaoEventoPayload` é um espelho manual** do payload publicado pelo
   `arj-contratocommand` — os dois não compartilham código-fonte; mudanças no schema do
   evento precisam ser replicadas nos dois lados.
4. **`EventoAutorizacao.avsc` também é um espelho manual**, replicado em
   `apps/eventos-consumer` (ambos em `src/main/resources/avro`) — sem módulo Avro
   compartilhado no monorepo.
5. **`pollOnce()`/`processarEDarAck()` são package-private de propósito**, para permitir
   testar o adapter sem rodar a thread real de polling.
6. **`enableDecimalLogicalType=true`** no `avro-maven-plugin` — sem isso, os campos
   decimais viram `ByteBuffer` em vez de `BigDecimal`.
7. **Sem outbox/DLQ na fila SQS** — mensagem não-retryable é descartada (com log) em vez
   de retida para sempre; ver `design.md` da mudança `add-eventos-autorizacao-kafka`
   para os trade-offs aceitos.
8. **`tipoEvento` não vem mais do attribute SQS** — é derivado do campo `status` do
   payload (`TipoEventoAutorizacao.porStatus`); `status` desconhecido vira mensagem
   inválida (descarte consciente).

## Informações do Projeto

**Grupo:** br.com.srportto
**Artifact:** autorizacaostatus-producer
**Versão:** 0.0.1-SNAPSHOT
**Java:** 25 | **Spring Boot:** 4.0.7 | **Porta:** 8082
