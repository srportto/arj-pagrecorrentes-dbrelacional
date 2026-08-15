## MODIFIED Requirements

### Requirement: Unicidade da chave de negócio no banco

A coluna `id_autorizacao_empresa` da tabela `autorizacoes` SHALL ter unicidade garantida no banco
de dados **para autorizações ativas**, de modo que duas autorizações ativas da mesma conta
contratante com o mesmo valor NÃO possam coexistir. A garantia SHALL residir no banco, não
apenas na aplicação — a unicidade deve valer também para escritas que não passem pelo
`contratocommand`.

O escopo da garantia é deliberadamente limitado às **partições quentes** (`0..888`), onde
`id_particao_conta` é o hash da conta contratante e portanto a unicidade por partição equivale
à unicidade por conta. Autorizações em estado terminal, já transferidas para a partição de
expurgo (`900..999`), SHALL NOT estar sujeitas a essa unicidade: são linhas que existem apenas
até o próximo `DROP PARTITION`, e nas partições de expurgo `id_particao_conta` representa o
balde semanal — não a conta —, de modo que impor a chave ali produziria colisão entre
autorizações de contas distintas que compartilhem o mesmo `id_autorizacao_empresa`.

Em consequência, a transferência de uma autorização para a partição de expurgo SHALL NOT falhar
por colisão de `id_autorizacao_empresa`, com autorização de qualquer conta.

A garantia contra duplicata física exata na faixa de expurgo permanece a cargo da chave
primária `(id_autorizacao, id_particao_conta)`.

#### Scenario: Segunda inserção com a mesma chave é rejeitada pelo banco

- **WHEN** uma segunda linha com `id_autorizacao_empresa` já existente **em partição quente**
  é inserida diretamente no banco, fora do caminho da aplicação
- **THEN** o banco SHALL rejeitar a inserção por violação de unicidade

#### Scenario: Chave repetida entre contas distintas não impede o expurgo

- **WHEN** duas autorizações de **contas contratantes diferentes** compartilham o mesmo
  `id_autorizacao_empresa` e ambas chegam a estado terminal dentro do mesmo balde semanal de
  expurgo
- **THEN** as duas transferências SHALL ser concluídas com sucesso
- **AND** as duas linhas SHALL coexistir na mesma partição de expurgo
- **AND** nenhuma das operações SHALL responder `409`

#### Scenario: Unicidade não se aplica à faixa de expurgo

- **WHEN** duas linhas com a mesma conta contratante e o mesmo `id_autorizacao_empresa`, mas
  `id_autorizacao` distintos, residem na mesma partição de expurgo
- **THEN** o banco SHALL aceitá-las — consequência deliberada de a unicidade ser regra sobre
  autorizações ativas, e não invariante da tabela

#### Scenario: Declaração na entidade não promete o que o banco não impõe

- **WHEN** o mapeamento de `id_autorizacao_empresa` na entidade `Autorizacao` é inspecionado
- **THEN** ele NÃO SHALL declarar uma `@UniqueConstraint` que o banco não possui — a garantia
  passa a ser um índice único **parcial**, forma que JPA não é capaz de expressar
- **AND** a limitação SHALL estar registrada em comentário, apontando a migration que cria o
  índice

#### Scenario: Garantia de idempotência da criação permanece inalterada

- **WHEN** um `POST /api/autorizacoes` repete o `id_autorizacao_empresa` de uma autorização
  ativa da mesma conta contratante
- **THEN** a criação SHALL continuar sendo rejeitada com `409`, exatamente como antes deste
  ajuste
- **AND** a verificação prévia do `CriarAutorizacaoUseCase`, que consulta apenas a partição
  quente da conta, SHALL permanecer coerente com o novo escopo da garantia
