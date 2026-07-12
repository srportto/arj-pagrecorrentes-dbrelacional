## 1. Capacidades no enum TipoProduto

- [x] 1.1 Adicionar ao `TipoProduto` (arj-contratocommand) os atributos booleanos de capacidade no construtor (`PIX_AUTO(1L, true, true)`, `DDA_AUTO(2L, true, true)`) e os métodos `habilitadoParaContratar()` / `habilitadoParaCancelar()`, mantendo os lookups estáticos existentes
- [x] 1.2 Atualizar `TipoProdutoTest` cobrindo as respostas de capacidade de cada constante (ambas `true` para os produtos atuais)

## 2. Rule de cancelamento ProdutoSuportadoCancelamento

- [x] 2.1 Criar `ProdutoSuportadoCancelamento` em `application/cancelamento/rules/`: `@Component` + `@Order(Ordered.HIGHEST_PRECEDENCE)`, `aceita() → true`, `validar()` lança `BusinessException` ("Produto nao habilitado para cancelamento (tipoProduto: ...)") quando `context.tipoProduto().habilitadoParaCancelar()` for `false`
- [x] 2.2 Criar `ProdutoSuportadoCancelamentoTest` cobrindo: produto habilitado passa; produto desabilitado lança `BusinessException`
- [x] 2.3 Verificar (teste do `CancelamentoValidator` ou de ordenação) que `ProdutoSuportadoCancelamento` executa antes de `TipoProdutoCancelamento`

## 3. ProdutoSuportado delega ao enum

- [x] 3.1 Refatorar `ProdutoSuportado.validar()` para resolver a `String` do request em `TipoProduto` e exigir `habilitadoParaContratar()`, preservando a mensagem "Produto nao suportado ou invalido (tipoProduto: ...)" para desconhecido/nulo/desabilitado
- [x] 3.2 Atualizar `ProdutoSuportadoTest`: casos existentes (conhecido em qualquer caixa passa; desconhecido/nulo rejeita) seguem verdes e novo caso de produto desabilitado para contratar rejeita

## 4. Verificação final

- [x] 4.1 Rodar `mvn test` em `aplicacoes/arj-contratocommand` — suíte completa verde
- [x] 4.2 Conferir que nenhum contrato REST mudou (endpoints, headers e códigos HTTP intactos; apenas novo cenário 422 no cancelamento) e que o `TipoProduto` do `arj-contratoquery` não foi tocado
