# consumo-eventos-kafka

## Purpose

Descreve a aplicação `apps/eventos-consumer`, que consome os eventos Avro publicados no tópico
Kafka `eventos-autorizacao`.

## Requirements

### Requirement: Aplicação consumidora enxuta baseada no modelo do monorepo

O monorepo SHALL conter a aplicação `apps/eventos-consumer`, criada a partir do modelo
arquitetural hexagonal de `docs/arquitetura/based-java-aplication.md`, com Spring Boot
4.0.7, Java 25, pacote raiz `br.com.srportto.eventosconsumer` e porta `8083`. A
aplicação NÃO SHALL depender de JPA/PostgreSQL nem expor endpoints REST de negócio —
apenas o Actuator (`/actuator/health`). Os profiles `local` (defaults do Kafka local) e
`prod` (configuração via variáveis de ambiente) SHALL ser suportados.

#### Scenario: Aplicação sobe sem banco
- **WHEN** `mvn spring-boot:run` é executado em `apps/eventos-consumer` sem nenhum
  PostgreSQL disponível
- **THEN** a aplicação inicia com sucesso na porta 8083
- **AND** `/actuator/health` responde `200 (UP)`

#### Scenario: Defaults locais do Kafka
- **WHEN** a aplicação roda com o profile `local` (default de desenvolvimento)
- **THEN** ela consome o tópico `eventos-autorizacao` do broker e Schema Registry do
  compose local, sem configuração manual

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

Para cada evento consumido com sucesso, a aplicação SHALL registrar um log de sucesso
contendo o corpo do evento (representação legível do record) e o tipo do evento **derivado do
campo `status` do record Avro** via `TipoEventoAutorizacao.porStatus(status)` — o
header Kafka `tipoEvento` deixa de ser usado no processamento. O offset SHALL ser comitado
automaticamente (via `AckMode.RECORD`) após o método do listener retornar sem lançar exceção.
Em caso de erro no processamento (incluindo `status` desconhecido na derivação), o offset
NÃO SHALL ser comitado e a reentrega SHALL seguir a semântica do `DefaultErrorHandler` do
spring-kafka (novas tentativas via seek e, esgotadas as tentativas, publicação na DLT) — não
a semântica de visibility timeout do SQS.

#### Scenario: Consumo com sucesso
- **WHEN** um evento com `status=5` (`CANCELADA`) chega ao tópico
- **THEN** a aplicação loga o consumo com sucesso incluindo o corpo do evento e o tipo
  derivado `CANCELAMENTO`
- **AND** o offset da mensagem é comitado automaticamente

#### Scenario: Header não participa do processamento
- **WHEN** um evento chega com header `tipoEvento` divergente do `status` do record
  (ou sem header)
- **THEN** o log registra o tipo derivado do `status` do record

#### Scenario: Falha no processamento não comita offset
- **WHEN** ocorre um erro antes da conclusão do processamento do evento
- **THEN** o offset não é comitado
- **AND** o `DefaultErrorHandler` reentrega o evento nas tentativas configuradas antes
  de publicar o evento original na DLT

### Requirement: Mensagem não-processável na Dead Letter Topic

A aplicação SHALL encaminhar automaticamente para a Dead Letter Topic (`eventos-autorizacao.DLT`)
qualquer mensagem que esgote as tentativas de processamento via `DefaultErrorHandler`. O
`DeadLetterPublishingRecoverer` SHALL roteiar mensagens com falha de desserialização Avro (capturadas
por `ErrorHandlingDeserializer`) para um `KafkaTemplate<String, byte[]>` especializado, e mensagens
com falha de negócio (após desserialização com sucesso) para um `KafkaTemplate<String, EventoAutorizacao>`
com serialização Avro. O `DefaultErrorHandler` SHALL tentar reprocessar a mensagem 3 vezes com
intervalo de 1 segundo antes de encaminhá-la para a DLT.

#### Scenario: Falha de negócio vai para DLT com tipo Avro
- **WHEN** um evento com `status` desconhecido (ex.: `status=999`) chega ao tópico
- **THEN** `ProcessarEventoAutorizacaoUseCase.processar()` lança exceção em
  `TipoEventoAutorizacao.porStatus(999)`
- **AND** o `DefaultErrorHandler` reentrega a mensagem 3 vezes com 1s de intervalo
- **AND** esgotadas as tentativas, o evento original (`EventoAutorizacao` tipado) é
  publicado em `eventos-autorizacao.DLT` via `KafkaTemplate<String, EventoAutorizacao>`

#### Scenario: Falha de desserialização vai para DLT como bytes
- **WHEN** chega um evento malformado (Avro inválido, schema incompatível com Schema
  Registry) no tópico
- **THEN** `ErrorHandlingDeserializer` captura a exceção de desserialização e a transforma
  em `DeserializationException` recuperável
- **AND** o `DefaultErrorHandler` reentrega a mensagem (bytes crus, sem desserializar) 3
  vezes com 1s de intervalo
- **AND** esgotadas as tentativas, os bytes originais são publicados em
  `eventos-autorizacao.DLT` via `KafkaTemplate<String, byte[]>`
