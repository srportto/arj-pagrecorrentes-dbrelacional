## MODIFIED Requirements

### Requirement: Evento publicado após commit de cada persistência

O `arj-contratocommand` SHALL publicar um evento no tópico SNS
`sns-estados-autorizacao` a cada persistência confirmada na tabela `autorizacoes` —
criação (`CriarAutorizacaoUseCase`), cancelamento (`CancelarAutorizacaoUseCase`) e
decisão (`DecidirAutorizacaoUseCase`, ver capacidade `decisao-autorizacao`). A
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

#### Scenario: Decisão publica evento
- **WHEN** um PATCH `/api/autorizacoes/{id}/decisao` é concluído com sucesso (200)
- **THEN** um evento com o estado final da linha (`ATIVA` para aprovação, `REJEITADA`
  para rejeição e para expiração) é publicado no tópico

#### Scenario: Rollback não publica evento
- **WHEN** a transação de criação, cancelamento ou decisão sofre rollback (ex.:
  `BusinessException` de validação ou falha de banco)
- **THEN** nenhum evento é publicado no tópico

#### Scenario: Um evento lógico por operação
- **WHEN** um cancelamento transfere a autorização para outra partição de expurgo
  (delete + insert físicos na mesma transação), ou uma aprovação percorre dois saltos da
  máquina de estados na mesma transação
- **THEN** exatamente um evento é publicado, contendo o estado final da linha

### Requirement: Payload com a representação exata da tabela

O corpo do evento SHALL ser um JSON cujas chaves são os nomes das colunas da tabela
`autorizacoes` (ex.: `id_autorizacao`, `id_particao_conta`, `data_fim_vigencia`,
`tipo_produto`, `tipo_jornada`, `status`, `motivo_status`, `data_inicio_vigencia`,
`data_hora_inclusao`, `data_hora_ultima_atlz`, `valor`, `id_autorizacao_empresa`,
`valor_limite`, `frequencia`, `quantidade_dividas_ciclo`,
`indicador_uso_limite_conta`, `indicador_tipo_mensageria`,
`codigo_canal_contratacao`, `descricao`, `id_unico_conta_contratante`,
`id_pessoa_pagadora`, `id_pessoa_devedora`, `id_pessoa_recebedora`, colunas embutidas
de cancelamento e `metadados`), com os valores como persistidos. O mapeamento SHALL ser
feito por um record dedicado de payload (não pela serialização direta da entidade JPA).
O tipo do evento SHALL ser **derivado do `status` persistido da autorização** via
`TipoEventoAutorizacao.porStatus(status)` — não informado pelo use case — e publicado
como message attribute SNS (`tipoEvento`), mantendo o body como representação pura da
linha. O evento interno de persistência (`AutorizacaoPersistidaEvent`) NÃO SHALL
carregar campo de tipo de evento. O `tipoEvento` publicado na criação NÃO SHALL ser
sempre `ATIVACAO` — depende do status com que a autorização nasce, que por sua vez
depende do produto (ver capacidade `status-inicial-por-produto`).

Além de `tipoEvento`, a mensagem SHALL carregar os message attributes **`tipoProduto`** e
**`tipoJornada`**, com o nome do enum correspondente ao valor persistido nas colunas
`tipo_produto` e `tipo_jornada` da própria linha. Nenhum message attribute SHALL expressar
informação ausente do body — todo attribute é um espelho de coluna, existente para permitir
filtragem por filter policy sem inspeção do corpo.

#### Scenario: Chaves espelham as colunas
- **WHEN** um evento de criação é publicado
- **THEN** o JSON contém as chaves com os nomes das colunas da tabela e os valores da
  linha persistida
- **AND** `metadados` aparece como objeto JSON (não como string escapada)
- **AND** `tipo_jornada` reflete a jornada persistida na linha

#### Scenario: Criação de DDA_AUTO publica tipo derivado do status ATIVA
- **WHEN** um POST `/api/autorizacoes` com `tipoProduto: DDA_AUTO` é concluído com
  sucesso (linha persistida com status `ATIVA`)
- **THEN** a mensagem SNS carrega o attribute `tipoEvento` com valor `ATIVACAO`

#### Scenario: Criação de PIX_AUTO publica tipo derivado do status RECEBIDA
- **WHEN** um POST `/api/autorizacoes` com `tipoProduto: PIX_AUTO` é concluído com
  sucesso (linha persistida com status `RECEBIDA`)
- **THEN** a mensagem SNS carrega o attribute `tipoEvento` com valor `RECEPCAO`

#### Scenario: Cancelamento publica tipo derivado do status CANCELADA
- **WHEN** um PATCH `/api/autorizacoes/{id}/cancelar` é concluído com sucesso (linha
  persistida com status `CANCELADA`)
- **THEN** a mensagem SNS carrega o attribute `tipoEvento` com valor `CANCELAMENTO`

#### Scenario: Attribute sempre coerente com o body
- **WHEN** qualquer evento é publicado
- **THEN** o valor do attribute `tipoEvento` é igual a
  `TipoEventoAutorizacao.porStatus(status)` para o campo `status` presente no body
- **AND** o valor do attribute `tipoProduto` corresponde ao campo `tipo_produto` do body
- **AND** o valor do attribute `tipoJornada` corresponde ao campo `tipo_jornada` do body

#### Scenario: Attributes de produto e jornada presentes em todo evento
- **WHEN** um evento de criação, cancelamento ou decisão é publicado
- **THEN** a mensagem carrega os três attributes `tipoEvento`, `tipoProduto` e
  `tipoJornada`

#### Scenario: Attributes novos não afetam consumidores existentes
- **WHEN** os attributes `tipoProduto` e `tipoJornada` passam a ser publicados
- **THEN** a subscription `SQS-eventos-autorizacao` continua recebendo todos os eventos,
  sem filter policy e sem mudança de comportamento
