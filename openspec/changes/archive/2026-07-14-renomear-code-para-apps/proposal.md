## Why

A pasta `code/` foi introduzida na reorganização do monorepo (`reorganizar-monorepo-code-infra`) para separar código de aplicação de código de infraestrutura (`infra/`). Na prática, o nome `code/` passa a sensação de "todo o código do repositório", o que confunde com `infra/` — que também é código (Terraform). `apps/` nomeia o conteúdo pelo que ele é (as aplicações Java) e desambigua a topologia de topo. `infra/` permanece como está: não há a mesma ambiguidade nem motivação para renomeá-la agora.

## What Changes

- **BREAKING** (estrutura de pastas): renomear `code/` → `apps/`. Todos os caminhos em READMEs, docs, configs e na spec `monorepo-organization` passam a apontar para `apps/`.
- Atualizar o requirement "Separação de topo entre código e infraestrutura" em `openspec/specs/monorepo-organization/spec.md` para refletir `apps/` no lugar de `code/`.
- Atualizar referências textuais a `code/` em: `README.md` (raiz), `infra/README.md`, `infra/envs/local/README.md`, `infra/modules/ecs-service/README.md`, `docs/run_postgres16_ja_com_cron_partman/README.md`, `.claude/skills/create-based-aplication-java/SKILL.md`, e os `AGENTS.md`/`CLAUDE.md`/`README.md` de cada aplicação (`contratocommand`, `contratoquery`).
- Mover `code/docker-compose.yml` → `apps/docker-compose.yml` (o conteúdo do arquivo, incluindo os `build.context` relativos, não muda).
- **Fora de escopo**: renomear `infra/`; editar o conteúdo de changes já arquivadas em `openspec/changes/archive/` (são registro histórico de decisões já tomadas e não devem ser reescritas).
- Incluir como tarefa explícita a limpeza do cache do Java Language Server do VS Code (`Java: Clean Java Language Server Workspace`) após o `git mv`, já que o rename anterior (`aplicacoes/` → `code/`) deixou o cache do `redhat.java` (jdt.ls) apontando para caminhos inexistentes e quebrou Run/Debug — problema diagnosticado nesta mesma sessão.

## Capabilities

### New Capabilities
(nenhuma)

### Modified Capabilities
- `monorepo-organization`: o requirement "Separação de topo entre código e infraestrutura" passa a exigir a pasta de topo `apps/` (em vez de `code/`) para código de aplicação; os demais requirements (esqueleto de infra, contêiner por app, profiles Spring, infra local de banco, documentação) permanecem válidos, apenas com os caminhos de exemplo atualizados de `code/` para `apps/`.
- `readme-raiz`: o requirement "README de raiz linka para documentação de cada app" passa a exigir links para `apps/contratocommand/README.md` e `apps/contratoquery/README.md`.
- `documentacao-contratoquery`: o requirement "Aplicação contratoquery deve possuir arquivos de documentação na raiz" passa a referenciar `apps/contratoquery/` em vez de `code/contratoquery/`.
- `higiene-codigo-morto`: a descrição e o requirement "Código de produção não contém classes sem referência de produção" passam a referenciar as aplicações sob `apps/` em vez de `code/`.
- `higiene-comentarios-codigo`: a descrição e o requirement "Comentários de código existem apenas para explicar um porquê não óbvio" passam a referenciar `apps/contratocommand` e `apps/contratoquery` em vez de `code/...`.

> Nota: essas 4 últimas capabilities só foram identificadas durante a implementação (grep por `code/` no repositório inteiro revelou requirements de outras specs que também fixam o caminho antigo como texto normativo). Adicionadas ao escopo por serem a mesma natureza de mudança do `monorepo-organization` — troca de caminho, zero mudança de comportamento.

## Impact

- **Estrutura de diretórios**: `code/` → `apps/` (rename simples, conteúdo de cada aplicação inalterado); `infra/` inalterada.
- **Configuração/Build**: `apps/docker-compose.yml` (antes `code/docker-compose.yml`); comandos `mvn`/Docker passam a rodar a partir de `apps/<app>`.
- **Documentação**: README raiz, READMEs/CLAUDE.md/AGENTS.md das aplicações, READMEs de `infra/` que citam a topologia, e a skill `create-based-aplication-java`.
- **Spec**: `openspec/specs/monorepo-organization/spec.md` atualizada via delta spec desta change.
- **Ambiente local de desenvolvimento (IDE)**: cache do Java Language Server do VS Code precisa ser limpo manualmente após o rename (ação fora do repositório, documentada em `tasks.md`).
- **Comportamento das aplicações**: inalterado — endpoints, portas (8080/8081), dependências e suíte de testes continuam idênticos.
