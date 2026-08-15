## Contexto

Inventário de 2026-08-10.

```
                    linhas   papel declarado        papel real
apps/*/README.md      1621   "doc completa"         mistura tudo
apps/*/CLAUDE.md      1175   guia p/ agente         coerente
apps/*/AGENTS.md      1175   espelho de CLAUDE.md   idêntico (verificado)
apps/*/HELP.md          16   —                      boilerplate Initializr
docs/**               3098   material de apoio      1 vazio, 1 stub
infra/**/README.md     624   por módulo/ambiente    granularidade desigual
```

O `README.md` do `arj-contratocommand` sozinho é 55% de todo o volume de README das apps.

## Os 26 links quebrados, por causa raiz

| Causa | Qtd | Onde | Correção |
|---|---|---|---|
| `openspec/changes/*` arquivadas | 6 | `CLAUDE.md`+`AGENTS.md` de 3 apps | D3 |
| Referência a material que nunca existiu | 9 | command, `README` raiz, `modelo-dados` | D6 |
| Base relativa errada, alvo existe | 2 | `CLAUDE.md`+`AGENTS.md` do command | D6 — corrigir o caminho |
| `src/main/...` fora de contexto | 5 | `modelo-dados-...md` | reescrever com caminho de repo |
| `apps/temporiza-autorizacao/README.md` | 1 | `README` raiz | criar o arquivo (D5) |
| `./LICENSE` | 2 | command, `based-java-aplication` | apontar para a raiz |
| `./compatibility-tests/` | 1 | `floci-aws-local.md` | verificar e remover |

Atacar por causa, não por arquivo: as 3 primeiras linhas cobrem 17 dos 26.

## Decisões

### D1 — Papel de cada arquivo de documentação

**Decisão:** três papéis, sem sobreposição.

| Arquivo | Público | Responde | NÃO contém |
|---|---|---|---|
| `README.md` | humano chegando agora | o que é, como subir, como testar | armadilhas, changelog, contrato de API |
| `CLAUDE.md` / `AGENTS.md` | agente de IA | armadilhas, fluxos, invariantes, checklist | tutorial de instalação |
| `docs/` | quem investiga | POC, modelo de dados, decisões longas | o que cabe nos dois acima |

**Racional:** hoje o `README.md` do command tenta ser os três ao mesmo tempo, e por isso duplica
o `CLAUDE.md` em arquitetura e stack, duplica a si mesmo em testes e padrões, e ainda carrega
contrato de API. Sem regra de papel, a poda volta a crescer na próxima adição.

**Consequência prática:** ao escrever documentação nova, a pergunta passa a ser "quem lê isso?",
e a resposta escolhe o arquivo.

### D2 — Alvo de tamanho é consequência, não meta

**Decisão:** não fixar limite de linhas em spec.

**Racional:** limite numérico convida a jogo — quebra-se um arquivo em dois e o número fecha. O
que a spec proíbe é **duplicação** e **conteúdo falso**; se o command sair de 885 para ~150
linhas, é porque 735 delas eram uma dessas duas coisas, não porque 150 é o número certo. A
estimativa fica na proposal como expectativa, não como critério de aceite.

### D3 — Como referenciar change OpenSpec sem que o link morra

O problema: 6 links quebrados apontam para `openspec/changes/<nome>/design.md` de changes já
arquivadas. Reincide a cada `openspec archive`.

**Opções:**

| | Como | Custo | Sobrevive ao archive |
|---|---|---|---|
| A | Citar o nome da change, sem link | zero | sim |
| B | Linkar `openspec/changes/archive/<nome>/` | atualizar o link no archive | sim, se lembrarem |
| C | Linkar a spec resultante em `openspec/specs/<cap>/` | reescrever a referência | sim |

**Decisão: A como padrão, C quando a informação sobreviveu numa capacidade.**

**Racional:** o `design.md` de uma change é registro de **decisão datada** — ele não é
documentação viva, e apontar um leitor para lá meses depois raramente é o que se quer. Quando a
decisão virou regra, ela está na spec da capacidade, que é estável e é o destino certo. Quando
não virou, citar o nome basta para quem quiser garimpar o archive.

Opção B foi descartada porque transfere a manutenção do link para o momento do archive — que é
exatamente quando ninguém está olhando para a documentação das apps.

### D4 — A contradição 400 vs 422

`openspec/specs/contrato-api-consistente/spec.md` diz:

> erro de formato ou violação de Bean Validation (`@Valid`): **400**
> #### Scenario: Violação de Bean Validation retorna 400

O `CLAUDE.md` raiz diz, como decisão D3 de 2026-08-09 da change `reconciliar-contrato-spec-doc`:

> **Convenção única para entrada inválida do cliente: 422.** Tanto falha de formato via `@Valid`
> quanto violação de regra de negócio via `BusinessException` retornam **422**.

**Decisão: a spec está errada; corrigir para 422.**

**Racional:** três evidências convergem contra a spec — o código implementa 422, os
`CLAUDE.md` das duas apps documentam 422, e a decisão D3 é posterior e explícita sobre o
racional (a distinção formato/regra é carregada pelo *shape* da resposta,
`LayoutErrosApiValidationsResponse` vs `LayoutErrosApiResponse`, não pelo status). A spec ficou
para trás no arquivamento.

**Verificar antes de editar:** confirmar por teste que o comportamento vigente é 422 nas duas
apps. Se for 400 em alguma, a conclusão inverte e vira defeito de código — que sai do escopo
desta change.

### D5 — `README.md` do `temporiza-autorizacao`

**Decisão:** criar, seguindo D1 — não copiar a estrutura do `CLAUDE.md` da app.

**Racional:** é a única das 5 apps sem README, e o `README.md` raiz já linka para ele em dois
lugares (na tabela de microserviços, quebrado; e na tabela de Documentação, onde alguém contornou
apontando para o `CLAUDE.md`). O contorno na tabela de Documentação é a evidência de que a
ausência já incomodou uma vez.

### D6 — Os 11 links para `docs/`: três destinos diferentes

Investigado em 2026-08-10 contra o histórico completo do git (`git log --all --pretty=format:
--name-only`, todas as branches). A pergunta era se o material tinha se perdido. **Não se perdeu —
e cada um dos três caminhos tem uma história diferente:**

| Alvo | Estado real | Causa do link quebrado |
|---|---|---|
| `docs/post-autorizacoes.txt` | **existe em HEAD**, na raiz | base relativa errada |
| `docs/comandos-sql.txt` | nunca existiu em commit algum | referência aspiracional |
| `docs/resultado-poc/` | nunca existiu em commit algum | referência aspiracional |

**Decisão por caso:**

1. **`post-autorizacoes.txt` (2 links)** — corrigir o caminho. Os `CLAUDE.md`/`AGENTS.md` do
   `arj-contratocommand` escrevem `docs/post-autorizacoes.txt`, que resolve para
   `apps/arj-contratocommand/docs/post-autorizacoes.txt`. O correto é `../../docs/`. Não há nada
   a restaurar nem a remover.

2. **`comandos-sql.txt` e `resultado-poc/` (9 links)** — **repontar para onde o material está de
   fato** (decidido em 2026-08-10). Não há material perdido: `git log --all` não registra esses
   caminhos uma única vez, em nenhuma branch. Mas o *conteúdo* que eles prometiam **existe**, com
   outro nome e outro lugar:

   | O que o link prometia | Onde o conteúdo está |
   |---|---|
   | `docs/comandos-sql.txt` | `infra/local/postgres/exemplos-queries.sql` |
   | `docs/resultado-poc/POC_PARTICIONAMENTO_BUFFER_RING_UUIDV7.md` | `docs/arquitetura/modelo-dados-e-dados-poc-testada-para-essa-implementacao.md` (título real: "POC: Particionamento com Buffer Ring e UUID-V7 Reversível" — confirma o match) |
   | `docs/resultado-poc/sql-comandos.txt` | **existia, com caminho errado**: o link em `modelo-dados-...md` apontava para `docs/resultado-poc/sql-comandos.txt`, mas o arquivo real é `docs/arquitetura/sql-comandos.txt` (143 linhas de DDL) — **no mesmo diretório** do arquivo que continha o link. Corrigido para `sql-comandos.txt` (mesma pasta) |
   | `docs/resultado-poc/jornada-tecnica.txt`, `tradeoff-estrategias-particionamento-postgres.txt` | **nunca existiram em commit algum** (confirmado por `git log --all --pretty=format: --name-only`) — removidos sem substituto, ao contrário de `sql-comandos.txt` que só parecia aspiracional por causa do caminho errado |

   **Racional:** um link para o lugar certo vale mais que um link removido. Quem seguiu aquele
   link queria o material, e ele existe — apagar a referência resolve o defeito de link e cria um
   defeito de descoberta. Apagar apenas onde a triagem não encontrar destino real.

**Achado extra durante a triagem (2026-08-11):** a mesma seção "Referências e Recursos" de
`modelo-dados-...md` também linkava `PixAutoAutorizacaoService.java` e
`PixAutoAutorizacaoMapper.java` — classes que não existem no código atual (mesma fauna fictícia do
Strategy Pattern encontrada no README do command, D-nenhum — ver fase 2). Removidas sem
substituto; os outros 3 links de código (`IdContaUUIDPartitionDistributor`,
`ControleExpurgoAutorizacao`, `ReversibleUUIDv7`) existem de verdade e só tinham caminho relativo
errado.

**Consequência:** a Q1 deixa de bloquear a fase 4. Nada em `enxugar-documentacao-repo` depende
mais de investigação prévia.

### D7 — `infra/`: um README por módulo, e o stub de `postgres16` sai

Duas decisões menores, de 2026-08-10.

**Os 14 `README.md` de `infra/` ficam como estão em estrutura** (era Q2). A granularidade é
desigual — `infra/modules/observability` tem 9 linhas, `infra/envs/local` tem 95 —, mas a
proximidade com o código Terraform vale mais que o tamanho: quem abre
`infra/modules/observability/` quer a doc ali, não num índice distante. Um
`infra/modules/README.md` unificado teria 6 razões diferentes para mudar. Esta change **revisa a
fidelidade** desses arquivos, sem mexer na estrutura.

**`docs/run_postgres16_ja_com_cron_partman/` é removido** (era Q3). Ele existe como ponteiro "para
não quebrar links antigos", mas os links antigos que ele protege são **internos** e serão
corrigidos nesta mesma change. E o ponteiro carrega uma inconsistência própria: o diretório diz
`postgres16`, o conteúdo diz PostgreSQL 18. Renomear anularia a razão de ele existir (preservar o
caminho antigo), então a escolha é entre manter um nome errado ou remover — e remover é o que
sobra quando o motivo de existir some.

## Riscos

| Risco | Probabilidade | Mitigação |
|---|---|---|
| Poda remove informação que só existia ali | Média | Tarefa 2.1 é inventário do que sai, antes de sair; o que for único migra, não some |
| Espelhos `CLAUDE.md`/`AGENTS.md` divergem | Alta (5 pares, muita edição) | Tarefa 6.2: `diff` de cada par no fechamento |
| Correção 400→422 baseada em premissa errada | Baixa | D4 exige verificar por teste antes de editar |
| Links novos nascem quebrados | Média | Tarefa 6.1 roda a verificação de links no repo inteiro |

## Questões em aberto

- ~~**Q1:** o material de `docs/` referenciado por 11 links foi perdido ou nunca existiu?~~
  **Respondida em 2026-08-10 — ver D6.** Nada se perdeu: `post-autorizacoes.txt` existe (erro de
  base relativa), `comandos-sql.txt` e `resultado-poc/` nunca existiram em commit algum. Deixou
  de bloquear a fase 4.
- ~~**Q1b:** repontar os 9 links aspiracionais ou remover?~~ **Respondida em 2026-08-10:
  repontar onde houver destino real** — ver D6.
- ~~**Q2:** unificar os `README.md` pequenos de `infra/`?~~ **Respondida: manter um por módulo**
  — ver D7.
- ~~**Q3:** manter ou remover o stub `docs/run_postgres16_ja_com_cron_partman/`?~~
  **Respondida: remover** — ver D7.

Nenhuma questão em aberto. A change está pronta para execução.
