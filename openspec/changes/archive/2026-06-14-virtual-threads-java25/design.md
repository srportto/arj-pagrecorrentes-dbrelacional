## Context

**Estado atual:**
- Java 25 já configurado em ambos os `pom.xml` (`<java.version>25</java.version>`)
- Spring Boot 4.0.4 — suporte nativo a Virtual Threads via `spring.threads.virtual.enabled`
- `contratocommand`: servidor Tomcat (padrão Spring MVC)
- `contratoquery`: servidor Jetty (Tomcat excluído explicitamente no `pom.xml`)
- Hikari pool: `maximum-pool-size: 5`, `minimum-idle: 2` em ambas as apps
- Nenhuma thread pool customizada (`@Async`, `ThreadPoolTaskExecutor`) existe no código-fonte

**Como Virtual Threads funcionam aqui:**
Com `spring.threads.virtual.enabled: true`, o Spring Boot 4 automaticamente:
1. Configura o container web (Tomcat/Jetty) para usar Virtual Threads por requisição
2. Substitui o executor padrão do Spring MVC por um que cria Virtual Threads
3. Operações JDBC/JPA que bloqueiam (aguardar DB) suspendem o virtual thread, liberando a carrier thread do SO

## Goals / Non-Goals

**Goals:**
- Habilitar Virtual Threads em ambas as aplicações com uma propriedade de configuração
- Ajustar pool Hikari para comportamento ótimo com Virtual Threads
- Documentar o procedimento de benchmark para evidenciar melhora de throughput

**Non-Goals:**
- Reescrever código assíncrono (`@Async`, `CompletableFuture`) — desnecessário com Virtual Threads
- Mudar servidor web (Jetty continuará na query app)
- Adicionar `ThreadPoolTaskExecutor` customizado
- Implementar cache ou outras otimizações orthogonais

## Decisions

### D1 — Uma propriedade habilita tudo
`spring.threads.virtual.enabled: true` é a única mudança necessária. Não há `@Bean VirtualThreadPerTaskExecutor` manual pois o Spring Boot 4 auto-configura tudo.

**Alternativa considerada:** criar manualmente um `TomcatProtocolHandlerCustomizer` que injetasse `VirtualThreadPerTaskExecutor`. Rejeitado: a propriedade da Spring Boot é a forma oficial e mais simples.

### D2 — Hikari pool size: manter pequeno (5–10)
Com Virtual Threads, o número de threads aguardando conexão não é mais limitado pela platform thread pool. Isso pode gerar **thundering herd** no pool Hikari se o pool for muito grande (centenas de VTs tentando adquirir conexão simultaneamente). Manter o pool em `5–10` por instância é a recomendação oficial da HikariCP para Virtual Threads.

**Ajuste**: elevar de 5 → 10 para aproveitar melhor concorrência sem saturar o PostgreSQL.

**Alternativa considerada:** reduzir para 2–3. Rejeitado: muito conservador para a carga esperada.

### D3 — Pinning do Hikari
Versões do HikariCP anteriores à 6.x usam `synchronized` blocos que causam **pinning** de Virtual Threads (a VT não consegue desmontar da carrier thread enquanto aguarda o lock). Spring Boot 4.0.4 usa HikariCP 6.x que substituiu `synchronized` por `ReentrantLock`, mitigando o pinning.

**Verificação**: em runtime, `-Djdk.tracePinnedThreads=short` loga pinning. Se detectado com a versão atual, workaround é usar pgBouncer (pool externo).

### D4 — Benchmark de before/after
Para demonstrar melhora de throughput, a abordagem recomendada é um teste de carga HTTP com [Apache Benchmark](https://httpd.apache.org/docs/current/programs/ab.html) (`ab`) ou [k6](https://k6.io/):

```bash
# Exemplo ab — 500 requests, concorrência 50
ab -n 500 -c 50 http://localhost:8080/actuator/health
```

Comparar:
- **Requests/sec** (maior = melhor)
- **P50 / P99 latency** (menor = melhor)
- **Thread count** via JVM MXBean ou JConsole (deve cair significativamente)

## Risks / Trade-offs

| Risco | Mitigação |
|---|---|
| Pinning do Hikari (versão antiga) | Spring Boot 4 usa HikariCP 6.x com `ReentrantLock` — verificar se pinning ocorre com `-Djdk.tracePinnedThreads=short` |
| Thundering herd no pool de conexões | Manter pool pequeño (10 max); se necessário, adicionar `connectionTimeout` mais alto |
| Jetty + Virtual Threads na query app | Spring Boot 4 + Jetty 12 suportam Virtual Threads; testado e documentado na [documentação Spring Boot](https://docs.spring.io/spring-boot/reference/web/servlet.html#web.servlet.embedded-container.thread-model) |
| Carrier thread starvation | Não esperado pois JPA/JDBC são as únicas operações bloqueantes e HikariCP 6 não pina |

## Migration Plan

1. Adicionar `spring.threads.virtual.enabled: true` em `application.yaml` de ambas as apps
2. Ajustar `maximum-pool-size: 10` no Hikari de ambas as apps
3. Buildar e rodar testes: `mvn verify`
4. Executar benchmark com/sem a propriedade para evidenciar melhora
5. **Rollback**: remover a propriedade; nenhuma migração de dados necessária

## Benchmark — Como medir a melhora de performance

### Setup
Ambas as aplicações precisam estar rodando com banco de dados disponível. Execute o comando abaixo **antes** (sem VT, comentando `spring.threads.virtual.enabled: true`) e **depois** (com VT) para comparar.

### Comando de carga (Apache Benchmark)
```bash
# Query app — endpoint de leitura real (substituir pelo ID de uma autorização existente)
ab -n 1000 -c 50 -k http://localhost:8081/actuator/health

# Para um endpoint de negócio com banco real:
ab -n 500 -c 50 -H "Authorization: Bearer <token>" http://localhost:8081/api/autorizacoes
```

### Métricas a registrar

| Métrica | Sem VT (Platform Threads) | Com VT (Virtual Threads) | Melhora esperada |
|---|---|---|---|
| Requests/sec | ~ baseline | ~ 2–4× maior | +100–300% |
| P50 latência (ms) | ~ baseline | ~ similar ou menor | redução leve |
| P99 latência (ms) | alta sob carga | significativamente menor | -40 a -70% |
| Threads JVM ativas | alta (1 por req) | baixa (VTs são leves) | -80% threads SO |

### Por que a melhora acontece

```
SEM Virtual Threads:
  req1 → platform thread 1 [BLOQUEADA aguardando DB] → thread do SO parada
  req2 → platform thread 2 [BLOQUEADA aguardando DB] → thread do SO parada
  req50 → fila (sem threads disponíveis) → latência aumenta

COM Virtual Threads:
  req1 → virtual thread 1 [aguarda DB] → ESTACIONA, libera carrier thread
  req2 → virtual thread 2 [aguarda DB] → ESTACIONA, libera carrier thread
  req50 → virtual thread 50 → iniciada imediatamente
  carrier thread → continua servindo outras VTs → CPU nunca ociosa esperando I/O
```

Sob carga concorrente I/O-bound (que é o caso dessas apps: JPA + PostgreSQL), as Virtual Threads eliminam o gargalo de platform threads bloqueadas, resultando em throughput muito maior com a mesma infraestrutura.

### Verificar VTs ativas em runtime (JVM Flight Recorder)
```bash
# Adicionar ao startup da JVM para ver VTs pinadas (deve ser vazio com HikariCP 6.x):
-Djdk.tracePinnedThreads=short
```

## Open Questions

- Após habilitar VT, monitorar se `idle-timeout` e `max-lifetime` do Hikari precisam de ajuste para o novo padrão de uso de conexões (mais conexões retornadas rapidamente)
- Avaliar se o `maximum-pool-size` de 10 é suficiente para produção ou se deve ser configurável via env var (já suportado: `${DB_POOL_MAX_SIZE:10}`)
