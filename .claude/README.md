# Catálogo de skills e agents (`.claude/`)

Catálogo local de **skills** e **agents** para desenvolvimento Java/Spring Boot (Java 25 + Spring
Boot 4) neste monorepo. Já está instalado em `.claude/skills/` e `.claude/agents/` — não é preciso
copiar nada de outro lugar. Use para consultar convenções, padrões e práticas ao gerar, revisar ou
auditar código e infraestrutura.

## Como usar este catálogo

### 1. Leitura direta (referência)

Abra o `SKILL.md` (ou o `.md` do agent) direto em `.claude/skills/<nome>/SKILL.md` ou
`.claude/agents/<nome>.md`. O frontmatter traz `name` e `description` (gatilhos em pt-BR que dizem
**quando** a skill se aplica); o corpo traz a referência detalhada.

**Skills deste projeto (exceto as de openspec) não devem ser carregadas proativamente pela sessão
principal** — cada `description` termina com uma frase "Uso: agent `X` ou invocação manual via
`/<nome-da-skill>`", indicando que a skill só deve ser puxada por um agent especializado que a
referencia como fonte de verdade, ou manualmente via `/<nome-da-skill>`. Isso mantém as skills
ociosas por padrão e evita que a sessão principal carregue conteúdo de referência sem necessidade.

### 2. Por agente (uso programático)

Os agents deste catálogo são invocados como subagents. Cada agent declara:

- `description` — quando invocá-lo.
- `tools` — ferramentas que ele pode usar.
- `model` e `effort` — o "tamanho" do trabalho que ele faz.
- Referências às skills que ele consome como fonte de verdade (lidas via `.claude/skills/<nome>/SKILL.md`).

Fluxo típico:

1. Você faz um pedido (ex.: "revise esse diff", "crie uma app nova", "aplique Remove Parameter").
2. O agente correspondente é invocado.
3. O agente lê as skills referenciadas, aplica os critérios, e devolve o resultado.
4. O resultado é validado por outro agente (ex.: trabalho do `java-construtor` → validado pelo
   `java-revisor` no modo `auditoria`).

## Estrutura

```
.claude/
├── skills/                                # 24 skills (+ 5 skills openspec, fora deste catálogo)
│   ├── api-rest-design/                   # REST + OpenAPI + RFC 9457
│   ├── arquitetura-limpa-java/            # Hexagonal clássica (ports & adapters) + DDD + microservices
│   ├── banco-de-dados-performance/        # SQL + EXPLAIN + tuning (PostgreSQL/MySQL)
│   ├── chaos-engineer/                    # Chaos engineering, game days, Litmus
│   ├── cloud-architect/                   # AWS / Azure / GCP topology, FinOps, DR
│   ├── criar-aplicacao-java/              # Esqueleto hexagonal clássico + variantes (SQS/Kafka/banco)
│   ├── design-system-architecture/        # System design + ADRs (renomeada de architecture-designer)
│   ├── devops-cicd/                       # CI/CD GitHub Actions + Dockerfile + K8s manifest
│   ├── gerar-diagramas/                   # Mermaid padronizado em .md versionados
│   ├── java-architecture/                 # Spring stack + camadas clássicas
│   ├── java-moderno/                      # Records, sealed, pattern matching, virtual threads
│   ├── mensageria-sqs-kafka/              # SQS + Kafka (DLQ, idempotência, retry)
│   ├── monitoramento-java/                # Prometheus, OTel, Grafana, alertas
│   ├── padrao-de-logs-java/               # JSON estruturado + MDC + traceId
│   ├── padroes-de-projeto-java/           # 21 patterns GoF + Strategy por lista injetada
│   ├── persistencia-jpa/                  # JPA/Hibernate (N+1, transações, locking)
│   ├── python-pro/                        # Python 3.11+ (mypy, pytest, async) — apps/expurgo-particao
│   ├── qualidade-codigo-java/             # Clean code + refactorings do Fowler
│   ├── refactoring-remove-parameter/      # Foco Remove Parameter (passo a passo)
│   ├── remover-imports-nao-usados/        # Limpeza de imports multi-linguagem
│   ├── revisao-de-codigo-java/            # Checklist de revisão por severidade
│   ├── seguranca-aplicacao-java/          # OWASP Top 10 + JWT + CORS + secrets
│   ├── spring-data-redis/                 # Redis/Valkey — cache, sorted sets, streams, consumer groups
│   └── terraform-engineer/                # Terraform IaC — módulos, state, providers
└── agents/                                # 11 agents (após padronização de 2026-08-04)
    ├── arquiteto-sistemas.md              # Design de sistemas / ADRs / revisão arquitetural
    ├── cloud-architect.md                 # Topologia AWS/Azure/GCP + FinOps + DR
    ├── engenheiro-chaos.md                # Chaos engineering + game days
    ├── engenheiro-devops.md               # Pipeline CI + Dockerfile + manifest K8s (variantes)
    ├── engenheiro-seguranca.md            # Auditoria dedicada de segurança
    ├── especialista-banco-dados.md        # Performance de banco (SQL/SGBD)
    ├── especialista-monitoramento.md      # Observabilidade (Prometheus/OTel/Grafana)
    ├── java-construtor.md                 # Gerar/expandir app
    ├── java-revisor.md                    # Revisão (modos: tempestivo | auditoria)
    ├── projetista-api.md                  # Design de API REST
    └── refatorador-java.md                # Aplicar refactorings do Fowler
```

> **Crédito do conteúdo técnico:** as skills deste catálogo foram traduzidas, adaptadas e
> estendidas a partir do catálogo original de [`Jeffallan/claude-skills`](https://github.com/Jeffallan/claude-skills)
> — fonte base do conteúdo técnico das skills (exceto as de openspec, que vêm do próprio openspec).
> Cada frontmatter de skill mantém apenas o `author` local do catálogo (`srportto/srportto`);
> a origem é creditada aqui, em ponto único, para não poluir o frontmatter de cada skill.

## Padrão de cada skill

- **Bloco YAML frontmatter** com `name`, `description` (gatilhos em pt-BR), `metadata` opcional.
- **Título `# <Nome em Português>`** — descrição em português.
- **Seções em português** — explicações, contexto, exemplos textuais.
- **Trechos técnicos, padrões, exemplos de código em inglês** — nomes de classe, anotações
  Spring, APIs, mensagens de log, termos consagrados (`Builder`, `Factory`, `Strategy`, `JOIN
  FETCH`, `acks=all`, `ProblemDetail`, etc.).
- **Comentários de código em português** quando o exemplo for didático (sinaliza o ponto da
  explicação).
- **Quando NÃO usar** — sempre presente, lista skills alternativas para tarefas próximas mas
  diferentes.
- **Quem aplica o quê** — fecha a skill com a tabela que diz qual agent usa aquela skill em qual
  cenário.

## Como escolher a skill certa

Três atalhos:

1. **Pela tarefa** — use a [tabela de mapa rápido](#mapa-rapido-de-skills-por-tarefa) abaixo.
2. **Pela decisão arquitetural** — abra `arquitetura-limpa-java` (camadas, DDD, microservices) ou
   `java-architecture` (camadas clássicas + Spring stack).
3. **Pela dúvida específica** — toda skill tem seção "Quando NÃO usar" que aponta para skills
   irmãs. Se a que você abriu não for a certa, ela mesma diz qual é.

### Mapa rápido de skills por tarefa

| Tarefa | Skill principal | Skills complementares |
|---|---|---|
| Criar aplicação nova do zero | `criar-aplicacao-java` | `arquitetura-limpa-java`, `mensageria-sqs-kafka`, `persistencia-jpa` |
| Dúvida sobre em qual camada colocar código | `arquitetura-limpa-java` | `java-architecture` |
| Decompor monolito em microsserviços | `arquitetura-limpa-java` (seção DDD) | `mensageria-sqs-kafka`, `monitoramento-java` |
| Desenhar contrato de API | `api-rest-design` | `arquitetura-limpa-java` (driving adapter em `infrastructure/web`) |
| Implementar controller REST | `arquitetura-limpa-java` | `api-rest-design`, `revisao-de-codigo-java` |
| Resolver N+1, LazyInit, dirty checking | `persistencia-jpa` | `banco-de-dados-performance` |
| Otimizar query SQL, criar índice, tuning | `banco-de-dados-performance` | `persistencia-jpa` |
| Padronizar logs (JSON, MDC, traceId) | `padrao-de-logs-java` | `monitoramento-java` |
| Configurar observabilidade (Prometheus, OTel) | `monitoramento-java` | `padrao-de-logs-java` |
| Implementar autenticação/autorização | `seguranca-aplicacao-java` | `arquitetura-limpa-java`, `java-architecture` |
| Auditar segurança pré-produção | `seguranca-aplicacao-java` | `padrao-de-logs-java` |
| Revisar diff/PR | `revisao-de-codigo-java` | `padrao-de-logs-java`, `arquitetura-limpa-java`, `persistencia-jpa` |
| Auditar trabalho de outro agent | `revisao-de-codigo-java` | (todas conforme o tema do trabalho) |
| Aplicar refactoring | `qualidade-codigo-java` | `refactoring-remove-parameter` (foco) |
| Migrar para features modernas Java | `java-moderno` | `revisao-de-codigo-java` |
| Adicionar mensageria (SQS/Kafka) | `mensageria-sqs-kafka` | `criar-aplicacao-java` (variantes SQS/Kafka) |
| Pipeline CI/CD | `devops-cicd` | `monitoramento-java` (após deploy) |
| Dockerfile | `devops-cicd` | `monitoramento-java` (HEALTHCHECK) |
| Manifests Kubernetes | `devops-cicd` | `monitoramento-java` (probes) |
| Remover imports não usados | `remover-imports-nao-usados` | — |
| Escolher entre patterns (quando aplicar) | `padroes-de-projeto-java` | `qualidade-codigo-java` |
| Desenhar sistema distribuído, escrever ADR | `design-system-architecture` | `arquitetura-limpa-java`, `java-architecture` |
| Topologia de nuvem (VPC, IAM, DR, FinOps) | `cloud-architect` | `devops-cicd` |
| Experimento de chaos / game day | `chaos-engineer` | `monitoramento-java` |
| Gerar diagrama Mermaid versionado | `gerar-diagramas` | `design-system-architecture` |
| Cache, agendamento ou fila de trabalho com Redis/Valkey | `spring-data-redis` | `arquitetura-limpa-java` (adapter), `mensageria-sqs-kafka` (comparável) |
| Criar ou revisar código Python (Lambda, scripts) | `python-pro` | `arquitetura-limpa-java` (estrutura hexagonal em outros serviços) |
| Terraform IaC (módulos, state, providers) | `terraform-engineer` | `cloud-architect` (topologia), `devops-cicd` (deploy) |

## Como escolher o agent certo

### Por papel

| Papel | Agent | Modelo | Esforço | Quando invocar |
|---|---|---|---|---|
| Construtor | `java-construtor` | sonnet | medium | Gerar/expandir aplicação Java (inclui variantes cache/stream com Redis/Valkey e Lambda Python) |
| Revisor (tempestivo) | `java-revisor` (modo `tempestivo`) | opus | high | Revisão de diff pequeno (uma classe, um método) |
| Revisor (auditoria) | `java-revisor` (modo `auditoria`) | opus | high | Veredicto final de merge / auditoria completa |
| Designer de API | `projetista-api` | sonnet | medium | Desenhar/auditar contrato de API REST |
| DBA / SRE de banco | `especialista-banco-dados` | sonnet | medium | Investigar query lenta, criar índice, tuning |
| SRE de observabilidade | `especialista-monitoramento` | sonnet | medium | Configurar observabilidade, métricas, alertas, tracing |
| Refatorador | `refatorador-java` | sonnet | medium | Aplicar refactorings do Fowler |
| DevOps | `engenheiro-devops` | sonnet | medium | Pipeline CI/CD + Dockerfile + manifest K8s (variantes) |
| Segurança (auditoria dedicada) | `engenheiro-seguranca` | sonnet | medium | Varredura de CVEs, pentest interno, pré-produção |
| Arquiteto de sistemas | `arquiteto-sistemas` | sonnet | medium | Desenhar/revisar arquitetura distribuída, escrever ADR |
| Arquiteto de nuvem | `cloud-architect` | sonnet | medium | Topologia AWS/Azure/GCP, IAM, DR, FinOps, Terraform |
| Chaos engineer | `engenheiro-chaos` | sonnet | medium | Desenhar/executar experimento de falha, game day |

### Por fluxo de trabalho

| Você quer... | Primeiro | Depois (validação) |
|---|---|---|
| Criar uma aplicação nova | `java-construtor` | `java-revisor` (modo `auditoria`) |
| Adicionar uma feature/endpoint em app existente | sessão principal com skills (`arquitetura-limpa-java`, etc.) | `java-revisor` (modo `tempestivo`) → `java-revisor` (modo `auditoria`, pré-merge) |
| Revisar um PR/diff | `java-revisor` (modo `tempestivo`) | `java-revisor` (modo `auditoria`, se o PR for grande) |
| Auditar trabalho de outro agent | `java-revisor` (modo `auditoria`) | — |
| Aplicar um refactoring específico | `refatorador-java` | `java-revisor` (modo `tempestivo`) |
| Desenhar contrato de API | `projetista-api` | `java-construtor` (implementa) → `java-revisor` (modo `auditoria`, valida) |
| Investigar query lenta | `especialista-banco-dados` | — |
| Configurar observabilidade | `especialista-monitoramento` | — |
| Auditar segurança dedicada | `engenheiro-seguranca` | `java-revisor` (modo `auditoria`, para fechar achados críticos) |
| Montar pipeline (CI/Docker/K8s) | `engenheiro-devops` (variante `pipeline`/`docker`/`k8s`/`all`) | `java-revisor` (modo `auditoria`, se parte de entrega Java) |

### Regra de esforço

`java-revisor` é o **único** agent com `model: opus` e `effort: high` neste catálogo. É a
**última linha de defesa** antes de algo ser declarado pronto. Use-o para:

- Veredicto final de merge de mudança grande.
- Auditoria do trabalho de outro agent (ex.: saída do `java-construtor`).
- Pré-produção de feature crítica.

A diferença entre os dois modos do `java-revisor` é **amplitude da varredura**, não o tier
do modelo (sempre opus/high):

- `tempestivo` — feedback rápido sobre diff/classe; não bloqueia o fluxo.
- `auditoria` — varredura completa de todos os arquivos da entrega, com veredicto
  APROVADO/REPROVADO.

Todos os demais agents são `sonnet` + `medium` — calibrados para o trabalho tático do dia
a dia (gerar, revisar fragmento, investigar, configurar) sem o overhead do opus.

## Princípios do catálogo

- **Uma fonte de verdade por tema** — não há duas skills dizendo coisas diferentes sobre o
  mesmo assunto. Quando há sobreposição (ex.: JPA vs SQL tuning), as skills apontam uma para a
  outra.
- **Comentários em pt-BR, termos técnicos em inglês** — convenção da casa: explicações em
  português, padrões consagrados (`Builder`, `Factory`, `JOIN FETCH`, `ProblemDetail`) em inglês.
- **Quem aplica o quê explícito** — toda skill fecha com a tabela que diz "qual agent me
  invoca, em qual cenário". Evita confusão sobre quem faz o quê.
- **`Quando NÃO usar` explícito** — toda skill abre dizendo **para que ela não serve** e qual
  skill irmã usar no lugar. Reduz o erro de "abri a skill errada".
- **Ociosa por padrão** — nenhuma skill deste catálogo (exceto as de openspec) deve ser carregada
  proativamente pela sessão principal. Toda `description` termina indicando o(s) agent(s) que a
  usam como fonte de verdade, ou a invocação manual via `/<nome-da-skill>`.
- **Toda fila SQS nasce com DLQ, todo consumo de mensageria tem interceptor central de erro** —
  regras obrigatórias detalhadas em `mensageria-sqs-kafka` (seções 2 e 3), validadas pelo
  `java-revisor` no modo `auditoria`.
