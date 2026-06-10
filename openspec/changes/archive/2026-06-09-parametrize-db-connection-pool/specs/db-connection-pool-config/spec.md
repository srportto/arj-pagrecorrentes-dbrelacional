## ADDED Requirements

### Requirement: Pool de conexões parametrizável via variáveis de ambiente
As aplicações `contratocommand` e `contratoquery` SHALL expor os parâmetros do pool HikariCP como variáveis de ambiente com valores padrão definidos no `application.yaml`. As variáveis são opcionais — a ausência de qualquer uma delas NÃO DEVE impedir a inicialização da aplicação.

#### Scenario: Aplicação sobe sem variáveis de pool definidas
- **WHEN** a aplicação é iniciada sem as variáveis `DB_POOL_MAX_SIZE`, `DB_POOL_MIN_IDLE`, `DB_POOL_CONNECTION_TIMEOUT`, `DB_POOL_IDLE_TIMEOUT` e `DB_POOL_MAX_LIFETIME` definidas no ambiente
- **THEN** a aplicação SHALL utilizar os valores padrão configurados no `application.yaml` e inicializar com sucesso

#### Scenario: Tamanho máximo do pool é sobrescrito por variável de ambiente
- **WHEN** a variável `DB_POOL_MAX_SIZE` é definida com valor numérico inteiro positivo no ambiente de execução
- **THEN** o HikariCP SHALL configurar `maximumPoolSize` com o valor fornecido, conforme visível nos logs de inicialização do pool

#### Scenario: Mínimo de conexões ociosas é sobrescrito por variável de ambiente
- **WHEN** a variável `DB_POOL_MIN_IDLE` é definida com valor numérico inteiro não-negativo
- **THEN** o HikariCP SHALL configurar `minimumIdle` com o valor fornecido

#### Scenario: Timeouts do pool são sobrescritos por variáveis de ambiente
- **WHEN** as variáveis `DB_POOL_CONNECTION_TIMEOUT`, `DB_POOL_IDLE_TIMEOUT` e/ou `DB_POOL_MAX_LIFETIME` são definidas com valores em milissegundos
- **THEN** o HikariCP SHALL aplicar os respectivos timeouts fornecidos

### Requirement: Valor padrão conservador para maximumPoolSize
As aplicações SHALL definir `maximum-pool-size` com valor padrão de 5 conexões, reduzindo o footprint por container em relação ao default de 10 do HikariCP.

#### Scenario: Default reduzido é aplicado sem configuração explícita
- **WHEN** `DB_POOL_MAX_SIZE` não está definida e a aplicação inicializa
- **THEN** o HikariCP SHALL operar com `maximumPoolSize = 5`
