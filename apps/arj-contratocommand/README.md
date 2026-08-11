# arj-contratocommand

API REST de **escrita** de autorizações de produtos financeiros (PIX Automático e DDA Automático),
em arquitetura hexagonal, com particionamento temporal em PostgreSQL. Cria, cancela e decide
autorizações; publica eventos de estado no SNS após cada operação confirmada.

Para arquitetura, fluxos, armadilhas e checklist de commit, veja
[CLAUDE.md](CLAUDE.md) — este README cobre apenas como subir e testar a aplicação.

## Pré-requisitos

- **Java 25** (JDK 25+)
- **PostgreSQL 18** com `pg_partman`, `pg_cron` e `pgvector` — sem fallback para H2
- Variáveis de ambiente obrigatórias: `DB_NAME`, `DB_USER_NAME`, `DB_PASSWORD`

## Variáveis de ambiente obrigatórias

```bash
DB_NAME=contratocommand
DB_USER_NAME=postgres
DB_PASSWORD=sua_senha_segura

# opcional; default "local" quando omitido — produção deve setar "prod" explicitamente
SPRING_PROFILES_ACTIVE=local
```

## Build & Compilação

```bash
mvn clean package              # compilar + testes + JAR
mvn clean package -DskipTests  # build rápido, sem testes
mvn clean verify               # com verificações de qualidade
```

> **Maven Wrapper quebrado no Windows**: se `./mvnw.cmd` falhar, use `mvn` diretamente.

## Executar a aplicação

**Via Maven:**
```bash
mvn spring-boot:run
```

**Via JAR:**
```bash
java -jar target/contratocommand-0.0.1-SNAPSHOT.jar
```

Acesse: `http://localhost:8080`

## Via Docker

A aplicação tem `Dockerfile` próprio (multi-stage, Fargate-ready). Para subir tudo de uma vez
(Postgres + as duas aplicações REST), use o `docker-compose.yml` em `apps/`
(ver [README raiz](../../README.md)):

```bash
cd ..
DB_NAME=db-csp-postgres DB_USER_NAME=docker DB_PASSWORD=sua_senha docker compose up -d --build
```

Para subir só o banco (PostgreSQL 18 com `pg_partman` + `pg_cron` + `pgvector`), o compose fica em
`infra/local/postgres/` (raiz do repositório):

```bash
cd ../../infra/local/postgres
docker compose -f postgres-db-v18.yml up -d
```

## Testes

```bash
mvn test                                              # todos os testes
mvn test -Dtest=ControleExpurgoAutorizacaoTest        # classe específica
mvn clean test jacoco:report                          # com cobertura
# Abrir: target/site/jacoco/index.html
```

Testes de integração exigem PostgreSQL rodando localmente.

## Endpoints REST (base `/api/autorizacoes`)

| Método | Caminho | Descrição | Status |
|--------|---------|-----------|--------|
| POST | `/api/autorizacoes` | Criar autorização (multi-produto) | 201 |
| PATCH | `/api/autorizacoes/{idAutorizacao}/cancelar` | Cancelar (header `tipoProduto` obrigatório) | 200 |
| PATCH | `/api/autorizacoes/{idAutorizacao}/decisao` | Decisão sobre autorização em `RECEBIDA` (jornada 1 do PIX_AUTO) — `acao` = `APROVAR`\|`REJEITAR`\|`EXPIRAR` (header `tipoProduto` obrigatório) | 200 (aplicada) / 422 (status não permite) |

Contrato completo (schema de request/response, exemplos, códigos de erro): ver
[CLAUDE.md](CLAUDE.md#códigos-de-erro-handler-global) e
[docs/contrato-api-para-gateway.md](../../docs/contrato-api-para-gateway.md).

## Licença

MIT — veja [LICENSE](../../LICENSE) na raiz do repositório.
