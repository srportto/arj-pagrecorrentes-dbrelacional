## ADDED Requirements

### Requirement: TipoProduto é a fonte da verdade das capacidades por produto

O enum `TipoProduto` do `contratocommand` SHALL declarar, em cada constante, se o produto está habilitado para as capacidades **contratar** e **cancelar**, e SHALL responder a essas perguntas via métodos de instância (`habilitadoParaContratar()`, `habilitadoParaCancelar()`) consultáveis por qualquer trecho da aplicação, sem dependência de Spring. Adicionar uma nova constante MUST exigir a declaração explícita das duas capacidades. `PIX_AUTO` e `DDA_AUTO` MUST permanecer habilitados para ambas as capacidades.

#### Scenario: Consulta de capacidade de contratação

- **WHEN** qualquer componente consulta `habilitadoParaContratar()` de uma constante de `TipoProduto`
- **THEN** o enum responde `true`/`false` conforme declarado na própria constante, sem consultar configuração externa

#### Scenario: Consulta de capacidade de cancelamento

- **WHEN** qualquer componente consulta `habilitadoParaCancelar()` de uma constante de `TipoProduto`
- **THEN** o enum responde `true`/`false` conforme declarado na própria constante

#### Scenario: Produtos atuais habilitados para tudo

- **WHEN** as capacidades de `PIX_AUTO` e `DDA_AUTO` são consultadas
- **THEN** ambas as constantes respondem `true` para contratar e para cancelar

### Requirement: Cancelamento rejeita produto não habilitado para cancelar

Uma `CancelamentoRule` (`ProdutoSuportadoCancelamento`) SHALL executar antes das demais rules de cancelamento (`@Order(HIGHEST_PRECEDENCE)`) e SHALL rejeitar com `BusinessException` (HTTP 422) o cancelamento quando o `TipoProduto` do contexto responder que não está habilitado para cancelar. A validação de divergência entre o produto do header e o produto da autorização (`TipoProdutoCancelamento`) MUST permanecer como rule separada, executando depois.

#### Scenario: Cancelamento de produto habilitado

- **WHEN** uma requisição `PATCH /api/autorizacoes/{id}/cancelar` chega com header `tipoProduto` de um produto habilitado para cancelar
- **THEN** a rule `ProdutoSuportadoCancelamento` aprova e o fluxo segue para as demais rules, preservando o comportamento atual

#### Scenario: Cancelamento de produto não habilitado

- **WHEN** o contexto de cancelamento carrega um `TipoProduto` cuja capacidade de cancelar está desabilitada
- **THEN** a rule `ProdutoSuportadoCancelamento` lança `BusinessException` (HTTP 422) informando que o produto não está habilitado para cancelamento

#### Scenario: Capacidade é verificada antes da divergência de produto

- **WHEN** um cancelamento viola simultaneamente a capacidade de cancelar e a regra de divergência de produto
- **THEN** o erro reportado é o de produto não habilitado para cancelamento, pois `ProdutoSuportadoCancelamento` executa primeiro

### Requirement: Contratação exige produto habilitado para contratar

A rule `ProdutoSuportado` SHALL delegar ao enum a decisão de aceite: além de rejeitar `tipoProduto` desconhecido ou nulo, SHALL rejeitar com `BusinessException` (HTTP 422) produto conhecido cujo `habilitadoParaContratar()` responda `false`. A mensagem de erro atual de produto não suportado MUST ser preservada para os casos de rejeição.

#### Scenario: Contratação de produto habilitado

- **WHEN** uma requisição `POST /api/autorizacoes` chega com `tipoProduto` conhecido e habilitado para contratar (em qualquer caixa)
- **THEN** a rule `ProdutoSuportado` aprova e o fluxo segue inalterado

#### Scenario: Contratação de produto conhecido porém desabilitado

- **WHEN** o `tipoProduto` do request resolve para uma constante cujo `habilitadoParaContratar()` é `false`
- **THEN** a rule `ProdutoSuportado` lança `BusinessException` (HTTP 422) com a mensagem de produto não suportado
