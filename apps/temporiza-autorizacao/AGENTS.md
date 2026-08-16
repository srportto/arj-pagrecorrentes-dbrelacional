# CLAUDE.md

> Guia para agentes de IA (Claude Code, Copilot, etc.) trabalharem neste repositório.
> **Este arquivo e `AGENTS.md` são espelhos — mantenha-os idênticos ao editar.**

Temporizador da jornada 1 do PIX Automático, em **arquitetura hexagonal clássica**
(`domain` / `application` / `infrastructure`). Consome os eventos de recepção de
autorizações `PIX_AUTO`/`SPI_J1` publicados pelo `contratocommand` (via
`sns-estados-autorizacao` → SQS `SQS-temporizacao-autorizacao`, filtrada por filter
policy), agenda a expiração em 10 minutos no Valkey e, no vencimento, aciona
`PATCH /api/autorizacoes/{id}/decisao` com `acao: EXPIRAR` — rejeitando sistemicamente a
autorização caso o cliente pagador não tenha decidido a tempo.

## Comece por aqui

Leia nesta ordem:
1. [TemporizacaoEventoListener.java](src/main/java/br/com/srportto/temporizaautorizacao/infrastructure/messaging/TemporizacaoEventoListener.java) — adapter de ENTRADA: `@SqsListener`, traduz o payload em `(UUID, LocalDateTime)` e delega ao use case
2. [AgendarExpiracaoUseCase.java](src/main/java/br/com/srportto/temporizaautorizacao/domain/port/in/AgendarExpiracaoUseCase.java) + [AgendarExpiracaoService.java](src/main/java/br/com/srportto/temporizaautorizacao/application/usecase/AgendarExpiracaoService.java) — calcula o vencimento (`data_hora_inclusao` + prazo) e agenda
3. [AgendamentoRepository.java](src/main/java/br/com/srportto/temporizaautorizacao/domain/port/out/AgendamentoRepository.java) + [ValkeyAgendamentoRepository.java](src/main/java/br/com/srportto/temporizaautorizacao/infrastructure/persistence/ValkeyAgendamentoRepository.java) — porta de saída + `ZADD` no sorted set que funciona como relógio
4. [VarrerAgendamentosVencidosUseCase.java](src/main/java/br/com/srportto/temporizaautorizacao/domain/port/in/VarrerAgendamentosVencidosUseCase.java) + [VarrerAgendamentosVencidosService.java](src/main/java/br/com/srportto/temporizaautorizacao/application/usecase/VarrerAgendamentosVencidosService.java) + [varredura.lua](src/main/resources/scripts/varredura.lua) — varredura atômica: move vencidos do sorted set para o stream
5. [VarreduraAgendamentoScheduler.java](src/main/java/br/com/srportto/temporizaautorizacao/infrastructure/scheduler/VarreduraAgendamentoScheduler.java) — dispara a varredura em intervalo fixo
6. [ValkeyStreamConfig.java](src/main/java/br/com/srportto/temporizaautorizacao/infrastructure/config/ValkeyStreamConfig.java) — cria o consumer group (idempotente) e registra a subscription com ACK MANUAL
7. [ExpiracaoStreamListener.java](src/main/java/br/com/srportto/temporizaautorizacao/infrastructure/messaging/ExpiracaoStreamListener.java) — worker: só confirma (XACK) após desfecho conclusivo
8. [PendenciasSchedulerReivindicador.java](src/main/java/br/com/srportto/temporizaautorizacao/infrastructure/messaging/PendenciasSchedulerReivindicador.java) — reivindica (XCLAIM) pendências ociosas do grupo
9. [DecisaoAutorizacaoClient.java](src/main/java/br/com/srportto/temporizaautorizacao/domain/port/out/DecisaoAutorizacaoClient.java) + [CommandDecisaoAutorizacaoClient.java](src/main/java/br/com/srportto/temporizaautorizacao/infrastructure/external/CommandDecisaoAutorizacaoClient.java) — porta de saída + PATCH síncrono no `contratocommand`, classifica 2xx/4xx (conclusivo) vs. 5xx/timeout (retryable)
10. [TemporizacaoHealthIndicator.java](src/main/java/br/com/srportto/temporizaautorizacao/infrastructure/web/TemporizacaoHealthIndicator.java) — `/actuator/health` reflete o consumo SQS e a conexão Valkey

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
- **`contratocommand` no ar** para o worker conseguir acionar `PATCH /decisao` — sem
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

## Arquitetura (hexagonal clássica)

```
domain/port/in/              → AgendarExpiracaoUseCase, ProcessarExpiracaoUseCase, VarrerAgendamentosVencidosUseCase
domain/port/out/              → AgendamentoRepository, DecisaoAutorizacaoClient
domain/exception/              → AgendamentoInvalidoException (não-retryable), ExpiracaoRetryavelException (retryable)
application/usecase/           → AgendarExpiracaoService, ProcessarExpiracaoService, VarrerAgendamentosVencidosService (script Lua)
infrastructure/messaging/      → TemporizacaoEventoListener (@SqsListener, traduz payload → tipos simples),
                                  TemporizacaoEventoErrorInterceptor, AutorizacaoEventoPayload (formato de fio),
                                  ExpiracaoStreamListener (worker, ack manual), PendenciasSchedulerReivindicador (XCLAIM),
                                  ConsumidorRemocaoService (remoção segura de consumidor)
infrastructure/scheduler/      → VarreduraAgendamentoScheduler, ConsumidoresOrfaosLimpezaScheduler (ambos @Scheduled)
infrastructure/web/            → TemporizacaoHealthIndicator
infrastructure/persistence/    → ValkeyAgendamentoRepository (adapter, ZADD — estado próprio da app)
infrastructure/external/       → CommandDecisaoAutorizacaoClient (adapter, RestClient — sistema de outro dono)
infrastructure/config/         → TemporizacaoProperties, SqsListenerContainerFactoryConfig, CommandClientConfig, ValkeyStreamConfig
```

Categorias de adaptador seguem o **gatilho**, não a tecnologia: `scheduler/` é para tudo
acionado por `@Scheduled` (mesmo quando o assunto é o stream Valkey — o gatilho é tempo,
não mensagem); `web/` inclui o health indicator do Actuator (gatilho é HTTP); `persistence/`
é estado que só esta app lê/escreve (Valkey), `external/` é sistema de outro dono
(`contratocommand`, via HTTP). Esta app foi a segunda das cinco a migrar do layout anterior
(`entrypoint`/`application`/`domain`/`shared`) para o hexagonal clássico — ver a change
`hexagonal-classico-temporiza-autorizacao` em `openspec/changes/` para as decisões (D1–D6)
herdadas pelas migrações seguintes.

**Nota de acoplamento conhecida:** `application/usecase/AgendarExpiracaoService` e
`VarrerAgendamentosVencidosService` importam `infrastructure/config/TemporizacaoProperties`
— uma dependência de `application` sobre `infrastructure`, tecnicamente na direção errada.
É herança do desenho original (o mesmo acoplamento já existia entre `application` e
`shared/config` antes da migração) e o proposal desta mudança deliberadamente não a
redesenhou (movimento mecânico de pacote, zero mudança de comportamento). Corrigir exigiria
introduzir uma abstração de configuração no domínio — fora do escopo desta etapa.

### Por que sorted set + stream, e não só um dos dois

Redis/Valkey **não tem entrega de stream com atraso** — entrada de stream não expira. O
sorted set é o relógio (score = vencimento em epoch millis); o stream só recebe uma
entrada **no vencimento**, criada pela varredura. Ver
`openspec/specs/agendamento-expiracao-valkey/spec.md` para o contrato completo e
`design.md` da mudança `temporizacao-jornada-01-pix-auto` para as alternativas
descartadas (keyspace notifications, scheduler batendo no Postgres).

### Fluxo completo

```
TemporizacaoEventoListener.receber(body)                      [infrastructure/messaging]
  ├─ desserializa AutorizacaoEventoPayload {id_autorizacao, data_hora_inclusao}
  └─ AgendarExpiracaoUseCase.agendar(idAutorizacao, dataHoraInclusao)   [domain/port/in → application/usecase]
       ├─ vencimento = dataHoraInclusao + prazo (10 min, configurável)
       └─ AgendamentoRepository.agendar()                     [domain/port/out → infrastructure/persistence]
            → ZADD agenda:{pixauto:j1} <vencimento> <id>
            (idempotente: reagendar o mesmo id sobrescreve o score, não duplica)

VarreduraAgendamentoScheduler (@Scheduled, ~5s, roda em TODAS as instâncias)  [infrastructure/scheduler]
  └─ VarrerAgendamentosVencidosUseCase.varrer()                [domain/port/in → application/usecase]
       └─ script varredura.lua (atômico):
            ZRANGEBYSCORE agenda -inf <now> LIMIT 0 <lote>
            para cada id: ZREM (só um pod consegue — é o lock) → se sucesso, XADD stream

ValkeyStreamConfig registra a subscription com ACK MANUAL (não confirma sozinho)   [infrastructure/config]
ExpiracaoStreamListener.onMessage(record)                     [infrastructure/messaging]
  ├─ ProcessarExpiracaoUseCase.processar(idAutorizacao)        [domain/port/in → application/usecase]
  │    └─ DecisaoAutorizacaoClient.expirar(id)                 [domain/port/out → infrastructure/external]
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

A rota `/decisao` do `contratocommand` é o único ponto que decide se a expiração se
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
3. **`AutorizacaoEventoPayload` aqui é um subconjunto** do payload do `contratocommand`
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
11. **Consumidores mortos não se acumulam mais — mas a remoção tem duas camadas, e nenhuma delas
    usa `@PreDestroy`.** Camada 1: `ValkeyStreamConfig` implementa `SmartLifecycle` (fase 100,
    maior que a fase padrão do `LettuceConnectionFactory`, que é 0) e remove o consumidor desta
    instância em `stop()`, se `pending = 0`. **Não troque isso por `@PreDestroy`** — foi a
    primeira tentativa, e falhava sempre em runtime (`IllegalStateException`): o Spring executa a
    fase de `Lifecycle.stop()` de todo bean `SmartLifecycle` (a conexão Redis incluída) **antes**
    da fase de `@PreDestroy`/`DisposableBean` no fechamento do contexto — a conexão já estava
    morta quando `@PreDestroy` tentava usá-la. Fase maior para primeiro, com a conexão ainda viva
    (ver `design.md` da change `limpar-consumidores-orfaos-stream`). Camada 2 (rede de segurança
    para `SIGKILL`/OOM/nó perdido, que não aciona `SmartLifecycle.stop()`):
    `ConsumidoresOrfaosLimpezaScheduler`, remove por tempo ocioso (`consumidor-ocioso-limite-ms`,
    default 600000 ms) na cadência de `stream-min-idle-time-ms`.
12. **Nunca remova um consumidor com PEL não vazio.** `XGROUP DELCONSUMER` **descarta** as
    entradas pendentes do consumidor removido: elas não voltam ao grupo, não são reivindicáveis
    por `XCLAIM` e nunca mais são entregues — a autorização correspondente fica presa em
    `RECEBIDA` para sempre, sem sinal nenhum. As duas camadas de remoção
    (`ConsumidorRemocaoService.removerSeSemPendencia`) checam `pending` imediatamente antes de
    remover, nunca reaproveitando leitura de um ciclo anterior. Se for tocar nesse código, não
    remova a checagem achando redundante.
13. **`XGROUP DELCONSUMER` via API tipada do Spring Data Redis não expõe a contagem de PEL
    descartado** — nem `StreamOperations#deleteConsumer` (só `Boolean`), nem
    `RedisConnection#execute("XGROUP", "DELCONSUMER", ...)` genérico (decodifica a resposta como
    bulk string; lança `UnsupportedOperationException` em runtime para o inteiro real que o
    comando devolve — confirmado rodando os testes). `ConsumidorRemocaoService` acessa a conexão
    nativa do driver Lettuce (`RedisClusterAsyncCommands#xgroupDelconsumer`, tipado `Long`) para
    conseguir esse valor, que a verificação de PEL descartado (armadilha 12) exige.
14. **Rodar a app fora do Docker cria o consumidor `worker-local`** (o default de
    `${HOSTNAME:worker-local}`). Duas execuções locais simultâneas compartilhariam o mesmo
    consumidor e disputariam o mesmo PEL.

## Documentação relacionada

- [temporizacao-jornada-01](../../openspec/specs/temporizacao-jornada-01/spec.md) — contrato vigente do porquê `RECEBIDA` e da revalidação sob transação
- [agendamento-expiracao-valkey](../../openspec/specs/agendamento-expiracao-valkey/spec.md) — contrato vigente do sorted set + stream (decididos pela change arquivada `temporizacao-jornada-01-pix-auto`)
- [infra/local/redis/README.md](../../infra/local/redis/README.md) — como subir o Valkey local
- [infra/envs/local-messaging/README.md](../../infra/envs/local-messaging/README.md) — fila, DLQ e filter policy da subscription

## Checklist antes do commit

- [ ] `mvn test` passa (Floci e Valkey no ar — exigidos pelos testes de integração)
- [ ] `mvn clean compile` sem erros
- [ ] Nenhum log novo carrega o corpo do evento consumido
- [ ] Se mudou o cálculo do vencimento, confirmar que reentrega do SQS não o adia
- [ ] Se mexeu no script Lua, testar manualmente contra um Valkey real antes de commitar
  (lógica de script não é verificável em compile-time)
