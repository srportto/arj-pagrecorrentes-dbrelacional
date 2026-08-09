# Evidência de `EXPLAIN ANALYZE` — índice composto `idx_autorizacoes_conta_status_data`

> Change: `blindar-superficie-leitura` — tasks 5.1, 5.3, 5.4, 5.5, 5.6
> Data: 2026-08-09
> Banco: `postgres18-kiq` (PostgreSQL 18) — `db-csp-postgres`
> Tabela: `autorizacoes` — particionada por LIST em `id_particao_conta` (0–899 e 900–999) — **989 partições**
> Volume de dados no momento da captura: **21 linhas** distribuídas em 5 partições (a maior, `autorizacoes_pa6`, com 17 linhas do `id_unico_conta_contratante = 550e8400-e29b-41d4-a716-446655440000`)

---

## Resumo

Os três `EXPLAIN ANALYZE` abaixo foram capturados sobre a mesma query da listagem
(`GET /api/autorizacoes` com filtro por `idUnicoContaContratante`, paginação 20, ordenação
`dataHoraInclusao DESC`), em três estados:

1. **Sem índice** (índice composto dropado para baseline) — `explain-before.sql`
2. **Com índice composto aplicado** — `explain-after.sql`
3. **Com índice composto aplicado e filtro de `status`** — `explain-after-with-status.sql`

Os tempos de execução foram **13.800 ms (antes)** → **13.391 ms (depois, sem status)** →
**12.891 ms (depois, com `status = 1`)** — variação de ~3–7 % entre os cenários, **dentro da
margem de ruído de uma única execução** sobre 21 linhas em uma tabela vazia.

**Achado crítico:** nos três cenários, o plano executa **989 `Seq Scan`** (um por partição) e
**zero `Index Scan`**. O índice composto `idx_autorizacoes_conta_status_data` **não está sendo
utilizado pelo planejador** em nenhum dos caminhos de consulta exercidos.

---

## Tabela comparativa

| Métrica | Antes (sem índice) | Depois (com índice) | Depois com `status = 1` |
|---|---|---|---|
| Arquivo de evidência | [explain-before.sql](explain-before.sql) | [explain-after.sql](explain-after.sql) | [explain-after-with-status.sql](explain-after-with-status.sql) |
| **Tempo de execução (ms)** | **13.800** | **13.391** | **12.891** |
| **Tempo de planejamento (ms)** | 80.897 | 79.785 | 81.063 |
| **Custo estimado (top node)** | 39.03..39.08 | 39.03..39.08 | 38.64..38.69 |
| **Tipo de plano** | 989 × `Seq Scan` + 1 × `Sort` + 1 × `Limit` | 989 × `Seq Scan` + 1 × `Sort` + 1 × `Limit` | 989 × `Seq Scan` + 1 × `Sort` + 1 × `Limit` |
| **Partições varridas** | 989 (todas) | 989 (todas) | 989 (todas) |
| **`Index Scan` ocorrências** | 0 | 0 | 0 |
| **`Seq Scan` ocorrências** | 989 | 989 | 989 |
| **`Buffers: shared hit` (top)** | 10 | 10 | 10 |
| **Linhas retornadas** | 17 | 17 | 3 |

---

## Observações por cenário

### Antes (sem índice)

O planejador varre **todas as 989 partições** com `Seq Scan`. Em cada partição vazia (a maioria
— 984 das 989), o custo é `0.00..0.00 rows=1` e o filtro `id_unico_conta_contratante = …` é
aplicado, retornando 0 linhas. Apenas `autorizacoes_pa6` (com 17 linhas relevantes) e
`autorizacoes_pe951` (com 1 linha) mostram `Buffers: shared hit=1` real. O `Sort` final agrega
as 17 linhas e o `Limit` corta em 20 (que é maior que o resultado, por isso todos passam).

### Depois (com índice, sem status)

Plano **idêntico** ao antes. O índice composto `idx_autorizacoes_conta_status_data` foi criado
em cada uma das 5 partições com dados e na tabela-mãe via `CREATE INDEX CONCURRENTLY` +
`ALTER INDEX … ATTACH PARTITION`. Está válido (`indisvalid = t`, `indisready = t`) e propagado.
Mas o planejador **não o escolhe** — prefere `Seq Scan` em cada partição.

**Por quê:** com apenas 17 linhas em uma partição e 0 nas outras 988, o custo estimado de
`Index Scan` (descida na B-tree + 17 heap fetches) é maior que `Seq Scan` (que nas vazias
simplesmente lê zero páginas). O otimizador toma a decisão correta **para este volume**.

### Depois (com `status = 1`)

Mesma situação. Retorna 3 linhas (as do status 1 dentro do mesmo `id_unico_conta_contratante`).
Tempo até ligeiramente menor (12.891 ms) — provavelmente ruído. `Index Scan` continua ausente.

---

## Causa raiz do índice não ser usado

A decisão do planejador é dominada por **dois fatores combinados** que tornam o `Seq Scan`
mais barato que o `Index Scan` neste cenário:

1. **Volume mínimo por partição.** A única partição não-vazia (`autorizacoes_pa6`) tem 17
   linhas. O custo de ler 17 entradas no índice, ir ao heap 17 vezes, ordenar e limitar é
   próximo (ou maior) que ler 17 linhas sequenciais da partição — onde os blocos já estão
   quentes em buffer (`shared hit` alto).

2. **Pruning não acontece.** O filtro é `id_unico_conta_contratante`, **não** `id_particao_conta`
   (a chave de particionamento). O Postgres não consegue podar partições por esse predicado, então
   visita todas as 989. Em cada uma, o índice composto é **inútil para partições vazias**
   (não há nada para indexar) e **desnecessário para a única partição com 17 linhas**.

O índice composto **passa a pagar** quando a tabela crescer e cada partição tiver volume
suficiente para que o custo de `Index Scan` (logarítmico) seja menor que o de `Seq Scan`
(linear). Com 17 linhas, isso não acontece — o ponto de virada costuma estar em algumas
centenas ou milhares de linhas por partição, dependendo de `random_page_cost` e
`effective_cache_size`.

---

## Conclusão

À luz do observado, **a spec `desempenho-consulta-autorizacoes` não está plenamente atendida
neste momento**, embora a migration do índice tenha sido aplicada com sucesso:

- ✅ Índice criado sem bloquear escrita (`CREATE INDEX CONCURRENTLY` em cada partição).
- ✅ Índice propagado para todas as partições com dados (5 de 989) e para a tabela-mãe.
- ✅ Cenário "índice cobre também o filtro por status" está **previsto na estrutura do índice**:
  a coluna `status` é a 2ª do composto e o índice contém a ordem correta
  `(conta, status, data)`.
- ❌ **O plano de execução ainda indica varredura sequencial** — o `EXPLAIN ANALYZE` mostra
  989 `Seq Scan` e 0 `Index Scan`, mesmo após a criação do índice. O cenário da spec que
  diz "NÃO SHALL indicar varredura sequencial das partições" **falha neste baseline**.

**O índice composto é o desenho correto** — a ordem de colunas, a direção DESC e a forma
de propagação estão alinhados com o uso. **A evidência atual não comprova melhoria porque o
volume de teste é insuficiente** (21 linhas, 5 partições com dados). Para validar o índice
é preciso:

1. **Popular a tabela com volume representativo** (recomendação: ≥ 1.000 linhas em ao menos
   10 partições distintas, distribuídas entre 0–899 e 900–999).
2. **Re-capturar `EXPLAIN (ANALYZE, BUFFERS)`** nas três variantes, sem e com `status`.
3. **Confirmar se o plano muda** para `Index Scan` à medida que o volume cresce.

**Lacunas e próximos passos sugeridos:**

- **Popular dados sintéticos** via `INSERT … SELECT generate_series(…)` em uma sessão
  controlada, ou via carga real do `arj-contratocommand` em loop, e re-executar a captura.
- **Considerar `enable_indexscan = off` e `enable_seqscan = off` em uma sessão de teste**
  apenas para confirmar que o índice **é capaz** de ser usado (sanity check de viabilidade,
  não de produção) — se mesmo assim o planejador escolher outra rota, há problema mais
  profundo.
- **Considerar partial indexes** por status (ex.: índice apenas para `status IN (1, 4, 6)`)
  caso a cardinalidade de status seja baixa e o filtro esteja sempre presente.
- **Revalidar `effective_cache_size` e `random_page_cost`** — em SSD, `random_page_cost = 1.1`
  é o padrão recomendado; valores altos desfavorecem `Index Scan`.
- **Reavaliar a forma do índice** se o `Index Scan` continuar ausente com volume real:
  a coluna `id_particao_conta` (chave de particionamento) **não está no índice**, e
  particionar o próprio índice por ela pode ajudar o pruning. Hoje a chave de partição
  não aparece em nenhum dos predicados da query, então a inclusão dela no índice não muda
  este caso, mas é uma decisão a revisitar com volume real.

---

## Metadados da captura

- **Container:** `postgres18-kiq` (`postgres18-kiq-extras-partman_cron_vector:1.0`).
- **Versão do Postgres:** 18 (extraída da imagem).
- **`id_unico_conta_contratante` usado:** `550e8400-e29b-41d4-a716-446655440000` (17 linhas
  presentes em `autorizacoes_pa6`).
- **Forma do índice:**
  `CREATE INDEX CONCURRENTLY idx_autorizacoes_<part>_<sufixo> ON autorizacoes_<part>
  (id_unico_conta_contratante, status, data_hora_inclusao DESC)` aplicado em
  `autorizacoes_pa6`, `autorizacoes_pa845`, `autorizacoes_pa863`, `autorizacoes_pa879`,
  `autorizacoes_pe951`, mais índice particionado na tabela-mãe
  `idx_autorizacoes_conta_status_data` com `ATTACH PARTITION` para os 5 índices filhos.
- **Forma de captura do "antes":** índice dropado em transação implícita (sem `BEGIN`/`COMMIT`
  para permitir `DROP INDEX CONCURRENTLY`-like em partições filhas), `EXPLAIN` capturado,
  índice recriado em seguida — conforme estratégia documentada na task 5.1.
