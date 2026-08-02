## MODIFIED Requirements

### Requirement: Consumo Avro via spring-kafka com ack por registro

A aplicação SHALL consumir o tópico `eventos-autorizacao` com spring-kafka
(`@KafkaListener` em `entrypoint/kafka/`), group id `eventos-consumer`, `AckMode.RECORD`
(definido em `ContainerProperties` do `ConcurrentKafkaListenerContainerFactory`) e
desserialização Avro com
`specific.avro.reader=true` envolvida por `ErrorHandlingDeserializer`, usando classes
geradas por `avro-maven-plugin` a partir de uma cópia própria do schema
`EventoAutorizacao` (espelho manual do `.avsc` do producer, mesmo precedente do espelho
do payload JSON no monorepo).

#### Scenario: Evento consumido e decodificado
- **WHEN** um evento Avro é produzido no tópico
- **THEN** o listener o recebe desserializado como `EventoAutorizacao` tipado

#### Scenario: Consumo contínuo
- **WHEN** a aplicação está no ar e novos eventos chegam ao tópico
- **THEN** os eventos são consumidos continuamente sem intervenção manual

### Requirement: Log de sucesso e commit automático do offset por registro

Para cada evento consumido, a aplicação SHALL registrar um log de sucesso contendo o
corpo do evento (representação legível do record) e o tipo do evento derivado do campo
`status` do record Avro via `TipoEventoAutorizacao.porStatus(status)` — o header Kafka
`tipoEvento` continua sem uso no processamento. O offset SHALL avançar automaticamente
(`AckMode.RECORD`) somente após o método do listener retornar sem lançar exceção — sem
`Acknowledgment` injetado nem `acknowledge()` explícito. Em caso de erro no processamento
(incluindo `status` desconhecido na derivação) ou de falha de desserialização, o offset
NÃO SHALL avançar até a mensagem ser reentregue pelas tentativas configuradas do
`DefaultErrorHandler` ou publicada na DLT (ver requisito de DLT).

#### Scenario: Consumo com sucesso
- **WHEN** um evento com `status=5` (`CANCELADA`) chega ao tópico
- **THEN** a aplicação loga o consumo com sucesso incluindo o corpo do evento e o tipo
  derivado `CANCELAMENTO`
- **AND** o offset avança automaticamente após o retorno do listener

#### Scenario: Header não participa do processamento
- **WHEN** um evento chega com header `tipoEvento` divergente do `status` do record
  (ou sem header)
- **THEN** o log registra o tipo derivado do `status` do record

#### Scenario: Falha no processamento não avança o offset
- **WHEN** ocorre um erro antes da conclusão do processamento do evento
- **THEN** o offset não avança
- **AND** o `DefaultErrorHandler` reentrega o evento nas tentativas configuradas antes de
  publicar na DLT

## ADDED Requirements

### Requirement: Mensagem não-processável é publicada na DLT após esgotar tentativas

A aplicação SHALL publicar a mensagem original (bytes crus) no tópico
`eventos-autorizacao.DLT` via `DeadLetterPublishingRecoverer`, e o offset da mensagem
original SHALL avançar (a mensagem não é reentregue indefinidamente), após esgotar as
tentativas do `DefaultErrorHandler` (`FixedBackOff` de 3 tentativas, 1s de intervalo)
sem sucesso — seja por exceção no processamento (ex.: `status` desconhecido) ou por
falha de desserialização Avro/Schema Registry capturada pelo `ErrorHandlingDeserializer`.

#### Scenario: Status desconhecido esgota tentativas e vai para a DLT
- **WHEN** um evento com `status` não mapeado em `TipoEventoAutorizacao.porStatus` é
  reentregue 3 vezes sem sucesso
- **THEN** a mensagem original é publicada em `eventos-autorizacao.DLT`
- **AND** o offset da mensagem original avança (não é reentregue uma 4ª vez)

#### Scenario: Falha de desserialização vai para a DLT
- **WHEN** uma mensagem no tópico não pode ser desserializada como `EventoAutorizacao`
  (schema incompatível ou payload corrompido)
- **THEN** o `ErrorHandlingDeserializer` captura a falha sem derrubar o listener container
- **AND** a mensagem original é publicada em `eventos-autorizacao.DLT` após esgotar as
  tentativas
