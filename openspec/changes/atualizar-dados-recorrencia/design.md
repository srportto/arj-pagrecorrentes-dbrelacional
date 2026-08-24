## Context

`contratocommand` hoje só expõe transições de **estado** (`criar`, `cancelar`,
`decisao`) — todos os três seguem o mesmo esqueleto: carregar por partição, validar via
um `Validator<Rule<T>, T>` específico do comando, aplicar via
`AutorizacaoPersistenceMapper.aplicarEm(modelo, entidadeGerenciada)`, publicar
`AutorizacaoPersistidaEvent` após commit. Esta change adiciona a primeira operação que
muda **dado** sem mudar **estado**: `valorLimite`, `dataFimVigencia`,
`indicadorUsoLimiteConta`, `quantidadeDividasCiclo`, restrita a autorizações `ATIVA`.

Duas invariantes do sistema de eventos foram investigadas antes de qualquer decisão:

1. `TipoEventoAutorizacao` é uma **bijeção estrita 1:1 com `StatusAutorizacao`** (8
   valores) — nunca informado à parte do status, só derivado (`porStatus`).
2. Essa derivação acontece **duas vezes, de forma independente**: uma vez no
   `contratocommand` (vira SNS message attribute, usado só como filtro pela subscription
   de `temporiza-autorizacao`) e de novo no `autorizacaostatus-producer`
   (`SqsEventoAutorizacaoListener.derivarTipoEvento`, que recalcula do campo `status` do
   **payload recebido** — ignora qualquer message attribute do SNS, por decisão
   consciente já documentada na armadilha nº 9 do `CLAUDE.md` daquele serviço).

A consequência prática: **não é possível introduzir um `tipoEvento` novo sem também
mudar `autorizacaostatus-producer`**, porque ele recalcularia o tipo antigo (`ATIVACAO`)
de qualquer forma, a partir do `status` inalterado no payload — o attribute novo seria
publicado e completamente ignorado no segundo hop.

## Goals / Non-Goals

**Goals:**
- Permitir corrigir/renegociar os 4 campos de uma autorização `ATIVA` sem cancelar e
  recriar o contrato.
- Reaproveitar 100% da infraestrutura existente (partição, lock otimista, framework de
  rules, publicação de evento pós-commit, `ApiExceptionHandler`).
- Manter o escopo da mudança dentro de `apps/contratocommand`.

**Non-Goals:**
- Não introduz um `tipoEvento` novo nem muda a bijeção `TipoEventoAutorizacao`↔`StatusAutorizacao`.
- Não muda `autorizacaostatus-producer`, `eventos-consumer` nem os `.avsc` espelhados.
- Não define o significado de negócio de `indicadorUsoLimiteConta` (ver Open Questions).
- Não introduz atualização de nenhum outro campo além dos 4 pedidos (ex.: `descricao`,
  `frequenciaPagamento` ficam fora — podem ser um capability futuro, se necessário).
- Não permite atualização em `RECEBIDA`, `PENDENTE_ACEITE`, `EM_PROCESSO_ATIVACAO` nem em
  estado terminal.

## Decisions

### D1: Evento reaproveita `ATIVACAO` — sem `tipoEvento` novo

**Alternativas consideradas:**
- *Novo `tipoEvento` dedicado* (ex. `ATUALIZACAO_DADOS`): rejeitada. Quebraria a bijeção
  1:1 documentada em `publicacao-eventos-autorizacao`, e não funcionaria de ponta a ponta
  sem também alterar `autorizacaostatus-producer` para parar de recalcular o tipo a
  partir do `status` do payload (mudança fora do escopo desta change).
- *Não publicar evento*: rejeitada. Quebraria a expectativa implícita — documentada no
  requirement "Evento publicado após commit de cada persistência" — de que toda mutação
  confirmada em `autorizacoes` gera evento.
- **Escolhida: reaproveitar o evento do status vigente.** Como a atualização só é
  permitida em `ATIVA`, `TipoEventoAutorizacao.porStatus(status)` deriva `ATIVACAO`
  automaticamente, sem nenhuma mudança na derivação. **Trade-off aceito**: consumidores
  do tópico não distinguem "autorização foi ativada agora" de "dado de uma autorização
  já ativa foi atualizado" — os dois publicam `ATIVACAO`. Se essa distinção se tornar
  necessária no futuro, é uma change separada que precisa tratar
  `autorizacaostatus-producer` também.

### D2: Restrito a `ATIVA` — nova rule, não extensão do grafo de `StatusAutorizacao`

A tabela `TRANSICOES` de `StatusAutorizacao` é sobre transições status→status; esta
operação não transiciona nada. A restrição vira uma rule de validação
(`StatusPermiteAtualizacao`), no mesmo espírito de `TransicaoValidaDecisao` (que também
impõe uma condição de status mais estrita que o grafo por si só permitiria).

### D3: PATCH parcial com `null` = "não mexe" — sem `JsonNullable`

**Alternativas consideradas:**
- `JsonNullable<T>` (distingue ausente/null/valor): mais correto tecnicamente, mas
  introduz uma dependência nova só para este endpoint e diverge do padrão de `record`
  simples usado em todo `contratosrest/`.
- `JsonNode` cru + checagem manual de presença: também distingue de verdade, mas foge do
  padrão record-imutável do resto do pacote.
- **Escolhida: `null` (ou campo ausente) sempre significa "não altera este campo".**
  Nenhum dos 4 campos tem um caso de uso identificado de "limpar de propósito" — inclusive
  `valorLimite`, que já é nulável por dado legado (armadilha nº 15 do `CLAUDE.md`), não
  tem um fluxo de negócio conhecido que precise setá-lo para `null` via API.
  Consequência: com Bean Validation, `@Min(1)` em `quantidadeDividasCiclo` continua
  funcionando sem `@NotNull` — valida só quando o campo vem preenchido.

### D4: `DataFimVigenciaInvalida` é duplicada, não reaproveitada

A regra existente é uma `ContratacaoRule`, tipada para `CriarAutorizacaoCommand`. O
próprio código já resolve esse tipo de sobreposição duplicando por tipo de comando
(`ProdutoSuportado` vs. `ProdutoSuportadoCancelamento`, `TipoProdutoCancelamento` vs.
`TipoProdutoDecisao`) em vez de compartilhar abstração entre `Rule<T>` de tipos
diferentes. `DataFimVigenciaInvalidaAtualizacao` segue a mesma convenção — duplica a
checagem de ~5 linhas ("não pode ser no passado") como uma nova `AtualizacaoRule`.

### D5: `valorLimite` ganha sua primeira regra de negócio (`> 0`)

O campo nunca teve validação alguma — a rule `ValorLimiteContrato` (nome parecido, mas
sem relação) valida o `valor` da transação contra um teto por produto, não `valorLimite`.
Para a atualização, a regra escolhida é a mínima razoável: rejeitar `<= 0` quando
informado, sem impor teto por produto (não há dado hoje para calibrar esse teto).

### D6: Auditoria (`codigoCanalAtualizacao` + `idPessoaAtualizacao`) obrigatória

Consistente com `Cancelamento` (`codigoCanalCancelamento`/`idPessoaCancelamento`) e
`DecidirAutorizacaoCommand` (`codigoCanalDecisao`/`idPessoaDecisao`) — toda mutação
financeira hoje carrega canal + pessoa responsável.

### D7: Rota `PATCH /api/autorizacoes/{idAutorizacao}/atualizar`

Verbo, simetria com `/cancelar`. Sem contrato prévio no gateway (`docs/contrato-api-para-gateway.md`
não menciona este endpoint) — nome é desta change, não uma restrição externa.

## Risks / Trade-offs

- **[Risco] Consumidores de `eventos-autorizacao` não distinguem ativação real de
  atualização de dados** (ambas publicam `ATIVACAO`) → **Mitigação**: aceito
  conscientemente (D1); documentar no requirement de `publicacao-eventos-autorizacao`
  para não ser lido como bug depois.
- **[Risco] `indicadorUsoLimiteConta` segue sem validação porque seu significado de
  negócio não está documentado em nenhum lugar do repo** (o próprio spec
  `higiene-comentarios-codigo` usa esse campo como exemplo de indicador sem significado
  óbvio) → **Mitigação**: nenhuma nesta change; ver Open Questions.
- **[Risco] Duplicar `DataFimVigenciaInvalida` cria uma segunda cópia da mesma regra de
  negócio** ("não pode ser no passado") → **Mitigação**: aceito por consistência com o
  padrão já estabelecido no código; se a regra mudar no futuro, checklist de revisão
  precisa lembrar de replicar nas duas rules (registrar isso no `CLAUDE.md` do
  `contratocommand` ao final da implementação).

## Migration Plan

Aditivo, sem migração de schema (os 4 campos e as colunas já existem desde a criação da
tabela) e sem dado a migrar. Deploy é o deploy normal de uma versão nova do
`contratocommand` — nenhuma coordenação com os outros 4 serviços é necessária.

## Open Questions

- **Significado de negócio de `indicadorUsoLimiteConta`**: o time de produto/negócio
  sabe qual é o domínio de valores válido (flag booleana 0/1? faixa maior?). Sem essa
  resposta, o campo continua aceito sem validação nesta atualização, igual à criação.
