## MODIFIED Requirements

### Requirement: Valor padrão para maximumPoolSize

As aplicações SHALL definir `maximum-pool-size` com valor padrão de 10 conexões
(`${DB_POOL_MAX_SIZE:10}`), conforme exigido pela capacidade `virtual-threads-config`.

O valor 10 é consequência direta da habilitação de Virtual Threads: a maior concorrência de
requisições exige pool proporcional, e um pool subdimensionado sob Virtual Threads produz
contenção no acesso ao banco. Em caso de divergência entre esta capacidade e
`virtual-threads-config` sobre esta propriedade, `virtual-threads-config` prevalece, por ser a
capacidade que motiva o valor.

#### Scenario: Default é aplicado sem configuração explícita

- **WHEN** `DB_POOL_MAX_SIZE` não está definida e a aplicação inicializa
- **THEN** o HikariCP SHALL operar com `maximumPoolSize = 10`

#### Scenario: Valor coerente entre specs e código

- **WHEN** o valor padrão declarado nesta capacidade, em `virtual-threads-config` e no
  `application.yaml` das duas aplicações é comparado
- **THEN** os três SHALL declarar 10, sem contradição

#### Scenario: Override por variável de ambiente continua válido

- **WHEN** `DB_POOL_MAX_SIZE` é definida com valor numérico inteiro positivo
- **THEN** o HikariCP SHALL configurar `maximumPoolSize` com o valor fornecido, sobrescrevendo o
  padrão
