## 1. Novo DTO de cancelamento

- [x] 1.1 Criar `infrastructure/web/contratosrest/CancelamentoResponseDto` (record:
      `codigoCanalCancelamento`, `idPessoaCancelamento`, `dataHoraCancelamento`,
      `motivoCancelamento`) com método estático `from(Cancelamento)` retornando `null`
      quando o argumento for `null`

## 2. Completar o DTO de detalhe

- [x] 2.1 Adicionar a `AutorizacaoDetalheResponseDto` os campos `frequenciaPagamento`,
      `quantidadeDividasCiclo`, `indicadorUsoLimiteConta`, `indicadorTipoMensageria`,
      `codigoCanalContratacao` (mesmos nomes e tipos do domínio) e `cancelamento`
      (`CancelamentoResponseDto`)
- [x] 2.2 Atualizar `AutorizacaoDetalheResponseDto.from(autorizacao)` para popular os 6
      campos novos a partir do modelo de domínio, usando
      `CancelamentoResponseDto.from(autorizacao.getCancelamento())` para o campo
      `cancelamento`

## 3. Testes

- [x] 3.1 Teste unitário de `CancelamentoResponseDto.from`: mapeia os 4 campos quando
      `Cancelamento` não é nulo; retorna `null` quando `Cancelamento` é nulo
- [x] 3.2 Estender `AutorizacaoDetalheResponseDtoTest` — no cenário de mapeamento completo
      já existente (`mapeiaCompleto`), incluir os valores de `frequenciaPagamento`,
      `quantidadeDividasCiclo`, `indicadorUsoLimiteConta`, `indicadorTipoMensageria` e
      `codigoCanalContratacao` no `Autorizacao.builder()` de fixture e assertar os 5
      campos no DTO resultante
- [x] 3.3 Novo teste: autorização sem cancelamento (`cancelamento(null)` no builder)
      resulta em `dto.cancelamento() == null`
- [x] 3.4 Novo teste: autorização com `Cancelamento` preenchido resulta em
      `dto.cancelamento()` não nulo com os 4 campos refletindo os valores do domínio

## 4. Documentação

- [x] 4.1 Atualizar `apps/contratoquery/CLAUDE.md` **e** `AGENTS.md` (espelhos): se houver
      seção descrevendo o shape de `AutorizacaoDetalheResponseDto`, incluir os 6 campos
      novos; manter a nota já existente sobre `tipoJornada` continuar de fora (decisão em
      aberto, não relacionada a esta change)
- [x] 4.2 Atualizar o graphify (`graphify-out/`) ao final da implementação, conforme
      convenção do monorepo (`--update --code-only` + `cluster-only`; `CancelamentoResponseDto`
      novo confirmado no `GRAPH_REPORT.md`)
