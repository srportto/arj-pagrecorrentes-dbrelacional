# higiene-documentacao-repo Specification

## Purpose

Definir a higiene da documentação do monorepo — o papel único de cada arquivo (`README.md`,
`CLAUDE.md`/`AGENTS.md`, `docs/`), a presença obrigatória desses arquivos em cada app, a
integridade dos links relativos, a forma de referenciar changes OpenSpec sem que o link morra no
arquivamento, e a ausência de arquivo vazio, duplicado ou meramente herdado de boilerplate.

## Requirements

### Requirement: Cada arquivo de documentação tem um papel único

A documentação do repositório SHALL separar três papéis, e cada arquivo SHALL responder a apenas
um deles:

| Arquivo | Público | Responde |
|---|---|---|
| `README.md` de app | pessoa chegando agora | o que o serviço é, como subir, como rodar teste |
| `CLAUDE.md` / `AGENTS.md` | agente de IA | armadilhas, fluxos, invariantes, checklist de commit |
| `docs/` | quem investiga a fundo | POC, modelo de dados, decisões longas |

Um `README.md` de app MUST NOT conter changelog de versões, contrato de request/response de API,
instruções de contribuição herdadas de outro repositório, nem repetir seções inteiras do
`CLAUDE.md` da mesma app.

#### Scenario: README não carrega changelog

- **WHEN** o `README.md` de uma app é lido
- **THEN** não há seção enumerando alterações por versão — esse histórico vive no git

#### Scenario: README não carrega contrato de API

- **WHEN** o `README.md` de uma app REST é lido
- **THEN** ele MAY listar método, caminho e descrição de cada endpoint
- **AND** MUST NOT conter exemplos de corpo de requisição/resposta nem schema de campo

#### Scenario: README não duplica o CLAUDE.md

- **WHEN** uma seção de arquitetura, stack ou estrutura de pastas existe no `CLAUDE.md` de uma app
- **THEN** o `README.md` da mesma app aponta para ela em vez de repeti-la

#### Scenario: README não duplica a si mesmo

- **WHEN** um arquivo de documentação é lido
- **THEN** nenhuma seção aparece duas vezes com conteúdo equivalente

### Requirement: Toda app tem os arquivos de documentação do seu papel

Cada aplicação em `apps/` SHALL possuir `README.md`, `CLAUDE.md` e `AGENTS.md` na sua raiz.
Nenhuma app SHALL depender do `CLAUDE.md` para cumprir o papel do `README.md`.

#### Scenario: As cinco apps têm os três arquivos

- **WHEN** as raízes de `arj-contratocommand`, `arj-contratoquery`, `autorizacaostatus-producer`,
  `eventos-consumer` e `temporiza-autorizacao` são inspecionadas
- **THEN** cada uma contém `README.md`, `CLAUDE.md` e `AGENTS.md`

#### Scenario: Índice da raiz aponta para o arquivo do papel certo

- **WHEN** o `README.md` da raiz lista a documentação de cada app
- **THEN** ele aponta para o `README.md` da app
- **AND** não contorna a ausência de um README apontando para o `CLAUDE.md`

### Requirement: Nenhum link relativo aponta para caminho inexistente

Todo link relativo em arquivo `.md` de `apps/`, `docs/`, `infra/` ou da raiz SHALL resolver para
um caminho existente, tomando como base o diretório do arquivo que o contém.

Link quebrado é defeito de mesma natureza que documentação falsa: quem o segue conclui que a
informação se perdeu, e para de confiar no restante do arquivo.

#### Scenario: Verificação de links no repositório inteiro

- **WHEN** os links relativos de todos os `.md` versionados são resolvidos a partir do diretório
  de cada arquivo
- **THEN** todos apontam para arquivo ou diretório existente

#### Scenario: Verificação não produz falso negativo por locale

- **WHEN** a verificação de links é executada neste ambiente
- **THEN** ela SHALL usar mecanismo que não dependa de `grep -P`, que falha com
  "supports only unibyte and UTF-8 locales" e devolve resultado vazio sem erro visível
- **AND** um resultado vazio SHALL ser confirmado por um caso conhecido antes de ser lido como
  "zero links quebrados"

### Requirement: Referência a change OpenSpec sobrevive ao arquivamento

Documentação de app, de raiz ou de infra MUST NOT linkar para `openspec/changes/<nome>/` por
caminho relativo: esse caminho deixa de existir quando a change é arquivada, e o link morre em
silêncio.

A referência SHALL ser feita por uma destas formas:
- citar o **nome** da change, sem link, quando a intenção é permitir garimpo no histórico;
- linkar a **spec da capacidade** resultante (`openspec/specs/<capacidade>/`), quando a decisão
  virou regra estável.

#### Scenario: Nenhuma doc linka changes por caminho

- **WHEN** os `.md` de `apps/`, `docs/`, `infra/` e da raiz são inspecionados
- **THEN** nenhum contém link relativo para `openspec/changes/<nome>/`

#### Scenario: Decisão que virou regra aponta para a spec

- **WHEN** a documentação precisa justificar um comportamento decidido numa change já arquivada,
  e essa decisão virou requisito de uma capacidade
- **THEN** a referência aponta para `openspec/specs/<capacidade>/`, não para a change

### Requirement: Não há arquivo de documentação vazio ou meramente herdado

O repositório MUST NOT conter arquivo `.md` versionado com conteúdo vazio, nem arquivo de
boilerplate gerado por ferramenta e nunca adaptado ao projeto.

#### Scenario: Arquivo vazio não existe

- **WHEN** os `.md` versionados são inspecionados
- **THEN** nenhum tem tamanho zero

#### Scenario: Boilerplate de gerador é removido

- **WHEN** um arquivo contém apenas o texto gerado pelo Spring Initializr (links para
  documentação do Maven e do Spring Boot, sem conteúdo do projeto)
- **THEN** esse arquivo não existe no repositório

#### Scenario: Conteúdo não é duplicado entre dois caminhos

- **WHEN** o mesmo conteúdo é necessário em dois lugares
- **THEN** existe uma única cópia e as demais posições apontam para ela
