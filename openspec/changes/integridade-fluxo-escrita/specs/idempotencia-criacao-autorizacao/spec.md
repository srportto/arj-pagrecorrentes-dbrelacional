## ADDED Requirements

### Requirement: Unicidade da chave de negócio no banco

A coluna `id_autorizacao_empresa` da tabela `autorizacoes` SHALL ter unicidade garantida no banco
de dados, de modo que duas autorizações com o mesmo valor NÃO possam coexistir. A garantia SHALL
residir no banco, não apenas na aplicação — a unicidade deve valer também para escritas que não
passem pelo `arj-contratocommand`.

#### Scenario: Segunda inserção com a mesma chave é rejeitada pelo banco

- **WHEN** uma segunda linha com `id_autorizacao_empresa` já existente é inserida diretamente no
  banco, fora do caminho da aplicação
- **THEN** o banco SHALL rejeitar a inserção por violação de unicidade

#### Scenario: Entidade declara a unicidade

- **WHEN** o mapeamento de `id_autorizacao_empresa` na entidade `Autorizacao` é inspecionado
- **THEN** ele SHALL declarar a coluna como única, coerente com a constraint existente no banco

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
