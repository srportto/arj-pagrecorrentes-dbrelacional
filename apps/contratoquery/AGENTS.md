# CLAUDE.md

> Guia para agentes de IA (Claude Code, Copilot, etc.) trabalharem neste repositório.
> **Este arquivo e `AGENTS.md` são espelhos — mantenha-os idênticos ao editar.**

> Para entender este serviço, comece pela análise do grafo de conhecimento gerado pelo
> `graphify` (`../../graphify-out/`, skill `graphify`) — só leia arquivos diretamente quando
> necessário ou ao desconfiar de alguma imprecisão no grafo. Atualize o `graphify` sempre que
> encontrar divergência entre o grafo e o código, e sempre ao final da conclusão de uma change.

API REST de **leitura de autorizações de produtos financeiros** (PIX Automático e DDA Automático), em **arquitetura hexagonal**, com **particionamento temporal** em PostgreSQL. Este serviço é **somente leitura** — as operações de escrita ficam no `contratocommand` (porta 8080).

## Comece por aqui

Leia nesta ordem:
1. [AutorizacaoController.java](src/main/java/br/com/srportto/contratoquery/infrastructure/web/AutorizacaoController.java) — os 2 endpoints GET, traduz o resultado do caso de uso em DTO
2. [ListarAutorizacoesService.java](src/main/java/br/com/srportto/contratoquery/application/usecase/ListarAutorizacoesService.java) — listagem paginada com filtro de status
3. [ConsultarAutorizacaoService.java](src/main/java/br/com/srportto/contratoquery/application/usecase/ConsultarAutorizacaoService.java) — busca por id (a cascata de partições não vive mais aqui)
4. [AutorizacaoJpaAdapter.java](src/main/java/br/com/srportto/contratoquery/infrastructure/persistence/AutorizacaoJpaAdapter.java) — a cascata de localização em partições, agora aqui
5. [Autorizacao.java](src/main/java/br/com/srportto/contratoquery/domain/model/Autorizacao.java) — modelo de domínio puro (sem JPA); a entidade JPA é [AutorizacaoJpaEntity.java](src/main/java/br/com/srportto/contratoquery/infrastructure/persistence/AutorizacaoJpaEntity.java)

## Build & Testes

```bash
mvn clean package                                    # Compilar + testes + JAR
mvn spring-boot:run                                  # Rodar localmente (porta 8081)
mvn test                                             # Todos os testes
mvn test -Dtest=ListarAutorizacoesServiceTest        # Classe específica
mvn test -Dtest=ListarAutorizacoesServiceTest#metodo # Método específico
```

> **Maven Wrapper quebrado no Windows**: se `./mvnw.cmd` falhar, use `mvn` diretamente.

> **CI**: `ci-testesunitarios-contratoquery.yml` roda `mvn test -Dtest='!*IntegrationTest'` a cada
> push/PR que toque `apps/contratoquery/**`, usando o Maven do runner (mesma razão do wrapper
> quebrado acima). Exclui por convenção de nome toda classe terminada em `IntegrationTest` — nenhuma
> delas roda no CI hoje, guardada por `PostgresLocalDisponivelCondition` ou não.

Classes de teste existentes: `ContratoqueryApplicationTests`; `ConsultarAutorizacaoServiceTest`, `ListarAutorizacoesServiceTest` (`application/usecase/`); `AutorizacaoJpaAdapterTest` (cascata de partições), `AutorizacaoPersistenceMapperTest`, `ConsultaCascataIntegrationTest`, `ListarPorContaIntegrationTest`, `ReversibleUUIDv7Test`, `TipoProdutoConverterTest`, `TipoJornadaAutorizacaoConverterTest` (`infrastructure/persistence/`); `AutorizacaoControllerTest`, `ApiExceptionHandlerTest`, `AutorizacaoDetalheResponseDtoTest`, `AutorizacaoResumidaResponseDtoTest`, `CancelamentoResponseDtoTest` (`infrastructure/web/`); `StatusAutorizacaoTest`, `TipoProdutoTest`, `TipoEventoAutorizacaoTest`, `DirecaoOrdenacaoTest` (`domain/enums/`); `OrdenacaoTest` (`domain/model/`); `PlanCacheModeHikariIntegrationTest` (`integration/`).

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
| Lombok | 1.18.40 | `@Data`, `@Value`, `@Builder`, `@AllArgsConstructor` |
| PostgreSQL | 18 | Particionamento com `pg_partman` + `pg_cron` |

> Sem MapStruct — não há mapeamento DTO↔Entity nesta app; os DTOs são construídos via `from()` estático. O mapeamento entidade JPA↔domínio (`AutorizacaoPersistenceMapper`) também é feito à mão, num sentido só.

## Logging

Log estruturado em JSON (`logging.structured.format.console: logstash`, `application.yaml`) em todo
profile, inclusive `local` — suporte nativo do Spring Boot 4, sem dependência extra. Para leitura
legível em desenvolvimento: `mvn spring-boot:run | jq .`.

Toda requisição HTTP é correlacionada por `traceId` no MDC do SLF4J, populado por `TraceIdFilter`
(`infrastructure/web/`): reaproveita o cabeçalho `X-Trace-Id` quando presente, gera um `UUID` novo
caso contrário, e limpa o MDC no `finally` (thread do pool do servidor é reaproveitada entre
requisições).

## Endpoints reais (base `/api/autorizacoes`)

| Método | Caminho | Descrição |
|--------|---------|-----------|
| GET | `/api/autorizacoes` | Listagem paginada por conta. Params obrigatórios: `idUnicoContaContratante`. Opcionais: `status`, `pagina` (0), `tamanho` (20), `ordenarPor` (`dataHoraInclusao,desc`). → 200 |
| GET | `/api/autorizacoes/{autorizacaoId}` | Consulta por id. Extrai partição do UUID automaticamente. → 200 / 404 |
| GET | `/actuator/health` | Health-check (Actuator) com readiness de banco. → 200 (UP) / 503 (DOWN) |

> **Não existem** POST, PATCH ou DELETE nesta app — toda escrita fica no `contratocommand` (porta 8080).
> **Nenhuma mudança de contrato REST nesta migração de layout** — parâmetros, formato de resposta e códigos de erro são idênticos a antes; a divergência intencional com o `contratocommand` (`status` como `String`, nomes curtos `valor`/`dataCriacao`/`dataAtualizacao`) permanece.

## Validações e códigos de erro

`ApiExceptionHandler` (`infrastructure/web/`) é o único mapeador entre exceção e status HTTP. Respostas seguem `LayoutErrosApiResponse`.

### Parâmetros de borda do `GET /api/autorizacoes`

| Param | Regra | Erro se violar |
|---|---|---|
| `idUnicoContaContratante` | opcional no controller; se **omitido**, o caso de uso valida nulidade | 422 — `BusinessException`: "idUnicoContaContratante é obrigatório" |
| `pagina` | deve ser **≥ 0** | 422 — `BusinessException`: "pagina deve ser maior ou igual a 0" |
| `tamanho` | deve ser **entre 1 e 100** (inclusive) | 422 — `BusinessException`: "tamanho deve estar entre 1 e 100" |
| `ordenarPor` | formato `campo` ou `campo,direcao`, parseado inteiro por `Ordenacao.de` (`domain/model/`): campo contra whitelist fechada, direção contra `asc`/`desc` (qualquer caixa); campo vazio, direção vazia ou mais de duas partes são rejeitados — nada disso vira padrão silencioso. Omitido/em branco → `Ordenacao.padrao()` (`dataHoraInclusao,desc`) | 422 — `BusinessException` citando o valor recebido e os aceitos (campo ou direção, conforme o caso) |

> **Teto de `tamanho` = 100** impede `?tamanho=999999`, que dispararia varredura completa de partições sem limite.
> **Quebra de contrato (mudança de versão anterior a esta migração):** clientes que enviam `idUnicoContaContratante` vazio e esperavam 400 do Spring agora recebem **422** desta API — o controller deixou o binding ser opcional e a validação virou de negócio. Era 400 (Spring) → agora 422 (handler).

### Códigos de erro desta API

| Status | Exceção | Quando |
|---|---|---|
| 422 | `MethodArgumentNotValidException` | Falha de `@Valid` no body / params — payload do cliente não respeitou as validações declarativas. Resposta no formato `LayoutErrosApiValidationsResponse`, com `occurrences` por campo. |
| 404 | `ResourceNotFoundException` | Autorização inexistente no `GET /{autorizacaoId}` — significa "não existe em partição alguma", após esgotar os níveis habilitados da cascata. Também cobre UUID com partição embutida fora da faixa 0–889, aí sem tocar no banco |
| 500 | `ApplicationException` | Mesma autorização encontrada em mais de uma partição — corrupção, provável resíduo de transferência de partição interrompida. Nenhuma linha é escolhida |
| 422 | `BusinessException` | Violação de regra de negócio — borda de paginação (ver tabela acima), `idUnicoContaContratante` ausente, `ordenarPor` com campo, direção ou formato inválido (`Ordenacao.de`) |
| 500 | `ApplicationException` | Erro inesperado de aplicação (resposta genérica; detalhe fica no log do servidor) |
| 500 | `Exception` (catch-all) | Qualquer outra exceção não mapeada (resposta genérica; detalhe fica no log) |

> **Convenção mantida (D3, 2026-08-09):** entrada inválida do cliente — tanto falha de formato (`@Valid`/`MethodArgumentNotValidException`) quanto violação de regra de negócio (`BusinessException`) — retorna **422**. A distinção entre as duas é carregada pelo **shape da resposta** (`LayoutErrosApiValidationsResponse` vs `LayoutErrosApiResponse`), não pelo primeiro byte do status. Decisão registrada em `openspec/changes/archive/2026-08-09-reconciliar-contrato-spec-doc/design.md` (D3).

> **Nenhuma resposta expõe nome de classe, stack trace, nome de tabela/coluna/constraint.** O log do servidor carrega a cadeia completa de causas.

## Arquitetura (hexagonal clássica, domínio puro)

```
domain/            → Java puro, sem Spring/JPA
  model/              → Autorizacao, Cancelamento — imutáveis (Lombok @Value @Builder, sem setter).
                        idAutorizacao é UUID plano (sem partição — extração é detalhe de
                        armazenamento, D4); sem version (controle de concorrência não interessa
                        a quem só lê, D2). Ordenacao (record campo+direção) — único ponto de parse
                        de `ordenarPor`, fábrica `Ordenacao.de(String)`/`Ordenacao.padrao()`
  port/in/            → ConsultarAutorizacaoUseCase, ListarAutorizacoesUseCase (+ o record
                        ResultadoListagem, envelope de paginação em domínio puro)
  port/out/           → AutorizacaoRepository: buscarPorId(UUID) e
                        listarPorConta(..., Ordenacao) — devolve PaginaAutorizacoes (conteúdo +
                        total), nunca Page do Spring Data
  exception/          → BusinessException, ApplicationException, ResourceNotFoundException
  enums/              → StatusAutorizacao, TipoEventoAutorizacao, TipoJornadaAutorizacao,
                        TipoProduto, CampoOrdenacao, DirecaoOrdenacao
application/
  usecase/            → ConsultarAutorizacaoService (só traduz ausência em 404 — a cascata não
                        está mais aqui), ListarAutorizacoesService (validação, defaults, whitelist
                        de ordenação, monta o envelope de paginação em domínio puro)
infrastructure/
  web/                → AutorizacaoController (monta os DTOs a partir do modelo de domínio — D6),
                        contratosrest/ (DTOs de resposta), ApiExceptionHandler e layouts de erro
  persistence/        → AutorizacaoJpaEntity (@Entity real), IdAutorizacaoJpaEmbeddable,
                        CancelamentoJpaEmbeddable, AutorizacaoPersistenceMapper (paraDominio —
                        sentido único, a app não escreve), SpringDataAutorizacaoRepository
                        (package-private), AutorizacaoJpaAdapter (a cascata de três níveis vive
                        aqui — D3), ReversibleUUIDv7, TipoProdutoConverter,
                        TipoJornadaAutorizacaoConverter
```

### Fluxo de uma requisição GET (listagem)

```
AutorizacaoController.listar()
  └─ ListarAutorizacoesUseCase.listar()   (application/usecase/ListarAutorizacoesService)
       ├─ valida idUnicoContaContratante (BusinessException se nulo) e os limites de paginação
       ├─ delega ordenarPor a Ordenacao.de(String) — parse único, valida campo (whitelist) e
       │    direção (asc/desc) juntos, sem conhecer caminho JPA (ver domain/model/Ordenacao)
       └─ AutorizacaoRepository.listarPorConta(..., Ordenacao)   (domain/port/out, implementado
            por infrastructure/persistence/AutorizacaoJpaAdapter)
            ├─ traduz Ordenacao.campo() → caminho de propriedade JPA e Ordenacao.direcao() →
            │    Sort.Direction, monta Pageable/Sort e chama o Spring Data (JPQL explícito) —
            │    única tradução para vocabulário de persistência
            └─ mapeia cada AutorizacaoJpaEntity → Autorizacao (domínio) e devolve
               PaginaAutorizacoes(conteúdo, total)
  └─ controller monta PaginacaoResponseDto a partir do ResultadoListagem
       └─ AutorizacaoResumidaResponseDto.from(autorizacao) por item
```

### Fluxo de uma requisição GET (consulta por id)

```
AutorizacaoController.consultarPorId()
  └─ ConsultarAutorizacaoUseCase.consultarPorId()   (application/usecase/ConsultarAutorizacaoService)
       └─ AutorizacaoRepository.buscarPorId(UUID)   (domain/port/out, implementado por
            infrastructure/persistence/AutorizacaoJpaAdapter)
            ├─ ReversibleUUIDv7.extract(uuid) ← extrai a partição de CRIAÇÃO, sem query adicional
            ├─ valida faixa de partição (0–889), devolve Optional.empty() se fora — sem tocar no banco
            └─ cascata de localização (para no primeiro nível que encontrar):
                 N1  findById(IdAutorizacaoJpaEmbeddable(uuid, particao))    1 part.   ~3 ms  → ativa
                 N2  buscarNaFaixaDeExpurgo(uuid, 900)                    100 part.  ~16 ms  → terminal
                 N3  buscarEmOutrasParticoesQuentes(uuid, 900, part)      888 part. ~137 ms → anomalia + WARN
            └─ mapeia AutorizacaoJpaEntity → Autorizacao (domínio) se encontrou
       └─ Optional vazio → ResourceNotFoundException (o caso de uso só faz essa tradução)
  └─ controller: AutorizacaoDetalheResponseDto.from(autorizacao)
```

**Por que existe a cascata** — e não é otimização prematura nem paranoia: a partição em que a
linha reside **deixou de ser derivável do id**. O `ReversibleUUIDv7` carrega a partição de
*criação*, imutável; mas o `ExpurgoAutorizacaoService` do `contratocommand` transfere toda
autorização em estado terminal (`CANCELADA`, `REJEITADA`, `EXPIRADA`, `FINALIZADA`) para a faixa
de expurgo (900–999, balde da semana da transição). Sem a cascata, o `GET /{id}` devolve **404
para toda autorização em estado terminal** — foi o que aconteceu entre 2026-08-09 e a mudança
`fallback-consulta-autorizacao-expurgada`.

A cascata é **detalhe de armazenamento, não regra de negócio** (D3 da mudança
`hexagonal-classico-contratoquery`) — por isso vive inteira em `AutorizacaoJpaAdapter`
(`infrastructure/persistence/`), incluindo as constantes `PARTICAO_MIN`/`PARTICAO_MAX`/
`PRIMEIRA_PARTICAO_EXPURGO` e a flag de habilitação do nível 3. `ConsultarAutorizacaoService`
(`application/usecase/`) não sabe que existe partição — só chama `buscarPorId` e traduz ausência
em `ResourceNotFoundException`.

Os três níveis cobrem conjuntos de partições **disjuntos**, e juntos cobrem a tabela inteira.
Isso é o que faz um acerto em N3 ser, por definição, violação do invariante "ou está na partição
do seu id, ou está no expurgo" — daí o `log.warn`. N3 é desligável via
`contratoquery.consulta.busca-em-particoes-inesperadas` (default `true`): o pior caso da cascata
é o id **inexistente**, que percorre todos os níveis habilitados antes do 404.

Mais de uma linha para o mesmo id em qualquer nível é **corrupção**, não empate: resulta em
`ApplicationException` (500), nunca em escolher uma das linhas.

### Particionamento temporal (crítico para leitura)

Tabela `autorizacoes` particionada por `id_particao_conta` (range **900–999** no command; a query extrai a partição do UUID reversível para localizar o registro sem query extra).

- `AutorizacaoJpaAdapter` extrai a partição via `ReversibleUUIDv7.extract(uuid)` — UUIDs fora da faixa (0–889 neste serviço) resultam em `Optional.empty()` imediato (→ 404 no caso de uso), sem hit no banco.
- `SpringDataAutorizacaoRepository` usa JPQL explícito (não usa métodos derivados do Spring Data) para garantir compatibilidade com o particionamento.
- `domain/` inteiro **não sabe que existe partição** — nem `Autorizacao` (modelo), nem as portas. É D3+D4 da mudança `hexagonal-classico-contratoquery`.

### Entidade JPA ≠ modelo de domínio

`domain/model/Autorizacao` é Java puro (Lombok `@Value @Builder`, sem setter, sem `@Entity`); a
entidade JPA correspondente é `infrastructure/persistence/AutorizacaoJpaEntity` (`@Entity`,
`@EmbeddedId`, `@Convert`, etc.). A ponte é `AutorizacaoPersistenceMapper.paraDominio(entity)` —
**sentido único**, porque esta app não escreve. Diferenças deliberadas em relação à entidade:

- `Autorizacao` (domínio) não tem `idParticaoConta` — só `UUID`. A chave composta
  (`IdAutorizacaoJpaEmbeddable`) existe só na entidade JPA.
- `Autorizacao` (domínio) não tem `version` — a coluna é mapeada na entidade JPA só para o
  Hibernate não reclamar do schema; não há lock otimista porque não há escrita (D2).

### Enums de domínio

`domain/enums/StatusAutorizacao` (espelho do `contratocommand`) carrega o grafo de transições da máquina de estados via `podeTransicionarPara(destino)` — não usado por esta app (somente leitura), disponível para eventual validação futura. `domain/enums/TipoEventoAutorizacao` (8 valores, `porStatus(status)`) também é um espelho, sem uso atual nesta app. `domain/enums/TipoJornadaAutorizacao` (espelho parcial: só resolve código→enum, sem `obterJornadaAutorizacaoEnumPorNome` — esta app não recebe o header `tipoJornada`) existe apenas para o converter da coluna `tipo_jornada` funcionar; inclui `DESCONHECIDA(0)` para linhas anteriores à coluna existir.

### Coluna tipo_jornada

A entidade `AutorizacaoJpaEntity` espelha a coluna `tipo_jornada` (`TipoJornadaAutorizacaoConverter`,
mesmo padrão de `TipoProdutoConverter`). Não é exposta nos DTOs de resposta
(`AutorizacaoDetalheResponseDto`/`AutorizacaoResumidaResponseDto`) — expor ou não é decisão de
contrato de API em aberto (ver `design.md` da mudança `temporizacao-jornada-01-pix-auto`,
Open Questions), não bloqueante para esta app funcionar.

### `AutorizacaoDetalheResponseDto` é a representação completa — confira ao adicionar campo

`GET /api/autorizacoes/{autorizacaoId}` SHALL devolver todo campo de `domain/model/Autorizacao`
que não tenha uma exclusão documentada (hoje só `tipoJornada`, ver acima). O DTO já inclui
`frequenciaPagamento`, `quantidadeDividasCiclo`, `indicadorUsoLimiteConta`,
`indicadorTipoMensageria`, `codigoCanalContratacao` e `cancelamento` (`CancelamentoResponseDto`,
`null` se a autorização nunca foi cancelada, espelha `CancelamentoResponseDto` do
`contratocommand`) — ver a change `completar-detalhe-consulta-autorizacao`, que fechou o gap
entre a promessa do Javadoc ("representação completa") e o shape real. Campo novo no domínio =
campo novo aqui, salvo exclusão documentada.

## Armadilhas críticas

1. **Esta app é somente leitura** — `DB_READ_ONLY=true` por padrão. Não tente usar `@Transactional` para escrita aqui.
2. **Porta 8081**, não 8080 (que é do `contratocommand`).
3. **Não há Strategy Pattern** — sem orquestradores de contratação/cancelamento, sem use cases `Criar*` ou `Cancelar*`.
4. **Sem MapStruct** — conversão feita via `from()` estático nos DTOs (ex.: `AutorizacaoResumidaResponseDto.from(autorizacao)`) e à mão em `AutorizacaoPersistenceMapper`.
5. **Container é Jetty**, não Tomcat — o `pom.xml` exclui o Tomcat explicitamente.
6. **Faixa de partição na leitura**: `AutorizacaoJpaAdapter.buscarPorId` valida a partição **embutida no id** em 0–889 — UUIDs fora disso → 404 (via `Optional.empty()`) sem consultar o banco. Isso é a validação do *id*, não o escopo da *busca*: a cascata consulta também 900–999 (nível 2) e as demais quentes (nível 3). Não confunda os dois.
7. **A partição do id ≠ a partição onde a linha está.** O id carrega a partição de criação e é imutável; o expurgo move a linha. Toda leitura por id precisa da cascata. Se você adicionar um caminho novo que localize por `(uuid, partição extraída)` e pare aí, ele vai devolver "não encontrado" para autorização em estado terminal — exatamente o defeito corrigido pela mudança `fallback-consulta-autorizacao-expurgada`.
8. **O custo das consultas sem poda por chave de partição é linear no número de partições — em DUAS fatias, não só uma.** Com 24 linhas (base quase vazia), o planejamento dominava (148 ms) e a execução era desprezível (18 ms). Com volume representativo (276 mil linhas, `infra/local/postgres/gerar-massa-sintetica-representativa.sql`), a execução deixa de ser pequena: ~120–136 ms, quase do tamanho do planejamento — porque `id_unico_conta_contratante` não é a chave de particionamento, então **nenhum** plano evita visitar fisicamente as 889 partições quentes para aplicar o filtro. Índice ajuda dentro de cada partição visitada; não evita a visita. `plan_cache_mode = force_generic_plan` (`hikari.connection-init-sql`, adotado na change `reduzir-custo-planejamento-consultas`) amortiza o planejamento a quase zero por conexão física — mas não muda a execução: a listagem continua em ~180–200 ms ponta a ponta. Reduzir o custo de execução exigiria podar por partição (hoje fora de escopo) ou reduzir o número de partições quentes (H2 da mesma change, bloqueada por falta de dado de volume de negócio). **Confirmado sob carga concorrente real** (change `testes-de-carga-tps`, `testes-carga/relatorios/RESUMO-baseline-2026-08-23.md`): os ~180-200 ms acima são por *query isolada*; com massa representativa carregada e carga concorrente (ramp até 400 usuários/s), a fila se acumula e o p99 chega a **52 segundos**, com conexões fechadas pelo servidor (`Premature close`) — o efeito de fila sob concorrência multiplica o custo de execução já documentado, não é um custo novo e independente.
9. **Queries JPQL explícitas** — `SpringDataAutorizacaoRepository` não usa métodos derivados; cuidado ao renomear campos da entidade.
10. **Coluna nova exige edição em três lugares, não um.** `AutorizacaoJpaEntity` (schema real) **e** `domain/model/Autorizacao` (campo novo no modelo puro) **e** `AutorizacaoPersistenceMapper.paraDominio` (senão o campo nunca chega ao domínio, silenciosamente) — desde a separação modelo/entidade (change `hexagonal-classico-contratoquery`), esquecer qualquer um dos três não quebra a compilação, só devolve dado incompleto em runtime.
11. **`SpringDataAutorizacaoRepository` é package-private** (`infrastructure/persistence/`) — não tente injetá-lo em `application/` nem torná-lo `public`; é o mecanismo que impede a regressão da violação "use case falando Spring Data direto". Use a porta `domain/port/out/AutorizacaoRepository`.

## Checklist antes do commit

- [ ] `mvn test` passa
- [ ] `mvn clean compile` sem erros
- [ ] Endpoints mantidos como GET — sem adicionar POST/PATCH
- [ ] Se mexeu em `AutorizacaoJpaAdapter`, verificar a faixa de validação do id (0–889) **e** os três níveis da cascata — eles precisam continuar disjuntos, ou o acerto em N3 deixa de significar anomalia
- [ ] Se mexeu na localização por id, rodar `ConsultaCascataIntegrationTest` (exige PostgreSQL local no ar e `DB_PASSWORD` exportada)
- [ ] Se mexeu no schema de `autorizacoes` (coluna nova/renomeada): atualize `AutorizacaoJpaEntity`, `domain/model/Autorizacao` **e** `AutorizacaoPersistenceMapper` (três pontos, ver armadilha 10) — e confira se `apps/contratocommand` precisa do mesmo campo
- [ ] Modelo de domínio (`Autorizacao`, `Cancelamento`) é imutável — nunca adicione setter; construa sempre via `Autorizacao.builder()...build()`
- [ ] DTOs (records/classes `@Builder`) recriados, não mutados
