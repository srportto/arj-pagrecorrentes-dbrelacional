## Context

`eventos-consumer` (porta 8083, sem banco, sem REST de negócio) consome o tópico Avro
`eventos-autorizacao` e hoje só loga o evento e comita o offset. Uma exploração
(`/opsx:explore`) confrontou o app com as skills `arquitetura-limpa-java` e
`mensageria-sqs-kafka` deste monorepo e achou quatro desvios da convenção do catálogo:
`AckMode.MANUAL` sem necessidade de negócio, ausência de DLT, listener fora de
`entrypoint/`, e enum de negócio fora de `domain/enums/`. `StatusAutorizacao` e
`TipoEventoAutorizacao` são espelhos manuais de `arj-contratocommand`, com o grafo de
transições exigido pela capability `maquina-estados-autorizacao` — essa capability
explicitamente fixa a localização atual (`application/eventos/`) para as "aplicações de
eventos", então mover de camada aqui é uma mudança de requisito, não só de código.

## Goals / Non-Goals

**Goals:**
- Eliminar `AckMode.MANUAL`/`Acknowledgment` mantendo a mesma garantia observável (offset
  só avança após o log de sucesso).
- Adicionar DLT para mensagens não-processáveis (hoje ficam em retry indefinido).
- Realinhar pacotes com a tabela "Que classe vai em qual camada" de `arquitetura-limpa-java`.
- Trocar a dependência Maven para o starter documentado pela skill de mensageria.

**Non-Goals:**
- Mudar a derivação de `tipoEvento` (continua vindo do `status` do record, não do header
  Kafka) — decisão já tomada e especificada, fora do escopo desta limpeza.
- Tocar `autorizacaostatus-producer` (mesmo desvio de camada `infrastructure/` vs
  `entrypoint/`, mas fica para mudança futura — ver Open Questions).
- Qualquer processamento de negócio novo em `eventos-consumer` — continua sendo log + ack.
- Mudar o schema Avro, o tópico, o group id ou a semântica at-least-once do fluxo.

## Decisions

### D1. `AckMode.RECORD`, não `AckMode.MANUAL`

A skill `mensageria-sqs-kafka` lista "commit manual de offset sem necessidade" como erro
comum: o commit automático do `@KafkaListener` já cobre "processar e confirmar". A
alternativa correta para "log então commit, por registro, só em caso de sucesso" **sem**
`Acknowledgment` manual é `AckMode.RECORD` — commit síncrono por registro,
automaticamente, após o método do listener retornar sem lançar exceção. Elimina o
parâmetro `Acknowledgment` e o `acknowledge()` explícito do listener.

`AckMode.RECORD` é definido explicitamente em `factory.getContainerProperties()` (mesmo
lugar onde `AckMode.MANUAL` estava antes) — **não** via propriedade
`spring.kafka.listener.ack-mode`: o `ConcurrentKafkaListenerContainerFactoryConfigurer`
do Boot (que aplicaria a propriedade automaticamente) só aceita o factory tipado como
`<Object, Object>`, incompatível por invariância de generics com o factory fortemente
tipado `<String, EventoAutorizacao>` já usado aqui. Não valia introduzir um segundo
factory/bean só para contornar isso.

- *Alternativa considerada:* manter `AckMode.MANUAL`. Rejeitada — não há razão de negócio
  documentada para desacoplar processamento de confirmação (a skill reserva `MANUAL` para
  commit em lote ou confirmação assíncrona; aqui é sempre síncrono, 1 log → 1 commit).
- *Alternativa considerada:* `ConcurrentKafkaListenerContainerFactoryConfigurer` +
  `spring.kafka.listener.ack-mode`. Rejeitada nesta mudança pelo motivo de generics acima;
  fica registrada como possível trabalho futuro se o factory for generalizado para
  `<Object, Object>`.

### D2. DLT com `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`

Sem DLT, uma mensagem sempre-falha (ex.: `status` desconhecido em
`TipoEventoAutorizacao.porStatus`) fica em retry indefinido no `DefaultErrorHandler`
padrão do spring-kafka — outro erro comum listado na skill. Adiciona-se:

- `ErrorHandlingDeserializer` envolvendo o `KafkaAvroDeserializer` no
  `value.deserializer` — captura falha de desserialização (Avro/Schema Registry) como
  `DeserializationException` recuperável, em vez de derrubar o listener container.
- Um `KafkaTemplate<String, byte[]>` dedicado (com `ByteArraySerializer`), usado **só**
  pelo `DeadLetterPublishingRecoverer` — publica os bytes crus da mensagem original em
  `eventos-autorizacao.DLT` (convenção do Spring: `<topico>.DLT`), tanto para falha de
  desserialização quanto para exceção de negócio (`TipoEventoAutorizacao.porStatus`
  desconhecido).
- `DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 3))` — 3 tentativas com 1s de
  intervalo antes do DLT, espelhando o exemplo já validado da skill/overlay
  `kafka-consumer` do catálogo.

- *Alternativa considerada:* aumentar o número de tentativas do `DefaultErrorHandler`
  padrão sem DLT. Rejeitada — sem DLT, esgotadas as tentativas o spring-kafka só loga e
  segue (mensagem perdida sem rastro); com DLT, a mensagem fica disponível para
  reprocessamento/investigação manual.

### D3. Listener em `entrypoint/kafka/`, enums em `domain/enums/`

Alinha com a tabela de `arquitetura-limpa-java` (`Listener SQS, consumer Kafka →
entrypoint/`; `Enum de negócio → domain/enums/`) e com o próprio `arj-contratocommand`,
que já segue essa convenção. Introduz a camada `domain/` em `eventos-consumer` pela
primeira vez — hoje o app não tem `domain/` porque não tinha regra de negócio própria;
`StatusAutorizacao`/`TipoEventoAutorizacao` sempre foram regra de negócio (grafo de
transições), só estavam na camada errada.

- *Alternativa considerada:* manter `application/eventos/` (como a capability
  `maquina-estados-autorizacao` fixa hoje). Rejeitada como padrão definitivo — era uma
  decisão pragmática do momento em que o app "não tinha domain/", não uma escolha
  arquitetural deliberada; mantê-la perpetuaria a única inconsistência de camada do
  monorepo em vez de corrigi-la enquanto o app é pequeno.
- `EventoAutorizacaoKafkaListener` vai para `entrypoint/kafka/`; `ProcessarEventoAutorizacaoUseCase`
  permanece em `application/eventos/` (é orquestração, não regra pura).

### D4. `spring-boot-starter-kafka` no lugar de `spring-kafka`

Convenção documentada na skill `mensageria-sqs-kafka`. Sem efeito funcional imediato
(a autoconfiguração do Boot já era alcançável via `spring-kafka` isolado nesta app, pois
`spring-boot-autoconfigure` já está no classpath via outros starters), mas remove a
divergência de dependência e deixa o caminho livre para, no futuro, simplificar
`KafkaConsumerConfig` usando `spring.kafka.consumer.*` em vez de beans manuais (fora do
escopo desta mudança — ver Open Questions).

## Risks / Trade-offs

- [Granularidade de commit muda de "explícito por linha de código" para "implícito via
  `AckMode.RECORD`"] → mitigação: teste de integração garante que uma exceção no
  processamento não avança o offset (mesmo scenario já coberto pelo teste atual do
  listener, sem precisar mockar `Acknowledgment`).
- [Tópico `eventos-autorizacao.DLT` não existe no compose Kafka local] → mitigação: o
  broker local tem auto-create habilitado por padrão para tópicos não explicitamente
  provisionados (diferente do tópico principal, que tem auto-create desabilitado de
  propósito); documentar no `CLAUDE.md` que o `.DLT` é criado sob demanda.
- [Mover pacotes quebra imports em massa em um único commit] → mitigação: mover
  listener + enums + testes atomicamente, rodar `mvn clean test` antes de considerar a
  tarefa concluída.
- [Assimetria com `autorizacaostatus-producer`, que continua em `infrastructure/`, fica
  mais visível] → aceito conscientemente; registrado como trabalho futuro (Open
  Questions), não silenciado.
- [`maquina-estados-autorizacao` hoje fixa `application/eventos/` para "aplicações de
  eventos" (plural — inclui o producer)] → a delta spec desta mudança restringe
  explicitamente a alteração a `eventos-consumer`; `autorizacaostatus-producer` continua
  regido pelo requisito original até uma mudança futura o alinhar também.

## Migration Plan

1. Trocar a dependência Maven (`spring-kafka` → `spring-boot-starter-kafka`) e confirmar
   `mvn clean compile`.
2. Mover `StatusAutorizacao`/`TipoEventoAutorizacao` (+ testes) de `application/eventos/`
   para `domain/enums/`; ajustar imports em `ProcessarEventoAutorizacaoUseCase` e testes.
3. Mover `EventoAutorizacaoKafkaListener` (+ teste) de `infrastructure/kafka/` para
   `entrypoint/kafka/`; ajustar imports.
4. Trocar `AckMode.MANUAL`/`Acknowledgment` por `AckMode.RECORD` em
   `ContainerProperties` (código, não propriedade — ver D1); simplificar
   `EventoAutorizacaoKafkaListener.escutar` (sem o parâmetro `Acknowledgment`).
5. Adicionar `ErrorHandlingDeserializer`, `KafkaTemplate<String, byte[]>` do DLT e o bean
   `DefaultErrorHandler` com `DeadLetterPublishingRecoverer`.
6. Atualizar `CLAUDE.md`/`AGENTS.md` de `eventos-consumer` (mapa de pacotes, fluxo de
   consumo, seção de DLT).
7. `mvn clean test` — todos os testes (existentes + novos de DLT) devem passar.

**Rollback:** mudança contida em `apps/eventos-consumer` (sem infraestrutura nova além do
tópico `.DLT`, auto-criado); reverter o commit é suficiente.

## Open Questions

- Estender o mesmo tratamento (`entrypoint/`+`domain/`, `AckMode.RECORD`, DLT) para
  `autorizacaostatus-producer` numa mudança futura, para eliminar a assimetria entre os
  dois apps Kafka do monorepo?
- 3 tentativas / 1s de backoff antes do DLT (espelhando o overlay `kafka-consumer` do
  catálogo) é adequado para este fluxo, ou o volume/latência esperados pedem outro valor?
- Vale uma mudança futura separada para simplificar `KafkaConsumerConfig` usando
  `spring.kafka.consumer.*`/`spring.kafka.consumer.properties.*` em vez de
  `ConsumerFactory`/`ContainerFactory` manuais, agora que a dependência já é o starter?
