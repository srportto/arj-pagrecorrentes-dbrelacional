## Why

`CommandDecisaoAutorizacaoClient` (temporiza-autorizacao) trata qualquer resposta 4xx do
`PATCH /decisao` como "trabalho concluído, nada a fazer" — mas 409
(`ObjectOptimisticLockingFailureException`/conflito de lock otimista no arj-contratocommand,
cuja própria mensagem é "Tente novamente") significa o oposto de 422: a transação **não**
foi aplicada. O cliente confirma (XACK) a entrada do stream do Valkey mesmo assim, perdendo a
única chance de retry — a autorização fica presa em `RECEBIDA` para sempre, mesmo depois do
prazo de expiração vencido. Reproduzido em ambiente local (autorização
`019fe814-09e1-7091-beaf-67814cc70006`).

## What Changes

- `CommandDecisaoAutorizacaoClient.expirar()` passa a distinguir 409 (`Conflict`) do restante
  do range 4xx: 409 lança `ExpiracaoRetryavelException` (mantém a entrada no PEL do stream,
  elegível a reprocesso); demais 4xx (422 etc.) continuam sendo tratados como conclusivos.
- `PendenciasSchedulerReivindicador` passa a ler `PendingMessage.getTotalDeliveryCount()`
  (contador nativo do `XPENDING`, já exposto pelo Spring Data Redis) e, ao atingir 5
  tentativas, para de reivindicar aquela entrada: confirma (XACK) para não recircular
  indefinidamente e registra `log.error` (sem o corpo do evento) para investigação manual —
  sem stream Valkey dedicado a "mortas" (decisão explícita: sem infraestrutura nova).
- Escopo restrito à aplicação `temporiza-autorizacao`; nenhuma mudança no
  `arj-contratocommand`.

## Capabilities

### New Capabilities

(nenhuma)

### Modified Capabilities

- `temporizacao-jornada-01`: o requisito "Expiração aciona a rota de decisão do command"
  hoje classifica todo 4xx (incluindo 422) como conclusivo — passa a excluir 409
  explicitamente dessa classificação, tratando-o como retryable.
- `agendamento-expiracao-valkey`: o requisito "Consumo do trabalho com confirmação explícita
  e recuperação de pendências" não define um limite de reivindicações — passa a incluir um
  teto de tentativas (5), após o qual a entrada é confirmada sem novo processamento e um log
  de erro sinaliza a desistência.

## Impact

- Código: `apps/temporiza-autorizacao/src/main/java/br/com/srportto/temporizaautorizacao/application/expiracao/CommandDecisaoAutorizacaoClient.java`,
  `apps/temporiza-autorizacao/src/main/java/br/com/srportto/temporizaautorizacao/entrypoint/stream/PendenciasSchedulerReivindicador.java`.
- Testes: novo teste em `CommandDecisaoAutorizacaoClientTest` (409 → `ExpiracaoRetryavelException`,
  não engole) e novo teste em `PendenciasSchedulerReivindicadorTest` (5ª tentativa não reivindica
  de novo, apenas confirma).
- Sem mudança de schema, endpoint público ou infraestrutura. Sem mudança no `arj-contratocommand`.
- A autorização de teste local `019fe814-09e1-7091-beaf-67814cc70006` não será corrigida
  retroativamente (decisão explícita do usuário — é dado de teste local).
