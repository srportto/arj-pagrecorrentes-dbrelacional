# Design: melhoria-coesao-contratocommand

## Context

O fluxo de criação/cancelamento do `contratocommand` atravessa hoje 4 pacotes em 4 saltos:

```
AutorizacaoController
  → {Contratacao,Cancelamento}OrquestradorService   (application/services)
    → PixAutoService | DdaAutoService               (application/enabledproduct)
      → {Criar,Cancelar}AutorizacaoUseCase          (application/autorizacao/usecases)
        → Validator + Rules                          (domain/services)
```

Depois que o refactor anterior centralizou o encanamento nos use cases compartilhados, as duas strategies ficaram idênticas exceto pela constante `TipoProduto`, e o par orquestrador+strategy computa apenas "tipoProduto é conhecido?". Além disso, `domain/services` importa DTOs de `entrypoint/contratosrest` e usa `@Component` — a seta de dependência aponta do domínio para a borda.

Restrições:
- Contrato REST intocável: `POST /api/autorizacoes` (201), `PATCH /api/autorizacoes/{id}/cancelar` com header `tipoProduto` (200), erros 422/400/500 e mensagens preservadas.
- Framework `shared/validationsetup` (`Rule<T>`/`Validator<R,T>`) é a parte boa do design atual e deve ser mantido como está.
- A spec `coesao-contratocommand` tem 2 requirements que exigem a estrutura antiga — esta change os substitui via delta spec.

## Goals / Non-Goals

**Goals:**
- Reduzir o fluxo de 4 saltos para 1: `Controller → UseCase → Validator/Rules`.
- Deletar as 6 classes vestigiais (2 orquestradores, 2 strategies, 2 interfaces de strategy).
- Reorganizar por feature: `application/contratacao` e `application/cancelamento` contêm cada uma seu use case, validator, rules e (no cancelamento) o context.
- Restaurar a pureza do `domain/`: sem imports de `entrypoint`/`application`, sem estereótipos Spring.
- Preservar 100% do comportamento externo observável.

**Non-Goals:**
- Não alterar o framework `shared/validationsetup`.
- Não alterar lógica de particionamento, transações ou mapeamento (MapStruct).
- Não mexer no `contratoquery` nem em banco/infra.
- Não introduzir camada de command objects entre entrypoint e application (menos abstração, não mais).

## Decisions

### D1: Deletar a camada de strategy em vez de generalizá-la

**Escolha**: remover `ContratacaoOrquestradorService`, `CancelamentoOrquestradorService`, `PixAutoService`, `DdaAutoService`, `ContratacaoService`, `CancelamentoService`.

**Alternativas consideradas**:
- *Manter uma strategy única com `Map<TipoProduto, ...>`*: continua sendo uma tabela em que todas as entradas apontam para a mesma função. Rejeitada.
- *Manter a interface como ponto de extensão futuro*: YAGNI — variação por produto em **regras** já é coberta pelo `aceita()` das rules; variação de **pipeline** é hipotética e barata de reintroduzir quando (se) aparecer.

**Racional**: o único trabalho observável da camada é rejeitar produto desconhecido com `BusinessException` 422, que passa a ser feito por uma rule (D2).

### D2: Checagem de produto suportado vira `ContratacaoRule`

**Escolha**: nova rule `ProdutoSuportado implements ContratacaoRule` em `application/contratacao/rules/`, com `aceita() → true` e `validar()` que tenta resolver `TipoProduto` a partir de `request.tipoProduto()` (case-insensitive, como hoje) e lança `BusinessException("Produto nao suportado ou invalido (tipoProduto: ...)")` — mesma mensagem do orquestrador atual.

**Alternativas**: fazer a checagem inline no use case. Rejeitada: a rule usa o framework existente, é injetada automaticamente e mantém o use case sem `if`s de validação.

**Atenção à ordem**: rules como `MetadadoRule` e `ValorLimiteContrato` podem depender do produto; como `Validator.validar()` itera a lista injetada pelo Spring, a `ProdutoSuportado` deve ser ordenada primeiro (`@Order(1)` ou `Ordered.HIGHEST_PRECEDENCE`) para que as demais rules possam assumir produto válido. Verificar na implementação se o default `validar()` itera em ordem de injeção — se sim, `@Order` resolve.

**Cancelamento não precisa de rule nova**: o header `tipoProduto` já chega tipado como enum (a conversão/rejeição de valor inválido acontece na borda), e `TipoProdutoCancelamento` já valida a divergência contra o produto do banco. Conferir na implementação qual status HTTP um header inválido gera hoje e preservá-lo.

### D3: Reorganização por feature, validators/rules saem do domínio

**Escolha**: estrutura alvo —

```
application/
  contratacao/
    CriarAutorizacaoUseCase
    ContratacaoValidator, ContratacaoRule
    rules/ ProdutoSuportado, DataFimVigenciaInvalida, ValorLimiteContrato, MetadadoRule
  cancelamento/
    CancelarAutorizacaoUseCase
    CancelamentoContext
    CancelamentoValidator, CancelamentoRule
    rules/ TipoProdutoCancelamento
  autorizacao/
    AutorizacaoRepository, AutorizacaoMapper      (compartilhados entre features)
domain/    entities, enums, converters, utilities  (puro)
shared/    exceptions, validationsetup, interceptors (intacto)
```

**Alternativas**: manter validators/rules em `domain/services` e criar command objects de domínio para eliminar o import de `entrypoint`. Rejeitada: adiciona uma camada de mapeamento (mais abstração) para preservar um rótulo. As rules validam DTOs de request e usam Spring — são código de aplicação; mover é o caminho honesto e sem custo.

**Racional**: coesão por feature — entender "contratação" passa a exigir 1 pacote, não 4. A violação `domain → entrypoint` é resolvida por relocação, não por abstração.

### D4: Controller injeta os use cases diretamente

**Escolha**: `AutorizacaoController` troca `{Contratacao,Cancelamento}OrquestradorService` por `CriarAutorizacaoUseCase` e `CancelarAutorizacaoUseCase`. Assinaturas dos endpoints inalteradas.

**Racional**: com a seleção de strategy extinta, uma fachada intermediária não agrega nada; `@Transactional` já vive nos use cases.

## Risks / Trade-offs

- **[Perda do ponto de extensão para produto com pipeline diferente]** → Variação de regras já é coberta por `aceita()` nas rules; se um produto exigir pipeline estruturalmente diferente, reintroduzir um branch point é mudança pequena e localizada no controller/use case.
- **[Ordem de execução das rules pode mudar comportamento de mensagens de erro]** → Garantir `ProdutoSuportado` primeiro via `@Order`; testes de contrato cobrem produto desconhecido (422 com a mesma mensagem).
- **[Renomear pacotes quebra referências em testes e docs]** → Compilador acusa testes; `CLAUDE.md`/`AGENTS.md` do módulo entram como task explícita (os dois devem permanecer espelhados).
- **[Spec existente `coesao-contratocommand` fica desatualizada]** → Delta spec nesta change substitui os 2 requirements afetados; os demais permanecem.
- **[Regressão silenciosa de comportamento HTTP]** → Rodar `mvn test` a cada task; manter os testes existentes de orquestrador/strategy como referência de comportamento até a task que os substitui/remove.

## Migration Plan

Refactor interno de um módulo, sem migração de dados nem deploy coordenado. Ordem segura (cada passo compila e passa testes):

1. Criar `ProdutoSuportado` rule + testes (estrutura antiga ainda de pé — comportamento fica duplicado temporariamente, sem conflito).
2. Apontar o controller para os use cases; deletar orquestradores, strategies e interfaces + seus testes (adaptando os cenários de "produto não suportado" para testes da rule/controller).
3. Mover use cases, validators, rules e context para os pacotes de feature; atualizar imports e testes.
4. Atualizar `CLAUDE.md`/`AGENTS.md` do módulo.

Rollback: revert do(s) commit(s) — sem estado externo envolvido.

## Open Questions

- Nenhuma bloqueante. (Confirmar durante implementação: comportamento HTTP atual para header `tipoProduto` inválido no PATCH, para preservá-lo byte a byte.)
