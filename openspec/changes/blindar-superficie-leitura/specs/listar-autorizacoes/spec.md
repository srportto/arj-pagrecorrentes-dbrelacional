## MODIFIED Requirements

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
