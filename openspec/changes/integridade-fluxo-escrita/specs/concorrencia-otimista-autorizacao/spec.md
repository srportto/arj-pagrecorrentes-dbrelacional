## ADDED Requirements

### Requirement: Lock otimista na entidade Autorizacao

A entidade `Autorizacao` do `arj-contratocommand` SHALL possuir um campo de versão gerenciado
pelo provedor JPA (`@Version`), persistido em coluna própria da tabela `autorizacoes`. Toda
escrita sobre uma autorização existente SHALL verificar que a versão lida permanece inalterada no
momento do commit.

#### Scenario: Escrita isolada incrementa a versão

- **WHEN** um cancelamento é aplicado a uma autorização sem nenhuma escrita concorrente
- **THEN** a operação SHALL ser concluída com sucesso
- **AND** o valor da coluna de versão SHALL ser incrementado

#### Scenario: Segunda escrita concorrente é rejeitada

- **WHEN** duas transações leem a mesma autorização com status `ATIVA` e ambas tentam cancelá-la
- **THEN** exatamente uma SHALL ser confirmada com sucesso
- **AND** a outra SHALL falhar com erro de concorrência, sem sobrescrever os dados de cancelamento
  já gravados

#### Scenario: Apenas um evento é publicado sob concorrência

- **WHEN** dois cancelamentos concorrentes disputam a mesma autorização
- **THEN** exatamente um evento `CANCELAMENTO` SHALL ser publicado no SNS

### Requirement: Conflito de concorrência devolve erro de contrato

Quando uma escrita é rejeitada por conflito de concorrência, a API SHALL responder com status
HTTP `409 Conflict` e corpo no formato `LayoutErrosApiResponse`, indicando que o recurso foi
modificado por outra operação. A API NÃO SHALL responder `500` nem expor a exceção do provedor
JPA.

#### Scenario: Resposta de conflito é estruturada

- **WHEN** um cancelamento falha por conflito de concorrência
- **THEN** a resposta SHALL ter status `409`
- **AND** o corpo SHALL seguir o formato `LayoutErrosApiResponse` usado pelos demais erros do
  serviço

#### Scenario: Detalhe interno não vaza

- **WHEN** a resposta de conflito é inspecionada
- **THEN** ela NÃO SHALL conter nome de classe de exceção, stack trace nem nome de coluna do banco

### Requirement: Leitura permanece compatível com a coluna de versão

A adição da coluna de versão à tabela compartilhada NÃO SHALL quebrar o `arj-contratoquery`, que
lê a mesma tabela. A entidade de leitura SHALL mapear ou ignorar explicitamente a coluna.

#### Scenario: Consulta funciona após a migration

- **WHEN** a coluna de versão existe na tabela e o `arj-contratoquery` executa `GET /api/autorizacoes`
  e `GET /api/autorizacoes/{id}`
- **THEN** as duas consultas SHALL retornar normalmente, sem erro de mapeamento
