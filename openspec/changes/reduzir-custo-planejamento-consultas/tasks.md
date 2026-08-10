## 1. Preparar terreno de medição

- [ ] 1.1 Determinar onde medir com volume representativo: ambiente existente ou massa
      sintética. Sem isso, nada abaixo que envolva escolha de plano tem valor — ver `design.md`
      › Riscos.
- [ ] 1.2 Se for massa sintética, gerar volume que reproduza a distribuição real por conta e por
      partição, não linhas uniformes.
- [ ] 1.3 Registrar a medida de referência atual (antes de qualquer mudança) para os endpoints:
      `GET /api/autorizacoes`, `GET /api/autorizacoes/{id}` nos três caminhos da cascata.

## 2. Validar H1 — `force_generic_plan`

- [ ] 2.1 Determinar a forma de aplicar `plan_cache_mode` que valha para **todas** as conexões
      do pool HikariCP (parâmetro de conexão, `connection-init-sql` ou `ALTER ROLE`) — e provar
      que vale, não supor.
- [ ] 2.2 Medir a listagem paginada com ordenação sob plano genérico. É a consulta de maior
      tráfego e a que o spike **não** cobriu.
- [ ] 2.3 Medir os três níveis da cascata de `GET /{id}` sob plano genérico.
- [ ] 2.4 Medir as consultas de escrita do `arj-contratocommand` (`existsBy...` da idempotência,
      busca por chave composta, movimentação de partição).
- [ ] 2.5 Procurar ativamente consulta que **piore** com plano genérico. Uma medição que só
      confirma a hipótese não a testou.
- [ ] 2.6 Decidir adoção com base nas medições, e registrar a decisão no `design.md`.

## 3. Validar H2 — número de partições quentes

- [ ] 3.1 Levantar volume esperado de contas e a política de retenção que o expurgo pressupõe.
- [ ] 3.2 Calcular o custo de planejamento em função do número de partições, para dimensionar o
      ganho de reduzi-lo.
- [ ] 3.3 Se houver ganho relevante, abrir change própria para a migração — mudar número de
      partições é migração de dados com janela, não configuração.

## 4. Atualizar a capacidade

- [ ] 4.1 Aplicar os requisitos novos de `desempenho-consulta-autorizacoes`: avaliação que
      reporta planejamento e execução separadamente, e medida de referência com contexto.
- [ ] 4.2 Registrar as medidas de referência obtidas em 1.3 e 2.x onde possam ser encontradas e
      comparadas depois.

## 5. Reavaliar o que dependia destes números

- [ ] 5.1 Se o plano genérico for adotado, revisitar a flag
      `contratoquery.consulta.busca-em-particoes-inesperadas` da change
      `fallback-consulta-autorizacao-expurgada`: a justificativa de **custo** desaparece, resta
      a de diagnóstico. Decidir se a flag continua existindo.
- [ ] 5.2 Revisitar a armadilha 8 do `CLAUDE.md` do `contratoquery`, que hoje afirma que o custo
      dominante é planejamento — se deixar de ser, a armadilha precisa mudar, não sumir.
