# eventos-consumer

Consumidora do tópico Kafka `eventos-autorizacao`, em arquitetura hexagonal. Recebe os eventos
Avro produzidos pela `autorizacaostatus-producer`, loga o consumo com sucesso (só
`idAutorizacao`/`tipoEvento`, nunca o record inteiro — carrega PII) e comita o offset (ack)
somente após o log. Mensagens que esgotam as tentativas de processamento vão para a DLT
(`eventos-autorizacao.DLT`).

Para arquitetura, fluxo completo, a regra de proteção de dado sensível em log e armadilhas, veja
[CLAUDE.md](CLAUDE.md) — este README cobre apenas como subir e testar a aplicação.

## Pré-requisitos

- **Java 25** (JDK 25+)
- **Sem banco de dados** — esta app não usa JPA/PostgreSQL
- **Kafka local no ar** (broker + Schema Registry) via [`infra/local/kafka/`](../../infra/local/kafka/)
  — a conexão é lazy: a app sobe sem o broker, mas só consome quando ele existir

## Variáveis de ambiente

```bash
# Obrigatórias apenas em prod (o profile local já tem defaults do Kafka local)
KAFKA_BOOTSTRAP_SERVERS=kafka.exemplo:9092
KAFKA_SCHEMA_REGISTRY_URL=https://schema-registry.exemplo

# opcional; default "local" quando omitido — produção deve setar "prod" explicitamente
SPRING_PROFILES_ACTIVE=local
```

## Build & Execução

```bash
mvn clean package     # compilar + testes + JAR (gera as classes Avro em generate-sources)
mvn spring-boot:run   # rodar localmente (porta 8083)
java -jar target/eventos-consumer-0.0.1-SNAPSHOT.jar
```

> **Sem `mvnw`/`mvnw.cmd`** — use `mvn` diretamente.

## Testar o fluxo local

```bash
# 1. Suba o Kafka local (infra/local/kafka) e a autorizacaostatus-producer
# 2. Suba esta app (mvn spring-boot:run)
# 3. Crie ou cancele uma autorização via contratocommand (POST/PATCH /api/autorizacoes)
# 4. Confira o log desta app: deve aparecer a confirmação de consumo
# 5. Confira o Kafbat UI (http://localhost:8090): o consumer group eventos-consumer
#    deve aparecer com lag zerando
```

## Testes

```bash
mvn test          # todos os testes — não exige broker real (conexão do consumer é lazy)
mvn clean verify  # com relatório de cobertura (JaCoCo)
# Abrir: target/site/jacoco/index.html
```

## Endpoints

| Método | Caminho | Descrição |
|--------|---------|-----------|
| GET | `/actuator/health` | Health-check (Actuator) |

> Não há endpoints REST de negócio — esta app só consome o tópico Kafka em background.

## Licença

MIT — veja [LICENSE](../../LICENSE) na raiz do repositório.
