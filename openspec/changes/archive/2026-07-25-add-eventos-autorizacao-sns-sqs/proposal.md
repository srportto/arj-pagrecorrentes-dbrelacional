# Proposta: add-eventos-autorizacao-sns-sqs

## Why

Hoje as mudanças de estado das autorizações (criação e cancelamento) ficam restritas ao
banco relacional do `contratocommand` — nenhum outro componente consegue reagir a
elas sem consultar o banco. Publicar cada persistência como evento em SNS/SQS (emulados
no Floci) cria a fundação de mensageria do monorepo e habilita consumidores
desacoplados, começando por um listener que comprova o fluxo ponta a ponta localmente.

## What Changes

- Novo root Terraform `infra/envs/local-messaging/` (isolado do `envs/local` para não
  subir ECS junto) provisionando no Floci:
  - Tópico SNS `sns-estados-autorizacao`;
  - Fila SQS `SQS-eventos-autorizacao`;
  - Subscription SNS→SQS com `raw_message_delivery = true` (body da fila = JSON puro do
    evento, sem envelope SNS).
- `contratocommand` passa a publicar no SNS, **após o commit** da transação
  (`ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)`), um JSON
  com a exata representação da linha da tabela `autorizacoes` (chaves = nomes das
  colunas), a cada persistência: 1 evento lógico por operação (criação e cancelamento,
  sempre com o estado final da linha — a troca de partição no cancelamento não gera
  eventos físicos intermediários).
- Integração AWS via **AWS SDK v2 puro** (`SnsClient`/`SqsClient`) — sem Spring Cloud
  AWS, por risco de incompatibilidade com Spring Boot 4.0.7.
- Nova aplicação `apps/autorizacaostatus-producer`, baseada na `contratocommand`
  (hexagonal, Spring Boot 4.0.7, Java 25, porta 8082, sem JPA/Postgres/endpoints REST de
  negócio), que consome `SQS-eventos-autorizacao` via polling com `SqsClient`, loga o
  consumo com sucesso incluindo a representação da entidade e dá ack
  (`DeleteMessage`).
- Execução local sem Docker para as aplicações (`mvn spring-boot:run`); apenas o Floci
  roda em container (`http://localhost:4566`, credenciais `test`/`test`, `us-east-1`).
- Sem outbox pattern nesta fase: se o publish falhar após o commit, o evento se perde
  (trade-off aceito e documentado no design).

## Capabilities

### New Capabilities

- `local-messaging-environment`: root Terraform `infra/envs/local-messaging` que
  provisiona SNS + SQS + subscription no Floci, com provider apontado para o emulador e
  state local, aplicável de forma independente do ambiente ECS.
- `publicacao-eventos-autorizacao`: publicação pelo `contratocommand` de um evento
  JSON com a representação exata da tabela `autorizacoes` no tópico
  `sns-estados-autorizacao` após cada persistência confirmada (criação e cancelamento).
- `consumo-eventos-autorizacao`: aplicação `autorizacaostatus-producer` que consome a
  fila `SQS-eventos-autorizacao`, loga o consumo com a representação da entidade e
  confirma (ack) a mensagem.

### Modified Capabilities

- `monorepo-organization`: o cenário "Aplicações vivem sob apps/" passa a incluir
  `autorizacaostatus-producer/` além de `contratocommand/` e `contratoquery/`
  (a nova app segue as mesmas regras já existentes: Dockerfile multi-stage
  Fargate-ready, profiles Spring, README próprio).

## Impact

- **Código**: `contratocommand` ganha um evento de domínio interno, um listener
  AFTER_COMMIT e um adapter SNS (porta de saída na hexagonal); nova app
  `apps/autorizacaostatus-producer` criada a partir do modelo arquitetural de
  `docs/arquitetura/based-java-aplication.md`.
- **Dependências**: AWS SDK v2 (`software.amazon.awssdk:sns` no command,
  `software.amazon.awssdk:sqs` na nova app) via BOM.
- **Infra**: novo root Terraform `infra/envs/local-messaging/` (não altera
  `infra/envs/local`); Floci precisa estar no ar para apply e para as aplicações.
- **Runtime local**: command precisa de novas variáveis/props de endpoint AWS
  (`http://localhost:4566`) com defaults no profile `local`; nova app roda na porta
  8082.
- **Sem impacto** em `contratoquery`, endpoints REST existentes e schema do banco.
