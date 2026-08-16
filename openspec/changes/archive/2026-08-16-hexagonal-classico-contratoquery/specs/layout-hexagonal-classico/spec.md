## ADDED Requirements

### Requirement: Modelo de domínio é livre de mapeamento objeto-relacional

Numa app migrada que acessa banco relacional, `domain/model/` SHALL conter apenas Java puro. Nenhuma
classe de `domain/` SHALL declarar `@Entity`, `@Table`, `@Column`, `@EmbeddedId`, `@Embedded`,
`@Convert`, `@Version` ou qualquer anotação de `jakarta.persistence` / `org.hibernate`.

A entidade anotada SHALL residir em `infrastructure/persistence/`, com sufixo `JpaEntity`, e um
mapper no mesmo pacote SHALL converter entre ela e o modelo de domínio. `AttributeConverter` de JPA
SHALL residir em `infrastructure/persistence/`.

A interface `JpaRepository` (ou equivalente Spring Data) SHALL residir em
`infrastructure/persistence/` e NÃO SHALL ser `public` — a visibilidade de pacote é o que impede que
uma camada interna a injete. A porta de saída declarada em `domain/port/out/` NÃO SHALL estender
`JpaRepository` nem qualquer interface de Spring Data.

#### Scenario: Domínio sem anotação de persistência

- **WHEN** os imports e anotações de qualquer classe sob `domain/` de uma app migrada com banco são inspecionados
- **THEN** nenhum referencia `jakarta.persistence` ou `org.hibernate`

#### Scenario: Entidade JPA confinada ao adaptador

- **WHEN** uma classe anotada com `@Entity` é localizada numa app migrada
- **THEN** ela reside em `infrastructure/persistence/`
- **AND** existe um mapper no mesmo pacote que a converte para o modelo de domínio

#### Scenario: Spring Data invisível fora do adaptador

- **WHEN** a interface Spring Data de uma app migrada é inspecionada
- **THEN** ela reside em `infrastructure/persistence/` e não é declarada `public`
- **AND** nenhuma classe fora desse pacote a referencia

#### Scenario: Porta de saída não é interface de framework

- **WHEN** a porta de saída de persistência em `domain/port/out/` é inspecionada
- **THEN** ela não estende `JpaRepository` nem outra interface de Spring Data
- **AND** seus parâmetros e retornos são tipos simples ou tipos de `domain/model`

### Requirement: Estratégia de armazenamento não vaza para a aplicação

Toda decisão sobre **como** o dado está guardado SHALL residir em `infrastructure/persistence/` —
particionamento, cascata de busca entre partições, escolha de índice, ordem de tentativas e tradução
para `Pageable`. Nenhuma classe de `application` ou `domain` SHALL conhecer número de partição, faixa
de partição ou constante derivada do layout físico da tabela.

Uma porta de saída SHALL ser expressa em termos do que a aplicação **quer** (buscar por
identificador, listar por conta), nunca em termos de como o armazenamento chega lá (buscar numa
partição, buscar numa faixa).

#### Scenario: Aplicação não conhece partição

- **WHEN** as classes sob `application/` e `domain/` de uma app migrada são inspecionadas
- **THEN** nenhuma declara constante de partição nem faixa de partição
- **AND** nenhuma implementa lógica de tentativa entre partições

#### Scenario: Porta expressa intenção e não mecanismo

- **WHEN** a assinatura da porta de saída de persistência é inspecionada
- **THEN** nenhum método nomeia partição, faixa ou índice
- **AND** nenhum parâmetro é `Pageable`, `Sort` ou outro tipo de Spring Data

#### Scenario: Cascata de busca é responsabilidade do adaptador

- **WHEN** uma app migrada precisa procurar um registro em mais de um lugar por causa do layout físico
- **THEN** a sequência de tentativas está implementada no adaptador de persistência
- **AND** a configuração que a habilita ou desabilita é lida pelo adaptador

### Requirement: contratoquery segue o layout hexagonal clássico

A aplicação `contratoquery` SHALL estar organizada em `domain` / `application` / `infrastructure`,
com modelo de domínio puro, portas próprias e a cascata de partições confinada ao adaptador.

#### Scenario: Árvore de pacotes do contratoquery

- **WHEN** `apps/contratoquery/src/main/java/br/com/srportto/contratoquery` é inspecionado
- **THEN** `domain/model/` contém `Autorizacao` em Java puro
- **AND** `domain/port/in/` contém `ConsultarAutorizacaoUseCase` e `ListarAutorizacoesUseCase`
- **AND** `domain/port/out/` contém `AutorizacaoRepository`, que não estende `JpaRepository`
- **AND** `domain/exception/` contém `BusinessException`, `ApplicationException` e `ResourceNotFoundException`
- **AND** `application/usecase/` contém `ConsultarAutorizacaoService` e `ListarAutorizacoesService`
- **AND** `infrastructure/persistence/` contém `AutorizacaoJpaEntity`, `AutorizacaoPersistenceMapper`,
  `AutorizacaoJpaAdapter`, `SpringDataAutorizacaoRepository`, `ReversibleUUIDv7`, `TipoProdutoConverter`
  e `TipoJornadaAutorizacaoConverter`
- **AND** `infrastructure/web/` contém `AutorizacaoController`, os DTOs de resposta e `ApiExceptionHandler`

#### Scenario: Caso de uso não devolve DTO de resposta

- **WHEN** `ConsultarAutorizacaoService` e `ListarAutorizacoesService` são inspecionados
- **THEN** nenhum importa tipo de `infrastructure/web`
- **AND** o retorno de cada um é expresso em `domain/model/Autorizacao`
- **AND** quem monta `AutorizacaoDetalheResponseDto`, `AutorizacaoResumidaResponseDto` e
  `PaginacaoResponseDto` é o controller

#### Scenario: Contrato REST preservado byte a byte

- **WHEN** `GET /api/autorizacoes/{id}` e `GET /api/autorizacoes` são chamados antes e depois da migração com os mesmos dados
- **THEN** os corpos de resposta são idênticos
- **AND** `status` continua serializado como `String` e os campos continuam nomeados `valor`,
  `dataCriacao` e `dataAtualizacao` — a divergência intencional com o `contratocommand` permanece

#### Scenario: Autorização expurgada continua encontrável

- **WHEN** `GET /api/autorizacoes/{id}` é chamado para autorização em estado terminal, já movida para
  a faixa de partições de expurgo
- **THEN** a resposta é 200 com a autorização
- **AND** o número de queries disparadas é o mesmo de antes da migração

#### Scenario: Mapper cobre todos os campos

- **WHEN** `AutorizacaoPersistenceMapper` é exercitado por teste
- **THEN** todos os campos da entidade são verificados, incluindo `metadados` em jsonb, o
  `cancelamento` embutido e os enums convertidos por `AttributeConverter`
