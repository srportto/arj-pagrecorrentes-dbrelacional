# CLAUDE.md

> Guia para agentes de IA (Claude Code, Copilot, etc.) trabalharem neste repositório.
> **Este arquivo e `AGENTS.md` são espelhos — mantenha-os idênticos ao editar.**

API REST de **leitura de autorizações de produtos financeiros** (PIX Automático e DDA Automático), em **arquitetura hexagonal**, com **particionamento temporal** em PostgreSQL. Este serviço é **somente leitura** — as operações de escrita ficam no `contratocommand` (porta 8080).

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
  - `hikari.connection-init-sql` fixa `plan_cache_mode = force_generic_plan` em toda conexão física do pool (não configurável por env var — ver armadilha 8).
- Docker com PostgreSQL em `infra/local/postgres/` (raiz do repositório) — fonte única do Postgres local.
- Dockerfile próprio (multi-stage, Fargate-ready) nesta pasta; `apps/docker-compose.yml` sobe as cinco aplicações (sem Postgres — ver `infra/local/postgres/`). Para o ambiente local completo num só comando, use o `compose.yaml` da raiz.
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

> **Não existem** POST, PATCH ou DELETE nesta app — toda escrita fica no `contratocommand` (porta 8080).

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
| 404 | `ResourceNotFoundException` | Autorização inexistente no `GET /{autorizacaoId}` — significa "não existe em partição alguma", após esgotar os níveis habilitados da cascata. Também cobre UUID com partição embutida fora da faixa 0–889, aí sem tocar no banco |
| 500 | `ApplicationException` | Mesma autorização encontrada em mais de uma partição — corrupção, provável resíduo de transferência de partição interrompida. Nenhuma linha é escolhida |
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
       ├─ ReversibleUUIDv7.extract(uuid) ← extrai a partição de CRIAÇÃO, sem query adicional
       ├─ valida faixa de partição (0–889), lança 404 se fora — sem tocar no banco
       └─ cascata de localização (para no primeiro nível que encontrar):
            N1  findById(IdAutorizacao(uuid, particao))          1 part.   ~3 ms  → ativa
            N2  buscarNaFaixaDeExpurgo(uuid, 900)              100 part.  ~16 ms  → terminal
            N3  buscarEmOutrasParticoesQuentes(uuid, 900, part) 888 part. ~137 ms → anomalia + WARN
            └─ AutorizacaoDetalheResponseDto.from(autorizacao)
```

**Por que existe a cascata** — e não é otimização prematura nem paranoia: a partição em que a
linha reside **deixou de ser derivável do id**. O `ReversibleUUIDv7` carrega a partição de
*criação*, imutável; mas o `ExpurgoAutorizacaoService` do `contratocommand` transfere toda
autorização em estado terminal (`CANCELADA`, `REJEITADA`, `EXPIRADA`, `FINALIZADA`) para a faixa
de expurgo (900–999, balde da semana da transição). Sem a cascata, o `GET /{id}` devolve **404
para toda autorização em estado terminal** — foi o que aconteceu entre 2026-08-09 e a mudança
`fallback-consulta-autorizacao-expurgada`.

Os três níveis cobrem conjuntos de partições **disjuntos**, e juntos cobrem a tabela inteira.
Isso é o que faz um acerto em N3 ser, por definição, violação do invariante "ou está na partição
do seu id, ou está no expurgo" — daí o `log.warn`. N3 é desligável via
`contratoquery.consulta.busca-em-particoes-inesperadas` (default `true`): o pior caso da cascata
é o id **inexistente**, que percorre todos os níveis habilitados antes do 404.

Mais de uma linha para o mesmo id em qualquer nível é **corrupção**, não empate: resulta em
`ApplicationException` (500), nunca em escolher uma das linhas.

### Particionamento temporal (crítico para leitura)

Tabela `autorizacoes` particionada por `id_particao_conta` (range **900–999** no command; a query extrai a partição do UUID reversível para localizar o registro sem query extra).

- `ConsultarAutorizacaoService` extrai a partição via `ReversibleUUIDv7.extract(uuid)` — UUIDs fora da faixa (0–889 neste serviço) resultam em 404 imediato, sem hit no banco.
- `AutorizacaoRepository` usa JPQL explícito (não usa métodos derivados do Spring Data) para garantir compatibilidade com o particionamento.

### Enums de domínio

`domain/enums/StatusAutorizacao` (espelho do `contratocommand`) carrega o grafo de transições da máquina de estados via `podeTransicionarPara(destino)` — não usado por esta app (somente leitura), disponível para eventual validação futura. `domain/enums/TipoEventoAutorizacao` (8 valores, `porStatus(status)`) também é um espelho, sem uso atual nesta app. `domain/enums/TipoJornadaAutorizacao` (espelho parcial: só resolve código→enum, sem `obterJornadaAutorizacaoEnumPorNome` — esta app não recebe o header `tipoJornada`) existe apenas para o converter da coluna `tipo_jornada` funcionar; inclui `DESCONHECIDA(0)` para linhas anteriores à coluna existir.

### Coluna tipo_jornada

A entidade `Autorizacao` espelha a coluna `tipo_jornada` (`TipoJornadaAutorizacaoConverter`,
mesmo padrão de `TipoProdutoConverter`). Não é exposta nos DTOs de resposta
(`AutorizacaoDetalheResponseDto`/`AutorizacaoResumidaResponseDto`) — expor ou não é decisão de
contrato de API em aberto (ver `design.md` da mudança `temporizacao-jornada-01-pix-auto`,
Open Questions), não bloqueante para esta app funcionar.

## Armadilhas críticas

1. **Esta app é somente leitura** — `DB_READ_ONLY=true` por padrão. Não tente usar `@Transactional` para escrita aqui.
2. **Porta 8081**, não 8080 (que é do `contratocommand`).
3. **Não há Strategy Pattern** — sem orquestradores de contratação/cancelamento, sem use cases `Criar*` ou `Cancelar*`.
4. **Sem MapStruct** — conversão feita via `from()` estático nos DTOs (ex.: `AutorizacaoResumidaResponseDto.from(autorizacao)`).
5. **Container é Jetty**, não Tomcat — o `pom.xml` exclui o Tomcat explicitamente.
6. **Faixa de partição na leitura**: `ConsultarAutorizacaoService` valida a partição **embutida no id** em 0–889 — UUIDs fora disso → 404 sem consultar o banco. Isso é a validação do *id*, não o escopo da *busca*: a cascata consulta também 900–999 (nível 2) e as demais quentes (nível 3). Não confunda os dois.
7. **A partição do id ≠ a partição onde a linha está.** O id carrega a partição de criação e é imutável; o expurgo move a linha. Toda leitura por id precisa da cascata. Se você adicionar um caminho novo que localize por `(uuid, partição extraída)` e pare aí, ele vai devolver 404 para autorização em estado terminal — exatamente o defeito corrigido pela mudança `fallback-consulta-autorizacao-expurgada`.
8. **O custo das consultas sem poda por chave de partição é linear no número de partições — em DUAS fatias, não só uma.** Com 24 linhas (base quase vazia), o planejamento dominava (148 ms) e a execução era desprezível (18 ms). Com volume representativo (276 mil linhas, `infra/local/postgres/gerar-massa-sintetica-representativa.sql`), a execução deixa de ser pequena: ~120–136 ms, quase do tamanho do planejamento — porque `id_unico_conta_contratante` não é a chave de particionamento, então **nenhum** plano evita visitar fisicamente as 889 partições quentes para aplicar o filtro. Índice ajuda dentro de cada partição visitada; não evita a visita. `plan_cache_mode = force_generic_plan` (`hikari.connection-init-sql`, adotado na change `reduzir-custo-planejamento-consultas`) amortiza o planejamento a quase zero por conexão física — mas não muda a execução: a listagem continua em ~180–200 ms ponta a ponta. Reduzir o custo de execução exigiria podar por partição (hoje fora de escopo) ou reduzir o número de partições quentes (H2 da mesma change, bloqueada por falta de dado de volume de negócio).
9. **Queries JPQL explícitas** — `AutorizacaoRepository` não usa métodos derivados; cuidado ao renomear campos da entidade.

## Checklist antes do commit

- [ ] `mvn test` passa
- [ ] `mvn clean compile` sem erros
- [ ] Endpoints mantidos como GET — sem adicionar POST/PATCH
- [ ] Se mexeu em `ConsultarAutorizacaoService`, verificar a faixa de validação do id (0–889) **e** os três níveis da cascata — eles precisam continuar disjuntos, ou o acerto em N3 deixa de significar anomalia
- [ ] Se mexeu na localização por id, rodar `ConsultaCascataIntegrationTest` (exige PostgreSQL local no ar e `DB_PASSWORD` exportada)
- [ ] DTOs (records) recriados, não mutados
