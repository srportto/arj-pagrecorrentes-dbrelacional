## ADDED Requirements

### Requirement: Jornada de origem persistida em coluna própria

A tabela `autorizacoes` SHALL possuir a coluna `tipo_jornada`, preenchida na criação com o
código do enum `TipoJornadaAutorizacao` correspondente ao header `tipoJornada` recebido. A
jornada de origem NÃO SHALL depender de leitura reversa de `motivo_status` para ser
recuperada — `motivo_status` é sobrescrito a cada transição de status e, portanto, não
preserva a jornada ao longo do ciclo de vida.

A coluna SHALL aceitar valor de "jornada desconhecida" para linhas criadas antes desta
mudança, sem exigir backfill. As duas aplicações que mapeiam a tabela
(`arj-contratocommand` e `arj-contratoquery`) SHALL declarar a coluna em suas entidades.

#### Scenario: Criação persiste a jornada recebida
- **WHEN** o sistema processa `POST /api/autorizacoes` com `tipoJornada: SPI_J1`
- **THEN** o registro em banco tem `tipo_jornada` correspondente a `SPI_J1`
- **AND** `motivo_status` continua sendo `RECEPCAO_SPI_J1`

#### Scenario: Jornada sobrevive à mudança de status
- **WHEN** uma autorização criada em `SPI_J1` transiciona para `REJEITADA` com
  `motivo_status = REJEITADA_PAGADOR`
- **THEN** a coluna `tipo_jornada` continua indicando `SPI_J1`

#### Scenario: Linhas legadas não impedem a leitura
- **WHEN** uma autorização criada antes desta mudança é lida
- **THEN** a coluna `tipo_jornada` apresenta o valor de jornada desconhecida, sem erro

### Requirement: Motivo de rejeição sistêmica por expiração de prazo

O enum `MotivoStatusAutorizacao` SHALL conter o valor `REJEITADA_SISTEMA_TIMEOUT_J1`,
descrevendo a rejeição aplicada pelo sistema por ausência de resposta do cliente pagador
dentro do prazo da jornada 1. Esse valor SHALL ser distinto de `REJEITADA_PAGADOR` (rejeição
explícita do cliente) e SHALL ser gravado exclusivamente pela ação de expiração da capacidade
`decisao-autorizacao`. Os valores `EXPIRADA_01` e `EXPIRADA_02` NÃO SHALL ser reaproveitados
para esta finalidade, pois descrevem o status `EXPIRADA`, não alcançado por este fluxo.

#### Scenario: Expiração e rejeição do cliente são distinguíveis
- **WHEN** duas autorizações em `RECEBIDA` terminam em `REJEITADA`, uma por rejeição
  explícita do cliente e outra por expiração de prazo
- **THEN** a primeira tem `motivo_status = REJEITADA_PAGADOR`
- **AND** a segunda tem `motivo_status = REJEITADA_SISTEMA_TIMEOUT_J1`

#### Scenario: Código do motivo é único no enum
- **WHEN** o enum `MotivoStatusAutorizacao` é inspecionado
- **THEN** `REJEITADA_SISTEMA_TIMEOUT_J1` possui código próprio, não colidindo com nenhum
  valor existente
