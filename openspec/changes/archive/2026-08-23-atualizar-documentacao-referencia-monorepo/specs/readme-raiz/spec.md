## MODIFIED Requirements

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
