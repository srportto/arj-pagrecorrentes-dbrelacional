# CLAUDE.md

> Guia para agentes de IA (Claude Code, Copilot, etc.) trabalharem neste repositório.
> **Este arquivo e `AGENTS.md` são espelhos — mantenha-os idênticos ao editar.**

Temporizador da jornada 1 do PIX Automático, em **arquitetura hexagonal**. Consome os
eventos de recepção de autorizações `PIX_AUTO`/`SPI_J1` publicados pelo
`arj-contratocommand` (via `sns-estados-autorizacao` → SQS `SQS-temporizacao-autorizacao`,
filtrada por filter policy), agenda a expiração em 10 minutos no Valkey e, no vencimento,
aciona `PATCH /api/autorizacoes/{id}/decisao` com `acao: EXPIRAR` — rejeitando
sistemicamente a autorização caso o cliente pagador não tenha decidido a tempo.

## Comece por aqui

Leia nesta ordem:
1. [TemporizacaoEventoListener.java](src/main/java/br/com/srportto/temporizaautorizacao/entrypoint/sqs/TemporizacaoEventoListener.java) — adapter de ENTRADA: `@SqsListener`, só delega ao use case
2. [AgendarExpiracaoUseCase.java](src/main/java/br/com/srportto/temporizaautorizacao/application/agendamento/AgendarExpiracaoUseCase.java) — calcula o vencimento (`data_hora_inclusao` do payload + prazo) e agenda
3. [ValkeyAgendamentoRepository.java](src/main/java/br/com/srportto/temporizaautorizacao/application/agendamento/ValkeyAgendamentoRepository.java) — `ZADD` no sorted set que funciona como relógio
4. [VarrerAgendamentosVencidosUseCase.java](src/main/java/br/com/srportto/temporizaautorizacao/application/varredura/VarrerAgendamentosVencidosUseCase.java) + [varredura.lua](src/main/resources/scripts/varredura.lua) — varredura atômica: move vencidos do sorted set para o stream
5. [VarreduraAgendamentoScheduler.java](src/main/java/br/com/srportto/temporizaautorizacao/entrypoint/scheduler/VarreduraAgendamentoScheduler.java) — dispara a varredura em intervalo fixo
6. [ValkeyStreamConfig.java](src/main/java/br/com/srportto/temporizaautorizacao/entrypoint/stream/ValkeyStreamConfig.java) — cria o consumer group (idempotente) e registra a subscription com ACK MANUAL
7. [ExpiracaoStreamListener.java](src/main/java/br/com/srportto/temporizaautorizacao/entrypoint/stream/ExpiracaoStreamListener.java) — worker: só confirma (XACK) após desfecho conclusivo
8. [PendenciasSchedulerReivindicador.java](src/main/java/br/com/srportto/temporizaautorizacao/entrypoint/stream/PendenciasSchedulerReivindicador.java) — reivindica (XCLAIM) pendências ociosas do grupo
9. [CommandDecisaoAutorizacaoClient.java](src/main/java/br/com/srportto/temporizaautorizacao/application/expiracao/CommandDecisaoAutorizacaoClient.java) — PATCH síncrono no `arj-contratocommand`, classifica 2xx/4xx (conclusivo) vs. 5xx/timeout (retryable)
10. [TemporizacaoHealthIndicator.java](src/main/java/br/com/srportto/temporizaautorizacao/entrypoint/health/TemporizacaoHealthIndicator.java) — `/actuator/health` reflete o consumo SQS e a conexão Valkey

## Build & Testes

```bash
mvn clean package                            # Compilar + testes + JAR
mvn spring-boot:run                          # Rodar localmente (porta 8084)
mvn test                                     # Todos os testes
```

> **Sem `mvnw`/`mvnw.cmd`** — use `mvn` diretamente, mesma orientação das demais apps.

## Pré-requisitos

- **Java 25** (JDK 25+) — usa `public static void main()`
- **Sem banco de dados** — esta app não usa JPA/PostgreSQL, não conhece o schema de `autorizacoes`
- **Floci no ar** com a fila `SQS-temporizacao-autorizacao` e a subscription filtrada
  aplicadas (`infra/envs/local-messaging/`)
- **Valkey local no ar** (`infra/local/redis/`) — sem ele, `/actuator/health` reporta DOWN
  e nada é agendado nem processado
- **`arj-contratocommand` no ar** para o worker conseguir acionar `PATCH /decisao` — sem
  ele, expirações ficam retidas no PEL do stream até ele voltar (nada se perde)
- Variáveis de ambiente obrigatórias em `prod`: `AWS_REGION`, `AWS_SQS_QUEUE_URL`,
  `VALKEY_HOST`, `COMMAND_BASE_URL` (no profile `local` há defaults apontando para o
  Floci, `localhost:6379` e `localhost:8080`)
- Profiles Spring: `local` (padrão) e `prod` (via `SPRING_PROFILES_ACTIVE=prod`)

## Stack

| Componente | Versão | Notas |
|---|---|---|
| Java | 25 | `void main()` pendente do maven plugin |
| Spring Boot | 4.0.7 | Web MVC (Actuator + `RestClient`), Actuator, `@EnableScheduling` |
| Spring Cloud AWS | 4.0.0 | `spring-cloud-aws-starter-sqs` — `@SqsListener` |
| spring-boot-starter-data-redis | gerenciado pelo Spring Boot BOM | Lettuce; `StringRedisTemplate` para ZSET, script Lua e stream com consumer group |
| Lombok | 1.18.40 | uso mínimo |

## Endpoints reais

| Método | Caminho | Descrição |
|--------|---------|-----------|
| GET | `/actuator/health` | Health-check — reflete consumo SQS e conexão Valkey |

> **Não há endpoints REST de negócio** — consome a fila SQS, agenda/varre no Valkey e
> aciona o command em background.

## Arquitetura (hexagonal)

```
entrypoint/sqs/         → TemporizacaoEventoListener (@SqsListener), TemporizacaoEventoErrorInterceptor
entrypoint/scheduler/   → VarreduraAgendamentoScheduler (@Scheduled)
entrypoint/stream/      → ValkeyStreamConfig (consumer group + subscription), ExpiracaoStreamListener
                           (worker, ack manual), PendenciasSchedulerReivindicador (XCLAIM)
entrypoint/health/      → TemporizacaoHealthIndicator
application/eventos/    → AutorizacaoEventoPayload (subconjunto do payload do command)
application/agendamento/→ AgendarExpiracaoUseCase, AgendamentoRepository (porta),
                           ValkeyAgendamentoRepository (adapter, ZADD)
application/varredura/  → VarrerAgendamentosVencidosUseCase (script Lua)
application/expiracao/  → ProcessarExpiracaoUseCase, DecisaoAutorizacaoClient (porta),
                           CommandDecisaoAutorizacaoClient (adapter, RestClient)
shared/config/          → TemporizacaoProperties, SqsListenerContainerFactoryConfig, CommandClientConfig
shared/exceptions/      → AgendamentoInvalidoException (não-retryable), ExpiracaoRetryavelException (retryable)
```

### Por que sorted set + stream, e não só um dos dois

Redis/Valkey **não tem entrega de stream com atraso** — entrada de stream não expira. O
sorted set é o relógio (score = vencimento em epoch millis); o stream só recebe uma
entrada **no vencimento**, criada pela varredura. Ver
`openspec/specs/agendamento-expiracao-valkey/spec.md` para o contrato completo e
`design.md` da mudança `temporizacao-jornada-01-pix-auto` para as alternativas
descartadas (keyspace notifications, scheduler batendo no Postgres).

### Fluxo completo

```
SqsListener.receber(body)
  └─ AgendarExpiracaoUseCase.agendar(body)
       ├─ desserializa {id_autorizacao, data_hora_inclusao}
       ├─ vencimento = data_hora_inclusao + prazo (10 min, configurável)
       └─ ValkeyAgendamentoRepository.agendar() → ZADD agenda:{pixauto:j1} <vencimento> <id>
            (idempotente: reagendar o mesmo id sobrescreve o score, não duplica)

VarreduraAgendamentoScheduler (@Scheduled, ~5s, roda em TODAS as instâncias)
  └─ VarrerAgendamentosVencidosUseCase.varrer()
       └─ script varredura.lua (atômico):
            ZRANGEBYSCORE agenda -inf <now> LIMIT 0 <lote>
            para cada id: ZREM (só um pod consegue — é o lock) → se sucesso, XADD stream

ValkeyStreamConfig registra a subscription com ACK MANUAL (não confirma sozinho)
ExpiracaoStreamListener.onMessage(record)
  ├─ ProcessarExpiracaoUseCase.processar(idAutorizacao)
  │    └─ CommandDecisaoAutorizacaoClient.expirar(id)
  │         PATCH /api/autorizacoes/{id}/decisao {"acao":"EXPIRAR"}
  │         2xx/4xx (inclui 422 "já resolvida") → retorna normalmente
  │         5xx/timeout/conexão → ExpiracaoRetryavelException
  ├─ sucesso → XACK (streamOps.acknowledge)
  └─ ExpiracaoRetryavelException → NÃO confirma, loga erro, entrada fica no PEL

PendenciasSchedulerReivindicador (@Scheduled, intervalo = stream-min-idle-time-ms)
  ├─ XPENDING no grupo, filtra por tempo ocioso >= min-idle-time
  ├─ XCLAIM os ids ociosos para este consumidor
  └─ reprocessa cada um pelo mesmo caminho do listener normal (mesmo método, sem duplicar lógica)
```

### Contrato de conclusão com o command

A rota `/decisao` do `arj-contratocommand` é o único ponto que decide se a expiração se
aplica — este app **não lê o banco**, nunca. O contrato de status HTTP é o que decide
ack/retenção:

| Resposta do command | Ação do worker |
|---|---|
| 2xx (expiração aplicada) | XACK |
| 409 (conflito de lock otimista — "Tente novamente") | sem XACK, permanece no PEL |
| 4xx exceto 409, incluindo 422 (já resolvida/não encontrada) | XACK — nada a fazer |
| 5xx / timeout / erro de conexão | sem XACK, permanece no PEL |

> **409 não é um 4xx comum**: ao contrário de 422 (a transação rodou e confirmou que não há
> nada a fazer), 409 significa que a transação do command **foi revertida** — a expiração
> pode não ter sido aplicada. Tratar 409 como os demais 4xx faz o worker confirmar (XACK) um
> trabalho que na verdade não foi concluído, prendendo a autorização em `RECEBIDA` para
> sempre, sem retry possível (bug corrigido pela mudança `corrigir-ack-indevido-expiracao-409`).
> `CommandDecisaoAutorizacaoClient` captura `HttpClientErrorException.Conflict` (409) **antes**
> do catch genérico de `HttpClientErrorException`, relançando como `ExpiracaoRetryavelException`.

> **409 no caminho feliz não existe mais.** Entre 2026-08-09 e a mudança
> `corrigir-expurgo-merge-version`, **toda** expiração recebia 409 do command: a transferência da
> autorização para a partição de expurgo estava quebrada de forma determinística (o `merge` de
> instância detached parou de funcionar quando `@Version` foi adicionado à entidade), e o retry
> jamais poderia ter sucesso. Nada desta app precisou mudar — o retry em 409 e o teto de 5
> tentativas se comportaram exatamente como especificado, e foram eles que transformaram um bug
> silencioso do command em sinal operacional. Hoje a expiração conclui na primeira tentativa; 409
> volta a significar apenas o que sempre devia significar: disputa real com outro chamador.

## Armadilhas críticas

1. **Porta 8084** — diferente de 8080/8081/8082/8083.
2. **Sem banco de dados** — não adicione JPA/Postgres aqui. Se algo parecer exigir
   persistência relacional, é mudança de escopo.
3. **`AutorizacaoEventoPayload` aqui é um subconjunto** do payload do `arj-contratocommand`
   (só `id_autorizacao` e `data_hora_inclusao`) — não um espelho completo. Isso é
   intencional: a filter policy da subscription já garante o filtro por produto/jornada/
   tipo de evento, então este app não precisa dos demais campos.
4. **A app não lê a base de autorizações** — o command revalida sob transação de qualquer
   forma (idempotência via 422), então uma leitura prévia seria só otimização, com o custo
   de acoplar esta app ao schema particionado. Ver design.md, decisão 6.
5. **`varredura.lua` roda em TODAS as instâncias, sem lock distribuído externo** — o
   `ZREM` dentro do script é o lock: só a instância cuja remoção retornar sucesso cria a
   entrada no stream. Não adicione lock (Redlock, etc.) por cima disso.
6. **`ExpiracaoStreamListener` e `PendenciasSchedulerReivindicador` compartilham o mesmo
   caminho de processamento** (`processarEConfirmarSeConcluido`) — não duplique a lógica
   de ack/retry no reivindicador.
7. **Chaves com hash tag** (`agenda:{pixauto:j1}`, `stream:{pixauto:j1}:expiracoes`) —
   necessário para operação em cluster mode do ElastiCache (mesmo slot). Não remova as
   chaves `{}` ao editar `application.yaml`.
8. **`CommandDecisaoAutorizacaoClient` não loga em caso de sucesso (2xx)** — só loga 4xx
   (info) e 5xx/erro de conexão (via exceção). Ausência de log não indica falha.
9. **Nenhum log carrega o corpo do evento consumido** — o payload de origem (mesmo o
   subconjunto) não deve aparecer em logs de erro; identifique sempre por
   `idAutorizacao`/`messageId`/`streamId`.
10. **Teto de 5 tentativas por entrada do stream de expirações** — `PendenciasSchedulerReivindicador`
    lê `PendingMessage.getTotalDeliveryCount()` (contador nativo do `XPENDING`, incrementado a
    cada `XCLAIM`) e, ao atingir `MAX_TENTATIVAS_EXPIRACAO` (5), confirma (XACK) a entrada
    diretamente **sem** reivindicá-la/reprocessá-la, registrando `log.error` com `streamId` e
    `idAutorizacao` (nunca o corpo do evento). Sem esse teto, uma entrada que falhe de forma
    persistente recircularia entre o PEL e o reivindicador indefinidamente, a cada
    `stream-min-idle-time-ms`, sem nenhum sinal operacional. Não há stream Valkey dedicado a
    "mortas" — a investigação de uma entrada esgotada é manual, via log.

## Documentação relacionada

- [design.md da mudança temporizacao-jornada-01-pix-auto](../../openspec/changes/temporizacao-jornada-01-pix-auto/design.md) — decisões técnicas completas (por que RECEBIDA e não PENDENTE_ACEITE, por que sorted set + stream, por que sem leitura de banco, etc.)
- [infra/local/redis/README.md](../../infra/local/redis/README.md) — como subir o Valkey local
- [infra/envs/local-messaging/README.md](../../infra/envs/local-messaging/README.md) — fila, DLQ e filter policy da subscription

## Checklist antes do commit

- [ ] `mvn test` passa (Floci e Valkey no ar — exigidos pelos testes de integração)
- [ ] `mvn clean compile` sem erros
- [ ] Nenhum log novo carrega o corpo do evento consumido
- [ ] Se mudou o cálculo do vencimento, confirmar que reentrega do SQS não o adia
- [ ] Se mexeu no script Lua, testar manualmente contra um Valkey real antes de commitar
  (lógica de script não é verificável em compile-time)
