## Context

O parâmetro `ordenarPor` da listagem chega como `String` no formato `"campo,direcao"` e é
quebrado à mão dentro de `ListarAutorizacoesService.listar`:

```java
String[] partes = ordenarPor.split(",");
if (partes.length >= 1) campoOrdenacao = mapearCampoOrdenacao(partes[0].trim());
if (partes.length >= 2) ascendente = "asc".equalsIgnoreCase(partes[1].trim());
```

O campo é validado contra uma whitelist fechada (`mapearCampoOrdenacao` lança
`BusinessException` no `default` do switch). A direção não é validada por nada: o
`equalsIgnoreCase("asc")` transforma **todo** valor não reconhecido em `false`, que o adaptador
traduz para `Sort.Direction.DESC`. O resultado é a falha mais cara possível numa API de leitura —
resposta bem-sucedida com os dados na ordem oposta à pedida.

É um caso de livro de *Primitive Obsession*: uma `String` carregando duas semânticas distintas,
com parsing repetido e validação parcial. A capacidade `limites-consulta-autorizacoes` já
estabeleceu a postura correta para a metade do campo; falta aplicar a mesma postura à direção.

Restrições herdadas que esta change **não** questiona:

- A validação de listagem vive na camada de aplicação e responde `LayoutErrosApiResponse` com
  422 — exigido explicitamente por `listar-autorizacoes` e `limites-consulta-autorizacoes`.
- A porta `AutorizacaoRepository` não conhece JPA: filtro e ordenação trafegam como vocabulário
  de domínio, e a tradução para caminho de propriedade fica no `AutorizacaoJpaAdapter` (D3/D4 da
  porta).

## Goals / Non-Goals

**Goals:**

- Nenhuma direção de ordenação inválida produz HTTP 200.
- O parse de `ordenarPor` existe em exatamente um lugar, no domínio, testável sem Spring.
- A assinatura da porta de saída perde um parâmetro primitivo em vez de ganhar mais um.
- Comportamento válido de hoje (`valor,asc`, `valor`, `ordenarPor` omitido) permanece idêntico.

**Non-Goals:**

- Mudar o shape da resposta de erro para `LayoutErrosApiValidationsResponse`/`occurrences`.
- Mover validação de paginação para a borda / anotações Bean Validation.
- Introduzir `CriteriosListagem` (Introduce Parameter Object nos 5 parâmetros da listagem).
- Remover `TipoEventoAutorizacao` ou `StatusAutorizacao.podeTransicionarPara` do `contratoquery`.
- Qualquer alteração em `contratocommand` ou nos demais serviços.

## Decisions

### D1 — O parse mora num value object de domínio, não no controller nem em util de infra

`Ordenacao` nasce em `domain/model/`, com fábrica estática `Ordenacao.de(String expressao)` que
valida a expressão inteira e devolve o objeto pronto, ou lança `BusinessException`.

**Alternativas consideradas:**

| Opção | Por que não |
|---|---|
| `Converter<String, Ordenacao>` do Spring no controller | O erro viraria `MethodArgumentTypeMismatchException` e cairia em `LayoutErrosApiValidationsResponse`, mudando o shape que as specs vigentes fixam. Também amarraria o VO ao framework. |
| Classe utilitária em `infrastructure/web` | O vocabulário de ordenação é domínio (`CampoOrdenacao` já vive em `domain/enums`); colocar a regra na borda espalharia a decisão em duas camadas. |
| Manter no service, só adicionando um `throw` na direção | Resolve o bug, mas deixa o método de 45 linhas intacto e a `String` continua sendo o tipo que trafega. Uma terceira ocorrência de parse reabriria o problema. |

A whitelist de aliases (`dataCriacao`/`dataHoraInclusao` para `DATA_CRIACAO`, etc.), hoje em
`mapearCampoOrdenacao`, migra junto para o VO — campo e direção passam a ser validados no mesmo
lugar. Nada de JPA atravessa: o VO só conhece `CampoOrdenacao` e `DirecaoOrdenacao`.

### D2 — `DirecaoOrdenacao` como enum, substituindo o `boolean ascendente`

O `boolean` que hoje atravessa caso de uso, porta e adaptador não tem terceiro estado onde
representar "direção inválida", e obriga cada camada a lembrar que `false` significa `DESC`.
Um enum de dois valores custa 8 linhas, torna `rg "DirecaoOrdenacao.ASC"` efetivo e permite ao
adaptador mapear com `switch` exaustivo em vez de ternário sobre booleano.

**Alternativa:** reusar `org.springframework.data.domain.Sort.Direction`. Rejeitada — importaria
Spring Data no domínio, exatamente o que a porta foi desenhada para evitar.

### D3 — A porta recebe `Ordenacao`, não o par `(CampoOrdenacao, boolean)`

```
antes:  listarPorConta(idConta, statuses, pagina, tamanho, campoOrdenacao, ordenacaoAscendente)
depois: listarPorConta(idConta, statuses, pagina, tamanho, ordenacao)
```

Os dois parâmetros sempre viajam juntos e nunca fazem sentido isolados. Deixá-los separados
significaria criar o value object e desmontá-lo na linha seguinte.

**Trade-off aceito:** muda a assinatura da porta, tocando `AutorizacaoJpaAdapter`,
`AutorizacaoJpaAdapterTest` e `ListarPorContaIntegrationTest`. É refactoring mecânico, sem
mudança de comportamento nesses pontos.

### D4 — Direção omitida usa padrão; direção vazia é erro

| Entrada | Decisão | Racional |
|---|---|---|
| `valor` | `VALOR` + `DESC` | Omissão total é forma válida documentada; preserva comportamento atual. |
| `valor,` | **422** | A vírgula sinaliza intenção de especificar direção; aceitar como omissão reintroduz a falha silenciosa em outra roupagem. |
| `,asc` | **422** | Campo vazio não está na whitelist. |
| `valor,asc,extra` | **422** | Ignorar excedente esconde erro do cliente. |
| ausente / em branco | padrão da listagem | Caminho válido, distinto de expressão malformada. |

A distinção que guia a tabela: **omitir** é escolha válida; **informar errado** nunca é.

### D5 — O erro continua `BusinessException` mapeada para 422 `LayoutErrosApiResponse`

Direção inválida é tratada com o mesmo mecanismo que campo inválido já usa hoje. Isso mantém a
change em conformidade com `limites-consulta-autorizacoes` ("erro de contrato no formato
`LayoutErrosApiResponse`") e evita abrir a discussão de shape de erro dentro de uma correção de
bug. O `ApiExceptionHandler` não é tocado.

### D6 — Endurecimento da borda de entrada fica fora do escopo (decidido em 2026-09-01)

A análise inicial propôs tornar `idUnicoContaContratante` obrigatório no `@RequestParam` e mover
validação de faixas para Bean Validation, produzindo `LayoutErrosApiValidationsResponse` com
`occurrences`. A spec `listar-autorizacoes` exige textualmente o oposto:

> *"O parâmetro SHALL ser declarado como opcional no controller, de modo que sua ausência alcance
> a validação de negócio e produza resposta no formato `LayoutErrosApiResponse`"*

Há inclusive um cenário de teste (`Validação de conta contratante é alcançável`) que trava essa
escolha. Mudar isso é decisão de contrato de API, com quebra de shape para clientes existentes, e
merece change própria — possivelmente coordenada com `endurecer-borda-entrada-contratocommand`,
que trata do mesmo tema no lado de escrita.

### D7 — Remoção de código de espelhamento fica fora do escopo (decidido em 2026-09-01)

`TipoEventoAutorizacao` e `StatusAutorizacao.podeTransicionarPara` não têm chamador de produção
no `contratoquery`, mas **não** são código morto acidental: `maquina-estados-autorizacao` exige
que as quatro aplicações do monorepo carreguem ambos, com o mesmo grafo de transições, como
invariante de espelhamento manual. `higiene-codigo-morto` reforça a mesma postura ao preservar
deliberadamente o par `ReversibleUUIDv7.generate`/`extract`.

Reduzir essa superfície exigiria rever a estratégia de espelhamento do monorepo — assunto maior
que esta change e sem relação com o bug que ela corrige.

## Risks / Trade-offs

- **Cliente que hoje envia direção malformada e recebe 200 passa a receber 422** → É o objetivo
  declarado da change, não efeito colateral. Mitigação: acompanhar a taxa de 422 na listagem logo
  após o deploy; um pico revela integrações que dependiam do comportamento silencioso e precisam
  corrigir a query string. Como o endpoint é somente leitura, o pior caso é o cliente ver erro em
  vez de dado na ordem errada — degradação preferível à atual.

- **Mudança de assinatura da porta espalha diff por 3 arquivos de teste** → Refactoring mecânico,
  validado pela suíte existente. Se algum teste passar a não compilar, é sinal de que estava
  acoplado à representação primitiva — o que a change quer justamente eliminar.

- **A whitelist de aliases sai do service e vai para o VO** → Risco de a mensagem de erro de campo
  inválido mudar de texto e quebrar teste que asserte a string exata. Mitigação: preservar a lista
  de campos aceitos na mensagem, conforme `limites-consulta-autorizacoes` exige.

- **Um value object a mais no domínio** → Custo real, mas justificado pela regra das 3: campo,
  direção e formato da expressão são três validações que hoje moram em lugares diferentes (switch,
  ternário, e nenhum lugar). YAGNI não se aplica quando a abstração remove um bug em produção.

## Migration Plan

Não há migração de dados nem de schema. Deploy é substituição de artefato, e rollback é o deploy
anterior — o comportamento antigo volta inteiro, já que nada é persistido.

Ordem sugerida: criar `DirecaoOrdenacao` e `Ordenacao` com seus testes verdes **antes** de tocar
no `ListarAutorizacoesService`, para que a suíte nunca fique vermelha por mais de um passo.

## Open Questions

- Vale emitir uma métrica dedicada (contador de ordenação rejeitada) para dimensionar quantos
  clientes dependiam do `DESC` silencioso? Fora do escopo desta change, mas é a informação que
  responderia se a quebra teve impacto real.
