# consumo-eventos-kafka — Delta

## MODIFIED Requirements

### Requirement: Log de sucesso e commit manual do offset

Para cada evento consumido, a aplicação SHALL registrar um log de sucesso contendo o
corpo do evento (representação legível do record) e o tipo do evento **derivado do
campo `status` do record Avro** via `TipoEventoAutorizacao.porStatus(status)` — o
header Kafka `tipoEvento` deixa de ser usado no processamento. Somente após o log a
aplicação SHALL comitar o offset (`Acknowledgment.acknowledge()`). Em caso de erro no
processamento (incluindo `status` desconhecido na derivação), o offset NÃO SHALL ser
comitado e a reentrega SHALL seguir a semântica do `DefaultErrorHandler` do
spring-kafka (novas tentativas via seek e, esgotadas as tentativas, log do descarte) —
não a semântica de visibility timeout do SQS.

#### Scenario: Consumo com sucesso
- **WHEN** um evento com `status=5` (`CANCELADA`) chega ao tópico
- **THEN** a aplicação loga o consumo com sucesso incluindo o corpo do evento e o tipo
  derivado `CANCELAMENTO`
- **AND** comita o offset da mensagem

#### Scenario: Header não participa do processamento
- **WHEN** um evento chega com header `tipoEvento` divergente do `status` do record
  (ou sem header)
- **THEN** o log registra o tipo derivado do `status` do record

#### Scenario: Falha no processamento não comita offset
- **WHEN** ocorre um erro antes da conclusão do processamento do evento
- **THEN** o offset não é comitado
- **AND** o `DefaultErrorHandler` reentrega o evento nas tentativas configuradas antes
  de registrar o descarte em log
