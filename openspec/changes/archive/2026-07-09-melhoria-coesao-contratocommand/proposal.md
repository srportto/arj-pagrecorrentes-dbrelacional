# Proposta: melhoria-coesao-contratocommand

## Why

Após o refactor que centralizou criação e cancelamento nos use cases compartilhados, a camada de strategy do `arj-contratocommand` ficou vestigial: `PixAutoService` e `DdaAutoService` são idênticos exceto pela constante do enum, e o par orquestrador + strategy (6 classes em 3 pacotes) hoje só verifica se `tipoProduto` é conhecido. Além disso, `domain/services` viola a direção de dependência do hexagonal — importa DTOs de `entrypoint` e usa anotações Spring — e o fluxo de cada operação está espalhado por 4 pacotes, dificultando leitura e manutenção.

## What Changes

- **BREAKING (interno, sem impacto no contrato REST)**: remoção da camada de strategy por produto — deletar `ContratacaoOrquestradorService`, `CancelamentoOrquestradorService` (`application/services/`), `PixAutoService`, `DdaAutoService` (`application/enabledproduct/`) e as interfaces `ContratacaoService`, `CancelamentoService` (`domain/services/`).
- `AutorizacaoController` passa a injetar e chamar diretamente `CriarAutorizacaoUseCase` e `CancelarAutorizacaoUseCase`.
- A checagem "produto suportado" vira uma `ContratacaoRule` (`ProdutoSuportado`) que resolve o `TipoProduto` do request e lança `BusinessException` (HTTP 422) para produto desconhecido — mesma mensagem/status de hoje. No cancelamento, o header já chega como enum e `TipoProdutoCancelamento` segue cobrindo a divergência.
- Reorganização por feature: validators, rules e `CancelamentoContext` saem de `domain/services/{contratacao,cancelamento}` para `application/contratacao` e `application/cancelamento`; os use cases saem de `application/autorizacao/usecases` para esses mesmos pacotes de feature. `AutorizacaoRepository` e `AutorizacaoMapper` permanecem compartilhados em `application/autorizacao`.
- `domain/` volta a ser puro: apenas `entities`, `enums`, `converters` e `utilities` — sem imports de `entrypoint` nem estereótipos Spring.
- Comportamento externo preservado: mesmos endpoints, mesmos códigos HTTP (201/200/422/500), mesmas mensagens de erro de produto não suportado.

## Capabilities

### New Capabilities

_(nenhuma)_

### Modified Capabilities

- `coesao-contratocommand`: dois requirements mudam de forma estrutural:
  - "Variação por produto vive em strategy fino, sem duplicação de encanamento" → substituído por "Fluxo único compartilhado por produto, com variação apenas em rules": a seleção por strategy/orquestrador deixa de existir; produto não suportado passa a ser rejeitado por rule de validação, com o mesmo comportamento HTTP observável.
  - "Regras de negócio residem no domínio e orquestradores na aplicação" → substituído por "Organização por feature na aplicação e domínio puro": validators/rules/contexto passam a residir em `application/{contratacao,cancelamento}`; orquestradores deixam de existir; `domain/` não pode importar `entrypoint` nem `application`, nem usar estereótipos Spring.
  - Os demais requirements (cancelamento transacional, exceções do projeto, status via enum, DTOs imutáveis, contratos REST preservados, domínio sem dead code) permanecem válidos e inalterados.

## Impact

- **Código afetado** (tudo em `aplicacoes/arj-contratocommand`):
  - Deletados: `application/services/**` (2 classes), `application/enabledproduct/**` (2 classes), `domain/services/contratacao/ContratacaoService`, `domain/services/cancelamento/CancelamentoService` — e seus testes correspondentes.
  - Movidos: `domain/services/contratacao/{ContratacaoValidator,ContratacaoRule,rules/*}` → `application/contratacao/**`; `domain/services/cancelamento/{CancelamentoValidator,CancelamentoRule,CancelamentoContext,rules/*}` → `application/cancelamento/**`; `application/autorizacao/usecases/*UseCase` → `application/{contratacao,cancelamento}`.
  - Novos: `application/contratacao/rules/ProdutoSuportado` (rule de produto suportado).
  - Alterados: `AutorizacaoController` (injeção direta dos use cases), imports em testes.
- **API/contratos**: nenhum — endpoints, headers, códigos HTTP e corpos preservados.
- **Documentação**: `CLAUDE.md`/`AGENTS.md` do `arj-contratocommand` descrevem a arquitetura antiga (fluxo com orquestrador/strategy) e precisam ser atualizados.
- **Dependências/sistemas**: nenhum impacto em `arj-contratoquery`, banco ou infraestrutura.
