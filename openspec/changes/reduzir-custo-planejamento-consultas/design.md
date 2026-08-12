## Context

`autorizacoes` é particionada por `LIST (id_particao_conta)` em 989 partições. O PostgreSQL
planeja a consulta considerando cada partição que não conseguir podar; o custo desse
planejamento é pago **a cada chamada**, em CPU, e é linear no número de partições consideradas.

As medições que motivam esta mudança estão na seção "Why" do `proposal.md`. O ponto central: com
a tabela contendo 24 linhas, a listagem gasta 147,6 ms planejando e 17,8 ms executando. A
proporção 8:1 entre planejar e executar é o problema.

Esta é uma change de **investigação antes de decisão**. O spike já feito estabelece que o ganho
existe; o que falta é confirmar que ele não vem acompanhado de perdas em consultas específicas.

## Goals / Non-Goals

**Goals:**

- Determinar se `force_generic_plan` é seguro e vantajoso para cada consulta das duas apps de
  leitura, medindo uma a uma em vez de generalizar a partir do spike.
- Estabelecer medida de referência de latência por endpoint, para que regressão deixe de passar
  despercebida.
- Responder se 889 partições quentes se justificam.

**Non-Goals:**

- Reverter ou reabrir a cascata de `fallback-consulta-autorizacao-expurgada`. Ela resolve um
  problema de **correção** (404 indevido), não de desempenho.
- Mudar contrato de API.
- Reescrever consultas para incluir a chave de particionamento onde ela não faz sentido — a
  listagem filtra por conta porque é isso que o negócio pede.

## Decisions

### Ambiente e massa de medição (tarefas 1.1–1.2)

Não existe ambiente com volume de produção acessível para este monorepo — só o PostgreSQL local
(Docker, `infra/local/postgres/`). **Decisão: gerar massa sintética local**, reproduzindo a
distribuição real (cada conta cai numa única partição quente, via `id_particao_conta` fixo por
conta — não linhas uniformes soltas) e um skew realista de autorizações por conta.

Script: `infra/local/postgres/gerar-massa-sintetica-representativa.sql`. Gerado em 2026-08-11:
**80.000 contas sintéticas, 276.521 linhas**, cobrindo as 889 partições quentes (nenhuma vazia),
skew de 1–3 autorizações para 90% das contas, 4–15 para 9%, e 16–150 (contas "pesadas") para o
1% restante — média 3,46/conta, máximo 150/conta. `ANALYZE` executado após a carga.

Isso satisfaz o requisito da capacidade (`desempenho-consulta-autorizacoes`) de que conclusão
sobre **escolha de plano e seletividade** não pode vir de base vazia — mas com uma ressalva
válida e registrada: é volume representativo de **distribuição**, não de **volume real de
produção** (que seguiria sendo desconhecido mesmo aqui, ver H2). As medições de planejamento
continuam válidas independentemente (não dependem de volume, só do número de partições).

### Medida de referência (tarefa 1.3) — antes de qualquer mudança, ambiente local, 276.521 linhas

Medido via `EXPLAIN (ANALYZE, BUFFERS)`, PostgreSQL 18 local, `plan_cache_mode = auto` (default):

| Consulta | Partições | Planning | Execution | Total |
|---|---|---|---|---|
| Listagem (`GET /api/autorizacoes`, conta com 150 linhas) | 989 | **144–150 ms** | **119–136 ms** | ~260–280 ms |
| N1 (id + partição exata) | 1 | 0,14 ms | 0,02 ms | ~0,16 ms |
| N2 (faixa de expurgo, `>= 900`) | 100 | 2,6 ms | 0,4 ms | ~3 ms |
| N3 (demais quentes, pior caso) | 888 | 29,5 ms | 14,8 ms | ~44 ms |

**Achado que revisa a premissa do `proposal.md`:** com 24 linhas (medição anterior), a execução da
listagem era desprezível (17,8 ms) frente ao planejamento (147,6 ms) — proporção 8:1. Com volume
representativo (276 mil linhas), a execução da listagem **deixa de ser desprezível**: 119–136 ms,
quase do tamanho do planejamento. A causa: `id_unico_conta_contratante` **não é** a chave de
particionamento (`id_particao_conta`), então nenhum plano — custom ou genérico — evita varrer
fisicamente as 889 partições quentes para aplicar o filtro. Com dado real nas partições, esse
scan deixa de ser gratuito. Isso é relevante para H1 e H2 abaixo: o problema da listagem não é só
planejamento.

Latência HTTP ponta a ponta (`GET /api/autorizacoes`, mesma conta, container Docker,
`plan_cache_mode = auto`, medida em 2026-08-11): **~200 ms** em regime (primeira chamada ~520 ms,
efeito de aquecimento de JVM/pool). Bem acima da soma de planning+execution medida via
`EXPLAIN` (~260–280 ms na conta, mas essa é single-shot; HTTP em regime já se beneficia de cache
de página/relcache quente) — a diferença residual é serialização JSON, Jetty e round-trip
Docker, não banco.

### H1 — `force_generic_plan` — CONFIRMADA E ADOTADA (tarefas 2.1–2.6)

**2.1 — Mecanismo comprovado para todas as conexões do pool.** `spring.datasource.hikari.
connection-init-sql: SET plan_cache_mode = 'force_generic_plan'` roda uma vez por **conexão
física** (não por chamada, não só a primeira) — comprovado, não suposto, por
`PlanCacheModeHikariIntegrationTest` (`arj-contratoquery`): abre 4 conexões físicas
simultâneas de um pool com `minimumIdle == maximumPoolSize == 4` e confirma `SHOW
plan_cache_mode = force_generic_plan` em cada uma via `SHOW`. `ALTER ROLE` foi descartado
por afetar também sessões `psql` manuais de diagnóstico, fora do controle desta change.

**2.2–2.4 — Medições sob `force_generic_plan`** (PostgreSQL 18 local, 276.521 linhas,
`PREPARE`/`EXECUTE` repetido 6× por consulta — mesma metodologia do spike original, que já
mostrou que o custom nunca migra sozinho neste schema):

| Consulta | Partições | 1ª exec. (planning+exec) | Exec. seguintes (planning+exec) | vs. `auto` (regime) |
|---|---|---|---|---|
| Listagem (989, sem poda) | 989 | 30,6+78,3 ms | **0,2+77 ms** (média) | auto regime: 30+79 ms → **~30 ms/chamada economizados**, execução não muda |
| N1 (1 partição) | 1 | 33,5+0,06 ms | **0,13+0,03 ms** | auto regime: 0,04–0,08+0,01 ms → **piora ~0,05–0,1 ms**, sempre sub-ms |
| N2 (100) | 100 | 36,3+0,43 ms | **0,11+0,3 ms** | — |
| N3 (888, pior caso) | 888 | 37,1+18,1 ms | **0,22+6,7 ms** | auto: 29,5+14,8=44 ms → **~6,9 ms, 6,4× mais rápido** |
| `existsByIdAutorizacao_...` (contratocommand, 1 partição) | 1 | 23,2+0,10 ms | **0,14+0,03 ms** | mesmo padrão de N1: piora sub-ms |
| `moverParaParticao` (contratocommand, UPDATE, 1 partição) | 1 | 21,0+0,22 ms | **0,13+0,02 ms** | mesmo padrão de N1: piora sub-ms |
| `findByIdAutorizacao` (contratocommand, sem poda) | 989 | 32,8+6,6 ms (auto, todas execuções — nunca migra) | **0,3+6,5 ms** | auto: ~32+6,5=38,5 ms → **~6,8 ms, 5,6× mais rápido** |

**2.5 — Consulta que piora, procurada ativamente e encontrada:** as três consultas que **já**
podavam para 1 partição sozinhas (N1, `existsBy...`, `moverParaParticao`) pagam, sob plano
genérico já cacheado, um planejamento **maior** que o custom em regime (~0,03–0,16 ms contra
~0,04–0,08 ms) — o `force_generic_plan` monta um plano que precisa de poda em tempo de execução
mesmo quando o valor do parâmetro tornaria a poda em tempo de planejamento trivial. A regressão é
real e confirma o risco descrito no `proposal.md`, mas seu tamanho absoluto (dezenas a centenas
de **micro**segundos) é irrelevante frente ao custo de rede/serialização de qualquer chamada HTTP
real (dezenas de **mili**segundos, ver medida ponta a ponta abaixo). Nenhuma consulta testada
piorou de forma perceptível pelo chamador.

**2.6 — Decisão: adotar `force_generic_plan` nas duas apps de leitura E escrita**
(`arj-contratoquery` e `arj-contratocommand`, via `hikari.connection-init-sql`). Aplicado em
`application.yaml` de ambas em 2026-08-11. Racional: ganhos de 5–6× nas consultas sem poda
(N3, `findByIdAutorizacao`) e ~30 ms/chamada na listagem superam, em várias ordens de grandeza,
a perda sub-milissegundo nas consultas já podadas. `arj-contratocommand` foi incluída apesar de
suas escritas mais comuns já podarem sozinhas — o ganho aparece em `findByIdAutorizacao` (sem
filtro de partição) e em qualquer consulta futura que não pode.

Confirmado sem regressão após rebuild + restart dos containers: `mvn test` das duas apps verde
(evidência em `tasks.md`), containers `healthy`, HTTP ponta a ponta da listagem **~180–200 ms**
(igual ao `auto`, dentro do ruído — esperado, já que a listagem é dominada por execução, não por
planejamento, ver achado da medida de referência acima) e N1 feliz (`GET /{id}` de autorização
ativa) **~43 ms** ponta a ponta, sem erro nos logs.

**O que isso muda para `fallback-consulta-autorizacao-expurgada`:** o pior caso da cascata (N3,
id inexistente) cai de ~44 ms para ~6,9 ms em nível de consulta — a flag `contratoquery.consulta.
busca-em-particoes-inesperadas` perde a justificativa de **custo**; ver seção 5 abaixo.

### H2 — número de partições quentes — BLOQUEADA por falta de dado de negócio (tarefas 3.1–3.3)

**3.1 — Volume esperado de contas: não existe no repositório.** Busca em `docs/` (incluindo
`modelo-dados-e-dados-poc-testada-para-essa-implementacao.md`, que é o único documento com
números) não encontra volume esperado de contas nem política de retenção além do ciclo de ~2
anos do buffer de expurgo (100 semanas). O documento fala em "escalar até 10B registros" como
ambição de design, não como projeção de negócio. **Sem esse número, H2 não tem como ser decidida
nesta change** — não é uma lacuna de medição, é ausência do dado que a decisão depende.

**3.2 — Custo de planejamento e execução em função do número de partições, medido:**

```
custo de planejamento ≈ 0,03–0,04 ms por partição considerada (auto, catálogo quente;
                          medido variando de 100 a 989 partições)
custo de execução (consulta que NÃO poda pela chave de partição, ex. a listagem)
                        ≈ 0,088 ms por partição, MESMO sob plano genérico
custo de execução (consulta que poda por índice em cada partição, ex. findByIdAutorizacao)
                        ≈ 0,0075 ms por partição sob plano genérico
```

**Achado que revisa a relação entre H1 e H2 do `proposal.md`:** o `proposal.md` supunha que H2
"barateia toda consulta do sistema" de forma **adicional** a H1. A medição mostra algo mais
específico: depois de H1 adotada, o custo de **planejamento** já está amortizado a quase zero por
conexão. O que sobra — e que só H2 resolveria — é o custo de **execução** de consultas que não
podam pela chave de partição, como a listagem (~0,088 ms/partição × 889 = **~78 ms**, o número
efetivamente medido). H2 deixou de ser "mais uma alavanca genérica" e passou a ser
"a única alavanca restante para o tempo de execução da listagem" — mais concentrada, não menos
importante.

**Achado fora do escopo desta change, registrado para decisão futura:** `id_particao_conta` é
função determinística de `id_unico_conta_contratante` (mesmo hash usado na escrita). Filtrar a
listagem também por `id_particao_conta` (calculado em Java a partir do parâmetro já recebido)
podaria para 1 partição sem mudar o contrato de API nem o filtro de negócio — mas o `proposal.md`
declara "reescrever consultas para incluir a chave de particionamento" como **Non-Goal** desta
change, e a duplicação da lógica de hash (hoje só em `arj-contratocommand`) para o
`arj-contratoquery` é decisão de arquitetura própria. Não implementado aqui; candidato forte a
change própria, com potencial de eliminar os ~78 ms de execução da listagem sem esperar por H2.

**3.3 — Decisão: não abrir change de migração de partições agora.** H2 permanece uma alavanca
válida e mensurável (fórmula acima), mas (a) falta o dado de negócio que dimensionaria o alvo,
e (b) a alavanca da listagem com melhor relação esforço/ganho é o achado do parágrafo anterior
(filtrar por partição derivada), não reduzir o número de partições. Reabrir quando houver projeção
real de contas/volume — a fórmula de custo já registrada elimina a necessidade de remedir do zero.

### H3 — O ganho de índice é secundário enquanto o planejamento dominar — CONFIRMADA, com nuance

A capacidade `desempenho-consulta-autorizacoes` já exigia que o plano use índice em vez de
varredura sequencial — e está correta, mas incompleta. Ela endereça a *execução* por linha, e a
medição desta change (seção "Medida de referência" acima) mostra que a execução da listagem não
é pequena por ser sequencial-sem-índice — é grande porque **889 partições são fisicamente
visitadas** mesmo quando a maioria não tem linha nenhuma da conta filtrada. Índice ajuda dentro de
cada partição visitada; não evita a visita. Corrigir a migration do índice (v1.0.6) continua
necessário e insuficiente — a seção 4 abaixo aplica os requisitos novos da capacidade para exigir
que **as duas fatias** (planejamento e execução) sejam avaliadas, não só uma.

## Risks / Trade-offs

- **Medir em ambiente local não prova nada sobre produção** → mitigado parcialmente: a massa
  sintética (276.521 linhas, distribuição realista por conta/partição) permite medir execução e
  seletividade, não só planejamento. Persiste como risco residual: é volume sintético, não
  volume real de produção (que continua desconhecido — ver H2).

- **`force_generic_plan` é global à sessão** → confirmado, e por isso a decisão em 2.6 foi tomada
  consulta a consulta antes de ligar globalmente, não por extrapolação do spike.

- **Reduzir partições é irreversível na prática** → não avaliado nesta change (H2 ficou bloqueada
  antes de chegar a essa decisão, por falta do dado de negócio que a antecede).

## Open Questions

- ~~Qual o volume real esperado por conta e por partição?~~ **Continua em aberto** — não é mais
  bloqueio de H1 (resolvida com massa sintética), mas segue bloqueando H2 (ver seção 3.1/3.3).
- ~~O `arj-contratocommand` também deve adotar plano genérico?~~ **Resolvido: sim** — ver 2.6.
- ~~Existe ambiente com volume representativo, ou é preciso gerar massa sintética?~~ **Resolvido:
  massa sintética local**, script em `infra/local/postgres/gerar-massa-sintetica-representativa.sql`.
- Vale abrir change própria para filtrar a listagem também por `id_particao_conta` derivado (achado
  da seção H2)? Vetada como Non-Goal *desta* change; decisão de abrir ou não fica para depois.
