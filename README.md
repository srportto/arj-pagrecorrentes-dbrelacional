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
| [arj-contratocommand](aplicacoes/arj-contratocommand/README.md) | 8080 | Criar e cancelar autorizações (POST, PATCH) | Não |
| [arj-contratoquery](aplicacoes/arj-contratoquery/README.md) | 8081 | Listar e consultar autorizações (GET) | Sim |

Ambos compartilham o mesmo banco de dados e a mesma tabela `autorizacoes`, particionada por `id_particao_conta` (range 900–999). O UUID de cada autorização carrega a partição embutida (`ReversibleUUIDv7`), eliminando joins extras na leitura.

## Estrutura do Repositório

```
arj-pagrecorrentes-dbrelacional/
├── aplicacoes/
│   ├── arj-contratocommand/   # Microserviço de escrita (Java 25 + Spring Boot 4.0.4)
│   └── arj-contratoquery/     # Microserviço de leitura (Java 25 + Spring Boot 4.0.4)
├── docs/
│   ├── arquitetura/                        # Diagramas de arquitetura
│   ├── info_build-my-image-and-execute.md  # Docker + PostgreSQL com partman/cron
│   ├── comandos-sql.txt                    # Scripts SQL de particionamento
│   ├── post-autorizacoes.txt               # Exemplos de payloads REST
│   ├── run_postgres16_ja_com_cron_partman/ # Dockerfile do banco
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

### 1. Subir o banco de dados

```bash
# Build da imagem PostgreSQL com pg_partman + pg_cron
docker build -t contratocommand-db:16 \
  -f docs/run_postgres16_ja_com_cron_partman/Dockerfile .

# Subir o container
docker run -d \
  --name contratocommand-db \
  -e POSTGRES_DB=contratocommand \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=sua_senha \
  -p 5432:5432 \
  contratocommand-db:16
```

### 2. Rodar o serviço de escrita (command)

```bash
cd aplicacoes/arj-contratocommand

DB_NAME=contratocommand DB_USER_NAME=postgres DB_PASSWORD=sua_senha \
  mvn spring-boot:run
# Disponível em http://localhost:8080
```

### 3. Rodar o serviço de leitura (query)

```bash
cd aplicacoes/arj-contratoquery

DB_NAME=contratocommand DB_USER_NAME=postgres DB_PASSWORD=sua_senha \
  mvn spring-boot:run
# Disponível em http://localhost:8081
```

> Consulte o README de cada app para a lista completa de variáveis de ambiente e comandos de build.

## Documentação

| Arquivo | Descrição |
|---------|-----------|
| [aplicacoes/arj-contratocommand/README.md](aplicacoes/arj-contratocommand/README.md) | Documentação completa do serviço de escrita |
| [aplicacoes/arj-contratoquery/README.md](aplicacoes/arj-contratoquery/README.md) | Documentação completa do serviço de leitura |
| [docs/info_build-my-image-and-execute.md](docs/info_build-my-image-and-execute.md) | Build e execução via Docker |
| [docs/comandos-sql.txt](docs/comandos-sql.txt) | Scripts SQL de particionamento |
| [docs/post-autorizacoes.txt](docs/post-autorizacoes.txt) | Exemplos de payloads REST |
| [docs/resultado-poc/](docs/resultado-poc/) | POC do particionamento com UUIDv7 reversível |

## Licença

MIT © 2026 Caique Porto — veja [LICENSE](LICENSE) para detalhes.
