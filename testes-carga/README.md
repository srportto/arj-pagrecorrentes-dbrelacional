# testes-carga

Infraestrutura de teste de carga (TPS) do monorepo — change OpenSpec
`testes-de-carga-tps` (ver `openspec/changes/testes-de-carga-tps/`). Mede o TPS de
criação/cancelamento/decisão de autorização no `contratocommand`, o TPS de consulta no
`contratoquery`, e observa o comportamento do pipeline assíncrono (SNS/SQS/Kafka) sob carga.

**Ferramenta de diagnóstico, não código de produção.** Não é empacotada nem deployada junto
com as 5 apps — módulo Maven independente, fora do build delas.

## Ferramenta escolhida: Gatling (Java DSL)

Avaliado contra os requisitos de `design.md` (D2/D3): o executor precisa suportar kill
switches automáticos configuráveis e discriminar códigos de erro (409 vs. 5xx vs. timeout).
Gatling foi escolhido em vez de k6/Locust porque:

- É JVM-based — este monorepo é 100% Java/Maven; nenhuma outra linguagem de runtime é
  introduzida.
- Os kill switches deste design (D2) não são "abort ao final da simulação" (o que Gatling já
  faz nativamente via `assertions`) — são **externos**, monitorando métricas que vêm de fora
  do processo de carga (Hikari pool via `/actuator/prometheus`, profundidade de fila
  SQS/lag Kafka). Isso não depende de nenhuma capacidade especial da ferramenta de carga em
  si, então a integração nativa com JVM/Maven pesou mais que qualquer feature de "abort"
  embutida.
- Permite reaproveitar diretamente `software.amazon.awssdk` (já usado por
  `apps/contratocommand`) e `kafka-clients` sem gambiarra de linguagem cruzada, se algum
  cenário futuro precisar consultar fila/lag de dentro da própria simulação.

## Pré-requisito bloqueante: limites de recursos (D5)

Nenhuma execução desta capability é válida sem `deploy.resources.limits` (CPU/mem) aplicados
em todos os containers do ambiente local — sem isso, o TPS medido reflete a capacidade do
Docker Desktop do executor, não do serviço. Já aplicado nesta change:

| Serviço | CPU | Memória | Racional |
|---|---|---|---|
| `contratocommand` | 1.0 | 512M | alvo de carga síncrona |
| `contratoquery` | 1.0 | 512M | alvo de carga síncrona |
| `autorizacaostatus-producer` | 0.5 | 512M | ponte SQS→Kafka, observado por lag |
| `eventos-consumer` | 0.5 | 512M | consumer Kafka, observado por lag |
| `temporiza-autorizacao` (x2 réplicas) | 0.5 cada | 512M cada | sem porta HTTP, tráfego só via fila |
| `postgres` | 2.0 | 2G | compartilhado por command/query — maior candidato a gargalo |
| `kafka` | 1.0 | 1G | broker |
| `schema-registry` | 0.5 | 512M | apoio Kafka |
| `kafbat-ui` | 0.5 | 512M | dashboard, não crítico |
| `floci` | 1.0 | 1G | emulador AWS local, roda containers-irmão (ECS/Lambda/ECR) |
| `valkey` | 0.5 | 256M | agenda do temporizador |

Valores são **ponto de partida para calibração**, não teto definitivo — mesma convenção já
usada em `MAX_CONCURRENT_MESSAGES` (ver `SqsListenerContainerFactoryConfig`). Ajustar
proporcionalmente ao host de referência antes de comparar execuções entre máquinas
diferentes.

## Pré-requisito: métricas expostas nas apps

`contratocommand` e `contratoquery` ganharam `micrometer-registry-prometheus` e
`/actuator/prometheus` exposto **só no profile `local`** — sem isso, o sinal mais precoce de
colapso (`hikaricp_connections_pending`, D1) não existe em lugar nenhum acessível. Ver
`proposal.md` (revisão de escopo) para o racional completo dessa mudança de app.

## Estrutura

```
testes-carga/
├── pom.xml                                    módulo Maven independente (Gatling + AWS SDK + Kafka client)
├── src/test/java/carga/
│   ├── support/
│   │   ├── Config.java                        URLs base, limiares dos kill switches (env vars)
│   │   ├── MassaTeste.java                     gera idAutorizacaoEmpresa = LOADTEST-{timestamp}-{seq} (D6)
│   │   └── ErroClassificador.java              classifica erro em 3 buckets (D3)
│   └── scenarios/
│       ├── ContratocommandEscritaSimulation.java   criar → decidir → cancelar, ramp-up (D4)
│       ├── ContratoqueryLeituraSimulation.java     GET listagem, ramp-up (D4)
│       └── JornadaCompostaSimulation.java          criar → decidir, patamar fixo (D4)
├── scripts/
│   ├── kill-switch-monitor.sh                  nível aplicação + host (D2) — hikari pending, CPU
│   ├── fila-lag-monitor.sh                     nível fila/lag (D2) — só para a jornada composta
│   ├── limpar-massa-teste.sql                  remove tudo com prefixo LOADTEST- (D6)
│   └── rodar-cenario.sh                        orquestra: roda cenário + kill switches + limpeza sempre
└── relatorios/                                 saída de rodar-cenario.sh (git-ignorado se preferir)
```

## Como rodar

Pré-requisito: ambiente local no ar (`docker compose up -d --build` na raiz do repo).

```bash
# Cenário isolado de escrita (contratocommand)
testes-carga/scripts/rodar-cenario.sh carga.scenarios.ContratocommandEscritaSimulation

# Cenário isolado de leitura (contratoquery)
testes-carga/scripts/rodar-cenario.sh carga.scenarios.ContratoqueryLeituraSimulation

# Jornada composta -- liga também o monitor de fila/lag
testes-carga/scripts/rodar-cenario.sh carga.scenarios.JornadaCompostaSimulation --com-fila-lag
```

Cada execução:
1. Roda o cenário Gatling em background.
2. Liga o(s) monitor(es) de kill switch em paralelo, observando o PID do Gatling.
3. Se um limiar for ultrapassado de forma sustentada, mata o processo automaticamente —
   nenhum critério de parada é manual (D2).
4. **Sempre** limpa a massa de teste ao final (sucesso, abort ou falha) via
   `limpar-massa-teste.sql`.
5. Salva um relatório em `relatorios/` com a configuração vigente usada e a ressalva de
   ambiente local (Requirement "Escopo de ambiente local sem extrapolação para produção").

Relatório detalhado (gráficos, percentis) fica em `target/gatling/<simulação>-<timestamp>/`
(gerado pelo `gatling-maven-plugin`).

## Limiares dos kill switches (env vars, valores de partida)

| Variável | Default | Nível |
|---|---|---|
| `LOADTEST_LIMITE_HIKARI_PENDING` | `0` | Aplicação (D1) |
| `LOADTEST_LIMITE_CPU_HOST_PCT` | `90` | Host |
| `LOADTEST_LIMITE_FILA_SQS` | `1000` | Fila/lag |
| `LOADTEST_LIMITE_LAG_KAFKA` | `1000` | Fila/lag |
| `LOADTEST_MONITOR_LEITURAS_SUSTENTADAS` | `3` | (comum) leituras seguidas antes de abortar |

Nenhum desses valores foi calibrado contra uma execução real ainda — são ponto de partida.
Task de calibração pendente (`openspec/changes/testes-de-carga-tps/tasks.md`, 8.3).
