## MODIFIED Requirements

### Requirement: Evento publicado após commit de cada persistência

O `contratocommand` SHALL publicar um evento no tópico SNS
`sns-estados-autorizacao` a cada persistência confirmada na tabela `autorizacoes` —
criação (`CriarAutorizacaoUseCase`), cancelamento (`CancelarAutorizacaoUseCase`),
decisão (`DecidirAutorizacaoUseCase`, ver capacidade `decisao-autorizacao`) e
atualização de dados da recorrência (`AtualizarDadosRecorrenciaUseCase`, ver capacidade
`atualizacao-dados-recorrencia`). A publicação SHALL ocorrer somente após o commit da
transação, via evento de domínio interno (`ApplicationEventPublisher`) consumido por um
listener `@TransactionalEventListener(phase = AFTER_COMMIT)`. A publicação SHALL usar o
AWS SDK v2 (`SnsClient`), sem Spring Cloud AWS.

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

#### Scenario: Atualização de dados da recorrência publica evento com o tipo do status vigente
- **WHEN** um PATCH `/api/autorizacoes/{id}/atualizar` é concluído com sucesso (200)
- **THEN** um evento com o estado final da linha (status `ATIVA`, campos atualizados
  refletidos) é publicado no tópico
- **AND** o attribute `tipoEvento` é `ATIVACAO` — derivado do status `ATIVA` inalterado,
  não um tipo de evento novo — porque a operação não transiciona estado

#### Scenario: Rollback não publica evento
- **WHEN** a transação de criação, cancelamento, decisão ou atualização de dados sofre
  rollback (ex.: `BusinessException` de validação ou falha de banco)
- **THEN** nenhum evento é publicado no tópico

#### Scenario: Um evento lógico por operação
- **WHEN** um cancelamento transfere a autorização para outra partição de expurgo
  (delete + insert físicos na mesma transação), ou uma aprovação percorre dois saltos da
  máquina de estados na mesma transação
- **THEN** exatamente um evento é publicado, contendo o estado final da linha
