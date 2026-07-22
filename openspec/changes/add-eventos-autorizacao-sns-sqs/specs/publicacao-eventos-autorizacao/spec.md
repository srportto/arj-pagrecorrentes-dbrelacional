# publicacao-eventos-autorizacao

## ADDED Requirements

### Requirement: Evento publicado após commit de cada persistência

O `arj-contratocommand` SHALL publicar um evento no tópico SNS
`sns-estados-autorizacao` a cada persistência confirmada na tabela `autorizacoes` —
criação (`CriarAutorizacaoUseCase`) e cancelamento (`CancelarAutorizacaoUseCase`). A
publicação SHALL ocorrer somente após o commit da transação, via evento de domínio
interno (`ApplicationEventPublisher`) consumido por um listener
`@TransactionalEventListener(phase = AFTER_COMMIT)`. A publicação SHALL usar o AWS SDK
v2 (`SnsClient`), sem Spring Cloud AWS.

#### Scenario: Criação publica evento
- **WHEN** um POST `/api/autorizacoes` é concluído com sucesso (201)
- **THEN** um evento com o estado final da linha persistida é publicado no tópico
  `sns-estados-autorizacao`

#### Scenario: Cancelamento publica evento
- **WHEN** um PATCH `/api/autorizacoes/{id}/cancelar` é concluído com sucesso (200)
- **THEN** um evento com o estado final da linha (status `CANCELADA`, dados de
  cancelamento preenchidos) é publicado no tópico

#### Scenario: Rollback não publica evento
- **WHEN** a transação de criação ou cancelamento sofre rollback (ex.:
  `BusinessException` de validação ou falha de banco)
- **THEN** nenhum evento é publicado no tópico

#### Scenario: Um evento lógico por operação
- **WHEN** um cancelamento transfere a autorização para outra partição de expurgo
  (delete + insert físicos na mesma transação)
- **THEN** exatamente um evento é publicado, contendo o estado final da linha na nova
  partição

### Requirement: Payload com a representação exata da tabela

O corpo do evento SHALL ser um JSON cujas chaves são os nomes das colunas da tabela
`autorizacoes` (ex.: `id_autorizacao`, `id_particao_conta`, `data_fim_vigencia`,
`tipo_produto`, `status`, `motivo_status`, `data_inicio_vigencia`,
`data_hora_inclusao`, `data_hora_ultima_atlz`, `valor`, `id_autorizacao_empresa`,
`valor_limite`, `frequencia`, `quantidade_dividas_ciclo`,
`indicador_uso_limite_conta`, `indicador_tipo_mensageria`,
`codigo_canal_contratacao`, `descricao`, `id_unico_conta_contratante`,
`id_pessoa_pagadora`, `id_pessoa_devedora`, `id_pessoa_recebedora`, colunas embutidas
de cancelamento e `metadados`), com os valores como persistidos. O mapeamento SHALL ser
feito por um record dedicado de payload (não pela serialização direta da entidade JPA).
O tipo da operação (`CRIACAO` ou `CANCELAMENTO`) SHALL ser informado como message
attribute SNS (`tipoEvento`), mantendo o body como representação pura da linha.

#### Scenario: Chaves espelham as colunas
- **WHEN** um evento de criação é publicado
- **THEN** o JSON contém as chaves com os nomes das colunas da tabela e os valores da
  linha persistida
- **AND** `metadados` aparece como objeto JSON (não como string escapada)

#### Scenario: Operação identificada por message attribute
- **WHEN** um evento é publicado
- **THEN** a mensagem SNS carrega o attribute `tipoEvento` com valor `CRIACAO` ou
  `CANCELAMENTO`

### Requirement: Falha de publicação não afeta a operação de negócio

Uma falha ao publicar no SNS (emulador fora do ar, erro de rede) NÃO SHALL alterar o
resultado da operação REST já confirmada: a resposta HTTP permanece de sucesso e a
falha SHALL ser registrada em log de erro com contexto suficiente para reprocessamento
manual (id da autorização e tipo do evento). A ausência de outbox pattern é um
trade-off aceito nesta fase.

#### Scenario: SNS indisponível após commit
- **WHEN** o commit ocorre com sucesso e o publish no SNS falha
- **THEN** o cliente REST recebe a resposta de sucesso normalmente
- **AND** um log de erro registra o id da autorização e o tipo do evento perdido
