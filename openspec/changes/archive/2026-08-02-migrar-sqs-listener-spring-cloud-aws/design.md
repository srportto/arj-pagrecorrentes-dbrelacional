## Context

A `autorizacaostatus-producer` é uma ponte SQS → Kafka. Seu adaptador de entrada é hoje
um listener escrito à mão: `SqsEventoAutorizacaoListener` implementa `SmartLifecycle`,
inicia uma virtual thread própria, roda um loop de long polling com o `SqsClient` do AWS
SDK v2, processa o lote em série e emite `DeleteMessage` explicitamente após a
confirmação do broker Kafka.

Essa forma foi deliberada e está registrada na spec `consumo-eventos-autorizacao`, que
prescreve "sem Spring Cloud AWS". A prescrição existia porque não havia versão do Spring
Cloud AWS compatível com Spring Boot 4. O **Spring Cloud AWS 4.0.0**, alinhado ao release
train Spring Cloud 2025.1.0 (Oakwood), entrega compatibilidade com Spring Boot 4.x e
Spring Framework 7.x — a restrição caiu.

Três propriedades do estado atual motivam a mudança:

```
┌──────────────────────────────────────────────────────────────────────┐
│ 1. ~180 linhas de infraestrutura de listener mantidas à mão          │
│    loop, SmartLifecycle, join calibrado, backoff, catch(Throwable),  │
│    health indicator de liveness de thread                            │
│                                                                      │
│ 2. Orçamento de retry de ~90s                                        │
│    visibility timeout 30s (default implícito) × maxReceiveCount 3    │
│    → outage de Kafka > 90s esvazia a fila na DLQ                     │
│                                                                      │
│ 3. Concorrência efetiva = 1                                          │
│    lote de 10 processado em série numa única thread                  │
│    → produce síncrono vira batch Kafka de tamanho 1 por evento       │
│    → a virtual thread existente é decorativa                         │
└──────────────────────────────────────────────────────────────────────┘
```

As decisões de semântica foram tomadas na fase de exploração e são premissas deste
design, não questões abertas: a garantia **at-least-once** é preservada (ack condicionado
à confirmação do Kafka) e a **ordenação é responsabilidade do consumidor a jusante**, que
ordena por `data_hora_ultima_atlz`.

## Goals / Non-Goals

**Goals:**

- Substituir o adaptador de entrada manual por `@SqsListener`, eliminando o código de
  ciclo de vida, polling, backoff e ack explícito.
- Preservar integralmente a garantia at-least-once e o ponto único de classificação de
  erro, migrando apenas o mecanismo pelo qual se expressam.
- Elevar a concorrência de processamento por instância de 1 para um valor configurável
  (ver Decisão 7 — a mecânica real de concorrência do framework difere da hipótese
  inicial de virtual threads).
- Recalibrar o orçamento de retry de ~90s para ~10min.
- Fechar o único caminho do produce sem teto de tempo (round-trip ao Schema Registry).
- Manter `application/`, `domain/` e o adaptador de saída Kafka intocados.

**Non-Goals:**

- **Não** alterar a semântica de entrega. Ack independente da confirmação do Kafka foi
  avaliado e rejeitado (ver Decisão 2).
- **Não** migrar `data_hora_ultima_atlz` de `local-timestamp-micros` para
  `timestamp-micros`. É um follow-up legítimo, mas exige coordenação com os espelhos
  manuais do `.avsc` em `apps/eventos-consumer` e do payload em `arj-contratocommand`.
- **Não** adotar `spring-kafka`. O adaptador de saída continua sendo cliente Kafka puro.
- **Não** introduzir dedup persistente na ponte. A deduplicação é contrato do consumidor,
  pela key.
- **Não** alterar o schema Avro, o payload consumido, a key de idempotência ou o header
  `tipoEvento`.

## Decisions

### Decisão 1 — Spring Cloud AWS 4.0.0 com `@SqsListener`

**Escolha:** adotar `io.awspring.cloud:spring-cloud-aws-starter-sqs` (BOM 4.0.0) e um
método `@SqsListener` que apenas delega ao use case.

**Alternativas consideradas:**

- *Manter o SDK v2 puro e apenas paralelizar o lote à mão.* Resolveria a concorrência,
  mas manteria as ~180 linhas de ciclo de vida e adicionaria gestão de concorrência
  própria — mais código, não menos. Também exigiria implementar à mão a extensão de
  visibilidade, que o framework oferece.
- *Manter o SDK v2 e apenas recalibrar o visibility timeout.* Resolveria o orçamento de
  retry sem tocar em código, mas deixaria os outros dois problemas intactos.

**Racional:** a única razão documentada para o SDK puro era a incompatibilidade com Boot
4, que não existe mais. O framework entrega por configuração exatamente o que o código
mantém à mão, incluindo extensão de visibilidade e polling com back-pressure (só busca
mensagens quando há capacidade de processamento), que a implementação manual não tem.

### Decisão 2 — Ack permanece condicionado à confirmação do Kafka

**Escolha:** manter o produce síncrono dentro do método do listener, usando
`AcknowledgementMode.ON_SUCCESS` (default do framework): retorno normal → ack, exceção
propagada → mensagem retida.

```
Opção adotada                              Opção rejeitada
─────────────                              ───────────────
método { produce.get(); }                  método { produce(); }  // sem get
  retorna  → framework acka                  retorna  → framework acka
  lança    → mensagem retida                broker rejeita depois → EVENTO PERDIDO

AT-LEAST-ONCE                              AT-MOST-ONCE
duplicata possível, perda impossível       perda silenciosa, sem DLQ, sem log
```

**Alternativas consideradas:**

- *Produce assíncrono com ack no retorno do método.* É mais simples e comum no mercado,
  mas troca uma falha visível e recuperável (mensagem na DLQ) por perda silenciosa de um
  evento de transição de estado de autorização — numa aplicação de pagamentos
  recorrentes, o consumidor a jusante ficaria com estado desatualizado sem nenhum sinal
  no sistema.
- *`AcknowledgementMode.MANUAL` com ack no callback do produce assíncrono.* Entregaria o
  throughput do assíncrono preservando o at-least-once, mas reintroduz gestão de ack
  explícita — justamente o que a Decisão 1 busca eliminar — em troca de um ganho de
  throughput que a Decisão 7 mostra ser desnecessário no volume esperado.

**Racional adicional:** a simplificação do produce assíncrono é menor do que parece. A
serialização Avro e o round-trip ao Schema Registry acontecem **dentro** de
`Producer.send()`, antes do `Future`. Toda a classificação de `SerializationException` em
`KafkaEventoAutorizacaoProducer` continuaria necessária no modo assíncrono; some apenas o
caminho de falha do broker — exatamente o caminho cuja perda queremos evitar.

### Decisão 3 — Ponto único de classificação vira error handler central

**Escolha:** registrar `SqsEventoAutorizacaoErrorInterceptor` como error handler da
`SqsMessageListenerContainerFactory`. O contrato inverte de `boolean tratar(...)` para
engolir (→ ack) ou relançar (→ retenção).

```
HOJE                                    DEPOIS
tratar() → true   → listener acka       engole   → método retorna → ack
tratar() → false  → listener não acka   relança  → exceção sobe   → sem ack
```

**Alternativa considerada:** `try/catch` dentro do método `@SqsListener`, chamando o
interceptor. Rejeitada por reintroduzir no adaptador a decisão de ack que a armadilha 12
do `CLAUDE.md` e a skill `mensageria-sqs-kafka` mandam concentrar num ponto único.

**Ponto de atenção para a implementação:** com o error handler central, a exceção chega a
ele *depois* do método. É preciso garantir que engolir `EventoAutorizacaoInvalidoException`
produza de fato ack, e não um retry silencioso. Errar aqui é invisível até a fila encher —
merece teste de integração dedicado.

### Decisão 4 — Visibility timeout de 60s, declarado no Terraform

**Escolha:** `visibility_timeout_seconds = 60` em `infra/envs/local-messaging/main.tf`,
exposto como variável. O listener **não** define `messageVisibilitySeconds`.

**Racional do valor:** com processamento concorrente, o requisito de dimensionamento passa
a ser o pior caso de **uma** mensagem, não do lote:

```
pior caso de uma mensagem = [Schema Registry: teto explícito ~3s, ver Decisão 6]
                          + max.block.ms          5s
                          + delivery.timeout.ms  15s
                          ≈ 23s

visibility timeout = 60s  →  ~2,5× de margem
```

Sob o processamento serial atual, o requisito honesto seria `10 × 23s = 230s` — outra
evidência de que os 30s implícitos de hoje estão subdimensionados.

**Alternativa considerada:** configurar `messageVisibilitySeconds` no listener. Rejeitada
como fonte da verdade: o visibility timeout é atributo da fila, vale para qualquer
consumidor, e convive com a `redrive_policy` que já mora no Terraform. Duas fontes
configurando o mesmo atributo é divergência silenciosa esperando acontecer.

### Decisão 5 — `maxReceiveCount` de 3 para 10

**Escolha:** elevar o orçamento de retry de ~90s (30s × 3) para ~10min (60s × 10).

**Racional:** o orçamento maior é seguro **porque a classificação de erro é boa**. Uma
mensagem com payload inválido recebe ack na primeira tentativa via interceptor e nunca
consome tentativas. Só falha de infraestrutura as consome — e para essas, tempo adicional
é exatamente o remédio. Os dois mecanismos se sustentam: com classificação ruim,
`maxReceiveCount` alto seria um loop caro; com esta, é ganho puro de resiliência.

A DLQ preserva seu papel: "Kafka fora há mais de 10 minutos" é um incidente que se **quer**
ver materializado, não um erro de calibração.

### Decisão 6 — Teto explícito para o cliente do Schema Registry

**Escolha:** configurar timeouts de conexão e leitura do cliente HTTP do Schema Registry
em `KafkaProducerClientConfig`.

**Racional:** é hoje o único caminho do produce sem teto garantido.

```
producer.send(record) ──┐
                        │ serialização Avro
                        │ + round-trip HTTP ao Schema Registry  ◀── SEM TIMEOUT
                        │ + max.block.ms = 5s
                        └─▶ retorna Future ──▶ .get(20s) ──▶ delivery.timeout 15s

O .get(20s) só conta DEPOIS que send() retorna — não protege contra Registry lento.
```

Na prática o schema é cacheado após a primeira mensagem, então só a primeira paga o
round-trip. Mas num Registry degradado ou logo após um restart de instância, esse caminho
é ilimitado — e qualquer calibração de visibility timeout construída sobre ele é fictícia.

### Decisão 7 — Concorrência inicial no default (10), calibrada por medição

**Escolha:** iniciar com `maxConcurrentMessages = 10` (default do framework) e ajustar por
medição, não por antecipação.

**Correção em relação à exploração inicial**: a hipótese de que essa concorrência
rodaria em virtual threads — usada para justificar o valor durante a fase de
exploração — **não se confirmou** ao inspecionar o código-fonte do
`spring-cloud-aws-sqs` 4.0.0. O mecanismo real:

```
AbstractPipelineMessageListenerContainer.createTaskExecutor():
  ThreadPoolTaskExecutor dimensionado para
    maxConcurrentMessages × numero_de_message_sources  (= 10 × 1, uma fila)
  usando MessageExecutionThreadFactory — cria SEMPRE platform threads

AbstractPipelineMessageListenerContainer.verifyThreadType():
  um componentsTaskExecutor customizado so e aceito se produzir instancias de
  MessageExecutionThread (subclasse de Thread comum) — nao ha combinacao
  suportada de virtual threads com o pipeline de execucao do listener nesta
  versao. Tentar injetar Executors.newVirtualThreadPerTaskExecutor() lanca
  UnsupportedThreadFactoryException.
```

Ou seja: a concorrência vem de um **pool fixo de 10 platform threads** (não virtual),
dimensionado automaticamente pelo próprio `maxConcurrentMessages` — não algo que a
aplicação precise ou consiga trocar por virtual threads nesta versão do framework.

```
throughput por instância ≈ maxConcurrentMessages / latência_do_produce
com latência saudável de 5–20ms:  10 workers → 500–2.000 msg/s por instância
```

**Racional revisado:** 10 threads de plataforma é uma alocação de recurso trivial —
nada a temer em termos de contenção ou de tamanho de pool nesse patamar. O ganho da
migração aqui não é "virtual threads tornam concorrência grátis" (não se aplica dentro
do pipeline do listener), e sim simplesmente sair de concorrência efetiva 1
(processamento serial do lote) para `maxConcurrentMessages` mensagens em voo por vez,
com o dimensionamento do pool delegado ao framework em vez de código próprio.
Calibra-se por throughput observado (tarefa 10.5); subir o valor sobe o pool
proporcionalmente — 100 seria 100 platform threads, ainda barato, mas sem o
racional de "custo de thread bloqueada é quase zero" que valeria para virtual threads.

O `KafkaProducer` continua thread-safe e compartilhado entre as execuções concorrentes:
N workers preenchendo o mesmo `RecordAccumulator` substitui o batch de tamanho 1 do
processamento serial anterior — esse ganho de batching no Kafka é independente do tipo
de thread e permanece válido.

JEP 491 (Java 25, `synchronized` não fixa mais virtual threads) deixa de ser relevante
para *esta* trilha de execução, já que ela não usa virtual threads. Continua relevante
em geral para a JVM da aplicação, apenas sem efeito aqui.

### Decisão 8 — Health indicator sobre `MessageListenerContainerRegistry`

**Escolha:** reescrever `SqsListenerHealthIndicator` consultando o registro de containers.

**Racional:** o requisito original (armadilha 11 do `CLAUDE.md`) é que um outage total de
consumo não passe despercebido — a flag `running` continuava `true` com a thread morta. O
requisito permanece; some apenas a thread própria que o indicador observava.

**Ressalva:** "thread de polling morta" e "container parado" não são obviamente
equivalentes. A equivalência precisa ser verificada antes de assumir paridade de cobertura
(ver Riscos).

### Decisão 9 — Batching de acknowledgement habilitado

**Escolha:** manter o batching de ack do framework (`acknowledgementThreshold` /
`acknowledgementInterval`) com intervalo curto, em vez de desabilitá-lo.

**Trade-off:** o batching alarga a janela entre "Kafka confirmou" e "SQS deletou". Um
crash nessa janela gera reentrega — ou seja, **duplicata**, nunca perda. Como a
deduplicação por key já é contrato explícito do consumidor a jusante, o custo é
absorvido pelo desenho, e o ganho é menos chamadas à API do SQS.

Se a medição mostrar taxa de duplicatas relevante, o ajuste é reduzir o intervalo — não
mudar a semântica.

## Risks / Trade-offs

**[Acknowledgement pode falhar no graceful shutdown]** → **Resolvido — spike concluído.**
O issue [awspring/spring-cloud-aws#925](https://github.com/awspring/spring-cloud-aws/issues/925)
foi fechado em 2024-03-12, e o issue relacionado
[#1029](https://github.com/awspring/spring-cloud-aws/issues/1029) (race condition no
`BatchingAcknowledgementProcessor` que também descartava acks) foi fechado em 2024-02-07 —
ambos mais de um ano antes do release 4.0.0 (nov/2025), portanto incluídos nele.
Mantém-se a configuração explícita de `listenerShutdownTimeout` e
`acknowledgementShutdownTimeout`, e o teste de shutdown gracioso sob carga (tarefa 10.3)
como confirmação empírica — não porque o bug seja esperado, mas porque é o comportamento
mais crítico de todo o adaptador.

**[Health indicator pode perder cobertura]** → **Investigado — cobertura equivalente.**
`MessageListenerContainerRegistry.getListenerContainers()` + `isRunning()` por container é
o padrão documentado pelo próprio projeto para health check via Actuator (discussion #528).
`isRunning()` reflete o estado de ciclo de vida do container, gerenciado internamente pelo
framework — que trata falhas de polling sem matar o container, papel equivalente ao
`catch (Throwable)` do loop manual atual. A cobertura é considerada equivalente à liveness
de thread anterior; se a validação de shutdown (tarefa 6.2) revelar um cenário de container
"em execução" mas parado de buscar mensagens, complementar com métrica de idade da última
mensagem processada.

**[Rename de variáveis de ambiente quebra o deploy]** → **Risco reduzido na
implementação.** `AWS_REGION` continua existindo (agora sob
`spring.cloud.aws.region.static`). `AWS_SQS_QUEUE_URL` **não precisou ser renomeada**:
`io.awspring.cloud.sqs.QueueAttributesResolver` aceita tanto nome quanto URL completa da
fila — detecta o formato pelo prefixo `http` e usa a URL diretamente, sem round-trip de
`GetQueueUrl`. O valor da variável de ambiente é o mesmo de hoje, só migrou de propriedade
custom (`aws.sqs.queue-url`) para `sqs.queue-url` (consumida por `@SqsListener` via
placeholder). `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` de produção não existiam como
variável própria da aplicação (perfil `prod` usa a cadeia padrão de credenciais, ex. task
role do ECS) — nada muda aí. Resta apenas validar em staging que a fila está sendo
consumida, não só que a aplicação subiu — parte da tarefa 10.1.

**[Concorrência sem recalibrar o visibility timeout duplica processamento]** → Elevar
`maxConcurrentMessages` antes de aplicar o Terraform faria mensagens voltarem a ficar
visíveis durante o processamento. Mitigação: aplicar a mudança de infraestrutura antes da
de aplicação (ver Migration Plan) — a ordem não é arbitrária.

**[Autoconfiguração pode não aceitar o endpoint do Floci como o bean manual aceitava]** →
O `SqsClientConfig` atual faz `endpointOverride` + credenciais estáticas condicionalmente.
Mitigação: validar o profile `local` contra o Floci logo no início da implementação; é o
primeiro ponto onde a migração pode travar.

**[Perda de controle fino sobre o loop]** → Trade-off aceito: comportamentos hoje
explícitos (backoff, resiliência a `Throwable`) passam a ser do framework e deixam de ser
ajustáveis no código da aplicação. É o preço de não mantê-los.

## Migration Plan

A ordem importa — a infraestrutura precede a aplicação:

1. **Spikes de verificação** (bloqueiam a implementação): status do issue #925 na 4.0.0;
   equivalência do health indicator; autoconfiguração com endpoint do Floci.
2. **Terraform**: visibility timeout 60s e `maxReceiveCount` 10, aplicados em
   `local-messaging` e refletidos no provisionamento de produção. Aplicar **antes** de
   elevar a concorrência.
3. **Timeouts do Schema Registry**: mudança pequena, independente e sem risco — pode ir
   antes da migração do listener e já melhora o estado atual.
4. **Migração do listener**: dependência, `@SqsListener`, factory, interceptor, health
   indicator, configuração, remoção do código morto.
5. **Documentação**: `CLAUDE.md` e `AGENTS.md` (espelhos), incluindo as armadilhas 5, 6,
   11 e 12.

**Rollback:** os passos 2 e 3 são independentes e não precisam ser revertidos — o
visibility timeout maior e os timeouts do Registry são melhorias válidas para o listener
manual também. O passo 4 é revertido por reversão do commit; como nenhuma classe de
`application/` ou `domain/` é tocada, a superfície de rollback é o adaptador de entrada e
a configuração.

## Open Questions

1. **Qual o volume de pico real da fila?** Decide se `maxConcurrentMessages = 10` sobra
   com folga ou precisa subir. É a única questão que pode alterar um número deste design.
2. **Com que precisão o `arj-contratocommand` grava `data_hora_ultima_atlz`?** A key de
   idempotência é `SHA-256(id_autorizacao + data_hora_ultima_atlz)`. Se a origem gravar
   com precisão de milissegundo ou segundo, duas transições rápidas da mesma autorização
   colidem na key — e o consumidor, deduplicando por ela, descartaria um evento legítimo.
   Não bloqueia esta mudança, mas é um risco que ela torna mais visível.
3. **O tópico `eventos-autorizacao` está configurado com log compaction?** Com key única
   por transição a compactação nunca teria efeito; se estiver ligada, é CPU de broker
   desperdiçada.
4. **Onde vive o provisionamento da fila em produção?** **Investigado**: `infra/envs/prod`
   é hoje um placeholder (`README.md` apenas, "Status: placeholder — sem código Terraform
   ainda"), com escopo descrito restrito a VPC/RDS/ECS — mensageria não está no escopo
   sequer planejado desse root ainda. Não há, portanto, onde refletir a calibração de
   `visibility_timeout_seconds`/`maxReceiveCount` em produção hoje. Fica registrado como
   pendência explícita: quando o Terraform de produção para a mensageria for criado, ele
   precisa nascer com os mesmos valores aplicados em `local-messaging`
   (`sqs_visibility_timeout_seconds = 60`, `sqs_dlq_max_receive_count = 10`), não com os
   defaults do serviço.
