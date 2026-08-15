# concorrencia-otimista-autorizacao Specification

## Purpose

Definir o controle de concorrência otimista sobre a entidade `Autorizacao` do `contratocommand` — campo `@Version`, contrato de erro `409` para conflito real entre chamadores, e compatibilidade da coluna de versão com o `contratoquery` e com os caminhos de escrita existentes.

## Requirements

### Requirement: Lock otimista na entidade Autorizacao

A entidade `Autorizacao` do `contratocommand` SHALL possuir um campo de versão gerenciado
pelo provedor JPA (`@Version`), persistido em coluna própria da tabela `autorizacoes`. Toda
escrita sobre uma autorização existente SHALL verificar que a versão lida permanece inalterada no
momento do commit.

A presença do campo de versão NÃO SHALL alterar o resultado de operações que não envolvem
concorrência. Em particular, uma escrita isolada que transfira a autorização entre partições
SHALL ser concluída com sucesso — o lock otimista existe para detectar escritas concorrentes
de terceiros, nunca para rejeitar uma transação por efeito das suas próprias instruções
anteriores.

Nota sobre a versão anterior deste requisito: ele admitia explicitamente que "é aceitável que
AMBAS as transações falhem com erro de concorrência", registrando como comportamento validado
o que era, na verdade, um defeito — o falso positivo de lock otimista produzido pelo `merge`
de instância detached no caminho `delete`+`flush`+`detach`+`save` do `ExpurgoAutorizacaoService`
na presença de `@Version`. Aquela transação falhava contra si mesma, com ou sem concorrência.
Corrigido o defeito (change `corrigir-expurgo-merge-version`), o resultado "ambas falham"
deixa de ser possível e deixa de ser aceito por esta especificação.

#### Scenario: Escrita isolada incrementa a versão

- **WHEN** um cancelamento é aplicado a uma autorização sem nenhuma escrita concorrente
- **THEN** a operação SHALL ser concluída com sucesso
- **AND** o valor da coluna de versão SHALL ser incrementado

#### Scenario: Escrita isolada com troca de partição é concluída com sucesso

- **WHEN** um cancelamento, uma rejeição ou uma expiração é aplicado, sem nenhuma escrita
  concorrente, a uma autorização cuja partição de expurgo de destino difere da partição atual
- **THEN** a operação SHALL ser concluída com sucesso
- **AND** a API SHALL responder `200`, nunca `409`
- **AND** o valor da coluna de versão SHALL ser incrementado

#### Scenario: Escrita concorrente nunca sobrescreve silenciosamente a outra

- **WHEN** duas transações leem a mesma autorização com status `ATIVA` e ambas tentam cancelá-la
- **THEN** as duas NÃO SHALL ser confirmadas com sucesso simultaneamente — nenhum cenário SHALL
  resultar em dados de cancelamento de uma transação sobrescrevendo os da outra sem erro
- **AND** exatamente uma das transações SHALL ser concluída com sucesso
- **AND** a outra SHALL falhar com erro de concorrência, recebendo `409`

#### Scenario: No máximo um evento é publicado sob concorrência

- **WHEN** dois cancelamentos concorrentes disputam a mesma autorização
- **THEN** exatamente um evento `CANCELAMENTO` SHALL ser publicado no SNS — nunca dois, e
  nunca zero

### Requirement: Conflito de concorrência devolve erro de contrato

Quando uma escrita é rejeitada por conflito de concorrência, a API SHALL responder com status
HTTP `409 Conflict` e corpo no formato `LayoutErrosApiResponse`, indicando que o recurso foi
modificado por outra operação. A API NÃO SHALL responder `500` nem expor a exceção do provedor
JPA.

A API SHALL reservar o `409` para conflito real entre chamadores distintos. Falha interna de
persistência que não decorra de escrita concorrente de terceiro NÃO SHALL ser reportada como
conflito, sob pena de induzir o chamador automatizado a repetir indefinidamente uma operação
que jamais poderá ter sucesso.

#### Scenario: Resposta de conflito é estruturada

- **WHEN** um cancelamento falha por conflito de concorrência
- **THEN** a resposta SHALL ter status `409`
- **AND** o corpo SHALL seguir o formato `LayoutErrosApiResponse` usado pelos demais erros do
  serviço

#### Scenario: Detalhe interno não vaza

- **WHEN** a resposta de conflito é inspecionada
- **THEN** ela NÃO SHALL conter nome de classe de exceção, stack trace nem nome de coluna do banco

#### Scenario: Falha determinística não se disfarça de conflito

- **WHEN** uma operação falha de forma determinística por defeito interno, repetindo o mesmo
  resultado em toda tentativa e sem qualquer chamador concorrente
- **THEN** a API NÃO SHALL responder `409`

### Requirement: Leitura permanece compatível com a coluna de versão

A adição da coluna de versão à tabela compartilhada NÃO SHALL quebrar o `contratoquery`, que
lê a mesma tabela. A entidade de leitura SHALL mapear ou ignorar explicitamente a coluna.

Além da leitura, a adição do campo de versão NÃO SHALL alterar o comportamento de nenhum
caminho de escrita já existente. Qualquer caminho cujo funcionamento dependa da ausência de
campo de versão SHALL ser identificado e corrigido junto com a introdução do `@Version`.

#### Scenario: Consulta funciona após a migration

- **WHEN** a coluna de versão existe na tabela e o `contratoquery` executa `GET /api/autorizacoes`
  e `GET /api/autorizacoes/{id}`
- **THEN** as duas consultas SHALL retornar normalmente, sem erro de mapeamento

#### Scenario: Nenhum caminho de escrita depende da ausência de versão

- **WHEN** o código de escrita do `contratocommand` é inspecionado
- **THEN** NÃO SHALL haver caminho que submeta ao provedor JPA uma instância detached cuja
  linha tenha sido removida na mesma transação, contando com a inferência de estado do
  provedor para produzir um `INSERT`
