## ADDED Requirements

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
