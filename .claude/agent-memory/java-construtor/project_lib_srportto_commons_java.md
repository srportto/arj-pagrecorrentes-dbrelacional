---
name: project_lib_srportto_commons_java
description: Existencia e escopo da lib compartilhada libs/srportto-commons-java (change criar-lib-utilitarios-java)
metadata:
  type: project
---

Criada em 2026-09-12 a lib Java pura `libs/srportto-commons-java` (change OpenSpec
`criar-lib-utilitarios-java`), hospedando `ReversibleUUIDv7`, `BusinessException` e
`ApplicationException`, antes duplicadas entre `contratocommand` e `contratoquery`. Publicação via
GitHub Packages (workflow `ci-publish-srportto-commons-java.yml`), versão fixa (`1.0.0`), consumida
pelas duas apps com `<repositories>`+`<dependency>` no `pom.xml`.

**Por quê:** as 3 classes não carregam regra de negócio nem contrato de rede — diferente do resto
do monorepo, que espelha contratos/enums de máquina de estados à mão de propósito (para não
acoplar deploy de serviços com ritmos diferentes). Critério de elegibilidade de classe para essa
lib está documentado em `libs/srportto-commons-java/CLAUDE.md` e no `CLAUDE.md` raiz.

**Pendências no momento da criação** (ambiente de execução sem acesso real ao GitHub Packages):
publicação efetiva da v1.0.0 no GitHub Packages e confirmação do CI das apps resolvendo a
dependência num runner limpo — ambos dependem do commit chegar ao GitHub Actions. Localmente, a
lib foi validada com `mvn install` no repositório Maven local.

**Como aplicar:** se pedirem para adicionar uma classe nova a essa lib, sempre confira as 3
condições de elegibilidade antes (não regra de negócio, não contrato de rede, já duplicada em
2+ apps) — não é lugar para `AutorizacaoEventoPayload`, `.avsc` ou enums de máquina de estados.
