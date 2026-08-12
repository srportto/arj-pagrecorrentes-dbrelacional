## Why

A documentação do repositório cresceu por acréscimo e nunca foi podada. O estado medido em
2026-08-10:

| Onde | Volume | Problema |
|---|---|---|
| `apps/**/*.md` | 3.987 linhas | `README.md` do command sozinho tem **885** |
| `docs/**/*.md` | 3.098 linhas | 1 arquivo vazio, 1 stub de redirecionamento |
| `infra/**/README.md` | 624 linhas | 14 arquivos, granularidade desigual |
| Links relativos quebrados | **26** | em 8 arquivos diferentes |

Três defeitos concretos, não questão de gosto:

**1. Documentação que descreve o que não existe.** O `README.md` do `arj-contratocommand` termina
com uma seção de 20 linhas — "⚠️ Notas Importantes Sobre Java 25 Preview Features" — ensinando a
compilar com `--add-modules=jdk.incubator.vector` e `--enable-preview`. **Nenhum `pom.xml` do
monorepo declara preview features**, e o comando que ela ensina
(`javac ... -jar target/contratocommand.jar`) nem é sintaxe válida. Isso viola diretamente a
capacidade `documentacao-fiel-ao-codigo`, que trata documentação falsa como defeito: ela suprime
a desconfiança de quem a lê justamente para descobrir armadilhas.

**2. Duas specs em contradição sobre a mesma regra.** A spec `contrato-api-consistente` exige
**400** para violação de Bean Validation e tem um cenário explícito nesse sentido. O `CLAUDE.md`
raiz registra a decisão D3 de 2026-08-09 fixando **422** para os dois casos, e é o 422 que está
implementado. A capacidade `documentacao-fiel-ao-codigo` proíbe exatamente isso — "duas
capacidades NÃO SHALL especificar valores conflitantes para o mesmo comportamento".

**3. Links que morrem em silêncio.** Dos 26 quebrados, 6 apontam para `design.md` de changes já
arquivadas. Não é descuido pontual: é uma classe de defeito que **reincide a cada `openspec
archive`**, porque os `CLAUDE.md` das apps linkam design de change pelo caminho `changes/`, que
deixa de existir.

E há duplicação bruta: `apps/arj-contratocommand/docs/info_build-my-image-and-execute.md` é um
arquivo de **0 bytes** cujo conteúdo real (85 linhas sobre build da imagem PostgreSQL 18) vive em
`docs/info_build-my-image-and-execute.md`. O `README.md` do command repete a seção de testes duas
vezes (linhas 228-246 e 624-700) e a de padrões de design duas vezes (134-144 e 741-751).

## Dependência de ordem

**Esta change não pode começar antes da fase 1 de `limpar-codigo-das-apps`.**

Descoberto em 2026-08-10: as seções de API que esta change apaga —
`apps/arj-contratocommand/README.md` linhas 268-380 e `apps/arj-contratoquery/README.md` linhas
181-264 — são **duas das três fontes do contrato de API** do repositório, e o gateway ainda não o
tem. A fase 1 daquela change consolida as três em `docs/contrato-api-para-gateway.md`; só depois
disso as seções podem ser removidas com segurança.

A dependência é estreita: só a fase 1 precede esta change, não a change inteira. Ver D1b em
`limpar-codigo-das-apps/design.md`.

## What Changes

**Podar o `README.md` do `arj-contratocommand`** — de 885 para ~150 linhas, cortando:
- a seção de Java 25 preview features (fictícia);
- o changelog "Alterações Recentes v0.0.1 (maio 2026)" (~135 linhas), que é `git log`;
- a documentação de request/response da API (~110 linhas), que vai para o gateway;
- as duas seções duplicadas (testes, padrões de design);
- "Contribuindo / Licença / Suporte", herdadas de um repositório forkado — instruem `git clone`
  de `github.com/your-username/contratocommand.git`.

**Definir o papel de cada arquivo**, para que a poda não se desfaça:
```
README.md   → para humanos: o que é, como subir, como rodar teste
CLAUDE.md   → para agentes: armadilhas, fluxos, checklist  (espelho: AGENTS.md)
docs/       → material que não cabe em nenhum dos dois (POC, modelo de dados)
```
Hoje os três se sobrepõem sem regra.

**Corrigir os 26 links quebrados**, atacando a causa por grupo, não arquivo a arquivo.

**Criar `apps/temporiza-autorizacao/README.md`** — é a única das 5 apps sem README, e o
`README.md` raiz já linka para ele (link quebrado nº 26).

**Remover redundância morta**: o arquivo de 0 bytes, e `HELP.md` (16 linhas de boilerplate do
Spring Initializr, links para docs do Maven).

**Resolver a contradição 400 vs 422** na spec `contrato-api-consistente`, alinhando-a à decisão
D3 já implementada.

## Capabilities

### New Capabilities

- `higiene-documentacao-repo`: papel de cada arquivo de documentação, proibição de link relativo
  quebrado, proibição de conteúdo duplicado entre arquivos, e a regra de como referenciar change
  OpenSpec sem que o link morra no arquivamento.

### Modified Capabilities

- `documentacao-fiel-ao-codigo`: ganha requisito de que afirmação sobre o **build** (flags de
  compilação, preview features, plugins) corresponda ao `pom.xml`. Hoje a spec cobre
  infraestrutura, versões e componentes, mas não o build.
- `contrato-api-consistente`: corrigir o requisito de status HTTP de 400 para 422 em violação de
  Bean Validation, alinhando à decisão D3 de 2026-08-09 e ao código.

## Impact

**Documentação de apps**
- `apps/arj-contratocommand/README.md` — 885 → ~150 linhas
- `apps/arj-contratoquery/README.md` — 356 linhas, revisão de sobreposição com `CLAUDE.md`
- `apps/temporiza-autorizacao/README.md` — **criar**
- Os 5 pares `CLAUDE.md`/`AGENTS.md` — hoje idênticos (verificado); qualquer edição SHALL ser
  replicada nos dois

**Remoções**
- `apps/arj-contratocommand/HELP.md`
- `apps/arj-contratocommand/docs/info_build-my-image-and-execute.md` (0 bytes) e o diretório
  `docs/` da app, se ficar vazio

**Raiz, docs/ e infra/**
- `README.md` raiz — 4 links quebrados
- `docs/arquitetura/modelo-dados-e-dados-poc-testada-para-essa-implementacao.md` — 8 links
- `docs/arquitetura/based-java-aplication.md`, `docs/floci-aws-local/floci-aws-local.md`
- Os 14 `README.md` de `infra/` — revisão de consistência

**Specs**
- `openspec/specs/contrato-api-consistente/spec.md` — correção 400 → 422

**Fora de escopo**
- Remover as anotações springdoc do código — é a change `limpar-codigo-das-apps`.
- Reorganizar os arquivos de compose — é a change `unificar-orquestracao-docker-local`.
- Unificar `CLAUDE.md` e `AGENTS.md` num arquivo só. A duplicação é deliberada (ferramentas
  diferentes leem nomes diferentes) e está protegida por `documentacao-fiel-ao-codigo`.
