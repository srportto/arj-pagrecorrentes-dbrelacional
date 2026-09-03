# limites-consulta-autorizacoes Specification

## Purpose

Descreve os limites que blindam a superfície de leitura do `contratoquery` contra consulta
capaz de degradar o banco: teto máximo de tamanho de página, validação de índice e tamanho, e
whitelist fechada de campos de ordenação. O alvo é a tabela `autorizacoes`, particionada em 989
partições, onde uma consulta sem limite não é lenta apenas para quem a fez.

## Requirements
### Requirement: Teto máximo de tamanho de página

O `contratoquery` SHALL impor limite máximo ao parâmetro `tamanho` da listagem. Requisição com
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

A **direção** informada em `ordenarPor` SHALL ser validada contra uma lista fechada igualmente
fechada — apenas `asc` e `desc`, em qualquer caixa. Direção fora dessa lista SHALL ser rejeitada
com erro de negócio, e a mensagem SHALL informar o valor recebido e as direções aceitas. O
sistema NÃO SHALL assumir uma direção padrão quando a direção informada não é reconhecida:
tratar valor desconhecido como `desc` produz ordem inversa à pedida sob HTTP 200, sem qualquer
sinal de erro para o cliente.

Quando a direção não é informada (apenas o campo, sem vírgula), a ordenação SHALL usar `desc`
como padrão — a ausência de direção é omissão válida, não valor inválido.

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

#### Scenario: Direção de ordenação desconhecida é rejeitada

- **WHEN** o cliente envia `ordenarPor=valor,ascc` ou `ordenarPor=valor,ASCENDING`
- **THEN** a resposta SHALL ser erro de contrato no formato `LayoutErrosApiResponse` com HTTP 422
- **AND** a mensagem SHALL informar o valor de direção recebido e as direções aceitas
- **AND** nenhuma consulta SHALL ser executada no banco

#### Scenario: Direção desconhecida não vira descendente silencioso

- **WHEN** o cliente envia uma direção não reconhecida
- **THEN** a resposta NÃO SHALL ser HTTP 200
- **AND** a listagem NÃO SHALL ser retornada em ordem descendente como se a direção tivesse sido
  aceita

#### Scenario: Direções válidas são aceitas em qualquer caixa

- **WHEN** o cliente envia `ordenarPor=valor,ASC`, `ordenarPor=valor,Desc` ou
  `ordenarPor=valor, asc` (com espaço)
- **THEN** a listagem SHALL ser ordenada na direção correspondente sem erro

#### Scenario: Direção omitida usa descendente como padrão

- **WHEN** o cliente envia `ordenarPor=valor`, sem vírgula e sem direção
- **THEN** a listagem SHALL ser ordenada pelo campo `valor` em ordem descendente
- **AND** a resposta SHALL ser HTTP 200

### Requirement: Expressão de ordenação malformada é rejeitada

A expressão recebida em `ordenarPor` SHALL ser interpretada como `campo` ou `campo,direcao` e
NÃO SHALL admitir outras formas. Expressão com campo vazio, direção vazia ou mais de duas partes
SHALL ser rejeitada com erro de contrato, sem cair em campo ou direção padrão.

O parse SHALL viver em um único ponto do domínio, de modo que nenhuma camada precise repetir a
quebra da string. Quando `ordenarPor` é omitido ou vem em branco, a ordenação padrão da
capacidade `listar-autorizacoes` SHALL ser aplicada — omissão total continua sendo caminho
válido, distinto de expressão malformada.

#### Scenario: Direção vazia após a vírgula é rejeitada

- **WHEN** o cliente envia `ordenarPor=valor,`
- **THEN** a resposta SHALL ser erro de contrato no formato `LayoutErrosApiResponse` com HTTP 422
- **AND** a listagem NÃO SHALL ser executada com a direção padrão

#### Scenario: Campo vazio antes da vírgula é rejeitado

- **WHEN** o cliente envia `ordenarPor=,asc`
- **THEN** a resposta SHALL ser erro de contrato no formato `LayoutErrosApiResponse` com HTTP 422
- **AND** a mensagem SHALL indicar que o campo de ordenação é inválido, listando os aceitos

#### Scenario: Expressão com mais de duas partes é rejeitada

- **WHEN** o cliente envia `ordenarPor=valor,asc,extra`
- **THEN** a resposta SHALL ser erro de contrato no formato `LayoutErrosApiResponse` com HTTP 422
- **AND** o excedente NÃO SHALL ser ignorado silenciosamente

#### Scenario: Ordenação omitida aplica o padrão da listagem

- **WHEN** o cliente não envia `ordenarPor`, ou envia o parâmetro em branco
- **THEN** a listagem SHALL ser ordenada por `dataHoraInclusao` em ordem descendente
- **AND** a resposta SHALL ser HTTP 200

#### Scenario: Parse da expressão existe em um único ponto

- **WHEN** o código de produção do `contratoquery` é inspecionado
- **THEN** a quebra da string `ordenarPor` SHALL ocorrer em exatamente um lugar
- **AND** o caso de uso de listagem NÃO SHALL conter chamada a `split` sobre esse parâmetro

