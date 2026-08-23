## Why

O frontend precisa exibir o tipo de produto (`PIX_AUTO`/`DDA_AUTO`) na listagem de autorizações
sem bater `GET /api/autorizacoes/{id}` item a item — necessidade concreta hoje bloqueada porque
`AutorizacaoResumidaResponseDto` (`GET /api/autorizacoes`, `contratoquery`) não expõe `tipoProduto`,
apesar de o campo já estar disponível ponta a ponta no domínio (é exposto no DTO de detalhe). Ao
investigar, achamos uma segunda lacuna do mesmo tipo: `motivoStatus` já existe e é preenchido no
DTO de listagem, mas nunca entrou na spec formal `listar-autorizacoes` — ajustamos os dois juntos
para não deixar a spec defasada de novo.

Esta não é uma correção de bug isolado: a spec `listar-autorizacoes` (Requirement "Estrutura do
DTO de resposta de listagem") enumera explicitamente os campos do DTO, e a ausência de
`tipoProduto` já havia sido investigada e documentada como comportamento real verificado em
`docs/contrato-api-para-gateway.md` (2026-08-11, seção "Divergências encontradas", item 5) — sem
nunca ter havido, em nenhum design doc, um racional para excluí-lo (ao contrário do caso de
`tipo_jornada`, que tem decisão em aberto documentada). A causa raiz remonta à change arquivada
`2026-06-09-mover-listagem-autorizacoes-para-contratoquery`, que moveu a listagem de
`contratocommand` para `contratoquery` como lift-and-shift do DTO já existente, sem reconsiderar
se o shape herdado ainda fazia sentido. Esta change trata a spec e o DTO como o que deveriam ter
saído daquela mudança, não como uma reinterpretação nova de requisito.

## What Changes

- `AutorizacaoResumidaResponseDto` (`contratoquery`, `GET /api/autorizacoes`) passa a incluir o
  campo `tipoProduto`, preenchido a partir de `Autorizacao.getTipoProduto()` (já carregado no
  domínio; nenhuma mudança de entidade JPA, mapper ou modelo de domínio é necessária).
- `motivoStatus`, que já existe e já é preenchido no DTO de listagem, passa a constar
  explicitamente na spec `listar-autorizacoes` — hoje é comportamento não especificado.
- Spec `listar-autorizacoes` (Requirement "Estrutura do DTO de resposta de listagem") passa a
  listar `tipoProduto` e `motivoStatus` entre os campos obrigatórios do item de listagem, com
  cenário novo cobrindo `tipoProduto`.
- `docs/contrato-api-para-gateway.md` é atualizado: o exemplo de resposta de `GET /api/autorizacoes`
  passa a incluir os dois campos, e o item 5 de "Divergências encontradas" (que hoje documenta a
  ausência de `tipoProduto` como comportamento correto) é removido/corrigido, já que a divergência
  deixa de existir.
- Sem **BREAKING**: mudança aditiva — nenhum campo existente é removido ou renomeado, nenhum
  cliente que já consome a listagem quebra.

## Capabilities

### New Capabilities

*(nenhuma)*

### Modified Capabilities

- `listar-autorizacoes`: Requirement "Estrutura do DTO de resposta de listagem" passa a exigir
  `tipoProduto` e `motivoStatus` como campos do item de listagem retornado por
  `GET /api/autorizacoes`.

## Impact

- **contratoquery** (`apps/contratoquery`): `infrastructure/web/contratosrest/AutorizacaoResumidaResponseDto.java`
  (novo campo + `.from()`) e `AutorizacaoResumidaResponseDtoTest` (cenário novo). Nenhuma mudança
  em `domain/`, `infrastructure/persistence/` ou `contratocommand` — `tipoProduto` já chega ao
  domínio hoje.
- **Documentação**: `openspec/specs/listar-autorizacoes/spec.md` (spec fonte da verdade) e
  `docs/contrato-api-para-gateway.md` (insumo temporário para o gateway, com prazo de validade
  próprio — ver nota no topo do arquivo).
- **Clientes existentes da listagem**: nenhum impacto negativo — adição de campos é
  retrocompatível para qualquer consumidor JSON tolerante a campos novos.
