## 1. contratocommand — Configuração do pool HikariCP

- [x] 1.1 Adicionar bloco `spring.datasource.hikari` no `application.yaml` do `arj-contratocommand` com os cinco parâmetros parametrizáveis via variáveis de ambiente e valores padrão conservadores
- [x] 1.2 Verificar que a aplicação `contratocommand` inicializa sem nenhuma das novas variáveis de ambiente definidas (usa defaults do yaml)
- [x] 1.3 Verificar nos logs de inicialização do HikariCP que `maximumPoolSize` exibe o valor padrão 5

## 2. contratoquery — Configuração do pool HikariCP

- [x] 2.1 Adicionar bloco `spring.datasource.hikari` no `application.yaml` do `arj-contratoquery` com os cinco parâmetros parametrizáveis via variáveis de ambiente e valores padrão conservadores
- [x] 2.2 Verificar que a aplicação `contratoquery` inicializa sem nenhuma das novas variáveis de ambiente definidas (usa defaults do yaml)
- [x] 2.3 Verificar nos logs de inicialização do HikariCP que `maximumPoolSize` exibe o valor padrão 5

## 3. Validação de override por variável de ambiente

- [x] 3.1 Iniciar `contratocommand` com `DB_POOL_MAX_SIZE=2` definido e confirmar nos logs que o pool sobe com `maximumPoolSize..2`
- [x] 3.2 Iniciar `contratoquery` com `DB_POOL_MAX_SIZE=2` definido e confirmar nos logs que o pool sobe com `maximumPoolSize..2`
