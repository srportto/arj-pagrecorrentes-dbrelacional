## Why

Ambas as aplicações já rodam Java 25 e Spring Boot 4.0.4, mas nenhuma habilita Virtual Threads. As threads virtuais do Java 25 permitem que operações bloqueantes de I/O (consultas ao banco, chamadas HTTP) suspendam o virtual thread sem bloquear uma platform thread do SO, aumentando significativamente o throughput sob carga concorrente sem aumentar infraestrutura.

## What Changes

- Habilitar `spring.threads.virtual.enabled: true` em ambos os `application.yaml` (command e query)
- Ajustar `maximum-pool-size` do Hikari para aproveitar melhor a concorrência virtual sem provocar contenção no pool de conexões
- Documentar benchmark de before/after para evidenciar melhora de throughput (requests/sec e latência P99 sob carga)

## Capabilities

### New Capabilities

- `virtual-threads-config`: Configuração de Virtual Threads do Java 25 via propriedade Spring Boot — habilita threads virtuais para o container web (Tomcat/Jetty) e para tarefas I/O-bound do JPA/Hikari em ambas as aplicações.

### Modified Capabilities

<!-- Nenhuma especificação de comportamento de API ou domínio é alterada. Mudança é exclusivamente de infraestrutura de execução. -->

## Impact

- `aplicacoes/contratocommand/src/main/resources/application.yaml` — adicionar propriedade virtual threads + ajuste de pool
- `aplicacoes/contratoquery/src/main/resources/application.yaml` — idem
- Nenhum código Java precisa ser alterado; não há mudança de contrato de API
- Hikari: comportamento de aquisição de conexão muda; virtual threads "estacionam" ao aguardar conexão (ao invés de bloquear platform thread), mas Hikari ainda pode causar pinning em versões antigas — documentado em design.md
