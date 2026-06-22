## Why

As regras de negócio de contratação e cancelamento (contratos de strategy, validators, rules e o contexto de cancelamento) vivem hoje em `application/defaultservice`, embora a própria documentação de arquitetura atribua "regras de negócio" à camada de domínio. Movê-las para `domain/services` melhora a coesão e deixa explícito que são lógica de domínio. Os orquestradores (componentes Spring que selecionam a strategy por produto) permanecem na camada de aplicação, pois são orquestração de caso de uso, e o nome genérico `defaultservice` é eliminado.

## What Changes

- Mover para `domain/services/{contratacao,cancelamento}` (com seus `rules/`): os contratos de strategy (`ContratacaoService`, `CancelamentoService`), validators (`ContratacaoValidator`, `CancelamentoValidator`), rules (`ContratacaoRule`, `CancelamentoRule`, `ValorLimiteContrato`, `DataFimVigenciaInvalida`, `MetadadoRule`, `TipoProdutoCancelamento`) e o `CancelamentoContext`.
- Manter os orquestradores na camada de aplicação, movendo-os de `application/defaultservice/{contratacao,cancelamento}` para `application/services/{contratacao,cancelamento}` (`ContratacaoOrquestradorService`, `CancelamentoOrquestradorService`).
- Eliminar o pacote `application/defaultservice` por completo.
- Atualizar declarações `package`, arquivos de teste espelhados e todos os `import` afetados (produção e teste).
- Atualizar a documentação de arquitetura e os links de referência (`README`, `CLAUDE.md`, `AGENTS.md`, `docs/arquitetura`).
- Refatoração estrutural pura: nenhuma mudança de comportamento, assinatura pública, contrato REST ou regra de negócio.

## Capabilities

### New Capabilities
<!-- Nenhuma capability nova: é refatoração de organização, sem novo comportamento observável. -->

### Modified Capabilities
- `coesao-contratocommand`: acrescenta a invariante de organização de que as regras de negócio (contratos de strategy, validators, rules e contexto) residem em `domain/services`, enquanto os orquestradores de seleção por produto residem em `application/services`, e o pacote `application/defaultservice` deixa de existir.

## Impact

- **Código de domínio movido**: `application/defaultservice/{contratacao,cancelamento}` (exceto orquestradores, incluindo `rules/`) → `domain/services/{contratacao,cancelamento}`.
- **Orquestradores movidos (continuam em aplicação)**: `application/defaultservice/{contratacao,cancelamento}/*OrquestradorService` → `application/services/{contratacao,cancelamento}`.
- **Imports a corrigir** (referenciam `application.defaultservice`): `entrypoint/AutorizacaoController`, `application/autorizacao/usecases/CriarAutorizacaoUseCase`, `application/autorizacao/usecases/CancelarAutorizacaoUseCase`, `application/enabledproduct/pixauto/PixAutoService`, `application/enabledproduct/ddaauto/DdaAutoService`. Os orquestradores passam a importar explicitamente os contratos/contexto agora em `domain.services`.
- **Testes movidos e/ou com imports a corrigir**: testes espelhados de `defaultservice` (validators/rules → `domain.services`; orquestradores → `application.services`), além de `AutorizacaoControllerTest`, `PixAutoServiceTest`, `DdaAutoServiceTest`, `CriarAutorizacaoUseCaseTest`, `CancelarAutorizacaoUseCaseTest`, `TestFixtures`.
- **Documentação**: `docs/arquitetura/based-java-aplication.md` e os arquivos de referência da app (`README.md`, `CLAUDE.md`, `AGENTS.md`) que citam a árvore de pacotes.
- **Sem impacto** em: contratos REST (`POST /api/autorizacoes`, `PATCH /api/autorizacoes/{id}/cancelar`, health-check), banco de dados, dependências externas e build (`pom.xml`). Artefatos gerados em `target/` são regenerados pelo build.
