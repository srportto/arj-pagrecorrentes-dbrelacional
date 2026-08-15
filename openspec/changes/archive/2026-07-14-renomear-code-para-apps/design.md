## Context

O monorepo organiza-se hoje em `code/` (aplicações Java) e `infra/` (esqueleto de infraestrutura). `code/` foi escolhido na change `reorganizar-monorepo-code-infra`, concluída nesta mesma data, como nome genérico em inglês para "código de aplicação". Na prática o nome não comunica bem o conteúdo: ao lado de `infra/` — que também é código (Terraform) — `code/` dá a impressão de abranger todo o código do repositório. Esta change troca `code/` por `apps/`, que nomeia o conteúdo pelo que ele é (as aplicações). `infra/` não muda: não há a mesma ambiguidade e não há necessidade de alterá-la agora.

Esta é a segunda rename de pasta de topo do dia (antes: `aplicacoes/` → `code/`). A primeira já revelou um efeito colateral fora do repositório: o cache do Java Language Server do VS Code (`redhat.java`, workspace `jdt_ws`) manteve referências ao caminho antigo e passou a falhar ao compilar/rodar/debugar (`NoSuchFileException` apontando para o caminho removido), exigindo `Java: Clean Java Language Server Workspace` para reconstruir o cache. Esse efeito é reproduzível e deve se repetir aqui.

## Goals / Non-Goals

**Goals:**
- Renomear `code/` → `apps/` preservando o conteúdo e comportamento de cada aplicação.
- Atualizar a spec `monorepo-organization` e toda a documentação/configuração que referencia `code/` como caminho.
- Documentar explicitamente o passo de limpeza do cache do VS Code como parte do plano, não como descoberta pós-quebra.

**Non-Goals:**
- Renomear `infra/` (fora de escopo desta change).
- Alterar comportamento de aplicação, endpoints, portas ou dependências.
- Reescrever o histórico de changes já arquivadas (`openspec/changes/archive/**`), que documentam decisões já tomadas com os nomes vigentes à época.
- Introduzir qualquer convenção de tooling específica (Nx/Turborepo) além do rename de pasta.

## Decisions

- **`git mv code apps`** em vez de recriar a estrutura manualmente: preserva o histórico de cada arquivo como rename no Git (mesma abordagem usada em `aplicacoes/` → `code/`).
- **`infra/` permanece inalterada**: a ambiguidade motivadora ("code dá sensação de código de tudo") é específica do nome `code/`; `infra/` já é um nome específico e não sofre do mesmo problema.
- **Não editar `openspec/changes/archive/2026-07-14-reorganizar-monorepo-code-infra/`**: artefatos de change arquivada são snapshot histórico de uma decisão já tomada e concluída; alterá-los reescreveria história em vez de documentar a evolução. A spec viva (`openspec/specs/monorepo-organization/spec.md`) é a única fonte de verdade atualizada por esta change.
- **Passo explícito de limpeza de cache do IDE em `tasks.md`**: como o efeito já foi observado e diagnosticado nesta sessão para o rename anterior, incluir a ação preventivamente evita redescobrir o mesmo sintoma (Run/Debug quebrado, `NoSuchFileException` no log do jdt.ls) por tentativa e erro.

## Risks / Trade-offs

- [Cache do Java Language Server do VS Code (`redhat.java`) fica com caminhos antigos após o rename, quebrando Run/Debug] → Mitigação: passo explícito em `tasks.md` para rodar `Java: Clean Java Language Server Workspace` após o `git mv`; documentado também no README/CLAUDE.md como armadilha conhecida de rename de pastas com código Java.
- [Alguma referência textual a `code/` passar despercebida fora da lista mapeada] → Mitigação: `tasks.md` inclui um passo de busca (`grep`/equivalente) por `code/` no repositório inteiro (exceto `openspec/changes/archive/**` e `target/`) como verificação final antes de considerar a change completa.
- [Quem tiver o repositório aberto em outra janela/branch durante o rename pode ter conflitos de merge triviais] → Mitigação: risco baixo e aceito; não é uma operação coordenada entre múltiplos colaboradores neste momento do projeto.

## Migration Plan

1. `git mv code apps` (preserva histórico por arquivo).
2. Atualizar `apps/docker-compose.yml` (nome do arquivo já migra junto; conteúdo interno não muda, pois os `build.context` são relativos ao próprio arquivo).
3. Atualizar as referências textuais listadas na proposta (README raiz, READMEs/CLAUDE.md/AGENTS.md das aplicações, READMEs de `infra/`, doc do Postgres local, skill `create-based-aplication-java`).
4. Atualizar `openspec/specs/monorepo-organization/spec.md` via delta desta change.
5. Rodar `mvn test` em `apps/contratocommand` e `apps/contratoquery` para confirmar que nada quebrou funcionalmente.
6. Limpar o cache do Java Language Server do VS Code (`Java: Clean Java Language Server Workspace`) e confirmar que Run/Debug voltam a funcionar.

Não há rollback especial: reverter é o `git mv` inverso (`apps` → `code`) mais reverter o commit da documentação/spec, seguido da mesma limpeza de cache do IDE.

## Open Questions

(nenhuma — escopo e exclusões já validados com o usuário durante a exploração desta change)
