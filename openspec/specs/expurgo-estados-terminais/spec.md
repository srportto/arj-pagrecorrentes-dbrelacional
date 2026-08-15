# expurgo-estados-terminais

## Purpose

Descreve o serviço compartilhado de transferência de partição usado pelo `contratocommand`
para mover autorizações em estados terminais (`CANCELADA`, `REJEITADA`, `EXPIRADA` ou
`FINALIZADA`) para a partição de expurgo, e como os casos de uso de decisão (rejeição,
expiração) e cancelamento o utilizam.

## Requirements

### Requirement: Serviço compartilhado de transferência para partição de expurgo

O `contratocommand` SHALL expor um serviço compartilhado em `application/`
(`ExpurgoAutorizacaoService` ou nome equivalente), independente de qualquer feature
específica, responsável por transferir uma `Autorizacao` da sua partição atual para a
partição de expurgo calculada via `ControleExpurgoAutorizacao.obterParticaoExpurgoWrite`
a partir de uma data de referência fornecida pelo chamador. Qualquer caso de uso que leve
uma autorização a um estado terminal (`CANCELADA`, `REJEITADA`, `EXPIRADA` ou `FINALIZADA`)
SHALL usar este serviço em vez de reimplementar a transferência de partição.

A especificação NÃO prescreve o algoritmo de transferência. O requisito é o resultado
observável: transferência bem-sucedida, dados preservados e a garantia de lock otimista de
`concorrencia-otimista-autorizacao` intacta. A implementação SHALL NOT depender de o
provedor JPA inferir corretamente o estado (transiente ou detached) de uma instância cuja
linha foi removida na mesma transação — inferência que varia conforme a entidade possua ou
não campo de versão e que já produziu falha determinística no passado.

Quando a partição de destino coincide com a partição atual, o serviço SHALL apenas persistir
a autorização, sem qualquer movimentação.

#### Scenario: Partição de destino diferente da atual

- **WHEN** o serviço é chamado com uma autorização cuja partição atual difere da partição
  calculada para a data de referência informada
- **THEN** a operação SHALL ser concluída com sucesso
- **AND** a autorização SHALL passar a existir na partição de destino, com o mesmo
  `id_autorizacao` e todos os demais dados preservados
- **AND** NÃO SHALL restar linha alguma com esse `id_autorizacao` na partição de origem

#### Scenario: Partição de destino igual à atual

- **WHEN** o serviço é chamado e a partição calculada para a data de referência coincide
  com a partição atual da autorização
- **THEN** a autorização é apenas salva, sem movimentação, e sem erro

#### Scenario: Transferência isolada não é confundida com conflito de concorrência

- **WHEN** uma única transação, sem qualquer escrita concorrente sobre a mesma autorização,
  executa a transferência para uma partição diferente
- **THEN** a operação SHALL ser concluída com sucesso
- **AND** a API NÃO SHALL responder `409`

#### Scenario: Serviço reutilizável por qualquer caso de uso terminal

- **WHEN** um novo caso de uso (existente ou futuro) leva uma autorização a um dos quatro
  estados terminais
- **THEN** ele invoca o serviço compartilhado passando a data de referência apropriada, sem
  duplicar a lógica de movimentação

#### Scenario: Movimentação parcial nunca passa despercebida

- **WHEN** a operação de movimentação não afeta exatamente uma linha
- **THEN** a transação SHALL ser revertida com erro explícito
- **AND** o serviço NÃO SHALL devolver ao chamador uma autorização declarada como
  transferida enquanto a linha permanece na partição de origem

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

### Requirement: Transferência de partição verificada contra banco real

A transferência entre partições SHALL ser coberta por teste executado contra um PostgreSQL
real (Testcontainers ou a instância local já exigida pelo build), e NÃO SHALL depender
exclusivamente de teste com repositório e `EntityManager` mockados.

A justificativa é empírica: a suíte mockada existente verificava a *ordem* das chamadas ao
repositório e passava com sucesso enquanto a operação falhava de forma determinística em
banco real. Verificação de sequência de chamadas não é capaz de detectar defeitos que vivem
na decisão que o provedor JPA toma diante do estado do banco.

#### Scenario: Teste afirma o resultado no banco, não a sequência de chamadas

- **WHEN** o teste de transferência de partição é executado
- **THEN** ele SHALL consultar o banco após a operação e afirmar que a linha existe na
  partição de destino e não existe na de origem
- **AND** NÃO SHALL bastar verificar que determinados métodos do repositório foram chamados
  numa dada ordem

#### Scenario: Cobertura dos dois chamadores terminais

- **WHEN** a suíte de testes é executada
- **THEN** SHALL haver verificação contra banco real de que `PATCH /{id}/cancelar` e
  `PATCH /{id}/decisao` (com `acao: REJEITAR` e com `acao: EXPIRAR`) concluem com sucesso e
  deixam a autorização na partição de expurgo esperada
