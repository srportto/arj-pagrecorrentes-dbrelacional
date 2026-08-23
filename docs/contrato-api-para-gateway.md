# Contrato de API — insumo para o gateway

> **Este arquivo tem prazo de validade.** Ele existe porque as anotações springdoc/OpenAPI foram
> removidas do código de produção (`contratocommand`, `contratoquery`) sem que o gateway
> ainda tivesse absorvido o contrato — decisão registrada em
> `openspec/changes/archive/2026-08-15-limpar-codigo-das-apps/design.md` (D1, D1b). Ele consolida as três fontes que
> carregavam esse contrato no repositório, para servir de insumo a quem montar o gateway.
> **Quando o gateway assumir o contrato, remova este arquivo.**
>
> Gerado em 2026-08-11, a partir de:
> 1. anotações `io.swagger.v3.oas.annotations.*` dos dois `AutorizacaoController` (antes da remoção);
> 2. `apps/contratocommand/README.md`, seção "API REST Endpoints" (linhas 268-380, antes da remoção);
> 3. `apps/contratoquery/README.md`, seção "API REST Endpoints" (linhas 181-264, antes da remoção).
>
> Onde as fontes 2 e 3 divergiam do código real, este documento segue o código — ver
> "Divergências encontradas" ao final.

## contratocommand (porta 8080)

Base: `/api/autorizacoes`. API de escrita — cria, cancela e decide autorizações.

### POST `/api/autorizacoes` — Criar autorização (multi-produto)

Cria uma autorização para o produto informado no campo `tipoProduto` (`PIX_AUTO` nasce em
`RECEBIDA` aguardando aprovação; `DDA_AUTO` nasce `ATIVA`). O header `tipoJornada` identifica a
jornada (ex.: `SPI_J1`).

**Header obrigatório:** `tipoJornada` (string, ex.: `SPI_J1`) — resolvido para o enum
`TipoJornadaAutorizacao`.

**Request body** (`CriarAutorizacaoRequest`):

| Campo | Tipo | Obrigatório | Validação |
|---|---|---|---|
| `dataFimVigencia` | LocalDate | não | não pode estar no passado (regra de negócio) |
| `tipoProduto` | String | sim | `PIX_AUTO` ou `DDA_AUTO` |
| `valor` | BigDecimal | sim | — |
| `idAutorizacaoEmpresa` | String | sim | idempotência: mesmo id na mesma partição → 409 |
| `valorLimite` | BigDecimal | não | — |
| `frequencia` | Integer | sim | 1 a 4 (semanal a trimestral) |
| `quantidadeDividasCiclo` | Integer | sim | ≥ 1 |
| `indicadorUsoLimiteConta` | Integer | sim | — |
| `codigoCanalContratacao` | String | sim | — |
| `descricao` | String | não | — |
| `idUnicoContaContratante` | UUID | sim | — |
| `idPessoaPagadora` | UUID | sim | — |
| `idPessoaDevedora` | UUID | sim | — |
| `idPessoaRecebedora` | UUID | sim | — |
| `metadados` | JsonNode | não | objeto livre |

Exemplo de corpo:
```json
{
  "dataFimVigencia": "2026-12-31",
  "tipoProduto": "PIX_AUTO",
  "valor": 500.00,
  "idAutorizacaoEmpresa": "EMP001",
  "valorLimite": 10000.00,
  "frequencia": 2,
  "quantidadeDividasCiclo": 5,
  "indicadorUsoLimiteConta": 1,
  "codigoCanalContratacao": "01",
  "descricao": "Autorização PIX automática para transferências",
  "idUnicoContaContratante": "550e8400-e29b-41d4-a716-446655440000",
  "idPessoaPagadora": "550e8400-e29b-41d4-a716-446655440001",
  "idPessoaDevedora": "550e8400-e29b-41d4-a716-446655440002",
  "idPessoaRecebedora": "550e8400-e29b-41d4-a716-446655440003",
  "metadados": { "origem": "MOBILE", "versao_contrato": "1.0" }
}
```

**Respostas:**

| Status | Quando | Schema |
|---|---|---|
| 201 | Autorização criada. Header `Location` com a URI do recurso. | `AutorizacaoCompletaResponseDto` |
| 409 | `idAutorizacaoEmpresa` já existe na partição, ou conflito de concorrência | `LayoutErrosApiResponse` |
| 422 | Falha de validação de formato (`@Valid`) ou violação de regra de negócio | `LayoutErrosApiValidationsResponse` **ou** `LayoutErrosApiResponse` (ver nota de shape abaixo) |
| 500 | Erro inesperado de aplicação | `LayoutErrosApiResponse` |

`AutorizacaoCompletaResponseDto` (shape real, verificado contra o código —
`entrypoint/contratosrest/AutorizacaoCompletaResponseDto.java`):

```json
{
  "idAutorizacao": "550e8400-e29b-41d4-a716-446655440000",
  "dataFimVigencia": "2026-12-31",
  "tipoProduto": "PIX_AUTO",
  "status": 1,
  "motivoStatus": "RECEBIDA",
  "dataInicioVigencia": "2026-08-11",
  "dataHoraInclusao": "2026-08-11T14:32:00",
  "dataHoraUltimaAtualizacao": "2026-08-11T14:32:00",
  "valorAutorizacao": 500.00,
  "idAutorizacaoEmpresa": "EMP001",
  "valorLimite": 10000.00,
  "frequenciaPagamento": 2,
  "quantidadeDividasCiclo": 5,
  "indicadorUsoLimiteConta": 1,
  "indicadorTipoMensageria": null,
  "codigoCanalContratacao": "01",
  "descricao": "Autorização PIX automática para transferências",
  "idUnicoContaContratante": "550e8400-e29b-41d4-a716-446655440000",
  "idPessoaPagadora": "550e8400-e29b-41d4-a716-446655440001",
  "idPessoaDevedora": "550e8400-e29b-41d4-a716-446655440002",
  "idPessoaRecebedora": "550e8400-e29b-41d4-a716-446655440003",
  "cancelamento": null,
  "metadados": { "origem": "MOBILE", "versao_contrato": "1.0" }
}
```

**Importante para quem for montar o gateway:** `status` é o **código inteiro** do enum
`StatusAutorizacao` nesta API (não o nome). O `contratoquery` expõe `status` como string —
divergência de design aceita e documentada em `CLAUDE.md` da raiz ("Command e query têm
representações distintas por design").

### PATCH `/api/autorizacoes/{idAutorizacao}/cancelar` — Cancelar autorização

Cancela uma autorização existente. Header `tipoProduto` obrigatório; deve bater com o produto
persistido.

**Path param:** `idAutorizacao` (string/UUID)
**Header obrigatório:** `tipoProduto` (ex.: `PIX_AUTO`) — resolvido para o enum `TipoProduto`.

**Request body** (`CancelarAutorizacaoRequest`):

| Campo | Tipo | Obrigatório |
|---|---|---|
| `codigoCanalCancelamento` | String | sim |
| `idPessoaCancelamento` | UUID | sim |
| `motivoCancelamento` | String | não |

```json
{
  "codigoCanalCancelamento": "01",
  "idPessoaCancelamento": "550e8400-e29b-41d4-a716-446655440001",
  "motivoCancelamento": "SOLICITACAO_CLIENTE"
}
```

**Respostas:**

| Status | Quando | Schema |
|---|---|---|
| 200 | Autorização cancelada | `AutorizacaoCompletaResponseDto` |
| 409 | Conflito de concorrência (lock otimista, stale state ou violação de integridade) | `LayoutErrosApiResponse` |
| 422 | Falha de validação de formato ou de regra — **inclui autorização inexistente** (`BusinessException("Autorização não encontrada...")`), transição de status inválida, produto divergente | `LayoutErrosApiValidationsResponse` ou `LayoutErrosApiResponse` |
| 500 | Erro inesperado de aplicação | `LayoutErrosApiResponse` |

> Não existe 404 neste endpoint — ver divergência 7 abaixo.

### PATCH `/api/autorizacoes/{idAutorizacao}/decisao` — Decidir sobre autorização em RECEBIDA

Aplica uma decisão (`APROVAR` / `REJEITAR` / `EXPIRAR`) sobre uma autorização em `RECEBIDA`
(jornada 1 do PIX_AUTO). Idempotente: status diferente de `RECEBIDA` resulta em 422 sem alterar a
linha — sinal para o chamador automatizado não repetir.

**Path param:** `idAutorizacao` (string/UUID)
**Header obrigatório:** `tipoProduto` (ex.: `PIX_AUTO`)

**Request body** (`DecisaoAutorizacaoRequest`):

| Campo | Tipo | Obrigatório |
|---|---|---|
| `acao` | String (`APROVAR`\|`REJEITAR`\|`EXPIRAR`) | sim |
| `codigoCanalDecisao` | String | não |
| `idPessoaDecisao` | UUID | não |

```json
{ "acao": "APROVAR", "codigoCanalDecisao": "01", "idPessoaDecisao": "550e8400-e29b-41d4-a716-446655440001" }
```

**Respostas:**

| Status | Quando | Schema |
|---|---|---|
| 200 | Decisão aplicada | `AutorizacaoCompletaResponseDto` |
| 409 | Conflito de concorrência | `LayoutErrosApiResponse` |
| 422 | Status atual não permite decisão (já resolvida), ação inválida, divergência de produto — **inclui autorização inexistente** | `LayoutErrosApiValidationsResponse` ou `LayoutErrosApiResponse` |
| 500 | Erro inesperado de aplicação | `LayoutErrosApiResponse` |

> Não existe 404 neste endpoint — ver divergência 7 abaixo.

### Shapes de erro do command

`LayoutErrosApiResponse` (regra de negócio / conflito / não encontrado / erro inesperado):
```json
{
  "timestamp": "2026-08-11T14:32:00Z",
  "error": "Unprocessable Content",
  "message": "Data de fim de vigência não pode estar no passado",
  "path": "/api/autorizacoes"
}
```

`LayoutErrosApiValidationsResponse` (falha de formato via `@Valid`, estende o anterior com
`occurrences`):
```json
{
  "timestamp": "2026-08-11T14:32:00Z",
  "error": "Requisicao nao respeitou as validacoes basicas do contrato",
  "message": "Erro durante a validacao da requisicao, confira as occurrences",
  "path": "/api/autorizacoes",
  "occurrences": [
    { "fieldName": "frequencia", "message": "O campo 'frequencia' deve ser maior ou igual a 1." }
  ]
}
```

**Convenção de status (D3, 2026-08-09):** tanto falha de formato (`@Valid`) quanto violação de
regra de negócio (`BusinessException`) retornam **422**. A distinção entre as duas é carregada
pelo **shape** da resposta (`LayoutErrosApiValidationsResponse` com `occurrences` vs
`LayoutErrosApiResponse` sem), não pelo status HTTP.

---

## contratoquery (porta 8081)

Base: `/api/autorizacoes`. API de leitura de autorizações — somente `GET`.

### GET `/api/autorizacoes` — Listar autorizações paginadas por conta contratante

Retorna uma página de autorizações resumidas da conta contratante. Suporta filtro por status,
paginação e ordenação. Parâmetros de borda são validados como regra de negócio (422).

**Query params:**

| Parâmetro | Tipo | Obrigatório | Padrão | Descrição |
|---|---|---|---|---|
| `idUnicoContaContratante` | UUID | sim (validado como regra de negócio, não binding) | — | UUID da conta contratante |
| `status` | String, repetível | não | todos | Nome do enum (ex.: `ATIVA`, `RECEBIDA`); filtro multi-valor |
| `pagina` | Integer | não | `0` | Índice da página (base 0) |
| `tamanho` | Integer | não | `20` | 1 a 100 |
| `ordenarPor` | String | não | `dataHoraInclusao,desc` | Campo + direção; whitelist fechada |

**Resposta 200** (`PaginacaoResponseDto<AutorizacaoResumidaResponseDto>`, shape real):
```json
{
  "conteudo": [
    {
      "idAutorizacao": "550e8400-e29b-41d4-a716-446655440000",
      "tipoProduto": "PIX_AUTO",
      "dataCriacao": "2026-08-11T14:32:00",
      "dataInicioVigencia": "2026-08-11",
      "dataFimVigencia": "2026-12-31",
      "idPessoaRecebedora": "550e8400-e29b-41d4-a716-446655440003",
      "nomeRecebedor": null,
      "valor": 500.00,
      "status": "ATIVA",
      "motivoStatus": "RECEBIDA",
      "metadado": { "origem": "MOBILE" }
    }
  ],
  "paginaAtual": 0,
  "totalPaginas": 3,
  "totalElementos": 58,
  "tamanho": 20
}
```

`nomeRecebedor` é sempre `null` hoje — o DTO tem o campo mas nada o preenche ainda.

**422** — `idUnicoContaContratante` ausente, `pagina`/`tamanho` fora da faixa, `ordenarPor`
desconhecido: `LayoutErrosApiValidationsResponse` ou `LayoutErrosApiResponse`, mesma convenção do
command.

**500** — erro inesperado: `LayoutErrosApiResponse`.

### GET `/api/autorizacoes/{autorizacaoId}` — Consultar autorização por id

Retorna a representação detalhada de uma autorização. A partição é extraída do UUID (fora da
faixa 0-889 resulta em 404 sem hit no banco).

**Path param:** `autorizacaoId` (UUID, gerado por `ReversibleUUIDv7`)

**Resposta 200** (`AutorizacaoDetalheResponseDto`, shape real):
```json
{
  "idAutorizacao": "550e8400-e29b-41d4-a716-446655440000",
  "tipoProduto": "PIX_AUTO",
  "status": "ATIVA",
  "motivoStatus": "RECEBIDA",
  "dataInicioVigencia": "2026-08-11",
  "dataFimVigencia": "2026-12-31",
  "dataCriacao": "2026-08-11T14:32:00",
  "dataAtualizacao": "2026-08-11T14:32:00",
  "valor": 500.00,
  "valorLimite": 10000.00,
  "idUnicoContaContratante": "550e8400-e29b-41d4-a716-446655440000",
  "idPessoaPagadora": "550e8400-e29b-41d4-a716-446655440001",
  "idPessoaDevedora": "550e8400-e29b-41d4-a716-446655440002",
  "idPessoaRecebedora": "550e8400-e29b-41d4-a716-446655440003",
  "idAutorizacaoEmpresa": "EMP001",
  "descricao": "Autorização PIX automática para transferências",
  "metadado": { "origem": "MOBILE" }
}
```

**404** — autorização inexistente, ou UUID com partição embutida fora da faixa 0-889:
`LayoutErrosApiResponse`.

**500** — mesma autorização encontrada em mais de uma partição (corrupção), ou erro inesperado:
`LayoutErrosApiResponse`.

### Shape de erro do query

Idêntico ao do command — `LayoutErrosApiResponse` (`timestamp`/`error`/`message`/`path`) e
`LayoutErrosApiValidationsResponse` (o mesmo, mais `occurrences`). **Não** é o shape
`{"status": ..., "mensagem": ...}` que aparecia no README antigo — ver divergência 1 abaixo.

---

## Divergências encontradas entre os READMEs antigos e o código real

Verificadas em 2026-08-11 contra os DTOs de produção, antes de escrever este documento. Registradas
para que quem monte o gateway não herde exemplos errados.

1. **Shape de erro do query.** O README antigo mostrava `{"status": 422, "mensagem": "..."}`. O
   código usa `LayoutErrosApiResponse` (`timestamp`/`error`/`message`/`path`), igual ao command.
   Corrigido acima.

2. **Resposta 201 do command tinha `idAutorizacao` aninhado.** O README antigo mostrava
   `"idAutorizacao": {"idAutos": ..., "idParticaoConta": ...}`. O DTO real
   (`AutorizacaoCompletaResponseDto`) serializa `idAutorizacao` como **UUID simples** — a chave
   composta é interna (JPA `@EmbeddedId`), não vaza para a resposta. Corrigido acima.

3. **Resposta 201 do command estava incompleta.** O README antigo listava só 5 campos
   (`idAutorizacao`, `status`, `dataFimVigencia`, `tipoProduto`, `valor`); o DTO real tem 22
   campos. Corrigido acima com o shape completo.

4. **Corpo do cancelamento estava errado.** O README antigo mostrava
   `{"dataFimVigencia": ..., "motivoCancelamento": ...}`. O record real
   (`CancelarAutorizacaoRequest`) não tem `dataFimVigencia` e **exige**
   `codigoCanalCancelamento` e `idPessoaCancelamento`, ausentes do exemplo antigo. Corrigido acima.

5. **Resposta de listagem do query estava incompleta e com um campo inexistente (em 2026-08-11).**
   Na época, o README antigo incluía `tipoProduto` (que não existia em
   `AutorizacaoResumidaResponseDto`) e omitia `dataCriacao`, `idPessoaRecebedora`, `nomeRecebedor`
   e `motivoStatus`, que existiam. **Atualização:** a change `completar-shape-listagem-autorizacoes`
   acrescentou `tipoProduto` de volta ao DTO real (necessidade concreta de exibir o produto na
   listagem sem consultar o detalhe item a item) — o exemplo acima já reflete o shape atual, com
   `tipoProduto` presente de fato, não mais um campo fantasma do README antigo.

6. **Resposta de consulta por id do query estava incompleta.** O README antigo listava 7 campos;
   o DTO real (`AutorizacaoDetalheResponseDto`) tem 17. Também usava a chave `dataHoraInclusao`,
   que no DTO se chama `dataCriacao`. Corrigido acima.

7. **Não existe 404 em `cancelar`/`decidir` do command — nem a classe existe.** As anotações
   springdoc dos dois endpoints documentavam `@ApiResponse(responseCode = "404", description =
   "Autorizacao inexistente", ...)`, e o `CLAUDE.md`/`AGENTS.md` de `contratocommand`
   listavam `404 | ResourceNotFoundException | Autorização inexistente` na tabela de códigos de
   erro. **`ResourceNotFoundException` não existe em nenhum lugar do código** — nem em
   `src/main`, nem em `src/test` (`grep -rl ResourceNotFoundException` no app inteiro não
   encontra nada). O comportamento real, verificado em
   `CancelarAutorizacaoUseCase.obterAutorizacaoPorIdEParticao` e
   `DecidirAutorizacaoUseCase.obterAutorizacaoPorIdEParticao`: autorização inexistente lança
   `BusinessException("Autorização não encontrada com ID: ...")`, mapeada pelo
   `ApiExceptionHandler` para **422**, mesmo shape (`LayoutErrosApiResponse`) de qualquer outra
   violação de regra. Já coberto por teste — `CancelarAutorizacaoUseCaseTest` e
   `DecidirAutorizacaoUseCaseTest` têm o caso "lança BusinessException quando a autorização não é
   encontrada", e `ApiExceptionHandlerTest` cobre `BusinessException → 422`. Este documento reflete
   o comportamento real (422); a documentação de origem (anotação e `CLAUDE.md`) estava errada e
   precisa de correção — ver tarefa 6.3 de `limpar-codigo-das-apps/tasks.md`.

Estas sete divergências não foram corrigidas nos READMEs de origem — eles estavam sendo removidos
na mesma mudança (`enxugar-documentacao-repo`) que motivou preservar este contrato em outro lugar.
A sétima é mais grave que as demais: não é um exemplo desatualizado, é um status HTTP e uma classe
de exceção que nunca existiram no código, documentados como se existissem.
