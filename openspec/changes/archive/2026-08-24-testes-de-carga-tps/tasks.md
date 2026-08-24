## 1. Pré-requisitos de ambiente (D5)

- [x] 1.1 Adicionar `deploy.resources.limits` (CPU e memória) por serviço nos composes
      relevantes (`compose.yaml`, `apps/docker-compose.yml`, `infra/local/postgres/`,
      `infra/local/kafka/`, `infra/local/redis/`, `infra/local/floci/`) — sem isso nenhuma
      execução desta capability é válida (Requirement "Isolamento de recursos por container
      como pré-requisito")
- [x] 1.2 Documentar os valores de limite escolhidos e a justificativa (proporção de
      recursos do host de referência) em `docs/` ou no README da ferramenta de carga
- [x] 1.3 Validar que o ambiente local sobe normalmente com os limites aplicados
      (`docker compose up -d --build`, todos os health checks `healthy`)

## 1.4 Instrumentação de métricas (descoberta durante implementação, não prevista originalmente)

- [x] 1.4.1 Adicionar `micrometer-registry-prometheus` em `apps/contratocommand/pom.xml` e
      `apps/contratoquery/pom.xml` — nenhum dos dois expunha métrica além de `health`, e o
      sinal de D1 (`hikaricp_connections_pending`) não existia em lugar nenhum acessível
- [x] 1.4.2 Expor `/actuator/prometheus` em `application-local.yaml` das duas apps (restrito
      ao profile local; `prod` continua só com `health`) — decisão registrada em `proposal.md`
      (revisão de escopo) por conflitar com o Impact original ("zero mudança de app")
- [x] 1.4.3 Validar `mvn compile` nas duas apps e confirmar `hikaricp_connections_pending`
      real via `curl http://localhost:8080/actuator/prometheus` e `:8081` contra o ambiente
      local rebuildado

## 2. Escolha e setup da ferramenta de carga

- [x] 2.1 Avaliar ferramentas (k6/Gatling/Locust) contra os requisitos de D2/D3: suporte a
      kill switches automáticos configuráveis (threshold de erro, p99, lag externo) e
      capacidade de classificar/discriminar códigos de erro (409 vs. 5xx vs. timeout) —
      Gatling escolhido (JVM/Maven, integra com o resto do monorepo; kill switches são
      externos ao processo de carga de qualquer forma, ver 4.5/5.3/6.3/6.4)
- [x] 2.2 Registrar a escolha e o racional — `testes-carga/README.md`, seção "Ferramenta
      escolhida"
- [x] 2.3 Criar a estrutura de diretório da ferramenta de carga (`testes-carga/` na raiz do
      monorepo, paralelo a `apps/`/`infra/`) com README explicando como rodar cada cenário

## 3. Convenção de massa de teste (D6)

- [x] 3.1 Implementar geração de `idAutorizacaoEmpresa` no formato `LOADTEST-{timestamp}-{seq}`
      em todos os cenários que criam autorização — `carga.support.MassaTeste`, usado pelos 3
      cenários
- [x] 3.2 Escrever script de limpeza pós-teste que localiza autorizações pelo prefixo
      `LOADTEST-` tanto nas partições quentes (0-888) quanto na faixa de expurgo (900-999) —
      `testes-carga/scripts/limpar-massa-teste.sql` (DELETE pela tabela-pai, Postgres localiza
      em qualquer partição)
- [x] 3.3 Validar que o script de limpeza funciona mesmo quando o teste foi interrompido por
      kill switch — validado com smoke test real (criar via contratocommand, confirmar via
      contratoquery, limpar, confirmar remoção); `rodar-cenario.sh` sempre chama a limpeza
      independente do código de saída do Gatling

## 4. Cenário isolado: contratocommand (escrita)

- [x] 4.1 Implementar cenário de carga para `POST /api/autorizacoes` (criação) com estratégia
      de ramp-up (D4) — `ContratocommandEscritaSimulation`, payload validado via smoke test
      real (HTTP 201)
- [x] 4.2 Implementar cenário de carga para `PATCH /api/autorizacoes/{id}/decisao` — mesma
      simulação, encadeado após a criação; validado via smoke test real (HTTP 200)
- [x] 4.3 Implementar cenário de carga para `PATCH /api/autorizacoes/{id}/cancelar` — mesma
      simulação, encadeado após a decisão; validado via smoke test real (HTTP 200)
- [x] 4.4 Instrumentar coleta de `hikaricp_connections_pending`, p99 de latência e taxa de
      erro classificada (D1/D3) durante a execução — pool via `/actuator/prometheus` (grupo
      1.4), p99 nativo do relatório Gatling, classificação via `ErroClassificador`
- [x] 4.5 Configurar kill switch de nível aplicação (D2) para este cenário —
      `scripts/kill-switch-monitor.sh`, acionado por `rodar-cenario.sh`

## 5. Cenário isolado: contratoquery (leitura)

- [x] 5.1 Implementar cenário de carga para `GET /api/autorizacoes` com estratégia de
      ramp-up (D4) — `ContratoqueryLeituraSimulation`, query validada via smoke test real
      (HTTP 200)
- [x] 5.2 Instrumentar coleta de `hikaricp_connections_pending`, p99 de latência e taxa de
      erro classificada (D1/D3) durante a execução — mesmo mecanismo do grupo 4
- [x] 5.3 Configurar kill switch de nível aplicação (D2) para este cenário — mesmo
      `kill-switch-monitor.sh`

## 6. Cenário composto: jornada completa

- [x] 6.1 Implementar cenário que encadeia criação → decisão → observa o pipeline assíncrono
      completo, com carga em patamar fixo sustentado, não ramp-up (D4) —
      `JornadaCompostaSimulation` (`constantUsersPerSec`)
- [x] 6.2 Instrumentar coleta de profundidade de fila SQS (`SQS-eventos-autorizacao`,
      `SQS-temporizacao-autorizacao`) e lag de consumer group Kafka (`eventos-consumer`) —
      `scripts/fila-lag-monitor.sh` (aws cli contra Floci + `kafka-consumer-groups --describe`
      via `docker exec`)
- [x] 6.3 Configurar kill switch de nível fila/lag (D2) para este cenário —
      `fila-lag-monitor.sh`, acionado por `rodar-cenario.sh --com-fila-lag`
- [x] 6.4 Configurar kill switch de nível host (CPU/mem) aplicável a todos os cenários —
      incluído em `kill-switch-monitor.sh` (agregado de `docker stats`), roda para os 3
      cenários

## 7. Execução de baseline e relatório

- [x] 7.1 Rodar cenário isolado do `contratocommand` em baseline (sem recalibrar tetos) e
      registrar TPS de criação/cancelamento/decisão — executado de verdade (ramp 1→50
      usuários/s, 5 min): 22.950 requisições, **0 falhas**, p99=364ms, throughput médio
      76,5 req/s (~25,5 ciclos completos/s). **Achado**: o teto de ramp usado (50/s) não foi
      suficiente para encontrar o ponto de colapso real — o sistema aguentou toda a faixa
      testada sem degradação sustentada (nenhum kill switch acionado). Não é "TPS máximo",
      é "TPS mínimo confirmado sem colapso" — precisa de um ramp com teto mais alto para
      achar o joelho da curva de verdade (ver relatório completo em
      `testes-carga/relatorios/ContratocommandEscritaSimulation-20260823-210637.md` e HTML
      em `testes-carga/target/gatling/contratocommandescritasimulation-20260824000641723/`)
- [x] 7.2 Rodar cenário isolado do `contratoquery` em baseline e registrar TPS de consulta —
      executado de verdade (ramp 1→50 usuários/s, 5 min): 7.650 requisições, **0 falhas**,
      p99=18ms, max=177ms, throughput médio 25,5 req/s. **Ressalva importante**: o Postgres
      local está praticamente vazio nesta execução — a massa sintética representativa
      (~276 mil linhas, `gerar-massa-sintetica-representativa.sql`) documentada em
      `apps/contratoquery/CLAUDE.md` (armadilha 8, ~180-200ms ponta a ponta com volume real)
      não foi carregada. Este resultado mede a listagem contra base quase vazia, não o
      cenário de gargalo já conhecido — não deve ser lido como "o teto real da listagem"
      (relatório em `testes-carga/relatorios/ContratoqueryLeituraSimulation-20260823-211222.md`)
- [x] 7.3 Rodar cenário composto em baseline e registrar TPS síncrono + comportamento de
      lag/profundidade de fila ao longo do tempo — executado de verdade (patamar fixo de 5
      usuários/s, 10 min): 6.000 requisições, **0 falhas**, throughput 10 req/s (bate exato
      com o patamar configurado), p99=18ms. **Fila SQS e lag Kafka ficaram em 0 durante toda
      a execução** (monitorado a cada 5s por `fila-lag-monitor.sh`) — o pipeline assíncrono
      (`autorizacaostatus-producer`→Kafka→`eventos-consumer`) absorveu a carga em tempo real
      nesse patamar, sem acumular atraso. Nota: erro cosmético de permissão no `echo` do
      `kill-switch-monitor.sh` ao final da execução (processo já finalizando) — não afetou
      resultado nem limpeza (relatório em
      `testes-carga/relatorios/JornadaCompostaSimulation-20260823-211801.md`)
- [x] 7.4 Gerar relatório final incluindo: configuração vigente usada (pool, concorrência de
      listener/consumer), ressalva de ambiente local (Requirement "Escopo de ambiente local
      sem extrapolação para produção"), e se os limites de recursos (D5) estavam aplicados —
      `testes-carga/relatorios/RESUMO-baseline-2026-08-23.md`, consolidando as 3 execuções e
      registrando explicitamente o que os resultados NÃO provam (colapso não encontrado,
      listagem sem massa representativa, buckets de erro não exercitados)
- [x] 7.5 Rodar o script de limpeza (3.2) após cada execução — confirmado nos 3 logs de
      execução (`rodar-cenario.sh` chama a limpeza sempre, independente do resultado)

## 8. Validação final

- [x] 8.1 Confirmar que nenhum cenário de carga foi executado sem os limites de recursos de
      D5 aplicados — confirmado: os 3 baselines rodaram depois do grupo 1 (limites aplicados
      e validados via `docker stats` antes de qualquer execução)
- [x] 8.2 Confirmar que os três relatórios (4, 5, 6) discriminam erro-por-design de colapso
      real (D3), não uma taxa de erro agregada única — mecanismo (`ErroClassificador`)
      implementado e presente nos 3 relatórios; **ressalva**: como as 3 execuções tiveram 0
      falhas, o mecanismo nunca precisou discriminar de fato em execução real — só validado
      via smoke test manual (409 não ocorreu nas execuções de carga). Ver limitação 3 do
      `RESUMO-baseline-2026-08-23.md`
- [x] 8.3 Revisar se os limiares numéricos usados nos kill switches precisam de calibração —
      **rodada 2 (agressiva) executada**: ramp de escrita/leitura elevado para 10→400
      usuários/s e jornada composta para patamar fixo 30/s, com massa sintética
      representativa (~281 mil linhas) carregada antes da leitura. Resultado:
      - `contratocommand`: teto real ainda não encontrado — ~450 req/s sustentado antes de
        esbarrar em esgotamento de porta efêmera do **gerador de carga** (não do servidor);
        primeiro 409 real de idempotência sob concorrência genuína observado (D3 funcionou
        como desenhado).
      - `contratoquery`: **colapso real encontrado** — p99 salta de 18ms (banco vazio) para
        52 segundos com massa representativa carregada, confirmando empiricamente o gargalo
        já documentado (`CLAUDE.md`, armadilha 8). `IOException: Premature close` é o sinal
        real de saturação a usar como limiar (distinto de `BindException`, que é ruído do
        cliente).
      - Jornada composta: 60 req/s sustentado, 0 falhas, fila/lag em 0 — pipeline assíncrono
        segue sem sinal de colapso mesmo 6x acima do patamar inicial.
      Detalhe completo em `testes-carga/relatorios/RESUMO-baseline-2026-08-23.md`, seção
      "Rodada 2". Recalibração numérica fina dos limiares (`LOADTEST_LIMITE_*`) fica como
      trabalho futuro pontual — já há sinal real (p99/Premature close) para basear isso,
      diferente do estado antes desta rodada.
