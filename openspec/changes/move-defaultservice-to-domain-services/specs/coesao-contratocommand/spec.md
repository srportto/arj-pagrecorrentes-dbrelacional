## ADDED Requirements

### Requirement: Regras de negócio residem no domínio e orquestradores na aplicação

As regras de negócio de contratação e cancelamento — contratos de strategy (`ContratacaoService`, `CancelamentoService`), validators, rules e o `CancelamentoContext` — SHALL residir na camada de domínio, no pacote `domain/services`, preservando os subpacotes por operação (`contratacao`, `cancelamento`) e seus `rules`. Os orquestradores de seleção por produto (`ContratacaoOrquestradorService`, `CancelamentoOrquestradorService`) SHALL residir na camada de aplicação, em `application/services`, pois representam orquestração de caso de uso. O pacote `application/defaultservice` MUST NOT existir; nenhuma classe MUST declarar pacote ou `import` sob `...application.defaultservice...`. Nenhuma classe da camada de domínio MUST importar a camada de aplicação. A relocação SHALL preservar comportamento, assinaturas públicas e contratos REST — somente a localização e o nome do pacote mudam.

#### Scenario: Regras de negócio estão no domínio

- **WHEN** a árvore de pacotes da aplicação `contratocommand` é inspecionada
- **THEN** os contratos de strategy, validators, rules e o contexto de cancelamento estão sob `domain/services/{contratacao,cancelamento}` e não existe pacote `application/defaultservice`

#### Scenario: Orquestradores permanecem na aplicação

- **WHEN** a localização dos orquestradores de contratação e cancelamento é inspecionada
- **THEN** `ContratacaoOrquestradorService` e `CancelamentoOrquestradorService` estão sob `application/services/{contratacao,cancelamento}`

#### Scenario: Sem referência ao pacote antigo e sem dependência domínio→aplicação

- **WHEN** o código de produção e de teste é compilado
- **THEN** nenhuma declaração `package` ou `import` referencia `...application.defaultservice...`, nenhuma classe de `domain` importa `application`, e o build compila sem erros

#### Scenario: Comportamento preservado após a relocação

- **WHEN** os fluxos de criação (`POST /api/autorizacoes`) e cancelamento (`PATCH /api/autorizacoes/{id}/cancelar`) são exercitados após a mudança
- **THEN** o resultado é idêntico ao comportamento anterior, sem alteração de contrato REST, status HTTP ou regra de negócio
