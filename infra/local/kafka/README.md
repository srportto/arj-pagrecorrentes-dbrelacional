# infra/local/kafka

Ambiente Kafka local **standalone**, independente do compose de apps
(`apps/docker-compose.yml`) e do Terraform de mensageria SQS
(`infra/envs/local-messaging/`). Subir ou derrubar este compose não afeta os demais
ambientes locais.

Serviços:

- **kafka** — broker Confluent (`cp-kafka`) em modo KRaft, nó único (sem ZooKeeper).
- **kafka-topic-init** — cria o tópico `eventos-autorizacao` (3 partições) e encerra.
  `auto.create.topics.enable=false` no broker: o tópico é contrato explícito.
- **schema-registry** — Confluent Schema Registry, storage no próprio broker.
- **kafbat-ui** — dashboard (mensagens decodificadas via Avro, consumer groups, lag).

## Portas em localhost

| Serviço | Porta | Uso |
|---|---|---|
| Kafka (broker) | `19092` | `bootstrap.servers=localhost:19092` (a `9092` colide com a faixa dinâmica reservada pelo Hyper-V/WSL2 no Windows) |
| Schema Registry | `8085` | `http://localhost:8085` (evita colidir com o `8081` do `arj-contratoquery`) |
| Kafbat UI | `8090` | `http://localhost:8090` (evita colidir com o `8080` do `arj-contratocommand`) |

## Rodar

```bash
docker compose -f infra/local/kafka/compose.yaml up -d
```

## Validar

```bash
# tópico criado com 3 partições
docker exec kafka-eventos-autorizacao kafka-topics --bootstrap-server localhost:19092 \
  --describe --topic eventos-autorizacao

# schema registry no ar
curl http://localhost:8085/subjects
```

Abra o dashboard em [http://localhost:8090](http://localhost:8090) — configure lá o
cluster `eventos-autorizacao-local` (já pré-configurado via variáveis de ambiente) para
ver mensagens, consumer groups e lag por partição.

## Ambiente completo

Este compose é independente, mas para o fluxo fim a fim funcionar (evento fluindo de
`arj-contratocommand` até `eventos-consumer`), use o ponto de entrada único da raiz do
repositório (`compose.yaml`, `docker compose up -d`) — ele inclui este compose junto com
Postgres, Floci, Valkey e as cinco aplicações, na ordem correta, sem exigir os passos manuais
que existiam aqui antes. Ver o `README.md` da raiz, seção "Começando". O provisionamento do
tópico/fila SNS/SQS (`infra/envs/local-messaging/`, via Terraform) continua um passo à parte.

## Limpar

```bash
docker compose -f infra/local/kafka/compose.yaml down -v
```

Remove containers e o volume `kafka_data` sem afetar Postgres, Floci ou as apps.
