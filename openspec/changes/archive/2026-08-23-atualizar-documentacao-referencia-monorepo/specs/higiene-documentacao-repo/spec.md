## MODIFIED Requirements

### Requirement: Toda app tem os arquivos de documentação do seu papel

Cada aplicação em `apps/` SHALL possuir `README.md`, `CLAUDE.md` e `AGENTS.md` na sua raiz,
independentemente de linguagem, framework ou modelo de execução (serviço de vida longa ou função
invocada sob demanda). Nenhuma app SHALL depender do `CLAUDE.md` para cumprir o papel do
`README.md`.

#### Scenario: Todas as apps existentes têm os três arquivos

- **WHEN** as raízes de todas as aplicações em `apps/` são inspecionadas (hoje: `contratocommand`,
  `contratoquery`, `autorizacaostatus-producer`, `eventos-consumer`, `temporiza-autorizacao` e
  `expurgo-particao`)
- **THEN** cada uma contém `README.md`, `CLAUDE.md` e `AGENTS.md`

#### Scenario: App nova exige os três arquivos desde a primeira change que a introduz

- **WHEN** uma aplicação nova é adicionada em `apps/`, de qualquer linguagem ou modelo de execução
- **THEN** a change que a introduz, ou uma change subsequente antes da próxima revisão de higiene de
  documentação, SHALL criar `README.md`, `CLAUDE.md` e `AGENTS.md` para ela

#### Scenario: Índice da raiz aponta para o arquivo do papel certo

- **WHEN** o `README.md` da raiz lista a documentação de cada app
- **THEN** ele aponta para o `README.md` da app
- **AND** não contorna a ausência de um README apontando para o `CLAUDE.md`
