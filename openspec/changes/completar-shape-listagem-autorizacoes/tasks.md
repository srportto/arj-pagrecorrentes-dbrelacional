## 1. DTO de listagem (contratoquery)

- [x] 1.1 Adicionar o campo `tipoProduto` (tipo `TipoProduto`) a `AutorizacaoResumidaResponseDto`
      (`infrastructure/web/contratosrest/AutorizacaoResumidaResponseDto.java`)
- [x] 1.2 Preencher `tipoProduto` em `AutorizacaoResumidaResponseDto.from()` via
      `autorizacao.getTipoProduto()`
- [x] 1.3 Confirmar que `motivoStatus` (já existente no record e já preenchido em `.from()`) não
      precisa de nenhuma mudança de código — só de spec (coberto na seção 3)

## 2. Testes

- [x] 2.1 Atualizar `AutorizacaoResumidaResponseDtoTest` para asserir `dto.tipoProduto()` no
      cenário `mapeiaCompleto()`
- [x] 2.2 Adicionar cenário cobrindo os dois valores de `TipoProduto` (`PIX_AUTO` e `DDA_AUTO`)
      no DTO de listagem
- [x] 2.3 Rodar `mvn test -Dtest=AutorizacaoResumidaResponseDtoTest` em `apps/contratoquery`

## 3. Spec e documentação

- [x] 3.1 Confirmar que o delta em `openspec/changes/completar-shape-listagem-autorizacoes/specs/listar-autorizacoes/spec.md`
      reflete o shape final do DTO (`tipoProduto` e `motivoStatus` na lista de campos)
- [x] 3.2 Atualizar `docs/contrato-api-para-gateway.md`: exemplo de resposta de
      `GET /api/autorizacoes` passa a incluir `tipoProduto` e `motivoStatus`
- [x] 3.3 Remover/corrigir o item 5 de "Divergências encontradas" em
      `docs/contrato-api-para-gateway.md` (hoje documenta a ausência de `tipoProduto` como
      comportamento correto — deixa de ser verdade após esta change)

## 4. Validação final

- [x] 4.1 `mvn clean package` em `apps/contratoquery` sem erros
- [x] 4.2 `mvn test` completo em `apps/contratoquery` verde (79 testes, 0 falhas, 0 erros, 11 skipped por perfil de integração)
- [x] 4.3 Conferir manualmente uma resposta real de `GET /api/autorizacoes` (ambiente local) e
      confirmar presença de `tipoProduto` e `motivoStatus` no JSON de cada item — confirmado
      contra o ambiente local (`docker compose`), incluindo os dois valores de `TipoProduto`
      (`PIX_AUTO` e `DDA_AUTO`) em itens reais da listagem
- [ ] 4.4 Rodar `openspec archive completar-shape-listagem-autorizacoes` após merge, sincronizando
      o delta com `openspec/specs/listar-autorizacoes/spec.md`
