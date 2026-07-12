# coesao-contratocommand

## Purpose

Definir as invariantes de coesão e organização da aplicação `contratocommand` que o refactor de limpeza estabeleceu e que trabalhos futuros (incluindo a operação de alteração de contrato) devem manter: cancelamento transacional, exceções do próprio projeto, status via enum, DTOs de request imutáveis sem estado-carteiro, fluxo único compartilhado por produto com variação apenas em rules, contratos REST preservados, domínio sem dead code e organização por feature na aplicação (`application/{contratacao,cancelamento}`, com `AutorizacaoRepository`/`AutorizacaoMapper` compartilhados na raiz de `application/`) e domínio puro, sem dependências de `entrypoint`/`application` nem anotações Spring.

## Requirements

### Requirement: Cancelamento executa dentro de transação

O fluxo de cancelamento, que persiste a mudança de status e transfere a autorização entre partições via `delete + insert`, SHALL executar dentro de um único limite transacional. A anotação `@Transactional` MUST estar no método público de entrada do use case (`execute`), nunca em método privado ou auto-invocado, de modo que falha na reinserção faça rollback do delete.

#### Scenario: Reinserção falha após o delete

- **WHEN** o cancelamento remove a autorização da partição antiga e a reinserção na nova partição falha
- **THEN** a transação faz rollback e a autorização original permanece persistida (nenhuma linha é perdida)

#### Scenario: Anotação transacional efetiva

- **WHEN** o código do use case de cancelamento é inspecionado
- **THEN** `@Transactional` está no método público `execute` e não há `@Transactional` em método privado

### Requirement: Erros inesperados usam exceções do próprio projeto

Falhas inesperadas de sistema SHALL ser sinalizadas com `ApplicationException` (mapeada para HTTP 500) e violações de regra de negócio com `BusinessException` (HTTP 422). O código MUST NOT lançar exceções de framework (ex.: `org.springframework.context.ApplicationContextException`) para representar erros de aplicação.

#### Scenario: Falha inesperada ao buscar autorização

- **WHEN** ocorre um erro inesperado ao obter a autorização por id/partição no cancelamento
- **THEN** é lançada `ApplicationException` e a resposta HTTP é 500

#### Scenario: Autorização não encontrada

- **WHEN** a autorização informada no cancelamento não existe
- **THEN** é lançada `BusinessException` e a resposta HTTP é 422

### Requirement: Status da autorização tem o enum como fonte da verdade

O valor numérico de `status` SHALL derivar do enum `StatusAutorizacao` em vez de números mágicos espalhados no código. Criação MUST gravar o status correspondente a "ativa" conforme o enum, e cancelamento MUST gravar `CANCELADA`. Nenhum literal numérico de status pode contradizer o enum.

#### Scenario: Criação grava status coerente com o enum

- **WHEN** uma autorização é criada com sucesso
- **THEN** o `status` persistido corresponde à constante do enum `StatusAutorizacao` definida para autorização ativa, e não a um literal divergente

#### Scenario: Cancelamento grava status CANCELADA

- **WHEN** uma autorização é cancelada
- **THEN** o `status` persistido corresponde a `StatusAutorizacao.CANCELADA`

### Requirement: DTOs de request são imutáveis e não carregam estado interno

Os DTOs de entrada SHALL ser records imutáveis e MUST conter apenas dados recebidos do cliente. Valores derivados durante o processamento (ex.: o tipo de produto lido do banco, o id da autorização vindo do path, o produto vindo do header) MUST ser passados como parâmetros/contexto explícitos entre as camadas, e não mutados dentro do DTO.

#### Scenario: Request de cancelamento é imutável

- **WHEN** o DTO de request de cancelamento é instanciado
- **THEN** ele é um record sem setters e nenhum campo é reatribuído após a construção

#### Scenario: Validação de divergência de produto sem mutar o request

- **WHEN** a regra que compara o produto do header com o produto da autorização é executada
- **THEN** ambos os valores chegam como parâmetros/contexto explícitos da validação, não como campos previamente injetados no DTO de request

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

### Requirement: Contratos REST públicos preservados

O refactor SHALL preservar os contratos REST existentes: `POST /api/autorizacoes`, `PATCH /api/autorizacoes/{idAutorizacao}/cancelar` (com header `tipoProduto`) e o health-check. O corpo de request, os headers exigidos e os códigos HTTP MUST permanecer inalterados; a única mudança observável permitida é o valor de `status` na resposta passar a refletir o enum.

#### Scenario: Criação mantém contrato

- **WHEN** um cliente envia `POST /api/autorizacoes` com o mesmo corpo e header `tipoJornada` de antes
- **THEN** a resposta é 201 com o mesmo conjunto de campos, exceto `status` agora coerente com o enum

#### Scenario: Cancelamento mantém contrato

- **WHEN** um cliente envia `PATCH /api/autorizacoes/{id}/cancelar` com header `tipoProduto` e o mesmo corpo de antes
- **THEN** a resposta é 200 com a autorização cancelada

### Requirement: Domínio sem dead code e com anotações corretas

A entidade e seus value objects SHALL estar livres de inicialização que recebe a si própria como parâmetro, de anotações de persistência incorretas e de código morto. `inicializaCriacao` MUST operar sobre `this`; campos básicos de `@Embeddable` MUST usar `@Column` (não `@JoinColumn`); nomes de coluna MUST NOT conter espaços; classes não utilizadas (ex.: `ContratoBase`) MUST ser removidas ou efetivamente utilizadas.

#### Scenario: Inicialização opera sobre this

- **WHEN** uma autorização é inicializada para criação
- **THEN** o método de inicialização não recebe a própria autorização como parâmetro e opera sobre `this`

#### Scenario: Anotações de embeddable corretas

- **WHEN** os value objects embutidos (`IdAutorizacao`, `Cancelamento`) são inspecionados
- **THEN** seus campos básicos usam `@Column` e nenhum nome de coluna contém espaços em branco

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
