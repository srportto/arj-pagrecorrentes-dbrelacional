## MODIFIED Requirements

### Requirement: Fluxo único compartilhado por produto, com variação apenas em rules

A persistência (`AutorizacaoRepository`), o mapeamento request→entidade (`AutorizacaoMapper`) e o fluxo de cada operação (`CriarAutorizacaoUseCase`, `CancelarAutorizacaoUseCase`) SHALL ser únicos e compartilhados entre produtos (PIX_AUTO, DDA_AUTO). O `AutorizacaoController` SHALL invocar os use cases diretamente, sem camada intermediária de orquestrador ou strategy por produto. A rejeição de produto não suportado na criação SHALL ser feita por uma `ContratacaoRule` (`ProdutoSuportado`) que resolve o `TipoProduto` do request (case-insensitive), exige que o produto esteja habilitado para contratar segundo o próprio enum e lança `BusinessException` (HTTP 422) preservando a mensagem atual de produto não suportado; essa rule MUST executar antes das demais rules de contratação. No cancelamento, uma `CancelamentoRule` (`ProdutoSuportadoCancelamento`) SHALL executar antes das demais e rejeitar com `BusinessException` (HTTP 422) produto não habilitado para cancelar segundo o enum. Variação de comportamento por produto MUST ser expressa exclusivamente via rules (usando `aceita()`) e via capacidades declaradas no enum `TipoProduto`, não via classes de strategy. Adicionar um novo produto MUST NOT exigir duplicar Mapper, Repository ou UseCases.

#### Scenario: Criação com produto suportado

- **WHEN** uma requisição `POST /api/autorizacoes` chega com `tipoProduto` suportado (ex.: `PIX_AUTO`, em qualquer caixa)
- **THEN** o controller invoca `CriarAutorizacaoUseCase` diretamente e a resposta é 201, idêntica ao comportamento anterior ao refactor

#### Scenario: Criação com produto não suportado

- **WHEN** uma requisição `POST /api/autorizacoes` chega com `tipoProduto` desconhecido, nulo ou não habilitado para contratar
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

#### Scenario: Cancelamento com produto não habilitado para cancelar

- **WHEN** o contexto de cancelamento carrega um `TipoProduto` não habilitado para cancelar
- **THEN** a rule `ProdutoSuportadoCancelamento` lança `BusinessException` (HTTP 422) antes das demais rules de cancelamento
