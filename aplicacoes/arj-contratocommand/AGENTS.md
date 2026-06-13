# CLAUDE.md

> Guia para agentes de IA (Claude Code, Copilot, etc.) trabalharem neste repositório.
> **Este arquivo e `AGENTS.md` são espelhos — mantenha-os idênticos ao editar.**

API REST de **autorizações de produtos financeiros** (PIX Automático e DDA Automático), em **arquitetura hexagonal**, com **particionamento temporal** em PostgreSQL e expurgo automático de dados.

## Comece por aqui

Leia nesta ordem:
1. [AutorizacaoController.java](src/main/java/br/com/srportto/contratocommand/entrypoint/AutorizacaoController.java) — os 3 endpoints REST
2. [ContratacaoOrquestradorService.java](src/main/java/br/com/srportto/contratocommand/application/defaultservice/contratacao/ContratacaoOrquestradorService.java) — orquestração via Strategy
3. [CriarPixAutoUseCase.java](src/main/java/br/com/srportto/contratocommand/application/enabledproduct/pixauto/usecases/CriarPixAutoUseCase.java) — caso de uso completo (validação → mapper → save)
4. [Autorizacao.java](src/main/java/br/com/srportto/contratocommand/domain/entities/Autorizacao.java) — entidade de domínio com particionamento

## Build & Testes

```bash
mvn clean package                            # Compilar + testes + JAR
mvn spring-boot:run                          # Rodar localmente
mvn test                                     # Todos os testes
mvn test -Dtest=ControleExpurgoAutorizacaoTest          # Classe específica
mvn test -Dtest=ControleExpurgoAutorizacaoTest#metodo   # Método específico
```

> **Maven Wrapper quebrado no Windows**: se `./mvnw.cmd` falhar, use `mvn` diretamente.

Classes de teste existentes: `ContratocommandApplicationTests`, `PixAutoAutorizacaoServiceTest`, `ListarAutorizacoesServiceTest`, `ControleExpurgoAutorizacaoTest` (+ helper `GeraDatasPorParticao`).

## Pré-requisitos

- **Java 25** (JDK 25+) — usa `void main()` em vez de `public static void main()`
- **PostgreSQL 16+** com `pg_partman` e `pg_cron` — **sem fallback para H2**
- Variáveis de ambiente obrigatórias: `DB_NAME`, `DB_USER_NAME`, `DB_PASSWORD`
- Variáveis de ambiente opcionais (datasource, com defaults no `application.yaml`):
  - `DB_TRANSACTION_ISOLATION` — nível de isolamento (default `TRANSACTION_READ_COMMITTED`; aceita `TRANSACTION_READ_UNCOMMITTED`, `TRANSACTION_READ_COMMITTED`, `TRANSACTION_REPEATABLE_READ`, `TRANSACTION_SERIALIZABLE`).
  - `DB_READ_ONLY` — modo de acesso (default `false` no `contratocommand`, `true` no `contratoquery`).
  - Pool HikariCP: `DB_POOL_MAX_SIZE`, `DB_POOL_MIN_IDLE`, `DB_POOL_CONNECTION_TIMEOUT`, `DB_POOL_IDLE_TIMEOUT`, `DB_POOL_MAX_LIFETIME`.
- Docker com PostgreSQL em `run_postgres16_ja_com_cron_partman/`. Exemplos de payloads em `docs/post-autorizacoes.txt`.

## Stack

| Componente | Versão | Notas |
|---|---|---|
| Java | 25 | `void main()`; records imutáveis |
| Spring Boot | 4.0.4 | Web MVC, Data JPA, Validation |
| Lombok | 1.18.40 | `@Data`, `@Getter`, `@Setter`, `@AllArgsConstructor` |
| MapStruct | 1.5.5.Final | Mapeamento DTO↔Entity com `@AfterMapping` |
| Yasson | 3.0.3 | Jakarta JSON Binding |
| PostgreSQL | 16+ | Particionamento com `pg_partman` + `pg_cron` |

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
application/  → Orquestradores, Services de produto, Use Cases, Mappers, Repositories, Validators
domain/       → Entidades, Enums, Converters, Utilities (lógica pura, sem frameworks)
shared/       → Exceções, Interceptadores (ApiExceptionHandler), framework de validação
```

`application/` divide-se em:
- `defaultservice/contratacao` e `defaultservice/cancelamento` — orquestração + framework de regras
- `enabledproduct/pixauto` e `enabledproduct/ddaauto` — implementação por produto

### Fluxo de uma requisição POST (criar)

```
AutorizacaoController.insert()
  └─ ContratacaoOrquestradorService.criar()            (defaultservice/contratacao)
       └─ percorre List<ContratacaoService> e chama validaContratacaoSuportada(request)
            └─ PixAutoService.criarAutorizacao()        (enabledproduct/pixauto)
                 └─ CriarPixAutoUseCase.execute()       (@Transactional)
                      ├─ ContratacaoValidator.validar() ← roda todas as ContratacaoRule
                      ├─ PixAutoMapper.toDomain()        ← MapStruct + @AfterMapping
                      │    └─ Autorizacao.inicializaCriacao()  ← gera UUID+partição, defaults
                      └─ PixAutoRepository.save()
```

O cancelamento segue o mesmo padrão via `CancelamentoOrquestradorService` + `CancelamentoService` + `CancelarPixAutoUseCase`.

### Strategy Pattern para múltiplos produtos

`ContratacaoOrquestradorService` injeta `List<ContratacaoService>` e seleciona o primeiro cujo `validaContratacaoSuportada(request)` retorna `true`; senão lança `BusinessException` ("Produto nao suportado").

- `PixAutoService` → `CriarPixAutoUseCase` / `CancelarPixAutoUseCase`
- `DdaAutoService` → `CriarDdaAutoUseCase` / `CancelarDdaAutoUseCase`

`PixAutoService` implementa **as duas** interfaces (`ContratacaoService` e `CancelamentoService`).

**Adicionar um produto novo**: crie um `*Service` que implemente `ContratacaoService` e/ou `CancelamentoService`, mais os Use Cases. O orquestrador o descobre automaticamente via injeção de lista. (Não há `ProdutoStrategyFactory` em `src/` — os arquivos em `docs/strategyProduto/` são apenas exemplos didáticos.)

### Framework de validação de regras de negócio

```
Rule<T>              → interface (shared/validationsetup): aceita(T) + validar(T)
Validator<R,T>       → interface: getRules() + validar(T) default que itera as regras
ContratacaoRule      → extends Rule<CriarAutorizacaoRequest> (marker)
ContratacaoValidator → implements Validator<ContratacaoRule, CriarAutorizacaoRequest>;
                       Spring injeta List<ContratacaoRule> automaticamente
```

Regras de contratação existentes (`defaultservice/contratacao/rules/`): `DataFimVigenciaInvalida`, `ValorLimiteContrato`, `MetadadoRule`.
Regra de cancelamento (`defaultservice/cancelamento/rules/`): `TipoProdutoCancelamento`.

**Adicionar regra de criação**: crie um `@Component` que implemente `ContratacaoRule` — é injetado automaticamente no `ContratacaoValidator`.

### Particionamento temporal (crítico)

Tabela `autorizacoes` particionada por `id_particao_conta` (range **900–999**).

- **Partição de escrita**: `ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(dataFimVigencia)` — `900 + (semanas desde Epoch % 100)`.
- **Partição segura para drop**: `ControleExpurgoAutorizacao.obterParticaoExpurgoDrop(dataReferencia)` — lança `BusinessException` se a data está no passado ou colide com a partição de escrita atual.
- **UUID com partição embutida**: `IdContaUUIDPartitionDistributor.getPartitionFast(idUnicoContaContratante)` + `ReversibleUUIDv7.generate(particao)`. Extrai depois com `ReversibleUUIDv7.extract(uuid)`, sem query adicional.
- Tudo é orquestrado em `Autorizacao.inicializaCriacao()`, chamado no `@AfterMapping` do MapStruct.

Chave composta: `IdAutorizacao(UUID idAutorizacao, Integer idParticaoConta)` como `@EmbeddedId`. Queries só por UUID usam JPQL explícito em `PixAutoRepository`.

### Mapeamento de status

`status` na entidade `Autorizacao` é `Integer` (`1 = ATIVO`), **não** enum. O enum `StatusAutorizacao` existe para conversão em `ListarAutorizacoesService`.

### Exceções e códigos HTTP

Tratadas em `shared/interceptors/api/ApiExceptionHandler`.

| Origem | HTTP | Quando |
|--------|------|--------|
| `@Valid` em DTO | 400 | Violação de `@NotNull`, `@Min`, `@Max` |
| `BusinessException` | 422 | Regra de negócio (data no passado, produto inválido, etc.) |
| `ApplicationException` | 500 | Erro inesperado de sistema |

### Convenções

- DTOs são **records imutáveis** (`entrypoint/contratosrest/`) — para alterar, recrie: `new CriarAutorizacaoRequest(...)`. (`tipoProduto` é `String` no request; `metadados` é `JsonNode`.)
- Mappers `@Mapper(componentModel = "spring")` com callbacks `@AfterMapping`.
- `@Transactional` nos **Use Cases** (não nos Services/Orquestradores).
- Testes de domínio (`domain/utilities/`) são lógica pura, sem Spring.

## Armadilhas críticas

1. **Base de URL é `/api/autorizacoes`** (plural). README/diagramas antigos citam `/api/autorizacao`.
2. **Só existem `PIX_AUTO` e `DDA_AUTO`** — `CARTAO_CREDITO` não existe.
3. **Partições vão de 900 a 999**, não de 1 a 100.
4. **`Autorizacao` está em `domain/entities/`**, não em `domain/model/` (lá só existe `ContratoBase`).
5. **PostgreSQL obrigatório** — sem fallback H2; dialeto Hibernate específico.
6. **Records imutáveis** — não tente reatribuir campos; recrie o record.

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
