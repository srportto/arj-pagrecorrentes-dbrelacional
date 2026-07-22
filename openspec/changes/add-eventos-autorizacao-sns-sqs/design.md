# Design: add-eventos-autorizacao-sns-sqs

## Context

O `arj-contratocommand` persiste autorizações na tabela particionada `autorizacoes` em
dois casos de uso: `CriarAutorizacaoUseCase` (um `save`) e `CancelarAutorizacaoUseCase`
(um `save` de update ou, quando muda a partição de expurgo, `deleteById` + `flush` +
`save` dentro da mesma transação). O monorepo já tem um emulador AWS local (Floci em
`http://localhost:4566`) e um root Terraform `infra/envs/local` que provisiona
VPC/ECS — aplicá-lo sobe containers, o que está fora do escopo desta fase.

Stack relevante: Spring Boot 4.0.7, Java 25 (virtual threads), Jackson 3
(`tools.jackson`), MapStruct, arquitetura hexagonal em 4 camadas
(`entrypoint/application/domain/shared`).

## Goals / Non-Goals

**Goals:**

- Todo estado final persistido de uma autorização vira um evento JSON no tópico SNS
  `sns-estados-autorizacao`, somente após o commit da transação.
- Fila `SQS-eventos-autorizacao` assinada no tópico recebe o JSON puro (sem envelope
  SNS).
- Nova app `autorizacaostatus-producer` consome a fila, loga a entidade e dá ack.
- Tudo roda local: apps via `mvn spring-boot:run`, recursos AWS no Floci via Terraform
  em root próprio.

**Non-Goals:**

- Garantia de entrega exactly-once ou outbox pattern (dual-write aceito nesta fase).
- Processamento de negócio no consumidor (apenas log + ack).
- Execução dockerizada das aplicações ou provisionamento em ECS.
- DLQ, retry policy customizada, FIFO, deduplicação e versionamento de schema do
  evento.

## Decisions

### D1 — Gatilho de publicação: evento de domínio + `@TransactionalEventListener(AFTER_COMMIT)`

Os use cases publicam um evento interno (`ApplicationEventPublisher`) ao final do
`execute()`; um componente `@TransactionalEventListener(phase = AFTER_COMMIT)` faz o
publish no SNS.

- **Por quê**: publica só depois do commit (rollback não gera evento); emite **1 evento
  lógico por operação** com o estado final — o cancelamento com troca de partição faz
  DELETE+INSERT físicos que não devem virar dois eventos; mantém o adapter SNS fora dos
  use cases (porta de saída, hexagonal).
- **Alternativas descartadas**:
  - *JPA `@EntityListeners` (`@PostPersist`/`@PostUpdate`)*: dispara antes do commit
    (evento de transação que pode sofrer rollback) e a troca de partição geraria par
    REMOVE+INSERT espúrio.
  - *Publish inline no use case*: acopla o SNS à transação (latência dentro do
    `@Transactional`) e publica antes do commit.

### D2 — Payload: representação exata da tabela, chaves = nomes das colunas

O corpo da mensagem é um JSON cujas chaves são os nomes das colunas de `autorizacoes`
(`id_autorizacao`, `id_particao_conta`, `data_fim_vigencia`, `tipo_produto` (código
persistido), `status`, `motivo_status`, ..., colunas embutidas de cancelamento quando
preenchidas, `metadados` como objeto JSON). Um record dedicado
(`AutorizacaoEventoPayload` ou similar, serializado com Jackson 3) faz o mapeamento
explícito entidade→colunas — sem serializar a entidade JPA diretamente.

- **Por quê**: "exata representação da tabela" é o contrato pedido; record dedicado
  evita acoplar o contrato do evento a detalhes do Hibernate (lazy, embeddables) e a
  renomeações de campos Java.
- O tipo da operação (`CRIACAO`/`CANCELAMENTO`) vai em **message attribute** SNS
  (`tipoEvento`), mantendo o body como representação pura da linha.

### D3 — AWS SDK v2 puro, sem Spring Cloud AWS

`software.amazon.awssdk:sns` no command e `software.amazon.awssdk:sqs` no consumidor
(via `software.amazon.awssdk:bom`).

- **Por quê**: Spring Cloud AWS (`io.awspring.cloud`) historicamente atrasa o suporte a
  majors novos do Spring Boot; a base está em Boot 4.0.7. O SDK puro elimina o risco e
  o custo é baixo (publish é uma chamada; o consumo é um loop de long polling).
- **Alternativa descartada**: `@SqsListener` do awspring — conveniente, mas
  compatibilidade com Boot 4 não comprovada.

### D4 — Subscription com `raw_message_delivery = true`

A subscription SNS→SQS entrega o corpo cru: o body na fila é exatamente o JSON da
entidade, sem envelope (`Type`, `TopicArn`, `Message`).

- **Por quê**: consumidor trivial (não precisa desembrulhar), e o contrato do evento
  fica idêntico nas duas pontas.

### D5 — Root Terraform separado: `infra/envs/local-messaging/`

Novo root com provider AWS apontado para o Floci (mesmo padrão de
`infra/envs/local/providers.tf`: endpoints em `http://localhost:4566`, credenciais
`test`/`test`, `skip_*`, state local), contendo apenas `aws_sns_topic`,
`aws_sqs_queue` e `aws_sns_topic_subscription` (+ `aws_sqs_queue_policy` permitindo
`sns.amazonaws.com` fazer `SendMessage` — mesmo em emulador, mantém fidelidade com AWS
real).

- **Por quê**: `terraform apply` em `envs/local` sobe ECS/containers; um root de
  mensageria isolado aplica em segundos e não interfere no ambiente ECS.
- **Alternativas descartadas**: `messaging.tf` dentro de `envs/local` com apply via
  `-target` (fluxo frágil e não idiomático); criação via AWS CLI (fora do padrão IaC do
  repo).

### D6 — Consumidor: loop de long polling em virtual thread

Na `autorizacaostatus-producer`, um componente de ciclo de vida (`SmartLifecycle`)
inicia uma virtual thread (Java 25) com o loop: `ReceiveMessage` (long polling,
`WaitTimeSeconds=20`, `MaxNumberOfMessages=10`) → loga sucesso com o body (a
representação da entidade) → `DeleteMessage` (ack). Erros de processamento não dão ack
(a mensagem volta após o visibility timeout — semântica at-least-once).

- **Por quê**: long polling é o idioma SQS (evita busy-wait), virtual thread elimina
  gestão de pool, `SmartLifecycle` dá shutdown limpo no stop do Spring.

### D7 — Estrutura da nova app `apps/autorizacaostatus-producer`

Baseada na `arj-contratocommand` e no modelo de
`docs/arquitetura/based-java-aplication.md`, enxuta:

```
entrypoint/    → (sem controllers de negócio; actuator health apenas)
application/   → ProcessarEventoAutorizacaoUseCase (loga o evento recebido)
domain/        → record do payload do evento (espelho das colunas)
shared/        → exceções/config comuns ao modelo
infrastructure → adapter SQS (loop de consumo) + config do SqsClient
```

Pacote `br.com.srportto.autorizacaostatusproducer`, porta **8082**, sem
`spring-boot-starter-data-jpa`/PostgreSQL, profiles `local` (defaults do Floci) e
`prod`. Dockerfile multi-stage incluído para cumprir a regra do monorepo (não usado
nesta fase).

### D8 — Configuração de endpoint/credenciais

Propriedades próprias (`aws.endpoint`, `aws.region`, `aws.sns.topic-arn` /
`aws.sqs.queue-url`) com defaults no profile `local`:

- endpoint `http://localhost:4566`, região `us-east-1`, credenciais estáticas
  `test`/`test`;
- ARN/URL determinísticos no Floci (conta default `000000000000`):
  `arn:aws:sns:us-east-1:000000000000:sns-estados-autorizacao` e
  `http://localhost:4566/000000000000/SQS-eventos-autorizacao`;
- no profile `prod`, tudo vem de variável de ambiente, sem default de emulador.

## Risks / Trade-offs

- **[Dual-write sem outbox]** Commit OK + SNS fora do ar ⇒ evento perdido. → Mitigação:
  log de erro explícito no listener AFTER_COMMIT; outbox documentado como evolução
  futura.
- **[AFTER_COMMIT roda na thread da requisição]** Latência do publish não afeta a
  transação, mas afeta a resposta HTTP. → Aceito nesta fase (Floci local, latência
  ~ms); se incomodar, `@Async` sobre o listener é o próximo passo.
- **[Contrato acoplado ao schema]** Alterações de coluna em `autorizacoes` mudam o
  evento implicitamente. → Mitigação: record dedicado torna a mudança explícita em
  code review; versionamento de schema fica como evolução.
- **[SQS at-least-once]** Mensagem pode ser entregue/logada mais de uma vez. → Aceito:
  o consumidor apenas loga; idempotência real fica para quando houver processamento.
- **[Nome da fila com maiúsculas]** `SQS-eventos-autorizacao` é válido em SQS, mas
  destoa da convenção kebab-minúscula. → Mantido conforme pedido; apenas registrado.
- **[Floci indisponível no startup do consumidor]** Loop de polling falharia em
  sequência. → Mitigação: backoff simples entre erros de `ReceiveMessage` e log claro.

## Migration Plan

1. `docker compose -f infra/local/floci/compose.yaml up -d` (pré-requisito).
2. `terraform init && terraform apply` em `infra/envs/local-messaging/` (cria tópico,
   fila, subscription).
3. Subir `arj-contratocommand` (`mvn spring-boot:run`, profile `local`) — publica
   eventos; sem o passo 2, publish falha com erro logado, API continua funcionando.
4. Subir `autorizacaostatus-producer` (`mvn spring-boot:run`, porta 8082) — consome e
   loga.
5. Rollback: `terraform destroy` no root de mensageria; reverter os commits das apps —
   nenhuma migração de dados envolvida.

## Open Questions

- Nenhuma bloqueante. (Futuras evoluções já mapeadas como non-goals: outbox, DLQ,
  versionamento do evento, processamento real no consumidor.)
