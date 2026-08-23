## MODIFIED Requirements

### Requirement: Estrutura do DTO de resposta de listagem
Cada item da listagem SHALL conter os campos resumidos de uma autorização: `idAutorizacao`, `tipoProduto`, `dataCriacao`, `dataInicioVigencia`, `dataFimVigencia`, `idPessoaRecebedora`, `nomeRecebedor`, `valor`, `status` (nome do enum, não o código inteiro), `motivoStatus` e `metadado`.

#### Scenario: Status é retornado como nome do enum
- **WHEN** a autorização tem `status = 4` (código do `ATIVA`)
- **THEN** o campo `status` no DTO retornado é a string `"ATIVA"`

#### Scenario: Campo nomeRecebedor está presente mas pode ser nulo
- **WHEN** a autorização é retornada na listagem
- **THEN** o campo `nomeRecebedor` está presente na resposta (podendo ser `null` até integração posterior)

#### Scenario: Campo tipoProduto identifica o produto do item da listagem
- **WHEN** a autorização listada é do produto `PIX_AUTO`
- **THEN** o campo `tipoProduto` no item da listagem é `"PIX_AUTO"`
- **AND** o mesmo vale para `DDA_AUTO`, sem exigir uma chamada adicional a `GET /api/autorizacoes/{autorizacaoId}` para descobrir o produto

#### Scenario: Campo motivoStatus está presente na listagem
- **WHEN** a autorização listada tem um `motivoStatus` registrado (ex.: `RECEPCAO_SPI_J1`)
- **THEN** o campo `motivoStatus` no item da listagem reflete o mesmo valor exposto em `GET /api/autorizacoes/{autorizacaoId}`
