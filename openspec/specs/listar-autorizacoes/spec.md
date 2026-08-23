# listar-autorizacoes

## Purpose

Definir a listagem paginada de autorizações resumidas por conta contratante no `contratoquery` via `GET /api/autorizacoes`, com filtro por status, paginação e ordenação, e a estrutura do DTO de resposta. A listagem pertence exclusivamente ao `contratoquery` (lado de leitura do CQRS).

> **Nota (2026-08-09):** esta spec é o que o `contratoquery` expõe. O `contratocommand` representa a mesma autorização com nomes de campo e formato de `status` diferentes (`status` como `Integer`, nomes longos como `valorAutorizacao` / `dataHoraInclusao` / `dataHoraUltimaAtualizacao`). Não há migração planejada — ver change `reconciliar-contrato-spec-doc` D1/D2.
## Requirements
### Requirement: Listar autorizações paginadas por conta contratante
O `contratoquery` SHALL expor o endpoint `GET /api/autorizacoes` que retorna uma página de autorizações resumidas de uma conta contratante, com suporte a filtro por status, paginação e ordenação configuráveis.

A validação do parâmetro `idUnicoContaContratante` SHALL ocorrer na camada de aplicação, não no binding do framework. O parâmetro SHALL ser declarado como opcional no controller, de modo que sua ausência alcance a validação de negócio e produza resposta no formato `LayoutErrosApiResponse` — e não o erro genérico do framework.

Os parâmetros de paginação e ordenação SHALL respeitar os limites definidos na capacidade `limites-consulta-autorizacoes`: teto máximo de `tamanho`, rejeição de `pagina` negativa e de `tamanho` não positivo, e whitelist fechada de campos de `ordenarPor`.

#### Scenario: Listagem sem filtro de status retorna todas as autorizações da conta
- **WHEN** o cliente envia `GET /api/autorizacoes?idUnicoContaContratante={uuid}`
- **THEN** o sistema retorna HTTP 200 com `PaginacaoResponseDto` contendo todas as autorizações da conta, ordenadas por `dataHoraInclusao` DESC, página 0, tamanho 20

#### Scenario: Listagem com filtro de status retorna apenas as autorizações filtradas
- **WHEN** o cliente envia `GET /api/autorizacoes?idUnicoContaContratante={uuid}&status=ATIVA&status=RECEBIDA`
- **THEN** o sistema retorna HTTP 200 apenas com autorizações cujo status corresponda a `ATIVA` ou `RECEBIDA`

#### Scenario: idUnicoContaContratante ausente resulta em erro de negócio
- **WHEN** o cliente omite o parâmetro `idUnicoContaContratante`
- **THEN** o sistema retorna HTTP 422 com mensagem indicando que o campo é obrigatório
- **AND** o corpo da resposta segue o formato `LayoutErrosApiResponse`

#### Scenario: Validação de conta contratante é alcançável
- **WHEN** o controller e o service de listagem são inspecionados
- **THEN** o parâmetro `idUnicoContaContratante` NÃO SHALL ser declarado como obrigatório no binding
- **AND** a verificação de nulidade no service SHALL ser alcançável em execução, não código morto

#### Scenario: Status inválido resulta em erro de negócio
- **WHEN** o cliente passa `status=STATUS_DESCONHECIDO`
- **THEN** o sistema retorna HTTP 422 listando os valores aceitos de `StatusAutorizacao`

#### Scenario: Paginação customizada é respeitada
- **WHEN** o cliente envia `pagina=2&tamanho=5`
- **THEN** o sistema retorna a terceira página com no máximo 5 itens e metadados corretos (`paginaAtual=2`, `tamanho=5`, `totalPaginas`, `totalElementos`)

#### Scenario: Ordenação customizada é aplicada
- **WHEN** o cliente envia `ordenarPor=valor,asc`
- **THEN** os itens retornados estão ordenados pelo campo `valorAutorizacao` em ordem ascendente

#### Scenario: Conta sem autorizações retorna lista vazia sem erro
- **WHEN** o `idUnicoContaContratante` não possui nenhuma autorização cadastrada
- **THEN** o sistema retorna HTTP 200 com `conteudo=[]` e `totalElementos=0`

### Requirement: Estrutura do DTO de resposta de listagem
Cada item da listagem SHALL conter os campos resumidos de uma autorização: `idAutorizacao`, `tipoProduto`, `dataCriacao`, `dataInicioVigencia`, `dataFimVigencia`, `idPessoaRecebedora`, `nomeRecebedor`, `valor`, `status` (nome do enum, não o código inteiro), `motivoStatus` e `metadado`.

#### Scenario: Status é retornado como nome do enum
- **WHEN** a autorização tem `status = 4` (código do `ATIVA`)
- **THEN** o campo `status` no DTO retornado é a string `"ATIVA"`

#### Scenario: Campo nomeRecebedor está presente mas pode ser nulo
- **WHEN** a autorização é retornada na listagem
- **THEN** o campo `nomeRecebedor` está presente na resposta (podendo ser `null` até integração posterior)

#### Scenario: Campo tipoProduto identifica o produto do item da listagem
- **WHEN** a autorização listada é do produto `PIX_AUTO`
- **THEN** o campo `tipoProduto` no item da listagem é `"PIX_AUTO"`
- **AND** o mesmo vale para `DDA_AUTO`, sem exigir uma chamada adicional a `GET /api/autorizacoes/{autorizacaoId}` para descobrir o produto

#### Scenario: Campo motivoStatus está presente na listagem
- **WHEN** a autorização listada tem um `motivoStatus` registrado (ex.: `RECEPCAO_SPI_J1`)
- **THEN** o campo `motivoStatus` no item da listagem reflete o mesmo valor exposto em `GET /api/autorizacoes/{autorizacaoId}`

### Requirement: Listagem de autorizações pertence ao contratoquery
O endpoint de listagem `GET /api/autorizacoes` SHALL existir apenas no `contratoquery`; o `contratocommand` SHALL expor apenas `POST /api/autorizacoes` e `PATCH /api/autorizacoes/{idAutorizacao}/cancelar`.

#### Scenario: contratocommand não oferece a listagem GET
- **WHEN** o cliente envia `GET /api/autorizacoes` para o `contratocommand`
- **THEN** o sistema não processa a listagem (retorna erro de método/rota não suportado, ex.: HTTP 405), pois o `contratocommand` não expõe o handler GET

#### Scenario: contratocommand continua respondendo às rotas de escrita
- **WHEN** o cliente envia `POST /api/autorizacoes` ou `PATCH /api/autorizacoes/{id}/cancelar` para o `contratocommand`
- **THEN** o sistema processa normalmente sem alteração de comportamento

