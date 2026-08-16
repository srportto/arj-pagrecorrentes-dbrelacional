## Why

Segunda das seis mudanças que migram as aplicações de `apps/` para a arquitetura hexagonal clássica
(ver `hexagonal-classico-eventos-consumer` para o motivo geral e para as convenções de nomenclatura
que esta mudança herda).

O `temporiza-autorizacao` é o caso mais interessante da frota: **ele já é ports & adapters, só não
está endereçado assim.** Duas portas de saída existem, com interface própria, javadoc explicando o
papel de porta e implementação separada:

```
application/agendamento/                 application/expiracao/
  AgendamentoRepository      (interface)   DecisaoAutorizacaoClient      (interface)
  ValkeyAgendamentoRepository (impl)       CommandDecisaoAutorizacaoClient (impl)
        └── mesmo pacote ──┘                     └── mesmo pacote ──┘
```

A inversão de dependência está feita e testada — `ProcessarExpiracaoUseCaseTest` e
`AgendarExpiracaoUseCaseTest` já mockam as interfaces. O que falta é **separar porta de adaptador em
pacotes distintos**, para que a regra "domínio declara, infraestrutura implementa" seja verificável
por inspeção de pacote em vez de leitura de javadoc.

Além disso, esta app tem quatro tipos de driving adapter — listener SQS, listener de stream Valkey,
scheduler e health indicator —, mais do que qualquer outra. É onde as decisões sobre **onde mora um
adaptador acionado por tempo** e **onde mora um health indicator** precisam ser tomadas, e as duas
viram precedente para o restante da frota.

## What Changes

- Reorganizar as 23 classes de `main` de `entrypoint`/`application`/`domain`/`shared` para
  `domain`/`application`/`infrastructure`:

  | Hoje | Depois |
  |---|---|
  | `application/agendamento/AgendamentoRepository` | `domain/port/out/AgendamentoRepository` |
  | `application/agendamento/ValkeyAgendamentoRepository` | `infrastructure/persistence/ValkeyAgendamentoRepository` |
  | `application/expiracao/DecisaoAutorizacaoClient` | `domain/port/out/DecisaoAutorizacaoClient` |
  | `application/expiracao/CommandDecisaoAutorizacaoClient` | `infrastructure/external/CommandDecisaoAutorizacaoClient` |
  | `application/{agendamento,expiracao,varredura}/*UseCase` | interface em `domain/port/in/` + `*Service` em `application/usecase/` |
  | `application/eventos/AutorizacaoEventoPayload` | `infrastructure/messaging/AutorizacaoEventoPayload` |
  | `entrypoint/sqs/*` | `infrastructure/messaging/` |
  | `entrypoint/stream/*` (menos `ValkeyStreamConfig`) | `infrastructure/messaging/` |
  | `entrypoint/stream/ValkeyStreamConfig` | `infrastructure/config/` |
  | `entrypoint/scheduler/VarreduraAgendamentoScheduler` | `infrastructure/scheduler/` |
  | `entrypoint/health/TemporizacaoHealthIndicator` | `infrastructure/web/` |
  | `shared/config/*` | `infrastructure/config/` |
  | `shared/exceptions/*` | `domain/exception/` |

- Desdobrar os três casos de uso (`AgendarExpiracaoUseCase`, `ProcessarExpiracaoUseCase`,
  `VarrerAgendamentosVencidosUseCase`) em interface `domain/port/in/` + implementação
  `application/usecase/*Service`, conforme a convenção D1 fixada no piloto.
- Mover as 14 classes de teste para espelhar a árvore nova.
- Acrescentar à capacidade `layout-hexagonal-classico` o requisito específico desta app, mais os
  requisitos gerais sobre adaptadores acionados por tempo e sobre health indicator — que valem para
  a frota inteira e são decididos aqui.
- Atualizar `apps/temporiza-autorizacao/CLAUDE.md` e `AGENTS.md` (espelhos idênticos).

- **Nenhuma mudança de comportamento.** Fila SQS, stream Valkey, consumer group, janela de 10
  minutos da jornada 1, cadência do scheduler, endpoint de health e o `PATCH /decisao` disparado no
  `contratocommand` permanecem idênticos. Critério de aceite: `mvn test` verde com a mesma contagem.

- **Fora de escopo:** o subconjunto de campos que `AutorizacaoEventoPayload` consome do evento (id +
  data de inclusão). Reorganizar pacote não altera o contrato de mensagem.
- **Fora de escopo:** a lógica de reivindicação de pendências e limpeza de consumidores órfãos do
  stream Valkey. As classes se movem; o algoritmo não é tocado.

## Capabilities

### Modified Capabilities

- `layout-hexagonal-classico`: acrescenta (a) o requisito de que adaptadores acionados por tempo
  vivam em `infrastructure/scheduler/` e health indicators em `infrastructure/web/` — duas
  categorias que a skill `arquitetura-limpa-java` não enumera e que esta app é a primeira a ter; e
  (b) o requisito específico do `temporiza-autorizacao`.

## Impact

- **Código afetado (23 arquivos em `main`):** todos mudam de pacote; 3 interfaces novas (portas de
  entrada); 3 classes renomeadas para `*Service`. As duas portas de saída existentes mudam de pacote
  sem mudar de nome nem de assinatura.
- **Testes (14 arquivos):** movidos. Os testes de integração (`VarreduraEAgendamentoIntegrationTest`,
  `ConsumidorRemocaoIntegrationTest`, `ValkeyStreamConfigIntegrationTest`) dependem de Valkey no
  ambiente — confirmar que continuam com o mesmo desfecho (passar ou pular) de antes.
- **Configuração:** `ValkeyStreamConfig` sai de `entrypoint/stream/` para `infrastructure/config/`.
  Confirmar que nenhum bean é resolvido por nome de pacote e que o `@Bean` de container de listener
  continua sendo encontrado.
- **Aplicação relacionada:** o `contratocommand` é acionado por esta app via HTTP
  (`CommandDecisaoAutorizacaoClient` → `PATCH /decisao`). A URL, o timeout e a política de retry não
  mudam; o `contratocommand` não precisa de nenhuma alteração.
- **Documentação:** `apps/temporiza-autorizacao/CLAUDE.md` + `AGENTS.md`.
