## Why

O endpoint `POST /api/autorizacoes` aceita autorizações sem identificar a jornada (SPI J1, QRC J2/J3/J4) que originou a requisição, e persiste um `motivoStatus` genérico e sem significado de negócio. Isso impede rastreabilidade e auditoria por jornada.

## What Changes

- **NOVO**: Header HTTP obrigatório `tipoJornada` no `POST /api/autorizacoes` (values: `SPI_J1`, `QRC_J2`, `QRC_J3`, `QRC_J4`); requisições sem header válido são rejeitadas com `BusinessException` (HTTP 422).
- **NOVO**: Campo `TipoJornadaAutorizacao tipoJornada` adicionado ao record `CriarAutorizacaoRequest`.
- **NOVO**: Método `obterJornadaAutorizacaoEnumPorNome()` adicionado ao enum `TipoJornadaAutorizacao` (padrão dos demais enums; corrige também a exceção interna de `IllegalArgumentException` → `BusinessException`).
- **ALTERADO**: `motivoStatus` persistido no banco passa a armazenar o nome do enum `MotivoStatusAutorizacao` correspondente à jornada (`RECEPCAO_SPI_J1`, `LEITURA_QRC_J2`, `LEITURA_QRC_J3`, `LEITURA_QRC_J4`) em vez de texto genérico.
- **ALTERADO**: `AutorizacaoDetalheResponseDto` e `AutorizacaoResumidaResponseDto` (query app) passam a expor o campo `motivoStatus`.

## Capabilities

### New Capabilities

- `validacao-header-jornada`: Validação e extração do header `tipoJornada` no endpoint de contratação; rejeita valores inválidos com BusinessException.
- `motivo-status-por-jornada`: Persistência e exposição do `motivoStatus` correto por jornada nos dois lados (command e query).

### Modified Capabilities

<!-- Nenhum spec existente alterado — as capacidades acima são novas. -->

## Impact

- **contratocommand**: `AutorizacaoController`, `CriarAutorizacaoRequest`, `TipoJornadaAutorizacao`, `PixAutoMapper`, `DdaAutoMapper`, `Autorizacao.inicializaCriacao()` e todos os testes que criam `CriarAutorizacaoRequest` (via `TestFixtures`).
- **contratoquery**: `AutorizacaoDetalheResponseDto`, `AutorizacaoResumidaResponseDto` e seus respectivos testes.
- **API (contrato externo)**: O `POST /api/autorizacoes` passa a exigir o header `tipoJornada` — **breaking change** para clientes que não enviam o header.
- **Banco de dados**: Dados existentes em `motivo_status` não são migrados; o novo formato vale apenas para registros criados após o deploy.
