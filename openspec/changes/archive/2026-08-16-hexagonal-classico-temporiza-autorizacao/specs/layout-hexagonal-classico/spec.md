## ADDED Requirements

### Requirement: Categoria do adaptador segue o gatilho, não a tecnologia

Um adaptador de `infrastructure` SHALL ser classificado pelo **papel** que exerce, não pelo produto
que usa. Não SHALL existir pacote de infraestrutura nomeado por fornecedor ou tecnologia
(`valkey/`, `kafka/`, `postgres/`).

As categorias SHALL ser:

| Categoria | Papel |
|---|---|
| `infrastructure/web/` | driving adapter acionado por HTTP — inclui health indicator do Actuator |
| `infrastructure/messaging/` | driving adapter acionado por mensagem (fila, tópico, stream) e driven adapter que publica mensagem; também o formato de fio das mensagens |
| `infrastructure/scheduler/` | driving adapter acionado pela passagem do tempo (`@Scheduled`) |
| `infrastructure/persistence/` | driven adapter sobre estado que é da própria aplicação, em qualquer tecnologia |
| `infrastructure/external/` | driven adapter sobre sistema de outro dono, cujo contrato a aplicação não controla |
| `infrastructure/config/` | `@Configuration`, beans e `@ConfigurationProperties` |

O critério que separa `persistence/` de `external/` SHALL ser a **propriedade do dado**: estado que
só a aplicação lê e escreve é `persistence/`, ainda que o armazenamento seja chave-valor ou stream;
colaborador com contrato alheio é `external/`, ainda que a chamada seja HTTP simples.

#### Scenario: Store de estado próprio classifica como persistence

- **WHEN** um adaptador que guarda estado exclusivo da aplicação é localizado
- **THEN** ele reside em `infrastructure/persistence/`
- **AND** não existe pacote de infraestrutura nomeado com a tecnologia de armazenamento

#### Scenario: Cliente de outro serviço classifica como external

- **WHEN** um adaptador que chama outro serviço do monorepo ou de terceiro é localizado
- **THEN** ele reside em `infrastructure/external/`

#### Scenario: Adaptador acionado por tempo tem pacote próprio

- **WHEN** uma classe com método anotado `@Scheduled` é localizada numa app migrada
- **THEN** ela reside em `infrastructure/scheduler/`
- **AND** não está em `infrastructure/messaging/`, mesmo quando o trabalho que ela dispara é sobre
  fila ou stream

#### Scenario: Health indicator classifica como web

- **WHEN** uma implementação de `HealthIndicator` é localizada numa app migrada
- **THEN** ela reside em `infrastructure/web/`

### Requirement: Formato de fio de mensagem não atravessa a fronteira da aplicação

O formato de serialização de uma mensagem recebida ou publicada SHALL residir em
`infrastructure/messaging/`, seja ele record ou classe. Uma porta de entrada NÃO SHALL declará-lo em
sua assinatura.

O driving adapter SHALL traduzir o formato de fio em tipos simples ou em modelo de domínio antes de
acionar a porta, de modo que renomear um campo no contrato do produtor externo não alcance
`application` nem `domain`.

#### Scenario: Porta de entrada não recebe formato de fio

- **WHEN** a assinatura de uma porta de entrada de uma app migrada é inspecionada
- **THEN** nenhum parâmetro é um record de payload de mensagem
- **AND** os parâmetros são tipos simples ou tipos de `domain/model`

#### Scenario: Tradução acontece no adaptador

- **WHEN** um listener de fila ou stream de uma app migrada é inspecionado
- **THEN** ele desserializa o payload e extrai os campos necessários antes de chamar a porta

### Requirement: Contrato comportamental de porta é declarado no domínio

Uma exceção que faz parte do contrato de uma porta SHALL residir em `domain/exception/` — isto é,
quando lançá-la é parte do protocolo entre a aplicação e o adaptador. Uma porta declarada em
`domain/port/` NÃO SHALL referenciar, em assinatura ou javadoc normativo, tipo definido fora de
`domain`.

#### Scenario: Exceção citada no contrato da porta vive no domínio

- **WHEN** o javadoc ou a assinatura de uma porta de saída cita uma exceção como parte do protocolo
- **THEN** essa exceção reside em `domain/exception/`

### Requirement: temporiza-autorizacao segue o layout hexagonal clássico

A aplicação `temporiza-autorizacao` SHALL estar organizada em `domain` / `application` /
`infrastructure`, com as duas portas de saída existentes separadas de seus adaptadores.

#### Scenario: Portas de saída no domínio, adaptadores na infraestrutura

- **WHEN** `apps/temporiza-autorizacao/src/main/java/br/com/srportto/temporizaautorizacao` é inspecionado
- **THEN** `domain/port/out/` contém as interfaces `AgendamentoRepository` e `DecisaoAutorizacaoClient`
- **AND** `infrastructure/persistence/` contém `ValkeyAgendamentoRepository`
- **AND** `infrastructure/external/` contém `CommandDecisaoAutorizacaoClient`
- **AND** nenhuma dessas interfaces divide pacote com a classe que a implementa

#### Scenario: Os três casos de uso têm porta de entrada

- **WHEN** os casos de uso da app são inspecionados
- **THEN** `domain/port/in/` contém as interfaces `AgendarExpiracaoUseCase`,
  `ProcessarExpiracaoUseCase` e `VarrerAgendamentosVencidosUseCase`
- **AND** `application/usecase/` contém `AgendarExpiracaoService`, `ProcessarExpiracaoService` e
  `VarrerAgendamentosVencidosService`, cada uma implementando a porta de mesmo prefixo

#### Scenario: Driving adapters distribuídos por gatilho

- **WHEN** os adaptadores de entrada da app são localizados
- **THEN** `infrastructure/messaging/` contém `TemporizacaoEventoListener`,
  `TemporizacaoEventoErrorInterceptor`, `ExpiracaoStreamListener`,
  `PendenciasSchedulerReivindicador`, `ConsumidorRemocaoService` e `AutorizacaoEventoPayload`
- **AND** `infrastructure/scheduler/` contém `VarreduraAgendamentoScheduler` e
  `ConsumidoresOrfaosLimpezaScheduler`
- **AND** `infrastructure/web/` contém `TemporizacaoHealthIndicator`
- **AND** `infrastructure/config/` contém `ValkeyStreamConfig`, `CommandClientConfig`,
  `SqsListenerContainerFactoryConfig` e `TemporizacaoProperties`

#### Scenario: Temporização da jornada 1 continua funcionando

- **WHEN** o evento de recepção `PIX_AUTO` / `SPI_J1` é publicado na fila da app e a janela de 10
  minutos vence sem decisão do cliente
- **THEN** o agendamento é gravado no Valkey na recepção
- **AND** a varredura aciona `PATCH /decisao` com `acao: EXPIRAR` no `contratocommand`
- **AND** a janela, a cadência da varredura e a política de retry permanecem as de antes da migração

#### Scenario: Health continua exposto após a mudança de pacote

- **WHEN** `GET /actuator/health` é chamado na app migrada
- **THEN** a resposta inclui o indicador de temporização
