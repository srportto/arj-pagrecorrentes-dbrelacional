## Context

As 5 aplicações do monorepo (`contratocommand`, `contratoquery`, `autorizacaostatus-producer`,
`eventos-consumer`, `temporiza-autorizacao`) emitem log em texto livre, sem correlação entre linhas de
uma mesma requisição/mensagem. A skill `padrao-de-logs-java` já documenta o padrão-alvo (JSON via
`logging.structured.format.console: logstash`, nativo do Spring Boot 4; correlação via MDC/`traceId`)
mas nenhuma app o aplica hoje. `contratocommand` e `contratoquery` expõem endpoint HTTP; as outras três
são acionadas por mensageria (SQS: `autorizacaostatus-producer`, `temporiza-autorizacao`; Kafka:
`eventos-consumer`) e não passam por filtro de servlet.

## Goals / Non-Goals

**Goals:**
- Log em JSON estruturado em todo profile (inclusive `local`) nas 5 apps, sem dependência nova.
- `traceId` correlacionando todas as linhas de log de uma mesma requisição HTTP ou mensagem processada,
  reaproveitado quando já existir na origem (header HTTP ou atributo/header da mensagem) e gerado novo
  caso contrário.
- MDC sempre limpo no `finally`, para não vazar entre requisições/mensagens que reaproveitam thread de
  pool.

**Non-Goals:**
- Propagação de `traceId` entre serviços via header nas chamadas SNS→SQS→Kafka feitas pelas próprias
  apps (`autorizacaostatus-producer` publica no Kafka o payload vindo do SQS) — a propagação ponta a
  ponta entre apps fica fora deste change; cada app gera/recebe `traceId` no seu próprio ponto de
  entrada.
- Alterar formato de payload de negócio (JSON/Avro dos eventos) — a mudança é só na saída de log.
- Adicionar biblioteca de log (`logstash-logback-encoder` ou similar); o suporte a `logging.structured`
  é nativo do Spring Boot 4.

## Decisions

- **JSON via propriedade nativa do Spring Boot 4, não `logback-spring.xml` customizado.**
  `logging.structured.format.console: logstash` no `application.yaml`, sem appender customizado.
  Alternativa descartada: `logstash-logback-encoder` — dependência extra sem necessidade, já que o
  suporte nativo cobre o caso de uso.
- **`TraceIdFilter` como `@Component implements Filter` em `contratocommand` e `contratoquery`**,
  seguindo exatamente o exemplo da skill `padrao-de-logs-java` (cabeçalho `X-Trace-Id`, gera
  `UUID.randomUUID()` se ausente, `MDC.clear()` no `finally`). Fica em `infrastructure/web/` de cada
  app, junto aos demais adapters HTTP.
- **MDC manual no início de cada listener de mensageria**, não um `Aspect`/interceptor genérico:
  - `autorizacaostatus-producer` (`SqsEventoAutorizacaoListener`): `traceId` lido de um atributo da
    mensagem SQS se presente, senão gerado.
  - `temporiza-autorizacao` (listener de stream/SQS existente): mesmo critério.
  - `eventos-consumer` (`@KafkaListener`): `traceId` lido de um header do `ConsumerRecord` se presente,
    senão gerado.
  Alternativa descartada: interceptor central único reutilizável entre as 3 apps — não há módulo
  compartilhado no monorepo (ver `CLAUDE.md` raiz, "Schemas são espelhados manualmente"); a mesma
  convenção de duplicação controlada se aplica aqui, o custo de manter 3 blocos pequenos e idênticos é
  menor que criar acoplamento entre apps independentes.
- **Nenhum profile foge do JSON estruturado**, inclusive `local`. A skill documenta a limitação
  conhecida do Spring Boot (não dá para desligar `logging.structured.format.console` por profile de
  forma suportada) e a alternativa de um `logback-spring.xml` com `<springProfile name="log-humano">`
  — fora do escopo deste change; quem quiser log legível em dev usa `| jq .`, já documentado na skill.

## Risks / Trade-offs

- [Log em JSON quebra hábito de leitura direta no console em dev] → Mitigar documentando
  `mvn spring-boot:run | jq .` no `CLAUDE.md` de cada app (já documentado na skill
  `padrao-de-logs-java`, replicar a menção).
- [`traceId` gerado por app, sem propagação entre serviços] → Aceito como Non-Goal explícito; cada
  ponto de entrada (HTTP ou mensageria) já resolve correlação *dentro* da própria app, que é o problema
  reportado na auditoria. Propagação ponta a ponta fica para change futuro se a necessidade aparecer.
- [MDC não limpo vaza `traceId` entre requisições/mensagens no mesmo pool de thread] → Mitigado por
  `finally` obrigatório em todo ponto que faz `MDC.put`, cobrado nas tasks de implementação e no
  critério de scenario da spec.

## Migration Plan

- Mudança aditiva de configuração e um filtro/populamento de MDC por app — sem migração de dado, sem
  mudança de contrato de API/evento. Deploy app a app, na ordem que for conveniente; nenhuma depende
  das outras.
- Rollback: reverter o commit da app específica (config + filtro/listener) — não há estado persistido
  novo a desfazer.
