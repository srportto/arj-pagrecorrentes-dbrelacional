---
name: monitoramento-java
description: Use ao configurar monitoramento, implementar logging estruturado, criar dashboards Prometheus/Grafana, definir alerting rules, instrumentar distributed tracing com OpenTelemetry, ou diagnosticar problemas de produção usando logs/métricas/traces. Gatilhos - "monitoramento", "observabilidade", "Prometheus", "Grafana", "OpenTelemetry", "tracing distribuído", "alert", "MTTR".
---

# Monitoramento de Aplicações Java

## Visão geral

Guia de observabilidade para aplicações Java/Spring Boot: as **três pillars** (logs estruturados,
métricas, tracing distribuído) e como instrumentá-las no stack deste catálogo (Spring Boot 4,
Micrometer, OpenTelemetry, Prometheus, Grafana). Use esta skill ao planejar a observabilidade de um
serviço novo, ao adicionar métricas, ao investigar um incidente, ou ao configurar alertas.

**Quando NÃO usar:** para o **padrão de logging** (formato JSON, MDC, o que logar por camada), use
`padrao-de-logs-java` — esta skill é o "vizinho de cima" que mostra como exportar e consultar os
logs em uma stack de observabilidade, mas o formato e o que logar vivem na outra skill. Para o lado
de **correlação ponta a ponta entre microsserviços** (correlation ID middleware, propagação de
`X-Trace-Id`), use `arquitetura-limpa-java` (seção resiliência). Para tuning do banco, use
`banco-de-dados-performance`.

## Workflow de instrumentação

1. **Avalie o que precisa de monitoramento** — SLIs (Service Level Indicators) do serviço, caminhos
   críticos, métricas de negócio (não só técnicas).
2. **Instrumente** — adicione logs, métricas e traces (ver exemplos abaixo).
3. **Colete** — configure agregação e storage (Prometheus scrape, log shipper, endpoint OTLP);
   **valide que o dado está chegando antes de prosseguir**.
4. **Visualize** — monte dashboards usando RED (Rate/Errors/Duration) para serviços user-facing ou
   USE (Utilization/Saturation/Errors) para recursos.
5. **Alerte** — defina alertas por threshold e por anomalia em caminhos críticos; **valide que não
   há inundação de falsos positivos antes de sair para produção**.

---

# Logs estruturados (resumo)

Já cobertos em detalhe na skill `padrao-de-logs-java`. Em uma stack de observabilidade o que muda
é **para onde os logs vão**:

```yaml
# application.yaml — Spring Boot 3.4+ (e Boot 4): JSON nativo
logging:
  structured:
    format:
      console: logstash   # ou "ecs" para Elastic Common Schema
```

Saída típica (1 linha JSON por evento, com MDC `traceId` automático):

```json
{
  "timestamp": "2026-01-29T10:15:30.123Z",
  "level": "INFO",
  "logger": "com.example.OrderService",
  "message": "Order created",
  "requestId": "req-abc123",
  "traceId": "trace-xyz",
  "orderId": 12345,
  "userId": "user-789",
  "duration_ms": 45,
  "step": "payment_completed"
}
```

**Não confunda "campo no JSON" com "placeholder no log".** O que vira campo JSON de verdade é o
que está no MDC (ver `padrao-de-logs-java`, seção 4) — placeholders `{}` viram texto do campo
`message`, não campos separados.

---

# Métricas com Micrometer + Prometheus

Spring Boot 4 já traz `spring-boot-starter-actuator` + Micrometer; expõe `/actuator/prometheus`
automaticamente ao adicionar `micrometer-registry-prometheus`. As métricas mais importantes para
um serviço HTTP são as **RED**:

- **R**ate — requests/segundo por endpoint
- **E**rrors — ratio de erros por endpoint
- **D**uration — latência (p50, p95, p99)

## Instrumentação custom

```java
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Service
public class PedidoService {

    private final Counter pedidosCriados;
    private final Timer processamentoTimer;

    public PedidoService(MeterRegistry registry) {
        this.pedidosCriados = Counter.builder("pedidos_criados_total")
                .description("Total de pedidos criados com sucesso")
                .register(registry);
        this.processamentoTimer = Timer.builder("pedido_processamento")
                .description("Latência do processamento de pedido")
                .publishPercentileHistogram()
                .register(registry);
    }

    public Pedido criar(Pedido pedido) {
        return processamentoTimer.record(() -> {
            Pedido criado = salvar(pedido);
            pedidosCriados.increment();
            return criado;
        });
    }
}
```

## Endpoint de scrape

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus, metrics
  endpoint:
    prometheus:
      enabled: true
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
```

Prometheus scrape config (exemplo):

```yaml
scrape_configs:
  - job_name: 'app-pedidos'
    metrics_path: /actuator/prometheus
    scrape_interval: 15s
    static_configs:
      - targets: ['app-pedidos:8080']
```

## Tipos de métrica — quando usar cada um

| Tipo | Quando usar | Exemplo |
|---|---|---|
| **Counter** | Valor que só cresce (total de eventos) | `pedidos_criados_total`, `erros_total` |
| **Gauge** | Valor que oscila (tamanho atual, valor instantâneo) | `pedidos_em_processamento`, `conexoes_ativas` |
| **Histogram / Summary** | Distribuição de valores (latência, tamanho) | `pedido_processamento_seconds` (com p50/p95/p99) |
| **Timer** | Caso especial de Histogram para duração | Tempo de uma operação |

**Erro comum:** usar **Gauge** para algo que deveria ser **Counter**. Contadores são a base de
todas as agregações por `rate()` no PromQL — usar Gauge para "total de eventos" quebra o `rate()`.

---

# Tracing distribuído com OpenTelemetry

Tracing distribuído segue uma requisição **fim a fim** entre microsserviços, mostrando onde o
tempo foi gasto e em qual serviço. Cada **span** é uma unidade de trabalho nomeada com timestamps
de início/fim; um **trace** é uma árvore de spans com o mesmo `traceId`.

## Dependências

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

## Configuração

```yaml
management:
  tracing:
    sampling:
      probability: 1.0   # em dev/staging: 100% dos traces
  otlp:
    tracing:
      endpoint: http://otel-collector:4318/v1/traces
```

Em produção, **abaixe a sampling probability** (0.1 = 10%) para não estourar o storage; o resto
fica nos logs via correlation ID (`traceId` propagado no header, ver
`arquitetura-limpa-java` seção DDD).

## Spans customizados em código

```java
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.Span;

@Service
public class PedidoService {

    private final Tracer tracer;

    public PedidoService(Tracer tracer) {
        this.tracer = tracer;
    }

    public Pedido criar(Pedido pedido) {
        Span span = tracer.nextSpan().name("pedido.criar").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            span.tag("pedido.id", pedido.id().toString());
            Pedido criado = salvar(pedido);
            span.event("pedido.salvo");
            return criado;
        } catch (Exception e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
```

## Propagação de contexto (entre microsserviços)

O Micrometer Tracing + OpenTelemetry injeta automaticamente o `traceparent` header (W3C Trace
Context) nas requisições HTTP de saída via `RestClient`/`WebClient`. Garanta:

- Cliente HTTP suportado: `RestClient` (Boot 4) ou `WebClient`.
- Baggage (campos extras que viajam com o trace, ex.: `userId`) configurado em
  `management.tracing.baggage.remote-fields`.

---

# Alerting rules (Prometheus)

```yaml
groups:
  - name: app-pedidos.rules
    rules:
      - alert: HighErrorRate
        expr: |
          rate(http_server_requests_seconds_count{application="pedidos", status=~"5.."}[5m])
          / rate(http_server_requests_seconds_count{application="pedidos"}[5m]) > 0.05
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Error rate acima de 5% em {{ $labels.uri }}"
          description: "Mais de 5% de erros 5xx por 2 minutos consecutivos em {{ $labels.uri }}"

      - alert: HighP99Latency
        expr: |
          histogram_quantile(0.99,
            sum(rate(http_server_requests_seconds_bucket{application="pedidos"}[5m])) by (le, uri)
          ) > 1.0
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "p99 acima de 1s em {{ $labels.uri }}"
```

---

# Dashboards

## RED method (serviços user-facing)

Para cada endpoint / serviço:
- **R**ate — `rate(http_server_requests_seconds_count[1m])`
- **E**rrors — `rate(http_server_requests_seconds_count{status=~"5.."}[1m]) / rate(_count[1m])`
- **D**uration — `histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))`

## USE method (recursos / infra)

Para cada recurso (CPU, memória, disco, pool de conexões):
- **U**tilization — % em uso
- **S**aturation — fila / espera
- **E**rrors — eventos de erro

---

# Health & readiness probes

Separar liveness (o processo está vivo?) de readiness (pode receber tráfego?):

```java
// Liveness — simples, sem dependências
@Bean
public HealthIndicator liveness() {
    return () -> Health.up().build();
}

// Readiness — verifica dependências críticas (DB, broker)
@Bean
public HealthIndicator databaseReadiness(DataSource ds) {
    return () -> {
        try (Connection c = ds.getConnection()) {
            return c.isValid(1) ? Health.up().build() : Health.down().build();
        } catch (SQLException e) {
            return Health.down(e).build();
        }
    };
}
```

Spring Boot expõe automaticamente `/actuator/health/liveness` e `/actuator/health/readiness`
quando os beans estão presentes. Veja a config do Kubernetes em
`arquitetura-limpa-java` (seção "Health & readiness probe").

---

# Constraints

## MUST DO
- Use logs estruturados (JSON) — texto livre é parseável, mas estruturado é **filtrável**.
- Inclua `traceId` em logs, métricas e traces para correlação.
- Configure alertas em caminhos críticos (latência, taxa de erro, saturação).
- Monitore **métricas de negócio**, não só técnicas (`pedidos_criados_total` > `jvm_memory_used`).
- Use o tipo de métrica correto (counter/gauge/histogram/timer).
- Implemente endpoints de health check (liveness **e** readiness separados).
- Propague `traceparent` entre microsserviços (W3C Trace Context).

## MUST NOT DO
- Logar dados sensíveis (senhas, tokens, PII) — ver `padrao-de-logs-java`.
- Alertar em todo erro (alert fatigue) — defina threshold + `for` duration para evitar flapping.
- Usar Gauge onde Counter é o correto (quebra `rate()` no PromQL).
- Pular correlation ID em sistemas distribuídos.
- Definir sampling em 100% em produção (estoura storage e custo) — use 1-10% e rely em logs
  estruturados para o resto.
- Misturar dashboards RED e USE sem critério — defina por serviço qual faz sentido.

## Quem aplica o quê

| Situação | Quem | Skill |
|---|---|---|
| Adicionar métrica Micrometer em código | session principal | esta skill |
| Configurar stack Prometheus + Grafana + OTel Collector | session principal | esta skill |
| Padronizar formato de log + MDC | session principal | `padrao-de-logs-java` |
| Auditar instrumentação existente de um serviço | agent `java-especialista` | esta skill + `padrao-de-logs-java` |
| Definir/alertas de SLO | session principal | esta skill |
