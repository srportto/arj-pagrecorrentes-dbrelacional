## 1. Rename da pasta

- [x] 1.1 `git mv code apps` na raiz do repositório (preserva histórico por arquivo)
- [x] 1.2 Confirmar que `apps/contratocommand/` e `apps/contratoquery/` existem e que `code/` não existe mais

## 2. Atualizar documentação e configuração

- [x] 2.1 Atualizar `README.md` (raiz) — árvore de diretórios e comandos que referenciam `code/`
- [x] 2.2 Atualizar `infra/README.md`, `infra/envs/local/README.md`, `infra/modules/ecs-service/README.md` — referências a `code/`
- [x] 2.3 Atualizar `apps/contratocommand/{AGENTS.md,CLAUDE.md,README.md}` — caminhos próprios e referências a `apps/docker-compose.yml`
- [x] 2.4 Atualizar `apps/contratoquery/{AGENTS.md,CLAUDE.md,README.md}` — caminhos próprios e referências a `apps/docker-compose.yml`
- [x] 2.5 Atualizar `docs/run_postgres16_ja_com_cron_partman/README.md` — referências a `code/`
- [x] 2.6 Atualizar `.claude/skills/create-based-aplication-java/SKILL.md` — referências a `code/`
- [x] 2.7 Conferir `apps/docker-compose.yml` (arquivo já migrado pelo `git mv`) — confirmar que `build.context` de cada serviço continua correto (`./contratocommand`, `./contratoquery`, `../infra/local/postgres`)

## 3. Atualizar spec

- [x] 3.1 Aplicar o delta spec desta change em `openspec/specs/monorepo-organization/spec.md` (via `openspec archive` ao final, ou merge manual dos requirements MODIFIED)
- [x] 3.2 (descoberto durante a implementação) Aplicar deltas equivalentes em `readme-raiz`, `documentacao-contratoquery`, `higiene-codigo-morto` e `higiene-comentarios-codigo` — specs vivas que também fixavam `code/` como caminho normativo em requirements; proposal.md atualizada com as 4 capabilities adicionais

## 4. Verificação

- [x] 4.1 Buscar por `code/` em todo o repositório (exceto `openspec/changes/archive/**` e `**/target/**`) e confirmar que não sobrou referência não intencional — únicos hits restantes são intencionais (artefatos desta própria change narrando o rename, `openspec/specs/monorepo-organization/spec.md` descrevendo o estado antigo no cenário, changes arquivadas, e `.vscode/` como falso-positivo em `.gitignore`)
- [x] 4.2 Rodar `mvn test` em `apps/contratocommand` e `apps/contratoquery` — suíte deve passar sem alteração de comportamento (94 + 40 testes, `BUILD SUCCESS` nos dois módulos)
- [x] 4.3 Rodar `mvn spring-boot:run` (ou equivalente) a partir de `apps/contratocommand` com as variáveis do `.env` exportadas, e confirmar que a aplicação inicia e conecta ao Postgres local ("Started ContratocommandApplication", HikariPool conectado em `jdbc:postgresql://localhost:5432/db-csp-postgres`)

## 5. Ambiente local (VS Code)

- [x] 5.1 No VS Code, rodar o comando `Java: Clean Java Language Server Workspace` (paleta de comandos) após o rename
- [x] 5.2 Aguardar a reindexação do Java Language Server (notificação de progresso "Building workspace")
- [x] 5.3 Confirmar que Run/Debug (`F5` ou botão acima do `main`) funciona para `ContratocommandApplication` e `ContratoqueryApplication`
