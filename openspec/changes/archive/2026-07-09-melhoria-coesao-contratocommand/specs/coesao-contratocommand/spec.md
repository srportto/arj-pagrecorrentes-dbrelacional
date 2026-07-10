# Delta: coesao-contratocommand

## ADDED Requirements

### Requirement: Fluxo único compartilhado por produto, com variação apenas em rules

A persistência (`AutorizacaoRepository`), o mapeamento request→entidade (`AutorizacaoMapper`) e o fluxo de cada operação (`CriarAutorizacaoUseCase`, `CancelarAutorizacaoUseCase`) SHALL ser únicos e compartilhados entre produtos (PIX_AUTO, DDA_AUTO). O `AutorizacaoController` SHALL invocar os use cases diretamente, sem camada intermediária de orquestrador ou strategy por produto. A rejeição de produto não suportado na criação SHALL ser feita por uma `ContratacaoRule` (`ProdutoSuportado`) que resolve o `TipoProduto` do request (case-insensitive) e lança `BusinessException` (HTTP 422) preservando a mensagem atual de produto não suportado; essa rule MUST executar antes das demais rules de contratação. Variação de comportamento por produto MUST ser expressa exclusivamente via rules (usando `aceita()`), não via classes de strategy. Adicionar um novo produto MUST NOT exigir duplicar Mapper, Repository ou UseCases.

#### Scenario: Criação com produto suportado

- **WHEN** uma requisição `POST /api/autorizacoes` chega com `tipoProduto` suportado (ex.: `PIX_AUTO`, em qualquer caixa)
- **THEN** o controller invoca `CriarAutorizacaoUseCase` diretamente e a resposta é 201, idêntica ao comportamento anterior ao refactor

#### Scenario: Criação com produto não suportado

- **WHEN** uma requisição `POST /api/autorizacoes` chega com `tipoProduto` desconhecido ou nulo
- **THEN** a rule `ProdutoSuportado` lança `BusinessException` (HTTP 422) com a mensagem de produto não suportado usada antes do refactor

#### Scenario: ProdutoSuportado executa antes das demais rules

- **WHEN** uma requisição de criação com produto desconhecido também viola outra regra de negócio (ex.: data de vigência no passado)
- **THEN** o erro reportado é o de produto não suportado, pois `ProdutoSuportado` executa primeiro

#### Scenario: Ausência da camada de strategy

- **WHEN** o código da aplicação `contratocommand` é inspecionado
- **THEN** não existem orquestradores (`ContratacaoOrquestradorService`, `CancelamentoOrquestradorService`), strategies por produto (`PixAutoService`, `DdaAutoService`) nem interfaces de strategy (`ContratacaoService`, `CancelamentoService`)

#### Scenario: Sem duplicação de componentes por produto

- **WHEN** a camada de aplicação é inspecionada
- **THEN** existe um único Mapper, um único Repository e um único use case por operação para a entidade `Autorizacao`, sem cópias por produto

#### Scenario: Cancelamento com produto divergente

- **WHEN** uma requisição `PATCH /api/autorizacoes/{id}/cancelar` chega com header `tipoProduto` diferente do produto atrelado à autorização
- **THEN** a rule `TipoProdutoCancelamento` lança `BusinessException` (HTTP 422), como antes do refactor

### Requirement: Organização por feature na aplicação e domínio puro

Os componentes de cada operação SHALL residir agrupados por feature na camada de aplicação: `application/contratacao` (use case de criação, `ContratacaoValidator`, `ContratacaoRule` e suas rules, incluindo `ProdutoSuportado`) e `application/cancelamento` (use case de cancelamento, `CancelamentoContext`, `CancelamentoValidator`, `CancelamentoRule` e suas rules). `AutorizacaoRepository` e `AutorizacaoMapper` SHALL permanecer compartilhados em `application/autorizacao`. Os pacotes `application/services`, `application/enabledproduct`, `application/autorizacao/usecases` e `domain/services` MUST NOT existir. Nenhuma classe da camada de domínio (`domain/`) MUST importar `entrypoint` ou `application`, nem usar estereótipos/anotações Spring — o domínio contém apenas `entities`, `enums`, `converters` e `utilities`. O framework de validação (`shared/validationsetup`) permanece inalterado.

#### Scenario: Árvore de pacotes organizada por feature

- **WHEN** a árvore de pacotes da aplicação `contratocommand` é inspecionada
- **THEN** use case, validator, rules (e contexto, no cancelamento) de cada operação estão sob `application/{contratacao,cancelamento}`, o repository e o mapper estão em `application/autorizacao`, e não existem os pacotes `application/services`, `application/enabledproduct`, `application/autorizacao/usecases` e `domain/services`

#### Scenario: Domínio sem dependências de borda ou framework

- **WHEN** as classes sob `domain/` são inspecionadas
- **THEN** nenhuma importa pacotes de `entrypoint` ou `application` e nenhuma usa anotações Spring (`@Component`, `@Service`, etc.)

#### Scenario: Comportamento preservado após a reorganização

- **WHEN** a suíte de testes é executada após a mudança de pacotes
- **THEN** todos os testes passam e os contratos REST (endpoints, headers, códigos HTTP e mensagens) permanecem os mesmos

## REMOVED Requirements

### Requirement: Variação por produto vive em strategy fino, sem duplicação de encanamento

**Reason**: Após a centralização do encanamento nos use cases compartilhados, as strategies por produto ficaram idênticas exceto pela constante do enum; a camada orquestrador+strategy (6 classes) só verificava se o produto era conhecido. A variação por produto passa a ser expressa exclusivamente via rules.

**Migration**: A seleção por strategy é substituída pela rule `ProdutoSuportado` (mesma `BusinessException`/HTTP 422 e mensagem). O requirement "Fluxo único compartilhado por produto, com variação apenas em rules" cobre as invariantes remanescentes (sem duplicação de Mapper/Repository/UseCase, produto não suportado rejeitado).

### Requirement: Regras de negócio residem no domínio e orquestradores na aplicação

**Reason**: Os validators e rules validam DTOs de request e usam anotações Spring — são código de aplicação; mantê-los em `domain/services` invertia a direção de dependência do hexagonal (`domain → entrypoint`). Os orquestradores deixam de existir.

**Migration**: Validators, rules e `CancelamentoContext` movem para `application/{contratacao,cancelamento}`; o requirement "Organização por feature na aplicação e domínio puro" define a nova estrutura e a invariante de pureza do domínio.
