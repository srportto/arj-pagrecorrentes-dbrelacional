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
- Docker com PostgreSQL em `infra/local/postgres/` (raiz do repositório). Exemplos de payloads em `docs/post-autorizacoes.txt`.
- Dockerfile próprio (multi-stage, Fargate-ready) nesta pasta; `apps/docker-compose.yml` sobe as 2 aplicações + Postgres de uma vez.
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
| GET | `/actuator/health` | Health-check (Actuator) com readiness de banco (indicador `db`). → 200 (UP) / 503 (DOWN) |

> A base é `/api/autorizacoes` (**plural**). Não existem `/olaMundo` nem `/ativas`. As leituras ficam no `arj-contratoquery` (porta 8081): `GET /api/autorizacoes` (listagem paginada por conta — params `idUnicoContaContratante`, `status`, `pagina`, `tamanho`, `ordenarPor`) e `GET /api/autorizacoes/{autorizacaoId}` (consulta por id, 404 se não encontrado).

## Arquitetura (hexagonal, 4 camadas)

```
entrypoint/   → AutorizacaoController + DTOs (records imutáveis em contratosrest/)
application/  → Use Cases por feature (contratacao/, cancelamento/), Mappers, Repositories, eventos/
domain/       → Entidades, Enums, Converters, Utilities — lógica pura, sem Spring
shared/       → Exceções, Interceptadores (ApiExceptionHandler), config/, framework de validação
```

`application/` divide-se em:
- raiz de `application/` — componentes **compartilhados** por todos os produtos e por ambas as features: `AutorizacaoRepository`, `AutorizacaoMapper`. Não têm subpacote próprio (não são uma feature).
- `contratacao/` — `CriarAutorizacaoUseCase`, `ContratacaoContext`, `ContratacaoValidator`, `ContratacaoRule` e `rules/` (inclui `ProdutoSuportado`)
- `cancelamento/` — `CancelarAutorizacaoUseCase`, `CancelamentoContext`, `CancelamentoValidator`, `CancelamentoRule` e `rules/`
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

### Publicação de eventos (após commit)

Ao final de `CriarAutorizacaoUseCase.execute()` e `CancelarAutorizacaoUseCase.execute()`, um `AutorizacaoPersistidaEvent` (só a entidade final — sem campo de tipo) é publicado via `ApplicationEventPublisher`. Quem efetivamente fala com o SNS é `AutorizacaoEventoPublisher`, um `@TransactionalEventListener(phase = AFTER_COMMIT)`:

```
CriarAutorizacaoUseCase / CancelarAutorizacaoUseCase (fim do execute(), ainda na transação)
  └─ eventPublisher.publishEvent(new AutorizacaoPersistidaEvent(autorizacao))
       ⋮ (commit da transação)
AutorizacaoEventoPublisher.aoPersistir()   ← só roda se o commit teve sucesso
  ├─ AutorizacaoEventoPayload.from(autorizacao)  ← chaves = nomes das colunas, não campos Java
  ├─ TipoEventoAutorizacao.porStatus(autorizacao.getStatus())  ← deriva o tipo do status persistido
  └─ SnsClient.publish()  ← tópico sns-estados-autorizacao, message attribute tipoEvento
```

O `tipoEvento` **não é mais informado pelo use case** — é derivado do `status` da entidade (`TipoEventoAutorizacao`, 8 valores em bijeção com `StatusAutorizacao`: `RECEPCAO`, `PENDENCIA_ACEITE`, `INICIO_ATIVACAO`, `ATIVACAO`, `CANCELAMENTO`, `REJEICAO`, `EXPIRACAO`, `FINALIZACAO`). Criação (status `ATIVA`) publica `tipoEvento=ATIVACAO`; cancelamento (status `CANCELADA`) publica `tipoEvento=CANCELAMENTO`. O antigo par `CRIACAO`/`CANCELAMENTO` não existe mais.

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
Regra de cancelamento (`application/cancelamento/rules/`): `TipoProdutoCancelamento`.

**Adicionar regra de criação**: crie um `@Component` (não `@Service` — rules são estratégias plugáveis, não o orquestrador) que implemente `ContratacaoRule` com `aceita(ContratacaoContext contexto)`/`validar(ContratacaoContext contexto)` — é injetado automaticamente no `ContratacaoValidator`. Use `@Order` se a regra precisar rodar antes/depois de outra.

### Particionamento temporal (crítico)

Tabela `autorizacoes` particionada por `id_particao_conta` (range **900–999**).

- **Partição de escrita**: `ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(dataFimVigencia)` — `900 + (semanas desde Epoch % 100)`.
- **Partição segura para drop**: `ControleExpurgoAutorizacao.obterParticaoExpurgoDrop(dataReferencia)` — lança `BusinessException` se a data está no passado ou colide com a partição de escrita atual.
- **UUID com partição embutida**: `IdContaUUIDPartitionDistributor.getPartitionFast(idUnicoContaContratante)` + `ReversibleUUIDv7.generate(particao)`. Extrai depois com `ReversibleUUIDv7.extract(uuid)`, sem query adicional.
- Tudo é orquestrado em `Autorizacao.inicializaCriacao()`, chamado no `@AfterMapping` do MapStruct.

Chave composta: `IdAutorizacao(UUID idAutorizacao, Integer idParticaoConta)` como `@EmbeddedId`. Queries só por UUID usam JPQL explícito em `PixAutoRepository`.

### Mapeamento de status

`status` na entidade `Autorizacao` é `Integer`, **não** enum — mas o enum `StatusAutorizacao` é a **fonte da verdade** dos valores: criação grava `ATIVA` (= 4) e cancelamento grava `CANCELADA` (= 5), via `StatusAutorizacao.X.getStatusAutorizacao()` (sem números mágicos). O enum também carrega o grafo de transições da máquina de estados via `podeTransicionarPara(destino)` — não usado ainda pelas operações de criação/cancelamento, disponível para validações futuras.

### Exceções e códigos HTTP

Tratadas em `shared/interceptors/api/ApiExceptionHandler`.

| Origem | HTTP | Quando |
|--------|------|--------|
| `@Valid` em DTO | 400 | Violação de `@NotNull`, `@Min`, `@Max` |
| `BusinessException` | 422 | Regra de negócio (data no passado, produto inválido, etc.) |
| `ApplicationException` | 500 | Erro inesperado de sistema |

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
8. **Publish no SNS nunca lança para fora do listener** — `AutorizacaoEventoPublisher.aoPersistir()` captura qualquer exceção e só loga. Não confunda isso com falha silenciosa da API: a criação/cancelamento já foi commitada antes do listener rodar.

## Documentação em `docs/`

- [info_build-my-image-and-execute.md](docs/info_build-my-image-and-execute.md) — Docker + PostgreSQL com partman/cron
- [comandos-sql.txt](docs/comandos-sql.txt) — scripts SQL de particionamento
- [post-autorizacoes.txt](docs/post-autorizacoes.txt) — exemplos de payloads REST
- [resultado-poc/POC_PARTICIONAMENTO_BUFFER_RING_UUIDV7.md](docs/resultado-poc/POC_PARTICIONAMENTO_BUFFER_RING_UUIDV7.md) — racional do particionamento
- `docs/strategyProduto/` — **exemplos didáticos** de Strategy (não é o código de produção)

## Checklist antes do commit

- [ ] `mvn test` passa
- [ ] `mvn clean compile` sem erros
- [ ] Exceções corretas: `BusinessException` (422) para regras, `ApplicationException` (500) para inesperados
- [ ] Se mexeu em particionamento, rodar `ControleExpurgoAutorizacaoTest`
- [ ] DTOs (records) recriados, não mutados
- [ ] Se mexeu no schema de `autorizacoes`, atualizar `AutorizacaoEventoPayload` aqui **e** em `apps/autorizacaostatus-producer`
