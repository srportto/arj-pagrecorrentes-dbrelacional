## Why

Hoje as duas aplicações usam o nível de isolamento transacional implícito (default do PostgreSQL/HikariCP) e ambas abrem conexões em modo leitura/escrita, mesmo o `contratoquery`, que é o lado de leitura do CQRS e nunca escreve. Falta (a) um modo de acesso explícito que impeça escritas acidentais no `contratoquery` e (b) controle do nível de isolamento que possa ser ajustado por ambiente, sem recompilar, conforme a necessidade de concorrência evolui.

## What Changes

- **Nível de isolamento parametrizável** em `contratocommand` e `contratoquery` via `spring.datasource.hikari.transaction-isolation`, alimentado por variável de ambiente `DB_TRANSACTION_ISOLATION` com default `TRANSACTION_READ_COMMITTED` (default do PostgreSQL). Ajustável a qualquer momento (basta redefinir a variável e reiniciar), sem mudança de código.
- **Modo de acesso parametrizável** via `spring.datasource.hikari.read-only`, alimentado por `DB_READ_ONLY`:
  - `contratoquery` → default `true` (somente leitura; o PostgreSQL rejeita escritas na conexão).
  - `contratocommand` → default `false` (leitura e escrita).
- Mudança **config-only**: nenhuma alteração de código de aplicação; apenas os dois `application.yaml`. As variáveis são opcionais — a ausência usa os defaults e a aplicação sobe normalmente (mesmo padrão de `db-connection-pool-config`).

## Capabilities

### New Capabilities
- `db-isolation-and-access`: parametrização do nível de isolamento transacional (ambas as apps) e do modo de acesso ao banco (read-only no `contratoquery`, read-write no `contratocommand`) via variáveis de ambiente com defaults no `application.yaml`.

### Modified Capabilities
<!-- Nenhuma. A capability db-connection-pool-config continua válida e não tem requisitos alterados. -->

## Impact

- **contratocommand** — `src/main/resources/application.yaml`: adiciona `hikari.transaction-isolation` e `hikari.read-only` (default `false`).
- **contratoquery** — `src/main/resources/application.yaml`: adiciona `hikari.transaction-isolation` e `hikari.read-only` (default `true`).
- **Variáveis de ambiente novas (opcionais):** `DB_TRANSACTION_ISOLATION`, `DB_READ_ONLY`.
- **Dependências:** nenhuma nova (HikariCP já presente via `spring-boot-starter-data-jpa`).
- **Comportamento:** escritas no `contratoquery` passam a falhar (read-only) — desejável, pois ele só lê; o `contratocommand` mantém o comportamento atual de escrita.
