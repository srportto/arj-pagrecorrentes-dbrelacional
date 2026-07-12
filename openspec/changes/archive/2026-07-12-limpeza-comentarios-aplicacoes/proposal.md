## Why

Um levantamento (`/opsx:explore`) sobre os comentários em `aplicacoes/arj-contratocommand` e `aplicacoes/arj-contratoquery` encontrou três problemas concretos, não só estética: (1) `CLAUDE.md`/`AGENTS.md` dos dois módulos afirmam "usa `void main()`", mas o código real usa `public static void main()` com um bloco `void main()` morto e comentado — documentação que descreve um código que não existe; (2) o javadoc de `AutorizacaoRepository` ainda menciona "strategies", conceito removido no refactor de coesão já arquivado — um comentário que hoje está simplesmente errado; (3) vários comentários apenas repetem em português o que a linha seguinte já diz em código (`// Delete do banco com a chave antiga` sobre `repository.deleteById(...)`), além de um banner decorativo e duas linhas de cálculo morto disfarçadas de comentário. Nenhum desses problemas afeta o comportamento da aplicação, mas todos afetam a confiança que se pode depositar nos comentários como fonte de verdade.

## What Changes

- `ContratocommandApplication`/`ContratoqueryApplication`: o bloco `void main()` comentado (3 linhas de código morto) vira um `// TODO` de uma linha só, sinalizando a migração pendente sem fingir ser documentação executável.
- `CLAUDE.md`/`AGENTS.md` dos dois módulos: a afirmação "usa `void main()`" é corrigida para descrever a realidade atual (`public static void main()`, com nota sobre a limitação do maven plugin para Java 25).
- `AutorizacaoRepository` (arj-contratocommand): javadoc corrigido — "vive nas strategies e nas regras de negócio" passa a "vive nas rules", eliminando a referência a um conceito removido.
- `LayoutErrosApiResponse` (ambos os módulos): remove o banner decorativo `//?----...` que só repete o que a classe já deixa óbvio.
- `BusinessException`, `ApplicationException` (ambos os módulos) e `ResourceNotFoundException` (arj-contratoquery): comentários de convenção de uso encurtados, já que a tabela exceção→HTTP está documentada no `CLAUDE.md` de cada módulo.
- `CancelarAutorizacaoUseCase`, `Autorizacao.inicializaCriacao()`, `IdContaUUIDPartitionDistributor.getPartitionFast()`, `ControleExpurgoAutorizacao.obterParticaoExpurgoDrop()` (arj-contratocommand): remove comentários que só reafirmam a linha seguinte; em `obterParticaoExpurgoDrop()` remove também duas linhas de cálculo morto (nunca usadas) sob um comentário `//calcula diferenca...`.
- Comentários que decodificam valores de negócio não óbvios (indicadores 0/1, canais C1/C2/C3, a aritmética de partição em `ControleExpurgoAutorizacao`, o bit-twiddling de `ReversibleUUIDv7`, o gotcha do MapStruct em `AutorizacaoMapper`, os javadocs de `ProdutoSuportado`/`ProdutoSuportadoCancelamento`/`CancelamentoContext`/`CriarAutorizacaoUseCase`/`ConsultarAutorizacaoService`) **permanecem intactos** — são exatamente o tipo de comentário que explica um "porquê" não óbvio.

## Capabilities

### New Capabilities
- `higiene-comentarios-codigo`: define o padrão de comentários para o código Java em `aplicacoes/` (contratocommand e contratoquery) — comentários existem só para explicar um "porquê" não óbvio, não podem descrever comportamento ou conceito que não existe mais no código (stale), não podem ser puramente decorativos, e código morto não pode ser mantido disfarçado de comentário explicativo; a documentação de módulo (`CLAUDE.md`/`AGENTS.md`) não pode afirmar um comportamento do entrypoint que diverge do código real.

### Modified Capabilities
(nenhuma — os specs existentes de `coesao-contratocommand` e `documentacao-contratoquery` descrevem outras invariantes; este padrão de comentários é uma capability nova e independente)

## Impact

- **Código**: 9 arquivos Java (`ContratocommandApplication`, `ContratoqueryApplication`, `AutorizacaoRepository`, `LayoutErrosApiResponse` x2, `BusinessException` x2, `ApplicationException` x2, `ResourceNotFoundException`, `CancelarAutorizacaoUseCase`, `Autorizacao`, `IdContaUUIDPartitionDistributor`, `ControleExpurgoAutorizacao`) com remoção/ajuste de comentários — nenhuma mudança de lógica executável, exceto a remoção de 2 linhas de cálculo morto já inalcançáveis.
- **Documentação**: `CLAUDE.md`/`AGENTS.md` dos dois módulos corrigidos quanto ao `main()`.
- **Testes**: nenhum teste muda de comportamento; `mvn test` deve permanecer 100% verde nos dois módulos.
- **APIs**: nenhuma — mudança é só de comentários/docs, sem tocar contratos REST.
- **Escopo**: apenas `aplicacoes/arj-contratocommand` e `aplicacoes/arj-contratoquery` (por pedido explícito do usuário) — nada fora de `aplicacoes/` é tocado.
