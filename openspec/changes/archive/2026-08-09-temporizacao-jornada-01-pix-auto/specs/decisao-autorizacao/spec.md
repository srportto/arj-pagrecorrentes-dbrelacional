## ADDED Requirements

### Requirement: Rota de decisão sobre autorização recebida

O `contratocommand` SHALL expor `PATCH /api/autorizacoes/{idAutorizacao}/decisao`,
recebendo o header obrigatório `tipoProduto` e um corpo com o campo obrigatório `acao`,
cujos valores válidos SHALL ser `APROVAR`, `REJEITAR` e `EXPIRAR`. A rota SHALL seguir o
padrão estrutural já usado por criação e cancelamento: record de contexto imutável,
use case `@Transactional` e validação por rules plugáveis. Ausência do campo `acao` SHALL
resultar em erro de validação de contrato (422, mesmo status já usado pelo
`ApiExceptionHandler` para violação de `@NotNull` nesta aplicação); valor de `acao` fora do
conjunto válido SHALL resultar em `BusinessException` (422), no mesmo padrão já usado por
`ProdutoSuportado` para `tipoProduto` desconhecido.

#### Scenario: Ação desconhecida é rejeitada
- **WHEN** um `PATCH /api/autorizacoes/{id}/decisao` chega com `acao: "CONFIRMAR"`
- **THEN** a resposta é 422 e nenhuma alteração é persistida

#### Scenario: Corpo sem ação é rejeitado
- **WHEN** um `PATCH /api/autorizacoes/{id}/decisao` chega sem o campo `acao`
- **THEN** a resposta é 422 e nenhuma alteração é persistida

### Requirement: Aprovação leva a autorização a ATIVA

Com `acao: APROVAR` sobre uma autorização em `RECEBIDA`, o use case SHALL validar contra o
grafo de `StatusAutorizacao` os saltos `RECEBIDA → EM_PROCESSO_ATIVACAO` e
`EM_PROCESSO_ATIVACAO → ATIVA` via `podeTransicionarPara`, e SHALL persistir o estado final
`ATIVA` com `motivo_status = AUTORIZACAO_ACEITA_POR_TODOS` em uma única transação. O estado
intermediário `EM_PROCESSO_ATIVACAO` NÃO SHALL ser persistido como linha observável, e
exatamente **um** evento SHALL ser publicado.

#### Scenario: Aprovação de autorização recebida
- **WHEN** um `PATCH /{id}/decisao` com `acao: APROVAR` é processado para uma autorização
  `PIX_AUTO` em status `RECEBIDA`
- **THEN** a linha persistida tem `status` correspondente a `ATIVA` (código 4)
- **AND** `motivo_status` é `AUTORIZACAO_ACEITA_POR_TODOS`
- **AND** a resposta é 200 com o estado final da autorização

#### Scenario: Aprovação publica um único evento de ativação
- **WHEN** uma aprovação é concluída com sucesso
- **THEN** exatamente um evento é publicado no tópico `sns-estados-autorizacao`
- **AND** o message attribute `tipoEvento` vale `ATIVACAO`

### Requirement: Rejeição pelo cliente e expiração pelo sistema gravam REJEITADA com motivos distintos

O use case SHALL validar `RECEBIDA → REJEITADA` via `podeTransicionarPara` quando a ação for
`REJEITAR` ou `EXPIRAR` sobre uma autorização em `RECEBIDA`, e SHALL persistir `status`
correspondente a `REJEITADA` (código 6). O `motivo_status` SHALL distinguir a origem:
`REJEITADA_PAGADOR` para `REJEITAR` e `REJEITADA_SISTEMA_TIMEOUT_J1` para `EXPIRAR`. O grafo
de transições de `StatusAutorizacao` NÃO SHALL ser alterado por esta capacidade.

#### Scenario: Cliente rejeita explicitamente
- **WHEN** um `PATCH /{id}/decisao` com `acao: REJEITAR` é processado para uma autorização em
  `RECEBIDA`
- **THEN** a linha persistida tem `status` correspondente a `REJEITADA` (código 6)
- **AND** `motivo_status` é `REJEITADA_PAGADOR`

#### Scenario: Sistema expira por ausência de resposta
- **WHEN** um `PATCH /{id}/decisao` com `acao: EXPIRAR` é processado para uma autorização em
  `RECEBIDA`
- **THEN** a linha persistida tem `status` correspondente a `REJEITADA` (código 6)
- **AND** `motivo_status` é `REJEITADA_SISTEMA_TIMEOUT_J1`

#### Scenario: Ambas publicam evento de rejeição
- **WHEN** uma rejeição ou uma expiração é concluída com sucesso
- **THEN** a mensagem SNS carrega o attribute `tipoEvento` com valor `REJEICAO`

### Requirement: Decisão sobre autorização já resolvida é erro de negócio, não sucesso

A rota SHALL ser segura para chamadas repetidas por um chamador at-least-once. Quando o
status atual não permitir a transição pedida — incluindo o caso de a autorização já ter
sido aprovada, rejeitada, expirada ou cancelada — o use case SHALL lançar
`BusinessException`, resultando em HTTP 422, e NÃO SHALL alterar a linha nem publicar
evento. A mensagem de erro SHALL identificar o status atual, de modo que o chamador
automatizado possa distinguir "já resolvida" (não repetir) de falha de sistema (repetir).
A revalidação SHALL ocorrer dentro da transação, e não depender de leitura prévia feita
pelo chamador.

#### Scenario: Expiração chega depois da aprovação do cliente
- **WHEN** um `PATCH /{id}/decisao` com `acao: EXPIRAR` é processado para uma autorização já
  em `ATIVA`
- **THEN** a resposta é 422 identificando o status atual
- **AND** a linha permanece em `ATIVA`
- **AND** nenhum evento é publicado

#### Scenario: Decisão repetida não duplica efeito
- **WHEN** a mesma decisão é submetida duas vezes em sequência para a mesma autorização
- **THEN** a primeira chamada aplica a transição e a segunda resulta em 422
- **AND** exatamente um evento é publicado no total

#### Scenario: Autorização inexistente
- **WHEN** um `PATCH /{id}/decisao` é processado para um id que não existe
- **THEN** a resposta é 422 e nenhum evento é publicado
