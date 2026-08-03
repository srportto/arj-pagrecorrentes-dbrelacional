## Why

O adaptador de entrada da `autorizacaostatus-producer` mantém à mão (~180 linhas) o que
o Spring Cloud AWS resolve por configuração: loop de long polling, ciclo de vida via
`SmartLifecycle`, `join` com timeout calibrado contra o `timeout-per-shutdown-phase`,
backoff, rede de segurança contra `Throwable` e health indicator de liveness de thread.
Essa escolha foi deliberada e está registrada na spec `consumo-eventos-autorizacao`
("sem Spring Cloud AWS") — mas a restrição que a motivava caiu: o **Spring Cloud AWS
4.0.0 é compatível com Spring Boot 4.x / Spring Framework 7.x**.

Além do custo de manutenção, a implementação manual carrega dois limites operacionais
que só aparecem sob estresse:

1. **Orçamento de retry de ~90 segundos.** A fila usa o visibility timeout default
   (30s — não há `visibility_timeout_seconds` em `infra/envs/local-messaging/main.tf`)
   com `maxReceiveCount = 3`. Uma indisponibilidade de Kafka superior a ~90s envia a
   fila inteira para a DLQ.
2. **Processamento serial do lote.** `pollOnce()` recebe até 10 mensagens e as processa
   uma a uma na mesma virtual thread. A concorrência efetiva por instância é 1, e o
   produce síncrono vira um batch Kafka de tamanho 1 por evento — o pior aproveitamento
   possível do broker. A virtual thread existente é decorativa: uma única thread
   bloqueada não se beneficia do modelo.

## What Changes

- Substituir `SqsEventoAutorizacaoListener` (`SmartLifecycle` + virtual thread + loop
  manual) por um método `@SqsListener` do Spring Cloud AWS 4.0.0, com container
  configurado por `SqsMessageListenerContainerFactory`.
- **MANTER a semântica at-least-once**: o ack continua condicionado à confirmação do
  broker Kafka. O produce permanece síncrono dentro do método do listener — retorno
  normal gera ack, exceção propagada retém a mensagem. Ack cego (produce assíncrono com
  ack no retorno) foi **avaliado e rejeitado**: trocaria falha visível e recuperável
  (DLQ) por perda silenciosa de evento de transição de estado de autorização.
- **MANTER o ponto único de classificação de erro**, migrando o mecanismo: o contrato de
  `SqsEventoAutorizacaoErrorInterceptor` deixa de ser `boolean tratar(...)` e passa a ser
  engolir (→ ack) ou relançar (→ sem ack), registrado como error handler central da
  factory. Nenhum `catch` por tipo de exceção volta para o método do listener.
- Elevar a concorrência por instância de 1 para `maxConcurrentMessages` (partindo do
  default 10) — o framework passa a dimensionar automaticamente um pool de threads
  proporcional a esse valor, eliminando o processamento serial do lote. (Nota: o pool é
  de platform threads, não virtual — ver Decisão 7 do `design.md`.)
- **Recalibrar o orçamento de retry**: `visibility_timeout_seconds` de 30s para 60s e
  `maxReceiveCount` de 3 para 10, elevando a tolerância a indisponibilidade de Kafka de
  ~90s para ~10min. É seguro porque payload inválido recebe ack na primeira tentativa
  via interceptor e nunca consome o orçamento.
- **Fechar o único caminho de produce sem teto de tempo**: configurar explicitamente os
  timeouts HTTP do cliente do Schema Registry. Hoje a serialização Avro e o round-trip ao
  Registry ocorrem dentro de `Producer.send()`, antes do `Future`, e não são cobertos por
  `max.block.ms` nem pelo `get(20s)`.
- Substituir `SqsListenerHealthIndicator` (liveness de thread) por indicador equivalente
  sobre o `MessageListenerContainerRegistry`, preservando o requisito de que consumidor
  morto não seja reportado como saudável.
- Remover `SqsClientConfig` e `AwsProperties` em favor da autoconfiguração
  `spring.cloud.aws.*` (incluindo endpoint override do Floci no profile `local`).

Nada em `application/`, `domain/` ou no adaptador de saída Kafka é alterado — a troca é
restrita ao adaptador de entrada, como o modelo hexagonal prevê.

## Capabilities

### New Capabilities

Nenhuma. A mudança reescreve o mecanismo de capacidades já especificadas.

### Modified Capabilities

- `consumo-eventos-autorizacao`: o requisito de consumo deixa de exigir AWS SDK v2 puro,
  loop manual em `SmartLifecycle` e virtual thread própria, passando a exigir
  `@SqsListener` do Spring Cloud AWS com processamento concorrente; o requisito de
  encerramento gracioso passa a ser satisfeito pelos timeouts de shutdown do container; o
  requisito de health-check passa a refletir o estado do container em vez da liveness de
  uma thread. A garantia at-least-once e a proibição de logar o body permanecem
  inalteradas.
- `publicacao-eventos-kafka`: o requisito "Produce síncrono com timeouts abaixo do
  visibility timeout" passa a referenciar o visibility timeout recalibrado (60s) e a
  exigir teto de tempo explícito para o round-trip ao Schema Registry.
- `local-messaging-environment`: o requisito da fila SQS passa a fixar
  `visibility_timeout_seconds` e a declarar explicitamente a DLQ e o `maxReceiveCount`
  recalibrado — hoje a `redrive_policy` existe no Terraform mas não está coberta por
  nenhum requisito da spec.

## Impact

**Código da aplicação** (`apps/autorizacaostatus-producer`):
- Removidos: `entrypoint/sqs/SqsEventoAutorizacaoListener`, `shared/config/SqsClientConfig`,
  `shared/config/AwsProperties`.
- Alterados: `entrypoint/sqs/SqsEventoAutorizacaoErrorInterceptor` (contrato inverte),
  `entrypoint/sqs/SqsListenerHealthIndicator` (fonte de liveness),
  `shared/config/KafkaProducerClientConfig` (timeouts do Schema Registry).
- Novos: método `@SqsListener` em `entrypoint/sqs/`, configuração da
  `SqsMessageListenerContainerFactory` em `shared/config/`.
- **Não alterados**: `ProcessarEventoAutorizacaoUseCase`, `AutorizacaoEventoPayloadValidator`,
  `EventoAutorizacaoConverter`, `IdempotenciaKeyGenerator`, `PublicadorEventoAutorizacao`,
  `KafkaEventoAutorizacaoProducer`, `domain/enums/`. Os testes dessas classes seguem
  válidos sem edição.

**Dependências** (`pom.xml`): adicionar `io.awspring.cloud:spring-cloud-aws-starter-sqs`
(BOM 4.0.0); remover `software.amazon.awssdk:sqs` direto e o BOM do AWS SDK se não
restar outro uso.

**Configuração**: `application.yaml` (remoção do `timeout-per-shutdown-phase` calibrado à
mão), `application-local.yaml` e `application-prod.yaml` (bloco `aws:` → `spring.cloud.aws:`
+ `sqs.queue-url`, propriedade própria da aplicação consumida pelo `@SqsListener`). As
variáveis de ambiente de produção (`AWS_REGION`, `AWS_SQS_QUEUE_URL`) **não mudam de
nome nem de valor** — `QueueAttributesResolver` do Spring Cloud AWS aceita a URL completa
da fila diretamente, sem exigir o nome isolado.

**Infraestrutura**: `infra/envs/local-messaging/main.tf` e `variables.tf` (visibility
timeout e `maxReceiveCount`). A mesma calibração precisa ser refletida no provisionamento
de produção.

**Documentação**: `apps/autorizacaostatus-producer/CLAUDE.md` e `AGENTS.md` (espelhos) —
seções "Comece por aqui", "Arquitetura", "Fluxo de consumo → produção" e as armadilhas
5, 6, 11 e 12 descrevem o listener manual e ficam desatualizadas.

**Riscos que exigem investigação antes da implementação**:
- Issue [awspring/spring-cloud-aws#925](https://github.com/awspring/spring-cloud-aws/issues/925)
  reporta falha de acknowledgement em graceful shutdown. É exatamente a garantia que o
  `join()` manual atual entrega. Pior caso conhecido é duplicata (aceitável no modelo
  at-least-once), não perda — mas precisa ser confirmado na 4.0.0.
- A equivalência entre "thread de polling morta" (indicador atual) e "container parado"
  (indicador novo) não é óbvia e precisa ser verificada.

**Fora de escopo, registrado como follow-up**: o campo `data_hora_ultima_atlz` é
`local-timestamp-micros` (sem fuso) no `EventoAutorizacao.avsc`. Com a decisão de que a
ordenação dos eventos é responsabilidade do consumidor a jusante, ordenando por esse
timestamp, o campo passou a carregar responsabilidade para a qual seu tipo não foi
escolhido. Migrar para `timestamp-micros` exige coordenação com os espelhos manuais do
`.avsc` em `apps/eventos-consumer` e do payload em `arj-contratocommand` — change própria.
