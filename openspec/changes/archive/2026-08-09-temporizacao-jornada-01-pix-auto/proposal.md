## Why

Autorizações `PIX_AUTO` da jornada 1 nascem com status `RECEBIDA` e **hoje ficam nesse
estado para sempre** — não existe endpoint para o cliente pagador aprovar ou rejeitar, nem
mecanismo que encerre a espera. A regra de negócio dá ao cliente **10 minutos** para
decidir; passado o prazo sem resposta, a autorização deve ser rejeitada sistemicamente.

Faltam as três peças: a rota de decisão no `contratocommand`, um temporizador
distribuído que dispare a expiração no vencimento, e o dado que permite ao temporizador
selecionar **só** as autorizações da jornada 1 — a jornada não é persistida hoje, ela é
descartada após derivar o `motivo_status`, e some da base assim que a autorização muda de
status.

## What Changes

- **Rota nova no `contratocommand`**: `PATCH /api/autorizacoes/{idAutorizacao}/decisao`
  com ação `APROVAR` (→ `ATIVA`), `REJEITAR` (→ `REJEITADA`, motivo `REJEITADA_PAGADOR`) ou
  `EXPIRAR` (→ `REJEITADA`, motivo novo `REJEITADA_SISTEMA_TIMEOUT_J1`). Toda transição é
  validada contra o grafo existente de `StatusAutorizacao` — **o grafo não muda**, porque
  `RECEBIDA → REJEITADA` e `RECEBIDA → EM_PROCESSO_ATIVACAO → ATIVA` já existem.
- **Jornada passa a ser persistida** em coluna própria `tipo_jornada` na tabela
  `autorizacoes`, deixando de ser recuperável apenas por leitura reversa de `motivo_status`
  (que é sobrescrito na primeira transição de status).
- **Evento SNS ganha dois message attributes**: `tipoProduto` e `tipoJornada`, ambos com
  contrapartida no body — mantendo o invariante "attribute sempre coerente com o body". O
  payload JSON e o schema Avro ganham `tipo_jornada` (nullable, compatível para trás).
- **Aplicação nova `temporiza-autorizacao`** (porta 8084), hexagonal, **sem banco de dados**:
  consome uma fila SQS filtrada pelo SNS (`RECEPCAO` + `PIX_AUTO` + `SPI_J1`), agenda a
  expiração no Valkey e, no vencimento, aciona a rota de decisão do command.
- **Agendamento no Valkey** por sorted set (relógio) + stream com consumer group (fila de
  trabalho): `ZADD` na recepção, varredura atômica em Lua que faz `ZREM`+`XADD` no
  vencimento, `XREADGROUP`/`XACK` no worker e `XAUTOCLAIM` para recuperar PEL de pod morto.
  Persistência AOF. **Redis/Valkey não entrega entrada de stream com TTL** — o sorted set é
  o que supre o atraso, sem abrir mão de ack, redelivery e durabilidade.
- **Infra**: fila `SQS-temporizacao-autorizacao` + DLQ + subscription com filter policy no
  root `infra/envs/local-messaging/`; Valkey local em `infra/local/redis/` (diretório já
  existe, vazio); módulo Terraform de ElastiCache Valkey para AWS.
- **Documentação**: README raiz, READMEs de infra e os pares `CLAUDE.md`/`AGENTS.md`
  (espelhos) das aplicações afetadas, mais o guia da aplicação nova.

Não é breaking: a coluna e o campo do evento são nullable, os attributes são aditivos, e
nenhuma subscription ou consumidor existente muda de comportamento.

## Capabilities

### New Capabilities

- `decisao-autorizacao`: rota de decisão no `contratocommand` (aprovar/rejeitar/expirar),
  transições resultantes, motivos gravados, idempotência e contrato de erro que o chamador
  automatizado usa para decidir entre confirmar e reprocessar.
- `temporizacao-jornada-01`: a aplicação `temporiza-autorizacao` — consumo da fila filtrada,
  cálculo do vencimento a partir de `data_hora_inclusao`, acionamento da expiração e
  classificação de falha (confirma vs. retém).
- `agendamento-expiracao-valkey`: o contrato do mecanismo de agendamento e entrega no
  Valkey — sorted set como relógio, stream com consumer group como fila de trabalho,
  garantias de at-least-once, recuperação de PEL órfão e persistência.
- `local-valkey-environment`: Valkey local em `infra/local/redis/`, no mesmo padrão de
  `local-kafka-environment` e `local-messaging-environment`.
- `aws-elasticache-valkey`: módulo Terraform do cluster ElastiCache Valkey para AWS, no
  mesmo padrão dos módulos existentes em `infra/modules/`.

### Modified Capabilities

- `publicacao-eventos-autorizacao`: payload ganha `tipo_jornada`; message attributes
  `tipoProduto` e `tipoJornada` passam a acompanhar `tipoEvento`; a decisão passa a ser uma
  terceira origem de publicação, além de criação e cancelamento.
- `motivo-status-por-jornada`: a jornada passa a ser persistida em coluna própria, além de
  continuar derivando o `motivo_status` na criação; o enum `MotivoStatusAutorizacao` ganha
  `REJEITADA_SISTEMA_TIMEOUT_J1`.
- `local-messaging-environment`: novo par fila/DLQ e a primeira subscription do tópico com
  filter policy.
- `monorepo-organization`: o monorepo passa de quatro para cinco aplicações sob `apps/`.

## Impact

**Código**: `contratocommand` (controller, use case + rules de decisão, entidade,
mapper, enums de motivo, payload e publisher de evento); `contratoquery` (espelho da
coluna); `autorizacaostatus-producer` e `eventos-consumer` (espelho do payload e do
`.avsc`); aplicação nova `apps/temporiza-autorizacao`.

**Schema**: coluna `tipo_jornada` em `autorizacoes` (nullable / default para linhas
legadas), replicada nas entidades JPA das duas apps que mapeiam a tabela.

**Infra**: `infra/envs/local-messaging/` (fila, DLQ, subscription filtrada),
`infra/local/redis/` (compose do Valkey), `infra/modules/` (ElastiCache Valkey),
`infra/envs/prod/`.

**Dependências**: cliente Redis/Valkey (Lettuce, via `spring-boot-starter-data-redis`) e
cliente HTTP na aplicação nova. Porta 8084 passa a ser ocupada.

**Operação**: novo ponto de falha entre o vencimento e a expiração efetiva — a fila SQS
tem DLQ e o stream tem PEL, mas uma indisponibilidade prolongada do `contratocommand`
atrasa expirações sem perdê-las.

**Dívida conhecida, fora deste escopo**: estados terminais `REJEITADA`, `EXPIRADA` e
`FINALIZADA` não são transferidos para a partição de expurgo (só `CANCELADA` é), então
autorizações rejeitadas por esta mudança permanecem indefinidamente na partição de
vigência. Endereçar em proposta própria (`expurgo-estados-terminais`).
