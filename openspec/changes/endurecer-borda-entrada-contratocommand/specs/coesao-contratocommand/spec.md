## MODIFIED Requirements

### Requirement: Erros inesperados usam exceções do próprio projeto

Falhas inesperadas de sistema SHALL ser sinalizadas com `ApplicationException` (mapeada para HTTP 500) e violações de regra de negócio com `BusinessException` (HTTP 422). O código MUST NOT lançar exceções de framework (ex.: `org.springframework.context.ApplicationContextException`) para representar erros de aplicação.

O reembalo em `ApplicationException` SHALL alcançar apenas falhas genuinamente inesperadas. Exceções
que possuem tratamento de contrato próprio no `ApiExceptionHandler` MUST NOT ser capturadas e
reembaladas pela camada de aplicação — em particular `ConcurrencyFailureException` e suas subclasses
(incluindo `CannotAcquireLockException` e `ObjectOptimisticLockingFailureException`), que mantêm o
contrato **409** definido pela capability `concorrencia-otimista-autorizacao`. Um `catch` amplo que
converta conflito de concorrência em 500 anula, a partir da camada de aplicação, uma decisão de
contrato tomada no handler.

#### Scenario: Falha inesperada ao buscar autorização

- **WHEN** ocorre um erro inesperado ao obter a autorização por id/partição no cancelamento
- **THEN** é lançada `ApplicationException` e a resposta HTTP é 500

#### Scenario: Autorização não encontrada

- **WHEN** a autorização informada no cancelamento não existe
- **THEN** é lançada `BusinessException` e a resposta HTTP é 422

#### Scenario: Conflito de concorrência ao carregar a autorização preserva o 409

- **WHEN** o carregamento da autorização em um use case de escrita (cancelamento, decisão ou
  atualização) falha com `ConcurrencyFailureException`
- **THEN** a exceção SHALL propagar até o `ApiExceptionHandler` sem ser reembalada em
  `ApplicationException`
- **AND** a resposta HTTP SHALL ser 409, não 500

#### Scenario: Camada de aplicação não captura exceção com contrato próprio

- **WHEN** os use cases de escrita do `contratocommand` são inspecionados
- **THEN** nenhum bloco `catch` SHALL converter `ConcurrencyFailureException` ou suas subclasses em
  `ApplicationException`

## ADDED Requirements

### Requirement: Fonte única de carregamento da autorização nos use cases de escrita

Os use cases de escrita SHALL compartilhar um único ponto de verdade para carregar a autorização por
identificador, tratar o caso "não encontrada" e enriquecer o comando com os dados lidos do banco
(produto persistido e status atual). Esse ponto único SHALL ser compartilhado por
`CancelarAutorizacaoService`, `DecidirAutorizacaoService` e `AtualizarDadosRecorrenciaService`.

Nenhum desses use cases SHALL manter cópia própria da lógica de carregamento. Uma mudança na
política de carregamento ou na mensagem de "autorização não encontrada" MUST exigir edição em um
único lugar.

#### Scenario: Lógica de carregamento não é duplicada

- **WHEN** os três use cases de escrita são inspecionados
- **THEN** nenhum deles SHALL declarar método próprio de carregamento da autorização por
  identificador
- **AND** todos SHALL delegar ao mesmo componente compartilhado

#### Scenario: Mensagem de não encontrada é preservada

- **WHEN** uma operação de cancelamento, decisão ou atualização referencia autorização inexistente
- **THEN** a resposta SHALL ser 422 com a mesma mensagem de negócio devolvida antes da mudança

#### Scenario: Comportamento das três rotas preservado

- **WHEN** a suíte de testes é executada após a extração
- **THEN** todos os testes das três rotas de escrita SHALL passar sem alteração de expectativa de
  status ou de corpo de resposta, exceto os casos corrigidos por esta change
