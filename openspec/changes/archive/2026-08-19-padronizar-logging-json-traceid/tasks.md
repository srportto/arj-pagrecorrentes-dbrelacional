## 1. JSON estruturado nas 5 apps

- [x] 1.1 Adicionar `logging.structured.format.console: logstash` ao `application.yaml` de
      `contratocommand`, ativo em todo profile (default, `local`, demais)
- [x] 1.2 Adicionar `logging.structured.format.console: logstash` ao `application.yaml` de
      `contratoquery`, ativo em todo profile
- [x] 1.3 Adicionar `logging.structured.format.console: logstash` ao `application.yaml` de
      `autorizacaostatus-producer`, ativo em todo profile
- [x] 1.4 Adicionar `logging.structured.format.console: logstash` ao `application.yaml` de
      `eventos-consumer`, ativo em todo profile
- [x] 1.5 Adicionar `logging.structured.format.console: logstash` ao `application.yaml` de
      `temporiza-autorizacao`, ativo em todo profile
- [x] 1.6 Subir cada app localmente (`mvn spring-boot:run` ou `docker compose up`) e confirmar que o
      console emite JSON válido com `@timestamp`/`level`/`logger_name`/`message`

## 2. traceId via MDC em contratocommand e contratoquery

- [x] 2.1 Criar `TraceIdFilter` (`@Component implements Filter`) em
      `contratocommand/infrastructure/web/`, lendo `X-Trace-Id` ou gerando `UUID.randomUUID()`,
      populando `MDC.put("traceId", ...)` e limpando com `MDC.clear()` no `finally`
- [x] 2.2 Criar `TraceIdFilter` equivalente em `contratoquery/infrastructure/web/`
- [x] 2.3 Teste de integração em cada app: requisição sem `X-Trace-Id` gera um `traceId` presente em
      todas as linhas de log da requisição; requisição com `X-Trace-Id` reaproveita o valor recebido
- [x] 2.4 Teste confirmando que duas requisições consecutivas na mesma thread do pool não compartilham
      `traceId` (MDC limpo entre uma e outra)

## 3. traceId via MDC nos listeners de mensageria

- [x] 3.1 Popular `traceId` no início do processamento em
      `autorizacaostatus-producer/infrastructure/messaging/SqsEventoAutorizacaoListener` — gera `UUID`
      por mensagem (a fila não carrega hoje um atributo de correlação reaproveitável sem mudar a
      assinatura chamada diretamente pelos testes unitários existentes); limpa o MDC no `finally`
- [x] 3.2 Popular `traceId` em `temporiza-autorizacao` nos dois pontos de entrada de mensagem:
      `TemporizacaoEventoListener` (SQS — o entrypoint real desta app, gera `UUID` por mensagem) e
      `ExpiracaoStreamListener` (Valkey stream — reaproveita o `streamId`, identificador único da
      entrada, como `traceId`); cobre também o reprocessamento via `PendenciasSchedulerReivindicador`,
      que chama o mesmo método `processarEConfirmarSeConcluido` do segundo
- [x] 3.3 Popular `traceId` no início do processamento em
      `eventos-consumer/infrastructure/messaging/EventoAutorizacaoKafkaListener` — gera `UUID` por
      registro (mesmo critério do item 3.1); limpa o MDC no `finally`
- [x] 3.4 Teste por app confirmando que todas as linhas de log de uma mensagem processada carregam o
      mesmo `traceId` e que o MDC é limpo após sucesso e após falha

## 4. Documentação

- [x] 4.1 Atualizar `CLAUDE.md`/`AGENTS.md` (idênticos) de cada uma das 5 apps: log agora sai em JSON
      estruturado em todo profile, com nota sobre `| jq .` para leitura legível em dev local
- [x] 4.2 Documentar em `CLAUDE.md`/`AGENTS.md` de cada app a origem do `traceId` (header `X-Trace-Id`
      para as HTTP; atributo/header da mensagem para as de mensageria) e o ponto exato onde é
      populado/limpo

## 5. Verificação final

- [x] 5.1 `mvn clean verify` em cada um dos 5 módulos com a infra correspondente no ar — suíte verde,
      sem regressão
- [x] 5.2 Subir o ambiente local completo (`docker compose up -d --build` na raiz) e confirmar as 5
      apps `healthy`
- [x] 5.3 Smoke test manual: disparar uma requisição HTTP em `contratocommand` (criação de autorização)
      e acompanhar o mesmo `traceId` propagando pelas linhas de log da mesma requisição — confirmado
      via `docker logs ... | grep traceId`: as duas linhas (`log.info` de início e sucesso do
      `CriarAutorizacaoService`) carregam `"traceId":"smoke-test-trace-final1"`, reaproveitado do
      header `X-Trace-Id` enviado
