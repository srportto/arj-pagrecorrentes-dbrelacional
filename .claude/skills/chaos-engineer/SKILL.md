---

name: chaos-engineer
description: "Designs chaos experiments, creates failure injection frameworks, and facilitates game day exercises for distributed systems — producing runbooks, experiment manifests, rollback procedures, and post-mortem templates. Use when designing chaos experiments, implementing failure injection frameworks, or conducting game day exercises. Invoke for resilience testing, blast radius control, game days, antifragile systems, fault injection, Chaos Monkey, Litmus Chaos, toxiproxy. Uso: agent `engenheiro-chaos` ou invocação manual via `/chaos-engineer`; não deve ser carregada proativamente pela sessão principal."
license: MIT
metadata:
  author: https://github.com/srportto/srportto
  co-author: https://github.com/Jeffallan/claude-skills
  version: "1.1.0"
  domain: devops
  triggers: chaos engineering, resilience testing, failure injection, game day, blast radius, chaos experiment, fault injection, Chaos Monkey, Litmus Chaos, antifragile
  role: specialist
  scope: implementation
  output-format: code
  related-skills: devops-cicd, cloud-architect, monitoramento-java, design-system-architecture
---
---

# Chaos Engineer

Referência para desenhar experimentos de **chaos engineering** em sistemas distribuídos:
failure injection (Chaos Monkey, Litmus Chaos, toxiproxy), game days, controle de blast
radius, rollback automatizado e melhoria contínua de resiliência.

**Quando NÃO usar:**

- Para observabilidade (Prometheus, OTel, alertas) que valida o experimento, use
  `monitoramento-java`.
- Para deploy/infraestrutura da aplicação (Dockerfile, K8s manifest, pipeline), use
  `devops-cicd` (via `engenheiro-devops`).
- Para topologia cloud (VPC, IAM, DR), use `cloud-architect`.
- Para design de sistemas / ADRs, use `design-system-architecture`.

## Quando aplicar

- Desenhar e executar experimentos de chaos (pod delete, network latency, DB failure).
- Implementar failure injection framework (Chaos Monkey, Litmus, Gremlin, Pumba).
- Planejar e conduzir game day.
- Construir controles de blast radius e mecanismos de segurança.
- Integrar chaos testing ao pipeline CI/CD (continuous resilience).
- Melhorar resiliência com base em findings de experimentos.

## Workflow

1. **Análise do sistema** — mapear arquitetura, dependências, caminhos críticos e
   modos de falha.
2. **Desenho do experimento** — hipótese, steady state, blast radius, controles de
   segurança.
3. **Executar** — rodar experimento controlado, monitorar, rollback rápido se
   necessário.
4. **Aprender e melhorar** — documentar findings, implementar fixes, melhorar
   monitoramento.
5. **Automatizar** — integrar chaos testing ao CI/CD para resiliência contínua.

## Guia de referências

| Tópico | Referência | Quando carregar |
|---|---|---|
| Experimentos | `references/experiment-design.md` | Hipótese, blast radius, rollback |
| Infraestrutura | `references/infrastructure-chaos.md` | Server, network, zone, region failures |
| Kubernetes | `references/kubernetes-chaos.md` | Pod, node, Litmus, chaos mesh |
| Ferramentas e automação | `references/chaos-tools.md` | Chaos Monkey, Gremlin, Pumba, CI/CD |
| Game Days | `references/game-days.md` | Planejar, executar, aprender |

## Checklist de segurança

Restrições não óbvias que devem ser aplicadas em todo experimento:

- **Steady state primeiro** — definir e verificar métricas baseline antes de injetar
  qualquer falha.
- **Blast radius limitado** — começar com o menor escopo possível; expandir só após
  validação.
- **Rollback automatizado em ≤ 30 segundos** — script de abort testado **antes** do
  experimento começar.
- **Variável única** — mudar uma condição de falha por vez até o comportamento ser
  bem compreendido.
- **Sem produção sem safety nets** — ambiente customer-facing exige circuit breaker,
  feature flag ou canary isolation.
- **Fechar o loop** — todo experimento deve gerar learning summary escrito e pelo
  menos uma melhoria rastreada.

## Constraints

### MUST DO

- Definir e medir steady state antes de qualquer injeção.
- Limitar blast radius e poder revertê-lo em segundos.
- Documentar hipótese, métrica alvo e critério de rollback.
- Monitorar durante todo o experimento (RED/USE — ver `monitoramento-java`).
- Conduzir game day com runbook validado.
- Post-mortem após incidente real ou experimental.

### MUST NOT DO

- Rodar em produção sem baseline validado e safety nets.
- Combinar múltiplas falhas em um mesmo experimento (perde atribuição causal).
- Pular comunicação prévia a stakeholders afetados.
- Deixar `engineState: active` sem dono (ChaosEngine órfão = incidente silencioso).
- Considerar "passou" sem métrica que sustente.

## Exemplos concretos

### Experimento de pod failure (Litmus Chaos no K8s)

```bash
# Verificar baseline: p99 latency < 200ms, error rate < 0.1%
kubectl get deploy my-service -n production
kubectl top pods -n production -l app=my-service
```

````yaml
# chaos-pod-delete.yaml — blast radius: 1 replica por vez
apiVersion: litmuschaos.io/v1alpha1
kind: ChaosEngine
metadata:
  name: my-service-pod-delete
  namespace: production
spec:
  appinfo:
    appns: production
    applabel: "app=my-service"
    appkind: deployment
  engineState: active
  chaosServiceAccount: litmus-admin
  experiments:
    - name: pod-delete
      spec:
        components:
          env:
            - name: TOTAL_CHAOS_DURATION
              value: "60"          # segundos
            - name: CHAOS_INTERVAL
              value: "20"          # deleta um pod a cada 20s
            - name: FORCE
              value: "false"
            - name: PODS_AFFECTED_PERC
              value: "33"          # max 33% das réplicas afetadas
````

```bash
# Aplicar o experimento
kubectl apply -f chaos-pod-delete.yaml

# Acompanhar status
kubectl describe chaosengine my-service-pod-delete -n production
kubectl get chaosresult my-service-pod-delete-pod-delete -n production -w

# Monitorar durante o experimento
kubectl logs -l app=my-service -n production --since=2m -f
kubectl get chaosresult my-service-pod-delete-pod-delete \
  -n production -o jsonpath='{.status.experimentStatus.verdict}'

# Rollback imediato se steady state violado
kubectl patch chaosengine my-service-pod-delete \
  -n production --type merge -p '{"spec":{"engineState":"stop"}}'
kubectl rollout status deployment/my-service -n production
```

### Network latency com toxiproxy

```bash
# toxiproxy — proxy intermediário para injetar latência entre serviço e dependência
toxiproxy-server &

toxiproxy-cli create -l 0.0.0.0:22222 -u downstream-db:5432 db-proxy

# Injetar 300ms de latência com 10% de jitter — blast radius: este proxy apenas
toxiproxy-cli toxic add db-proxy -t latency -a latency=300 -a jitter=30

# Rodar load test / observar métricas ...

# Remover o toxic para restaurar comportamento normal
toxiproxy-cli toxic remove db-proxy -n latency_downstream
```

### Chaos Monkey (Spinnaker / standalone)

````yaml
# chaos-monkey-config.yml — restringir a uma única ASG
deployment:
  enabled: true
  regionIndependence: false
chaos:
  enabled: true
  meanTimeBetweenKillsInWorkDays: 2
  minTimeBetweenKillsInWorkDays: 1
  grouping: APP           # mata uma instância por app, não por cluster
  exceptions:
    - account: production
      region: us-east-1
      detail: "*-canary"  # nunca mata instâncias canary
````

```bash
# Aplicar e disparar kill manual para teste
chaos-monkey --app my-service --account staging --dry-run false
```

## Templates de saída

Todo experimento deve entregar:

1. **Documento de design** (hipótese, métricas, blast radius, rollback).
2. **Código de injeção** (script/manifest reaplicável).
3. **Setup de monitoramento** + configuração de alertas.
4. **Procedimento de rollback** + controles de segurança testados.
5. **Learning summary** + melhorias rastreadas.

## Quem aplica o quê

| Cenário | Agent / Modo | Skills complementares |
|---|---|---|
| Desenhar experimento de chaos | `engenheiro-chaos` (sessão dedicada) | `monitoramento-java`, `cloud-architect` |
| Conduzir game day | `engenheiro-chaos` (sessão dedicada) | `devops-cicd`, `monitoramento-java` |
| Configurar chaos contínuo no CI | `engenheiro-chaos` + `engenheiro-devops` | `devops-cicd` |
| Auditar resiliência pós-incidente | `engenheiro-chaos` (modo `auditoria`) | `monitoramento-java` |
| Validar experimento antes de prod | `java-revisor` (modo `auditoria`) se envolver código Java | esta skill como referência |

[Documentação base](https://jeffallan.github.io/claude-skills/skills/devops/chaos-engineer/)
