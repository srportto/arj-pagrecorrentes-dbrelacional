## Why

Atualmente as aplicações `contratocommand` e `contratoquery` não definem limites no pool de conexões do HikariCP, deixando os valores padrão (10 conexões por instância). Ao escalar múltiplos containers isso gera sobrecarga no banco de dados com conexões abertas desnecessárias. Tornar esses valores parametrizáveis via variáveis de ambiente permite ajustar o pool por ambiente, reduzindo o footprint de cada container e possibilitando escalar horizontalmente com mais instâncias sem saturar o banco.

## What Changes

- Adição de configurações do HikariCP em `application.yaml` das duas aplicações (`maximum-pool-size`, `minimum-idle`, `connection-timeout`, `idle-timeout`, `max-lifetime`), com valores padrão conservadores e suporte a override por variável de ambiente.
- Nenhuma mudança de API ou comportamento funcional — apenas configuração de infraestrutura.

## Capabilities

### New Capabilities

- `db-connection-pool-config`: Configuração parametrizável do pool de conexões HikariCP nas aplicações `contratocommand` e `contratoquery`, com valores padrão via `application.yaml` e override por variáveis de ambiente.

### Modified Capabilities

<!-- Nenhuma capability existente tem seus requisitos funcionais alterados -->

## Impact

- **Arquivos alterados**: `aplicacoes/arj-contratocommand/src/main/resources/application.yaml`, `aplicacoes/arj-contratoquery/src/main/resources/application.yaml`
- **Sem impacto em API**: Mudança puramente de configuração de datasource/pool.
- **Variáveis de ambiente novas**: `DB_POOL_MAX_SIZE`, `DB_POOL_MIN_IDLE`, `DB_POOL_CONNECTION_TIMEOUT`, `DB_POOL_IDLE_TIMEOUT`, `DB_POOL_MAX_LIFETIME` (opcionais, com defaults definidos no yaml).
- **Dependência**: HikariCP já é o pool padrão do Spring Boot — nenhuma nova dependência necessária.
