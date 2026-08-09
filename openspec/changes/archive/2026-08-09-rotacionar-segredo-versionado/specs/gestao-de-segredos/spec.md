## ADDED Requirements

### Requirement: Proibição de segredo literal em arquivo versionado

Nenhum arquivo rastreado pelo git SHALL conter credencial, senha, chave de API ou token com
valor real — nem diretamente, nem como valor padrão de expansão de variável de ambiente
(`${VAR:-valor}`). Credenciais dummy exigidas por emuladores locais (ex.: `test`/`test` do
LocalStack/Floci) NÃO são consideradas segredo e permanecem permitidas.

#### Scenario: Arquivo de composição sem valor padrão de credencial

- **WHEN** `apps/docker-compose.yml` ou `infra/local/postgres/postgres-db-v18.yml` é inspecionado
- **THEN** a variável `DB_PASSWORD` SHALL aparecer sem nenhum valor padrão literal, e nenhuma
  senha real SHALL estar presente no arquivo

#### Scenario: Credencial dummy de emulador permanece permitida

- **WHEN** um `application-local.yaml` declara `access-key: test` e `secret-key: test` para o
  emulador AWS local
- **THEN** isso NÃO SHALL ser tratado como violação, por não constituir credencial real

#### Scenario: Arquivo de variáveis Terraform sem segredo versionado

- **WHEN** `infra/envs/local/terraform.tfvars` (ou qualquer outro `*.tfvars` do repositório) é
  inspecionado no controle de versão
- **THEN** o arquivo NÃO SHALL estar rastreado pelo git nem conter senha real — a variável
  `db_password` é suprida via `TF_VAR_db_password` no ambiente ou via `.tfvars` local ignorado
  (`*.tfvars` no `.gitignore`), documentado por um `terraform.tfvars.example` versionado

### Requirement: Ausência de variável obrigatória falha de forma explícita

Quando uma variável de ambiente que carrega credencial não está definida, o ambiente SHALL
abortar a inicialização com mensagem explícita indicando qual variável falta e como supri-la. O
ambiente NÃO SHALL adotar valor padrão nem prosseguir com credencial vazia.

#### Scenario: Subida sem DB_PASSWORD definida

- **WHEN** `docker compose up` é executado sem `DB_PASSWORD` definida no ambiente nem em `.env`
- **THEN** o Compose SHALL abortar antes de iniciar os containers, exibindo mensagem que nomeia a
  variável ausente e aponta o `.env.example` como caminho de correção

#### Scenario: Subida com DB_PASSWORD definida

- **WHEN** `DB_PASSWORD` está definida no `.env` ou exportada no ambiente
- **THEN** os containers SHALL iniciar normalmente, usando o valor fornecido

### Requirement: Contrato de configuração documentado via .env.example

O repositório SHALL versionar um `.env.example` que enumera todas as variáveis de ambiente
obrigatórias para subir o ambiente local, com placeholders sem valor real. O arquivo `.env` real
SHALL estar listado no `.gitignore`.

#### Scenario: Placeholder sem valor real

- **WHEN** o `.env.example` é inspecionado
- **THEN** cada variável SHALL ter placeholder descritivo (ex.: `DB_PASSWORD=<defina-sua-senha>`)
  e nenhum valor utilizável como credencial

#### Scenario: .env real não é versionado

- **WHEN** um desenvolvedor cria `.env` a partir do `.env.example` e executa `git status`
- **THEN** o arquivo `.env` NÃO SHALL aparecer como arquivo não rastreado passível de commit

### Requirement: Rotação obrigatória após exposição

A remoção do literal do código NÃO SHALL ser considerada suficiente quando uma credencial é
identificada em arquivo versionado. A credencial SHALL ser rotacionada em todos os ambientes
onde o valor exposto tenha sido utilizado, uma vez que o histórico do git preserva o valor
original.

#### Scenario: Correção considerada completa

- **WHEN** o literal é removido dos arquivos de composição mas a senha do Postgres não foi trocada
  em nenhum ambiente
- **THEN** a correção NÃO SHALL ser considerada completa, pois o valor permanece recuperável no
  histórico de commits e continua válido
