# monorepo-organization

## Purpose

TBD — capability recém-criada a partir da mudança `reorganizar-monorepo-code-infra`. Descreve como o monorepo separa código de aplicação (`apps/`) de código de infraestrutura (`infra/`), incluindo o esqueleto de Terraform, contêineres compatíveis com ECS/Fargate, configuração Spring por profiles, infraestrutura local de banco e documentação da topologia.

## Requirements

### Requirement: Separação de topo entre código e infraestrutura

O monorepo SHALL organizar-se em duas pastas de topo em inglês: `apps/` para código de aplicação e `infra/` para código de infraestrutura.

#### Scenario: Aplicações vivem sob apps/
- **WHEN** um desenvolvedor inspeciona a raiz do repositório
- **THEN** existe uma pasta `apps/` contendo `arj-contratocommand/`, `arj-contratoquery/`, `autorizacaostatus-producer/` e `eventos-consumer/`
- **AND** não existe mais a pasta `code/`

#### Scenario: Infraestrutura tem pasta dedicada
- **WHEN** um desenvolvedor inspeciona a raiz do repositório
- **THEN** existe uma pasta `infra/` dedicada a código de infraestrutura
- **AND** `apps/` não contém código de infraestrutura de provisionamento (Terraform)
- **AND** a infraestrutura Kafka local reside em `infra/local/kafka/`

#### Scenario: Comportamento das aplicações preservado
- **WHEN** a suíte de testes de cada aplicação é executada após a reorganização
- **THEN** `mvn test` passa em `apps/arj-contratocommand`, `apps/arj-contratoquery`, `apps/autorizacaostatus-producer` e `apps/eventos-consumer`
- **AND** os endpoints, portas (8080/8081/8082/8083) e contratos REST permanecem inalterados

### Requirement: Esqueleto de infraestrutura preparado para Terraform

A pasta `infra/` SHALL conter um esqueleto que antecipe a evolução para Terraform e AWS, sem provisionar recursos na cloud nesta fase. O esqueleto SHALL prever módulos reutilizáveis, ambientes separados (`local` e `prod`) e bootstrap de estado remoto, documentados por READMEs ou placeholders.

#### Scenario: Estrutura de módulos e ambientes existe
- **WHEN** um desenvolvedor abre a pasta `infra/`
- **THEN** encontra subpastas para módulos reutilizáveis e para ambientes (`local`, `prod`)
- **AND** cada área tem um README ou placeholder explicando o propósito e o que será implementado depois

#### Scenario: Nenhum recurso de cloud é provisionado
- **WHEN** a mudança é aplicada
- **THEN** nenhuma credencial AWS, backend remoto real ou recurso de cloud é criado
- **AND** o esqueleto não impede o repositório de compilar, testar ou rodar localmente

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

### Requirement: Configuração Spring por profiles local e prod

Cada aplicação SHALL organizar sua configuração em um `application.yml` base comum mais os arquivos `application-local.yml` e `application-prod.yml`. O profile ativo fixo `dev` SHALL ser removido; o profile ativo SHALL ser resolvido a partir do ambiente (`SPRING_PROFILES_ACTIVE`), assumindo `local` como default de desenvolvimento.

#### Scenario: Profile dev removido
- **WHEN** um desenvolvedor inspeciona o `application.yml` de qualquer aplicação
- **THEN** não há `spring.profiles.active: dev` fixo no arquivo

#### Scenario: Profile resolvido pelo ambiente
- **WHEN** a aplicação é iniciada com `SPRING_PROFILES_ACTIVE=prod`
- **THEN** as configurações de `application-prod.yml` são aplicadas sobre a base
- **AND** quando nenhum profile é informado em desenvolvimento, o profile `local` é usado

#### Scenario: Configuração comum não duplicada
- **WHEN** uma configuração é comum a todos os ambientes
- **THEN** ela reside no `application.yml` base e não é repetida nos arquivos de profile

### Requirement: Infraestrutura local de banco em infra/local

O Dockerfile do PostgreSQL com `pg_partman` e `pg_cron` SHALL residir sob `infra/local/` como artefato de infraestrutura de desenvolvimento local, e não sob `docs/`. Esse artefato NÃO SHALL ser usado como imagem de produção — na cloud o banco será um serviço gerido.

#### Scenario: Dockerfile do Postgres migrado
- **WHEN** um desenvolvedor procura como subir o banco local
- **THEN** o Dockerfile do Postgres (partman/cron) está sob `infra/local/`
- **AND** a documentação de setup local aponta para esse novo caminho

### Requirement: Documentação reflete a nova topologia

A documentação do repositório (README raiz, READMEs das aplicações e guias de agentes) SHALL ser atualizada para refletir a estrutura `apps/` + `infra/` e os novos caminhos de build, execução e infraestrutura local.

#### Scenario: README raiz atualizado
- **WHEN** um novo colaborador lê o `README.md` raiz
- **THEN** a árvore de diretórios documentada mostra `apps/` e `infra/`
- **AND** os comandos de build/execução referenciam `apps/<app>` e o caminho do banco local
