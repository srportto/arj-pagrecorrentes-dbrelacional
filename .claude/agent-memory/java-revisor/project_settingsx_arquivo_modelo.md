---
name: project-settingsx-arquivo-modelo
description: .claude/settingsX.json é arquivo-modelo intencional (untracked) de agent padrão da sessão — não é lixo de change nem achado de auditoria.
metadata:
  type: project
---

`.claude/settingsX.json` é um **modelo** deixado de propósito no working tree (untracked, fora de
qualquer change): contém um comentário instruindo a renomear para `settings.json` caso se queira
fixar `"agent": "java-construtor"` como agent padrão da sessão neste projeto.

**Why:** em auditorias da change `criar-lib-utilitarios-java` (2026-09-12) ele aparecia no
`git status` como `??` junto com artefatos da change, dando a impressão de arquivo espúrio; o
`.claude/settings.json` de verdade é versionado e já contém o mesmo conteúdo.

**How to apply:** em auditoria, não reportar `settingsX.json` como achado nem pedir remoção —
só confirmar que ele continua untracked e que `settings.json` não foi deletado/alterado pela
change. Relacionado: [[project-spec-viva-enumera-classes]].
