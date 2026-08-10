## Why

`GET /api/autorizacoes/{id}` devolve **404 para toda autorização em estado terminal**.

`ConsultarAutorizacaoService` deriva a partição do próprio UUID (`ReversibleUUIDv7.extract`),
que carrega a partição de **criação** — imutável. Desde a mudança `expurgo-estados-terminais`
(2026-08-09), toda autorização que chega a `CANCELADA`, `REJEITADA`, `EXPIRADA` ou `FINALIZADA`
é transferida para a partição de expurgo (`900–999`, balde semanal). A chave composta que a
consulta monta passa a apontar para uma partição onde a linha já não está.

```
criação                                    transição terminal
   │                                              │
   ▼                                              ▼
┌───────────────────────┐                ┌───────────────────────┐
│ partição QUENTE 0-888 │ ── expurgo ──▶ │ partição 900-999      │
│ = hash(conta)         │                │ = balde da semana     │
│ = embutida no UUIDv7  │                │ ≠ nada no UUID        │
└───────────────────────┘                └───────────────────────┘
          ▲                                         ▲
          └── extract() aponta aqui                 └── a linha está aqui → 404
```

Não é cenário de borda: é uma **categoria inteira** de autorizações, e o 404 é permanente até
a partição ser dropada. Verificado no ambiente local em 2026-08-10 — a autorização
`019fe8ef-…0006`, expirada com sucesso pelo `temporiza-autorizacao` às 00:00, reside na
partição 953 e é inconsultável por id.

É também um efeito colateral **não intencional e recente**: antes de `98287d6` (2026-08-09),
autorizações rejeitadas permaneciam na partição quente e a consulta funcionava. Três linhas
`REJEITADA` de 2026-08-08 ainda estão na partição 6, consultáveis — as posteriores, não.

## What Changes

- Introduzir uma **cascata de localização em três níveis** no `ConsultarAutorizacaoService`,
  cada nível cobrindo um conjunto de partições **disjunto** do anterior:

  | Nível | Onde procura | Partições | Custo medido | Significado do acerto |
  |---|---|---|---|---|
  | 1 | partição embutida no UUID | 1 | ~3 ms | autorização ativa (caso dominante) |
  | 2 | faixa de expurgo (`>= 900`) | 100 | ~16 ms | autorização em estado terminal |
  | 3 | demais partições quentes (`< 900` e `<> a do UUID`) | 888 | ~126 ms | **anomalia** — invariante violado |

- O nível 2 é semanticamente **exato**, não heurística: o único mecanismo que altera
  `id_particao_conta` é o `ExpurgoAutorizacaoService`, e ele só escreve em `900–999`.

- O nível 3 é rede de segurança para linha que não respeite o invariante "ou está na partição
  do seu UUID, ou está no expurgo" — importação, correção manual, ou defeito futuro. Um acerto
  nele SHALL ser tratado como sinal de anomalia, não como caminho normal.

- Corrigir a spec vigente de `consultar-autorizacao-por-id`, que descreve a faixa de validação
  do id como `900–999` quando o código valida `0–889` (a faixa quente). A spec nomeia a faixa
  errada; o código e o `CLAUDE.md` do `contratoquery` concordam entre si.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `consultar-autorizacao-por-id`: a localização deixa de ser uma única busca pela chave
  composta e passa a ser a cascata de três níveis; o 404 passa a significar "não existe em
  nenhuma partição" em vez de "não existe na partição derivada do UUID". Inclui a correção da
  faixa de validação descrita erradamente na spec atual.

## Impact

**Código (`arj-contratoquery`)**
- `application/autorizacao/ConsultarAutorizacaoService.java` — a cascata
- `application/autorizacao/AutorizacaoRepository.java` — duas consultas JPQL novas
- `shared/config/` — propriedade para habilitar/desabilitar o nível 3

**Não afetados**
- `GET /api/autorizacoes` (listagem) — filtra por conta, já varre todas as partições hoje.
- `arj-contratocommand` — **deliberadamente fora de escopo**, ver `design.md` › D4. O
  `findByIdAutorizacaoAndParticao` que falha lá produz o 422 "não encontrada", sinal conclusivo
  que o `temporiza-autorizacao` usa para dar `XACK` em expiração repetida. Adicionar cascata
  ali pagaria 16–126 ms no caminho de escrita para chegar à mesma resposta.

**Risco de custo que precisa estar consciente**
- O pior caso não é a autorização expurgada, é a **inexistente**: percorre os três níveis e
  paga ~145 ms antes do 404. Como o custo é CPU de planejamento (não I/O), requisições com
  ids inventados viram amplificação barata de CPU no banco. Ver `design.md` › D3.

**Fora de escopo, mas descoberto na investigação**
- `GET /api/autorizacoes` (listagem) gasta **148 ms de planejamento** por chamada, varrendo as
  989 partições — no endpoint principal, sem fallback envolvido. É um problema maior que este.
- `plan_cache_mode=force_generic_plan` pode eliminar o replanejamento por chamada e beneficiar
  a listagem, a cascata e todo o resto de uma vez, sem mudança de código. Merece spike próprio.
