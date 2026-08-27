# arj-pagrecorrentes-dbrelacional

Sistema de **autorizações de pagamentos recorrentes** (PIX Automático e DDA Automático), composto por cinco microserviços Java que operam sobre um banco PostgreSQL particionado temporalmente, mais uma Lambda Python agendada (`expurgo-particao`) que fecha o ciclo de expurgo desse particionamento.

```mermaid
flowchart TD
    ClienteEscrita["Cliente (escrita)"] --> Command["contratocommand<br/>porta 8080 · DB_READ_ONLY=false"]
    ClienteLeitura["Cliente (leitura)"] --> Query["contratoquery<br/>porta 8081 · DB_READ_ONLY=true"]

    Command --> TipoAutorizacao{"Tipo de<br/>autorização"}
    TipoAutorizacao -->|"DDA_AUTO"| DdaAtiva["nasce ATIVA<br/>(sem decisão/timer)"]
    TipoAutorizacao -->|"PIX_AUTO"| PixRecebida["nasce RECEBIDA<br/>(aguarda decisão)"]

    Command --> Postgres[("PostgreSQL 18<br/>pg_partman + pg_cron + pgvector")]
    Query --> Postgres

    Command -->|"publica evento após cada<br/>commit (criação/cancelamento/decisão)"| SNS["sns-estados-autorizacao (SNS)"]
    SNS -->|"subscription<br/>(raw delivery)"| SQS["SQS-eventos-autorizacao (SQS)"]
    SQS --> Producer["autorizacaostatus-producer<br/>porta 8082 · ponte SQS → Kafka"]
    Producer -->|"produz evento Avro<br/>(key determinística p/ dedupe a jusante)"| Kafka["eventos-autorizacao<br/>(tópico Kafka, Schema Registry)"]
    Kafka --> Consumer["eventos-consumer<br/>porta 8083"]

    SNS -->|"subscription filtrada<br/>(RECEPCAO+PIX_AUTO+SPI_J1)"| SQST["SQS-temporizacao-autorizacao (SQS)"]
    SQST --> Temporiza["temporiza-autorizacao<br/>porta 8084 · sem banco"]
    Temporiza -->|"ZADD (agenda)"| Valkey[("Valkey<br/>sorted set + stream")]
    Valkey -->|"vencido: XADD (script Lua)"| Temporiza
    Temporiza -->|"PATCH /decisao<br/>acao=EXPIRAR"| Command
```

## Ciclo de Expurgo (Ring Buffer)

`contratocommand` e `expurgo-particao` não se conectam entre si — cada um só conhece o Postgres.
São o escritor e o reclamador do mesmo ring buffer de partições `900`–`999`, fechando um ciclo que
nenhum evento de negócio atravessa:

```mermaid
flowchart LR
    Command["contratocommand<br/>ControleExpurgoAutorizacao"] -->|"autorização em estado terminal:<br/>move para gaveta 900-999"| PG[("PostgreSQL<br/>autorizacoes_pe900..999")]
    Scheduler["EventBridge Scheduler<br/>a cada 30 min"] --> Lambda["expurgo-particao<br/>(Python, Lambda)"]
    Lambda -->|"calcula alvo = escrita+2<br/>TRUNCATE se dado do ciclo anterior"| PG
    Cron["pg_cron<br/>(auditoria, sem escrita)"] -.->|"confere o que a Lambda afirmou"| PG
```

`expurgo-particao` não é acionada por evento — é agendada, e opera sobre a tabela `autorizacoes`
por fora do fluxo de requisição descrito acima. Detalhe completo:
[apps/expurgo-particao/README.md](apps/expurgo-particao/README.md) e a capability
[reclamacao-particao-expurgo](openspec/specs/reclamacao-particao-expurgo/spec.md).

## Estados da Autorização PIX_AUTO

Autorizações `PIX_AUTO` nascem `RECEBIDA` e aguardam decisão do cliente pagador (`PATCH
/decisao`) ou o vencimento de 10 minutos da jornada 1, temporizado por `temporiza-autorizacao`.
`DDA_AUTO` não passa por esse fluxo — nasce `ATIVA` diretamente (ver fluxograma acima).

```mermaid
stateDiagram-v2
    [*] --> RECEBIDA: criação (tipoJornada SPI_J1)
    RECEBIDA --> ATIVA: aprovação<br/>(PATCH /decisao, acao=APROVAR)
    RECEBIDA --> REJEITADA: rejeição do cliente<br/>(PATCH /decisao, acao=REJEITAR)
    RECEBIDA --> REJEITADA: timeout de 10min<br/>(temporiza-autorizacao, acao=EXPIRAR)
    ATIVA --> [*]
    REJEITADA --> [*]
```

## Microserviços

| Serviço | Porta | Responsabilidade | Read-Only |
|---------|-------|-----------------|-----------|
| [contratocommand](apps/contratocommand/README.md) | 8080 | Criar, cancelar e decidir autorizações (POST, PATCH); publica eventos de estado no SNS | Não |
| [contratoquery](apps/contratoquery/README.md) | 8081 | Listar e consultar autorizações (GET) | Sim |
| [autorizacaostatus-producer](apps/autorizacaostatus-producer/README.md) | 8082 | Ponte SQS → Kafka: consome a fila de eventos, converte para Avro e produz no tópico `eventos-autorizacao` com key determinística (dedupe é responsabilidade de quem consome) | N/A |
| [eventos-consumer](apps/eventos-consumer/README.md) | 8083 | Consome o tópico Kafka `eventos-autorizacao`, loga e confirma (ack) | N/A |
| [temporiza-autorizacao](apps/temporiza-autorizacao/README.md) | 8084 | Temporiza a jornada 1 do PIX_AUTO: agenda a expiração no Valkey e aciona `PATCH /decisao` no vencimento | N/A |

| App | Linguagem | Gatilho | Responsabilidade |
|---|---|---|---|
| [expurgo-particao](apps/expurgo-particao/README.md) | Python 3.13 (Lambda) | EventBridge Scheduler, a cada 30 min | Fecha o ring buffer de expurgo do `contratocommand` — ver "Ciclo de Expurgo" acima |

`expurgo-particao` não é um microserviço da tabela acima: não expõe porta HTTP, não é acionada por
evento de negócio nem por request — é invocação agendada, isolada numa tabela própria por não
compartilhar o modelo de disparo das outras cinco apps.

`contratocommand` e `contratoquery` compartilham o mesmo banco de dados e a mesma tabela `autorizacoes`, particionada por `id_particao_conta` (range 900–999). O UUID de cada autorização carrega a partição embutida (`ReversibleUUIDv7`), eliminando joins extras na leitura. `autorizacaostatus-producer`, `eventos-consumer` e `temporiza-autorizacao` não acessam o banco: os dois primeiros formam a ponte SQS → Kafka (a primeira consome a fila SQS alimentada pelos eventos publicados pelo `contratocommand` — ver [`infra/envs/local-messaging/`](infra/envs/local-messaging/) para provisionar tópico/filas no Floci — e produz no Kafka local, ver [`infra/local/kafka/`](infra/local/kafka/README.md); a segunda apenas consome esse tópico); `temporiza-autorizacao` consome uma fila **filtrada** do mesmo tópico SNS (só recepção de `PIX_AUTO` em `SPI_J1`), agenda no [Valkey local](infra/local/redis/README.md) e aciona de volta o `contratocommand` no vencimento de 10 minutos, sem nunca ler a tabela `autorizacoes`.

## Máquina de Estados: Autorização PIX_AUTO

Estados da autorização `PIX_AUTO` na jornada 1 (`SPI_J1`), decididos via `PATCH
/api/autorizacoes/{id}/decisao` no `contratocommand` (`DecidirAutorizacaoService` +
`StatusAutorizacao`). `DDA_AUTO` nasce direto em `ATIVA`, sem essa máquina de estados.

```mermaid
stateDiagram-v2
    [*] --> RECEBIDA: POST /api/autorizacoes<br/>(PIX_AUTO, SPI_J1)
    RECEBIDA --> EM_PROCESSO_ATIVACAO: PATCH /decisao<br/>acao=APROVAR
    EM_PROCESSO_ATIVACAO --> ATIVA
    RECEBIDA --> REJEITADA: PATCH /decisao<br/>acao=REJEITAR<br/>(motivo REJEITADA_PAGADOR)
    RECEBIDA --> REJEITADA: PATCH /decisao<br/>acao=EXPIRAR<br/>timeout 10min (temporiza-autorizacao)<br/>(motivo REJEITADA_SISTEMA_TIMEOUT_J1)
    ATIVA --> [*]
    REJEITADA --> [*]
```

> `RECEBIDA → EM_PROCESSO_ATIVACAO → ATIVA` acontece em uma única transação (`APROVAR`).
> A transição só é aceita a partir de `RECEBIDA` — mesmo o grafo completo de
> `StatusAutorizacao` permitindo `ATIVA → REJEITADA` por outro fluxo (cancelamento), a
> regra `TransicaoValidaDecisao` exige `statusAtual == RECEBIDA` explicitamente, tornando
> a rota seguros para chamada repetida at-least-once pelo `temporiza-autorizacao` sem
> "rejeitar" uma autorização já aprovada.

## Arquitetura de Conexão entre Serviços

Visão de infraestrutura: como os 5 microserviços se conectam entre si e com a mensageria
(SNS/SQS/Kafka) e o Valkey. Para o fluxo de negócio (estados da autorização, decisão
PIX_AUTO vs DDA_AUTO), veja o fluxograma no topo deste README.

```mermaid
flowchart LR
    subgraph Servicos["Microserviços (Java 25 + Spring Boot 4)"]
        CC["contratocommand<br/>:8080"]
        CQ["contratoquery<br/>:8081"]
        ASP["autorizacaostatus-producer<br/>:8082"]
        EC["eventos-consumer<br/>:8083"]
        TA["temporiza-autorizacao<br/>:8084"]
    end

    subgraph Mensageria["Mensageria (Floci local / SNS+SQS na AWS)"]
        SNS["SNS<br/>sns-estados-autorizacao"]
        SQS1["SQS<br/>SQS-eventos-autorizacao"]
        SQS2["SQS<br/>SQS-temporizacao-autorizacao<br/>(filtro: RECEPCAO+PIX_AUTO+SPI_J1)"]
        KAFKA["Kafka<br/>tópico eventos-autorizacao<br/>(Avro + Schema Registry)"]
    end

    PG[("PostgreSQL 18<br/>tabela autorizacoes")]
    VALKEY[("Valkey<br/>sorted set + stream")]

    CC --> PG
    CQ --> PG
    CC -->|"publica evento por commit"| SNS
    SNS --> SQS1
    SNS --> SQS2
    SQS1 --> ASP
    ASP -->|"produz Avro, key determinística"| KAFKA
    KAFKA --> EC
    SQS2 --> TA
    TA -->|"ZADD (agenda expiração)"| VALKEY
    VALKEY -->|"vencido: XADD (script Lua)"| TA
    TA -->|"PATCH /decisao acao=EXPIRAR"| CC
```

## Estrutura do Repositório

```
arj-pagrecorrentes-dbrelacional/
├── compose.yaml                # Ponto de entrada único do ambiente local (include: dos 5 abaixo)
├── apps/                       # Código de aplicação
│   ├── contratocommand/         # Microserviço de escrita (Java 25 + Spring Boot 4.0.7)
│   ├── contratoquery/           # Microserviço de leitura (Java 25 + Spring Boot 4.0.7)
│   ├── autorizacaostatus-producer/  # Ponte SQS -> Kafka (Java 25 + Spring Boot 4.0.7)
│   ├── eventos-consumer/            # Consumidora do tópico Kafka (Java 25 + Spring Boot 4.0.7)
│   ├── temporiza-autorizacao/       # Temporizador da jornada 1 do PIX_AUTO, sem banco (Java 25 + Spring Boot 4.0.7)
│   ├── expurgo-particao/            # Lambda agendada que fecha o ring buffer de expurgo (Python 3.13)
│   └── docker-compose.yml      # Ambiente local: as 5 apps Java (Postgres vem só de infra/local/postgres/)
├── testes-carga/               # Teste de carga (TPS) — módulo Maven independente (Gatling), ver "Teste de Carga (TPS)" abaixo
├── infra/                      # Código de infraestrutura (esqueleto Terraform, ver infra/README.md)
│   ├── modules/                 # Módulos Terraform reutilizáveis (networking, rds-postgres, ecs-*, elasticache-valkey, observability)
│   ├── envs/{local,local-messaging,prod}/  # Composição dos módulos por ambiente
│   ├── bootstrap/               # State remoto (pré-requisito dos envs)
│   ├── local/postgres/          # Fonte única do Postgres 18 local (pg_partman + pg_cron + pgvector, migrations)
│   ├── local/kafka/             # Kafka local standalone (broker KRaft, Schema Registry, Kafbat UI)
│   ├── local/floci/             # Floci local (emula SNS/SQS)
│   └── local/redis/             # Valkey local (sorted set + stream para temporiza-autorizacao)
├── docs/
│   ├── arquitetura/                        # Diagramas de arquitetura + POC de particionamento (Buffer Ring/UUIDv7)
│   ├── info_build-my-image-and-execute.md  # Docker + PostgreSQL com partman/cron
│   └── contrato-api-para-gateway.md        # Insumo temporário p/ montar o gateway (ver nota no topo do arquivo)
├── openspec/                  # Planejamento de mudanças (proposta → spec → tasks)
├── .env.example                # Modelo do .env único (raiz) — copie para .env
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

> `DB_PASSWORD` não tem valor padrão embutido nos arquivos de compose — a subida falha com erro
> explícito, nomeando a variável, se ela não estiver definida. Copie `.env.example` (raiz) para
> `.env` e defina sua própria senha local. Fonte única: não crie cópias de `.env` em `apps/` nem
> em `infra/local/*` — os composes desses caminhos leem do `.env` da raiz (veja `--env-file` na
> Opção C abaixo, para quando um deles sobe fora do compose de raiz).

### Opção A — Ponto de entrada único (recomendado)

Um único comando sobe o banco (com schema aplicado), a mensageria local (Floci, Kafka) e as
cinco aplicações — a ordem de subida está declarada no compose, não é conhecimento tácito:

```bash
docker compose up -d --build
```

- `contratocommand` → http://localhost:8080
- `contratoquery` → http://localhost:8081
- `autorizacaostatus-producer` → http://localhost:8082 — ponte SQS → Kafka
- `eventos-consumer` → http://localhost:8083 — loga cada evento consumido do Kafka
- `temporiza-autorizacao` — agenda e expira autorizações PIX_AUTO/SPI_J1; roda com 2 réplicas
  (`deploy.replicas`) e por isso **não publica porta no host** (sem endpoint de negócio, só
  `/actuator/health`, acessível via `docker compose exec` de dentro da rede)
- Dashboard do Kafka (mensagens, consumer groups, lag) → http://localhost:8090

Para o fluxo de eventos fim a fim funcionar (SNS → SQS → Kafka, e o timer da jornada 1), o
tópico/filas do Floci ainda precisam ser provisionados uma vez via Terraform — fora do escopo
deste compose (ver `infra/envs/local-messaging/`):

```bash
cd infra/envs/local-messaging && terraform init && terraform apply
```

Crie uma autorização `PIX_AUTO` com `tipoJornada: SPI_J1` no `contratocommand` e, sem uma
aprovação via `PATCH /api/autorizacoes/{id}/decisao`, ela é rejeitada automaticamente
(`REJEITADA_SISTEMA_TIMEOUT_J1`) 10 minutos depois.

### Opção B — Só um ambiente isolado

Cada compose de `infra/local/*` continua completo e válido por si — sobe sozinho, sem exigir
os demais no ar (ver o `README.md` de cada um: [postgres](infra/local/postgres/README.md),
[floci](infra/local/floci/README.md), [kafka](infra/local/kafka/README.md),
[redis](infra/local/redis/README.md)). Exemplo, só o banco:

```bash
cd infra/local/postgres
docker compose --env-file ../../../.env -f postgres-db-v18.yml up -d
```

### Opção C — Só as apps, contra infra já no ar

`apps/docker-compose.yml` permanece um arquivo próprio — útil para subir as cinco aplicações
sozinhas contra uma infra que já está no ar (pela Opção A ou pela B, ambiente por ambiente):

```bash
cd apps
docker compose --env-file ../.env up -d --build
```

### Opção D — Rodando uma aplicação manualmente (Maven)

Para depurar uma app fora do container, com a infra correspondente no ar:

```bash
cd apps/contratocommand
DB_NAME=db-csp-postgres DB_USER_NAME=docker DB_PASSWORD=sua_senha \
  mvn spring-boot:run
# Disponível em http://localhost:8080
```

Mesmo padrão para as outras quatro apps — `contratoquery` (8081, exige Postgres),
`autorizacaostatus-producer` (8082, exige Floci + Kafka), `eventos-consumer` (8083, exige Kafka)
e `temporiza-autorizacao` (8084, exige Floci + Valkey, e o `contratocommand` no ar para
acionar a decisão no vencimento).

> Consulte o README de cada app para a lista completa de variáveis de ambiente e comandos de build.

## Profiles Spring

Cada aplicação usa `application.yml` (configuração comum) mais `application-local.yml` e `application-prod.yml` (apenas o que difere entre ambientes). Não existe mais o profile `dev`.

- **Local** (padrão de desenvolvimento): ativado automaticamente quando `SPRING_PROFILES_ACTIVE` não é definido.
- **Produção**: **deve** definir `SPRING_PROFILES_ACTIVE=prod` explicitamente — o default `local` é só uma conveniência de desenvolvimento e não deve ser assumido em produção.

## CI

`.github/workflows/` mantém um workflow por preocupação, disparado só quando o path relevante muda:

| Workflow | O quê | Dispara em |
|---|---|---|
| `contrato-eventos.yml` | Compara as cópias espelhadas do contrato de evento (`AutorizacaoEventoPayload`, `.avsc`) | `apps/**`, `ci/contrato-eventos/**` |
| `ci-testesunitarios-<app>.yml` (uma por app, 6 no total) | Só testes unitários: `mvn test -Dtest='!*IntegrationTest'` nas 5 apps Java; `pytest --ignore=tests/test_rotina_integracao.py` em `expurgo-particao` (Python) | `apps/<app>/**` |

Testes de integração (Postgres, Floci, Valkey) não rodam no CI hoje — seguem manuais, com infra local
no ar (ver `infra/local/`).

## Teste de Carga (TPS)

`testes-carga/` (módulo Maven independente, Gatling) mede o TPS de criação/cancelamento/decisão
no `contratocommand`, o TPS de consulta no `contratoquery`, e o comportamento do pipeline
assíncrono (SNS/SQS/Kafka) sob carga — change `testes-de-carga-tps`, ver
[proposal.md](openspec/changes/testes-de-carga-tps/proposal.md) e
[design.md](openspec/changes/testes-de-carga-tps/design.md) para o racional completo (critério
de colapso multi-sinal, kill switches automáticos, classificação de erro em 3 categorias).

> Execução **só local** — os números abaixo refletem o ambiente `docker-compose` local, com
> `deploy.resources.limits` (CPU/mem) aplicado por container; não representam capacidade de
> produção.

Achados da execução mais recente (baseline + rodada agressiva,
[relatório completo](testes-carga/relatorios/RESUMO-baseline-2026-08-23.md)):

| Componente | Resultado |
|---|---|
| `contratocommand` (criação → decisão → cancelamento) | ~450 req/s sustentado sem colapso do servidor; teto real não encontrado — o gerador de carga local esbarrou em esgotamento de porta efêmera antes do sistema. HTTP 409 real de idempotência observado sob concorrência, corretamente não contado como colapso. |
| `contratoquery` (listagem) | **Colapso real confirmado** com massa de dado representativa (~281 mil linhas, 889 partições): p99 salta de 18ms (banco vazio) para **52 segundos**, com conexões fechadas pelo servidor. Confirma empiricamente o gargalo de scan sem poda por partição já documentado (`apps/contratoquery/CLAUDE.md`, armadilha 8). |
| Pipeline assíncrono (SNS→SQS→Kafka→`eventos-consumer`, SQS→`temporiza-autorizacao`) | 60 req/s sustentado por 8 min, 0 falhas, fila SQS e lag do consumer group Kafka em 0 durante toda a execução — sem sinal de colapso até essa taxa. |

Rodar: ver [testes-carga/README.md](testes-carga/README.md) (pré-requisitos, como executar cada
cenário, convenção de limpeza de massa de teste).

## Documentação

| Arquivo | Descrição |
|---------|-----------|
| [apps/contratocommand/README.md](apps/contratocommand/README.md) | Documentação completa do serviço de escrita |
| [apps/contratoquery/README.md](apps/contratoquery/README.md) | Documentação completa do serviço de leitura |
| [apps/autorizacaostatus-producer/README.md](apps/autorizacaostatus-producer/README.md) | Documentação completa da ponte SQS -> Kafka |
| [apps/eventos-consumer/README.md](apps/eventos-consumer/README.md) | Documentação completa da consumidora do tópico Kafka |
| [apps/temporiza-autorizacao/README.md](apps/temporiza-autorizacao/README.md) | Documentação completa do temporizador da jornada 1 do PIX_AUTO |
| [apps/expurgo-particao/README.md](apps/expurgo-particao/README.md) | Documentação completa da Lambda que fecha o ring buffer de expurgo |
| [infra/README.md](infra/README.md) | Topologia-alvo de infraestrutura (Terraform, ambientes, escopo) |
| [infra/envs/local-messaging/README.md](infra/envs/local-messaging/README.md) | Provisionamento do tópico SNS e das filas SQS (eventos + temporização) no Floci |
| [infra/local/postgres/README.md](infra/local/postgres/README.md) | Postgres 18 local (pg_partman, pg_cron, pgvector — subir, validar, adicionar extensão) |
| [infra/local/kafka/README.md](infra/local/kafka/README.md) | Kafka local standalone (broker, Schema Registry, dashboard) |
| [infra/local/redis/README.md](infra/local/redis/README.md) | Valkey local (sorted set + stream de expiração) |
| [docs/info_build-my-image-and-execute.md](docs/info_build-my-image-and-execute.md) | Build e execução via Docker |
| [graphify-out/README.md](graphify-out/README.md) | Grafo de conhecimento (graphify) — opcional, instalação em Windows/macOS/Linux, reduz consumo de tokens em sessões de IA |
| [infra/local/postgres/exemplos-queries.sql](infra/local/postgres/exemplos-queries.sql) | Scripts SQL de particionamento |
| [docs/arquitetura/modelo-dados-e-dados-poc-testada-para-essa-implementacao.md](docs/arquitetura/modelo-dados-e-dados-poc-testada-para-essa-implementacao.md) | POC do particionamento com UUIDv7 reversível (Buffer Ring) |
| [testes-carga/README.md](testes-carga/README.md) | Teste de carga (TPS): ferramenta, cenários, limites de recursos, como rodar |
| [testes-carga/relatorios/RESUMO-baseline-2026-08-23.md](testes-carga/relatorios/RESUMO-baseline-2026-08-23.md) | Resultados reais do baseline de TPS e da rodada agressiva (colapso real confirmado no `contratoquery`) |

## Licença

MIT © 2026 Caique Porto — veja [LICENSE](LICENSE) para detalhes.
