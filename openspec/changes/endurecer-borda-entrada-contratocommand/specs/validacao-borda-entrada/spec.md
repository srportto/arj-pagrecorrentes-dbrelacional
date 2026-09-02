## ADDED Requirements

### Requirement: Campo numérico não sofre perda silenciosa de precisão

Todo campo numérico do contrato REST SHALL declarar limite compatível com a faixa do seu tipo de
destino no modelo de domínio, sempre que esse destino for de largura menor que a do tipo recebido.
Valor fora da faixa SHALL ser rejeitado na borda com **422**, em vez de sofrer *narrowing cast*
silencioso.

Especificamente: `quantidadeDividasCiclo` e `indicadorUsoLimiteConta` são recebidos como `Integer` e
persistidos a partir de campos `short` do modelo `Autorizacao`. Ambos SHALL declarar `@Min` e `@Max`
em `CriarAutorizacaoRequest` e em `AtualizarDadosRecorrenciaRequest`:

- `indicadorUsoLimiteConta`: `@Min(0) @Max(1)` — o campo é uma flag booleana.
- `quantidadeDividasCiclo`: `@Min(1) @Max(32767)` — o teto é o limite físico do `short`,
  deliberadamente **não** uma regra de negócio; o objetivo é impedir truncamento, não introduzir
  limite de negócio não definido.

Nenhum valor aceito pela borda SHALL produzir, após a conversão para `short`, um valor diferente do
recebido.

#### Scenario: Valor acima da faixa do short é rejeitado na criação

- **WHEN** um `POST /api/autorizacoes` é enviado com `quantidadeDividasCiclo` igual a 32768
- **THEN** a resposta SHALL ser 422 no formato `LayoutErrosApiValidationsResponse`
- **AND** a autorização NÃO SHALL ser persistida

#### Scenario: Valor acima da faixa do short é rejeitado na atualização

- **WHEN** um `PATCH /api/autorizacoes/{id}/atualizar` é enviado com `quantidadeDividasCiclo` igual
  a 32768 sobre uma autorização `ATIVA`
- **THEN** a resposta SHALL ser 422 no formato `LayoutErrosApiValidationsResponse`
- **AND** o valor persistido NÃO SHALL ser alterado

#### Scenario: Flag fora do domínio booleano é rejeitada

- **WHEN** uma requisição de criação ou de atualização é enviada com `indicadorUsoLimiteConta` igual
  a 2
- **THEN** a resposta SHALL ser 422 no formato `LayoutErrosApiValidationsResponse`

#### Scenario: Valor dentro da faixa é preservado sem truncamento

- **WHEN** uma autorização é criada com `quantidadeDividasCiclo` igual a 32767
- **THEN** o valor persistido SHALL ser exatamente 32767

#### Scenario: Nenhum campo numérico convertido para short fica sem teto

- **WHEN** os records de request do `contratocommand` são inspecionados
- **THEN** todo campo cujo destino no modelo `Autorizacao` seja `short` SHALL declarar `@Max`

### Requirement: Identificador de autorização é validado na borda

O identificador de autorização recebido no path SHALL ser validado quanto ao formato UUID **antes**
de alcançar a camada de aplicação, e SHALL viajar tipado — não como `String` — entre o controller e
os use cases. Identificador malformado SHALL ser tratado como entrada inválida do cliente,
respondendo **422** no formato `LayoutErrosApiResponse`, em conformidade com a convenção já
estabelecida pela capability `contrato-api-consistente`.

A camada de aplicação MUST NOT executar conversão de formato do identificador (`UUID.fromString`) —
ao recebê-lo, o valor já SHALL estar validado.

#### Scenario: Identificador malformado no cancelamento retorna 422

- **WHEN** um `PATCH /api/autorizacoes/nao-e-uuid/cancelar` é enviado com header `tipoProduto` válido
- **THEN** a resposta SHALL ser 422 no formato `LayoutErrosApiResponse`
- **AND** NÃO SHALL ser 500

#### Scenario: Identificador malformado na decisão retorna 422

- **WHEN** um `PATCH /api/autorizacoes/nao-e-uuid/decisao` é enviado
- **THEN** a resposta SHALL ser 422 no formato `LayoutErrosApiResponse`

#### Scenario: Identificador malformado na atualização retorna 422

- **WHEN** um `PATCH /api/autorizacoes/nao-e-uuid/atualizar` é enviado
- **THEN** a resposta SHALL ser 422 no formato `LayoutErrosApiResponse`

#### Scenario: Entrada malformada não é registrada como erro do servidor

- **WHEN** uma requisição com identificador malformado é processada
- **THEN** o log do servidor NÃO SHALL registrar a ocorrência em nível `ERROR` como exceção não
  mapeada
- **AND** a requisição NÃO SHALL contribuir para a taxa de erro 5xx do serviço

#### Scenario: Aplicação recebe identificador já validado

- **WHEN** os use cases de escrita (`CancelarAutorizacaoService`, `DecidirAutorizacaoService`,
  `AtualizarDadosRecorrenciaService`) são inspecionados
- **THEN** nenhum deles SHALL conter chamada a `UUID.fromString`
- **AND** o identificador SHALL chegar aos comandos como tipo dedicado, não como `String`

#### Scenario: Identificador bem formado mantém o comportamento atual

- **WHEN** um `PATCH /api/autorizacoes/{id}/cancelar` é enviado com UUID válido de autorização
  existente e cancelável
- **THEN** a resposta SHALL ser 200, idêntica ao comportamento anterior à mudança

#### Scenario: Identificador válido de autorização inexistente permanece 422 de negócio

- **WHEN** um `PATCH /api/autorizacoes/{id}/cancelar` é enviado com UUID bem formado que não
  corresponde a nenhuma autorização
- **THEN** a resposta SHALL ser 422 por `BusinessException`, preservando a mensagem de autorização
  não encontrada
