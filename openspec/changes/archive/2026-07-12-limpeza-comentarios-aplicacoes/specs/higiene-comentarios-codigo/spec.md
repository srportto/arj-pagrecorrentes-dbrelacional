## ADDED Requirements

### Requirement: Comentários de código existem apenas para explicar um porquê não óbvio

O código Java em `aplicacoes/arj-contratocommand` e `aplicacoes/arj-contratoquery` SHALL conter comentários apenas quando eles explicam uma decisão de design, uma restrição não óbvia ou decodificam um valor de negócio que o código sozinho não deixa claro (ex.: o significado de um indicador numérico, a aritmética de uma partição, um gotcha de biblioteca). Comentários que apenas reafirmam em português o que a linha de código seguinte já expressa (WHAT redundante) MUST NOT existir. Banners puramente decorativos MUST NOT existir.

#### Scenario: Comentário decodificando valor de negócio é preservado

- **WHEN** um campo ou variável representa um código ou indicador cujo significado não é óbvio pelo nome/tipo (ex.: `indicadorUsoLimiteConta`, a "gaveta" de partição em `ControleExpurgoAutorizacao`)
- **THEN** o comentário que explica os valores possíveis permanece no código

#### Scenario: Comentário que reafirma a linha seguinte é removido

- **WHEN** um comentário apenas descreve em português o que a chamada de método imediatamente abaixo já deixa claro pelo nome (ex.: `// Delete do banco com a chave antiga` sobre `repository.deleteById(...)`)
- **THEN** esse comentário não existe no código

#### Scenario: Banner decorativo é removido

- **WHEN** uma classe é precedida por um comentário puramente decorativo (linhas de `---` ou símbolos delimitadores sem conteúdo informativo além do óbvio)
- **THEN** esse banner não existe no código

### Requirement: Comentários não descrevem código ou conceito que não existe mais

Nenhum comentário SHALL referenciar uma classe, padrão ou conceito removido do código-fonte (ex.: "strategies" após a remoção do Strategy Pattern). Comentários stale MUST ser corrigidos ou removidos ao serem identificados.

#### Scenario: Javadoc referenciando conceito removido é corrigido

- **WHEN** um javadoc menciona um padrão ou classe que não existe mais no código atual (ex.: "strategies" em `AutorizacaoRepository`, removidas no refactor de coesão)
- **THEN** o javadoc é atualizado para refletir a arquitetura atual (rules), sem mencionar o conceito removido

### Requirement: Código morto não é mantido disfarçado de comentário explicativo

Blocos de código comentado (código Java inativo dentro de `//` ou `/* */`) MUST NOT permanecer no corpo de uma classe como forma de documentação. Quando há intenção deliberada de registrar um trabalho futuro (ex.: uma migração pendente por limitação de ferramenta externa), a referência SHALL ser um comentário de uma linha (`// TODO: ...`), não um bloco de código inativo.

#### Scenario: Bloco de código morto vira TODO de uma linha

- **WHEN** existe um bloco de código Java comentado dentro do corpo de uma classe, mantido como referência de uma migração futura (ex.: a variante `void main()` do Java 25, pendente de suporte do maven plugin)
- **THEN** o bloco é substituído por um único comentário `// TODO` que descreve a migração pendente e sua causa, sem código Java inativo

#### Scenario: Cálculo morto sem relação com nenhum TODO é removido

- **WHEN** existem linhas de código comentado que não fazem parte do valor retornado pelo método nem documentam uma migração pendente (ex.: variáveis de cálculo intermediário nunca usadas em `ControleExpurgoAutorizacao.obterParticaoExpurgoDrop()`)
- **THEN** essas linhas são removidas, sem substituição

### Requirement: Documentação de módulo não afirma comportamento do entrypoint divergente do código real

Os arquivos `CLAUDE.md`/`AGENTS.md` de `arj-contratocommand` e `arj-contratoquery` SHALL descrever a forma real do método `main()` de cada aplicação. Caso o código utilize `public static void main(String[] args)` enquanto uma variante mais nova (`void main()`) está pendente de suporte de ferramentas externas, a documentação MUST afirmar a forma atualmente em uso e MAY mencionar a migração pendente, mas MUST NOT afirmar que a variante pendente já está em uso.

#### Scenario: CLAUDE.md descreve a forma real do main()

- **WHEN** o `CLAUDE.md` ou `AGENTS.md` de um dos módulos é lido
- **THEN** a seção de pré-requisitos descreve `public static void main()` como a forma em uso, não `void main()`
