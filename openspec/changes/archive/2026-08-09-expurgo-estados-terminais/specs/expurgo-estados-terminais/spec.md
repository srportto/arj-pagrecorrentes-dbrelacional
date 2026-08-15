## ADDED Requirements

### Requirement: Serviço compartilhado de transferência para partição de expurgo

O `contratocommand` SHALL expor um serviço compartilhado em `application/`
(`ExpurgoAutorizacaoService` ou nome equivalente), independente de qualquer feature
específica, responsável por transferir uma `Autorizacao` da sua partição atual para a
partição de expurgo calculada via `ControleExpurgoAutorizacao.obterParticaoExpurgoWrite`
a partir de uma data de referência fornecida pelo chamador. O serviço SHALL implementar o
mesmo algoritmo hoje usado por `CancelarAutorizacaoUseCase.transferirParaNovaParticao`
(delete → flush → detach → ajuste da partição no `@EmbeddedId` → save), preservando o
comportamento quando a partição de destino é igual à partição atual (apenas save, sem
delete+insert). Qualquer caso de uso que leve uma autorização a um estado terminal
(`CANCELADA`, `REJEITADA`, `EXPIRADA` ou `FINALIZADA`) SHALL usar este serviço em vez de
reimplementar a transferência de partição.

#### Scenario: Partição de destino diferente da atual
- **WHEN** o serviço é chamado com uma autorização cuja partição atual difere da partição
  calculada para a data de referência informada
- **THEN** a linha antiga é removida e uma nova linha é inserida na partição de destino,
  preservando todos os demais dados da autorização

#### Scenario: Partição de destino igual à atual
- **WHEN** o serviço é chamado e a partição calculada para a data de referência coincide
  com a partição atual da autorização
- **THEN** a autorização é apenas salva (sem delete+insert), sem erro

#### Scenario: Serviço reutilizável por qualquer caso de uso terminal
- **WHEN** um novo caso de uso (existente ou futuro) leva uma autorização a um dos quatro
  estados terminais
- **THEN** ele invoca o serviço compartilhado passando a data de referência apropriada, sem
  duplicar a lógica de delete+insert

### Requirement: Rejeição e expiração da jornada 1 também expurgam a autorização

`DecidirAutorizacaoUseCase` SHALL invocar o serviço de expurgo compartilhado quando a
decisão resultar em status `REJEITADA` (ações `REJEITAR` e `EXPIRAR`), usando
`dataHoraUltimaAtualizacao` (o instante da própria transição) como data de referência para
o cálculo da partição de destino — em vez de deixar a autorização na partição derivada do
`dataFimVigencia` padrão (`9999-12-31`). Quando a decisão resultar em `ATIVA` (ação
`APROVAR`), o comportamento de persistência permanece inalterado (save direto, sem
transferência de partição, pois `ATIVA` não é estado terminal).

#### Scenario: Rejeição pelo pagador move a autorização para a partição de expurgo
- **WHEN** um `PATCH /{id}/decisao` com `acao: REJEITAR` é processado com sucesso
- **THEN** a autorização passa a residir na partição de expurgo calculada a partir do
  instante da rejeição, e não mais na partição derivada de `dataFimVigencia`

#### Scenario: Expiração por timeout da jornada 1 move a autorização para a partição de expurgo
- **WHEN** um `PATCH /{id}/decisao` com `acao: EXPIRAR` é processado com sucesso
- **THEN** a autorização passa a residir na partição de expurgo calculada a partir do
  instante da expiração, e não mais na partição derivada de `dataFimVigencia`

#### Scenario: Aprovação não é afetada
- **WHEN** um `PATCH /{id}/decisao` com `acao: APROVAR` é processado com sucesso
- **THEN** a autorização é persistida normalmente, sem transferência de partição

### Requirement: Cancelamento preserva o comportamento observável após o refactor

`CancelarAutorizacaoUseCase` SHALL continuar transferindo a autorização cancelada para a
partição de expurgo calculada a partir de `dataHoraCancelamento`, agora delegando ao
serviço compartilhado em vez de conter a lógica localmente. O resultado observável
(partição final, evento publicado, dados da autorização) SHALL ser idêntico ao
comportamento anterior a esta mudança.

#### Scenario: Cancelamento continua expurgando corretamente
- **WHEN** um `PATCH /{id}/cancelar` é processado com sucesso
- **THEN** a autorização é transferida para a partição de expurgo calculada a partir de
  `dataHoraCancelamento`, com o mesmo resultado que antes desta mudança
