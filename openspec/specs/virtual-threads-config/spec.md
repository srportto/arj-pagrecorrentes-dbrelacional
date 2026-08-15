# virtual-threads-config

## Purpose

Habilitar Virtual Threads do Java em `contratocommand` e `contratoquery` via propriedade do Spring Boot, ajustando o pool do Hikari para evitar thundering herd e garantindo que build e testes continuem passando.

## Requirements

### Requirement: Virtual Threads habilitadas via propriedade Spring Boot
Ambas as aplicações (`contratocommand` e `contratoquery`) SHALL ter `spring.threads.virtual.enabled: true` configurado em seus respectivos `application.yaml`, ativando Virtual Threads do Java 25 para o container web e para execução de tarefas I/O-bound.

#### Scenario: Propriedade presente no command app
- **WHEN** a aplicação `contratocommand` é inicializada
- **THEN** o log de startup SHALL indicar uso de Virtual Threads no Tomcat (executor substituído)

#### Scenario: Propriedade presente no query app
- **WHEN** a aplicação `contratoquery` é inicializada
- **THEN** o log de startup SHALL indicar uso de Virtual Threads no Jetty

### Requirement: Hikari pool ajustado para Virtual Threads
O `maximum-pool-size` do Hikari SHALL ser ajustado para `10` (padrão via `${DB_POOL_MAX_SIZE:10}`) em ambas as aplicações, evitando thundering herd enquanto aproveita a maior concorrência proporcionada por Virtual Threads.

#### Scenario: Pool máximo configurado para 10 como padrão
- **WHEN** nenhuma variável de ambiente `DB_POOL_MAX_SIZE` é definida
- **THEN** o pool Hikari SHALL usar `maximum-pool-size` igual a `10`

#### Scenario: Pool configurável via variável de ambiente
- **WHEN** a variável de ambiente `DB_POOL_MAX_SIZE` é definida
- **THEN** o pool Hikari SHALL usar o valor da variável, sobrescrevendo o padrão

### Requirement: Build e testes continuam passando após habilitação de Virtual Threads
Ambas as aplicações SHALL continuar buildando com `mvn verify` e todos os testes unitários SHALL continuar passando após a habilitação de Virtual Threads. A cobertura mínima de 80% SHALL ser mantida.

#### Scenario: Build bem-sucedido no command app após mudança
- **WHEN** `mvn verify` é executado em `contratocommand` com Virtual Threads habilitadas
- **THEN** o build SHALL completar sem erros e todos os testes SHALL passar

#### Scenario: Build bem-sucedido no query app após mudança
- **WHEN** `mvn verify` é executado em `contratoquery` com Virtual Threads habilitadas
- **THEN** o build SHALL completar sem erros e todos os testes SHALL passar
