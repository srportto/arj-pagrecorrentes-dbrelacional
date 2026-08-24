## Context

Nenhuma infraestrutura de teste de carga existe hoje neste monorepo (nenhuma ferramenta,
script ou pipeline). Vários tetos que hoje limitam TPS foram fixados sem medição — pool
HikariCP=10 (`contratocommand`/`contratoquery`, sem override em `-local`/`-prod`),
`MAX_CONCURRENT_MESSAGES=10` hardcoded nos listeners SQS (`autorizacaostatus-producer`,
`temporiza-autorizacao`), Kafka consumer sem `concurrency` explícito (`eventos-consumer`,
default = 1 thread). Nenhum `docker-compose` do repo declara `deploy.resources.limits`
(CPU/mem) por container.

O sistema tem dois regimes de falha bem diferentes sob carga: o caminho síncrono
(`contratocommand`/`contratoquery`, cliente espera resposta HTTP) e o caminho assíncrono
(SNS→SQS→Kafka, onde sobrecarga não vira erro — vira lag/profundidade de fila crescente).
Um teste de carga ingênuo mede só o primeiro e declara "colapso" usando taxa de erro HTTP,
o que não captura o segundo regime nem diferencia erro-por-design (idempotência,
lock de partição no expurgo) de colapso real.

Este design incorpora o parecer do agente `engenheiro-chaos` (ótica de blast radius e
resiliência), obtido antes de qualquer implementação — resumo completo em
`.claude/agent-memory/engenheiro-chaos/` (memória própria do agente, não versionada no
diretório da change).

## Goals / Non-Goals

**Goals:**
- Medir, em baseline (sem recalibrar tetos), o TPS de criação/cancelamento/decisão
  (`contratocommand`) e de consulta (`contratoquery`), isolados e depois em jornada composta.
- Definir critério de "colapso" multi-sinal, que diferencie erro-por-design de colapso real.
- Definir kill switches automáticos, nunca dependentes de intervenção manual durante o teste.
- Registrar a necessidade de isolamento de recursos (CPU/mem) por container como
  pré-requisito da capability, mesmo que a implementação desse isolamento seja decisão
  separada.
- Registrar convenção de massa de teste identificável e limpável após a execução.

**Non-Goals:**
- Não escolhe ainda a ferramenta de execução de carga (k6/Gatling/Locust) — decisão a
  fechar na fase de tasks/implementação, não neste design (nenhum dos pontos de resiliência
  abaixo depende da ferramenta específica).
- Não recalibra `MAX_CONCURRENT_MESSAGES`, pool do Hikari ou `concurrency` do Kafka consumer
  — isso é o baseline sendo medido, não algo a mudar antes.
- Não provisiona ambiente prod-like (`infra/envs/prod` continua placeholder) — execução é
  só local.
- Não implementa o isolamento de CPU/mem por container agora — só registra como
  pré-requisito de design (ver Decisão D5 e Riscos).

## Decisions

**D1 — Critério de colapso síncrono é multi-sinal, não só taxa de erro HTTP.**
O Postgres é compartilhado entre `contratocommand` e `contratoquery` — saturar o pool de um
pode se manifestar como degradação no outro antes de qualquer erro HTTP aparecer. Sinal mais
precoce recomendado: `hikaricp_connections_pending > 0` sustentado (conexões esperando no
pool), monitorado junto com p99 de latência e taxa de erro. Alternativa descartada: usar só
taxa de erro HTTP como critério — chega tarde demais, o sistema já está degradado bem antes
do primeiro 5xx aparecer.

**D2 — Kill switches em 3 níveis, sempre automáticos no executor de carga.**
1. Nível aplicação: pool esgotado (`hikaricp_connections_pending`) + p99 de latência + taxa
   de erro real (ver D3 para o que conta como "real").
2. Nível fila/lag: profundidade de fila SQS e lag de consumer group Kafka além de um limiar.
3. Nível host: saturação de CPU/mem do Docker Desktop.
Nenhum critério de parada é manual — um teste "solto" continuar martelando o sistema já
colapsado é o próprio risco que o experimento deveria evitar, não produzir.

**D3 — Erros são classificados em 3 buckets antes de entrar no critério de abort.**
- *Esperado-por-design*: 409 de idempotência (`RecursoJaExisteException`) — o sistema
  respeitando sua própria regra de concorrência, não um sintoma de colapso.
- *Esperado-mas-monitorado*: `CannotAcquireLockException` (SQLSTATE 40001) do expurgo de
  partição sob concorrência — não é colapso por si só, mas merece rastreio se a taxa subir
  de forma anômala.
- *Colapso real*: timeout, 5xx genérico, conexão recusada — só este bucket conta para os
  kill switches de D2. Alternativa descartada: tratar toda taxa de erro elevada como sinal de
  parada — geraria abort prematuro só por causa do próprio design de idempotência do sistema
  sob concorrência real, mascarando o teto real de TPS.

**D4 — Estratégia de carga depende do regime: ramp-up para síncrono isolado, spike/patamar
fixo para a jornada composta assíncrona.**
Ramp-up gradual revela o "joelho da curva" nos cenários isolados (`contratocommand`,
`contratoquery`) — o ponto onde latência começa a crescer não-linearmente. Já a jornada
composta precisa de patamar fixo sustentado (não ramp-up) para revelar se o lag da fila
diverge ao longo do tempo sob uma carga constante — um ramp-up mascararia esse efeito porque
a carga muda antes do lag ter tempo de se estabilizar ou divergir. As duas estratégias não
devem ser misturadas no mesmo experimento — resultados de um regime não são comparáveis ao
teto do outro.

**D5 — Isolamento de CPU/mem por container é pré-requisito da capability, registrado aqui,
não implementado nesta change.**
Confirmado: nenhum `docker-compose` do repo declara `deploy.resources.limits` hoje. Sem
isso, um teste local mede a capacidade do Docker Desktop do executor, não do serviço sob
teste — dois desenvolvedores rodando o mesmo cenário em máquinas diferentes teriam números
não comparáveis entre si, e nenhum dos dois representaria um teto de serviço real.
Alternativa descartada: aceitar a medição sem limites, documentando só como "ressalva" —
insuficiente, porque a ausência de isolamento não é uma imprecisão pequena, é a diferença
entre medir o serviço e medir a máquina do desenvolvedor.

**D6 — Massa de teste usa prefixo convencionado em `idAutorizacaoEmpresa` para
identificação e limpeza pós-teste.**
Convenção: `LOADTEST-{timestamp}-{seq}`. Necessário porque o teste cria autorizações reais
na tabela particionada, incluindo estados terminais que disparam o fluxo de expurgo
(`ExpurgoAutorizacaoService`) para a faixa 900-999 — a limpeza pós-teste precisa alcançar
tanto as partições quentes (0-888) quanto a faixa de expurgo, não só onde os dados foram
originalmente inseridos.

## Risks / Trade-offs

- **[Risco] Testar sem isolamento de recursos (D5 ainda não implementado) produz números
  não confiáveis mesmo seguindo os demais critérios deste design** → Mitigação: a primeira
  task de implementação desta capability deve avaliar/aplicar `deploy.resources.limits`
  nos composes relevantes antes da primeira execução de baseline — sem isso, o próprio
  baseline fica comprometido.
- **[Risco] Classificação de erro em buckets (D3) exige que o executor de carga distinga
  códigos/tipos de erro, não só conte falhas genéricas** → Mitigação: a ferramenta escolhida
  na fase de tasks precisa suportar essa distinção nativamente ou via script de correlação
  (ex.: contar 409 separado de 5xx); isso entra como critério de seleção de ferramenta.
- **[Risco] Massa de teste (D6) não limpa se o teste for interrompido por kill switch antes
  do fim planejado** → Mitigação: o prefixo `LOADTEST-*` precisa ser suficiente para uma
  limpeza manual/script posterior independente de como o teste terminou (sucesso, abort ou
  crash) — não depender de um passo de cleanup ao final do próprio script de carga.
- **[Trade-off] Baseline sem recalibrar tetos conhecidos (decisão já tomada na proposta)
  pode só confirmar limites já documentados no código em vez de achar gargalo novo** →
  Aceito conscientemente: o valor do baseline é ter um número de referência real antes de
  qualquer recalibração, não maximizar descoberta de gargalo novo nesta primeira rodada.

## Migration Plan

Não há dado de produção envolvido — execução é local, contra ambiente efêmero
(`docker-compose`). "Migração" aqui é o passo a passo de rollout da própria capability:
1. Implementar/validar isolamento de recursos por container (D5) antes de qualquer medição.
2. Escolher ferramenta de carga (fora de escopo deste design — fase de tasks) capaz de
   suportar a classificação de erro em buckets (D3) e os kill switches automáticos (D2).
3. Rodar cenários isolados primeiro (`contratocommand`, depois `contratoquery`), cada um com
   ramp-up (D4).
4. Rodar jornada composta com patamar fixo (D4), observando lag/profundidade de fila.
5. Limpar massa de teste via prefixo `LOADTEST-*` (D6), cobrindo partições quentes e faixa
   de expurgo.
Rollback: nenhum — nada em produção é tocado; interromper a execução local via os kill
switches de D2 é o próprio mecanismo de "rollback" de um teste em andamento.

## Open Questions

- Ferramenta de execução de carga (k6/Gatling/Locust/outra) — decisão adiada para `tasks.md`
  ou uma sub-decisão de implementação, já que nenhum ponto de resiliência deste design
  depende de qual ferramenta é usada, só de quais capacidades ela precisa ter (D2/D3).
- Limiares numéricos concretos (ex.: p99 exato, profundidade de fila exata que dispara
  abort) não foram fixados aqui — dependem de uma primeira execução exploratória para
  calibrar o que é "normal" antes de definir o que é "anômalo". Podem entrar como task de
  calibração inicial antes do baseline oficial.
