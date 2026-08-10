## Context

O `temporiza-autorizacao` consome as expirações vencidas de um stream Valkey via consumer
group, com ack manual. Cada instância se identifica pelo `consumidor-id`, configurado como
`${HOSTNAME:worker-local}` em `application.yaml` — em container, o id do container.

O Valkey **cria o consumidor implicitamente** na primeira leitura (`XREADGROUP`) ou
reivindicação (`XCLAIM`), e nunca o remove sozinho. Remover é responsabilidade da aplicação,
via `XGROUP DELCONSUMER`, que hoje não é chamado em lugar nenhum.

Estado observado em 2026-08-09, ambiente local:

```
XINFO GROUPS  → consumers: 7, pending: 0
XINFO CONSUMERS →
  16559137cd1f   pending 0   idle ~3,6h   ← container morto
  25f265581471   pending 0   idle ~3,6h   ← container morto
  87c77e0118bc   pending 0   idle ~3,6h   ← container morto
  dd8389abb26f   pending 0   idle ~3,6h   ← container morto
  worker-local   pending 0   idle ~3,6h   ← execução fora do Docker
  5e53685807dc   pending 0                ← pod vivo
  6cd97562879e   pending 0                ← pod vivo
```

Limpeza manual aplicada no dia (mantida aqui como procedimento de contorno até esta mudança
ser implementada):

```bash
XGROUP DELCONSUMER stream:{pixauto:j1}:expiracoes temporizaautorizacao <nome>
```

### A restrição que domina o desenho

`XGROUP DELCONSUMER` **descarta o PEL do consumidor removido** e devolve quantas entradas
pendentes foram perdidas. Uma entrada assim descartada não volta ao grupo, não é reivindicável
por `XCLAIM` e nunca mais é entregue — a autorização correspondente ficaria presa em
`RECEBIDA` para sempre, sem sinal.

Isso é exatamente a classe de falha que a change `corrigir-expurgo-merge-version` acabou de
custar caro para diagnosticar: trabalho silenciosamente perdido, sem erro em lugar nenhum.
Qualquer limpeza automática aqui **precisa** ser incapaz de causá-la.

## Goals / Non-Goals

**Goals:**

- Consumidores de instâncias que não existem mais deixam de acumular no grupo.
- Nenhuma entrada pendente é perdida por causa da limpeza — em nenhuma ordem de eventos,
  incluindo pod morto abruptamente (SIGKILL, OOM, nó perdido).
- O crescimento anômalo da contagem de consumidores é observável antes de virar incidente.

**Non-Goals:**

- Mudar a estratégia de `consumidor-id`. O hostname é a escolha certa: o id precisa ser único
  por instância para o PEL ser atribuído corretamente.
- Limpar consumidores de outros grupos ou streams — só o grupo de expirações.
- Recuperar as entradas de um consumidor que já tenha sido removido no passado com PEL não
  vazio (não há registro de que tenha ocorrido; se ocorrer, é investigação, não rotina).

## Decisions

### D1 — Duas camadas: encerramento gracioso + rede de segurança por ociosidade

Nenhuma das duas isolada resolve.

**Camada 1 — remoção no encerramento (`@PreDestroy` / shutdown hook).** Cobre o caso comum:
deploy, `docker compose down`, scale-in. É imediata e precisa — a instância sabe quem ela é.
Sozinha, é insuficiente: não roda em `SIGKILL`, OOM, nó perdido ou `docker kill`, que são
justamente os cenários em que o consumidor morre **com** PEL não vazio.

**Camada 2 — varredura por tempo ocioso.** Cobre o que a camada 1 perde. Um consumidor com
`idle` acima de um limiar generoso e `pending = 0` é seguramente removível: não tem trabalho
atribuído e não lê o stream há muito tempo. Se voltar à vida, o Valkey o recria na próxima
leitura, sem efeito colateral.

**Alternativas descartadas:**

| Alternativa | Por que não |
|---|---|
| Só o shutdown hook | Não cobre morte abrupta, que é o caso perigoso e o mais comum em ECS sob OOM/rebalance. |
| Só a varredura por ociosidade | Funciona, mas deixa o consumidor de um deploy normal ocupando o grupo por horas sem necessidade. A camada 1 é barata e torna o estado do grupo imediatamente fiel à realidade. |
| Consultar quais instâncias estão vivas (service discovery, registro em chave Valkey com TTL) | Introduz um segundo mecanismo de presença para resolver um problema de higiene. O par `idle` + `pending`, que o próprio `XINFO CONSUMERS` já entrega, é suficiente e não adiciona estado novo. |
| `consumidor-id` estável por réplica (ex.: `worker-0`, `worker-1`) | Elimina o acúmulo, mas exige identidade ordinal estável (StatefulSet), que o ECS não dá. Pior: dois pods com o mesmo id disputariam o mesmo PEL. |

### D2 — `pending > 0` bloqueia a remoção, incondicionalmente

A limpeza **SHALL** verificar `pending` imediatamente antes de remover e **SHALL NOT** remover
consumidor com PEL não vazio — nem no shutdown hook, nem na varredura, nem com o consumidor
ocioso há dias.

Um órfão com pendências não é lixo: é um sinal de que trabalho ficou para trás. O caminho
correto para ele já existe e é o `PendenciasSchedulerReivindicador`, que reivindica a entrada
por tempo ocioso **da entrada** (não do consumidor) e a reprocessa. Depois de reivindicada, o
`pending` do órfão cai a zero e a limpeza passa a poder removê-lo na varredura seguinte — na
ordem certa, sem perda.

Consequência aceita: um consumidor órfão com PEL que nunca esvazie (porque a entrada esgotou
as 5 tentativas e foi confirmada por `desistirDeEntradasEsgotadas`) sai do grupo só no ciclo
seguinte. Atraso irrelevante; a alternativa seria perder trabalho.

### D3 — Limiar de ociosidade folgado, e maior que o do reivindicador

O limiar **SHALL** ser configurável e **SHALL** ser confortavelmente maior que
`stream-min-idle-time-ms`, para que a limpeza nunca corra na frente da reivindicação: quando
um consumidor entra na janela de remoção, suas entradas já devem ter sido reivindicadas por
outra instância há muito tempo.

Uma instância viva mas sem trabalho **não** fica ociosa indefinidamente — o
`PendenciasSchedulerReivindicador` chama `XPENDING`/`XCLAIM` periodicamente. Ainda assim, o
limiar deve ser dimensionado para o pior caso de inatividade legítima (madrugada sem
expirações), não para o intervalo do scheduler.

### D4 — Contagem de consumidores como sinal, não como alarme rígido

A contagem **SHALL** ser observável (health indicator, métrica ou log periódico). Não se
define aqui um alarme com limiar fixo: o número correto de consumidores é o número de
instâncias, que varia com o autoscaling. O valor está em tornar a divergência visível, não em
travar a aplicação por causa dela.

## Risks / Trade-offs

- **Shutdown hook remove o consumidor e a instância volta a processar antes de morrer** →
  O Valkey recria o consumidor na próxima leitura; não há erro. O único efeito é uma linha a
  mais no grupo, resolvida pela camada 2.

- **Corrida entre a varredura de uma instância e o PEL de outra** → A checagem de `pending` e
  o `DELCONSUMER` não são atômicos entre si; uma entrada pode ser atribuída ao consumidor
  exatamente entre as duas. Mitigação: o alvo tem `idle` acima de um limiar alto, então não
  está lendo o stream e só receberia entrada via `XCLAIM` explícito de outra instância — que
  reivindica **para si**, não para o ocioso. A janela é teórica.

- **Limpeza mascarando um problema real** → Se pods estão morrendo com frequência, a limpeza
  esconde o rastro. Mitigação: a remoção **SHALL** ser logada com o nome do consumidor e o
  tempo ocioso, de modo que a frequência de remoções fique auditável no log.

- **Mais uma coisa rodando em `@Scheduled` em todas as instâncias** → Como a varredura de
  agendamentos, roda em todas; `DELCONSUMER` é idempotente (remover o que já não existe é
  no-op), então não precisa de lock distribuído. Coerente com a armadilha 5 do
  `CLAUDE.md` da app: não adicionar lock por cima de operação já idempotente.

## Open Questions

- **Qual limiar de ociosidade?** Precisa de um número que não seja atingível por instância
  viva no pior caso de inatividade legítima. Depende do perfil de tráfego real da jornada 1,
  que ainda não foi medido em produção.
- **Onde expor a contagem?** `TemporizacaoHealthIndicator` (já reporta Valkey e SQS) ou
  métrica Micrometer. O health indicator é mais simples; a métrica é o que serve para
  alarme/histórico. A app ainda não tem Micrometer configurado.
- **A limpeza deve viver no `PendenciasSchedulerReivindicador` ou em componente próprio?**
  O reivindicador já faz `XPENDING` no grupo a cada ciclo, então aproveitaria a chamada — mas
  a responsabilidade é outra (higiene do grupo vs. reprocessamento de trabalho), e a armadilha
  6 do `CLAUDE.md` da app alerta contra inchar aquela classe.
