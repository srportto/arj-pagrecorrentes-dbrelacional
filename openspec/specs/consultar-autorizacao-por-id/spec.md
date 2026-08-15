# consultar-autorizacao-por-id

## Purpose

Definir a consulta de uma autorização individual por id no `contratoquery` via `GET /api/autorizacoes/{autorizacaoId}`, incluindo a cascata de localização entre partições quentes e faixa de expurgo, e o comportamento de não encontrado (404) e id inválido.

## Requirements

### Requirement: Consultar autorização individual por id

O `contratoquery` SHALL expor o endpoint `GET /api/autorizacoes/{autorizacaoId}` que retorna os
dados completos de uma única autorização identificada pelo seu UUID.

A localização SHALL seguir uma cascata de até três níveis, cujos conjuntos de partições são
disjuntos entre si e, juntos, cobrem a tabela inteira:

1. **Partição derivada do id** (`ReversibleUUIDv7.extract`) — onde a autorização foi criada.
   Atende à autorização ativa, o caso dominante.
2. **Faixa de expurgo** (`id_particao_conta >= 900`) — para onde toda autorização em estado
   terminal é transferida. A busca por faixa, e não por partição exata, é necessária porque a
   partição de expurgo deriva da **data da transição terminal**, que não é recuperável a partir
   do id.
3. **Demais partições quentes** (`id_particao_conta < 900` e diferente da derivada do id) —
   rede de segurança para linha que não respeite o invariante "ou está na partição do seu id,
   ou está na faixa de expurgo".

O sistema SHALL parar no primeiro nível que encontrar a autorização. O nível 3 SHALL ser
habilitável por configuração.

O 404 SHALL significar "não existe em partição alguma", e não "não existe na partição derivada
do id".

#### Scenario: Autorização ativa é encontrada no primeiro nível

- **WHEN** o cliente consulta uma autorização que reside na partição derivada do seu id
- **THEN** o sistema SHALL retornar HTTP 200 com `AutorizacaoDetalheResponseDto`
- **AND** SHALL consultar apenas a partição derivada do id, sem acionar os níveis seguintes

#### Scenario: Autorização em estado terminal é encontrada no segundo nível

- **WHEN** o cliente consulta uma autorização que já foi transferida para a partição de expurgo
  (status `CANCELADA`, `REJEITADA`, `EXPIRADA` ou `FINALIZADA`)
- **THEN** o sistema SHALL retornar HTTP 200 com os dados da autorização
- **AND** a resposta SHALL ser idêntica à que seria devolvida antes da transferência de
  partição, exceto pelos campos que a própria transição alterou

#### Scenario: Autorização fora dos dois lugares esperados é encontrada e sinalizada

- **WHEN** a autorização é localizada apenas no terceiro nível, isto é, numa partição quente
  diferente da derivada do seu id
- **THEN** o sistema SHALL retornar HTTP 200 com os dados da autorização
- **AND** SHALL registrar log de alerta identificando o id e a partição onde foi encontrada,
  por indicar violação do invariante de localização

#### Scenario: Autorização inexistente resulta em 404 após a cascata completa

- **WHEN** o cliente consulta um UUID válido que não corresponde a nenhuma autorização
- **THEN** o sistema SHALL retornar HTTP 404 com `LayoutErrosApiResponse`
- **AND** SHALL ter esgotado os níveis habilitados antes de concluir

#### Scenario: Terceiro nível desabilitado encerra a cascata mais cedo

- **WHEN** o terceiro nível está desabilitado por configuração e a autorização não é encontrada
  nos dois primeiros
- **THEN** o sistema SHALL retornar HTTP 404 sem consultar as demais partições quentes

#### Scenario: Id com partição fora da faixa válida resulta em 404 sem tocar no banco

- **WHEN** o cliente envia um UUID sintaticamente válido cuja partição extraída está fora da
  faixa de partições quentes (`0–889`)
- **THEN** o sistema SHALL retornar HTTP 404 (e NÃO HTTP 500), sem executar nenhum nível da
  cascata, pois nenhum id gerado pelo sistema pode ter partição embutida fora dessa faixa

#### Scenario: Id sintaticamente inválido resulta em 500 (defeito conhecido, não corrigido nesta spec)
- **WHEN** o cliente envia um `autorizacaoId` que não é um UUID válido
- **THEN** o sistema retorna HTTP **500**, não 400 — verificado por teste (`@WebMvcTest`) em
  2026-08-11: a falha de conversão do path variable (`MethodArgumentTypeMismatchException`) não
  tem handler dedicado em `ApiExceptionHandler`, e o catch-all `@ExceptionHandler(Exception.class)`
  a intercepta antes do tratamento default de binding do Spring, reportando "erro inesperado" para
  o que é, na verdade, entrada malformada do cliente. Esta spec documenta o comportamento real,
  não o desejado — corrigir o handler é mudança de comportamento, ainda não realizada.

### Requirement: Mesma autorização em mais de uma partição é tratada como erro

Qualquer nível da cascata que localize mais de uma linha para o mesmo `id_autorizacao` SHALL
tratar o resultado como erro de aplicação. O sistema NÃO SHALL escolher uma das linhas nem
devolver 200.

A mesma autorização em duas partições significa corrupção — provável resíduo de uma
transferência de partição interrompida. É condição que exige investigação, não desempate
automático.

#### Scenario: Duplicidade entre partições não é resolvida silenciosamente

- **WHEN** a busca localiza duas ou mais linhas com o mesmo `id_autorizacao` em partições
  distintas
- **THEN** o sistema SHALL responder HTTP 500 com `LayoutErrosApiResponse` genérico
- **AND** SHALL registrar em log o id e todas as partições envolvidas
- **AND** a resposta ao cliente NÃO SHALL expor nome de tabela, partição ou classe de exceção

### Requirement: Custo da cascata é previsível e configurável

O terceiro nível da cascata SHALL ser controlável por configuração, de modo que o custo do
caminho de "não encontrado" possa ser reduzido sem alteração de código.

A motivação é operacional: o pior caso da cascata não é a autorização expurgada, é a
**inexistente** — percorre todos os níveis habilitados antes do 404. Como o custo dominante é
planejamento de consulta sobre centenas de partições (CPU, não I/O), requisições com ids
inexistentes são um vetor barato de consumo de CPU do banco.

#### Scenario: Configuração é honrada sem reinício de contrato

- **WHEN** o terceiro nível é desabilitado
- **THEN** os dois primeiros níveis SHALL continuar funcionando exatamente como antes
- **AND** o contrato do endpoint (200/404/400) SHALL permanecer inalterado

### Requirement: Estrutura do DTO de detalhe da autorização
O `AutorizacaoDetalheResponseDto` SHALL conter a representação completa da autorização, incluindo no mínimo: `idAutorizacao`, `tipoProduto`, `status` (nome do enum, não o código inteiro), `dataInicioVigencia`, `dataFimVigencia`, `dataCriacao`, `valor`, `valorLimite`, `idUnicoContaContratante`, `idPessoaRecebedora` e `metadado`.

#### Scenario: Status é retornado como nome do enum
- **WHEN** a autorização consultada tem `status = 1`
- **THEN** o campo `status` no DTO retornado é a string correspondente ao nome do enum `StatusAutorizacao` (ex.: `"ATIVA"`)

#### Scenario: Metadado JSONB é retornado como objeto JSON
- **WHEN** a autorização possui `metadados` armazenados como JSONB
- **THEN** o campo `metadado` é retornado como objeto JSON estruturado (não como string escapada)
