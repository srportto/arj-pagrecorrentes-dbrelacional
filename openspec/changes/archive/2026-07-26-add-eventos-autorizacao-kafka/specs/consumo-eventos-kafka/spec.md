# consumo-eventos-kafka

## ADDED Requirements

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

### Requirement: Consumo Avro via spring-kafka com ack manual

A aplicação SHALL consumir o tópico `eventos-autorizacao` com spring-kafka
(`@KafkaListener`), group id `eventos-consumer`, `AckMode.MANUAL` e desserialização
Avro com `specific.avro.reader=true`, usando classes geradas por `avro-maven-plugin` a
partir de uma cópia própria do schema `EventoAutorizacao` (espelho manual do `.avsc` do
producer, mesmo precedente do espelho do payload JSON no monorepo).

#### Scenario: Evento consumido e decodificado
- **WHEN** um evento Avro é produzido no tópico
- **THEN** o listener o recebe desserializado como `EventoAutorizacao` tipado

#### Scenario: Consumo contínuo
- **WHEN** a aplicação está no ar e novos eventos chegam ao tópico
- **THEN** os eventos são consumidos continuamente sem intervenção manual

### Requirement: Log de sucesso e commit manual do offset

Para cada evento consumido, a aplicação SHALL registrar um log de sucesso contendo o
corpo do evento (representação legível do record) e, somente após o log, comitar o
offset (`Acknowledgment.acknowledge()`). Em caso de erro no processamento, o offset NÃO
SHALL ser comitado e a reentrega SHALL seguir a semântica do `DefaultErrorHandler` do
spring-kafka (novas tentativas via seek e, esgotadas as tentativas, log do descarte) —
não a semântica de visibility timeout do SQS.

#### Scenario: Consumo com sucesso
- **WHEN** um evento chega ao tópico
- **THEN** a aplicação loga o consumo com sucesso incluindo o corpo do evento
- **AND** comita o offset da mensagem

#### Scenario: Falha no processamento não comita offset
- **WHEN** ocorre um erro antes da conclusão do processamento do evento
- **THEN** o offset não é comitado
- **AND** o `DefaultErrorHandler` reentrega o evento nas tentativas configuradas antes
  de registrar o descarte em log
