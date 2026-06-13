# consultar-autorizacao-por-id

## Purpose

Definir a consulta de uma autorização individual por id no `contratoquery` via `GET /api/autorizacoes/{autorizacaoId}`, incluindo a derivação da partição a partir do próprio UUID e o comportamento de não encontrado (404) e id inválido.

## Requirements

### Requirement: Consultar autorização individual por id
O `contratoquery` SHALL expor o endpoint `GET /api/autorizacoes/{autorizacaoId}` que retorna os dados completos de uma única autorização identificada pelo seu UUID. O sistema SHALL derivar a partição a partir do próprio `autorizacaoId` (via `ReversibleUUIDv7.extract`) para localizar o registro pela chave composta.

#### Scenario: Autorização existente é retornada
- **WHEN** o cliente envia `GET /api/autorizacoes/{autorizacaoId}` com um id existente
- **THEN** o sistema retorna HTTP 200 com o corpo `AutorizacaoDetalheResponseDto` da autorização correspondente

#### Scenario: Autorização inexistente resulta em 404
- **WHEN** o cliente envia `GET /api/autorizacoes/{autorizacaoId}` com um UUID válido que não corresponde a nenhuma autorização
- **THEN** o sistema retorna HTTP 404 com `LayoutErrosApiResponse` indicando que o recurso não foi encontrado

#### Scenario: Id com partição fora da faixa válida resulta em 404
- **WHEN** o cliente envia um UUID sintaticamente válido cuja partição extraída está fora da faixa `900–999`
- **THEN** o sistema retorna HTTP 404 (e NÃO HTTP 500), pois o id não pode corresponder a nenhuma autorização persistida

#### Scenario: Id sintaticamente inválido resulta em 400
- **WHEN** o cliente envia um `autorizacaoId` que não é um UUID válido
- **THEN** o sistema retorna HTTP 400 (falha de conversão do path variable), sem atingir a camada de aplicação

### Requirement: Estrutura do DTO de detalhe da autorização
O `AutorizacaoDetalheResponseDto` SHALL conter a representação completa da autorização, incluindo no mínimo: `idAutorizacao`, `tipoProduto`, `status` (nome do enum, não o código inteiro), `dataInicioVigencia`, `dataFimVigencia`, `dataCriacao`, `valor`, `valorLimite`, `idUnicoContaContratante`, `idPessoaRecebedora` e `metadado`.

#### Scenario: Status é retornado como nome do enum
- **WHEN** a autorização consultada tem `status = 1`
- **THEN** o campo `status` no DTO retornado é a string correspondente ao nome do enum `StatusAutorizacao` (ex.: `"ATIVA"`)

#### Scenario: Metadado JSONB é retornado como objeto JSON
- **WHEN** a autorização possui `metadados` armazenados como JSONB
- **THEN** o campo `metadado` é retornado como objeto JSON estruturado (não como string escapada)
