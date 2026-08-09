## Why

A transferência de autorização para a partição de expurgo (`ExpurgoAutorizacaoService`)
está **quebrada de forma determinística** desde 2026-08-09: toda decisão `REJEITAR`/`EXPIRAR`
e todo cancelamento cuja partição de destino difira da atual respondem `409`, a transação é
revertida e a autorização permanece presa no status de origem. Não é um conflito de
concorrência real — a transação está colidindo consigo mesma.

A causa é a interação de dois commits que não se tocam:

| commit | data | efeito |
|---|---|---|
| `98287d6` | 2026-08-09 10:40 | `DecidirAutorizacaoUseCase` passa a usar `ExpurgoAutorizacaoService` (troca `UPDATE` simples por `delete`+`insert`) |
| `bd75167` | 2026-08-09 16:06 | adiciona `@Version` em `Autorizacao` (lock otimista da change `integridade-fluxo-escrita`) |

O algoritmo `delete → flush → detach → save` dependia — sem que estivesse escrito em lugar
nenhum — de a entidade **não ter campo de versão**. No `save`, o Spring Data chama
`EntityManager.merge()`; o Hibernate procura a linha (já apagada), não acha, e pergunta a
`AbstractEntityPersister.isTransient()` o que fazer:

- **sem `@Version`**: resposta `null` ("não sei") → o Hibernate assume transiente e faz `INSERT`. Era o que funcionava.
- **com `@Version`** (valor `0`, não-nulo): resposta `FALSE` ("é detached de verdade") → `throw new StaleObjectStateException` → `ObjectOptimisticLockingFailureException` → **409**.

Evidência em produção local: autorizações `019fe814-…0006` (19:50) e `019fe853-…0006`
(20:59) presas em `RECEBIDA`; partição de expurgo do dia (`953`) vazia; última linha
transferida com sucesso data de 2026-07-26 (partição `951`), antes do `@Version`.

Há ainda um segundo defeito latente, do mesmo mecanismo: a constraint
`uk_autorizacao_empresa_particao (id_particao_conta, id_autorizacao_empresa)` **muda de
significado** quando a linha entra na partição de expurgo — nas partições quentes
`id_particao_conta` é o hash da conta ("um `id_autorizacao_empresa` por conta"), nas de
expurgo é o balde da semana ("um `id_autorizacao_empresa` por semana, somando todas as
contas"). Duas autorizações de contas distintas com a mesma chave de empresa chegando a
estado terminal na mesma semana colidem com `DataIntegrityViolationException` → `409`,
com sintoma idêntico ao bug principal. Corrigir só o primeiro defeito faz este aflorar.

## What Changes

- **Substituir o `delete → flush → detach → save` do `ExpurgoAutorizacaoService`** por
  `UPDATE` do `id_particao_conta` com *row movement* nativo do PostgreSQL (suportado desde
  a versão 11), eliminando por completo o `merge` de instância detached — a fonte da
  fragilidade. O `@Version` continua protegendo a escrita das colunas de negócio via
  dirty-check normal, e o contador de versão volta a ser incrementado.
- **Corrigir a semântica da unicidade na partição de expurgo**, de forma que a transferência
  nunca falhe por colisão entre autorizações de contas distintas que compartilhem
  `id_autorizacao_empresa`.
- **Exigir verificação contra banco real** (não mock) para a transferência de partição. O
  `ExpurgoAutorizacaoServiceTest` atual é 100% mockado e verifica apenas a *ordem* das
  chamadas (`deleteById → flush → detach → save`) — por construção, incapaz de detectar este
  defeito, que vive na decisão que o Hibernate toma diante de um banco real.
- **Remover da spec de concorrência a tolerância "é aceitável que AMBAS as transações
  falhem"**, registrada empiricamente durante a change `integridade-fluxo-escrita`: aquela
  observação era este bug, não concorrência.
- **BREAKING (interno)**: o requisito vigente de `expurgo-estados-terminais` prescreve
  literalmente o algoritmo `delete → flush → detach → save`. Ele deixa de valer.

## Capabilities

### New Capabilities

Nenhuma. Todos os requisitos afetados pertencem a capacidades já existentes.

### Modified Capabilities

- `expurgo-estados-terminais`: o requisito deixa de prescrever o algoritmo
  `delete → flush → detach → save` e passa a exigir (a) que a transferência conclua com
  sucesso em transação isolada, qualquer que seja a implementação, (b) que a linha
  transferida preserve identidade e dados, e (c) que a garantia seja verificada contra um
  banco PostgreSQL real.
- `concorrencia-otimista-autorizacao`: remoção da tolerância "é aceitável que AMBAS as
  transações falhem com erro de concorrência"; escrita isolada com troca de partição SHALL
  concluir com sucesso e SHALL incrementar a versão.
- `idempotencia-criacao-autorizacao`: a unicidade de `id_autorizacao_empresa` passa a ter
  escopo explicitamente definido para partições quentes e de expurgo, de modo que a
  transferência para expurgo não produza colisão entre contas distintas.

## Impact

**Código (`arj-contratocommand`)**
- `application/ExpurgoAutorizacaoService.java` — coração da mudança
- `application/AutorizacaoRepository.java` — nova operação de movimentação de partição
- `application/decisao/DecidirAutorizacaoUseCase.java` e
  `application/cancelamento/CancelarAutorizacaoUseCase.java` — chamadores; sem mudança de
  assinatura esperada
- `domain/entities/Autorizacao.java` — declaração da constraint em `@Table`, se o escopo da
  unicidade mudar
- `src/test/.../ExpurgoAutorizacaoServiceTest.java` — deixa de ser suficiente sozinho

**Banco**
- Nova migration em `infra/local/postgres/migrations/` caso o ajuste de unicidade exija
  redefinir `uk_autorizacao_empresa_particao`
- Nenhuma mudança de schema é necessária para o defeito principal

**Sistemas a jusante**
- `temporiza-autorizacao`: nenhuma mudança de código. O teto de 5 tentativas
  (`PendenciasSchedulerReivindicador`) e o retry em `409`
  (`CommandDecisaoAutorizacaoClient`) estão corretos e se comportaram como especificado —
  foram eles que transformaram um bug silencioso em sinal operacional. Após a correção, a
  primeira tentativa passa a concluir.
- `arj-contratoquery`: lê a mesma tabela; a linha muda de partição, não de conteúdo.
- Entradas já esgotadas no PEL do stream Valkey (`XACK` dado sem sucesso) **não voltam
  sozinhas** — as autorizações presas em `RECEBIDA` exigem reprocessamento manual.

**Fora de escopo (registrar em change própria)**
- O índice `idx_autorizacoes_conta_status_data` está `INVALID` no banco local (criação não
  propagada a todas as partições), violando o requisito "Plano de execução usa índice" de
  `desempenho-consulta-autorizacoes`. Defeito real, sem relação causal com este.
