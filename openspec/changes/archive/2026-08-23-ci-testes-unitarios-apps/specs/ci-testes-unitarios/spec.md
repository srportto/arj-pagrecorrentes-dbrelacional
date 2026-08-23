## ADDED Requirements

### Requirement: Cada app dispara sua própria esteira de testes unitários por path

O monorepo SHALL possuir um workflow de GitHub Actions por app (`contratocommand`, `contratoquery`,
`autorizacaostatus-producer`, `eventos-consumer`, `temporiza-autorizacao`), disparado em `push` e
`pull_request` restritos a `paths: apps/<app>/**`, de forma que uma alteração isolada numa única app
NÃO SHALL disparar a esteira de testes das outras 4.

#### Scenario: Alteração em uma única app dispara só a esteira correspondente

- **WHEN** um pull request altera exclusivamente arquivos dentro de `apps/contratocommand/**`
- **THEN** o workflow de testes unitários de `contratocommand` SHALL ser executado
- **AND** os workflows de testes unitários das outras 4 apps NÃO SHALL ser executados

#### Scenario: Alteração fora de `apps/**` não dispara nenhuma esteira de testes

- **WHEN** um pull request altera apenas arquivos fora de qualquer diretório `apps/<nome>/**` (ex.:
  `README.md` na raiz)
- **THEN** nenhum dos 5 workflows de testes unitários SHALL ser executado

### Requirement: Esteira executa apenas testes unitários, excluindo testes de integração

Cada workflow de testes unitários SHALL executar os testes da app usando o Maven pré-instalado no
runner e
SHALL excluir da execução toda classe de teste cujo nome termine em `IntegrationTest`, independente
de essa classe já se auto-desabilitar (via condição de ambiente) ou não.

#### Scenario: Testes de integração não são executados mesmo sem guarda de ambiente

- **WHEN** a esteira de `autorizacaostatus-producer` ou `temporiza-autorizacao` é executada num
  runner sem Floci/Valkey disponível
- **THEN** as classes de teste terminadas em `IntegrationTest` NÃO SHALL ser executadas
- **AND** a esteira SHALL concluir com sucesso caso os testes unitários passem

#### Scenario: Testes unitários continuam sendo executados normalmente

- **WHEN** qualquer uma das 5 esteiras é executada
- **THEN** todas as classes de teste que não terminam em `IntegrationTest` SHALL ser executadas
  normalmente pelo Surefire

### Requirement: Cache de dependências Maven isolado por app

Cada workflow SHALL configurar cache de dependências Maven (`~/.m2`) com chave derivada do
`pom.xml` da própria app, de forma que uma mudança de dependência numa app NÃO SHALL invalidar o
cache das outras 4 apps.

#### Scenario: Mudança de dependência em uma app não invalida cache de outra

- **WHEN** uma dependência é adicionada ao `pom.xml` de `eventos-consumer`
- **THEN** a próxima execução da esteira de `eventos-consumer` SHALL ter cache miss e reconstruir o
  cache
- **AND** a próxima execução da esteira de `contratocommand` (não alterado) SHALL continuar
  reaproveitando seu cache existente

### Requirement: Check exposto no pull request identifica o job como testes unitários

Cada workflow SHALL expor no pull request um status check cujo job se chama `testes-unitarios`, de
forma que o resultado da esteira seja identificável na lista de checks do PR.

#### Scenario: Check aparece na lista de status do PR

- **WHEN** um pull request dispara a esteira de testes unitários de qualquer uma das 5 apps
- **THEN** o PR SHALL exibir um status check com o job `testes-unitarios`
