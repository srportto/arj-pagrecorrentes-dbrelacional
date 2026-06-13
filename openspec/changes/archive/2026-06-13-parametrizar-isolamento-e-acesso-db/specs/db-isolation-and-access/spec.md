## ADDED Requirements

### Requirement: Nível de isolamento transacional parametrizável via variável de ambiente
As aplicações `contratocommand` e `contratoquery` SHALL definir o nível de isolamento transacional do pool HikariCP via `spring.datasource.hikari.transaction-isolation`, alimentado pela variável de ambiente `DB_TRANSACTION_ISOLATION`, com valor padrão `TRANSACTION_READ_COMMITTED` no `application.yaml`. A variável é opcional — sua ausência NÃO DEVE impedir a inicialização.

#### Scenario: Aplicação sobe sem DB_TRANSACTION_ISOLATION definida
- **WHEN** a aplicação é iniciada sem `DB_TRANSACTION_ISOLATION` no ambiente
- **THEN** o HikariCP SHALL operar com `transactionIsolation = TRANSACTION_READ_COMMITTED` (default do PostgreSQL) e inicializar com sucesso

#### Scenario: Nível de isolamento é sobrescrito por variável de ambiente
- **WHEN** a variável `DB_TRANSACTION_ISOLATION` é definida com um nome de constante JDBC válido (ex.: `TRANSACTION_REPEATABLE_READ` ou `TRANSACTION_SERIALIZABLE`)
- **THEN** o HikariCP SHALL configurar `transactionIsolation` com o valor fornecido, aplicado às conexões do pool

### Requirement: contratoquery opera em modo somente-leitura parametrizável
O `contratoquery` SHALL configurar `spring.datasource.hikari.read-only` via variável de ambiente `DB_READ_ONLY`, com valor padrão `true` no `application.yaml`. Em modo somente-leitura, tentativas de escrita SHALL ser rejeitadas pelo PostgreSQL.

#### Scenario: contratoquery sobe sem DB_READ_ONLY definida
- **WHEN** o `contratoquery` é iniciado sem `DB_READ_ONLY` no ambiente
- **THEN** o HikariCP SHALL abrir conexões com `readOnly = true`

#### Scenario: Escrita no contratoquery é rejeitada em modo somente-leitura
- **WHEN** uma operação de escrita (INSERT/UPDATE/DELETE) é tentada através do `contratoquery` com `read-only = true`
- **THEN** o PostgreSQL SHALL rejeitar a operação com erro de transação somente-leitura

#### Scenario: Modo de acesso do contratoquery pode ser sobrescrito
- **WHEN** a variável `DB_READ_ONLY` é definida como `false`
- **THEN** o HikariCP SHALL abrir conexões em modo leitura/escrita, sem necessidade de alterar código

### Requirement: contratocommand opera em modo leitura/escrita parametrizável
O `contratocommand` SHALL configurar `spring.datasource.hikari.read-only` via variável de ambiente `DB_READ_ONLY`, com valor padrão `false` no `application.yaml`, preservando a capacidade de escrita das operações de contratação e cancelamento.

#### Scenario: contratocommand sobe sem DB_READ_ONLY definida
- **WHEN** o `contratocommand` é iniciado sem `DB_READ_ONLY` no ambiente
- **THEN** o HikariCP SHALL abrir conexões com `readOnly = false` (leitura/escrita)

#### Scenario: Escritas continuam funcionando no contratocommand
- **WHEN** uma autorização é criada (`POST /api/autorizacoes`) ou cancelada (`PATCH /api/autorizacoes/{id}/cancelar`) com a configuração padrão
- **THEN** a operação de escrita SHALL ser persistida normalmente, sem alteração de comportamento
