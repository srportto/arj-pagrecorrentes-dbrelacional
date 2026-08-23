## Why

Entre 22/08/2026 e hoje, o monorepo ganhou uma sexta aplicação — `apps/expurgo-particao`
(Python, Lambda agendada que fecha o ciclo do ring buffer de expurgo, change já arquivada
`reclamar-particao-expurgo-ciclo`) — e passou por ajustes no ambiente PostgreSQL local (receita de
extensões documentada, versão do `pgvector` fixada, changes `documentar-postgres-local-extensoes`
e `fixar-versao-pgvector`). Nenhuma dessas mudanças chegou à documentação de referência:

- `apps/expurgo-particao/` não tem `README.md`, `CLAUDE.md` nem `AGENTS.md` — viola a própria regra
  do repositório de que toda app em `apps/` possui os três (`higiene-documentacao-repo`), que hoje
  fala apenas em "cinco apps".
- O `README.md` da raiz descreve o sistema como "cinco microserviços Java" e não menciona
  `expurgo-particao` em lugar nenhum — nem no fluxograma, nem na tabela de serviços, nem na
  estrutura de pastas.
- O `AGENTS.md` da raiz está **dessincronizado** do `CLAUDE.md` da raiz: falta o parágrafo inteiro
  sobre o ring buffer de expurgo (escritor/reclamador em apps diferentes) que o `CLAUDE.md` já tem
  — quebra a convenção de espelho que o próprio `CLAUDE.md` declara ("`CLAUDE.md` e `AGENTS.md` são
  espelhos — mantenha-os idênticos").
- `infra/README.md` não cita o módulo `infra/modules/lambda-scheduled/` nem o repositório ECR da
  Lambda.
- A tabela "Documentação" do `README.md` de raiz nunca linkou `infra/local/postgres/README.md` (nem
  antes de hoje, nem depois da nova seção "Extensões" escrita lá) — é o único guia de infraestrutura
  local hoje sem entrada nessa tabela.

O sintoma comum: documentação de referência que descreve o estado de alguns dias atrás, não o
estado atual. Cada item acima é verificável por inspeção direta do repositório, não é interpretação.

## What Changes

- **Criar `apps/expurgo-particao/README.md`, `CLAUDE.md` e `AGENTS.md`**, seguindo o mesmo papel dos
  três arquivos nas outras cinco apps (`README.md` para quem chega agora; `CLAUDE.md`/`AGENTS.md`
  como espelhos, para agente de IA — armadilhas, fluxo, invariantes).
- **Atualizar `README.md` da raiz**: incluir `expurgo-particao` no fluxograma principal (ou em
  diagrama próprio, dado que ela não participa do fluxo síncrono de request), na tabela de
  microserviços (ou tabela irmã, já que não é um serviço com porta HTTP), na estrutura de pastas, e
  ajustar a frase de abertura que hoje fixa "cinco microserviços". Adicionar `infra/local/postgres`
  e `apps/expurgo-particao` à tabela "Documentação".
- **Sincronizar `AGENTS.md` da raiz com `CLAUDE.md` da raiz** — copiar o parágrafo faltante sobre o
  ring buffer de expurgo, restaurando a garantia de espelho.
- **Atualizar `infra/README.md`** para citar `infra/modules/lambda-scheduled/` e o papel do ECR da
  Lambda, no mesmo padrão em que os módulos das cinco apps Java já são descritos.
- **Atualizar a spec `higiene-documentacao-repo`**: o requisito "Toda app tem os arquivos de
  documentação do seu papel" e seu cenário passam a falar em **seis** apps, não cinco, e a passar a
  incluir `expurgo-particao` na lista nomeada.
- **Atualizar a spec `readme-raiz`**: o requisito de link para documentação de app está desatualizado
  desde antes desta change (cita só `contratocommand`/`contratoquery`); passa a exigir link para
  **todas** as apps de `apps/` e para os guias de ambiente local relevantes em `infra/local/`, em
  vez de nomear um subconjunto fixo que já ficou obsoleto uma vez.

Fora de escopo: qualquer mudança de comportamento em `apps/expurgo-particao` ou em qualquer outra
app; revisão de documentação não tocada pelas mudanças de 22/08 em diante; `docs/arquitetura/*`, que
já foi corrigido pela própria change `reclamar-particao-expurgo-ciclo`.

## Capabilities

### New Capabilities

(nenhuma)

### Modified Capabilities

- `higiene-documentacao-repo`: o requisito de presença obrigatória de `README.md`/`CLAUDE.md`/
  `AGENTS.md` por app passa a cobrir as seis apps de `apps/`, não cinco.
- `readme-raiz`: o requisito de link para documentação de app deixa de nomear um subconjunto fixo de
  apps e passa a exigir cobertura de todas as apps existentes em `apps/`.

## Impact

- **Documentação nova**: `apps/expurgo-particao/README.md`, `apps/expurgo-particao/CLAUDE.md`,
  `apps/expurgo-particao/AGENTS.md`.
- **Documentação alterada**: `README.md` (raiz), `AGENTS.md` (raiz), `infra/README.md`.
- **Nenhum arquivo executável é alterado.** Esta change é documentação pura — nenhum `.py`, `.java`,
  `.tf` ou compose muda.
- **Specs alteradas**: `openspec/specs/higiene-documentacao-repo/spec.md`,
  `openspec/specs/readme-raiz/spec.md` (requisitos existentes ficam mais precisos; nenhum
  comportamento novo é introduzido).
