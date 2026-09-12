## MODIFIED Requirements

### Requirement: contratocommand tem domínio puro

A aplicação `contratocommand` SHALL ter `domain/` livre de mapeamento objeto-relacional, com a
entidade JPA, o mapper, os `AttributeConverter` e os utilitários de particionamento confinados a
`infrastructure/persistence/`.

Este é o requisito que fecha a migração da frota: com ele cumprido, as cinco aplicações de `apps/`
seguem o layout hexagonal clássico.

#### Scenario: Domínio do contratocommand sem ORM

- **WHEN** `apps/contratocommand/src/main/java/br/com/srportto/contratocommand/domain` é inspecionado
- **THEN** nenhuma classe importa `jakarta.persistence` ou `org.hibernate`
- **AND** `org.springframework.*` aparece apenas em `domain/service/`, apenas como anotação de injeção
  e ordenação

#### Scenario: Persistência do contratocommand completa

- **WHEN** `infrastructure/persistence/` do `contratocommand` é inspecionado
- **THEN** contém `AutorizacaoJpaEntity`, os embeddables de chave composta e cancelamento,
  `AutorizacaoPersistenceMapper`, `AutorizacaoJpaAdapter`, `SpringDataAutorizacaoRepository`,
  `TipoProdutoConverter`, `TipoJornadaAutorizacaoConverter`,
  `IdContaUUIDPartitionDistributor`, `ControleExpurgoAutorizacao` e o adaptador da porta de identidade
- **AND** `ReversibleUUIDv7` não reside mais localmente — vem de `libs/srportto-commons-java`
  (`br.com.srportto.commons.persistence.ReversibleUUIDv7`), consumida como dependência Maven

#### Scenario: Unicidade parcial continua não declarada na entidade

- **WHEN** `AutorizacaoJpaEntity` é inspecionada
- **THEN** ela não declara constraint de unicidade para `id_autorizacao_empresa`
- **AND** o comentário que explica o índice único parcial restrito às partições quentes está preservado

#### Scenario: Mapeamento coluna a coluna preservado

- **WHEN** `AutorizacaoJpaEntity` é comparada com a entidade anterior e com as migrations
- **THEN** todos os nomes de coluna, tipos, nulabilidade, precisão, escala e conversores coincidem
- **AND** `metadados` continua mapeada como jsonb

#### Scenario: Fluxos de escrita preservados ponta a ponta

- **WHEN** os três fluxos de escrita são exercitados após a migração
- **THEN** criar `PIX_AUTO` produz status `RECEBIDA` e evento `RECEPCAO`
- **AND** aprovar via decisão produz `ATIVA` e evento `ATIVACAO`
- **AND** criar `DDA_AUTO` produz `ATIVA` e evento `ATIVACAO` diretamente
- **AND** cancelar produz `CANCELADA` e evento `CANCELAMENTO`
- **AND** os message attributes `tipoEvento`, `tipoProduto` e `tipoJornada` são os de antes da migração

#### Scenario: Expurgo continua movendo a linha e a leitura continua encontrando

- **WHEN** uma autorização em estado terminal é transferida para a faixa de partições de expurgo
- **THEN** a movimentação é executada pelo adaptador de persistência
- **AND** o `contratoquery` continua encontrando a autorização por id

#### Scenario: Frota inteira migrada

- **WHEN** as cinco aplicações de `apps/` são inspecionadas
- **THEN** todas estão organizadas em `domain` / `application` / `infrastructure`
- **AND** nenhuma tem pacote de topo `entrypoint` ou `shared`

### Requirement: contratocommand tem portas, adaptadores e regras no lugar

A aplicação `contratocommand` SHALL estar organizada em `domain` / `application` / `infrastructure`,
com portas de entrada e saída próprias, regras de negócio no domínio e o publicador SNS na
infraestrutura.

Esta é a primeira de duas etapas: ao final dela `Autorizacao` ainda é a entidade JPA, residindo em
`domain/model/`. A separação entre modelo e entidade é objeto da etapa seguinte.

#### Scenario: Árvore de pacotes do contratocommand após a etapa de portas

- **WHEN** `apps/contratocommand/src/main/java/br/com/srportto/contratocommand` é inspecionado
- **THEN** `domain/port/in/` contém as interfaces `CriarAutorizacaoUseCase`,
  `CancelarAutorizacaoUseCase` e `DecidirAutorizacaoUseCase` mais os três records de comando
- **AND** `domain/port/out/` contém `AutorizacaoRepository`, que não estende `JpaRepository`
- **AND** `domain/service/` contém o framework de validação, os três validadores e as dez regras
- **AND** `domain/event/` contém `AutorizacaoPersistidaEvent`
- **AND** `domain/exception/` contém `RecursoJaExisteException`
- **AND** `BusinessException` e `ApplicationException` não residem mais localmente em
  `domain/exception/` — vêm de `libs/srportto-commons-java`
  (`br.com.srportto.commons.exception.{BusinessException,ApplicationException}`), consumidas
  como dependência Maven
- **AND** `application/usecase/` contém `CriarAutorizacaoService`, `CancelarAutorizacaoService`,
  `DecidirAutorizacaoService`, `ExpurgoAutorizacaoService` e `AutorizacaoMapper`
- **AND** `infrastructure/persistence/` contém `SpringDataAutorizacaoRepository` e `AutorizacaoJpaAdapter`
- **AND** `infrastructure/messaging/` contém `AutorizacaoEventoPublisher` e `AutorizacaoEventoPayload`
- **AND** `infrastructure/web/` contém `AutorizacaoController`, os DTOs e `ApiExceptionHandler`
- **AND** `infrastructure/config/` contém `AwsProperties` e `SnsClientConfig`

#### Scenario: Caso de uso devolve modelo e não DTO

- **WHEN** os três casos de uso de escrita são inspecionados
- **THEN** nenhum importa tipo de `infrastructure/web`
- **AND** cada um devolve `domain/model/Autorizacao`
- **AND** quem monta `AutorizacaoCompletaResponseDto` é o controller

#### Scenario: Expurgo expressa intenção na porta

- **WHEN** `ExpurgoAutorizacaoService` é inspecionado
- **THEN** ele não calcula número de partição nem referencia `moverParaParticao`
- **AND** chama um método da porta que expressa a transferência para expurgo
- **AND** o cálculo da partição de destino e o `UPDATE` nativo residem no adaptador de persistência

#### Scenario: Contrato das três rotas preservado

- **WHEN** `POST /api/autorizacoes`, `PATCH /api/autorizacoes/{id}/cancelar` e
  `PATCH /api/autorizacoes/{id}/decisao` são exercitados antes e depois da migração
- **THEN** os corpos de resposta e os códigos de status são idênticos, incluindo 422 para `@Valid`,
  422 para `BusinessException` e 409 para conflito de concorrência e recurso já existente
- **AND** os message attributes publicados no SNS (`tipoEvento`, `tipoProduto`, `tipoJornada`) são os
  mesmos nos sete cenários de evento

#### Scenario: Idempotência da decisão preservada

- **WHEN** `PATCH /decisao` é chamado para autorização cujo status já não é `RECEBIDA`
- **THEN** a resposta é 422
- **AND** a linha não é alterada
- **AND** nenhum evento é publicado

### Requirement: contratoquery segue o layout hexagonal clássico

A aplicação `contratoquery` SHALL estar organizada em `domain` / `application` / `infrastructure`,
com modelo de domínio puro, portas próprias e a cascata de partições confinada ao adaptador.

#### Scenario: Árvore de pacotes do contratoquery

- **WHEN** `apps/contratoquery/src/main/java/br/com/srportto/contratoquery` é inspecionado
- **THEN** `domain/model/` contém `Autorizacao` em Java puro
- **AND** `domain/port/in/` contém `ConsultarAutorizacaoUseCase` e `ListarAutorizacoesUseCase`
- **AND** `domain/port/out/` contém `AutorizacaoRepository`, que não estende `JpaRepository`
- **AND** `domain/exception/` contém `ResourceNotFoundException`
- **AND** `BusinessException` e `ApplicationException` não residem mais localmente em
  `domain/exception/` — vêm de `libs/srportto-commons-java`
  (`br.com.srportto.commons.exception.{BusinessException,ApplicationException}`), consumidas
  como dependência Maven
- **AND** `application/usecase/` contém `ConsultarAutorizacaoService` e `ListarAutorizacoesService`
- **AND** `infrastructure/persistence/` contém `AutorizacaoJpaEntity`, `AutorizacaoPersistenceMapper`,
  `AutorizacaoJpaAdapter`, `SpringDataAutorizacaoRepository`, `TipoProdutoConverter`
  e `TipoJornadaAutorizacaoConverter`
- **AND** `ReversibleUUIDv7` não reside mais localmente — vem de `libs/srportto-commons-java`
  (`br.com.srportto.commons.persistence.ReversibleUUIDv7`), consumida como dependência Maven
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
