## ADDED Requirements

### Requirement: Teto máximo de tamanho de página

O `arj-contratoquery` SHALL impor limite máximo ao parâmetro `tamanho` da listagem. Requisição com
`tamanho` acima do teto SHALL ser rejeitada com erro de contrato antes de qualquer consulta ao
banco, e a mensagem SHALL informar o valor máximo aceito. O sistema NÃO SHALL truncar
silenciosamente o valor solicitado.

#### Scenario: Tamanho acima do teto é rejeitado

- **WHEN** o cliente envia `GET /api/autorizacoes?idUnicoContaContratante={uuid}&tamanho=999999`
- **THEN** a resposta SHALL ser erro de contrato no formato `LayoutErrosApiResponse`
- **AND** a mensagem SHALL informar o tamanho máximo permitido
- **AND** nenhuma consulta SHALL ser executada no banco

#### Scenario: Tamanho dentro do teto é aceito

- **WHEN** o cliente envia `tamanho` menor ou igual ao teto configurado
- **THEN** a listagem SHALL ser executada normalmente

#### Scenario: Truncamento silencioso não ocorre

- **WHEN** o cliente solicita tamanho acima do teto
- **THEN** o sistema NÃO SHALL retornar HTTP 200 com uma página menor que a solicitada sem
  informar a rejeição

### Requirement: Validação de índice e tamanho de página

Valores inválidos de paginação SHALL ser rejeitados com erro de contrato. `pagina` negativa e
`tamanho` menor ou igual a zero NÃO SHALL alcançar a construção do `PageRequest`, de modo que
nenhuma `IllegalArgumentException` do Spring Data escape como erro não tratado.

#### Scenario: Página negativa é rejeitada

- **WHEN** o cliente envia `pagina=-1`
- **THEN** a resposta SHALL ser erro de contrato no formato `LayoutErrosApiResponse`
- **AND** a resposta NÃO SHALL ser HTTP 500

#### Scenario: Tamanho zero ou negativo é rejeitado

- **WHEN** o cliente envia `tamanho=0` ou `tamanho=-5`
- **THEN** a resposta SHALL ser erro de contrato no formato `LayoutErrosApiResponse`

### Requirement: Whitelist fechada de campos de ordenação

O campo informado em `ordenarPor` SHALL ser validado contra uma lista fechada de campos
ordenáveis conhecidos. Campo fora da lista SHALL ser rejeitado com erro de negócio antes de
alcançar a construção do `Sort`, e a mensagem SHALL listar os campos aceitos. Nenhuma string
recebida do cliente SHALL ser repassada diretamente ao mecanismo de ordenação.

#### Scenario: Campo de ordenação desconhecido é rejeitado

- **WHEN** o cliente envia `ordenarPor=campoInexistente,asc`
- **THEN** a resposta SHALL ser erro de contrato no formato `LayoutErrosApiResponse`
- **AND** a mensagem SHALL listar os campos de ordenação aceitos
- **AND** nenhuma `PropertyReferenceException` SHALL ser lançada

#### Scenario: Campo de ordenação conhecido é aceito

- **WHEN** o cliente envia `ordenarPor=valor,asc`
- **THEN** a listagem SHALL ser ordenada pelo campo correspondente

#### Scenario: Nenhum repasse direto ao Sort

- **WHEN** o mapeamento de campo de ordenação é inspecionado
- **THEN** ele NÃO SHALL conter caminho que repasse a string recebida do cliente sem constar da
  lista fechada
