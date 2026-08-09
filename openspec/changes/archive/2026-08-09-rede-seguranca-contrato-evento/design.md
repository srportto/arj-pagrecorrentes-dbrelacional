## Context

O fluxo de eventos atravessa três serviços com deploy independente e quatro cópias manuais de
schema, sem módulo compartilhado:

```
  arj-contratocommand          autorizacaostatus-producer        eventos-consumer
  ───────────────────          ──────────────────────────        ────────────────
  AutorizacaoEventoPayload ──► AutorizacaoEventoPayload
       (JSON, cópia 1)              (JSON, cópia 2)
                                         │ EventoAutorizacaoConverter
                                         ▼
                              EventoAutorizacao.avsc ─────────►  EventoAutorizacao.avsc
                                  (Avro, cópia 1)                    (Avro, cópia 2)
```

Estado atual verificado na auditoria: **as quatro cópias estão sincronizadas**. As duas do Avro são
byte a byte idênticas; as duas do payload JSON têm os mesmos 26 campos com os mesmos
`@JsonProperty`.

A regra que mantém isso é uma linha de checklist em `apps/arj-contratocommand/CLAUDE.md:202`.
Nenhum teste, nenhuma verificação de CI, nenhuma falha de build. A sincronização depende de a
pessoa que altera um app lembrar de alterar o outro — e de o revisor perceber se ela esquecer.

O par de comportamentos que transforma esquecimento em desastre já foi validado como correto
isoladamente:

| Componente | Comportamento | Por que existe |
|---|---|---|
| `AutorizacaoEventoPayloadValidator` | payload inválido → não-retryable → ack | impedir que dado ruim trave a fila (especificado) |
| `new ObjectMapper()` no producer | propriedade desconhecida → ignorada | default do Jackson 3, nunca declarado explicitamente |

**Correção pós-auditoria (2026-08-09):** o cenário "campo novo = toda mensagem vira dado
inválido = descarte de 100%" descrito na versão original deste documento pressupunha Jackson 2.
O producer usa Jackson 3 (`tools.jackson.databind`), cujo `FAIL_ON_UNKNOWN_PROPERTIES` é `false`
por padrão — validado contra o jar empacotado. O risco real não é a perda de eventos por campo
novo; é o comportamento depender de um default de framework em vez de estar declarado no código
(ver D3, revisado).

## Goals / Non-Goals

**Goals:**

- Que a divergência entre cópias de schema seja detectada no build, não em produção.
- Que um campo novo desconhecido degrade o processamento (ignora o campo) em vez de apagá-lo
  (descarta a mensagem).
- Que a DLT do consumer exista de fato no broker.
- Que schema incompatível não seja auto-registrado em produção sem revisão.

**Non-Goals:**

- Módulo Maven compartilhado substituindo os espelhos.
- Verificação de compatibilidade contra o Schema Registry em CI.
- Alterar a classificação retryable/não-retryable do producer, que está correta e especificada.
- Corrigir o log de PII do consumer (proposta `parar-vazamento-dado-sensivel`).

## Decisions

### D1 — Teste de comparação, não módulo compartilhado

O módulo compartilhado é a solução estrutural correta e elimina a classe inteira de problema. Foi
descartado **deste escopo** por mudar a topologia de build e a autonomia de deploy dos serviços —
decisão de arquitetura que merece sua própria discussão, não um efeito colateral de uma correção
de auditoria.

O teste de comparação é desproporcionalmente barato para o risco que cobre: lê os dois arquivos,
compara, falha. Não impede a divergência, mas torna impossível mergeá-la sem ver.

### D2 — Onde o teste roda importa mais do que como ele compara

O risco real é a alteração **unilateral**: alguém mexe só no command. Se o teste viver apenas no
producer e o CI rodar somente os módulos afetados pelo diff, a alteração no command passa verde.

Duas formas de resolver, a decidir na implementação conforme o CI existente:

- **(a)** Teste em cada app comparando sua cópia com a do irmão, via caminho relativo no
  monorepo. Simples, mas cria acoplamento de path entre módulos.
- **(b)** Job de CI dedicado, independente de módulo, que roda em qualquer alteração sob `apps/`.
  Mais robusto contra build seletivo; exige tocar a pipeline.

A escolha depende de o CI hoje construir tudo ou só o que mudou — está como task de verificação.
O critério inegociável é o resultado: **mudar um lado e não o outro tem de falhar o build.**

**Decisão (2026-08-09, verificação da task 1.1):** não existe `.github/workflows/` neste
repositório — não há CI hoje, de nenhuma forma, seletiva ou não. Como não há pipeline
existente para acoplar um teste por-módulo, e como os apps espelhados não compartilham
módulo Maven, escolhida a opção **(b)**: um job de CI dedicado
(`.github/workflows/contrato-eventos.yml`), independente de módulo, que roda em qualquer
alteração sob `apps/**`. A verificação é um programa Java de arquivo único
(`ci/contrato-eventos/VerificarContratoEventos.java`, executado via `java
VerificarContratoEventos.java` — lançamento de fonte única do Java 25), sem depender de
build Maven de nenhum app, comparando os arquivos diretamente do disco.

### D3 — `ignoreUnknown = true` explícito, não como correção de bug (revisado 2026-08-09)

Validação pós-auditoria: o producer roda Jackson 3, cujo default já é `FAIL_ON_UNKNOWN_PROPERTIES
= false` — campo desconhecido já é ignorado hoje, mesmo sem a anotação. Não há falha ativa a
corrigir aqui; a decisão original de "ligar `ignoreUnknown` só depois do teste de contrato estar
no ar" (para não mascarar o teste com uma correção simultânea) permanece válida por prudência,
mas o racional muda: o objetivo passa a ser **declarar** o comportamento no código em vez de
depender de um default de versão do framework — e alinhar o producer ao `temporiza-autorizacao`,
que já é explícito nisso.

Mantém-se a divisão de papéis original, agora com o segundo item reclassificado:

- Teste de contrato (D1/D2): detecta a divergência entre cópias **antes** do deploy, ruidosamente
  — continua sendo a peça principal, sem mudança.
- `ignoreUnknown` explícito: não é mais uma rede contra perda de mensagens (essa perda não ocorre
  no comportamento atual do Jackson 3); é documentação executável de uma invariante que já existe.

**Achado adicional (2026-08-09, validação ponta a ponta na implementação):** provisionar o
tópico `eventos-autorizacao.DLT` não bastou. O destino default do
`DeadLetterPublishingRecoverer` no spring-kafka 4.0.6 (verificado via bytecode da classe)
é `<topico>` + `-dlt` (hífen, minúsculo) — não `<topico>.DLT` como a documentação do app e
esta proposta assumiam. Sem um resolver explícito, a publicação na DLT falhava com
`UnknownTopicOrPartitionException` contra o tópico inexistente `eventos-autorizacao-dlt`,
reproduzindo ao vivo o cenário exato que esta mudança existe para evitar (offset não
avança, partição trava na mensagem venenosa). Corrigido com um destino explícito
(`BiFunction`) em `KafkaConsumerConfig`, fixando `<topico>.DLT`. Revalidado com 3
mensagens venenosas após a correção: todas chegaram na DLT, offsets avançaram.

### D4 — DLT com as mesmas 3 partições do tópico principal

Não há motivo para divergir. Manter simétrico evita surpresa de paralelismo se um dia a DLT for
consumida, e mantém o `kafka-topic-init` legível.

Vale notar o alcance da correção: ela conserta o **ambiente local**. Se existir provisionamento de
tópicos para outros ambientes (IaC, pipeline), ele precisa da mesma adição — está como task de
verificação, porque o repo hoje só evidencia o compose local.

### D5 — `auto.register.schemas=false` em prod exige um caminho de registro

Desligar sem oferecer alternativa quebra o primeiro deploy de qualquer schema novo. A mudança
precisa definir **como** o schema passa a ser registrado em produção: passo de pipeline, registro
manual revisado, ou `maven-plugin` da Confluent num job dedicado.

Fica como Open Question porque depende de como o Schema Registry de produção é operado — informação
que não está no repositório.

**Decisão (2026-08-09, implementação):** o repositório não evidencia CI/CD (`.github/workflows/`
não existe até esta mudança) nem IaC de Schema Registry de produção. Sem pipeline para acoplar um
passo automatizado, a opção realista hoje é **registro manual revisado**: antes do primeiro deploy
de um schema novo/alterado em `prod`, quem opera o deploy roda a CLI do Schema Registry (ou o
`kafka-avro-console-producer`/`curl` contra a API REST do Registry) para registrar o subject
`eventos-autorizacao-value` com o `.avsc` atualizado, e confirma compatibilidade antes do produce.
Documentado como runbook manual no `CLAUDE.md`/`AGENTS.md` dos dois apps (task 6.4) — não é a
solução ideal (um passo de pipeline automatizado seria mais robusto), mas é o caminho proporcional
à infraestrutura de produção que o repositório de fato evidencia hoje. Revisitar quando um CI/CD
real existir.

**Achado adicional (auditoria 2026-08-09):** o mesmo literal fixo existe também em
`eventos-consumer/KafkaConsumerConfig.java:114` (lado consumidor da mesma integração com o
Schema Registry). A proposta original só cobria `KafkaProducerClientConfig`. Escopo ampliado:
a parametrização por profile (D5) e a task correspondente passam a cobrir os dois apps.

## Risks / Trade-offs

- **`ignoreUnknown` mascara divergência se o teste de contrato não pegar** → Mitigação: os dois
  entram juntos, e o teste é a peça principal (D3). Implementar apenas o `ignoreUnknown` seria
  trocar uma falha ruidosa por uma silenciosa.

- **CI com build seletivo pode não rodar o teste na alteração unilateral** → É exatamente o cenário
  que importa. Verificação explícita do comportamento do CI antes de escolher entre (a) e (b) em D2.

- **`auto.register.schemas=false` quebra o deploy de schema novo em produção** → Não mesclar sem
  definir o caminho de registro substituto (D5, Open Questions).

- **A correção da DLT pode cobrir só o ambiente local** → Task explícita para verificar se há
  provisionamento de tópicos em outros ambientes e replicar.

- **Teste de comparação byte a byte é frágil a reformatação** → Comparar estrutura semântica
  (conjunto de campos, tipos, nulabilidade) em vez de texto cru, para que reindentar um `.avsc` não
  quebre o build por nada.

## Migration Plan

1. Teste de contrato + ajuste do CI, validado com uma divergência proposital que deve falhar.
2. `ignoreUnknown` no producer (depois do teste estar no ar, nunca antes).
3. DLT no `kafka-topic-init` + verificação de outros ambientes.
4. Correção do `CLAUDE.md`/`AGENTS.md` do consumer.
5. `auto.register.schemas` por profile — por último, depois do caminho de registro em prod definido.

Rollback: todos os itens são independentes e reversíveis isoladamente.

## Open Questions

- O CI hoje constrói o monorepo inteiro ou só os módulos afetados pelo diff? Define a forma do
  teste (D2).
- Como o schema passará a ser registrado em produção com `auto.register.schemas=false`? Bloqueia
  o item 5 do plano.
- Existe provisionamento de tópicos Kafka para ambientes além do local? Se sim, a DLT precisa ser
  adicionada lá também.
