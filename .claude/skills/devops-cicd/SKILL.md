---
name: devops-cicd
description: Use ao montar ou ajustar pipeline de CI/CD para aplicação Java/Maven (GitHub Actions, GitLab CI, Jenkins), containerizar aplicação (Dockerfile multi-stage, .dockerignore, usuário não-root), ou escrever manifest Kubernetes de aplicação (Deployment, Service, ConfigMap, probes de saúde, graceful shutdown). Gatilhos - "GitHub Actions", "pipeline CI", "Dockerfile", "Kubernetes deployment", "k8s manifest", "rolling update", "graceful shutdown".
---

# DevOps & CI/CD (Java/Maven, Docker, Kubernetes)

## Visão geral

Guia de DevOps focado no **caminho do código até a aplicação rodando em produção** em stack
Java/Maven, com escopo limitado a **CI/CD + containerização + deployment da aplicação** — não
cobre Terraform de cluster inteiro, rede, IAM de provedor cloud, ou administração de cluster.

**Quando NÃO usar:** para escrever código de aplicação, use `java-construtor`. Para auditoria
completa de segurança de aplicação, use `seguranca-aplicacao-java` e o agent
`engenheiro-seguranca`. Para tuning de banco, use `banco-de-dados-performance`. Para observabilidade
depois do deploy, use `monitoramento-java`.

## Workflow

1. **Confirme o que já existe** — antes de criar do zero, verifique `.github/workflows/`,
   `Dockerfile`, `k8s/`, `docker-compose.yml`. Ajuste em vez de recriar.
2. **Defina os estágios necessários** — build → test → package → (push) → (deploy).
3. **Escreva o YAML** com quality gates apropriados ao projeto.
4. **Valide** — `docker build` se possível, `kubectl apply --dry-run=client` se o cluster estiver
   acessível, `mvn clean verify` localmente para o job de build.
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

- **Testes**: o build **deve falhar** se qualquer teste falhar — `mvn clean verify` já falha o
  processo com testes vermelhos. **Nunca** usar `-DskipTests` em pipeline de CI.
- **Cobertura**: se o projeto tiver JaCoCo configurado, o gate deve barrar merge abaixo do limiar
  (ex.: 80%).

```yaml
- name: Verificar cobertura minima
  run: |
    mvn verify
    mvn jacoco:check -Djacoco.minimum.coverage=0.80
```

- **Dependências vulneráveis**: varredura de CVE no PR — ver
  `seguranca-aplicacao-java` (seção "Dependências vulneráveis").

```yaml
- name: OWASP Dependency-Check
  run: mvn org.owasp:dependency-check-maven:check
```

## Versionamento de artefato

- Use `${project.version}` do Maven, tag Git ou `${{ github.sha }}` para builds de desenvolvimento.
- **Evite** publicar sempre `app.jar` sem versão em ambientes que não sejam efêmeros.

```yaml
- name: Build com versao
  run: mvn clean package -Drevision=${{ github.sha }}
```

## Cache de dependências

- `actions/setup-java` com `cache: 'maven'` já resolve a maioria dos casos.
- Para cache custom: `actions/cache@v4` com chave baseada em `pom.xml` hash.

## Estratégias de deployment (acoplado à pipeline)

### Rolling update (default Kubernetes)

```yaml
spec:
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
```

### Blue-Green

```yaml
- name: Deploy to blue
  run: |
    kubectl set image deployment/myapp-blue myapp=myapp:${{ github.sha }}
    kubectl rollout status deployment/myapp-blue
- name: Switch traffic
  run: |
    kubectl patch service/myapp -p '{"spec":{"selector":{"slot":"blue"}}}'
```

### Canary (com Flagger)

```yaml
apiVersion: flagger.app/v1beta1
kind: Canary
spec:
  targetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: myapp
  analysis:
    interval: 1m
    threshold: 5
    stepWeight: 20
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

# Stage 2: runtime
# Usamos a variante -jre-alpine: além de gerar imagem mais enxuta, o Alpine já traz
# "wget" via busybox por padrão — a variante -jre (Ubuntu/Debian) NÃO inclui wget nem
# curl pré-instalados, o que faria o HEALTHCHECK abaixo falhar em runtime com
# "command not found" (o docker build não detecta isso, pois HEALTHCHECK só roda depois).
FROM eclipse-temurin:25-jre-alpine
# No Alpine/busybox o usuário é criado com addgroup/adduser (não groupadd/useradd,
# que exigem o pacote shadow ausente por padrão nessa imagem).
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
  catálogo, ajuste conforme a aplicação).
- **wget/curl** presente na imagem final se o HEALTHCHECK usar; `-jre-alpine` traz via busybox;
  variantes `-jre` (Ubuntu/Debian) precisam instalar explicitamente.

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

## Service & Ingress (exposição externa)

```yaml
apiVersion: v1
kind: Service
metadata:
  name: minha-app-service
spec:
  selector:
    app: minha-app
  ports:
    - port: 80
      targetPort: 8080
  type: ClusterIP
---
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
                name: minha-app-service
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

- `readinessProbe` — diz ao Service se a réplica pode receber tráfego. Sem ele, Service envia
  tráfego para réplicas que ainda estão subindo ou em DB indisponível.
- `livenessProbe` — diz ao kubelet se o container está travado; reinicia se falhar. Sem ele, um
  deadlock no app fica lá para sempre.
- Separar os dois (ver `monitoramento-java`).

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
