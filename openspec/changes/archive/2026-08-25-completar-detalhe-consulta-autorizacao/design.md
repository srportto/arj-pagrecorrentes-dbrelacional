## Context

`contratoquery` já tem toda a informação necessária — os 6 campos existem em
`domain/model/Autorizacao` (`frequenciaPagamento`, `quantidadeDividasCiclo`,
`indicadorUsoLimiteConta`, `indicadorTipoMensageria`, `codigoCanalContratacao`, `cancelamento`)
e são preenchidos por `AutorizacaoPersistenceMapper.paraDominio`. O gap é só no último passo:
`AutorizacaoDetalheResponseDto.from(autorizacao)` nunca lê esses 6 campos do modelo. Não há
migração de schema, não há novo dado a buscar — é fechar um mapeamento incompleto.

`contratocommand` já resolveu o mesmo problema para o mesmo conjunto de campos em
`AutorizacaoCompletaResponseDto`/`CancelamentoResponseDto` — esta change replica a forma, não
inventa uma nova.

## Goals / Non-Goals

**Goals:**
- `GET /api/autorizacoes/{autorizacaoId}` devolver todo campo de `domain/model/Autorizacao` que
  não tenha uma razão documentada para ficar de fora.
- Seguir o padrão já usado por `contratocommand` para expor dados de cancelamento (DTO aninhado,
  `null` quando não houve cancelamento).

**Non-Goals:**
- Não resolve a divergência de nomes/tipos entre `contratocommand` e `contratoquery` (`valor` vs
  `valorAutorizacao`, `status` `String` vs `Integer`, etc.) — dívida aceita e fora do gatilho de
  correção documentado no `CLAUDE.md` raiz.
- Não expõe `tipoJornada` — decisão de contrato em aberto, tratada em change própria
  (`temporizacao-jornada-01-pix-auto`), não um campo esquecido.
- Não muda `AutorizacaoResumidaResponseDto` (listagem) — seu shape reduzido é requirement
  intencional de `listar-autorizacoes`, e "completar o detalhe" não estica para a listagem.
- Não muda `contratocommand` nem nenhum outro serviço — puramente leitura, campo já existente.

## Decisions

### D1: Nomes dos 6 campos ficam idênticos ao domínio (sem renomear)

`frequenciaPagamento`, `quantidadeDividasCiclo`, `indicadorUsoLimiteConta`,
`indicadorTipoMensageria` e `codigoCanalContratacao` já têm o mesmo nome nos dois serviços — não
fazem parte da divergência command/query documentada (que é sobre `valor`/`dataCriacao`/`status`).
Não há motivo para introduzir um nome novo aqui; usar o mesmo nome do domínio evita mais uma
divergência sem propósito.

### D2: `cancelamento` como objeto aninhado via novo `CancelamentoResponseDto`, não campos soltos

**Alternativas consideradas:**
- *Achatar os 4 campos de cancelamento direto no `AutorizacaoDetalheResponseDto*` (ex.:
  `codigoCanalCancelamento`, `idPessoaCancelamento`, ...): rejeitada — infla o DTO principal com
  campos que só fazem sentido em conjunto e que ficam `null` em massa (autorização nunca
  cancelada), e diverge do padrão já estabelecido no `contratocommand`.
- **Escolhida: objeto aninhado `cancelamento` (tipo `CancelamentoResponseDto`, `null` se a
  autorização nunca foi cancelada)** — espelha exatamente `AutorizacaoCompletaResponseDto.cancelamento`
  do `contratocommand`, reaproveitando um padrão já validado em produção.

### D3: Novo DTO é `record`, não `@Data`/`@Builder`

`contratoquery` já usa `record` para os dois DTOs de resposta existentes
(`AutorizacaoDetalheResponseDto`, `AutorizacaoResumidaResponseDto`) — `CancelamentoResponseDto`
segue a mesma convenção local, mesmo o equivalente em `contratocommand` sendo `@Data @Builder`
(convenção daquele serviço, não obrigatória aqui).

## Risks / Trade-offs

- **[Risco] Cliente que hoje ignora campos desconhecidos no JSON não é afetado — mas um cliente
  com deserialização estrita (`FAIL_ON_UNKNOWN_PROPERTIES`-like no sentido inverso, schema
  validation rígido) poderia, em teoria, já ter uma expectativa implícita do shape antigo** →
  **Mitigação**: mudança é puramente aditiva (nenhum campo removido/renomeado); é o padrão REST
  aceito no repo para evolução aditiva de contrato (ver `docs/contrato-api-para-gateway.md`, que
  já trata adição de endpoint/campo como não-breaking).
- **[Risco] `AutorizacaoDetalheResponseDtoTest` existente pode ter asserção implícita de "só estes
  campos"** → **Mitigação**: revisar o teste existente ao implementar; se ele fizer comparação
  campo-a-campo, precisa ganhar os novos casos, não quebrar.

## Migration Plan

Aditivo, sem migração de schema e sem dado a migrar (os 6 campos já existem na tabela desde a
criação). Deploy é o deploy normal de uma versão nova do `contratoquery` — nenhuma coordenação
com os outros 4 serviços é necessária.

## Open Questions

(nenhuma)
