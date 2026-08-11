# arj-pagrecorrentes-dbrelacional

Sistema de **autorizações de pagamentos recorrentes** (PIX Automático e DDA Automático), composto por cinco microserviços Java que operam sobre um banco PostgreSQL particionado temporalmente.

```mermaid
flowchart TD
    ClienteEscrita["Cliente (escrita)"] --> Command["arj-contratocommand<br/>porta 8080 · DB_READ_ONLY=false"]
    ClienteLeitura["Cliente (leitura)"] --> Query["arj-contratoquery<br/>porta 8081 · DB_READ_ONLY=true"]

    Command --> Postgres[("PostgreSQL 18<br/>pg_partman + pg_cron + pgvector")]
    Query --> Postgres

    Command -->|"publica evento após cada<br/>commit (criação/cancelamento/decisão)"| SNS["sns-estados-autorizacao (SNS)"]
    SNS -->|"subscription<br/>(raw delivery)"| SQS["SQS-eventos-autorizacao (SQS)"]
    SQS --> Producer["autorizacaostatus-producer<br/>porta 8082 · ponte SQS → Kafka"]
    Producer -->|"produz evento Avro<br/>(idempotente)"| Kafka["eventos-autorizacao<br/>(tópico Kafka, Schema Registry)"]
    Kafka --> Consumer["eventos-consumer<br/>porta 8083"]

    SNS -->|"subscription filtrada<br/>(RECEPCAO+PIX_AUTO+SPI_J1)"| SQST["SQS-temporizacao-autorizacao (SQS)"]
    SQST --> Temporiza["temporiza-autorizacao<br/>porta 8084 · sem banco"]
    Temporiza -->|"ZADD (agenda)"| Valkey[("Valkey<br/>sorted set + stream")]
    Valkey -->|"vencido: XADD (script Lua)"| Temporiza
    Temporiza -->|"PATCH /decisao<br/>acao=EXPIRAR"| Command
```

## Microserviços

| Serviço | Porta | Responsabilidade | Read-Only |
|---------|-------|-----------------|-----------|
| [arj-contratocommand](apps/arj-contratocommand/README.md) | 8080 | Criar, cancelar e decidir autorizações (POST, PATCH); publica eventos de estado no SNS | Não |
| [arj-contratoquery](apps/arj-contratoquery/README.md) | 8081 | Listar e consultar autorizações (GET) | Sim |
| [autorizacaostatus-producer](apps/autorizacaostatus-producer/README.md) | 8082 | Ponte SQS → Kafka: consome a fila de eventos, converte para Avro e produz no tópico `eventos-autorizacao` de forma idempotente | N/A |
| [eventos-consumer](apps/eventos-consumer/README.md) | 8083 | Consome o tópico Kafka `eventos-autorizacao`, loga e confirma (ack) | N/A |
| [temporiza-autorizacao](apps/temporiza-autorizacao/README.md) | 8084 | Temporiza a jornada 1 do PIX_AUTO: agenda a expiração no Valkey e aciona `PATCH /decisao` no vencimento | N/A |

`arj-contratocommand` e `arj-contratoquery` compartilham o mesmo banco de dados e a mesma tabela `autorizacoes`, particionada por `id_particao_conta` (range 900–999). O UUID de cada autorização carrega a partição embutida (`ReversibleUUIDv7`), eliminando joins extras na leitura. `autorizacaostatus-producer`, `eventos-consumer` e `temporiza-autorizacao` não acessam o banco: os dois primeiros formam a ponte SQS → Kafka (a primeira consome a fila SQS alimentada pelos eventos publicados pelo `arj-contratocommand` — ver [`infra/envs/local-messaging/`](infra/envs/local-messaging/) para provisionar tópico/filas no Floci — e produz no Kafka local, ver [`infra/local/kafka/`](infra/local/kafka/README.md); a segunda apenas consome esse tópico); `temporiza-autorizacao` consome uma fila **filtrada** do mesmo tópico SNS (só recepção de `PIX_AUTO` em `SPI_J1`), agenda no [Valkey local](infra/local/redis/README.md) e aciona de volta o `arj-contratocommand` no vencimento de 10 minutos, sem nunca ler a tabela `autorizacoes`.

## Estrutura do Repositório

```
arj-pagrecorrentes-dbrelacional/
├── apps/                       # Código de aplicação
│   ├── arj-contratocommand/         # Microserviço de escrita (Java 25 + Spring Boot 4.0.7)
│   ├── arj-contratoquery/           # Microserviço de leitura (Java 25 + Spring Boot 4.0.7)
│   ├── autorizacaostatus-producer/  # Ponte SQS -> Kafka (Java 25 + Spring Boot 4.0.7)
│   ├── eventos-consumer/            # Consumidora do tópico Kafka (Java 25 + Spring Boot 4.0.7)
│   ├── temporiza-autorizacao/       # Temporizador da jornada 1 do PIX_AUTO, sem banco (Java 25 + Spring Boot 4.0.7)
│   └── docker-compose.yml      # Ambiente local: as 2 apps de leitura/escrita + Postgres (partman/cron/pgvector)
├── infra/                      # Código de infraestrutura (esqueleto Terraform, ver infra/README.md)
│   ├── modules/                 # Módulos Terraform reutilizáveis (networking, rds-postgres, ecs-*, elasticache-valkey, observability)
│   ├── envs/{local,local-messaging,prod}/  # Composição dos módulos por ambiente
│   ├── bootstrap/               # State remoto (pré-requisito dos envs)
│   ├── local/postgres/          # Dockerfile do Postgres 18 com pg_partman + pg_cron + pgvector (dev local)
│   ├── local/kafka/             # Kafka local standalone (broker KRaft, Schema Registry, Kafbat UI)
│   └── local/redis/             # Valkey local (sorted set + stream para temporiza-autorizacao)
├── docs/
│   ├── arquitetura/                        # Diagramas de arquitetura + POC de particionamento (Buffer Ring/UUIDv7)
│   ├── info_build-my-image-and-execute.md  # Docker + PostgreSQL com partman/cron
│   ├── post-autorizacoes.txt               # Exemplos de payloads REST
│   └── contrato-api-para-gateway.md        # Insumo temporário p/ montar o gateway (ver nota no topo do arquivo)
├── openspec/                  # Planejamento de mudanças (proposta → spec → tasks)
├── LICENSE                    # MIT
└── README.md                  # Este arquivo
```

## Pré-requisitos

| Ferramenta | Versão mínima |
|------------|--------------|
| Java (JDK) | 25+ |
| Maven | 3.9+ |
| PostgreSQL | 18 (com `pg_partman`, `pg_cron` e `pgvector`) |
| Docker | Qualquer versão recente |

> PostgreSQL 18 é obrigatório — nenhum dos serviços possui fallback para H2 ou banco em memória.

## Começando

> `DB_PASSWORD` não tem mais valor padrão embutido nos arquivos de compose — a subida falha com
> erro explícito se a variável não estiver definida. Copie `apps/.env.example` para `apps/.env`
> (e `infra/local/postgres/.env.example` para `infra/local/postgres/.env`, se for usar a Opção B)
> e defina sua própria senha local, ou passe `DB_PASSWORD` inline como nos exemplos abaixo.

### Opção A — Docker Compose (recomendado)

Sobe o Postgres (partman/cron) e as duas aplicações com um único comando:

```bash
cd apps
DB_NAME=db-csp-postgres DB_USER_NAME=docker DB_PASSWORD=sua_senha \
  docker compose up -d --build
```

- `arj-contratocommand` → http://localhost:8080
- `arj-contratoquery` → http://localhost:8081

### Opção B — Rodando manualmente

#### 1. Subir o banco de dados

```bash
cd infra/local/postgres
DB_PASSWORD=sua_senha docker compose -f postgres-db-v18.yml up -d
```

#### 2. Rodar o serviço de escrita (command)

```bash
cd apps/arj-contratocommand

DB_NAME=db-csp-postgres DB_USER_NAME=docker DB_PASSWORD=sua_senha \
  mvn spring-boot:run
# Disponível em http://localhost:8080
```

#### 3. Rodar o serviço de leitura (query)

```bash
cd apps/arj-contratoquery

DB_NAME=db-csp-postgres DB_USER_NAME=docker DB_PASSWORD=sua_senha \
  mvn spring-boot:run
# Disponível em http://localhost:8081
```

#### 4. (Opcional) Eventos de autorização — SNS/SQS → Kafka fim a fim

Para ver os eventos de criação/cancelamento fluindo por SNS/SQS até o Kafka, suba
nesta ordem o [Floci](infra/local/floci/README.md), o tópico/fila via
[`infra/envs/local-messaging/`](infra/envs/local-messaging/) e o
[Kafka local](infra/local/kafka/README.md) (broker, Schema Registry, dashboard):

```bash
docker compose -f infra/local/floci/compose.yaml up -d
cd infra/envs/local-messaging && terraform init && terraform apply
cd ../../local/kafka && docker compose -f compose.yaml up -d

cd ../../../apps/autorizacaostatus-producer
mvn spring-boot:run
# Disponível em http://localhost:8082 — ponte SQS -> Kafka

cd ../eventos-consumer
mvn spring-boot:run
# Disponível em http://localhost:8083 — loga cada evento consumido do Kafka
```

Dashboard do Kafka (mensagens, consumer groups, lag): http://localhost:8090.

#### 5. (Opcional) Temporização da jornada 1 do PIX_AUTO

Exige o Floci e o Terraform de `local-messaging` do passo anterior (já provisiona a fila
filtrada `SQS-temporizacao-autorizacao`), mais o [Valkey local](infra/local/redis/README.md):

```bash
docker compose -f infra/local/redis/compose.yaml up -d

cd apps/temporiza-autorizacao
mvn spring-boot:run
# Disponível em http://localhost:8084 — agenda e expira autorizações PIX_AUTO/SPI_J1
```

Crie uma autorização `PIX_AUTO` com `tipoJornada: SPI_J1` no `arj-contratocommand` e, sem
uma aprovação via `PATCH /api/autorizacoes/{id}/decisao`, ela é rejeitada automaticamente
(`REJEITADA_SISTEMA_TIMEOUT_J1`) 10 minutos depois.

> Consulte o README de cada app para a lista completa de variáveis de ambiente e comandos de build.

## Profiles Spring

Cada aplicação usa `application.yml` (configuração comum) mais `application-local.yml` e `application-prod.yml` (apenas o que difere entre ambientes). Não existe mais o profile `dev`.

- **Local** (padrão de desenvolvimento): ativado automaticamente quando `SPRING_PROFILES_ACTIVE` não é definido.
- **Produção**: **deve** definir `SPRING_PROFILES_ACTIVE=prod` explicitamente — o default `local` é só uma conveniência de desenvolvimento e não deve ser assumido em produção.

## Documentação

| Arquivo | Descrição |
|---------|-----------|
| [apps/arj-contratocommand/README.md](apps/arj-contratocommand/README.md) | Documentação completa do serviço de escrita |
| [apps/arj-contratoquery/README.md](apps/arj-contratoquery/README.md) | Documentação completa do serviço de leitura |
| [apps/autorizacaostatus-producer/README.md](apps/autorizacaostatus-producer/README.md) | Documentação completa da ponte SQS -> Kafka |
| [apps/eventos-consumer/README.md](apps/eventos-consumer/README.md) | Documentação completa da consumidora do tópico Kafka |
| [apps/temporiza-autorizacao/README.md](apps/temporiza-autorizacao/README.md) | Documentação completa do temporizador da jornada 1 do PIX_AUTO |
| [infra/README.md](infra/README.md) | Topologia-alvo de infraestrutura (Terraform, ambientes, escopo) |
| [infra/envs/local-messaging/README.md](infra/envs/local-messaging/README.md) | Provisionamento do tópico SNS e das filas SQS (eventos + temporização) no Floci |
| [infra/local/kafka/README.md](infra/local/kafka/README.md) | Kafka local standalone (broker, Schema Registry, dashboard) |
| [infra/local/redis/README.md](infra/local/redis/README.md) | Valkey local (sorted set + stream de expiração) |
| [docs/info_build-my-image-and-execute.md](docs/info_build-my-image-and-execute.md) | Build e execução via Docker |
| [infra/local/postgres/exemplos-queries.sql](infra/local/postgres/exemplos-queries.sql) | Scripts SQL de particionamento |
| [docs/post-autorizacoes.txt](docs/post-autorizacoes.txt) | Exemplos de payloads REST |
| [docs/arquitetura/modelo-dados-e-dados-poc-testada-para-essa-implementacao.md](docs/arquitetura/modelo-dados-e-dados-poc-testada-para-essa-implementacao.md) | POC do particionamento com UUIDv7 reversível (Buffer Ring) |

## Licença

MIT © 2026 Caique Porto — veja [LICENSE](LICENSE) para detalhes.
