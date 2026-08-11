# Spec: validacao-header-jornada

## Purpose

Define os requisitos de validação do header HTTP `tipoJornada` no endpoint de contratação `POST /api/autorizacoes`, incluindo a conversão consistente do valor recebido para o enum interno correspondente.

## Requirements

### Requirement: Header tipoJornada obrigatório no endpoint de contratação
O endpoint `POST /api/autorizacoes` SHALL exigir o header HTTP `tipoJornada` com um valor correspondente a um dos nomes do enum `TipoJornadaAutorizacao` (`SPI_J1`, `QRC_J2`, `QRC_J3`, `QRC_J4`). Requisições com header ausente ou com valor não reconhecido MUST ser rejeitadas antes de qualquer processamento de negócio.

#### Scenario: Header válido permite prosseguir
- **WHEN** o cliente envia `POST /api/autorizacoes` com header `tipoJornada: SPI_J1` e body válido
- **THEN** o sistema processa a contratação normalmente e retorna HTTP 201

#### Scenario: Header com valor de jornada inválido é rejeitado
- **WHEN** o cliente envia `POST /api/autorizacoes` com header `tipoJornada: JORNADA_INVALIDA`
- **THEN** o sistema retorna HTTP 422 com `BusinessException` indicando que a jornada não é conhecida

#### Scenario: Header ausente é rejeitado com 500 (defeito conhecido, não corrigido nesta spec)
- **WHEN** o cliente envia `POST /api/autorizacoes` sem o header `tipoJornada` (com corpo válido)
- **THEN** o sistema retorna HTTP **500**, não 400 — verificado por teste (`@WebMvcTest`) em
  2026-08-11: a ausência do header obrigatório (`MissingRequestHeaderException`) não tem handler
  dedicado em `ApiExceptionHandler`, e o catch-all `@ExceptionHandler(Exception.class)` a
  intercepta antes do tratamento default de binding do Spring, reportando "erro inesperado" para
  o que é, na verdade, entrada malformada do cliente. Esta spec documenta o comportamento real,
  não o desejado — corrigir o handler é mudança de comportamento, fora do escopo de
  `enxugar-documentacao-repo`.

#### Scenario: Todos os 4 valores válidos são aceitos
- **WHEN** o cliente envia `POST /api/autorizacoes` com header `tipoJornada` igual a `SPI_J1`, `QRC_J2`, `QRC_J3` ou `QRC_J4`
- **THEN** o sistema aceita cada um desses valores e prossegue com a contratação

### Requirement: Conversão consistente de nome de enum para tipoJornada
O enum `TipoJornadaAutorizacao` SHALL expor o método `obterJornadaAutorizacaoEnumPorNome(String nome)` que realiza busca case-insensitive e lança `BusinessException` (não `IllegalArgumentException`) quando o nome não é reconhecido.

#### Scenario: Nome válido case-insensitive retorna enum
- **WHEN** o método é chamado com `"spi_j1"` (minúsculo)
- **THEN** retorna `TipoJornadaAutorizacao.SPI_J1`

#### Scenario: Nome inválido lança BusinessException
- **WHEN** o método é chamado com `"JORNADA_X"`
- **THEN** lança `BusinessException` com mensagem descritiva
