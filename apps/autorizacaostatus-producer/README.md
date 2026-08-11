# autorizacaostatus-producer

Ponte SQS → Kafka, em arquitetura hexagonal. Consome os eventos de estado de autorização
publicados pelo `arj-contratocommand` (via SNS → SQS `SQS-eventos-autorizacao`), converte cada
evento para Avro e produz no tópico Kafka `eventos-autorizacao` (Schema Registry), de forma
idempotente. O ack no SQS só ocorre após a confirmação do broker Kafka.

Para arquitetura, fluxo completo, classificação de erros e armadilhas, veja
[CLAUDE.md](CLAUDE.md) — este README cobre apenas como subir e testar a aplicação.

## Pré-requisitos

- **Java 25** (JDK 25+)
- **Sem banco de dados** — esta app não usa JPA/PostgreSQL
- **Floci no ar** com o tópico, a fila e a subscription já aplicados via
  [`infra/envs/local-messaging/`](../../infra/envs/local-messaging/)
- **Kafka local no ar** (broker + Schema Registry) via [`infra/local/kafka/`](../../infra/local/kafka/)

## Variáveis de ambiente

```bash
# Obrigatórias apenas em prod (o profile local já tem defaults do Floci/Kafka local)
AWS_REGION=us-east-1
AWS_SQS_QUEUE_URL=https://sqs.us-east-1.amazonaws.com/<conta>/SQS-eventos-autorizacao
KAFKA_BOOTSTRAP_SERVERS=kafka.exemplo:9092
KAFKA_SCHEMA_REGISTRY_URL=https://schema-registry.exemplo

# opcional; default "local" quando omitido — produção deve setar "prod" explicitamente
SPRING_PROFILES_ACTIVE=local
```

## Build & Execução

```bash
mvn clean package     # compilar + testes + JAR (gera as classes Avro em generate-sources)
mvn spring-boot:run   # rodar localmente (porta 8082)
java -jar target/autorizacaostatus-producer-0.0.1-SNAPSHOT.jar
```

> **Sem `mvnw`/`mvnw.cmd`** — use `mvn` diretamente.

## Testar o fluxo local

```bash
# 1. Suba o Floci + infra/envs/local-messaging e o Kafka local (infra/local/kafka)
# 2. Suba esta app (mvn spring-boot:run)
# 3. Em outro terminal, publique um evento de teste diretamente no tópico SNS:
export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test
aws --endpoint-url http://localhost:4566 --region us-east-1 sns publish \
  --topic-arn arn:aws:sns:us-east-1:000000000000:sns-estados-autorizacao \
  --message '{"id_autorizacao":"00000000-0000-0000-0000-000000000000","id_particao_conta":950,"data_fim_vigencia":"2027-01-01","tipo_produto":1,"status":4,"data_hora_inclusao":"2026-07-26T10:00:00","data_hora_ultima_atlz":"2026-07-26T10:00:00","codigo_canal_contratacao":"canal"}'
# 4. Confira o log desta app: deve aparecer a confirmação de produção
# 5. Confira o Kafbat UI (http://localhost:8090): o evento aparece no tópico eventos-autorizacao
```

## Testes

```bash
mvn test          # todos os testes (o de integração do listener exige o Floci no ar)
mvn clean verify  # com relatório de cobertura (JaCoCo)
# Abrir: target/site/jacoco/index.html
```

## Endpoints

| Método | Caminho | Descrição |
|--------|---------|-----------|
| GET | `/actuator/health` | Health-check (Actuator) — reflete o estado do consumo SQS |

> Não há endpoints REST de negócio — esta app só consome a fila SQS e produz no Kafka em
> background.

## Licença

MIT — veja [LICENSE](../../LICENSE) na raiz do repositório.
