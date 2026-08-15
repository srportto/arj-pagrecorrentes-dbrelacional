# monorepo-organization

## MODIFIED Requirements

### Requirement: Separação de topo entre código e infraestrutura

O monorepo SHALL organizar-se em duas pastas de topo em inglês: `apps/` para código de aplicação e `infra/` para código de infraestrutura.

#### Scenario: Aplicações vivem sob apps/
- **WHEN** um desenvolvedor inspeciona a raiz do repositório
- **THEN** existe uma pasta `apps/` contendo `contratocommand/`, `contratoquery/`, `autorizacaostatus-producer/` e `eventos-consumer/`
- **AND** não existe mais a pasta `code/`

#### Scenario: Infraestrutura tem pasta dedicada
- **WHEN** um desenvolvedor inspeciona a raiz do repositório
- **THEN** existe uma pasta `infra/` dedicada a código de infraestrutura
- **AND** `apps/` não contém código de infraestrutura de provisionamento (Terraform)
- **AND** a infraestrutura Kafka local reside em `infra/local/kafka/`

#### Scenario: Comportamento das aplicações preservado
- **WHEN** a suíte de testes de cada aplicação é executada após a reorganização
- **THEN** `mvn test` passa em `apps/contratocommand`, `apps/contratoquery`, `apps/autorizacaostatus-producer` e `apps/eventos-consumer`
- **AND** os endpoints, portas (8080/8081/8082/8083) e contratos REST permanecem inalterados

### Requirement: Contêiner por aplicação compatível com ECS/Fargate

Cada aplicação sob `apps/` SHALL possuir um `Dockerfile` que produza uma imagem compatível com AWS ECS e Fargate. A imagem SHALL ser construída em múltiplos estágios (build + runtime enxuto), executar o JAR sobre um JRE, expor a porta HTTP da aplicação e permitir health-check via `/actuator/health`.

#### Scenario: Imagem builda e roda o serviço
- **WHEN** a imagem de uma aplicação é construída a partir do seu `Dockerfile` e executada
- **THEN** o serviço inicia e responde em sua porta (`8080` para command, `8081` para query, `8082` para autorizacaostatus-producer, `8083` para eventos-consumer)
- **AND** `/actuator/health` responde `200 (UP)` quando as dependências da aplicação estão acessíveis (banco para command/query; nenhuma dependência obrigatória para autorizacaostatus-producer e eventos-consumer)

#### Scenario: Imagem lê configuração do ambiente
- **WHEN** o contêiner é iniciado com variáveis de ambiente (`SPRING_PROFILES_ACTIVE` e as variáveis da aplicação, como `DB_NAME`/`DB_USER_NAME`/`DB_PASSWORD` para command/query, endpoint/fila AWS e conexão Kafka para autorizacaostatus-producer, ou bootstrap servers/Schema Registry para eventos-consumer)
- **THEN** a aplicação usa esses valores sem exigir edição de arquivos dentro da imagem
- **AND** os logs são escritos em stdout

#### Scenario: Encerramento gracioso
- **WHEN** o contêiner recebe sinal de término (SIGTERM)
- **THEN** a aplicação encerra de forma graciosa, drenando requisições em andamento
