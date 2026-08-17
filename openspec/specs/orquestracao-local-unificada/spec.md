# orquestracao-local-unificada Specification

## Purpose

Definir a orquestração do ambiente de desenvolvimento local: um ponto de entrada único que sobe
banco, mensageria e as cinco aplicações sem exigir conhecimento tácito da ordem, preservando a
capacidade de subir cada ambiente de `infra/local/*` isoladamente, com definição única por serviço
de infraestrutura, extensões do PostgreSQL carregadas conforme declarado, publicação uniforme de
portas e fonte única de configuração via `.env`.

## Requirements

### Requirement: Ambiente local sobe por um ponto de entrada único

O repositório SHALL prover um ponto de entrada único capaz de subir o ambiente local completo —
banco, infraestrutura de mensageria e as cinco aplicações — sem exigir que a pessoa conheça a
ordem de subida dos ambientes.

A ordem de subida MUST NOT ser conhecimento tácito: se um ambiente depende de outro, a dependência
SHALL estar declarada no arquivo de composição, não apenas descrita em README.

#### Scenario: Subida completa a partir de repositório limpo

- **WHEN** alguém com o repositório recém-clonado e o `.env` preenchido executa o comando de
  subida do ponto de entrada único
- **THEN** as cinco aplicações sobem, o banco sobe com schema aplicado, e a infraestrutura de
  mensageria fica disponível
- **AND** nenhum passo manual anterior de subida de outro compose é necessário

#### Scenario: Nenhuma rede externa não criada

- **WHEN** o arquivo de composição do ambiente local é analisado
- **THEN** nenhuma rede declarada como externa depende de um compose que o próprio caminho
  unificado não sobe

### Requirement: Cada ambiente local continua subindo isoladamente

A existência do ponto de entrada único MUST NOT remover a capacidade de subir cada ambiente de
`infra/local/*` de forma independente. Cada `compose.yaml` SHALL permanecer um compose completo e
válido por si, com sua própria rede.

Esta é restrição de projeto herdada: `local-kafka-environment` exige que subir ou derrubar o
compose do Kafka não crie, altere nem dependa de recursos do compose de apps;
`local-valkey-environment` exige que subir o Valkey não exija Floci, Kafka nem PostgreSQL no ar.
O ponto de entrada único é uma camada de composição **acima** desses ambientes, nunca uma fusão
deles.

#### Scenario: Kafka sobe sozinho

- **WHEN** `infra/local/kafka/compose.yaml` é subido isoladamente
- **THEN** broker, Schema Registry e dashboard ficam disponíveis
- **AND** nenhum recurso do compose de apps é criado ou exigido

#### Scenario: Valkey sobe sozinho

- **WHEN** `infra/local/redis/compose.yaml` é subido isoladamente
- **THEN** a instância Valkey fica disponível
- **AND** Floci, Kafka e PostgreSQL não precisam estar no ar

#### Scenario: Composição referencia, não redefine

- **WHEN** o arquivo de composição de raiz é lido
- **THEN** ele referencia os composes de ambiente existentes
- **AND** não redeclara os serviços que eles já definem

### Requirement: Cada serviço de infraestrutura tem uma única definição

Um serviço de infraestrutura local (PostgreSQL, Kafka, Valkey, Floci) SHALL ser definido em
exatamente um arquivo de Docker Compose no repositório. Nenhum outro compose SHALL redeclarar o
mesmo serviço, ainda que com configuração equivalente.

Definição duplicada não é redundância inofensiva: as duas cópias divergem, e a divergência é
silenciosa. O caso concreto que originou este requisito — o PostgreSQL definido em
`infra/local/postgres/postgres-db-v18.yml` e em `apps/docker-compose.yml`, com apenas o primeiro
montando o diretório de migrations — produzia um banco **sem schema** quando subido pelo segundo
caminho, sem nenhuma mensagem de erro.

#### Scenario: PostgreSQL definido uma única vez

- **WHEN** os arquivos de Docker Compose do repositório são inspecionados
- **THEN** exatamente um deles declara um serviço PostgreSQL

#### Scenario: Banco sobe com schema por qualquer caminho

- **WHEN** o PostgreSQL local sobe com volume de dados limpo, por qualquer caminho documentado
- **THEN** os scripts de `infra/local/postgres/migrations/` são aplicados
- **AND** a tabela `autorizacoes` particionada existe ao fim da subida

#### Scenario: Healthcheck presente em qualquer caminho

- **WHEN** o PostgreSQL local sobe por qualquer caminho documentado
- **THEN** ele declara healthcheck, e os serviços que dele dependem esperam por `service_healthy`

### Requirement: Extensões do PostgreSQL carregam conforme declarado

O PostgreSQL local SHALL carregar `pg_partman_bgw` e `pg_cron`, declarados em uma **única**
diretiva `shared_preload_libraries` com lista separada por vírgula.

A mesma GUC MUST NOT ser passada em duas diretivas `-c` separadas: o PostgreSQL não acumula os
valores — o último prevalece —, de modo que a segunda declaração descarta a primeira sem erro,
sem aviso e sem entrada de log.

#### Scenario: Ambas as extensões carregadas

- **WHEN** `SHOW shared_preload_libraries;` é executado no banco local no ar
- **THEN** o resultado inclui `pg_partman_bgw` **e** `pg_cron`

#### Scenario: GUC não é declarada duas vezes

- **WHEN** o `command:` do serviço PostgreSQL é inspecionado
- **THEN** `shared_preload_libraries` aparece em exatamente uma diretiva `-c`

### Requirement: Publicação de portas é uniforme entre as aplicações, com exceção de réplicas múltiplas

As aplicações que rodam com uma única réplica SHALL publicar sua porta no host de forma explícita
e previsível, no formato `porta:porta`, usando a mesma porta declarada no `application.yaml` de
cada uma. Uma aplicação configurada com `deploy.replicas` maior que 1 MUST NOT publicar porta fixa
de host — múltiplos containers não podem fazer bind da mesma porta —, e essa exceção só é
aceitável para aplicações sem endpoint HTTP de negócio (apenas `/actuator/health`).

`temporiza-autorizacao` é a exceção vigente: roda com `deploy.replicas: 2` (a app já foi desenhada
para múltiplas instâncias concorrentes — o script Lua de varredura e o consumer id por `HOSTNAME`
não exigem coordenação externa) e não publica porta de host. Seu health-check é verificado via
`docker compose exec`/`docker inspect`, não por acesso HTTP direto do host.

#### Scenario: As portas de aplicações single-réplica são determinísticas

- **WHEN** o ambiente local sobe
- **THEN** `contratocommand`, `contratoquery`, `autorizacaostatus-producer` e `eventos-consumer`
  respondem em 8080, 8081, 8082 e 8083 no host
- **AND** nenhuma delas recebe porta atribuída aleatoriamente pelo Docker

#### Scenario: temporiza-autorizacao não publica porta de host

- **WHEN** o ambiente local sobe
- **THEN** `temporiza-autorizacao` roda com 2 réplicas e nenhuma delas tem porta publicada no host
- **AND** o healthcheck de cada réplica é validado via `docker inspect`/`docker compose exec`,
  não via `curl localhost:8084`

### Requirement: Configuração de ambiente tem uma fonte única

A configuração de ambiente do desenvolvimento local SHALL residir em um único arquivo `.env` —
senha de banco, nomes de banco e usuário —, com um `.env.example` versionado que o documente.
Cópias adicionais de `.env` em subdiretórios MUST NOT existir.

#### Scenario: Um .env e um .env.example

- **WHEN** o repositório é inspecionado
- **THEN** existe um único `.env.example` versionado
- **AND** não existem arquivos `.env` em subdiretórios de `apps/` ou `infra/`

#### Scenario: Variável obrigatória ausente falha com mensagem útil

- **WHEN** o ambiente local sobe sem `DB_PASSWORD` definida
- **THEN** a falha nomeia a variável e indica copiar `.env.example` para `.env`
