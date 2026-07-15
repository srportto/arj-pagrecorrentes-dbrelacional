# arj-pagrecorrentes-dbrelacional

Sistema de **autorizações de pagamentos recorrentes** (PIX Automático e DDA Automático), composto por dois microserviços Java que operam sobre um banco PostgreSQL particionado temporalmente.

```
Cliente (escrita)           Cliente (leitura)
      │                           │
      ▼                           ▼
arj-contratocommand        arj-contratoquery
  (porta 8080)               (porta 8081)
  DB_READ_ONLY=false         DB_READ_ONLY=true
      │                           │
      └──────────┬────────────────┘
                 ▼
         PostgreSQL 16+
     (pg_partman + pg_cron)
```

## Microserviços

| Serviço | Porta | Responsabilidade | Read-Only |
|---------|-------|-----------------|-----------|
| [arj-contratocommand](apps/arj-contratocommand/README.md) | 8080 | Criar e cancelar autorizações (POST, PATCH) | Não |
| [arj-contratoquery](apps/arj-contratoquery/README.md) | 8081 | Listar e consultar autorizações (GET) | Sim |

Ambos compartilham o mesmo banco de dados e a mesma tabela `autorizacoes`, particionada por `id_particao_conta` (range 900–999). O UUID de cada autorização carrega a partição embutida (`ReversibleUUIDv7`), eliminando joins extras na leitura.

## Estrutura do Repositório

```
arj-pagrecorrentes-dbrelacional/
├── apps/                       # Código de aplicação
│   ├── arj-contratocommand/    # Microserviço de escrita (Java 25 + Spring Boot 4.0.7)
│   ├── arj-contratoquery/      # Microserviço de leitura (Java 25 + Spring Boot 4.0.7)
│   └── docker-compose.yml      # Ambiente local: as 2 apps + Postgres (partman/cron)
├── infra/                      # Código de infraestrutura (esqueleto Terraform, ver infra/README.md)
│   ├── modules/                 # Módulos Terraform reutilizáveis (networking, rds-postgres, ecs-*, observability)
│   ├── envs/{local,prod}/       # Composição dos módulos por ambiente
│   ├── bootstrap/               # State remoto (pré-requisito dos envs)
│   └── local/postgres/          # Dockerfile do Postgres 16 com pg_partman + pg_cron (dev local)
├── docs/
│   ├── arquitetura/                        # Diagramas de arquitetura
│   ├── info_build-my-image-and-execute.md  # Docker + PostgreSQL com partman/cron
│   ├── comandos-sql.txt                    # Scripts SQL de particionamento
│   ├── post-autorizacoes.txt               # Exemplos de payloads REST
│   └── resultado-poc/                      # POC do particionamento com UUIDv7
├── openspec/                  # Planejamento de mudanças (proposta → spec → tasks)
├── LICENSE                    # MIT
└── README.md                  # Este arquivo
```

## Pré-requisitos

| Ferramenta | Versão mínima |
|------------|--------------|
| Java (JDK) | 25+ |
| Maven | 3.9+ |
| PostgreSQL | 16+ (com `pg_partman` e `pg_cron`) |
| Docker | Qualquer versão recente |

> PostgreSQL é obrigatório — nenhum dos serviços possui fallback para H2 ou banco em memória.

## Começando

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
docker compose -f postgres-db-v16.yml up -d
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
| [infra/README.md](infra/README.md) | Topologia-alvo de infraestrutura (Terraform, ambientes, escopo) |
| [docs/info_build-my-image-and-execute.md](docs/info_build-my-image-and-execute.md) | Build e execução via Docker |
| [docs/comandos-sql.txt](docs/comandos-sql.txt) | Scripts SQL de particionamento |
| [docs/post-autorizacoes.txt](docs/post-autorizacoes.txt) | Exemplos de payloads REST |
| [docs/resultado-poc/](docs/resultado-poc/) | POC do particionamento com UUIDv7 reversível |

## Licença

MIT © 2026 Caique Porto — veja [LICENSE](LICENSE) para detalhes.
