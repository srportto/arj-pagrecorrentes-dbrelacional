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

Classes de teste existentes: `ContratoqueryApplicationTests`, `ListarAutorizacoesServiceTest`, `ConsultarAutorizacaoServiceTest`, `AutorizacaoControllerTest`, `ApiExceptionHandlerTest`, `AutorizacaoDetalheResponseDtoTest`, `AutorizacaoResumidaResponseDtoTest`, `TipoProdutoConverterTest`, `TipoProdutoTest`, `StatusAutorizacaoTest`, `ReversibleUUIDv7Test`, `TipoJornadaAutorizacaoConverterTest`.

## Pré-requisitos

- **Java 25** (JDK 25+) — usa `public static void main()`; a forma `void main()` do Java 25 está pendente de suporte do maven plugin (ver `// TODO` no entrypoint)
- **PostgreSQL 18** com `pg_partman` e `pg_cron` — **sem fallback para H2**
- Variáveis de ambiente obrigatórias: `DB_NAME`, `DB_USER_NAME`, `DB_PASSWORD`
- Variáveis de ambiente opcionais (datasource, com defaults no `application.yaml`):
  - `DB_TRANSACTION_ISOLATION` — nível de isolamento (default `TRANSACTION_READ_COMMITTED`).
  - `DB_READ_ONLY` — modo de acesso (**default `true`** nesta app — somente leitura).
  - Pool HikariCP: `DB_POOL_MAX_SIZE`, `DB_POOL_MIN_IDLE`, `DB_POOL_CONNECTION_TIMEOUT`, `DB_POOL_IDLE_TIMEOUT`, `DB_POOL_MAX_LIFETIME`.
- Docker com PostgreSQL em `infra/local/postgres/` (raiz do repositório).
- Dockerfile próprio (multi-stage, Fargate-ready) nesta pasta; `apps/docker-compose.yml` sobe as 2 aplicações + Postgres de uma vez.
- Profiles Spring: `local` (padrão de desenvolvimento) e `prod` (deve ser setado explicitamente via `SPRING_PROFILES_ACTIVE=prod`) — não existe mais o profile `dev`.

## Stack

| Componente | Versão | Notas |
|---|---|---|
| Java | 25 | `void main()`; records imutáveis |
| Spring Boot | 4.0.7 | Web MVC, Data JPA, Validation, Actuator |
| Jetty | embutido | Container web (Tomcat excluído no `pom.xml`) |
| Lombok | 1.18.40 | `@Data`, `@Getter`, `@Builder`, `@AllArgsConstructor` |
| PostgreSQL | 18 | Particionamento com `pg_partman` + `pg_cron` |

> Sem MapStruct — não há mapeamento DTO↔Entity nesta app; os DTOs são construídos via `from()` estático.

## Endpoints reais (base `/api/autorizacoes`)

| Método | Caminho | Descrição |
|--------|---------|-----------|
| GET | `/api/autorizacoes` | Listagem paginada por conta. Params obrigatórios: `idUnicoContaContratante`. Opcionais: `status`, `pagina` (0), `tamanho` (20), `ordenarPor` (`dataHoraInclusao,desc`). → 200 |
| GET | `/api/autorizacoes/{autorizacaoId}` | Consulta por id. Extrai partição do UUID automaticamente. → 200 / 404 |
| GET | `/actuator/health` | Health-check (Actuator) com readiness de banco. → 200 (UP) / 503 (DOWN) |

> **Não existem** POST, PATCH ou DELETE nesta app — toda escrita fica no `arj-contratocommand` (porta 8080).

## Validações e códigos de erro

`ApiExceptionHandler` (`shared/interceptors/api/`) é o único mapeador entre exceção e status HTTP. Respostas seguem `LayoutErrosApiResponse`.

### Parâmetros de borda do `GET /api/autorizacoes`

| Param | Regra | Erro se violar |
|---|---|---|
| `idUnicoContaContratante` | opcional no controller; se **omitido**, o service valida nulidade | 422 — `BusinessException`: "idUnicoContaContratante é obrigatório" |
| `pagina` | deve ser **≥ 0** | 422 — `BusinessException`: "pagina deve ser maior ou igual a 0" |
| `tamanho` | deve ser **entre 1 e 100** (inclusive) | 422 — `BusinessException`: "tamanho deve estar entre 1 e 100" |
| `ordenarPor` | aceita apenas a **whitelist** de campos ordenáveis (atualmente: `dataHoraInclusao,desc` é o default; outros valores reconhecidos dependem do mapeamento de campos) | 422 — `BusinessException` listando os campos aceitos |

> **Teto de `tamanho` = 100** impede `?tamanho=999999`, que dispararia varredura completa de partições sem limite.
> **Quebra de contrato (mudança desta versão):** clientes que enviam `idUnicoContaContratante` vazio e esperavam 400 do Spring agora recebem **422** desta API — o controller deixou o binding ser opcional e a validação virou de negócio. Era 400 (Spring) → agora 422 (handler).

### Códigos de erro desta API

| Status | Exceção | Quando |
|---|---|---|
| 422 | `MethodArgumentNotValidException` | Falha de `@Valid` no body / params — payload do cliente não respeitou as validações declarativas. Resposta no formato `LayoutErrosApiValidationsResponse`, com `occurrences` por campo. |
| 404 | `ResourceNotFoundException` | Autorização inexistente no `GET /{autorizacaoId}` (ou UUID com partição fora da faixa 0–889) |
| 422 | `BusinessException` | Violação de regra de negócio — borda de paginação (ver tabela acima), `idUnicoContaContratante` ausente, `ordenarPor` desconhecido |
| 500 | `ApplicationException` | Erro inesperado de aplicação (resposta genérica; detalhe fica no log do servidor) |
| 500 | `Exception` (catch-all) | Qualquer outra exceção não mapeada (resposta genérica; detalhe fica no log) |

> **Convenção mantida (D3, 2026-08-09):** entrada inválida do cliente — tanto falha de formato (`@Valid`/`MethodArgumentNotValidException`) quanto violação de regra de negócio (`BusinessException`) — retorna **422**. A distinção entre as duas é carregada pelo **shape da resposta** (`LayoutErrosApiValidationsResponse` vs `LayoutErrosApiResponse`), não pelo primeiro byte do status. Decisão registrada em `openspec/changes/reconciliar-contrato-spec-doc/design.md` (D3).

> **Nenhuma resposta expõe nome de classe, stack trace, nome de tabela/coluna/constraint.** O log do servidor carrega a cadeia completa de causas.

## Arquitetura (hexagonal, 4 camadas)

```
entrypoint/   → AutorizacaoController + DTOs (AutorizacaoResumidaResponseDto, AutorizacaoDetalheResponseDto, PaginacaoResponseDto)
application/  → ListarAutorizacoesService, ConsultarAutorizacaoService, AutorizacaoRepository
domain/       → Entidades, Enums, Converters, Utilities (lógica pura, sem frameworks)
shared/       → Exceções (BusinessException, ApplicationException, ResourceNotFoundException), ApiExceptionHandler
```

### Fluxo de uma requisição GET (listagem)

```
AutorizacaoController.listar()
  └─ ListarAutorizacoesService.listar()
       ├─ valida idUnicoContaContratante (BusinessException se nulo)
       ├─ constrói Pageable (campo + direção)
       └─ AutorizacaoRepository.findByIdUnicoContaContratante() ← JPQL explícito
            └─ AutorizacaoResumidaResponseDto.from(autorizacao)
```

### Fluxo de uma requisição GET (consulta por id)

```
AutorizacaoController.consultarPorId()
  └─ ConsultarAutorizacaoService.consultarPorId()
       ├─ ReversibleUUIDv7.extract(uuid) ← extrai partição sem query adicional
       ├─ valida faixa de partição (0–889), lança 404 se fora
       └─ AutorizacaoRepository.findById(IdAutorizacao(uuid, particao))
            └─ AutorizacaoDetalheResponseDto.from(autorizacao)
```

### Particionamento temporal (crítico para leitura)

Tabela `autorizacoes` particionada por `id_particao_conta` (range **900–999** no command; a query extrai a partição do UUID reversível para localizar o registro sem query extra).

- `ConsultarAutorizacaoService` extrai a partição via `ReversibleUUIDv7.extract(uuid)` — UUIDs fora da faixa (0–889 neste serviço) resultam em 404 imediato, sem hit no banco.
- `AutorizacaoRepository` usa JPQL explícito (não usa métodos derivados do Spring Data) para garantir compatibilidade com o particionamento.

### Enums de domínio

`domain/enums/StatusAutorizacao` (espelho do `arj-contratocommand`) carrega o grafo de transições da máquina de estados via `podeTransicionarPara(destino)` — não usado por esta app (somente leitura), disponível para eventual validação futura. `domain/enums/TipoEventoAutorizacao` (8 valores, `porStatus(status)`) também é um espelho, sem uso atual nesta app. `domain/enums/TipoJornadaAutorizacao` (espelho parcial: só resolve código→enum, sem `obterJornadaAutorizacaoEnumPorNome` — esta app não recebe o header `tipoJornada`) existe apenas para o converter da coluna `tipo_jornada` funcionar; inclui `DESCONHECIDA(0)` para linhas anteriores à coluna existir.

### Coluna tipo_jornada

A entidade `Autorizacao` espelha a coluna `tipo_jornada` (`TipoJornadaAutorizacaoConverter`,
mesmo padrão de `TipoProdutoConverter`). Não é exposta nos DTOs de resposta
(`AutorizacaoDetalheResponseDto`/`AutorizacaoResumidaResponseDto`) — expor ou não é decisão de
contrato de API em aberto (ver `design.md` da mudança `temporizacao-jornada-01-pix-auto`,
Open Questions), não bloqueante para esta app funcionar.

## Armadilhas críticas

1. **Esta app é somente leitura** — `DB_READ_ONLY=true` por padrão. Não tente usar `@Transactional` para escrita aqui.
2. **Porta 8081**, não 8080 (que é do `arj-contratocommand`).
3. **Não há Strategy Pattern** — sem orquestradores de contratação/cancelamento, sem use cases `Criar*` ou `Cancelar*`.
4. **Sem MapStruct** — conversão feita via `from()` estático nos DTOs (ex.: `AutorizacaoResumidaResponseDto.from(autorizacao)`).
5. **Container é Jetty**, não Tomcat — o `pom.xml` exclui o Tomcat explicitamente.
6. **Faixa de partição na leitura**: `ConsultarAutorizacaoService` valida partição 0–889 (diferente da faixa 900–999 usada na escrita) — UUIDs fora disso → 404 sem consultar o banco.
7. **Queries JPQL explícitas** — `AutorizacaoRepository` não usa métodos derivados; cuidado ao renomear campos da entidade.

## Checklist antes do commit

- [ ] `mvn test` passa
- [ ] `mvn clean compile` sem erros
- [ ] Endpoints mantidos como GET — sem adicionar POST/PATCH
- [ ] Se mexeu em `ConsultarAutorizacaoService`, verificar a faixa de partição (0–889)
- [ ] DTOs (records) recriados, não mutados
