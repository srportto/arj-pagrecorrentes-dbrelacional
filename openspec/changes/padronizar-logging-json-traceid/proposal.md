## Why

Uma auditoria de logging nas 5 aplicações do monorepo (contratocommand, contratoquery,
autorizacaostatus-producer, eventos-consumer, temporiza-autorizacao), contra o padrão definido pela
skill `padrao-de-logs-java`, encontrou conformidade em quase todos os itens — SLF4J puro, sem
concatenação, sem dado sensível, sem log duplicado, domínio limpo de logging, handler central único —
exceto dois: nenhuma app emite log em JSON estruturado e nenhuma correlaciona linhas de log de uma
mesma requisição/mensagem via `traceId`. Sem JSON estruturado, ferramentas de observabilidade e
filtragem por campo (`jq`, query de log) não funcionam — hoje só existe grep frágil sobre texto livre.
Sem `traceId` correlacionando via MDC, não há como juntar as linhas de log de uma mesma requisição ou
mensagem processada, o que atrasa investigação de incidente.

## What Changes

- Habilitar `logging.structured.format.console: logstash` no `application.yaml` das 5 aplicações, em
  todo profile (incluindo `local`), sem dependência nova (suporte nativo do Spring Boot 4).
- Adicionar um `TraceIdFilter` (`infrastructure/web/`) em `contratocommand` e `contratoquery` —
  aplicações com endpoint HTTP — populando `traceId` no MDC a partir do cabeçalho `X-Trace-Id` recebido
  ou gerando um novo via `UUID.randomUUID()`, sempre limpando o MDC no `finally`.
- Popular `traceId` manualmente no MDC (`MDC.put`/`MDC.clear` no `finally`) no início do processamento
  de cada mensagem nos listeners de `autorizacaostatus-producer` (SQS), `temporiza-autorizacao`
  (SQS/stream) e `eventos-consumer` (Kafka) — reaproveitando um `traceId` já presente no atributo/header
  da mensagem quando existir, gerando um novo caso contrário.
- Atualizar `CLAUDE.md`/`AGENTS.md` (idênticos) de cada uma das 5 apps documentando o novo
  comportamento de log estruturado e a origem do `traceId` em cada uma.

## Capabilities

### New Capabilities
- `padronizacao-logging`: define o padrão de log estruturado (JSON) e de correlação via `traceId`/MDC
  exigido nas 5 aplicações do monorepo, alinhado à skill `padrao-de-logs-java`.

### Modified Capabilities

(nenhuma — não há capability existente cobrindo logging/observabilidade nas specs principais)

## Impact

- Código afetado: `application.yaml` das 5 apps; novo `TraceIdFilter` em `contratocommand` e
  `contratoquery`; listeners de mensageria em `autorizacaostatus-producer`, `temporiza-autorizacao` e
  `eventos-consumer`; `CLAUDE.md`/`AGENTS.md` das 5 apps.
- Sem dependência nova (suporte a log estruturado é nativo do Spring Boot 4; MDC é nativo do SLF4J).
- Sem mudança de contrato de API ou de schema de evento — é puramente observabilidade/logging.
- Formato de log muda de texto para JSON em todo ambiente, inclusive `local` — quem lê log local
  interativamente deve usar `| jq .` (documentado na skill `padrao-de-logs-java`).
