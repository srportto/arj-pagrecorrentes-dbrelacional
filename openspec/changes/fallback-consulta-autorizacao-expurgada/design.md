## Context

A tabela `autorizacoes` é particionada por `LIST (id_particao_conta)`, com 989 partições: 889
quentes (`0–888`, derivadas de `hash(id_unico_conta_contratante) % 889`) e 100 de expurgo
(`900–999`, balde semanal).

O `arj-contratoquery` localiza uma autorização pela chave composta, derivando a partição do
próprio UUID via `ReversibleUUIDv7.extract` — sem query extra, o que era a virtude do desenho.
A premissa implícita: **a partição em que a linha está é a mesma em que ela nasceu**. A mudança
`expurgo-estados-terminais` quebrou essa premissa sem que o `contratoquery` fosse ajustado.

### Medições (PostgreSQL 18 local, tabela com 24 linhas)

| Consulta | Subplanos | Planning | Execution | Total |
|---|---|---|---|---|
| `id = ? AND particao = <do UUID>` | 1 | 3,2 ms | 0,03 ms | **~3 ms** |
| `id = ? AND particao >= 900` | 100 | 15,2 ms | 1,0 ms | **~16 ms** |
| `id = ? AND particao < 900 AND particao <> <do UUID>` | 888 | 125,6 ms | 11,2 ms | **~137 ms** |
| `id = ?` (sem restrição) | 989 | 135,9 ms | 14,8 ms | **~151 ms** |
| `GET /api/autorizacoes` (listagem, hoje) | 989 | 147,6 ms | 17,8 ms | **~166 ms** |

Dois fatos que orientam tudo o que vem abaixo:

**O custo é planejamento, não I/O.** A tabela tem 24 linhas; os ~130 ms são o Postgres montando
centenas de subplanos. É custo fixo por query, proporcional ao número de partições
consideradas — **não diminui com menos dados e não melhora com índice**.

**O Postgres poda partição LIST com `>=`, `<` e `<>`.** Verificado: `< 900 AND <> 6` reduz de
989 para 888 subplanos. Isso é o que torna a cascata em níveis disjuntos possível.

### Spike de `plan_cache_mode` (executado em 2026-08-10)

| Modo | 1ª execução | Execuções seguintes |
|---|---|---|
| Custom (padrão), após 6 execuções do mesmo `PREPARE` | 35,2 ms planning | **35,2 ms** — não cacheia |
| `force_generic_plan` | 39,4 ms | **0,17 ms** planning |
| `force_generic_plan`, com partição parametrizada | 46,1 ms | **0,15 ms** planning, `Subplans Removed: 988`, execução 0,04 ms |

O planejador **nunca** migra para plano genérico por conta própria neste schema: com poda por
partição, o plano custom sempre parece mais barato, então o replanejamento de ~35 ms se repete
em toda chamada. Forçado o plano genérico, o custo por chamada praticamente desaparece e a poda
passa a ocorrer em tempo de execução (`Subplans Removed`).

**O que isso muda para esta mudança:** a ordenação dos níveis continua correta, mas as apostas
caem uma ordem de grandeza. Se `force_generic_plan` for adotado (change própria — beneficia a
listagem e todo o resto, não só esta cascata), o pior caso da cascata cai de ~156 ms para a
casa dos ~13 ms, e a justificativa de D3 enfraquece bastante. A flag de D3 continua valendo por
outros motivos (poder desligar N3 se ele nunca acertar), mas deixa de ser mitigação de risco
operacional.

**O que isso NÃO muda:** `plan_cache_mode` é configuração de sessão que afeta todas as
consultas da aplicação, com efeitos a avaliar em cada uma delas. Continua fora do escopo desta
mudança, agora com evidência de que vale uma change própria e prioritária.

### Latência real, ponta a ponta pela API (medida em 2026-08-10, após a implementação)

Média de 5 chamadas HTTP contra o `arj-contratoquery` em container, banco local:

| Caminho | Níveis percorridos | Latência |
|---|---|---|
| Autorização ativa | N1 | **7 ms** |
| Autorização expurgada | N1 + N2 | **22 ms** |
| Id inexistente | N1 + N2 + N3 | **75 ms** |

O pior caso ficou **abaixo da estimativa** derivada do `EXPLAIN` (~156 ms). A diferença vem de
o `EXPLAIN` contabilizar planejamento que o driver e o Hibernate acabam amortizando entre
chamadas, e de o próprio `EXPLAIN` ter custo. A ordem de grandeza e a ordenação dos níveis se
confirmam; o risco de amplificação de CPU descrito em D3 permanece real, apenas menor do que o
projetado.

## Goals / Non-Goals

**Goals:**

- Autorização em estado terminal volta a ser consultável por id.
- O caminho feliz (autorização ativa) não fica mais caro — continua em 1 partição.
- Um resultado encontrado fora dos dois lugares esperados é tratado como anomalia visível, não
  como sucesso silencioso.
- O custo do pior caso é conhecido, documentado e controlável por configuração.

**Non-Goals:**

- Otimizar `GET /api/autorizacoes` (listagem). Problema maior, change própria.
- Mudar a estratégia de particionamento ou o número de partições.
- Levar a cascata ao `arj-contratocommand` (ver D4).
- Eliminar o custo de planejamento por replanejamento de prepared statement (`plan_cache_mode`)
  — spike separado, benefício muito além desta mudança.

## Decisions

### D1 — Cascata de três níveis, com conjuntos de partições disjuntos

```
GET /api/autorizacoes/{id}
        │
        ▼
  extrai partição do UUID  ──── fora de 0..888 ──▶ 404 (sem tocar no banco)
        │
        ▼
  ┌───────────────────────────────────────────────┐
  │ N1: particao = <do UUID>            1 part.   │──── achou ──▶ 200
  │     "autorização ativa"             ~3 ms     │
  └───────────────────────────────────────────────┘
        │ não achou
        ▼
  ┌───────────────────────────────────────────────┐
  │ N2: particao >= 900               100 part.   │──── achou ──▶ 200
  │     "foi expurgada"                ~16 ms     │
  └───────────────────────────────────────────────┘
        │ não achou
        ▼
  ┌───────────────────────────────────────────────┐
  │ N3: particao < 900 AND <> <do UUID>           │──── achou ──▶ 200 + log WARN
  │     "anomalia"                    888 part.   │              (invariante violado)
  │                                    ~137 ms    │
  └───────────────────────────────────────────────┘
        │ não achou
        ▼
      404  (~156 ms no pior caso)
```

Os três conjuntos são **disjuntos e cobrem a tabela inteira**. Consequências que valem mais
que a economia de tempo:

- Nenhuma partição é consultada duas vezes.
- Um acerto em N3 é, **por definição**, uma linha que violou o invariante "ou está na partição
  do seu UUID, ou está no expurgo" — não é preciso re-derivar isso, o próprio nível já diz.
- 404 passa a significar "não existe em partição nenhuma", afirmação forte e verificável.

**Por que N2 antes de N3, e não uma varredura única:** N2 é exato, não heurística. O único
código que altera `id_particao_conta` é o `ExpurgoAutorizacaoService`, e ele só escreve em
`900–999`. Colapsar N2 e N3 numa consulta só (`id = ?`, 989 partições) custaria ~151 ms no caso
que deveria custar 16 ms — e perderia o sinal de anomalia.

**Alternativas descartadas:**

| Alternativa | Por que não |
|---|---|
| **Fallback único `WHERE id = ?`** (proposta original) | 151 ms para o caso comum de autorização expurgada, contra 16 ms de N2. E confunde "foi expurgada" (esperado) com "está num lugar errado" (anomalia) — os dois viram o mesmo 200 silencioso. |
| **Query única combinada** `particao = ? OR particao >= 900` | 101 partições **sempre**, inclusive no caminho feliz: encarece a consulta de autorização ativa de 3 ms para ~16 ms. Como consulta de autorização ativa é o caso dominante, é o trade-off invertido. |
| **Tabela de ponteiro** `id → partição atual`, atualizada no expurgo | Lookup O(1), mas cria segunda fonte de verdade sujeita a divergência, e adiciona escrita a uma transação que a change `corrigir-expurgo-merge-version` acabou de mostrar ser delicada. |
| **Não mover a partição no expurgo** | Reverte decisão recente e deliberada de `expurgo-estados-terminais`. |
| **Registrar a partição de destino no próprio UUID** | O UUID é imutável e já foi entregue ao cliente. |

### D2 — Mais de uma linha na cascata é erro, não "pegar a primeira"

Qualquer nível que devolva mais de uma linha para o mesmo `id_autorizacao` indica a mesma
autorização existindo em duas partições — corrupção. A cascata **SHALL** tratar isso como erro
de aplicação (500 genérico ao cliente, log completo no servidor), e **SHALL NOT** escolher uma
das linhas.

Não deveria acontecer: a movimentação virou um `UPDATE` atômico com row movement em
`corrigir-expurgo-merge-version`. Mas o `delete`+`insert` anterior tinha exatamente esse modo
de falha se interrompido entre as duas operações, e é o fallback quem encontraria o resíduo.

### D3 — O nível 3 é configurável, porque o pior caso é o id inexistente

O pior caso da cascata **não é** a autorização expurgada (16 ms). É a **inexistente**: percorre
os três níveis, paga ~156 ms e devolve 404. Como o custo é CPU de planejamento, um cliente
enviando ids inventados obtém amplificação barata — cada requisição trivial de emitir custa
~150 ms de CPU do banco.

O nível 3 **SHALL** ser habilitável/desabilitável por configuração, **padrão habilitado**.
Desabilitado, o pior caso cai para ~19 ms e a cascata perde apenas a rede de segurança contra
anomalia — que, se o invariante valer, nunca acerta mesmo.

Registrado explicitamente para que a decisão de desligar seja informada, e não descoberta sob
incidente.

**Não** se propõe cache negativo nem rate limiting aqui: são mecanismos de borda que
pertencem à camada de API/gateway, não ao caso de uso.

### D4 — A cascata NÃO vai para o `arj-contratocommand`

`DecidirAutorizacaoUseCase` e `CancelarAutorizacaoUseCase` também localizam por
`(uuid, partição do UUID)` e falham quando a linha já foi expurgada. Isso é **desejado**: o
`BusinessException` resultante vira 422 "não encontrada", e é o sinal conclusivo que o
`temporiza-autorizacao` usa para dar `XACK` numa expiração repetida (ver
`apps/temporiza-autorizacao/CLAUDE.md`, "Contrato de conclusão com o command").

Adicionar cascata ali não mudaria o resultado observável — a rule `TransicaoValidaDecisao`
barraria de qualquer forma, por exigir `statusAtual == RECEBIDA` — mas pagaria 16–137 ms no
caminho de escrita para chegar à mesma resposta.

## Risks / Trade-offs

- **404 fica ~50x mais caro** → De ~3 ms para ~156 ms com N3 ligado, ~19 ms desligado.
  Mitigação: D3 torna o nível 3 desligável; o custo está documentado em vez de escondido.

- **A cascata mascara o problema de desenho** → Ela remedia o sintoma; a causa é a partição de
  destino não ser derivável do id. Mitigação: registrar no `CLAUDE.md` do `contratoquery` que
  a cascata existe **porque** o expurgo move a linha, para que quem mexer no particionamento no
  futuro saiba o que quebra.

- **N3 nunca acerta e vira código morto caro** → Se o invariante sempre valer, N3 é 137 ms
  gastos por 404. Mitigação: o log de acerto em N3 é o que informa a decisão de removê-lo mais
  tarde — sem ele, nunca se saberia se vale a pena.

- **Custo cresce com o número de partições** → Planejamento é linear no número de partições
  consideradas. Se as 889 gavetas quentes forem além do necessário, todo o sistema (não só esta
  cascata) fica mais barato ao reduzi-las.

## Open Questions

- ~~**N3 deve nascer habilitado?**~~ **Resolvido em 2026-08-10: sim.** Uma anomalia que exista
  hoje precisa aparecer em vez de virar 404 silencioso. Com o resultado do spike, o custo de
  mantê-lo ligado é menor do que se supunha.
- ~~**O acerto em N3 deve ser `WARN` ou `ERROR`?**~~ **Resolvido em 2026-08-10: `WARN`.** A
  resposta ao cliente é sucesso e o dado foi entregue corretamente; não há falha a tratar, há
  um invariante a investigar. `ERROR` misturaria com falhas que exigem ação imediata.
- ~~**Vale medir `plan_cache_mode`?**~~ **Medido em 2026-08-10** — ver seção do spike acima.
  Confirmou-se decisivo, e virou candidato a change própria e prioritária.
