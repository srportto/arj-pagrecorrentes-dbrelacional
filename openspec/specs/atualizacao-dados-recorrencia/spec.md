# atualizacao-dados-recorrencia

## Purpose

Descreve a rota `PATCH /api/autorizacoes/{idAutorizacao}/atualizar` do `contratocommand`,
que permite atualização parcial dos campos `valorLimite`, `dataFimVigencia`,
`indicadorUsoLimiteConta` e `quantidadeDividasCiclo` de uma autorização `ATIVA`.

## Requirements

### Requirement: Atualização parcial de dados de uma autorização ATIVA

O `contratocommand` SHALL expor `PATCH /api/autorizacoes/{idAutorizacao}/atualizar`
para atualizar, de forma parcial, os campos `valorLimite`, `dataFimVigencia`,
`indicadorUsoLimiteConta` e `quantidadeDividasCiclo` de uma autorização com status
`ATIVA`. A operação SHALL exigir o header `tipoProduto` e SHALL rejeitar a requisição
com 422 quando o produto do header divergir do produto persistido. Campos ausentes ou
enviados como `null` no corpo SHALL permanecer inalterados — a operação NÃO SHALL exigir
que o cliente reenvie os 4 campos a cada chamada.

#### Scenario: Atualização de um campo isolado é aceita
- **WHEN** um PATCH `/api/autorizacoes/{id}/atualizar` é enviado só com `valorLimite`
  preenchido, para uma autorização `ATIVA`, com o `tipoProduto` do header igual ao
  persistido
- **THEN** somente `valorLimite` é alterado na linha
- **AND** os demais campos (`dataFimVigencia`, `indicadorUsoLimiteConta`,
  `quantidadeDividasCiclo`) permanecem com o valor anterior
- **AND** a resposta é 200 com o estado completo da autorização

#### Scenario: Atualização em autorização fora de ATIVA é rejeitada
- **WHEN** um PATCH `/api/autorizacoes/{id}/atualizar` é enviado para uma autorização com
  status `RECEBIDA`, `PENDENTE_ACEITE`, `EM_PROCESSO_ATIVACAO`, `CANCELADA`, `REJEITADA`,
  `EXPIRADA` ou `FINALIZADA`
- **THEN** a requisição é rejeitada com 422 (`BusinessException`), sem alterar a linha e
  sem publicar evento

#### Scenario: Produto do header divergente é rejeitado
- **WHEN** o `tipoProduto` do header diverge do produto persistido na autorização
- **THEN** a requisição é rejeitada com 422, sem alterar a linha

#### Scenario: Autorização inexistente não retorna 404
- **WHEN** o `idAutorizacao` do path não corresponde a nenhuma autorização persistida
- **THEN** a requisição é rejeitada com 422 (`BusinessException`) — não existe 404 nas
  rotas de mutação deste serviço, mesma convenção de `cancelar`/`decisao`

#### Scenario: Concorrência entre duas atualizações da mesma autorização
- **WHEN** duas requisições de atualização concorrentes alteram a mesma autorização
- **THEN** a segunda a commitar recebe 409 (`ObjectOptimisticLockingFailureException`),
  mesmo mecanismo de lock otimista de `cancelar`/`decisao`

### Requirement: Validação de negócio dos campos atualizáveis

`dataFimVigencia`, quando informada, SHALL seguir a mesma regra aplicada na criação: não
pode ser anterior à data corrente. `valorLimite`, quando informado, SHALL ser maior que
zero. `quantidadeDividasCiclo`, quando informado, SHALL ser maior ou igual a 1.
`indicadorUsoLimiteConta`, quando informado, NÃO SHALL ter validação de faixa além de ser
um inteiro — mesmo comportamento permissivo da criação.

#### Scenario: dataFimVigencia no passado é rejeitada
- **WHEN** o corpo informa `dataFimVigencia` anterior à data corrente
- **THEN** a requisição é rejeitada com 422, sem alterar a linha

#### Scenario: valorLimite zero ou negativo é rejeitado
- **WHEN** o corpo informa `valorLimite` igual a zero ou negativo
- **THEN** a requisição é rejeitada com 422, sem alterar a linha

#### Scenario: quantidadeDividasCiclo menor que 1 é rejeitada
- **WHEN** o corpo informa `quantidadeDividasCiclo` igual a zero ou negativo
- **THEN** a requisição é rejeitada com 422 (validação de formato, `LayoutErrosApiValidationsResponse`)

### Requirement: Auditoria de canal e responsável pela atualização

O corpo da requisição SHALL exigir `codigoCanalAtualizacao` e `idPessoaAtualizacao`,
mesmo padrão de auditoria já aplicado a `Cancelamento` e `DecidirAutorizacaoCommand`.

#### Scenario: Requisição sem canal ou pessoa é rejeitada
- **WHEN** o corpo não informa `codigoCanalAtualizacao` ou `idPessoaAtualizacao`
- **THEN** a requisição é rejeitada com 422 (validação de formato)
