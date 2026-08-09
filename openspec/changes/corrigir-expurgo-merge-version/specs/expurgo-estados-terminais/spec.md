## MODIFIED Requirements

### Requirement: Serviço compartilhado de transferência para partição de expurgo

O `arj-contratocommand` SHALL expor um serviço compartilhado em `application/`
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

## ADDED Requirements

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
