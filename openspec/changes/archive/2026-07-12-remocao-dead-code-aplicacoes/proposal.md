# Proposal: remocao-dead-code-aplicacoes

## Why

Um levantamento (`/opsx:explore`) sobre as duas aplicações encontrou código morto acumulado, concentrado em `contratoquery`: a app nasceu como cópia da `contratocommand` e carrega até hoje um "fantasma" da parte de escrita — 7 classes do `src/main` referenciadas apenas pelos próprios testes (ou por nada), incluindo lógica de expurgo/particionamento de escrita (`ControleExpurgoAutorizacao`) e o método write-side `Autorizacao.inicializaCriacao()` dentro de um serviço declaradamente somente leitura. Na `contratocommand` o problema é menor mas existe (2 enums e 1 utility sem uso em produção). O repositório já tem precedente explícito: a spec `coesao-contratocommand` exige que "classes não utilizadas (ex.: `ContratoBase`) MUST ser removidas" — a command removeu a sua, mas a cópia da query ficou. Código morto testado é duplamente caro: custa manutenção e passa falsa impressão de estar em uso.

## What Changes

### contratoquery (remoções principais)

- Remove `domain/model/ContratoBase` (e o pacote `domain/model/`) — zero referências; a command já removeu a sua cópia como dead code.
- Remove `domain/enums/TipoJornadaAutorizacao` — zero referências no main; a validação de jornada é responsabilidade exclusiva da command.
- Remove `domain/enums/CanaisConhecidosEnum`, `domain/enums/TipoConta`, `domain/enums/MotivoStatusAutorizacao` e seus testes — referenciados apenas pelos próprios testes; a query expõe `motivoStatus` como string crua do banco (conforme spec `motivo-status-por-jornada`), sem precisar do enum.
- Remove `domain/utilities/AchaQtdeSemanas` e `domain/utilities/ControleExpurgoAutorizacao` e seus testes — lógica de cálculo de partição de **escrita** e expurgo, sem sentido numa app read-only.
- Remove o método `Autorizacao.inicializaCriacao()` e o `AutorizacaoTest` que só o exercita — é o fluxo de criação da command, morto aqui (não há MapStruct/`@AfterMapping` na query). Em cascata, `domain/utilities/IdContaUUIDPartitionDistributor` (e seu teste) fica sem nenhum uso no main e também é removido.
- Remove o método `TipoProduto.obterTipoProdutoEnumPorNome` — usado apenas por teste; na query o produto chega do banco por id (via `TipoProdutoConverter`), nunca por nome.
- Remove o método `StatusAutorizacao.isStatusFinalizador` — zero usos (main e testes).
- `ReversibleUUIDv7` **permanece íntegro** (com `generate()` e `extract()`): `extract()` é usado pelo `ConsultarAutorizacaoService`, e `generate()` é necessário aos testes para construir UUIDs v7 válidos.
- Remove anotações Lombok redundantes na entidade `Autorizacao` (`@Getter`/`@Setter` já cobertos por `@Data`) — limpeza pontual, **não** é migração de DTO.

### contratocommand (remoções menores)

- Remove `domain/enums/CanaisConhecidosEnum` e `domain/enums/TipoConta` e seus testes — referenciados apenas pelos próprios testes.
- Move `domain/utilities/AchaQtdeSemanas` de `src/main` para `src/test` — usado somente pelo teste `AchaQtdeSemanasTest` e pelo helper de teste `GeraDatasPorParticao`; o código de produção (`ControleExpurgoAutorizacao`) calcula semanas por conta própria.
- Remove o método `StatusAutorizacao.isStatusFinalizador` — zero usos (main e testes).

### Documentação

- Atualiza `CLAUDE.md`/`AGENTS.md` (espelhos) dos dois módulos: remoção das menções às classes e testes removidos (ex.: lista de testes de enums/utilities da query).

### Fora de escopo (decisões explícitas)

- **Migração de DTOs Lombok → records**: excluída por decisão do usuário.
- Expansão de wildcard imports (`jakarta.persistence.*`, `lombok.*` etc.) — questão de estilo, sem sujeira real (a varredura não encontrou nenhum import não utilizado).
- Migração para `void main()` do Java 25 — já documentada como TODO aguardando suporte do maven plugin.

## Capabilities

### New Capabilities

- `higiene-codigo-morto`: define o padrão de higiene de código morto para as duas aplicações em `aplicacoes/` — classes e métodos do `src/main` devem ter ao menos uma referência em código de produção (testes não contam como uso); utilitários usados apenas por testes vivem em `src/test`; a app de leitura (`contratoquery`) não pode conter lógica exclusiva do fluxo de escrita.

### Modified Capabilities

(nenhuma — os endpoints e contratos REST das duas apps não mudam; as specs existentes `listar-autorizacoes`, `consultar-autorizacao-por-id`, `motivo-status-por-jornada` e `validacao-header-jornada` referem-se a comportamentos que permanecem intactos. A spec `coesao-contratocommand` já exige remoção de dead code na command e continua satisfeita.)

## Impact

- **contratoquery**: 7 classes main removidas + 1 método de entidade + 2 métodos de enum + ~7 classes de teste removidas. Nenhuma mudança de contrato REST — endpoints GET e DTOs de resposta intactos.
- **contratocommand**: 2 classes main removidas, 1 classe movida para `src/test`, 1 método de enum removido + 2 classes de teste removidas. Nenhuma mudança de contrato REST.
- **Testes**: `mvn test` deve permanecer 100% verde nos dois módulos após as remoções; os testes deletados só exercitavam o código morto.
- **Documentação**: `CLAUDE.md`/`AGENTS.md` dos dois módulos atualizados (mantendo-os idênticos entre si).
- **Escopo**: apenas `aplicacoes/contratocommand` e `aplicacoes/contratoquery`; nada fora de `aplicacoes/` além dos artefatos OpenSpec.
