# Proposal: add-maquina-estados-autorizacao

## Why

O ciclo de vida de uma autorização tem 8 estados com transições bem definidas (diagrama da máquina de estados), mas hoje esse conhecimento não existe no código: o enum `StatusAutorizacao` (presente só em `contratocommand` e `contratoquery`) lista os estados sem as transições, as apps de eventos (`autorizacaostatus-producer`, `eventos-consumer`) enxergam o status como `int` cru, e o `tipoEvento` publicado na cadeia SNS → SQS → Kafka é fixado manualmente no ponto de chamada (`CRIACAO`/`CANCELAMENTO`) em vez de derivar do status — duas fontes de verdade que podem divergir.

## What Changes

- **Máquina de estados nas 4 apps**: `StatusAutorizacao` passa a carregar o grafo de transições (RECEBIDA → PENDENTE_ACEITE/EM_PROCESSO_ATIVACAO/REJEITADA; PENDENTE_ACEITE → EM_PROCESSO_ATIVACAO/REJEITADA/EXPIRADA; EM_PROCESSO_ATIVACAO → ATIVA/REJEITADA/EXPIRADA; ATIVA → CANCELADA/FINALIZADA/REJEITADA; demais estados terminais) com método de validação `podeTransicionarPara(destino)`. Evolui os enums existentes em `contratocommand`/`contratoquery` e cria cópias em `autorizacaostatus-producer`/`eventos-consumer`.
- **BREAKING** — **`TipoEventoAutorizacao` expandido e derivado do status**: de `{CRIACAO, CANCELAMENTO}` para 8 valores 1:1 com os estados (`RECEPCAO`, `PENDENCIA_ACEITE`, `INICIO_ATIVACAO`, `ATIVACAO`, `CANCELAMENTO`, `REJEICAO`, `EXPIRACAO`, `FINALIZACAO`), com fábrica `porStatus(status)`. O evento de criação passa a sair com attribute `tipoEvento=ATIVACAO` (antes `CRIACAO`) — mudança de contrato do message attribute SNS/header Kafka.
- **Status como fonte de verdade na propagação**: cada salto deriva o tipo do campo `status` que já viaja no dado — o publisher SNS deriva do status da entidade (o campo `tipo` do evento interno `AutorizacaoPersistidaEvent` é removido), a ponte SQS→Kafka deriva o header do `status` do payload (deixa de repassar o attribute SQS), o consumer deriva do `status` do Avro para logar. Attribute/header continuam sendo enviados por valor operacional de filtragem.
- **Schemas Avro movidos para `resources`**: `EventoAutorizacao.avsc` sai de `src/main/avro/` para `src/main/resources/avro/` nas duas apps Kafka, com ajuste do `sourceDirectory` do `avro-maven-plugin`.
- **Comentários enxutos no código Java**: javadocs e comentários de linha extensos são resumidos (alvos principais: `ReversibleUUIDv7`, `AutorizacaoRepository`, javadocs de classe das apps de eventos). Docs (`CLAUDE.md`, `AGENTS.md`, `README.md`) não são resumidas — apenas atualizadas onde citam caminhos/contratos alterados.

## Capabilities

### New Capabilities

- `maquina-estados-autorizacao`: enum `StatusAutorizacao` com grafo de transições e validação `podeTransicionarPara`, replicado nas 4 apps do monorepo; enum `TipoEventoAutorizacao` com 8 valores mapeados 1:1 aos estados via `porStatus(status)`.

### Modified Capabilities

- `publicacao-eventos-autorizacao`: o message attribute `tipoEvento` passa a ser derivado do `status` persistido (8 valores possíveis) em vez de informado pelo use case (`CRIACAO`/`CANCELAMENTO`); criação publica `ATIVACAO`.
- `consumo-eventos-autorizacao`: o listener SQS deixa de solicitar/repassar o message attribute `tipoEvento` — a ponte deriva o tipo do campo `status` do payload.
- `publicacao-eventos-kafka`: o header Kafka `tipoEvento` passa a ser derivado do campo `status` do payload (sempre presente), não mais repassado do attribute SQS.
- `consumo-eventos-kafka`: o log de consumo passa a registrar o tipo de evento derivado do `status` do record Avro (enum), não mais o header recebido.
- `higiene-comentarios-codigo`: novo requirement de concisão — javadocs de classe/método limitados ao essencial (1–3 linhas), detalhes operacionais vivem nas docs da app.

## Impact

- **`apps/contratocommand`**: `StatusAutorizacao` (transições), `TipoEventoAutorizacao` (8 valores + `porStatus`), `AutorizacaoPersistidaEvent` (remove campo `tipo`), `AutorizacaoEventoPublisher` (deriva attribute), `CriarAutorizacaoUseCase`/`CancelarAutorizacaoUseCase` (simplificados), comentários em `ReversibleUUIDv7`/`AutorizacaoRepository`, testes correspondentes.
- **`apps/contratoquery`**: `StatusAutorizacao` (transições) + testes.
- **`apps/autorizacaostatus-producer`**: novos enums, `SqsEventoAutorizacaoListener` (para de ler attribute), `ProcessarEventoAutorizacaoUseCase`/`KafkaEventoAutorizacaoProducer` (header derivado), pom (`sourceDirectory`), move do `.avsc`, testes.
- **`apps/eventos-consumer`**: novos enums, `EventoAutorizacaoKafkaListener`/`ProcessarEventoAutorizacaoUseCase` (log derivado), pom (`sourceDirectory`), move do `.avsc`, testes.
- **Contrato de mensageria**: valor do attribute/header `tipoEvento` muda (`CRIACAO` → `ATIVACAO` + 6 valores novos possíveis). Consumidores atuais só logam — risco baixo, mas é breaking para qualquer filter policy futura baseada em `CRIACAO`.
- **Docs espelho** (`CLAUDE.md`/`AGENTS.md`/`README.md` das apps afetadas): atualização pontual de caminhos (`src/main/avro` → `src/main/resources/avro`) e do contrato do `tipoEvento`.
