## MODIFIED Requirements

### Requirement: Separação de topo entre código e infraestrutura

O monorepo SHALL organizar-se em duas pastas de topo em inglês: `apps/` para código de aplicação e `infra/` para código de infraestrutura.

#### Scenario: Aplicações vivem sob apps/
- **WHEN** um desenvolvedor inspeciona a raiz do repositório
- **THEN** existe uma pasta `apps/` contendo `contratocommand/`, `contratoquery/`, `autorizacaostatus-producer/`, `eventos-consumer/` e `temporiza-autorizacao/`
- **AND** não existe mais a pasta `code/`

#### Scenario: Infraestrutura tem pasta dedicada
- **WHEN** um desenvolvedor inspeciona a raiz do repositório
- **THEN** existe uma pasta `infra/` dedicada a código de infraestrutura
- **AND** `apps/` não contém código de infraestrutura de provisionamento (Terraform)
- **AND** a infraestrutura Kafka local reside em `infra/local/kafka/`
- **AND** a infraestrutura Valkey local reside em `infra/local/redis/`

#### Scenario: Comportamento das aplicações preservado
- **WHEN** a suíte de testes de cada aplicação é executada após a reorganização
- **THEN** `mvn test` passa em `apps/contratocommand`, `apps/contratoquery`, `apps/autorizacaostatus-producer`, `apps/eventos-consumer` e `apps/temporiza-autorizacao`
- **AND** os endpoints, portas (8080/8081/8082/8083/8084) e contratos REST permanecem inalterados
