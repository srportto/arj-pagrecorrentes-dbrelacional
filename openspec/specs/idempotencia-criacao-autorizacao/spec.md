# idempotencia-criacao-autorizacao Specification

## Purpose

Definir a idempotência da criação de autorizações no `contratocommand` a partir da chave de
negócio `id_autorizacao_empresa` — escopo e forma da garantia de unicidade no banco, rejeição de
chave já utilizada com `409` e tratamento de corrida entre criações concorrentes.

## Requirements
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

### Requirement: Criação rejeita chave de negócio já utilizada

O `CriarAutorizacaoUseCase` SHALL verificar, antes de persistir, se já existe autorização com o
mesmo `id_autorizacao_empresa`. Havendo, a criação SHALL ser rejeitada com status HTTP
`409 Conflict` e corpo `LayoutErrosApiResponse`, e nenhuma autorização nova SHALL ser criada.

#### Scenario: Retry de POST após timeout não duplica

- **WHEN** um cliente envia o mesmo POST duas vezes com o mesmo `id_autorizacao_empresa`, por
  timeout de rede na primeira tentativa
- **THEN** a primeira SHALL criar a autorização e retornar `201`
- **AND** a segunda SHALL retornar `409`, sem criar segunda autorização

#### Scenario: Nenhum evento duplicado é publicado

- **WHEN** a segunda tentativa é rejeitada por chave já utilizada
- **THEN** nenhum evento `ATIVACAO` adicional SHALL ser publicado no SNS

#### Scenario: Chave inédita cria normalmente

- **WHEN** um POST chega com `id_autorizacao_empresa` ainda não utilizado
- **THEN** a autorização SHALL ser criada e a resposta SHALL ser `201` com header `Location`

### Requirement: Corrida na criação é tratada sem erro interno

A API SHALL traduzir violação de unicidade em resposta `409 Conflict` no formato
`LayoutErrosApiResponse` quando duas requisições concorrentes com a mesma chave de negócio
ultrapassam a verificação da aplicação e a violação é detectada apenas pelo banco. A API NÃO SHALL
responder `500` nem expor o nome da constraint.

#### Scenario: Violação de constraint vira 409

- **WHEN** duas criações concorrentes com a mesma chave passam pela verificação e o banco rejeita
  a segunda
- **THEN** a segunda resposta SHALL ser `409` com corpo estruturado
- **AND** o nome da constraint e a exceção de acesso a dados NÃO SHALL aparecer na resposta

