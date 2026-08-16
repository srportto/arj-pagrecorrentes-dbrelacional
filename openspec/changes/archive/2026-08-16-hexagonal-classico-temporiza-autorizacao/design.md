## Context

O `temporiza-autorizacao` temporiza a jornada 1 do PIX Automático: recebe o evento de recepção por
SQS, agenda um vencimento de 10 minutos no Valkey, varre os vencidos e aciona `PATCH /decisao`
(`acao: EXPIRAR`) no `contratocommand`. Não tem banco relacional.

```
DRIVING (entram na app)                              DRIVEN (a app precisa)

SQS  ──▶ TemporizacaoEventoListener ────┐
                                        │
tempo ─▶ VarreduraAgendamentoScheduler ─┤            ┌─▶ AgendamentoRepository
                                        ├─▶ casos ───┤     └─ Valkey (sorted set)
stream ▶ ExpiracaoStreamListener ───────┤    de uso  │
         PendenciasSchedulerReivindicador│           └─▶ DecisaoAutorizacaoClient
         ConsumidoresOrfaosLimpezaScheduler                └─ HTTP p/ contratocommand
                                        │
HTTP ──▶ TemporizacaoHealthIndicator ───┘
```

Os dois lados DRIVEN já são interfaces. O problema é só de endereço: interface e implementação
moram no mesmo pacote, então a separação porta/adaptador depende de ler o javadoc para ser
percebida. E o lado DRIVING tem quatro naturezas distintas, duas das quais a skill
`arquitetura-limpa-java` não enumera (`web`, `messaging`, `persistence`, `external`, `config`) —
scheduler e health indicator.

## Goals / Non-Goals

**Goals**

- Separar as duas portas de saída existentes dos seus adaptadores, em pacotes distintos.
- Decidir onde vivem adaptadores acionados por tempo e health indicators — decisão de frota.
- Zero mudança de comportamento.

**Non-Goals**

- Reprojetar o mecanismo de stream do Valkey (reivindicação de pendências, limpeza de órfãos).
- Alterar a janela de 10 minutos, a cadência do scheduler ou a política de retry HTTP.
- Introduzir portas para o que hoje não é porta (relógio, logger).

## Decisions

### D1 — Valkey é `infrastructure/persistence/`, não `infrastructure/external/`

`ValkeyAgendamentoRepository` implementa `AgendamentoRepository`, que a própria interface descreve
como "relógio de vencimentos (sorted set no Valkey)". É **estado próprio da aplicação**, que só ela
lê e escreve — não é um sistema de terceiros com contrato alheio.

O critério que adoto para a frota: `persistence/` é onde a app guarda **estado que é dela**,
independente da tecnologia (relacional, chave-valor, stream); `external/` é onde a app fala com
**sistema de outro dono**, cujo contrato ela não controla.

Por esse critério, `CommandDecisaoAutorizacaoClient` (HTTP para o `contratocommand`, outro serviço,
outro time potencial) vai para `external/` — e é o exemplo `EstoqueHttpClient` da skill quase
literalmente.

**Alternativa descartada:** `infrastructure/valkey/`, um pacote por tecnologia. Rejeitada porque
faz o pacote nomear o fornecedor em vez do papel — trocar Valkey por outro store exigiria renomear
pacote, que é exatamente o acoplamento que hexagonal existe para evitar.

### D2 — Adaptador acionado por tempo vai para `infrastructure/scheduler/`

A skill enumera `web/`, `messaging/`, `persistence/`, `external/` e `config/`. Um `@Scheduled` não é
nenhum dos cinco: não há requisição HTTP, não há mensagem, não há armazenamento. Mas é
inequivocamente um **driving adapter** — o tempo é o ator que aciona a aplicação.

Decisão: criar `infrastructure/scheduler/` para essa categoria. `VarreduraAgendamentoScheduler` e
`ConsumidoresOrfaosLimpezaScheduler` vão para lá.

**Alternativa descartada:** enfiar em `messaging/` por proximidade. Rejeitada porque apagaria a
distinção entre "chegou uma mensagem" e "passou o tempo" — que aqui é justamente a natureza do
serviço.

**Ressalva sobre `ConsumidoresOrfaosLimpezaScheduler`:** ele é `@Scheduled` mas seu assunto é o
stream Valkey (housekeeping de consumidores). Vale para `scheduler/` pelo gatilho, não pelo assunto
— o gatilho é o que define a natureza do adaptador.

### D3 — Health indicator vai para `infrastructure/web/`

`TemporizacaoHealthIndicator` implementa `HealthIndicator` do Actuator e existe para responder
`GET /actuator/health`. O gatilho é HTTP; o consumidor é o orquestrador de containers. É um driving
adapter web, ainda que registrado por interface do Spring em vez de `@RestController`.

### D4 — `AutorizacaoEventoPayload` vai para `infrastructure/messaging/`

Este record é o **formato de fio** da mensagem SQS: existe para desserializar o JSON publicado pelo
`contratocommand`, e desta app usa só um subconjunto (id + data de inclusão). Formato de fio de um
produtor externo é detalhe de transporte.

O caso de uso deve receber os dados já traduzidos (`UUID` + `Instant`), não o record de transporte —
caso contrário `application` passa a depender do contrato de serialização de outro serviço, e um
campo renomeado lá vaza para cá.

**Consequência:** o listener SQS traduz o payload em argumentos simples antes de chamar a porta de
entrada. Isso é uma mudança de assinatura interna, não de comportamento.

### D5 — As duas portas de saída existentes mantêm nome e assinatura

`AgendamentoRepository` e `DecisaoAutorizacaoClient` mudam de pacote e nada mais. Os nomes já são
bons (um diz o papel, o outro o colaborador) e as assinaturas já são expressas em tipos simples
(`UUID`, `Instant`). Renomear seria churn sem ganho, e os testes que as mockam continuam válidos.

Observe que os dois sufixos divergem (`Repository` × `Client`) e isso é **proposital**: a skill usa
`PedidoRepository` para estado próprio e `EstoquePort`/`EstoqueHttpClient` para colaborador externo.
O sufixo carrega a mesma distinção de D1.

### D6 — `ExpiracaoRetryavelException` fica em `domain/exception/`, apesar do nome técnico

A exceção sinaliza "não concluído, reentregue" para o mecanismo de stream — parece infraestrutura.
Mas ela é parte do **contrato comportamental da porta** `DecisaoAutorizacaoClient`, cujo javadoc a
cita explicitamente: "retorno normal = concluído, confirmar (XACK); lançar
`ExpiracaoRetryavelException` = não concluído".

Uma porta declarada no domínio não pode ter no contrato uma exceção que vive fora dele — o domínio
passaria a depender de `infrastructure`. Vai para `domain/exception/` junto com
`AgendamentoInvalidoException`.

## Risks / Trade-offs

- **Risco: `ValkeyStreamConfig` sai de `entrypoint/stream/` e algum bean deixa de ser encontrado.**
  Beans de stream do Valkey costumam ser resolvidos por tipo, mas o listener container pode ser
  referenciado por nome. Mitigação: `ValkeyStreamConfigIntegrationTest` roda ao final e a app é
  subida contra o Valkey local.
- **Risco: os três testes de integração dependem de Valkey no ambiente e podem pular sem alarde.**
  Foi exatamente o falso silêncio que a change `integridade-fluxo-escrita` teve de corrigir
  (task 3.7, "Tests run: 0" indistinguível de sucesso). Mitigação: registrar executados **e**
  pulados na linha de base, e comparar os dois números.
- **Trade-off aceito: `infrastructure/scheduler/` extrapola a lista da skill.** A alternativa era
  distorcer uma categoria existente. Se o padrão pegar, cabe atualizar a skill depois — a skill
  descreve o monorepo, não o contrário.
- **Trade-off aceito: traduzir o payload no listener (D4) muda assinaturas internas.** É a única
  mudança desta proposta que não é puro movimento de arquivo, e por isso está coberta por cenário
  próprio na spec.

## Migration Plan

Etapa única. A app não tem estado persistente próprio além do Valkey (que guarda agendamentos
transitórios com TTL), não tem migration e não expõe contrato versionado. Reverter é `git revert`.

Ordem sugerida dentro da etapa, para manter o compilador como guia: portas de saída primeiro
(movimento puro), depois portas de entrada (introduz interface), depois adaptadores, e testes por
último.

## Open Questions

- Nenhuma bloqueante. Se D2 (`infrastructure/scheduler/`) se mostrar solitário — nenhuma outra app
  da frota tem `@Scheduled` fora desta —, vale reavaliar na última mudança se a categoria se
  justifica ou se vira um pacote com dois habitantes para sempre. Não bloqueia esta entrega.

## Implementation Notes

Implementado em 2026-08-15. Nenhuma das seis decisões (D1–D6) precisou ser revista na prática —
todas se sustentaram exatamente como descritas:

- **D1** (Valkey em `persistence/`, command em `external/`): aplicado sem ajuste.
- **D2** (`infrastructure/scheduler/`): `VarreduraAgendamentoScheduler` e
  `ConsumidoresOrfaosLimpezaScheduler` migraram para lá sem atrito. Continua solitário — nenhuma
  outra app da frota tem `@Scheduled` fora desta — mas a categoria provou seu valor: sem ela, os
  dois teriam sido forçados para dentro de `messaging/` por proximidade, obscurecendo que o
  gatilho real é tempo, não mensagem. Recomendação para a última mudança: manter a categoria.
- **D3** (health indicator em `web/`): aplicado sem ajuste; `TemporizacaoHealthIndicator` migrou
  intacto.
- **D4** (payload traduzido no listener): esta foi a única mudança não-mecânica da proposta.
  `AgendarExpiracaoUseCase` mudou de `agendar(String mensagemJson)` — que fazia sua própria
  desserialização Jackson internamente — para `agendar(UUID, LocalDateTime)`, com a
  desserialização de `AutorizacaoEventoPayload` movida para
  `infrastructure/messaging/TemporizacaoEventoListener`. Os testes que cobriam JSON malformado e
  campo ausente foram redistribuídos: o teste de JSON malformado foi para
  `TemporizacaoEventoListenerTest` (falha estrutural, é responsabilidade do adapter); os testes de
  campo nulo (`idAutorizacao`/`dataHoraInclusao`) foram para `AgendarExpiracaoServiceTest` (o
  serviço continua validando os dois como invariante de negócio, agora sobre tipos simples em vez
  de JSON). Contagem total de testes idêntica antes/depois (6 = 6), só a distribuição mudou.
- **D5** (portas de saída sem rename): aplicado sem ajuste.
- **D6** (`ExpiracaoRetryavelException` em `domain/exception/`): aplicado sem ajuste; o javadoc de
  `DecisaoAutorizacaoClient` que a referenciava pelo FQN antigo foi corrigido na mesma migração
  (task 2.5).

**Achado não antecipado pelo design:** `application/usecase/AgendarExpiracaoService` e
`VarrerAgendamentosVencidosService` importam `infrastructure/config/TemporizacaoProperties` —
uma dependência de `application` sobre `infrastructure`, na direção errada da regra hexagonal.
Isso já existia antes da migração (o mesmo acoplamento ligava `application` a `shared/config`) e
não é introduzido por esta mudança — é herança do desenho original, fora do escopo de um
movimento mecânico de pacote. Registrado aqui e em `apps/temporiza-autorizacao/CLAUDE.md`/
`AGENTS.md` como precedente: as próximas quatro migrações devem esperar o mesmo padrão (uso de
`*Properties` por casos de uso) e decidir, caso a caso, se vale introduzir uma abstração de
configuração no domínio ou aceitar a mesma dívida.

Validado também em runtime: app subida contra Valkey local e a fila SQS real (Floci, provisionada
via `infra/envs/local-messaging/`), com `TEMPORIZACAO_PRAZO_MINUTOS=0` para vencimento imediato.
Fluxo observado ponta a ponta — mensagem SQS consumida → agendamento gravado no Valkey → varredura
moveu para o stream (~5s depois) → listener acionou o `PATCH /decisao` no `contratocommand` (fora
do ar no ambiente de teste) → falha de conexão classificada corretamente como
`ExpiracaoRetryavelException`, sem XACK — confirma que a reorganização de pacotes não quebrou a
resolução de nenhum bean por nome (`temporizacaoSqsListenerContainerFactory`,
`streamMessageListenerContainer`).
