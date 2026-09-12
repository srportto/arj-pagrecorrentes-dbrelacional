---
name: project-spec-viva-enumera-classes
description: A spec viva openspec/specs/layout-hexagonal-classico enumera nome e pasta de cada classe das apps — todo move/rename de arquivo exige delta MODIFIED nessa capacidade.
metadata:
  type: project
---

`openspec/specs/layout-hexagonal-classico/spec.md` tem cenários que **listam nominalmente** as
classes de cada pasta (`domain/exception/ contém BusinessException, ApplicationException, ...`;
`infrastructure/persistence/ contém ... ReversibleUUIDv7 ...`), para `contratocommand` e
`contratoquery`.

**Why:** por isso, qualquer change que mova, renomeie ou remova uma classe dessas apps invalida a
spec viva mesmo sem mudar comportamento — foi o que aconteceu na change
`criar-lib-utilitarios-java` (2026-09-12), cujo `proposal.md` declarou "Modified Capabilities:
Nenhuma" enquanto tornava 4 assertivas da spec falsas.

**How to apply:** em auditoria de change que mexa em localização de arquivo Java nas duas apps,
sempre conferir `openspec/specs/layout-hexagonal-classico/spec.md` e exigir delta MODIFIED dessa
capacidade antes do merge. Também checar se `README.md` (seção de estrutura do repo, que o
`CLAUDE.md` raiz declara como fonte da estrutura completa) precisa de atualização quando a change
cria um diretório de topo novo.
