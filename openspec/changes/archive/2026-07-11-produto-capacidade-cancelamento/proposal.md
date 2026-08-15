## Why

Hoje só a contratação verifica se o produto é suportado (rule `ProdutoSuportado`), e o "suporte" é implícito: existir no enum `TipoProduto` significa estar habilitado. O cancelamento não tem verificação equivalente, e nenhum trecho da aplicação consegue perguntar de forma declarativa "este produto está habilitado para a capacidade X?". Isso impede, por exemplo, cadastrar um produto novo no enum que já contrata mas ainda não cancela (ou vice-versa).

## What Changes

- O enum `TipoProduto` (contratocommand) passa a declarar, por elemento, as capacidades habilitadas (contratar, cancelar) e a responder via métodos de instância — ex.: `habilitadoParaContratar()` e `habilitadoParaCancelar()` — tornando-se a fonte da verdade de capacidades por produto, consultável por qualquer trecho da aplicação.
- Nova rule `ProdutoSuportadoCancelamento` em `application/cancelamento/rules/`, espelhando a `ProdutoSuportado` da contratação: roda antes das demais `CancelamentoRule` (`@Order(HIGHEST_PRECEDENCE)`) e rejeita com `BusinessException` (HTTP 422) o cancelamento de produto não habilitado para cancelar.
- A rule `ProdutoSuportado` (contratação) passa a delegar a decisão ao enum: além de rejeitar produto desconhecido, rejeita produto conhecido porém não habilitado para contratar.
- `PIX_AUTO` e `DDA_AUTO` permanecem habilitados para ambas as capacidades — nenhum comportamento observável dos fluxos atuais muda.

## Capabilities

### New Capabilities
- `capacidades-produto`: o enum `TipoProduto` declara e responde, por produto, se cada capacidade (contratar, cancelar) está habilitada; as rules de contratação e cancelamento consomem essa resposta para aceitar ou rejeitar a operação.

### Modified Capabilities
- `coesao-contratocommand`: a rule `ProdutoSuportado` deixa de considerar "existe no enum" como suficiente e passa a exigir que o produto esteja habilitado para contratar; o pipeline de cancelamento ganha a rule `ProdutoSuportadoCancelamento` rodando antes das demais.

## Impact

- **Código**: `domain/enums/TipoProduto.java` (novos atributos/métodos de capacidade), `application/contratacao/rules/ProdutoSuportado.java` (delegar ao enum), novo `application/cancelamento/rules/ProdutoSuportadoCancelamento.java`.
- **Testes**: `TipoProdutoTest`, `ProdutoSuportadoTest`, novo `ProdutoSuportadoCancelamentoTest`; `CancelamentoValidator` passa a ter duas rules (verificar ordem).
- **APIs**: nenhum contrato REST muda; apenas um novo cenário de 422 no PATCH de cancelamento quando o produto não estiver habilitado para cancelar (hoje inalcançável, pois todos os produtos cancelam).
- **Escopo**: somente `contratocommand`; o `TipoProduto` do `contratoquery` não é alterado (leitura não tem capacidades de operação).
