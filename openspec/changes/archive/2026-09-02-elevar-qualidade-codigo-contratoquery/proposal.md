## Why

A direção de ordenação da listagem do `contratoquery` é interpretada com
`"asc".equalsIgnoreCase(partes[1])`: qualquer valor que não seja exatamente `asc` — incluindo
erro de digitação como `ordenarPor=valor,ascc` ou `ordenarPor=valor,ASCENDING` — resulta
silenciosamente em ordem **descendente** com HTTP 200. O cliente pede a lista em ordem crescente
e recebe a lista ao contrário, sem nenhum sinal de erro.

Isso contradiz a postura já estabelecida na capacidade `limites-consulta-autorizacoes`, que
fecha a whitelist do **campo** de ordenação e rejeita valor desconhecido — mas deixa a
**direção** sem validação alguma. A change fecha essa metade que ficou aberta, encapsulando o
parâmetro `ordenarPor` em um value object de domínio que valida as duas partes.

## What Changes

- **BREAKING** (comportamento observável): `ordenarPor` com direção desconhecida passa a ser
  rejeitado com HTTP 422 em vez de aplicar `DESC` silenciosamente com HTTP 200. Requisições
  hoje malformadas que retornavam dados passam a retornar erro.
- Novo value object de domínio `Ordenacao` (campo + direção), com fábrica de parse que valida a
  string `"campo,direcao"` inteira e falha alto em qualquer parte inválida.
- Novo enum de domínio `DirecaoOrdenacao` (`ASC` / `DESC`), substituindo o `boolean ascendente`
  que trafega hoje entre caso de uso, porta e adaptador.
- `ListarAutorizacoesService` deixa de fazer `split(",")` na mão e delega o parse ao value object.
- A porta `AutorizacaoRepository.listarPorConta` passa a receber um `Ordenacao` no lugar do par
  `(CampoOrdenacao campoOrdenacao, boolean ordenacaoAscendente)`.
- Formatos que hoje passam despercebidos (`valor,`, `,asc`, `valor,asc,extra`) ganham
  tratamento explícito e mensagem de erro que diz o que foi recebido e o que é aceito.

**Fora de escopo** (decidido em 2026-09-01, ver `design.md`): endurecimento da borda de entrada
com `LayoutErrosApiValidationsResponse`/`occurrences` e remoção de `TipoEventoAutorizacao` /
`StatusAutorizacao.podeTransicionarPara` do `contratoquery` — ambos contradizem specs
deliberadas em vigor.

## Capabilities

### New Capabilities

Nenhuma. A change fecha uma lacuna dentro de uma capacidade já existente.

### Modified Capabilities

- `limites-consulta-autorizacoes`: o requisito de whitelist fechada de ordenação passa a cobrir
  também a **direção**, não só o campo. Direção desconhecida SHALL ser rejeitada com erro de
  contrato, e o sistema NÃO SHALL assumir um padrão silencioso quando a direção informada não é
  reconhecida.

## Impact

**Código de produção** (`apps/contratoquery`):

| Arquivo | Mudança |
|---|---|
| `domain/enums/DirecaoOrdenacao.java` | novo |
| `domain/model/Ordenacao.java` | novo — value object com parse validado |
| `application/usecase/ListarAutorizacoesService.java` | remove o parse manual; delega ao VO |
| `domain/port/out/AutorizacaoRepository.java` | assinatura: 2 parâmetros viram `Ordenacao` |
| `infrastructure/persistence/AutorizacaoJpaAdapter.java` | consome `Ordenacao` ao montar o `Sort` |

**Testes**: `ListarAutorizacoesServiceTest`, `AutorizacaoJpaAdapterTest` e
`ListarPorContaIntegrationTest` acompanham a nova assinatura; novo `OrdenacaoTest` cobre o parse.

**Contrato REST**: nenhuma mudança de campo, rota ou shape de resposta. A resposta de erro
continua sendo `LayoutErrosApiResponse` com HTTP 422, como já exigido por
`limites-consulta-autorizacoes`. O que muda é apenas *quando* o erro é emitido.

**Sem impacto**: `contratocommand` e demais serviços — `ordenarPor` só existe na listagem do
`contratoquery`. Nenhum espelhamento manual é afetado.
