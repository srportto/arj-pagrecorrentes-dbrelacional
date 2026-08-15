## 1. Preparar terreno de medição

- [x] 1.1 Determinar onde medir com volume representativo: ambiente existente ou massa
      sintética. Sem isso, nada abaixo que envolva escolha de plano tem valor — ver `design.md`
      › Riscos. **Decisão: massa sintética local** — não há ambiente de produção acessível neste
      monorepo.
- [x] 1.2 Se for massa sintética, gerar volume que reproduza a distribuição real por conta e por
      partição, não linhas uniformes. Script `infra/local/postgres/gerar-massa-sintetica-representativa.sql`:
      80.000 contas, 276.521 linhas, cada conta numa única partição (0–888), skew realista
      (90% das contas com 1–3 autorizações, 9% com 4–15, 1% "pesadas" com 16–150).
- [x] 1.3 Registrar a medida de referência atual (antes de qualquer mudança) para os endpoints:
      `GET /api/autorizacoes`, `GET /api/autorizacoes/{id}` nos três caminhos da cascata. Ver
      `design.md` › "Medida de referência". **Achado**: com volume representativo, a execução da
      listagem deixa de ser desprezível (119–136 ms) — só o planejamento (144–150 ms) tinha sido
      medido antes, com base quase vazia.

## 2. Validar H1 — `force_generic_plan`

- [x] 2.1 Determinar a forma de aplicar `plan_cache_mode` que valha para **todas** as conexões
      do pool HikariCP (parâmetro de conexão, `connection-init-sql` ou `ALTER ROLE`) — e provar
      que vale, não supor. **`hikari.connection-init-sql`**, provado por
      `PlanCacheModeHikariIntegrationTest` (`contratoquery`) — 4 conexões físicas
      simultâneas, todas com `SHOW plan_cache_mode = force_generic_plan`.
- [x] 2.2 Medir a listagem paginada com ordenação sob plano genérico. É a consulta de maior
      tráfego e a que o spike **não** cobriu. Medido: economia de ~30 ms/chamada em regime
      (planejamento cai a ~0,2 ms); execução não muda (~77–79 ms, real I/O sobre 889 partições).
- [x] 2.3 Medir os três níveis da cascata de `GET /{id}` sob plano genérico. N3 (pior caso):
      ~44 ms → ~6,9 ms (6,4×). N1/N2 medidos, ver `design.md`.
- [x] 2.4 Medir as consultas de escrita do `contratocommand` (`existsBy...` da idempotência,
      busca por chave composta, movimentação de partição). `findByIdAutorizacao` (sem poda):
      ~38,5 ms → ~6,8 ms (5,6×). `existsBy...`/`moverParaParticao` (já podadas): ver 2.5.
- [x] 2.5 Procurar ativamente consulta que **piore** com plano genérico. Uma medição que só
      confirma a hipótese não a testou. **Encontrada**: N1, `existsBy...` e `moverParaParticao`
      (já podavam para 1 partição sozinhas) pagam planejamento cacheado maior sob genérico
      (~0,03–0,16 ms contra ~0,04–0,08 ms) — regressão real, porém sempre sub-milissegundo.
- [x] 2.6 Decidir adoção com base nas medições, e registrar a decisão no `design.md`. **Adotado**
      em `contratoquery` e `contratocommand` (`application.yaml`, 2026-08-11). Validado
      pós-rebuild: containers `healthy`, `mvn test` verde nas duas apps (68 e 167 testes, 0
      falhas), listagem HTTP ~180–200 ms (dentro do ruído do `auto`), N1 feliz ~43 ms ponta a
      ponta, sem erro nos logs.

## 3. Validar H2 — número de partições quentes

- [x] 3.1 Levantar volume esperado de contas e a política de retenção que o expurgo pressupõe.
      **Não existe no repositório** — busca em `docs/` não encontra projeção de negócio, só a
      ambição de design "escalar até 10B registros". H2 fica bloqueada por ausência do dado, não
      por falta de medição.
- [x] 3.2 Calcular o custo de planejamento em função do número de partições, para dimensionar o
      ganho de reduzi-lo. Fórmula registrada em `design.md`: ~0,03–0,04 ms/partição de
      planejamento; ~0,088 ms/partição de execução (consulta sem poda); ~0,0075 ms/partição de
      execução (consulta que poda por índice). **Achado**: depois de H1 adotada, é a fórmula de
      *execução* que importa — planejamento já está amortizado.
- [x] 3.3 Se houver ganho relevante, abrir change própria para a migração — mudar número de
      partições é migração de dados com janela, não configuração. **Decisão: não abrir agora** —
      falta o dado de 3.1, e a alavanca com melhor relação esforço/ganho para a listagem é outra
      (filtrar por `id_particao_conta` derivado — achado fora de escopo, registrado em
      `design.md` para decisão futura).

## 4. Atualizar a capacidade

- [x] 4.1 Aplicar os requisitos novos de `desempenho-consulta-autorizacoes`: avaliação que
      reporta planejamento e execução separadamente, e medida de referência com contexto.
      Aplicado em toda medição desta change (seções 1–3 acima e `design.md`) — nenhuma tabela
      mistura os dois tempos, todas indicam ambiente/volume/data.
- [x] 4.2 Registrar as medidas de referência obtidas em 1.3 e 2.x onde possam ser encontradas e
      comparadas depois. Registradas em `design.md` (`reduzir-custo-planejamento-consultas`),
      datadas (2026-08-11), com ambiente e volume explícitos.

## 5. Reavaliar o que dependia destes números

- [x] 5.1 Se o plano genérico for adotado, revisitar a flag
      `contratoquery.consulta.busca-em-particoes-inesperadas` da change
      `fallback-consulta-autorizacao-expurgada`: a justificativa de **custo** desaparece, resta
      a de diagnóstico. Decidir se a flag continua existindo. **Decisão: mantida**, comentário do
      `application.yaml` atualizado para refletir que a justificativa agora é só diagnóstico.
- [x] 5.2 Revisitar a armadilha 8 do `CLAUDE.md` do `contratoquery`, que hoje afirma que o custo
      dominante é planejamento — se deixar de ser, a armadilha precisa mudar, não sumir.
      Reescrita (CLAUDE.md + AGENTS.md, idênticos): agora descreve as duas fatias
      (planejamento amortizado por H1, execução ainda linear no número de partições) e aponta
      H2 como a alavanca restante.
