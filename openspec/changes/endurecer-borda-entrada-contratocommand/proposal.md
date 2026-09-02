## Why

O `contratocommand` aceita hoje entradas que **corrompem dado silenciosamente** e devolve **500 para
erro do cliente**, violando uma convenção que o próprio repositório já declara. Dois defeitos
concretos, encontrados em análise de qualidade de código:

1. `quantidadeDividasCiclo` e `indicadorUsoLimiteConta` chegam como `Integer` sem `@Max` e são
   convertidos para `short` no modelo de domínio. Um valor acima de 32767 sofre *narrowing cast*
   silencioso (32768 vira −32768) e é persistido assim — a coluna no banco é `INT`, ou seja, quem
   perde a informação é exclusivamente o Java. O campo vizinho `frequencia` **tem** a proteção
   (`@Min(1) @Max(4)`), o que evidencia omissão, não decisão.
2. Um `idAutorizacao` malformado no path (`PATCH /api/autorizacoes/nao-e-uuid/cancelar`) chega a
   `UUID.fromString` dentro do use case, lança `IllegalArgumentException` e cai no
   `@ExceptionHandler(Exception.class)` — respondendo **500** com log de erro. A capability
   `contrato-api-consistente` já determina que entrada inválida do cliente retorna **422**: isto é
   correção de conformidade, não requisito novo. O efeito colateral operacional é relevante:
   qualquer chamador externo consegue inflar a taxa de 5xx e disparar alarme falso.

Some-se a isso um risco de regressão silenciosa: o bloco de carregamento + validação da autorização
está **triplicado** em `CancelarAutorizacaoService`, `DecidirAutorizacaoService` e
`AtualizarDadosRecorrenciaService`, e o `catch (Exception)` genérico desses três métodos reembala
`ConcurrencyFailureException` como `ApplicationException` — convertendo em **500** um conflito que o
`ApiExceptionHandler` mapearia corretamente para **409**.

## What Changes

- **Faixa numérica validada na borda**: `@Min(0) @Max(1)` em `indicadorUsoLimiteConta` (flag
  booleana) e `@Max(32767)` em `quantidadeDividasCiclo`, em `CriarAutorizacaoRequest` e
  `AtualizarDadosRecorrenciaRequest`. O teto de `quantidadeDividasCiclo` é o limite físico do
  `short`, deliberadamente **não** uma regra de negócio nova — o objetivo é impedir truncamento,
  não inventar limite que ninguém definiu.
- **Identificador da autorização validado na borda**: o id deixa de viajar como `String` até o use
  case. Um value object `AutorizacaoId` valida o formato UUID na construção; id malformado passa a
  responder **422** (`LayoutErrosApiResponse`), não 500, e deixa de poluir o log de erro.
- **Fonte única de carregamento + validação**: o trecho triplicado nos três use cases de escrita
  passa a ter um único ponto de verdade.
- **`catch` estreitado**: falha de concorrência ao carregar a autorização deixa de ser reembalada
  como `ApplicationException` e volta a chegar ao handler que a mapeia para **409**.

Sem breaking change: nenhuma resposta de sucesso muda, nenhum campo de contrato é adicionado ou
removido. As mudanças de status observáveis (500 → 422, 500 → 409) são correções de casos hoje
defeituosos.

## Capabilities

### New Capabilities

- `validacao-borda-entrada`: garante que nenhum dado do cliente atravessa a borda REST do
  `contratocommand` sem validação que preserve sua integridade — faixa numérica compatível com o
  tipo de destino (sem perda silenciosa de precisão) e formato do identificador de recurso
  verificado antes de alcançar a camada de aplicação.

### Modified Capabilities

- `coesao-contratocommand`: o requirement "Erros inesperados usam exceções do próprio projeto"
  passa a excluir explicitamente falhas de concorrência do reembalo em `ApplicationException`
  (elas mantêm o contrato 409 de `concorrencia-otimista-autorizacao`), e ganha requirement de
  fonte única para o carregamento de autorização nos use cases de escrita.

## Impact

**Código afetado** (`apps/contratocommand`):

| Arquivo | Mudança |
|---|---|
| `infrastructure/web/contratosrest/CriarAutorizacaoRequest.java` | `@Min`/`@Max` nos dois campos |
| `infrastructure/web/contratosrest/AtualizarDadosRecorrenciaRequest.java` | `@Min`/`@Max` nos dois campos |
| `domain/model/AutorizacaoId.java` | **novo** — value object com validação de formato |
| `infrastructure/web/AutorizacaoController.java` | constrói `AutorizacaoId` a partir do path |
| `domain/port/in/{Cancelar,Decidir,AtualizarDadosRecorrencia}Command.java` | `String` → `AutorizacaoId` |
| `application/usecase/{Cancelar,Decidir,AtualizarDadosRecorrencia}Service.java` | usa a fonte única; `catch` estreitado |
| `application/usecase/CarregadorAutorizacao.java` | **novo** — fonte única de carregamento |

> `ApiExceptionHandler` **não** é alterado: `AutorizacaoId` lança `BusinessException`, que o handler
> já mapeia para 422 com `LayoutErrosApiResponse` (ver design.md, D1).

**Contratos REST**: preservados. Mudam apenas os status de dois casos defeituosos (id malformado
500 → 422; conflito de concorrência no carregamento 500 → 409).

**Banco de dados**: nenhuma migration. As colunas já são `INT`.

**Eventos SNS**: nenhum impacto — o payload não muda.

**Testes**: `AutorizacaoControllerTest`, `ApiExceptionHandlerTest` e os três `*ServiceTest` de
escrita precisam de casos novos; os existentes que passam id como `String` precisam de ajuste de
assinatura.

**Fora de escopo** (registrado para change futura): a migração do
`AutorizacaoPersistenceMapper` para MapStruct — 24 campos mantidos à mão em três métodos, onde
esquecer um deles causa perda silenciosa de dado. Depende de um teste de equivalência campo a campo
escrito antes, e por isso não cabe nesta change.
