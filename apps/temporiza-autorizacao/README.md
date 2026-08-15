# temporiza-autorizacao

Temporizador da jornada 1 do PIX Automático, em arquitetura hexagonal. Consome os eventos de
recepção de autorizações `PIX_AUTO`/`SPI_J1` publicados pelo `contratocommand`, agenda a
expiração em 10 minutos no Valkey e, no vencimento, aciona `PATCH /api/autorizacoes/{id}/decisao`
com `acao: EXPIRAR` — rejeitando sistemicamente a autorização caso o cliente pagador não tenha
decidido a tempo.

Para arquitetura, o fluxo completo (sorted set + stream), o contrato de conclusão com o command e
armadilhas, veja [CLAUDE.md](CLAUDE.md) — este README cobre apenas como subir e testar a
aplicação.

## Pré-requisitos

- **Java 25** (JDK 25+)
- **Sem banco de dados** — esta app não usa JPA/PostgreSQL, não conhece o schema de `autorizacoes`
- **Floci no ar** com a fila `SQS-temporizacao-autorizacao` e a subscription filtrada aplicadas
  via [`infra/envs/local-messaging/`](../../infra/envs/local-messaging/)
- **Valkey local no ar** via [`infra/local/redis/`](../../infra/local/redis/) — sem ele,
  `/actuator/health` reporta DOWN e nada é agendado nem processado
- **`contratocommand` no ar** para o worker conseguir acionar `PATCH /decisao` — sem ele,
  expirações ficam retidas no PEL do stream até ele voltar (nada se perde)

## Variáveis de ambiente

```bash
# Obrigatórias apenas em prod (o profile local já tem defaults do Floci, localhost:6379 e localhost:8080)
AWS_REGION=us-east-1
AWS_SQS_QUEUE_URL=https://sqs.us-east-1.amazonaws.com/<conta>/SQS-temporizacao-autorizacao
VALKEY_HOST=valkey.exemplo
COMMAND_BASE_URL=https://contratocommand.exemplo

# opcional; default "local" quando omitido — produção deve setar "prod" explicitamente
SPRING_PROFILES_ACTIVE=local
```

## Build & Execução

```bash
mvn clean package     # compilar + testes + JAR
mvn spring-boot:run   # rodar localmente (porta 8084)
java -jar target/temporiza-autorizacao-0.0.1-SNAPSHOT.jar
```

> **Sem `mvnw`/`mvnw.cmd`** — use `mvn` diretamente.

## Testes

```bash
mvn test  # todos os testes — os de integração exigem Floci e Valkey no ar
```

## Endpoints

| Método | Caminho | Descrição |
|--------|---------|-----------|
| GET | `/actuator/health` | Health-check — reflete o consumo SQS e a conexão Valkey |

> Não há endpoints REST de negócio — esta app consome a fila SQS, agenda/varre no Valkey e aciona
> o command em background.

## Licença

MIT — veja [LICENSE](../../LICENSE) na raiz do repositório.
