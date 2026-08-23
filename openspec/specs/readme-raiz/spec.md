## Purpose

Descreve a documentação de raiz do repositório `arj-pagrecorrentes-dbrelacional`: a visão geral
do sistema, a relação entre os microserviços e os links para a documentação de cada aplicação.

## Requirements

### Requirement: Repositório deve possuir README.md na raiz
O repositório `arj-pagrecorrentes-dbrelacional` SHALL possuir um `README.md` na raiz com visão geral do sistema, estrutura de pastas e links para os READMEs individuais de cada microserviço.

#### Scenario: README presente na raiz do repositório
- **WHEN** um desenvolvedor ou agente navega até a raiz do repositório
- **THEN** o arquivo `README.md` SHALL existir e descrever o propósito geral do sistema

### Requirement: README de raiz descreve a relação entre os microserviços
O `README.md` SHALL deixar claro que o sistema é composto por dois microserviços complementares — `contratocommand` (escrita, porta 8080) e `contratoquery` (leitura, porta 8081) — que compartilham o mesmo banco PostgreSQL.

#### Scenario: Relação command/query documentada
- **WHEN** o README de raiz é lido
- **THEN** SHALL ser possível entender que o command é responsável por escrita e o query por leitura, sem precisar abrir os READMEs individuais

### Requirement: README de raiz linka para documentação de cada app
O `README.md` SHALL conter links para o `README.md` de **cada** aplicação existente em `apps/`, sem
nomear um subconjunto fixo no texto do requisito, bem como para os arquivos relevantes em `docs/` e
para os guias de ambiente local em `infra/local/` que sustentam a subida do sistema (Postgres,
mensageria, Kafka, Valkey).

#### Scenario: Links funcionais para cada app
- **WHEN** o README de raiz é lido
- **THEN** SHALL existir link explícito para o `README.md` de cada aplicação presente em `apps/`
- **AND** SHALL existir links para os arquivos relevantes em `docs/`

#### Scenario: Nova app exige atualização do link, não do requisito
- **WHEN** uma aplicação nova é adicionada em `apps/`
- **THEN** o `README.md` de raiz SHALL passar a linkar o `README.md` dessa app
- **AND** este requisito NÃO SHALL precisar ser editado apenas por causa da app nova

#### Scenario: Guia de ambiente local do Postgres está linkado
- **WHEN** o README de raiz é lido
- **THEN** SHALL existir link explícito para `infra/local/postgres/README.md`
