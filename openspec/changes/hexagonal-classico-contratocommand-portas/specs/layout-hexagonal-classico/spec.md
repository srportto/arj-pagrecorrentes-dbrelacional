## ADDED Requirements

### Requirement: Regra de negócio reside no domínio

Toda regra que expressa política de negócio SHALL residir em `domain/service/` — o que é permitido,
o que é obrigatório, qual transição de estado é válida. NÃO SHALL residir em `application/usecase/`,
que orquestra, nem em adaptador de `infrastructure`, que traduz.

Como exceção estreita e explícita, classes de `domain/service/` MAY declarar as anotações de
**injeção e ordenação** do container (`@Component`, `@Order`) quando a regra é registrada como
estratégia plugável descoberta pelo container. A exceção NÃO SHALL alcançar `domain/model/`,
`domain/port/`, `domain/enums/`, `domain/exception/` nem `domain/event/`, que permanecem livres de
framework. Nenhuma outra anotação do Spring é admitida em `domain/service/`.

Quando a ordem de execução das regras é significativa, ela SHALL ser verificada pela lista
efetivamente injetada em runtime, e não pela leitura das anotações — anotação e ordem real já
divergiram neste monorepo.

#### Scenario: Regra de negócio vive em domain/service

- **WHEN** uma regra que valida política de negócio é localizada numa app migrada
- **THEN** ela reside em `domain/service/`
- **AND** não reside em `application/usecase/` nem em `infrastructure/`

#### Scenario: Exceção de anotação é estreita

- **WHEN** os imports de `org.springframework.*` em `domain/` são inspecionados
- **THEN** só aparecem em `domain/service/`
- **AND** são exclusivamente anotações de injeção e ordenação
- **AND** `domain/model/`, `domain/port/`, `domain/enums/`, `domain/exception/` e `domain/event/` não
  têm nenhum

#### Scenario: Ordem das regras é verificada em runtime

- **WHEN** a ordem de execução das regras importa para a mensagem de erro devolvida ao cliente
- **THEN** a ordem efetiva é confirmada pela lista injetada, antes e depois da migração
- **AND** as duas coincidem

### Requirement: Evento de domínio é declarado no domínio e traduzido na infraestrutura

O evento que representa um fato de negócio SHALL ser declarado em `domain/event/`. O adaptador que o
traduz para mensagem externa (SNS, Kafka, webhook) SHALL residir em `infrastructure/messaging/`,
junto do formato de fio correspondente.

Quando a publicação externa precisa acontecer **após o commit** da transação, o mecanismo que garante
isso SHALL ser preservado por qualquer migração de layout. Um caso de uso MAY injetar o barramento de
eventos in-process do container para publicar o evento de domínio — ele é parte do ciclo de vida da
transação, não um sistema externo — mas NÃO SHALL chamar o publicador externo diretamente de dentro da
transação.

#### Scenario: Evento de domínio no domínio, tradução na infraestrutura

- **WHEN** um evento de domínio de uma app migrada é localizado
- **THEN** ele reside em `domain/event/` e não importa framework
- **AND** o adaptador que o traduz para mensagem externa reside em `infrastructure/messaging/`

#### Scenario: Caso de uso não conhece o transporte de saída

- **WHEN** os imports de uma implementação de caso de uso que produz evento são inspecionados
- **THEN** nenhum referencia SDK de nuvem ou cliente de broker

#### Scenario: Rollback não publica evento

- **WHEN** uma transação de escrita sofre rollback por violação de regra de negócio
- **THEN** nenhuma mensagem é publicada no destino externo
- **AND** o tradutor continua registrado para a fase posterior ao commit

### Requirement: Comando de caso de uso é livre de contrato de transporte

O record de comando que um caso de uso recebe SHALL residir em `domain/port/in/`, junto da interface
que o consome, e SHALL declarar seus campos explicitamente.

Um comando NÃO SHALL encapsular o DTO de request HTTP nem qualquer tipo de `infrastructure`, e NÃO
SHALL importar `jakarta.validation.*` ou biblioteca de serialização. O driving adapter SHALL traduzir
o request nos campos do comando.

#### Scenario: Comando não carrega DTO de request

- **WHEN** um record de comando em `domain/port/in/` é inspecionado
- **THEN** nenhum de seus componentes é um DTO de request HTTP
- **AND** ele não importa `jakarta.validation` nem biblioteca de serialização

#### Scenario: Validação de formato permanece na borda

- **WHEN** uma requisição com corpo inválido chega a uma app migrada com API REST
- **THEN** a falha de `@Valid` é detectada no adaptador web
- **AND** a resposta mantém o status e o shape de corpo que a app produzia antes da migração

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
- **AND** `domain/exception/` contém `BusinessException`, `ApplicationException` e `RecursoJaExisteException`
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
