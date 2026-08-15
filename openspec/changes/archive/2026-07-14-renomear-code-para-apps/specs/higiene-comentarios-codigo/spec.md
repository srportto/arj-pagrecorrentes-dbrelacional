## MODIFIED Requirements

### Requirement: Comentários de código existem apenas para explicar um porquê não óbvio

O código Java em `apps/contratocommand` e `apps/contratoquery` SHALL conter comentários apenas quando eles explicam uma decisão de design, uma restrição não óbvia ou decodificam um valor de negócio que o código sozinho não deixa claro (ex.: o significado de um indicador numérico, a aritmética de uma partição, um gotcha de biblioteca). Comentários que apenas reafirmam em português o que a linha de código seguinte já expressa (WHAT redundante) MUST NOT existir. Banners puramente decorativos MUST NOT existir.

#### Scenario: Comentário decodificando valor de negócio é preservado

- **WHEN** um campo ou variável representa um código ou indicador cujo significado não é óbvio pelo nome/tipo (ex.: `indicadorUsoLimiteConta`, a "gaveta" de partição em `ControleExpurgoAutorizacao`)
- **THEN** o comentário que explica os valores possíveis permanece no código

#### Scenario: Comentário que reafirma a linha seguinte é removido

- **WHEN** um comentário apenas descreve em português o que a chamada de método imediatamente abaixo já deixa claro pelo nome (ex.: `// Delete do banco com a chave antiga` sobre `repository.deleteById(...)`)
- **THEN** esse comentário não existe no código

#### Scenario: Banner decorativo é removido

- **WHEN** uma classe é precedida por um comentário puramente decorativo (linhas de `---` ou símbolos delimitadores sem conteúdo informativo além do óbvio)
- **THEN** esse banner não existe no código
