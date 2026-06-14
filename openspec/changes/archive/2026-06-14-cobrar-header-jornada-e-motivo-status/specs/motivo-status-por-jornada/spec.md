## ADDED Requirements

### Requirement: motivoStatus persistido reflete a jornada de origem
Ao criar uma autorização com sucesso, o campo `motivo_status` na tabela `autorizacoes` SHALL ser preenchido com o nome do enum `MotivoStatusAutorizacao` correspondente à jornada recebida no header `tipoJornada`, e não com texto genérico.

Mapeamento SHALL ser:
- `SPI_J1` → `RECEPCAO_SPI_J1`
- `QRC_J2` → `LEITURA_QRC_J2`
- `QRC_J3` → `LEITURA_QRC_J3`
- `QRC_J4` → `LEITURA_QRC_J4`

#### Scenario: Contratação via SPI J1 persiste motivo correto
- **WHEN** o sistema processa `POST /api/autorizacoes` com `tipoJornada: SPI_J1`
- **THEN** o registro em banco tem `motivo_status = 'RECEPCAO_SPI_J1'`

#### Scenario: Contratação via QRC J2 persiste motivo correto
- **WHEN** o sistema processa `POST /api/autorizacoes` com `tipoJornada: QRC_J2`
- **THEN** o registro em banco tem `motivo_status = 'LEITURA_QRC_J2'`

#### Scenario: Contratação via QRC J3 persiste motivo correto
- **WHEN** o sistema processa `POST /api/autorizacoes` com `tipoJornada: QRC_J3`
- **THEN** o registro em banco tem `motivo_status = 'LEITURA_QRC_J3'`

#### Scenario: Contratação via QRC J4 persiste motivo correto
- **WHEN** o sistema processa `POST /api/autorizacoes` com `tipoJornada: QRC_J4`
- **THEN** o registro em banco tem `motivo_status = 'LEITURA_QRC_J4'`

#### Scenario: Campo motivoStatus exposto no response do command
- **WHEN** a autorização é criada com sucesso
- **THEN** o `AutorizacaoCompletaResponseDto` retornado contém `motivoStatus` com o nome do enum da jornada

### Requirement: motivoStatus exposto nos DTOs de resposta da query app
A query app SHALL expor o campo `motivoStatus` (valor string armazenado no banco) em ambos os DTOs de resposta: `AutorizacaoDetalheResponseDto` (endpoint `GET /api/autorizacoes/{id}`) e `AutorizacaoResumidaResponseDto` (endpoint `GET /api/autorizacoes`).

#### Scenario: Consulta por id retorna motivoStatus
- **WHEN** o cliente chama `GET /api/autorizacoes/{id}` para uma autorização existente
- **THEN** o response inclui o campo `motivoStatus` com o valor persistido no banco (ex: `"RECEPCAO_SPI_J1"`)

#### Scenario: Listagem retorna motivoStatus em cada item
- **WHEN** o cliente chama `GET /api/autorizacoes?idUnicoContaContratante=...`
- **THEN** cada item do array retornado inclui o campo `motivoStatus`
