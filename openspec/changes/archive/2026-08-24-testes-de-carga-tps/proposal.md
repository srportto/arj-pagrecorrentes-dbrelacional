## Why

Não existe nenhuma infraestrutura de teste de carga neste monorepo hoje — nenhuma ferramenta
(k6/Gatling/JMeter/Locust), nenhum script, nenhuma pipeline. Precisamos saber o TPS de criação,
cancelamento e decisão de autorização no `contratocommand`, o TPS de consulta no `contratoquery`,
e o TPS geral que o sistema como um todo suporta antes de colapsar — hoje isso é desconhecido, e
várias configurações que definem o teto (pool do Hikari, concorrência de listener SQS,
concorrência de consumer Kafka) foram fixadas sem nenhuma medição, algumas com comentário
explícito no próprio código dizendo "ponto de partida para calibração, não teto definitivo".

## What Changes

- **ADD** infraestrutura de teste de carga (ferramenta a definir em `design.md`) com cenários
  separados por serviço: criação/cancelamento/decisão no `contratocommand`, consulta no
  `contratoquery`.
- **ADD** um cenário de jornada composta (criação → decisão → pipeline de eventos SNS/SQS/Kafka
  → temporizador) para observar se o teto de um componente muda quando todos rodam sob carga ao
  mesmo tempo (contenção de CPU/conexão compartilhada).
- **ADD** medição de métricas assíncronas (profundidade de fila SQS, lag de consumer group
  Kafka) além de TPS síncrono — no pipeline de eventos, "colapso" não é erro HTTP, é atraso
  acumulado, e TPS sozinho não captura isso.
- Execução em **baseline**: mede com a configuração atual do sistema (Hikari pool=10, SQS
  `MAX_CONCURRENT_MESSAGES=10`, Kafka consumer sem `concurrency` explícito) — nenhum teto é
  recalibrado antes de medir. Recalibrar esses valores para tentar aumentar o TPS é trabalho
  futuro, condicionado ao resultado deste baseline.
- Ambiente de execução: **só local** (`docker-compose` + Floci) — não provisiona nada em
  `infra/envs/prod` (hoje placeholder). Resultado documentado com essa ressalva explícita: números
  não são diretamente extrapoláveis para capacidade de produção sem normalizar por CPU/memória do
  host, já que nenhum compose do repo declara `cpus`/`mem_limit` por serviço.
- **ADD** exposição do endpoint `/actuator/prometheus` em `contratocommand` e `contratoquery`,
  restrita ao profile `local` (`application-local.yaml`) — necessário porque nenhum dos dois
  apps expõe hoje qualquer métrica além de `health`, e o critério de colapso multi-sinal (D1 do
  `design.md`) depende de `hikaricp_connections_pending`. **Revisão de escopo**: a primeira
  versão desta proposta previa "nenhuma mudança de código de aplicação"; essa garantia não se
  sustentou na prática — o sinal mais precoce de saturação de pool não existe em lugar nenhum
  acessível sem instrumentar as apps. O profile `prod` continua expondo só `health`, sem mudança.
- **Sem BREAKING**: nenhuma mudança de contrato de API ou comportamento observável em produção —
  a infraestrutura de carga é ferramenta de diagnóstico, roda contra o ambiente local existente;
  a única mudança em `prod` é uma dependência nova (`micrometer-registry-prometheus`) presente
  mas com o endpoint não exposto por configuração.

## Capabilities

### New Capabilities

- `teste-de-carga-autorizacoes`: infraestrutura e cenários de teste de carga (TPS) do monorepo —
  cenários isolados por serviço (`contratocommand` escrita, `contratoquery` leitura) e cenário de
  jornada composta, execução em baseline contra o ambiente local, com medição de TPS síncrono e
  de lag/profundidade de fila para os trechos assíncronos do pipeline de eventos.

### Modified Capabilities

*(nenhuma — não altera nenhum requisito de spec existente; é capability nova, puramente aditiva
e de diagnóstico)*

## Impact

- **Novo diretório de ferramenta de carga** (localização e ferramenta a decidir em `design.md`) —
  não afeta código de aplicação dos 5 apps Java nem da Lambda `expurgo-particao`.
- **`contratocommand`** (`apps/contratocommand`): alvo de carga para `POST /api/autorizacoes`,
  `PATCH /decisao`, `PATCH /cancelar`; ganha `micrometer-registry-prometheus` (`pom.xml`) e
  `/actuator/prometheus` exposto só no profile `local` (`application-local.yaml`) — ver item
  acima.
- **`contratoquery`** (`apps/contratoquery`): alvo de carga para `GET /api/autorizacoes` — mesmo
  endpoint já documentado como sensível a volume (scan de partições sem poda por conta); mesma
  mudança de `/actuator/prometheus` do `contratocommand`.
- **`autorizacaostatus-producer`, `eventos-consumer`, `temporiza-autorizacao`**: observados
  indiretamente via lag/profundidade de fila durante o cenário de jornada composta — carga não é
  aplicada diretamente nas portas HTTP desses serviços (o tráfego real deles vem de fila/tópico,
  não de request direto; `temporiza-autorizacao` nem publica porta no host, ver
  `apps/docker-compose.yml`).
- **Ambiente local** (`docker-compose`, Floci, Postgres, Kafka, Valkey): precisa estar no ar
  durante a execução; nenhuma mudança de infraestrutura declarativa (Terraform) é necessária para
  este escopo local-only.
- **CI**: fora de escopo nesta change — nenhuma das 7 pipelines existentes roda hoje teste de
  performance/carga, e este proposal não adiciona isso ainda (execução manual/local primeiro).
