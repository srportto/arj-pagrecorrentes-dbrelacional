## Why

O código de produção das duas apps REST carrega a documentação do contrato de API embutida em
anotações springdoc/OpenAPI. São **69 anotações** (`@ApiResponse`×22, `@Schema`×21,
`@Parameter`×12, `@Operation`×6, `@Tag`×3) concentradas nos dois `AutorizacaoController`, que
respondem por boa parte do volume desses arquivos: o controller do command tem 177 linhas para
2 endpoints, o do query 110 linhas para 2 endpoints.

Essa superfície vai passar a viver no **gateway**. Enquanto ela permanece no código:

- Todo ajuste de descrição de erro, exemplo de parâmetro ou nome de tag exige mexer em classe de
  produção, recompilar e reimplantar a aplicação — para mudar texto.
- A app arrasta `springdoc-openapi-starter-webmvc-ui` e a UI do Swagger para dentro do artefato
  de produção, expondo `/swagger-ui` e `/v3/api-docs` em runtime.
- O contrato passa a ter duas fontes concorrentes (anotação no código e definição no gateway),
  e nada garante que convirjam.

Sobra ainda um resíduo pequeno de higiene de código nas cinco apps, medido e não endereçado:
dois `import` sem uso, e nenhuma varredura de parâmetro sem uso já executada.

## What Changes

- Remover do código de produção toda anotação de documentação de API (`io.swagger.v3.oas.*`),
  a dependência `springdoc-openapi-starter-webmvc-ui` e a propriedade `springdoc.version` dos
  dois `pom.xml`.
- Remover `OpenApiGenerationTest` das duas apps: ele existe apenas para materializar o
  `/v3/api-docs` do springdoc e perde objeto com a remoção.
- **Preservar** o `@ExceptionHandler(NoResourceFoundException.class)` dos dois
  `ApiExceptionHandler`. O comportamento (404 nativo para caminho desconhecido, em vez de 500 do
  catch-all) é correto por si — só a **justificativa no javadoc**, que hoje cita o
  `/v3/api-docs` do springdoc, precisa ser reescrita.
- Remover os dois `import` sem uso identificados.
- Varrer parâmetros sem uso nas cinco apps com `-Xlint:all` e remover os confirmados.
- Marcar com `// TODO` apenas os trechos com **custo concreto já identificado**, não todo cheiro
  de código.

## O contrato ainda não existe no gateway — e o que fazemos a respeito

Esta mudança **não gera nem versiona o `openapi.json` antes de remover** — decisão explícita de
2026-08-10. E, confirmado na mesma data, **o gateway ainda não tem o contrato**: ele será montado
depois.

Isso não é premissa, é lacuna conhecida. Não há `openapi.json` em lugar nenhum
(`find . -name "openapi*.json"` retorna vazio), e o contrato está espalhado em **três** fontes
que duas changes diferentes apagariam:

| # | Fonte | Quem apaga | O que só ela tem |
|---|---|---|---|
| 1 | 69 anotações springdoc nos 2 controllers | **esta change** | `description`, `example`, o `oneOf` do 422 do query |
| 2 | `apps/contratocommand/README.md` 268-380 | `enxugar-documentacao-repo` | corpo de request POST e PATCH, com valores reais |
| 3 | `apps/contratoquery/README.md` 181-264 | `enxugar-documentacao-repo` | exemplos de resposta do GET listagem e GET por id |

**Mitigação adotada:** antes de qualquer remoção, o conteúdo das **três** fontes é consolidado em
`docs/contrato-api-para-gateway.md` — um rascunho legível, organizado por endpoint, que serve de
insumo para montar o gateway. Não é `openapi.json` válido e não pretende ser: é o conteúdo, salvo
de forma que não dependa de reconstituir código apagado. É a fase 1 desta change, e ela cobre
também as fontes 2 e 3, que pertencem à outra change.

**Consequência de ordem: `enxugar-documentacao-repo` não pode começar antes da fase 1 desta
change.** Se ela cortar os READMEs primeiro, as fontes 2 e 3 somem sem passar pela preservação.
A dependência está declarada nas duas changes.

O que sobra no repositório depois disso são as tabelas de endpoints e de códigos de erro nos
`CLAUDE.md`/`AGENTS.md` — que a capacidade `documentacao-fiel-ao-codigo` já obriga a manter
fiéis, e que a change `enxugar-documentacao-repo` preserva.

## Capabilities

### New Capabilities

- `doc-api-fora-do-codigo`: regra de que código de aplicação não carrega documentação de contrato
  de API — nem anotação, nem dependência de geração, nem endpoint de UI —, porque essa superfície
  pertence ao gateway.

### Modified Capabilities

- `higiene-codigo-morto`: hoje escopada a `contratocommand` e `contratoquery`. Ganha
  requisito de ausência de `import` e de parâmetro sem uso, válido para as **cinco** apps.

## Impact

**Código**
- `apps/contratocommand/src/main/java/.../entrypoint/AutorizacaoController.java` (177 linhas,
  41 ocorrências de anotação de doc)
- `apps/contratoquery/src/main/java/.../entrypoint/AutorizacaoController.java` (110 linhas)
- Os dois `shared/interceptors/api/ApiExceptionHandler.java` — só o javadoc
- `apps/contratocommand/pom.xml` (linhas ~35 e ~123-130)
- `apps/contratoquery/pom.xml` (linhas ~21 e ~86-93)
- Remoção: `entrypoint/OpenApiGenerationTest.java` nas duas apps
- `apps/autorizacaostatus-producer/src/test/.../ProcessarEventoAutorizacaoUseCaseTest.java:4`
- `apps/temporiza-autorizacao/src/test/.../VarreduraEAgendamentoIntegrationTest.java:19`

**Runtime**
- `/swagger-ui/**` e `/v3/api-docs` deixam de existir nas duas apps. Quem depender desses
  caminhos em ambiente local passa a não ter substituto no repositório.

**Fora de escopo**
- Configurar o gateway. Esta mudança só retira do código; o destino é problema de outro repo.
- Enxugar `README.md`/`CLAUDE.md` — é a change `enxugar-documentacao-repo`.
- Refatorar de fato os trechos marcados com `// TODO`. Marcação não é execução.
- A contradição entre a spec `contrato-api-consistente` (que exige 400 para `@Valid`) e a decisão
  D3 do `CLAUDE.md` raiz (que fixou 422). É defeito de documentação, tratado em
  `enxugar-documentacao-repo`.
