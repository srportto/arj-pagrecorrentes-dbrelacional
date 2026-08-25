## MODIFIED Requirements

### Requirement: Estrutura do DTO de detalhe da autorização
O `AutorizacaoDetalheResponseDto` SHALL conter a representação completa da autorização, incluindo
no mínimo: `idAutorizacao`, `tipoProduto`, `status` (nome do enum, não o código inteiro),
`dataInicioVigencia`, `dataFimVigencia`, `dataCriacao`, `valor`, `valorLimite`,
`idUnicoContaContratante`, `idPessoaRecebedora`, `metadado`, `frequenciaPagamento`,
`quantidadeDividasCiclo`, `indicadorUsoLimiteConta`, `indicadorTipoMensageria`,
`codigoCanalContratacao` e `cancelamento`. O campo `cancelamento` SHALL ser `null` quando a
autorização nunca foi cancelada, e SHALL conter `codigoCanalCancelamento`,
`idPessoaCancelamento`, `dataHoraCancelamento` e `motivoCancelamento` quando presente.

#### Scenario: Status é retornado como nome do enum
- **WHEN** a autorização consultada tem `status = 1`
- **THEN** o campo `status` no DTO retornado é a string correspondente ao nome do enum `StatusAutorizacao` (ex.: `"ATIVA"`)

#### Scenario: Metadado JSONB é retornado como objeto JSON
- **WHEN** a autorização possui `metadados` armazenados como JSONB
- **THEN** o campo `metadado` é retornado como objeto JSON estruturado (não como string escapada)

#### Scenario: Campos de configuração da recorrência são retornados
- **WHEN** o cliente consulta uma autorização existente por id
- **THEN** a resposta contém `frequenciaPagamento`, `quantidadeDividasCiclo`,
  `indicadorUsoLimiteConta`, `indicadorTipoMensageria` e `codigoCanalContratacao` com os valores
  persistidos na linha

#### Scenario: Autorização nunca cancelada não tem dados de cancelamento
- **WHEN** o cliente consulta uma autorização que nunca foi cancelada
- **THEN** o campo `cancelamento` da resposta é `null`

#### Scenario: Autorização cancelada retorna os dados do cancelamento
- **WHEN** o cliente consulta uma autorização com status `CANCELADA`
- **THEN** o campo `cancelamento` da resposta contém `codigoCanalCancelamento`,
  `idPessoaCancelamento`, `dataHoraCancelamento` e `motivoCancelamento` com os valores
  persistidos
