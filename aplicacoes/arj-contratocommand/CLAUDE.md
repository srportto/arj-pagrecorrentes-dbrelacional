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

## Build & Testes

```bash
mvn clean package                            # Compilar + testes + JAR
mvn spring-boot:run                          # Rodar localmente
mvn test                                     # Todos os testes
mvn test -Dtest=ControleExpurgoAutorizacaoTest          # Classe específica
mvn test -Dtest=ControleExpurgoAutorizacaoTest#metodo   # Método específico
```

> **Maven Wrapper quebrado no Windows**: se `./mvnw.cmd` falhar, use `mvn` diretamente.

Classes de teste existentes: `ContratocommandApplicationTests`, `PixAutoAutorizacaoServiceTest`, `ControleExpurgoAutorizacaoTest` (+ helper `GeraDatasPorParticao`). `ListarAutorizacoesServiceTest` foi movido para `arj-contratoquery`.

## Pré-requisitos

- **Java 25** (JDK 25+) — usa `public static void main()`; a forma `void main()` do Java 25 está pendente de suporte do maven plugin (ver `// TODO` no entrypoint)
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

> A base é `/api/autorizacoes` (**plural**). As leituras ficam no `arj-contratoquery` (porta 8081): `GET /api/autorizacoes` (listagem paginada — antes `GET /listar`) e `GET /api/autorizacoes/{autorizacaoId}` (consulta por id, 404 se não encontrado).

## Arquitetura (hexagonal, 4 camadas)

```
entrypoint/   → AutorizacaoController + DTOs (records imutáveis em contratosrest/)
application/  → Use Cases por feature (contratacao/, cancelamento/), Mappers, Repositories
domain/       → Entidades, Enums, Converters, Utilities — lógica pura, sem Spring
shared/       → Exceções, Interceptadores (ApiExceptionHandler), framework de validação
```

`application/` divide-se em:
- raiz de `application/` — componentes **compartilhados** por todos os produtos e por ambas as features: `AutorizacaoRepository`, `AutorizacaoMapper`. Não têm subpacote próprio (não são uma feature).
- `contratacao/` — `CriarAutorizacaoUseCase`, `ContratacaoValidator`, `ContratacaoRule` e `rules/` (inclui `ProdutoSuportado`)
- `cancelamento/` — `CancelarAutorizacaoUseCase`, `CancelamentoContext`, `CancelamentoValidator`, `CancelamentoRule` e `rules/`

Dentro de cada feature, o estereótipo Spring reflete o papel: `@Service` nos orquestradores (`ContratacaoValidator`, `CancelamentoValidator`, `CriarAutorizacaoUseCase`, `CancelarAutorizacaoUseCase` — a lógica de negócio principal da operação), `@Component` nas rules individuais (estratégias plugáveis, injetadas coletivamente via `List<ContratacaoRule>`/`List<CancelamentoRule>`).

Não há mais orquestradores nem strategies por produto: o controller chama os use cases diretamente, e a variação por produto (incluindo a rejeição de produto desconhecido) vive inteiramente nas rules.

### Fluxo de uma requisição POST (criar)

```
AutorizacaoController.insert()
  └─ CriarAutorizacaoUseCase.execute()   (application/contratacao, @Transactional)
       ├─ ContratacaoValidator.validar() ← roda todas as ContratacaoRule (ProdutoSuportado primeiro)
       ├─ AutorizacaoMapper.toDomain()    ← MapStruct + @AfterMapping
       │    └─ Autorizacao.inicializaCriacao()  ← gera UUID+partição, defaults
       └─ AutorizacaoRepository.save()
```

O cancelamento segue o mesmo padrão: o controller chama `CancelarAutorizacaoUseCase.execute()` (application/cancelamento) diretamente, passando um `CancelamentoContext` imutável (path `idAutorizacao` + header `tipoProduto` + corpo).

### Variação por produto vive em rules, não em strategies

Não existem mais `*OrquestradorService`, `*Service` (strategy) nem `ContratacaoService`/`CancelamentoService`. A rejeição de `tipoProduto` desconhecido na criação é feita pela rule `ProdutoSuportado` (`application/contratacao/rules/`), anotada com `@Order(Ordered.HIGHEST_PRECEDENCE)` para rodar antes das demais `ContratacaoRule` — ela lança `BusinessException` ("Produto nao suportado ou invalido...") do mesmo jeito que o antigo orquestrador. No cancelamento, o header `tipoProduto` já é resolvido para o enum no controller (`TipoProduto.obterTipoProdutoEnumPorNome`) e a rule `TipoProdutoCancelamento` valida a divergência contra o produto lido do banco.

**Adicionar um produto novo**: adicione o valor em `TipoProduto` e, se houver regras específicas do produto, expresse-as em uma rule usando `aceita(request)` para filtrar por produto. Não crie classes de strategy — `Repository`, `Mapper` e `UseCase` são únicos e compartilhados. (Os arquivos em `docs/strategyProduto/` são só exemplos didáticos — não refletem o código de produção.)

### Framework de validação de regras de negócio

```
Rule<T>              → interface (shared/validationsetup): aceita(T) + validar(T)
Validator<R,T>       → interface: getRules() + validar(T) default que itera as regras
ContratacaoRule      → extends Rule<CriarAutorizacaoRequest> (marker)
ContratacaoValidator → implements Validator<ContratacaoRule, CriarAutorizacaoRequest>;
                       Spring injeta List<ContratacaoRule> automaticamente (ordenado por @Order)
```

Regras de contratação existentes (`application/contratacao/rules/`): `ProdutoSuportado` (roda primeiro), `DataFimVigenciaInvalida`, `ValorLimiteContrato`, `MetadadoRule`.
Regra de cancelamento (`application/cancelamento/rules/`): `TipoProdutoCancelamento`.

**Adicionar regra de criação**: crie um `@Component` (não `@Service` — rules são estratégias plugáveis, não o orquestrador) que implemente `ContratacaoRule` — é injetado automaticamente no `ContratacaoValidator`. Use `@Order` se a regra precisar rodar antes/depois de outra.

### Particionamento temporal (crítico)

Tabela `autorizacoes` particionada por `id_particao_conta` (range **900–999**).

- **Partição de escrita**: `ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(dataFimVigencia)` — `900 + (semanas desde Epoch % 100)`.
- **Partição segura para drop**: `ControleExpurgoAutorizacao.obterParticaoExpurgoDrop(dataReferencia)` — lança `BusinessException` se a data está no passado ou colide com a partição de escrita atual.
- **UUID com partição embutida**: `IdContaUUIDPartitionDistributor.getPartitionFast(idUnicoContaContratante)` + `ReversibleUUIDv7.generate(particao)`. Extrai depois com `ReversibleUUIDv7.extract(uuid)`, sem query adicional.
- Tudo é orquestrado em `Autorizacao.inicializaCriacao()`, chamado no `@AfterMapping` do MapStruct.

Chave composta: `IdAutorizacao(UUID idAutorizacao, Integer idParticaoConta)` como `@EmbeddedId`. Queries só por UUID usam JPQL explícito em `PixAutoRepository`.

### Mapeamento de status

`status` na entidade `Autorizacao` é `Integer`, **não** enum — mas o enum `StatusAutorizacao` é a **fonte da verdade** dos valores: criação grava `ATIVA` (= 4) e cancelamento grava `CANCELADA` (= 5), via `StatusAutorizacao.X.getStatusAutorizacao()` (sem números mágicos).

### Exceções e códigos HTTP

Tratadas em `shared/interceptors/api/ApiExceptionHandler`.

| Origem | HTTP | Quando |
|--------|------|--------|
| `@Valid` em DTO | 400 | Violação de `@NotNull`, `@Min`, `@Max` |
| `BusinessException` | 422 | Regra de negócio (data no passado, produto inválido, etc.) |
| `ApplicationException` | 500 | Erro inesperado de sistema |

### Convenções

- DTOs de **request** são **records imutáveis** (`entrypoint/contratosrest/`): `CriarAutorizacaoRequest` e `CancelarAutorizacaoRequest`. O cancelamento não muta o request — usa o record `CancelamentoContext` (`application/cancelamento`) para carregar `idAutorizacao` (path), `tipoProduto` (header) e o produto lido do banco como parâmetros explícitos. (`tipoProduto` é `String` no request de criação; `metadados` é `JsonNode`. O response `AutorizacaoCompletaResponseDto` ainda é `@Data @Builder`.)
- Mappers `@Mapper(componentModel = "spring")` com callbacks `@AfterMapping`.
- `@Transactional` nos **Use Cases**, chamados diretamente pelo `AutorizacaoController` (sem orquestrador/strategy intermediário).
- Testes de domínio (`domain/utilities/`) são lógica pura, sem Spring.

## Armadilhas críticas

1. **Base de URL é `/api/autorizacoes`** (plural). README/diagramas antigos citam `/api/autorizacao`.
2. **Só existem `PIX_AUTO` e `DDA_AUTO`** — `CARTAO_CREDITO` não existe.
3. **Partições vão de 900 a 999**, não de 1 a 100.
4. **`Autorizacao` está em `domain/entities/`**. (O antigo `domain/model/ContratoBase` — dead code — foi removido junto com o pacote `domain/model`.)
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
