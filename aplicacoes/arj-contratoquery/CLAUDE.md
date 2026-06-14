# CLAUDE.md

> Guia para agentes de IA (Claude Code, Copilot, etc.) trabalharem neste repositório.
> **Este arquivo e `AGENTS.md` são espelhos — mantenha-os idênticos ao editar.**

API REST de **leitura de autorizações de produtos financeiros** (PIX Automático e DDA Automático), em **arquitetura hexagonal**, com **particionamento temporal** em PostgreSQL. Este serviço é **somente leitura** — as operações de escrita ficam no `arj-contratocommand` (porta 8080).

## Comece por aqui

Leia nesta ordem:
1. [AutorizacaoController.java](src/main/java/br/com/srportto/contratoquery/entrypoint/AutorizacaoController.java) — os 2 endpoints GET
2. [ListarAutorizacoesService.java](src/main/java/br/com/srportto/contratoquery/application/autorizacao/ListarAutorizacoesService.java) — listagem paginada com filtro de status
3. [ConsultarAutorizacaoService.java](src/main/java/br/com/srportto/contratoquery/application/autorizacao/ConsultarAutorizacaoService.java) — busca por id com extração de partição do UUID
4. [Autorizacao.java](src/main/java/br/com/srportto/contratoquery/domain/entities/Autorizacao.java) — entidade de domínio com particionamento

## Build & Testes

```bash
mvn clean package                                    # Compilar + testes + JAR
mvn spring-boot:run                                  # Rodar localmente (porta 8081)
mvn test                                             # Todos os testes
mvn test -Dtest=ListarAutorizacoesServiceTest        # Classe específica
mvn test -Dtest=ListarAutorizacoesServiceTest#metodo # Método específico
```

> **Maven Wrapper quebrado no Windows**: se `./mvnw.cmd` falhar, use `mvn` diretamente.

Classes de teste existentes: `ContratoqueryApplicationTests`, `ListarAutorizacoesServiceTest`, `ConsultarAutorizacaoServiceTest`, `AutorizacaoControllerTest`, `ApiExceptionHandlerTest`, `AutorizacaoDetalheResponseDtoTest`, `AutorizacaoResumidaResponseDtoTest`, `TipoProdutoConverterTest`, `AutorizacaoTest` + testes de enums e utilities.

## Pré-requisitos

- **Java 25** (JDK 25+) — usa `void main()` em vez de `public static void main()`
- **PostgreSQL 16+** com `pg_partman` e `pg_cron` — **sem fallback para H2**
- Variáveis de ambiente obrigatórias: `DB_NAME`, `DB_USER_NAME`, `DB_PASSWORD`
- Variáveis de ambiente opcionais (datasource, com defaults no `application.yaml`):
  - `DB_TRANSACTION_ISOLATION` — nível de isolamento (default `TRANSACTION_READ_COMMITTED`).
  - `DB_READ_ONLY` — modo de acesso (**default `true`** nesta app — somente leitura).
  - Pool HikariCP: `DB_POOL_MAX_SIZE`, `DB_POOL_MIN_IDLE`, `DB_POOL_CONNECTION_TIMEOUT`, `DB_POOL_IDLE_TIMEOUT`, `DB_POOL_MAX_LIFETIME`.
- Docker com PostgreSQL em `run_postgres16_ja_com_cron_partman/` (na raiz do repositório).

## Stack

| Componente | Versão | Notas |
|---|---|---|
| Java | 25 | `void main()`; records imutáveis |
| Spring Boot | 4.0.4 | Web MVC, Data JPA, Validation, Actuator |
| Jetty | embutido | Container web (Tomcat excluído no `pom.xml`) |
| Lombok | 1.18.40 | `@Data`, `@Getter`, `@Builder`, `@AllArgsConstructor` |
| PostgreSQL | 16+ | Particionamento com `pg_partman` + `pg_cron` |

> Sem MapStruct — não há mapeamento DTO↔Entity nesta app; os DTOs são construídos via `from()` estático.

## Endpoints reais (base `/api/autorizacoes`)

| Método | Caminho | Descrição |
|--------|---------|-----------|
| GET | `/api/autorizacoes` | Listagem paginada por conta. Params obrigatórios: `idUnicoContaContratante`. Opcionais: `status`, `pagina` (0), `tamanho` (20), `ordenarPor` (`dataHoraInclusao,desc`). → 200 |
| GET | `/api/autorizacoes/{autorizacaoId}` | Consulta por id. Extrai partição do UUID automaticamente. → 200 / 404 |
| GET | `/actuator/health` | Health-check (Actuator) com readiness de banco. → 200 (UP) / 503 (DOWN) |

> **Não existem** POST, PATCH ou DELETE nesta app — toda escrita fica no `arj-contratocommand` (porta 8080).

## Arquitetura (hexagonal, 4 camadas)

```
entrypoint/   → AutorizacaoController + DTOs (AutorizacaoResumidaResponseDto, AutorizacaoDetalheResponseDto, PaginacaoResponseDto)
application/  → ListarAutorizacoesService, ConsultarAutorizacaoService, AutorizacaoQueryRepository
domain/       → Entidades, Enums, Converters, Utilities (lógica pura, sem frameworks)
shared/       → Exceções (BusinessException, ApplicationException, ResourceNotFoundException), ApiExceptionHandler
```

### Fluxo de uma requisição GET (listagem)

```
AutorizacaoController.listar()
  └─ ListarAutorizacoesService.listar()
       ├─ valida idUnicoContaContratante (BusinessException se nulo)
       ├─ constrói Pageable (campo + direção)
       └─ AutorizacaoQueryRepository.findByIdUnicoContaContratante() ← JPQL explícito
            └─ AutorizacaoResumidaResponseDto.from(autorizacao)
```

### Fluxo de uma requisição GET (consulta por id)

```
AutorizacaoController.consultarPorId()
  └─ ConsultarAutorizacaoService.consultarPorId()
       ├─ ReversibleUUIDv7.extract(uuid) ← extrai partição sem query adicional
       ├─ valida faixa de partição (0–889), lança 404 se fora
       └─ AutorizacaoQueryRepository.findById(IdAutorizacao(uuid, particao))
            └─ AutorizacaoDetalheResponseDto.from(autorizacao)
```

### Particionamento temporal (crítico para leitura)

Tabela `autorizacoes` particionada por `id_particao_conta` (range **900–999** no command; a query extrai a partição do UUID reversível para localizar o registro sem query extra).

- `ConsultarAutorizacaoService` extrai a partição via `ReversibleUUIDv7.extract(uuid)` — UUIDs fora da faixa (0–889 neste serviço) resultam em 404 imediato, sem hit no banco.
- `AutorizacaoQueryRepository` usa JPQL explícito (não usa métodos derivados do Spring Data) para garantir compatibilidade com o particionamento.

### Exceções e códigos HTTP

Tratadas em `shared/interceptors/api/ApiExceptionHandler`.

| Origem | HTTP | Quando |
|--------|------|--------|
| `BusinessException` | 422 | Parâmetro inválido (ex.: status inexistente) |
| `ResourceNotFoundException` | 404 | Autorização não encontrada |
| `ApplicationException` | 500 | Erro inesperado de sistema |

## Armadilhas críticas

1. **Esta app é somente leitura** — `DB_READ_ONLY=true` por padrão. Não tente usar `@Transactional` para escrita aqui.
2. **Porta 8081**, não 8080 (que é do `arj-contratocommand`).
3. **Não há Strategy Pattern** — sem orquestradores de contratação/cancelamento, sem use cases `Criar*` ou `Cancelar*`.
4. **Sem MapStruct** — conversão feita via `from()` estático nos DTOs (ex.: `AutorizacaoResumidaResponseDto.from(autorizacao)`).
5. **Container é Jetty**, não Tomcat — o `pom.xml` exclui o Tomcat explicitamente.
6. **Faixa de partição na leitura**: `ConsultarAutorizacaoService` valida partição 0–889 (diferente da faixa 900–999 usada na escrita) — UUIDs fora disso → 404 sem consultar o banco.
7. **Queries JPQL explícitas** — `AutorizacaoQueryRepository` não usa métodos derivados; cuidado ao renomear campos da entidade.

## Checklist antes do commit

- [ ] `mvn test` passa
- [ ] `mvn clean compile` sem erros
- [ ] Endpoints mantidos como GET — sem adicionar POST/PATCH
- [ ] Se mexeu em `ConsultarAutorizacaoService`, verificar a faixa de partição (0–889)
- [ ] DTOs (records) recriados, não mutados
