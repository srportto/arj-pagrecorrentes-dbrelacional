## ADDED Requirements

### Requirement: Grafo de transições aplicado no fluxo de escrita

O grafo de transições exposto por `StatusAutorizacao.podeTransicionarPara` SHALL ser consultado
pelo `arj-contratocommand` antes de persistir qualquer mudança de status de autorização. Uma
transição não permitida pelo grafo SHALL ser rejeitada, e a mudança de status NÃO SHALL ser
persistida nem gerar evento.

Esta exigência complementa o requisito existente "Enum StatusAutorizacao com grafo de transições
nas 4 aplicações", que hoje determina apenas que o grafo **exista** — sem exigir que seja
aplicado. O grafo passa a ser normativo em runtime.

#### Scenario: Cancelamento de autorização ativa é permitido

- **WHEN** um cancelamento é solicitado para autorização com status `ATIVA`
- **THEN** a transição `ATIVA` → `CANCELADA` SHALL ser reconhecida como válida pelo grafo
- **AND** o cancelamento SHALL prosseguir

#### Scenario: Cancelamento de autorização já cancelada é rejeitado

- **WHEN** um cancelamento é solicitado para autorização com status `CANCELADA`
- **THEN** a requisição SHALL ser rejeitada com erro de regra de negócio
- **AND** os dados de cancelamento existentes NÃO SHALL ser sobrescritos
- **AND** nenhum evento `CANCELAMENTO` SHALL ser publicado

#### Scenario: Cancelamento a partir de qualquer estado terminal é rejeitado

- **WHEN** um cancelamento é solicitado para autorização com status `REJEITADA`, `EXPIRADA` ou
  `FINALIZADA`
- **THEN** a requisição SHALL ser rejeitada com erro de regra de negócio, pois nenhum desses
  estados admite transição

#### Scenario: Validação de transição roda como rule do validador

- **WHEN** o `CancelamentoValidator` do `arj-contratocommand` é inspecionado
- **THEN** ele SHALL incluir uma rule que consulta `podeTransicionarPara`, seguindo o mesmo padrão
  das demais rules de cancelamento

#### Scenario: Método deixa de ser código sem uso em produção

- **WHEN** as referências a `podeTransicionarPara` no `arj-contratocommand` são inspecionadas
- **THEN** SHALL existir ao menos uma chamada em código de produção, além das chamadas em teste
