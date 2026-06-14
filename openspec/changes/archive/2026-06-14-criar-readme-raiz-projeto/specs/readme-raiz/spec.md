## ADDED Requirements

### Requirement: Repositório deve possuir README.md na raiz
O repositório `arj-pagrecorrentes-dbrelacional` SHALL possuir um `README.md` na raiz com visão geral do sistema, estrutura de pastas e links para os READMEs individuais de cada microserviço.

#### Scenario: README presente na raiz do repositório
- **WHEN** um desenvolvedor ou agente navega até a raiz do repositório
- **THEN** o arquivo `README.md` SHALL existir e descrever o propósito geral do sistema

### Requirement: README de raiz descreve a relação entre os microserviços
O `README.md` SHALL deixar claro que o sistema é composto por dois microserviços complementares — `arj-contratocommand` (escrita, porta 8080) e `arj-contratoquery` (leitura, porta 8081) — que compartilham o mesmo banco PostgreSQL.

#### Scenario: Relação command/query documentada
- **WHEN** o README de raiz é lido
- **THEN** SHALL ser possível entender que o command é responsável por escrita e o query por leitura, sem precisar abrir os READMEs individuais

### Requirement: README de raiz linka para documentação de cada app
O `README.md` SHALL conter links para `aplicacoes/arj-contratocommand/README.md` e `aplicacoes/arj-contratoquery/README.md`, bem como para os arquivos relevantes em `docs/`.

#### Scenario: Links funcionais para cada app
- **WHEN** o README de raiz é lido
- **THEN** SHALL existir links explícitos para os READMEs de ambas as apps e para a pasta `docs/`
