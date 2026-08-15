# CLAUDE.md

> Guia para agentes de IA (Claude Code, Copilot, etc.) trabalharem neste repositório.
> **Este arquivo e `AGENTS.md` são espelhos — mantenha-os idênticos ao editar.**

API REST de **autorizações de produtos financeiros** (PIX Automático e DDA Automático), em **arquitetura hexagonal**, com **particionamento temporal** em PostgreSQL e expurgo automático de dados.

## Comece por aqui

Leia nesta ordem:
1. [AutorizacaoController.java](src/main/java/br/com/srportto/contratocommand/entrypoint/AutorizacaoController.java) — os 3 endpoints REST, chama os use cases diretamente
2. [CriarAutorizacaoUseCase.java](src/main/java/br/com/srportto/contratocommand/application/contratacao/CriarAutorizacaoUseCase.java) — caso de uso compartilhado (validação → mapper → save)
3. [ContratacaoValidator.java](src/main/java/br/com/srportto/contratocommand/application/contratacao/ContratacaoValidator.java) — validação de regras de negócio via rules
4. [Autorizacao.java](src/main/java/br/com/srportto/contratocommand/domain/entities/Autorizacao.java) — entidade de domínio com particionamento
5. [AutorizacaoEventoPublisher.java](src/main/java/br/com/srportto/contratocommand/application/eventos/AutorizacaoEventoPublisher.java) — publica no SNS o estado final de cada autorização persistida, após o commit

## Build & Testes

```bash
mvn clean package                            # Compilar + testes + JAR
mvn spring-boot:run                          # Rodar localmente
mvn test                                     # Todos os testes
mvn test -Dtest=ControleExpurgoAutorizacaoTest          # Classe específica
mvn test -Dtest=ControleExpurgoAutorizacaoTest#metodo   # Método específico
```

> **Maven Wrapper quebrado no Windows**: se `./mvnw.cmd` falhar, use `mvn` diretamente.

Classes de teste existentes: `ContratocommandApplicationTests`, testes de use cases, validators e rules (`contratacao/` e `cancelamento/`), `AutorizacaoControllerTest`, `AutorizacaoMapperTest`, `ApiExceptionHandlerTest`, `AutorizacaoCompletaResponseDtoTest`, `AutorizacaoTest` e testes de domínio (`ControleExpurgoAutorizacaoTest`, `IdContaUUIDPartitionDistributorTest`, `ReversibleUUIDv7Test`, `AchaQtdeSemanasTest`, `TipoProdutoTest`, `TipoProdutoConverterTest`, `MotivoStatusAutorizacaoTest`). Helpers em `src/test`: `TestFixtures`, `GeraDatasPorParticao` e a utility `AchaQtdeSemanas` (usada apenas por testes — vive no source set de teste, não em `src/main`).

## Pré-requisitos

- **Java 25** (JDK 25+) — usa `public static void main()`; a forma `void main()` do Java 25 está pendente de suporte do maven plugin (ver `// TODO` no entrypoint)
- **PostgreSQL 18** com `pg_partman`, `pg_cron` e `pgvector` — **sem fallback para H2**
- Variáveis de ambiente obrigatórias: `DB_NAME`, `DB_USER_NAME`, `DB_PASSWORD`
- Variáveis de ambiente opcionais (datasource, com defaults no `application.yaml`):
  - `DB_TRANSACTION_ISOLATION` — nível de isolamento (default `TRANSACTION_READ_COMMITTED`; aceita `TRANSACTION_READ_UNCOMMITTED`, `TRANSACTION_READ_COMMITTED`, `TRANSACTION_REPEATABLE_READ`, `TRANSACTION_SERIALIZABLE`).
  - `DB_READ_ONLY` — modo de acesso (default `false` no `contratocommand`, `true` no `contratoquery`).
  - Pool HikariCP: `DB_POOL_MAX_SIZE`, `DB_POOL_MIN_IDLE`, `DB_POOL_CONNECTION_TIMEOUT`, `DB_POOL_IDLE_TIMEOUT`, `DB_POOL_MAX_LIFETIME`.
  - `hikari.connection-init-sql` fixa `plan_cache_mode = force_generic_plan` em toda conexão física do pool — elimina o replanejamento em `findByIdAutorizacao` (sem poda de partição); regride sub-milissegundo em consultas já podadas para 1 partição (`existsBy...`, `moverParaParticao`). Ver `openspec/changes/reduzir-custo-planejamento-consultas/design.md`.
- Docker com PostgreSQL em `infra/local/postgres/` (raiz do repositório) — fonte única do Postgres local. Exemplos de payloads em `docs/post-autorizacoes.txt`.
- Dockerfile próprio (multi-stage, Fargate-ready) nesta pasta; `apps/docker-compose.yml` sobe as cinco aplicações (sem Postgres — ver `infra/local/postgres/`). Para o ambiente local completo num só comando, use o `compose.yaml` da raiz.
- Profiles Spring: `local` (padrão de desenvolvimento) e `prod` (deve ser setado explicitamente via `SPRING_PROFILES_ACTIVE=prod`) — não existe mais o profile `dev`.
- **Publicação de eventos (opcional para rodar a API)**: a cada criação/cancelamento confirmado, a app publica no SNS `sns-estados-autorizacao` (ver `infra/envs/local-messaging/`). No profile `local` os defaults já apontam para o Floci (`http://localhost:4566`); se o Floci ou o tópico não existirem, o publish falha silenciosamente (só loga erro) — a API continua funcionando normalmente. Em `prod`, as variáveis `AWS_REGION` e `AWS_SNS_TOPIC_ARN` são obrigatórias (sem default).

## Stack

| Componente | Versão | Notas |
|---|---|---|
| Java | 25 | `void main()`; records imutáveis |
| Spring Boot | 4.0.7 | Web MVC, Data JPA, Validation |
| Lombok | 1.18.40 | `@Data`, `@Getter`, `@Setter`, `@AllArgsConstructor` |
| MapStruct | 1.5.5.Final | Mapeamento DTO↔Entity com `@AfterMapping` |
| Yasson | 3.0.3 | Jakarta JSON Binding |
| PostgreSQL | 18 | Particionamento com `pg_partman` + `pg_cron` + `pgvector` |
| AWS SDK v2 | 2.49.0 | `software.amazon.awssdk:sns` — publicação de eventos, sem Spring Cloud AWS |

> Serialização JSON usa **Jackson 3** (`tools.jackson.databind.JsonNode`).

## Endpoints reais (base `/api/autorizacoes`)

| Método | Caminho | Descrição |
|--------|---------|-----------|
| POST | `/api/autorizacoes` | Criar autorização (multi-produto). Body `CriarAutorizacaoRequest`. → 201 |
| PATCH | `/api/autorizacoes/{idAutorizacao}/cancelar` | Cancelar. **Header obrigatório `tipoProduto`**. → 200 |
| PATCH | `/api/autorizacoes/{idAutorizacao}/decisao` | Decisão sobre autorização em `RECEBIDA` (jornada 1 do PIX_AUTO): `acao` = `APROVAR`\|`REJEITAR`\|`EXPIRAR`. **Header obrigatório `tipoProduto`**. → 200 (aplicada) / 422 (status não permite — inclui já resolvida) |
| GET | `/actuator/health` | Health-check (Actuator) com readiness de banco (indicador `db`). → 200 (UP) / 503 (DOWN) |

> A base é `/api/autorizacoes` (**plural**). Não existem `/olaMundo` nem `/ativas`. As leituras ficam no `contratoquery` (porta 8081): `GET /api/autorizacoes` (listagem paginada por conta — params `idUnicoContaContratante`, `status`, `pagina`, `tamanho`, `ordenarPor`) e `GET /api/autorizacoes/{autorizacaoId}` (consulta por id, 404 se não encontrado).

## Códigos de erro (handler global)

`ApiExceptionHandler` (`shared/interceptors/api/`) é o único mapeador entre exceção e status HTTP. Respostas seguem `LayoutErrosApiResponse` (regra de negócio / conflito / não encontrado) ou `LayoutErrosApiValidationsResponse` (falha de validação de formato). Caminhos desta API:

| Status | Exceção | Quando |
|---|---|---|
| 422 | `MethodArgumentNotValidException` | Falha de `@Valid` no body / params — payload do cliente não respeitou as validações declarativas. Resposta no formato `LayoutErrosApiValidationsResponse`, com `occurrences` por campo. |
| 409 | `RecursoJaExisteException` | `id_autorizacao_empresa` já existe no `POST /api/autorizacoes` (constraint UNIQUE em `(id_particao_conta, id_autorizacao_empresa)`) |
| 409 | `ObjectOptimisticLockingFailureException` | Concorrência em `PATCH /api/autorizacoes/{id}/cancelar` ou `/decisao` — outro chamador já alterou a linha |
| 409 | `ConcurrencyFailureException` (inclui `CannotAcquireLockException`) | Concorrência na troca de partição do `ExpurgoAutorizacaoService`. Quando a transação vencedora move a linha, o Postgres não consegue seguir a cadeia de atualização entre partições e devolve `tuple to be locked was already moved to another partition` (SQLSTATE 40001) — conflito real, não erro interno |
| 409 | `StaleStateException` / `DataIntegrityViolationException` | Estado obsoleto ou violação de integridade em escrita concorrente |
| 422 | `BusinessException` | Violação de regra de negócio — **inclui autorização inexistente** em `cancelar`/`decidir` (não existe 404 nestas rotas: `ResourceNotFoundException` não existe no código desta app), validação de produto, dados inválidos, transição de status inválida |
| 500 | `ApplicationException` | Erro inesperado de aplicação (resposta genérica; detalhe fica no log do servidor) |
| 500 | `Exception` (catch-all) | Qualquer outra exceção não mapeada (resposta genérica; detalhe fica no log) |

> **Convenção mantida (D3, 2026-08-09):** entrada inválida do cliente — tanto falha de formato (`@Valid`/`MethodArgumentNotValidException`) quanto violação de regra de negócio (`BusinessException`) — retorna **422**. A distinção entre as duas é carregada pelo **shape da resposta** (`LayoutErrosApiValidationsResponse` vs `LayoutErrosApiResponse`), não pelo primeiro byte do status. Decisão registrada em `openspec/changes/reconciliar-contrato-spec-doc/design.md` (D3).

> **Nenhuma resposta expõe nome de classe, stack trace, nome de tabela/coluna/constraint.** O log do servidor carrega a cadeia completa de causas.

> **Breaking change (mudança desta versão):** clientes que tratavam o POST duplicado como 422 precisam migrar para 409. A resposta 409 carrega o id já criado — basta retorná-lo ao chamador.

### Lock otimista e idempotência

`Autorizacao` tem `@Version` (lock otimista JPA). Dois cancelamentos ou duas decisões concorrentes na mesma autorização disparam `ObjectOptimisticLockingFailureException` → 409. O cliente pode tentar de novo (a segunda tentativa vai ler o estado já persistido e cair em 422 por status inválido se já estiver sido resolvida).

A criação é idempotente por `id_autorizacao_empresa`: o segundo POST com o mesmo id recebe 409, **sem** publicar evento adicional. A garantia no banco é um **índice único parcial** (`uk_autorizacao_empresa_ativa`, migration v1.0.4) sobre `(id_particao_conta, id_autorizacao_empresa)` **restrito a `id_particao_conta < 900`** — ou seja, só às partições quentes. Nelas, `id_particao_conta` é o hash da conta, então "único por partição" equivale a "único por conta". Nas partições de expurgo a mesma coluna é o balde semanal, e impor a chave ali faria autorizações de **contas distintas** colidirem ao serem expurgadas na mesma semana. Decisão: a unicidade é regra sobre autorizações **ativas**, não invariante da tabela (ver a migration v1.0.4 para o racional completo).

> A entidade `Autorizacao` **não declara** essa unicidade em `@Table`: JPA não sabe expressar índice parcial, e declará-la prometeria uma garantia diferente da que o banco impõe.

A rule `TransicaoStatusValida` (`application/cancelamento/rules/`, `@Order(10)`) consulta `StatusAutorizacao.podeTransicionarPara` e bloqueia cancelamento a partir de `CANCELADA`, `REJEITADA`, `EXPIRADA` e `FINALIZADA` — antes essa proteção não existia, e dois cancelamentos concorrentes podiam sobrescrever dados.

## Arquitetura (hexagonal, 4 camadas)

```
entrypoint/   → AutorizacaoController + DTOs (records imutáveis em contratosrest/)
application/  → Use Cases por feature (contratacao/, cancelamento/), Mappers, Repositories, eventos/
domain/       → Entidades, Enums (TipoProduto, StatusAutorizacao, TipoEventoAutorizacao, MotivoStatusAutorizacao, CanaisConhecidosEnum, TipoConta, TipoJornadaAutorizacao), Converters, Utilities — lógica pura, sem Spring
shared/       → Exceções, Interceptadores (ApiExceptionHandler), config/, framework de validação
```

`application/` divide-se em:
- raiz de `application/` — componentes **compartilhados** por todos os produtos e por todas as features: `AutorizacaoRepository`, `AutorizacaoMapper`. Não têm subpacote próprio (não são uma feature).
- `contratacao/` — `CriarAutorizacaoUseCase`, `ContratacaoContext`, `ContratacaoValidator`, `ContratacaoRule` e `rules/` (inclui `ProdutoSuportado`)
- `cancelamento/` — `CancelarAutorizacaoUseCase`, `CancelamentoContext`, `CancelamentoValidator`, `CancelamentoRule` e `rules/`
- `decisao/` — `DecidirAutorizacaoUseCase`, `DecisaoContext`, `DecisaoValidator`, `DecisaoRule` e `rules/` (`AcaoDecisaoValida`, `TipoProdutoDecisao`, `TransicaoValidaDecisao`) — aprovação/rejeição/expiração de autorização em `RECEBIDA` (jornada 1 do PIX_AUTO, acionada pela app `temporiza-autorizacao` no caso de `EXPIRAR`)
- `eventos/` — `AutorizacaoPersistidaEvent` (evento interno), `AutorizacaoEventoPayload` (representação da linha, chaves = colunas), `AutorizacaoEventoPublisher` (`@TransactionalEventListener(AFTER_COMMIT)`, publica no SNS)

`shared/config/` contém `AwsProperties` e `SnsClientConfig` (bean do `SnsClient`, AWS SDK v2 puro).

Dentro de cada feature, o estereótipo Spring reflete o papel: `@Service` nos orquestradores (`ContratacaoValidator`, `CancelamentoValidator`, `CriarAutorizacaoUseCase`, `CancelarAutorizacaoUseCase` — a lógica de negócio principal da operação), `@Component` nas rules individuais (estratégias plugáveis, injetadas coletivamente via `List<ContratacaoRule>`/`List<CancelamentoRule>`).

Não há mais orquestradores nem strategies por produto: o controller chama os use cases diretamente, e a variação por produto (incluindo a rejeição de produto desconhecido) vive inteiramente nas rules.

### Fluxo de uma requisição POST (criar)

```
AutorizacaoController.insert()
  ├─ resolve tipoJornada do header (TipoJornadaAutorizacao.obterJornadaAutorizacaoEnumPorNome)
  └─ ContratacaoContext.doRequest(jornada, request)   ← contexto imutável (header + corpo)
       └─ CriarAutorizacaoUseCase.execute(context)   (application/contratacao, @Transactional)
            ├─ ContratacaoValidator.validar(context) ← roda todas as ContratacaoRule (ProdutoSuportado primeiro)
            ├─ AutorizacaoMapper.toDomain(context.dados(), context.tipoJornada())  ← MapStruct + @AfterMapping
            │    └─ Autorizacao.inicializaCriacao()  ← gera UUID+partição, defaults
            └─ AutorizacaoRepository.save()
```

O cancelamento segue o mesmo padrão simétrico: o controller resolve o header, monta `CancelamentoContext.doRequest(...)` (path `idAutorizacao` + header `tipoProduto` + corpo) e chama `CancelarAutorizacaoUseCase.execute()` (application/cancelamento) diretamente.

### Decisão sobre autorização em RECEBIDA (jornada 1 do PIX_AUTO)

`PATCH /api/autorizacoes/{id}/decisao` segue o mesmo padrão estrutural de cancelamento —
`DecisaoContext.doRequest(...)` → `DecidirAutorizacaoUseCase.execute()` (`@Transactional`,
`application/decisao`) — mas com uma diferença central: **a rota precisa ser segura para
chamada repetida por um chamador automatizado at-least-once** (a app `temporiza-autorizacao`,
que aciona `EXPIRAR` no vencimento de um timer, podendo chegar depois de o cliente já ter
decidido).

```
DecidirAutorizacaoUseCase.execute(context)
  ├─ carrega por UUID + partição extraída (mesmo padrão do cancelamento)
  ├─ DecisaoValidator roda as rules:
  │    ├─ AcaoDecisaoValida (@Order HIGHEST_PRECEDENCE) — acao resolve para APROVAR/REJEITAR/EXPIRAR
  │    ├─ TipoProdutoDecisao — produto do header bate com o persistido
  │    └─ TransicaoValidaDecisao — status atual PRECISA SER RECEBIDA (não basta o grafo
  │         permitir a transição a partir de outro estado — ATIVA também pode ir para
  │         REJEITADA no grafo, para outro fluxo; sem essa checagem explícita, uma
  │         expiração atrasada "rejeitaria" uma autorização já aprovada)
  ├─ aplica o efeito da ação:
  │    APROVAR  → status ATIVA,      motivo AUTORIZACAO_ACEITA_POR_TODOS (2 saltos do grafo
  │                                  numa única transação: RECEBIDA→EM_PROCESSO_ATIVACAO→ATIVA)
  │    REJEITAR → status REJEITADA,  motivo REJEITADA_PAGADOR
  │    EXPIRAR  → status REJEITADA,  motivo REJEITADA_SISTEMA_TIMEOUT_J1
  └─ publica um único AutorizacaoPersistidaEvent
```

Status que já não é `RECEBIDA` (incluindo já resolvida por outra decisão) resulta em
`BusinessException` → 422, sem alterar a linha nem publicar evento — é o sinal que o
chamador automatizado usa para não repetir. O grafo de `StatusAutorizacao` **não muda**:
`RECEBIDA → REJEITADA` e `RECEBIDA → EM_PROCESSO_ATIVACAO → ATIVA` já existiam.

### Publicação de eventos (após commit)

Ao final de `CriarAutorizacaoUseCase.execute()`, `CancelarAutorizacaoUseCase.execute()` **e** `DecidirAutorizacaoUseCase.execute()`, um `AutorizacaoPersistidaEvent` (só a entidade final — sem campo de tipo) é publicado via `ApplicationEventPublisher`. Quem efetivamente fala com o SNS é `AutorizacaoEventoPublisher`, um `@TransactionalEventListener(phase = AFTER_COMMIT)`:

```
CriarAutorizacaoUseCase / CancelarAutorizacaoUseCase / DecidirAutorizacaoUseCase (fim do execute(), ainda na transação)
  └─ eventPublisher.publishEvent(new AutorizacaoPersistidaEvent(autorizacao))
       ⋮ (commit da transação)
AutorizacaoEventoPublisher.aoPersistir()   ← só roda se o commit teve sucesso
  ├─ AutorizacaoEventoPayload.from(autorizacao)  ← chaves = nomes das colunas, não campos Java
  ├─ TipoEventoAutorizacao.porStatus(autorizacao.getStatus())  ← deriva o tipo do status persistido
  └─ SnsClient.publish()  ← tópico sns-estados-autorizacao, message attributes tipoEvento/tipoProduto/tipoJornada
```

O `tipoEvento` **não é mais informado pelo use case** — é derivado do `status` da entidade (`TipoEventoAutorizacao`, 8 valores em bijeção com `StatusAutorizacao`: `RECEPCAO`, `PENDENCIA_ACEITE`, `INICIO_ATIVACAO`, `ATIVACAO`, `CANCELAMENTO`, `REJEICAO`, `EXPIRACAO`, `FINALIZACAO`). Criação de `DDA_AUTO` (status `ATIVA`) publica `tipoEvento=ATIVACAO`; criação de `PIX_AUTO` (status `RECEBIDA`) publica `RECEPCAO`; cancelamento (status `CANCELADA`) publica `CANCELAMENTO`; decisão `APROVAR` publica `ATIVACAO`, `REJEITAR`/`EXPIRAR` publicam `REJEICAO`. O antigo par `CRIACAO`/`CANCELAMENTO` não existe mais.

Além do `tipoEvento`, todo evento carrega **`tipoProduto`** e **`tipoJornada`** como message
attributes — espelhos das colunas `tipo_produto`/`tipo_jornada` da própria linha, existentes
para permitir filtro por filter policy no SNS sem inspecionar o corpo (é assim que a
subscription de `SQS-temporizacao-autorizacao` restringe a entrega a `RECEPCAO` + `PIX_AUTO` +
`SPI_J1`, sem precisar de lógica de filtro na app consumidora). Nenhum attribute expressa
informação ausente do body.

Rollback (ex.: `BusinessException` de validação) nunca chega ao listener — nenhum evento é publicado. Falha no `publish()` (ex.: Floci fora do ar) é apenas logada; a resposta HTTP, já confirmada pelo commit, não é afetada. Não há outbox pattern nesta fase — é um trade-off aceito e documentado em `openspec/changes/add-eventos-autorizacao-sns-sqs/design.md`.

### Variação por produto vive em rules, não em strategies

Não existem mais `*OrquestradorService`, `*Service` (strategy) nem `ContratacaoService`/`CancelamentoService`. A rejeição de `tipoProduto` desconhecido na criação é feita pela rule `ProdutoSuportado` (`application/contratacao/rules/`), anotada com `@Order(Ordered.HIGHEST_PRECEDENCE)` para rodar antes das demais `ContratacaoRule` — ela lança `BusinessException` ("Produto nao suportado ou invalido...") do mesmo jeito que o antigo orquestrador. No cancelamento, o header `tipoProduto` já é resolvido para o enum no controller (`TipoProduto.obterTipoProdutoEnumPorNome`) e a rule `TipoProdutoCancelamento` valida a divergência contra o produto lido do banco.

**Adicionar um produto novo**: adicione o valor em `TipoProduto` e, se houver regras específicas do produto, expresse-as em uma rule usando `aceita(contexto)` para filtrar por produto (via `contexto.dados().tipoProduto()`). Não crie classes de strategy — `Repository`, `Mapper` e `UseCase` são únicos e compartilhados. (Os arquivos em `docs/strategyProduto/` são só exemplos didáticos — não refletem o código de produção.)

### Framework de validação de regras de negócio

```
Rule<T>              → interface (shared/validationsetup): aceita(T) + validar(T)
Validator<R,T>       → interface: getRules() + validar(T) default que itera as regras
ContratacaoRule      → extends Rule<ContratacaoContext> (marker)
ContratacaoValidator → implements Validator<ContratacaoRule, ContratacaoContext>;
                       Spring injeta List<ContratacaoRule> automaticamente (ordenado por @Order)
```

Regras de contratação existentes (`application/contratacao/rules/`): `ProdutoSuportado` (roda primeiro), `DataFimVigenciaInvalida`, `ValorLimiteContrato`, `MetadadoRule`. Todas recebem `ContratacaoContext` e acessam o body via `contexto.dados()`.
Regras de cancelamento (`application/cancelamento/rules/`): `ProdutoSuportadoCancelamento` (anotada com `@Order(Ordered.HIGHEST_PRECEDENCE)` — roda **antes** de `TipoProdutoCancelamento`, rejeitando produto não habilitado para cancelamento) e `TipoProdutoCancelamento` (`@Order(5)` — produto do header vs. produto persistido; roda antes de `TransicaoStatusValida` para que divergência de produto falhe com mensagem mais específica que erro de transição). Ambas recebem `CancelamentoContext`.

**Adicionar regra de criação**: crie um `@Component` (não `@Service` — rules são estratégias plugáveis, não o orquestrador) que implemente `ContratacaoRule` com `aceita(ContratacaoContext contexto)`/`validar(ContratacaoContext contexto)` — é injetado automaticamente no `ContratacaoValidator`. Use `@Order` se a regra precisar rodar antes/depois de outra.

### Particionamento temporal (crítico)

Tabela `autorizacoes` particionada por `id_particao_conta` (range **900–999**).

- **Partição de escrita**: `ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(dataFimVigencia)` — `900 + (semanas desde Epoch % 100)`.
- **Partição segura para drop**: `ControleExpurgoAutorizacao.obterParticaoExpurgoDrop(dataReferencia)` — lança `BusinessException` se a data está no passado ou colide com a partição de escrita atual.
- **UUID com partição embutida**: `IdContaUUIDPartitionDistributor.getPartitionFast(idUnicoContaContratante)` + `ReversibleUUIDv7.generate(particao)`. Extrai depois com `ReversibleUUIDv7.extract(uuid)`, sem query adicional.
- Tudo é orquestrado em `Autorizacao.inicializaCriacao()`, chamado no `@AfterMapping` do MapStruct.

Chave composta: `IdAutorizacao(UUID idAutorizacao, Integer idParticaoConta)` como `@EmbeddedId`. Queries só por UUID usam JPQL explícito em `PixAutoRepository`.

### Mapeamento de status

`status` na entidade `Autorizacao` é `Integer`, **não** enum — mas o enum `StatusAutorizacao` é a **fonte da verdade** dos valores. Cancelamento sempre grava `CANCELADA` (= 5). Na **criação**, o status inicial depende do produto (`Autorizacao.STATUS_INICIAL_POR_PRODUTO`, consultado dentro de `inicializaCriacao()`): `PIX_AUTO` nasce `RECEBIDA` (= 1) — vira `ATIVA` após aprovação do cliente pagador (`PATCH /decisao`, `acao: APROVAR`), ou `REJEITADA` (= 6) se o cliente rejeitar ou se o prazo de 10 minutos da jornada 1 expirar sem resposta (ver `application/decisao/`) — e `DDA_AUTO` nasce `ATIVA` (= 4) diretamente, sem etapa de aprovação. Produto sem entrada nesse mapa faz `inicializaCriacao()` lançar `IllegalStateException` (falha explícita, não herda `ATIVA` por omissão). Todas as gravações usam `StatusAutorizacao.X.getStatusAutorizacao()` (sem números mágicos). O enum também carrega o grafo de transições da máquina de estados via `podeTransicionarPara(destino)`, consultado por `DecidirAutorizacaoUseCase` — mas **a checagem de idempotência da decisão exige `statusAtual == RECEBIDA` explicitamente**, não apenas alcançabilidade no grafo: `ATIVA → REJEITADA` também é uma aresta válida (para outro fluxo de negócio), e sem a checagem explícita uma expiração atrasada rejeitaria uma autorização já aprovada (ver `TransicaoValidaDecisao`).

A jornada de origem (header `tipoJornada` na criação) é persistida em coluna própria
`tipo_jornada` (`Autorizacao.tipoJornada`, enum `TipoJornadaAutorizacao`) — **não** é
recuperável só por `motivo_status`, que é sobrescrito a cada transição de status. Linhas
anteriores a essa coluna existir têm `tipo_jornada = 0` (`TipoJornadaAutorizacao.DESCONHECIDA`).

### Convenções

- DTOs de **request** são **records imutáveis** (`entrypoint/contratosrest/`): `CriarAutorizacaoRequest` (só os 15 campos do body) e `CancelarAutorizacaoRequest`. Nenhuma das duas features muta o request — cada uma tem seu próprio record de contexto imutável em `application/{contratacao,cancelamento}`: `ContratacaoContext` carrega `tipoJornada` (header) + `dados` (o request do body); `CancelamentoContext` carrega `idAutorizacao` (path), `tipoProduto` (header), o produto lido do banco e `dados` (o request do body) como parâmetros explícitos. (`tipoProduto` é `String` no request de criação; `metadados` é `JsonNode`. O response `AutorizacaoCompletaResponseDto` ainda é `@Data @Builder`.)
- Mappers `@Mapper(componentModel = "spring")` com callbacks `@AfterMapping`.
- `@Transactional` nos **Use Cases**, chamados diretamente pelo `AutorizacaoController` (sem orquestrador/strategy intermediário).
- Testes de domínio (`domain/utilities/`) são lógica pura, sem Spring.

## Armadilhas críticas

1. **Base de URL é `/api/autorizacoes`** (plural). README/diagramas antigos citam `/api/autorizacao`.
2. **Só existem `PIX_AUTO` e `DDA_AUTO`** — `CARTAO_CREDITO` não existe.
3. **Partições vão de 900 a 999**, não de 1 a 100.
4. **`Autorizacao` está em `domain/entities/`**. (O antigo `domain/model/ContratoBase` — dead code — foi removido junto com o pacote `domain/model`.)
5. **PostgreSQL 18 obrigatório** — sem fallback H2; dialeto Hibernate específico.
6. **Records imutáveis** — não tente reatribuir campos; recrie o record.
7. **`AutorizacaoEventoPayload` não serializa a entidade JPA diretamente** — é um record dedicado com `@JsonProperty` mapeando cada campo para o nome da coluna. Se adicionar/renomear coluna em `Autorizacao`, atualize o payload manualmente (e replique em `apps/autorizacaostatus-producer`, que tem uma cópia própria do mesmo contrato).
8. **Publish no SNS nunca lança para fora do listener** — `AutorizacaoEventoPublisher.aoPersistir()` captura qualquer exceção e só loga. Não confunda isso com falha silenciosa da API: a criação/cancelamento/decisão já foi commitada antes do listener rodar.
9. **A checagem de transição da decisão não é só `podeTransicionarPara`** — exige `statusAtual == RECEBIDA` explicitamente antes de checar o grafo (ver seção "Mapeamento de status" acima). Reusar só `podeTransicionarPara` quebraria a idempotência da rota `/decisao`.
10. **`tipo_jornada` é NOT NULL** — qualquer novo caminho de criação de `Autorizacao` (fora do `AutorizacaoMapper`) precisa setar `tipoJornada`, ou a gravação falha na constraint.
11. **Nunca submeta ao JPA uma instância detached cuja linha você mesmo apagou na mesma transação.** O `ExpurgoAutorizacaoService` já fez isso (`delete` → `flush` → `detach` → `save`) e funcionou por meses — até `@Version` ser adicionado. Com um campo de versão, `AbstractEntityPersister.isTransient` deixa de responder `null` ("não sei") e passa a responder `FALSE` ("é detached de verdade"); o `merge` então conclui que **outra** transação apagou a linha e lança `StaleObjectStateException` → 409 determinístico, imune a retry. O Hibernate não distingue "a linha sumiu porque outro apagou" de "a linha sumiu porque eu apaguei". Hoje a movimentação de partição é um `UPDATE` nativo do `id_particao_conta` (row movement do PostgreSQL ≥ 11), sem `merge` de instância detached em lugar nenhum. Ver a change `corrigir-expurgo-merge-version`.
12. **Movimentar partição muda a forma do conflito de concorrência.** Sob disputa, a transação perdedora não recebe conflito de versão e sim `CannotAcquireLockException` (SQLSTATE 40001, "tuple to be locked was already moved to another partition") — tratada por um handler de `ConcurrencyFailureException`, sem o qual viraria 500 em vez de 409.
13. **`CREATE INDEX CONCURRENTLY` não funciona em tabela particionada** — o Postgres não recursa para as partições e deixa o índice-pai `INVALID`, invisível ao planejador. Foi o que aconteceu com `idx_autorizacoes_conta_status_data` na v1.0.3, e a listagem varreu as 989 partições sequencialmente até a v1.0.6 corrigir. O procedimento correto tem 3 passos: `CREATE INDEX ON ONLY` no pai (nasce inválido, por design) → `CREATE INDEX CONCURRENTLY` em cada partição → `ALTER INDEX ... ATTACH PARTITION` de cada filho. O pai vira válido sozinho quando o último é anexado. Como `CONCURRENTLY` não roda em bloco transacional, isso não cabe em `DO`/PL-pgSQL — a v1.0.6 usa `\gexec` do psql.
14. **Partição nova precisa do índice anexado.** `CREATE TABLE ... PARTITION OF` cria e anexa os índices filhos sozinho; partição criada solta e depois anexada com `ATTACH PARTITION`, não — e o índice-pai volta a `INVALID` silenciosamente.
15. **O banco impõe menos do que a entidade declara — confira antes de confiar.** Com `ddl-auto: none`, `@Column(nullable = false)` não cria nada: é documentação. Até a v1.0.5, 14 colunas declaradas obrigatórias na entidade aceitavam `NULL` no banco. A v1.0.5 fechou 13 delas; **`valor_limite` segue nulável** porque há linha legada com `NULL` e escolher um valor monetário é decisão de negócio, não de migration.

## Aplicação relacionada

`apps/temporiza-autorizacao` (porta 8084) consome o evento de recepção de `PIX_AUTO`/`SPI_J1`
publicado por esta app e aciona `PATCH /decisao` (`acao: EXPIRAR`) no vencimento de 10 minutos.
Ver [CLAUDE.md](../temporiza-autorizacao/CLAUDE.md) dela para o fluxo completo do lado do
temporizador.

## Documentação em `docs/`

- [info_build-my-image-and-execute.md](../../docs/info_build-my-image-and-execute.md) — Docker + PostgreSQL com partman/cron
- [exemplos-queries.sql](../../infra/local/postgres/exemplos-queries.sql) — scripts SQL de particionamento
- [post-autorizacoes.txt](../../docs/post-autorizacoes.txt) — exemplos de payloads REST
- [modelo-dados-e-dados-poc-testada-para-essa-implementacao.md](../../docs/arquitetura/modelo-dados-e-dados-poc-testada-para-essa-implementacao.md) — racional do particionamento (Buffer Ring + UUIDv7 reversível)

## Checklist antes do commit

- [ ] `mvn test` passa
- [ ] `mvn clean compile` sem erros
- [ ] Exceções corretas: `BusinessException` (422) para regras, `ApplicationException` (500) para inesperados
- [ ] Se mexeu em particionamento, rodar `ControleExpurgoAutorizacaoTest`
- [ ] DTOs (records) recriados, não mutados
- [ ] Se mexeu no schema de `autorizacoes`, atualizar `AutorizacaoEventoPayload` aqui, em `apps/autorizacaostatus-producer` **e** o `.avsc` em `apps/autorizacaostatus-producer`/`apps/eventos-consumer`
- [ ] Se mexeu na entidade `Autorizacao`, conferir se `apps/contratoquery` precisa do mesmo campo
- [ ] Se mexeu na rota `/decisao` ou no cálculo de `data_hora_inclusao`, conferir `apps/temporiza-autorizacao` (consumidor do evento de recepção)
