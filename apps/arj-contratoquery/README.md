# arj-contratoquery

API REST de **leitura** de autorizações de produtos financeiros (PIX Automático e DDA Automático),
em arquitetura hexagonal, com particionamento temporal em PostgreSQL. Serviço somente leitura —
as escritas ficam no `arj-contratocommand` (porta 8080), com quem compartilha o mesmo banco.

Para arquitetura, a cascata de localização por id, armadilhas e checklist de commit, veja
[CLAUDE.md](CLAUDE.md) — este README cobre apenas como subir e testar a aplicação.

## Pré-requisitos

- **Java 25** (JDK 25+)
- **PostgreSQL 18** com `pg_partman` e `pg_cron` — sem fallback para H2
- Variáveis de ambiente obrigatórias: `DB_NAME`, `DB_USER_NAME`, `DB_PASSWORD`

## Variáveis de ambiente

```bash
# Obrigatórias
DB_NAME=contratoquery           # nome do banco (compartilhado com o command)
DB_USER_NAME=postgres
DB_PASSWORD=sua_senha_segura

# Opcionais (defaults seguros para leitura)
DB_READ_ONLY=true               # default: true — não alterar em produção
DB_TRANSACTION_ISOLATION=TRANSACTION_READ_COMMITTED
DB_POOL_MAX_SIZE=10
DB_POOL_MIN_IDLE=2

# opcional; default "local" quando omitido — produção deve setar "prod" explicitamente
SPRING_PROFILES_ACTIVE=local
```

## Build & Execução

```bash
mvn clean package              # compilar + testes + JAR
mvn clean package -DskipTests  # build rápido, sem testes
mvn spring-boot:run            # rodar localmente (porta 8081)
java -jar target/contratoquery-0.0.1-SNAPSHOT.jar
```

> **Maven Wrapper quebrado no Windows**: se `./mvnw.cmd` falhar, use `mvn` diretamente.

Acesse: `http://localhost:8081`

## Via Docker

A aplicação tem `Dockerfile` próprio (multi-stage, Fargate-ready). Para subir tudo de uma vez
(Postgres + as duas aplicações REST), use o `docker-compose.yml` em `apps/`
(ver [README raiz](../../README.md)):

```bash
cd ..
DB_NAME=db-csp-postgres DB_USER_NAME=docker DB_PASSWORD=sua_senha docker compose up -d --build
```

Para subir só o banco, o compose fica em `infra/local/postgres/` (raiz do repositório):

```bash
cd ../../infra/local/postgres
docker compose -f postgres-db-v18.yml up -d
```

## Testes

```bash
mvn test                                          # todos os testes
mvn test -Dtest=ConsultarAutorizacaoServiceTest   # classe específica
mvn clean verify                                  # com relatório de cobertura (JaCoCo)
# Abrir: target/site/jacoco/index.html
```

Testes unitários rodam sem banco. Testes de integração (`ConsultaCascataIntegrationTest`) exigem
PostgreSQL local no ar.

## Endpoints REST (base `/api/autorizacoes`)

| Método | Caminho | Descrição | Status |
|--------|---------|-----------|--------|
| GET | `/api/autorizacoes` | Listagem paginada por conta. Obrigatório: `idUnicoContaContratante`. Opcionais: `status`, `pagina`, `tamanho`, `ordenarPor` | 200 |
| GET | `/api/autorizacoes/{autorizacaoId}` | Consulta por id | 200 / 404 |
| GET | `/actuator/health` | Health-check (Actuator) | 200 / 503 |

> Não existem POST, PATCH ou DELETE nesta app — toda escrita fica no `arj-contratocommand`.

Contrato completo (parâmetros de borda, schema de resposta, códigos de erro): ver
[CLAUDE.md](CLAUDE.md#validações-e-códigos-de-erro) e
[docs/contrato-api-para-gateway.md](../../docs/contrato-api-para-gateway.md).

## Licença

MIT — veja [LICENSE](../../LICENSE) na raiz do repositório.
