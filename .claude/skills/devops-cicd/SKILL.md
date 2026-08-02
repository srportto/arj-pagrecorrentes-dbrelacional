---
name: devops-cicd
description: Use ao montar ou ajustar pipeline de CI/CD para aplicação Java/Maven (GitHub Actions, GitLab CI, Jenkins), containerizar aplicação (Dockerfile multi-stage, .dockerignore, usuário não-root), ou escrever manifest Kubernetes de aplicação (Deployment, Service, ConfigMap, probes de saúde, graceful shutdown). Gatilhos - "GitHub Actions", "pipeline CI", "Dockerfile", "Kubernetes deployment", "k8s manifest", "rolling update", "graceful shutdown". Uso: agents `engenheiro-devops`/`especialista-docker`/`especialista-kubernetes` ou invocação manual via `/devops-cicd`; não deve ser carregada proativamente pela sessão principal.
---

# DevOps & CI/CD (Java/Maven, Docker, Kubernetes)

## Visão geral

Guia de DevOps focado no **caminho do código até a aplicação rodando em produção** em stack
Java/Maven, limitado a **CI/CD + containerização + deployment da aplicação** — não cobre Terraform
de cluster inteiro, rede, IAM de provedor cloud, ou administração de cluster.

**Quando NÃO usar:** código de aplicação → `java-construtor`. Auditoria completa de segurança →
`seguranca-aplicacao-java` + agent `engenheiro-seguranca`. Tuning de banco →
`banco-de-dados-performance`. Observabilidade pós-deploy → `monitoramento-java`.

## Workflow

1. **Confirme o que já existe** — verifique `.github/workflows/`, `Dockerfile`, `k8s/`,
   `docker-compose.yml`. Ajuste em vez de recriar.
2. **Defina os estágios necessários** — build → test → package → (push) → (deploy).
3. **Escreva o YAML** com quality gates apropriados ao projeto.
4. **Valide** — `docker build`, `kubectl apply --dry-run=client` (se o cluster estiver acessível),
   `mvn clean verify` localmente.
5. **Reporte** o que foi criado/alterado e quais gates foram configurados.

---

# 1. CI/CD — GitHub Actions

## Pipeline mínimo (build → test → package)

```yaml
name: ci
on:
  push:
    branches: [main]
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Configurar JDK 25 (Temurin)
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'
          cache: 'maven'

      - name: Build, testes e empacotamento
        run: mvn clean verify

      - name: Publicar artefato
        uses: actions/upload-artifact@v4
        with:
          name: app-jar
          path: target/*.jar
```

## Quality gates

- **Testes**: o build **deve falhar** se qualquer teste falhar — `mvn clean verify` já falha com
  testes vermelhos. **Nunca** usar `-DskipTests` em pipeline de CI.
- **Cobertura**: se o projeto tiver JaCoCo configurado, o gate barra merge abaixo do limiar (ex.: 80%)
  com `mvn jacoco:check -Djacoco.minimum.coverage=0.80`.
- **Dependências vulneráveis**: varredura de CVE no PR com
  `mvn org.owasp:dependency-check-maven:check` — ver `seguranca-aplicacao-java`.

## Versionamento e cache

- Versione o artefato com `${project.version}` do Maven, tag Git ou `${{ github.sha }}`
  (`mvn clean package -Drevision=${{ github.sha }}`); **evite** publicar sempre `app.jar` sem versão
  em ambientes não-efêmeros.
- Cache: `actions/setup-java` com `cache: 'maven'` resolve a maioria dos casos; para cache custom,
  `actions/cache@v4` com chave baseada em hash do `pom.xml`.

## Estratégias de deployment (acoplado à pipeline)

| Estratégia | Mecanismo | Quando usar |
|---|---|---|
| Rolling update (default K8s) | `RollingUpdate` com `maxUnavailable`/`maxSurge` | Padrão — substitui réplicas gradualmente |
| Blue-Green | Deploy em slot paralelo (`myapp-blue`), depois `kubectl patch service` troca o seletor | Rollback instantâneo, mas exige 2x recursos durante o switch |
| Canary (Flagger) | CRD `Canary` desloca tráfego em passos (`stepWeight`) monitorando métricas | Validação gradual com rollback automático por métrica |

```yaml
spec:
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
```

---

# 2. Docker — containerização

## Dockerfile multi-stage (Java 25)

```dockerfile
# Stage 1: build
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: runtime — -jre-alpine é mais enxuta e já traz wget via busybox (variante -jre
# Ubuntu/Debian NÃO tem wget/curl, o que quebra o HEALTHCHECK só em runtime, não no build).
FROM eclipse-temurin:25-jre-alpine
# Alpine/busybox cria usuário com addgroup/adduser, não groupadd/useradd (exigem pacote shadow).
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build /app/target/*.jar /app/app.jar
USER app
HEALTHCHECK --interval=30s --timeout=3s --start-period=15s --retries=3 \
  CMD wget -qO- http://localhost:8080/disponibilidade || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

## `.dockerignore` (sempre!)

```
target/
.git/
*.log
.env
```

## Regras não negociáveis

- **Multi-stage sempre** — nunca incluir Maven/JDK na imagem de runtime.
- **Usuário não-root** na imagem final.
- **HEALTHCHECK** apontando para o endpoint de disponibilidade (`/disponibilidade` na base deste
  catálogo, ajuste conforme a aplicação) — confirme que `wget`/`curl` existe na imagem final.

## Validação

```bash
docker build -t minha-app:test .
docker run --rm -p 8080:8080 minha-app:test
curl http://localhost:8080/disponibilidade   # smoke test
```

---

# 3. Kubernetes — manifests da aplicação

## Deployment + Service (mínimo completo)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: minha-app
spec:
  replicas: 2
  selector:
    matchLabels:
      app: minha-app
  template:
    metadata:
      labels:
        app: minha-app
    spec:
      terminationGracePeriodSeconds: 30
      containers:
        - name: minha-app
          image: minha-app:latest
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: minha-app-config
          readinessProbe:
            httpGet:
              path: /disponibilidade
              port: 8080
            initialDelaySeconds: 10
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /disponibilidade
              port: 8080
            initialDelaySeconds: 20
            periodSeconds: 15
          resources:
            requests:
              memory: "512Mi"
              cpu: "250m"
            limits:
              memory: "768Mi"
              cpu: "1000m"
          env:
            - name: JAVA_TOOL_OPTIONS
              value: "-XX:MaxRAMPercentage=75"
---
apiVersion: v1
kind: Service
metadata:
  name: minha-app
spec:
  selector:
    app: minha-app
  ports:
    - port: 80
      targetPort: 8080
```

## ConfigMap (env vars)

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: minha-app-config
data:
  SPRING_PROFILES_ACTIVE: "producao"
```

## Ingress (exposição externa, referencia o Service já criado acima)

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: minha-app-ingress
spec:
  ingressClassName: nginx
  rules:
    - host: myapp.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: minha-app
                port:
                  number: 80
```

## Recursos e memória JVM

- **Limite de memória do container** deve considerar **heap + metaspace + overhead da JVM**, não só
  o heap.
- Use `-XX:MaxRAMPercentage=75` (via `JAVA_TOOL_OPTIONS` ou `JAVA_OPTS`) para que a JVM dimensione o
  heap como fração do limite do container — evita OOMKill por heap sub/superdimensionado.
- Regra prática: `requests.memory` = `limits.memory` (classe QoS Guaranteed) para workloads
  previsíveis; ajuste conforme a criticidade.

## Graceful shutdown

- `terminationGracePeriodSeconds` no Deployment **maior** que o tempo de shutdown da aplicação.
- Spring Boot: habilitar `server.shutdown: graceful` + `spring.lifecycle.timeout-per-shutdown-phase`
  compatível (ex.: 25s para `terminationGracePeriodSeconds: 30`).

```yaml
# application.yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 25s
```

## Probes — por que sempre configurar

- `readinessProbe` — diz ao Service se a réplica pode receber tráfego; sem ele, tráfego vai para
  réplicas ainda subindo ou com dependência indisponível.
- `livenessProbe` — diz ao kubelet se o container travou; reinicia se falhar. Sem ele, um deadlock
  fica lá para sempre. Sempre configure os dois separadamente (ver `monitoramento-java`).

## Validação

```bash
kubectl apply --dry-run=client -f deployment.yaml
kubectl apply -f deployment.yaml
kubectl rollout status deployment/minha-app
```

---

# Constraints

## MUST DO
- Use infrastructure as code (nunca mudanças manuais em produção).
- Implemente health checks e readiness probes em **toda** aplicação.
- Armazene segredos em secret manager (não em env files ou configmaps).
- Habilite container scanning no CI (Trivy, Snyk).
- Documente procedimentos de rollback.
- Configure `terminationGracePeriodSeconds` + `server.shutdown: graceful` juntos.
- Configure **liveness e readiness probes** separados.
- Limite de memória do container sempre maior que o heap configurado via `MaxRAMPercentage`.

## MUST NOT DO
- Faça deploy em produção sem aprovação explícita.
- Armazene segredos em código ou variáveis de CI/CD.
- Pule testes em pipeline de CI (`-DskipTests`).
- Ignore limites de recursos em containers.
- Use tag `latest` em produção.
- Faça deploy na sexta sem monitoramento no fim de semana.
- **Deployment sem probe de saúde** — achado crítico.

## Quem aplica o quê

| Situação | Quem | Skill/agent |
|---|---|---|
| Montar/ajustar pipeline CI/CD | session principal ou `engenheiro-devops` | esta skill |
| Escrever Dockerfile | session principal ou `especialista-docker` | esta skill |
| Escrever manifest Kubernetes | session principal ou `especialista-kubernetes` | esta skill |
| Configurar observabilidade pós-deploy | session principal | `monitoramento-java` |
| Validar trabalho DevOps antes de merge | `java-especialista` | `revisao-de-codigo-java` |
