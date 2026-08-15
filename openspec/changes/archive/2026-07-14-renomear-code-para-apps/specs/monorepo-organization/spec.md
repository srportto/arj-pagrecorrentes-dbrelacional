## MODIFIED Requirements

### Requirement: Separação de topo entre código e infraestrutura

O monorepo SHALL organizar-se em duas pastas de topo em inglês: `apps/` para código de aplicação e `infra/` para código de infraestrutura.

#### Scenario: Aplicações vivem sob apps/
- **WHEN** um desenvolvedor inspeciona a raiz do repositório
- **THEN** existe uma pasta `apps/` contendo `contratocommand/` e `contratoquery/`
- **AND** não existe mais a pasta `code/`

#### Scenario: Infraestrutura tem pasta dedicada
- **WHEN** um desenvolvedor inspeciona a raiz do repositório
- **THEN** existe uma pasta `infra/` dedicada a código de infraestrutura
- **AND** `apps/` não contém código de infraestrutura de provisionamento (Terraform)

#### Scenario: Comportamento das aplicações preservado
- **WHEN** a suíte de testes de cada aplicação é executada após a reorganização
- **THEN** `mvn test` passa em `apps/contratocommand` e `apps/contratoquery`
- **AND** os endpoints, portas (8080/8081) e contratos REST permanecem inalterados

### Requirement: Contêiner por aplicação compatível com ECS/Fargate

Cada aplicação sob `apps/` SHALL possuir um `Dockerfile` que produza uma imagem compatível com AWS ECS e Fargate. A imagem SHALL ser construída em múltiplos estágios (build + runtime enxuto), executar o JAR sobre um JRE, expor a porta HTTP da aplicação e permitir health-check via `/actuator/health`.

#### Scenario: Imagem builda e roda o serviço
- **WHEN** a imagem de uma aplicação é construída a partir do seu `Dockerfile` e executada
- **THEN** o serviço inicia e responde em sua porta (`8080` para command, `8081` para query)
- **AND** `/actuator/health` responde `200 (UP)` quando o banco está acessível

#### Scenario: Imagem lê configuração do ambiente
- **WHEN** o contêiner é iniciado com variáveis de ambiente (`SPRING_PROFILES_ACTIVE`, `DB_NAME`, `DB_USER_NAME`, `DB_PASSWORD`)
- **THEN** a aplicação usa esses valores sem exigir edição de arquivos dentro da imagem
- **AND** os logs são escritos em stdout

#### Scenario: Encerramento gracioso
- **WHEN** o contêiner recebe sinal de término (SIGTERM)
- **THEN** a aplicação encerra de forma graciosa, drenando requisições em andamento

### Requirement: Documentação reflete a nova topologia

A documentação do repositório (README raiz, READMEs das aplicações e guias de agentes) SHALL ser atualizada para refletir a estrutura `apps/` + `infra/` e os novos caminhos de build, execução e infraestrutura local.

#### Scenario: README raiz atualizado
- **WHEN** um novo colaborador lê o `README.md` raiz
- **THEN** a árvore de diretórios documentada mostra `apps/` e `infra/`
- **AND** os comandos de build/execução referenciam `apps/<app>` e o caminho do banco local
