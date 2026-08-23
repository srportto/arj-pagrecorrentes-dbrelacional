# layout-hexagonal-classico

## Purpose

Cobre a migração das aplicações do monorepo para o layout hexagonal clássico (`domain` /
`application` / `infrastructure`), com domínio livre de dependências de infraestrutura.

## Requirements

### Requirement: Controle de concorrência otimista atravessa o mapper íntegro

Quando uma app migrada usa lock otimista, o token de versão SHALL trafegar do adaptador para o modelo
de domínio e de volta, de modo que o `UPDATE` emitido continue portando a cláusula de versão.

A verificação SHALL ser **empírica**, não por leitura de código: um teste de concorrência real, com
duas transações competindo sobre a mesma linha, SHALL ser executado — não pulado — antes de a migração
ser considerada concluída. Um teste de concorrência pulado NÃO SHALL contar como verificação
cumprida.

A escrita de agregado já existente SHALL reaplicar o estado do modelo sobre a entidade **gerenciada**
dentro da transação. NÃO SHALL montar entidade nova a partir do modelo e submetê-la a `save` ou
`merge` quando o token de versão está preenchido: com versão presente, o provedor trata a instância
como detached e conclui que outra transação removeu a linha, produzindo falha determinística e imune a
retry numa operação que deveria ter sucesso.

#### Scenario: Cláusula de versão preservada no UPDATE

- **WHEN** o SQL emitido numa escrita de agregado existente é inspecionado antes e depois da migração
- **THEN** os dois contêm a cláusula de comparação de versão

#### Scenario: Concorrência real é exercitada, não pulada

- **WHEN** a suíte é executada para fechar a migração
- **THEN** o teste de concorrência aparece entre os testes executados
- **AND** duas escritas concorrentes sobre a mesma linha resultam em falha de lock em ao menos uma delas

#### Scenario: Escrita simples continua tendo sucesso

- **WHEN** uma escrita sobre agregado existente ocorre sem concorrência
- **THEN** ela é persistida com sucesso
- **AND** não resulta em falha de estado obsoleto — sintoma de entidade detached submetida a `merge`

#### Scenario: Conflito na movimentação física continua sendo tratado como conflito

- **WHEN** duas transações disputam um registro cuja escrita implica movê-lo entre partições
- **THEN** a transação perdedora produz erro classificado como conflito de concorrência
- **AND** a resposta HTTP é a mesma de antes da migração, não um erro interno genérico

### Requirement: Identidade do agregado não carrega layout físico no domínio

A geração de identificador que depende do layout físico do armazenamento SHALL ficar atrás de uma
porta de saída declarada em `domain/port/out/` e implementada em `infrastructure/persistence/`.

O modelo de domínio SHALL receber o identificador pronto e NÃO SHALL conhecer partição, faixa de
partição ou qualquer constante derivada do armazenamento. A regra de negócio que acompanha a criação
— estado inicial, datas, defaults — SHALL permanecer no modelo.

A porta SHALL preservar exatamente o algoritmo de geração vigente: os identificadores produzidos
depois da migração SHALL ser idênticos aos que o caminho anterior produziria para as mesmas entradas.

#### Scenario: Domínio pede identidade e não sabe o que há nela

- **WHEN** o modelo de domínio e o caso de uso de criação são inspecionados
- **THEN** nenhum referencia número de partição, faixa de partição ou utilitário de particionamento
- **AND** o identificador chega por uma porta declarada em `domain/port/out/`

#### Scenario: Identificadores gerados são idênticos aos anteriores

- **WHEN** a porta de identidade gera identificadores para um conjunto conhecido de entradas
- **THEN** cada um é idêntico ao que o caminho anterior à migração produzia para a mesma entrada

#### Scenario: Regra de criação permanece no modelo

- **WHEN** o modelo de domínio é inspecionado
- **THEN** o estado inicial por produto, as datas e os defaults continuam sendo responsabilidade dele
- **AND** produto sem estado inicial definido continua falhando explicitamente

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
  `TipoProdutoConverter`, `TipoJornadaAutorizacaoConverter`, `ReversibleUUIDv7`,
  `IdContaUUIDPartitionDistributor`, `ControleExpurgoAutorizacao` e o adaptador da porta de identidade

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

### Requirement: Layout de pacotes em três camadas

Toda aplicação de `apps/` já migrada SHALL organizar seu código-fonte Java em exatamente três
pacotes de topo sob `br.com.srportto.<app>`: `domain`, `application` e `infrastructure`. Os pacotes
de topo `entrypoint` e `shared` do layout legado NÃO SHALL existir numa app migrada.

A distribuição SHALL seguir a skill `arquitetura-limpa-java`: `domain` para modelo, portas, enums,
serviços de domínio e exceções de negócio; `application` para as implementações de caso de uso; e
`infrastructure` para adaptadores (`web`, `messaging`, `persistence`, `external`) e configuração
(`config`).

#### Scenario: App migrada não tem pacote do layout legado

- **WHEN** a árvore de pacotes de uma app migrada é inspecionada
- **THEN** existem os pacotes de topo `domain`, `application` e `infrastructure`
- **AND** NÃO existem os pacotes de topo `entrypoint` nem `shared`

#### Scenario: Configuração de framework vive em infrastructure

- **WHEN** uma classe `@Configuration` ou `@ConfigurationProperties` de uma app migrada é localizada
- **THEN** ela reside em `infrastructure/config/`

### Requirement: Regra de dependência aponta sempre para dentro

As dependências entre camadas SHALL apontar exclusivamente para dentro: `infrastructure` pode
depender de `application` e `domain`; `application` pode depender de `domain`; `domain` NÃO SHALL
depender de nenhuma das outras duas.

Nenhuma classe de `domain` SHALL importar `org.springframework.*`, `jakarta.persistence.*`,
`org.apache.kafka.*`, SDK de nuvem ou biblioteca de serialização. Nenhuma classe de `application`
SHALL importar de `infrastructure`, nem tipos de transporte HTTP, JPA ou de SDK de broker.

#### Scenario: Domínio não conhece framework

- **WHEN** os imports de qualquer classe sob `domain/` de uma app migrada são inspecionados
- **THEN** nenhum deles referencia `org.springframework.*`, `jakarta.persistence.*` ou
  `org.apache.kafka.*`

#### Scenario: Application não conhece infraestrutura

- **WHEN** os imports de qualquer classe sob `application/` de uma app migrada são inspecionados
- **THEN** nenhum deles referencia o pacote `infrastructure` da própria app

#### Scenario: Application não devolve tipo de transporte

- **WHEN** a assinatura pública de uma implementação de caso de uso é inspecionada
- **THEN** nem os parâmetros nem o retorno são DTO de request/response HTTP, entidade JPA ou tipo de
  SDK de broker

### Requirement: Portas declaradas no domínio e adaptadores na infraestrutura

Todo caso de uso SHALL ser declarado como interface em `domain/port/in/` e implementado por uma
classe em `application/usecase/`. Todo recurso externo de que a aplicação precisa (repositório,
gateway, publicador, cliente HTTP) SHALL ser declarado como interface em `domain/port/out/` e
implementado por um adaptador em `infrastructure/`.

Um driving adapter SHALL injetar a interface da porta de entrada, nunca a classe que a implementa.
A interface e o adaptador que a implementa NÃO SHALL residir no mesmo pacote.

A exigência é estrutural e vale mesmo quando existe um único implementador: a uniformidade é o que
torna a regra de dependência verificável por inspeção, em vez de julgamento caso a caso.

#### Scenario: Caso de uso é interface no domínio

- **WHEN** um caso de uso de uma app migrada é localizado
- **THEN** existe uma interface correspondente em `domain/port/in/`
- **AND** a implementação reside em `application/usecase/`

#### Scenario: Driving adapter depende da porta

- **WHEN** um controller REST, listener SQS ou consumer Kafka de uma app migrada é inspecionado
- **THEN** o tipo declarado do campo injetado é a interface da porta de entrada
- **AND** não é a classe concreta que a implementa

#### Scenario: Porta de saída e adaptador ficam em pacotes distintos

- **WHEN** uma porta de saída de uma app migrada é localizada
- **THEN** a interface reside em `domain/port/out/`
- **AND** a implementação reside sob `infrastructure/`

### Requirement: Migração de layout preserva comportamento

Uma mudança cujo objetivo é migrar o layout de uma aplicação NÃO SHALL alterar comportamento
observável: rotas HTTP, portas de rede, tópicos, filas, group ids, contratos de mensagem, formato de
resposta ou chaves de configuração permanecem idênticos.

A suíte de testes da aplicação SHALL passar depois da migração com a **mesma contagem** de testes
executados de antes — nenhum teste é adicionado, removido ou silenciosamente pulado.

#### Scenario: Contagem de testes preservada

- **WHEN** `mvn test` é executado na app antes e depois da migração
- **THEN** a suíte passa nas duas execuções
- **AND** o número de testes executados é o mesmo

#### Scenario: Configuração externa não muda

- **WHEN** os arquivos `application.yaml` e `application-*.yaml` são comparados antes e depois
- **THEN** nenhuma chave de configuração foi renomeada, adicionada ou removida por causa da migração

### Requirement: eventos-consumer segue o layout hexagonal clássico

A aplicação `eventos-consumer` SHALL estar organizada em `domain` / `application` /
`infrastructure`, com o consumo do tópico Kafka atrás de uma porta de entrada.

#### Scenario: Árvore de pacotes do eventos-consumer

- **WHEN** `apps/eventos-consumer/src/main/java/br/com/srportto/eventosconsumer` é inspecionado
- **THEN** `domain/port/in/` contém a interface `ProcessarEventoAutorizacaoUseCase`
- **AND** `application/usecase/` contém `ProcessarEventoAutorizacaoService`, que a implementa
- **AND** `infrastructure/messaging/` contém `EventoAutorizacaoKafkaListener`
- **AND** `infrastructure/config/` contém `KafkaConsumerConfig` e `KafkaProperties`
- **AND** `domain/enums/` contém `StatusAutorizacao` e `TipoEventoAutorizacao`

#### Scenario: Listener Kafka depende da porta

- **WHEN** `EventoAutorizacaoKafkaListener` é inspecionado
- **THEN** o campo injetado é do tipo da interface `ProcessarEventoAutorizacaoUseCase`
- **AND** a classe `ProcessarEventoAutorizacaoService` não é referenciada por ele

#### Scenario: Consumo do tópico continua funcionando

- **WHEN** a app é executada contra o Kafka local e uma mensagem chega ao tópico configurado
- **THEN** o evento é processado e o `tipoEvento` derivado do status é logado
- **AND** o offset avança conforme o `AckMode.RECORD` já configurado

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



### Requirement: Desserialização de payload é responsabilidade do adaptador

Um caso de uso NÃO SHALL desserializar payload. Nenhuma classe de `application` SHALL importar
biblioteca de serialização (`tools.jackson.*`, `com.fasterxml.jackson.*`, JSON-B, Avro `Decoder`) nem
receber mensagem como `String` ou `byte[]` bruto.

O driving adapter SHALL desserializar, validar e converter o payload antes de acionar a porta de
entrada. A classificação de erro do adaptador SHALL tratar falha de desserialização como
**não-retryável**: uma mensagem sintaticamente inválida não se torna válida por reentrega, e
classificá-la como retryável produz reentrega indefinida.

#### Scenario: Caso de uso não conhece serialização

- **WHEN** os imports de uma implementação de caso de uso de uma app migrada são inspecionados
- **THEN** nenhum referencia biblioteca de serialização
- **AND** nenhum parâmetro da porta de entrada é `String` ou `byte[]` representando payload

#### Scenario: Payload malformado vai para a DLQ

- **WHEN** uma mensagem sintaticamente inválida chega à fila consumida por uma app migrada
- **THEN** a falha é classificada como não-retryável
- **AND** a mensagem termina na DLQ sem reentrega indefinida

#### Scenario: Erro de desserialização não vaza dado sensível

- **WHEN** uma falha de desserialização é registrada em log ou mensagem de exceção
- **THEN** o conteúdo do payload não aparece
- **AND** a mensagem é identificada por identificador técnico (message id, id da autorização)

### Requirement: Aplicação-ponte pode expor formato de destino na porta de saída, mediante registro explícito

Declarar tipo gerado por ferramenta de serialização na assinatura de uma porta de saída MAY ser
avaliado para uma aplicação-ponte — aquela cujo propósito é traduzir entre dois formatos de fio, sem
regra de negócio própria sobre o conteúdo. Esta é uma exceção estreita à regra de dependência, e
qualquer app que a invoque SHALL registrar no `design.md` da mudança correspondente: a decisão
explícita, o custo da alternativa (modelo de domínio próprio) e os gatilhos que obrigariam a
revisitá-la.

A exceção NÃO SHALL ser invocada por aplicação que tenha modelo de negócio próprio: nesse caso o
modelo de domínio existe e a porta SHALL falar nele. Uma app pode avaliar a exceção e optar por não
invocá-la, pagando o custo de um modelo de domínio próprio mesmo sendo apenas uma ponte — a decisão
SHALL constar do `design.md` de qualquer forma, com o resultado escolhido.

#### Scenario: Ponte que invoca a exceção declara-a no design

- **WHEN** uma app migrada declara tipo de serialização na assinatura de porta de saída
- **THEN** o `design.md` da mudança correspondente registra a decisão, o custo da alternativa e os
  gatilhos de revisão

#### Scenario: App com modelo próprio não invoca a exceção

- **WHEN** uma app migrada possui modelo de negócio em `domain/model/`
- **THEN** suas portas de saída falam em tipos de `domain`, não em tipos gerados de schema

### Requirement: autorizacaostatus-producer segue o layout hexagonal clássico

A aplicação `autorizacaostatus-producer` SHALL estar organizada em `domain` / `application` /
`infrastructure`, com a porta de saída de publicação separada do adaptador Kafka e a tradução de
formato inteiramente em `infrastructure`. A app **optou por não invocar** a exceção de
"aplicação-ponte pode expor formato de destino": possui `domain/model/EventoAutorizacao` próprio, e a
porta de saída fala nele — ver D2-b em `design.md`.

#### Scenario: Árvore de pacotes do producer

- **WHEN** `apps/autorizacaostatus-producer/src/main/java/br/com/srportto/autorizacaostatusproducer` é inspecionado
- **THEN** `domain/model/` contém `EventoAutorizacao` (tipo puro, sem import de Avro)
- **AND** `domain/port/out/` contém `PublicadorEventoAutorizacao`, com assinatura que usa
  `domain/model/EventoAutorizacao`, não o tipo Avro gerado
- **AND** `domain/port/in/` contém a interface `ProcessarEventoAutorizacaoUseCase`
- **AND** `domain/service/` contém `IdempotenciaKeyGenerator`
- **AND** `domain/exception/` contém `EventoAutorizacaoInvalidoException` e
  `EventoAutorizacaoKafkaIndisponivelException`
- **AND** `application/usecase/` contém `ProcessarEventoAutorizacaoService`
- **AND** `infrastructure/messaging/` contém `KafkaEventoAutorizacaoProducer`,
  `EventoAutorizacaoAvroMapper` (mapeia `domain/model/EventoAutorizacao` → Avro),
  `SqsEventoAutorizacaoListener`, `SqsEventoAutorizacaoErrorInterceptor`, `AutorizacaoEventoPayload`,
  `AutorizacaoEventoPayloadValidator` e `EventoAutorizacaoConverter` (mapeia payload → domínio)
- **AND** `infrastructure/web/` contém `SqsListenerHealthIndicator`
- **AND** `infrastructure/config/` contém `KafkaProducerClientConfig`, `KafkaProperties` e
  `SqsListenerContainerFactoryConfig`

#### Scenario: Chave de idempotência permanece regra de domínio

- **WHEN** `IdempotenciaKeyGenerator` é inspecionado
- **THEN** ele reside em `domain/service/`
- **AND** deriva a chave de `(idAutorizacao, dataHoraUltimaAtualizacao)` tipados, nunca da string
  JSON crua

#### Scenario: Ponte SQS para Kafka continua funcionando

- **WHEN** um evento válido é publicado na fila SQS consumida pela app
- **THEN** o evento correspondente é produzido no tópico Kafka em Avro
- **AND** a chave de idempotência é idêntica à que a app produzia antes da migração para o mesmo par
  de id e data de última atualização
- **AND** o schema Avro publicado não mudou


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

### Requirement: Modelo de domínio expõe comportamento, não apenas campos mutáveis

Uma transição de estado de negócio SHALL ser expressa como um método do modelo de domínio nomeado
pela ação (`aprovar()`, `cancelar(...)`, `expirar...()`), não como uma sequência de setters chamada
de dentro de `application/usecase`. O método do modelo SHALL encapsular todo par de campos que muda
junto (ex.: `status` e o `motivoStatus` correspondente), de modo que seja estruturalmente impossível
gravar um `status` sem o `motivoStatus` que o acompanha.

Um caso de uso em `application/usecase` MAY decidir **qual** transição chamar (a partir da ação
recebida), mas NÃO SHALL montar o novo estado campo a campo.

#### Scenario: Aplicação de decisão delega ao modelo

- **WHEN** um caso de uso de decisão sobre autorização (aprovar, rejeitar, expirar) é inspecionado
- **THEN** ele chama um método do modelo de domínio nomeado pela ação de negócio
- **AND** não atribui `status`/`motivoStatus`/campo equivalente diretamente

#### Scenario: Par status+motivo não se dissocia

- **WHEN** o modelo de domínio é inspecionado
- **THEN** não existe um setter público de `status` que possa ser chamado sem o motivo
  correspondente

### Requirement: DTO de resposta não embute tipo de domain/model diretamente

Um DTO de resposta (HTTP em `infrastructure/web` ou payload em `infrastructure/messaging`) MUST NOT declarar um campo cujo tipo seja uma classe de `domain/model`. Todo dado do domínio exposto na borda SHALL passar por um tipo próprio da borda (DTO aninhado ou campo achatado), mapeado explicitamente.

#### Scenario: Campo composto do domínio vira DTO próprio na resposta

- **WHEN** o modelo de domínio tem um campo cujo tipo é outro objeto de domínio (ex.:
  `Autorizacao.cancelamento: Cancelamento`)
- **THEN** o DTO de resposta que expõe esse dado declara um tipo próprio de
  `infrastructure/web`/`infrastructure/messaging`, não o tipo de `domain/model`

#### Scenario: Renomear campo do domínio não quebra o contrato em silêncio

- **WHEN** um campo de `domain/model` é renomeado
- **THEN** a mudança não compila até o mapeamento explícito da borda ser atualizado — não é
  detectável apenas em runtime pela serialização
