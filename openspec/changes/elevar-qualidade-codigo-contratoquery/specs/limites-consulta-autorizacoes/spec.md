## MODIFIED Requirements

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

## ADDED Requirements

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
