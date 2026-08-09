## Context

`temporiza-autorizacao` aciona `PATCH /api/autorizacoes/{id}/decisao` (`acao: EXPIRAR`) no
`arj-contratocommand` a partir de `ExpiracaoStreamListener`/`PendenciasSchedulerReivindicador`,
via `ProcessarExpiracaoUseCase` → `CommandDecisaoAutorizacaoClient`. O consumo é feito com
ack manual sobre um stream Valkey com consumer group: a entrada só é confirmada (XACK) se
`CommandDecisaoAutorizacaoClient.expirar()` retornar sem lançar exceção.

O `arj-contratocommand` mapeia quatro exceções distintas (`ObjectOptimisticLockingFailureException`,
`OptimisticLockException`, `DataIntegrityViolationException`, `StaleStateException`) para
HTTP 409, todas com a mensagem "Tente novamente" — sinal explícito de que a operação **não**
foi aplicada e pode ser repetida com segurança (a rota é idempotente por natureza: só age se
o status ainda for `RECEBIDA`). O `EXPIRAR`/`REJEITAR` passa sempre por
`ExpurgoAutorizacaoService.transferirParaExpurgo()` (delete na partição antiga + flush +
detach + insert na partição de expurgo), uma superfície de conflito de concorrência maior que
um `UPDATE` simples — qualquer segundo chamador concorrente sobre a mesma autorização (decisão
manual do cliente coincidindo com o timer, por exemplo) pode gerar esse 409.

Hoje, `CommandDecisaoAutorizacaoClient.expirar()` captura `HttpClientErrorException`
genericamente (todo o range 4xx) e trata como "nada a fazer" — inclui 409 nessa
classificação, causando XACK indevido: a entrada do stream é confirmada mesmo a transação
tendo sido revertida. Sem a entrada no PEL, `PendenciasSchedulerReivindicador` nunca reclama
nada, e não há nova tentativa. Reproduzido em ambiente local: autorização
`019fe814-09e1-7091-beaf-67814cc70006` presa em `RECEBIDA` após o prazo vencido, com 409 nos
logs do `arj-contratocommand`.

## Goals / Non-Goals

**Goals:**
- `CommandDecisaoAutorizacaoClient` distinguir 409 do restante do range 4xx e tratá-lo como
  retryable.
- Impedir que uma entrada problemática recircule indefinidamente entre
  `PendenciasSchedulerReivindicador` e o PEL, usando um teto de tentativas baseado no contador
  nativo do Redis (`XPENDING`/`PendingMessage.getTotalDeliveryCount()`).
- Cobrir os dois comportamentos com teste automatizado.

**Non-Goals:**
- Alterar o `arj-contratocommand` (ex.: self-heal de conflito de lock otimista dentro da
  própria transação de decisão) — avaliado e descartado nesta mudança; fica como possível
  mudança futura independente, que reduziria a exposição a 409 para **todo** chamador de
  `/decisao` e `/cancelar`, não só o temporizador.
- Criar um destino Valkey dedicado para entradas "mortas" (equivalente à DLT do
  `eventos-consumer`) — decisão explícita: log ERROR + XACK é suficiente por ora, sem
  infraestrutura nova.
- Corrigir retroativamente a autorização `019fe814-09e1-7091-beaf-67814cc70006` (dado de
  teste local).

## Decisions

### D1 — Catch específico para `HttpClientErrorException.Conflict` antes do catch genérico

`RestClient`/Spring já expõe `HttpClientErrorException.Conflict` como subtipo dedicado para
409 — não é necessário inspecionar o corpo da resposta. O catch específico precisa vir
**antes** do catch genérico de `HttpClientErrorException` (ordem de subtipo antes de
supertipo é obrigatória em Java; a ordem inversa não compila). Alternativa descartada:
inspecionar o corpo (`layoutError.getError()`) para decidir — mais frágil, acopla ao texto da
mensagem em vez do status HTTP, que é o contrato real.

### D2 — Teto de tentativas via `PendingMessage.getTotalDeliveryCount()`, não um contador próprio

O Redis já mantém, por entrada do PEL, quantas vezes ela foi entregue (incrementado a cada
`XCLAIM`) — exposto pelo Spring Data Redis em `PendingMessage.getTotalDeliveryCount()` (API
confirmada: `spring-data-redis:4.0.6`, retornado por `StreamOperations.pending(...)`).
Reaproveitar esse contador evita introduzir um contador paralelo (ex.: campo extra no Valkey)
que poderia divergir do estado real do PEL. Teto: **5 tentativas** — mesma ordem de grandeza
do orçamento de retry do SQS documentado em `autorizacaostatus-producer` (~10 min via
`maxReceiveCount`), já que o intervalo entre reivindicações é `stream-min-idle-time-ms`
(120000ms = 2min): 5 × 2min ≈ 10min.

### D3 — Entrada que estoura o teto: XACK + log ERROR, sem stream de "mortas"

Ao atingir o teto, `PendenciasSchedulerReivindicador` confirma (XACK) a entrada diretamente
(sem reivindicá-la/reprocessá-la) e registra `log.error` com `streamId`/`idAutorizacao` (nunca
o corpo do evento — mesma regra de proteção de dado sensível das demais aplicações do
monorepo). Decisão explícita do usuário: sem stream Valkey dedicado a "mortas" — mais simples,
sem estrutura nova a operar. Trade-off aceito: a autorização fica presa em `RECEBIDA` sem
sinal automático de recuperação, apenas o log — investigação e correção são manuais.

## Risks / Trade-offs

- **[Risco] Sem teto anterior, o comportamento atual (bug) já esconde falhas indefinidamente;
  com o teto, uma falha persistente ainda deixa a autorização presa, só que agora com log
  ERROR em vez de silêncio total.** → Mitigação: fora do escopo desta mudança, mas o log
  ERROR é o gancho para alerta/dashboard futuro, se necessário.
- **[Risco] 409 genuinamente raro em produção (fluxo normal não gera concorrência na maioria
  dos casos) — o teste automatizado é a única cobertura real do caminho, não há forma barata
  de reproduzir em ambiente local de forma determinística sem mocks.** → Mitigação: teste
  unitário do `CommandDecisaoAutorizacaoClient` com `RestClient` mockado devolvendo 409 é
  suficiente para travar o contrato; não é necessário teste de integração com concorrência
  real de banco para esta mudança (já existe `ConcorrenciaOptimisticaIntegrationTest` do lado
  do `arj-contratocommand` cobrindo a geração do 409 em si).
- **[Trade-off] Não fechar a lacuna no `arj-contratocommand`** (D-não-goal) significa que
  qualquer outro chamador futuro de `/decisao`/`/cancelar` que também trate 4xx
  genericamente terá o mesmo bug. Aceito nesta mudança por decisão explícita de escopo; deixar
  registrado aqui para uma eventual mudança futura de self-heal no command.

## Migration Plan

Mudança aditiva em `temporiza-autorizacao`, sem alteração de contrato externo (o endpoint
`/decisao` do command não muda). Deploy normal (rebuild + restart do serviço); sem
migração de dados, sem downtime coordenado. Rollback: reverter o commit/imagem — o
comportamento anterior (bug) volta, mas nenhuma estrutura de dados nova foi criada.

## Open Questions

(nenhuma — decisões validadas com o usuário na exploração que precedeu esta proposta)
