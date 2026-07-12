## Context

O `arj-contratocommand` valida operações via framework de rules (`Rule<T>` / `Validator<R,T>` em `shared/validationsetup`). Na contratação, a rule `ProdutoSuportado` (`@Order(HIGHEST_PRECEDENCE)`) rejeita `tipoProduto` desconhecido comparando a `String` do request contra `TipoProduto.values()`. No cancelamento existe só a `TipoProdutoCancelamento`, que compara o produto do header com o produto lido do banco — não há verificação de "produto habilitado a cancelar", porque hoje habilitação é implícita: existir no enum basta.

O enum `TipoProduto` (`domain/enums`) tem dois elementos (`PIX_AUTO`, `DDA_AUTO`) com um atributo `long` (id) e dois lookups estáticos que lançam `BusinessException`.

## Goals / Non-Goals

**Goals:**
- `TipoProduto` como fonte da verdade de capacidades: cada elemento declara se está habilitado para **contratar** e para **cancelar**, e responde via métodos de instância consultáveis por qualquer camada.
- Rule `ProdutoSuportadoCancelamento` espelhando a `ProdutoSuportado`: primeira do pipeline de cancelamento, rejeita com `BusinessException` (422) produto não habilitado a cancelar.
- `ProdutoSuportado` (contratação) delega a decisão ao enum em vez de só checar existência.
- Comportamento atual preservado: `PIX_AUTO` e `DDA_AUTO` habilitados para ambas as capacidades.

**Non-Goals:**
- Não alterar o `TipoProduto` do `arj-contratoquery` (leitura não tem capacidades de operação).
- Não externalizar capacidades em configuração/banco — declaração fica no código do enum.
- Não criar capacidade "consultar" nem antecipar capacidades futuras além de contratar/cancelar.
- Não mudar contratos REST, DTOs ou o framework de validação.

## Decisions

### 1. Capacidades como atributos `boolean` no construtor do enum

Cada constante declara suas capacidades na própria definição:

```java
PIX_AUTO(1L, true, true),
DDA_AUTO(2L, true, true);
```

com métodos `habilitadoParaContratar()` e `habilitadoParaCancelar()`.

- **Por quê**: é a forma mais simples e legível para 2 capacidades; a resposta fica no domínio (sem Spring), consultável de qualquer camada, e o compilador obriga todo produto novo a declarar suas capacidades explicitamente.
- **Alternativa considerada — `EnumSet<Capacidade>` por elemento**: mais extensível se surgirem muitas capacidades, mas adiciona um segundo enum e indireção sem necessidade atual (YAGNI). Se uma terceira capacidade aparecer, a migração para `EnumSet` é mecânica.
- **Alternativa considerada — mapa em configuração (`application.yaml`)**: permitiria ligar/desligar sem redeploy, mas quebra a regra do projeto de domínio puro sem Spring e cria estado divergente entre ambientes; descartada.

### 2. `ProdutoSuportadoCancelamento` valida capacidade, `TipoProdutoCancelamento` continua validando divergência

Nova rule em `application/cancelamento/rules/`, `@Component` + `@Order(Ordered.HIGHEST_PRECEDENCE)`, com `aceita() → true`. Valida `context.tipoProduto().habilitadoParaCancelar()` e lança `BusinessException("Produto nao habilitado para cancelamento (tipoProduto: ...)")`.

- **Por quê separar em rule nova em vez de estender `TipoProdutoCancelamento`**: espelha a estrutura da contratação (uma rule por preocupação), mantém `@Order` explícito para rodar primeiro — assim as demais rules podem assumir produto habilitado, mesmo contrato da `ProdutoSuportado` — e segue a convenção documentada de "adicionar regra = novo `@Component` que implementa a interface".
- No cancelamento o `tipoProduto` já chega como enum (o controller resolve o header via `obterTipoProdutoEnumPorNome`, que rejeita desconhecidos com 422), então a rule só precisa consultar a capacidade — sem parsing de `String`.

### 3. `ProdutoSuportado` delega ao enum

Mantém a responsabilidade de rejeitar produto desconhecido (o request de criação traz `tipoProduto` como `String`), mas a decisão final passa a ser do enum: resolve a `String` para `TipoProduto` e exige `habilitadoParaContratar()`. Mensagem de erro atual ("Produto nao suportado ou invalido...") preservada para o caso desconhecido/não habilitado.

- **Por quê**: cumpre o pedido de o enum "responder se está habilitado a contratar" e elimina a duplicação entre "existir no enum" e "estar habilitado". A iteração manual por `values()` pode ser trocada por try/catch de `obterTipoProdutoEnumPorNome` ou mantida — detalhe de implementação; o requisito é a decisão vir do enum.

## Risks / Trade-offs

- **[Capacidade hardcoded exige redeploy para mudar]** → Aceito deliberadamente: capacidade de produto é decisão de negócio estável, não toggle operacional. Se virar toggle, migrar para configuração será uma change própria.
- **[Enum duplicado no `arj-contratoquery` pode divergir]** → Fora de escopo por decisão (query não contrata/cancela); risco documentado no proposal para não parecer omissão.
- **[Nenhum produto hoje exercita o caminho "não habilitado" em produção]** → O caminho novo só é verificável por teste unitário; cobrir com testes parametrizados nas duas rules e no enum para o cenário desabilitado (via mock/constante de teste ou asserção direta dos métodos).
- **[Duas rules de cancelamento sem `@Order` relativo até agora]** → `ProdutoSuportadoCancelamento` recebe `HIGHEST_PRECEDENCE`; `TipoProdutoCancelamento` fica sem ordem (roda depois). Verificar em teste do `CancelamentoValidator` que a ordem observada é a esperada.

## Migration Plan

Mudança somente de código, sem migração de dados nem alteração de contrato REST. Deploy padrão; rollback = reverter o commit.

## Open Questions

- Nenhuma. (Se surgir um produto que contrata mas não cancela, basta declará-lo `(id, true, false)` — o pipeline já rejeitará o cancelamento com 422.)
