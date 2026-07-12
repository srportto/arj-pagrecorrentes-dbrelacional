## MODIFIED Requirements

### Requirement: Organização por feature na aplicação e domínio puro

Os componentes de cada operação SHALL residir agrupados por feature na camada de aplicação: `application/contratacao` (use case de criação, `ContratacaoValidator`, `ContratacaoRule` e suas rules, incluindo `ProdutoSuportado`) e `application/cancelamento` (use case de cancelamento, `CancelamentoContext`, `CancelamentoValidator`, `CancelamentoRule` e suas rules). Componentes compartilhados por mais de uma feature (não específicos de uma operação) SHALL residir na raiz de `application/`, sem subpacote próprio; `AutorizacaoRepository` e `AutorizacaoMapper` SHALL residir em `application/` (não em `application/autorizacao`). O pacote `application/autorizacao` MUST NOT existir. Os pacotes `application/services`, `application/enabledproduct`, `application/autorizacao/usecases` e `domain/services` MUST NOT existir. Nenhuma classe da camada de domínio (`domain/`) MUST importar `entrypoint` ou `application`, nem usar estereótipos/anotações Spring — o domínio contém apenas `entities`, `enums`, `converters` e `utilities`. O framework de validação (`shared/validationsetup`) permanece inalterado. Entre os beans Spring-gerenciados de `application/`, os orquestradores de regra de negócio por operação (`ContratacaoValidator`, `CancelamentoValidator`, `CriarAutorizacaoUseCase`, `CancelarAutorizacaoUseCase`) SHALL usar o estereótipo `@Service`; as rules individuais (implementações de `ContratacaoRule`/`CancelamentoRule`) SHALL usar `@Component`.

#### Scenario: Árvore de pacotes organizada por feature

- **WHEN** a árvore de pacotes da aplicação `contratocommand` é inspecionada
- **THEN** use case, validator, rules (e contexto, no cancelamento) de cada operação estão sob `application/{contratacao,cancelamento}`, o repository e o mapper estão soltos na raiz de `application/`, e não existem os pacotes `application/autorizacao`, `application/services`, `application/enabledproduct`, `application/autorizacao/usecases` e `domain/services`

#### Scenario: Domínio sem dependências de borda ou framework

- **WHEN** as classes sob `domain/` são inspecionadas
- **THEN** nenhuma importa pacotes de `entrypoint` ou `application` e nenhuma usa anotações Spring (`@Component`, `@Service`, etc.)

#### Scenario: Comportamento preservado após a reorganização

- **WHEN** a suíte de testes é executada após a mudança de pacotes
- **THEN** todos os testes passam e os contratos REST (endpoints, headers, códigos HTTP e mensagens) permanecem os mesmos

#### Scenario: Orquestradores usam @Service

- **WHEN** as classes `ContratacaoValidator`, `CancelamentoValidator`, `CriarAutorizacaoUseCase` e `CancelarAutorizacaoUseCase` são inspecionadas
- **THEN** todas estão anotadas com `@Service`

#### Scenario: Rules usam @Component

- **WHEN** as implementações de `ContratacaoRule` e `CancelamentoRule` (`DataFimVigenciaInvalida`, `MetadadoRule`, `ValorLimiteContrato`, `ProdutoSuportado`, `TipoProdutoCancelamento`, `ProdutoSuportadoCancelamento`) são inspecionadas
- **THEN** todas estão anotadas com `@Component`, não `@Service`
