# publicacao-eventos-kafka — Delta

## MODIFIED Requirements

### Requirement: Header tipoEvento propagado

A ponte SHALL preencher o header Kafka `tipoEvento` com o valor derivado do campo
`status` do payload consumido, via `TipoEventoAutorizacao.porStatus(status)` — não mais
repassando o message attribute SQS. Como o campo `status` é obrigatório no payload, o
header SHALL estar presente em todo evento produzido. Um `status` que não corresponda a
nenhum estado conhecido SHALL classificar a mensagem como inválida (não-retryable:
log de erro + ack/descarte, mesma classificação de payload inválido).

#### Scenario: Header derivado do status do payload
- **WHEN** uma mensagem cujo body tem `status=4` (`ATIVA`) é processada
- **THEN** o evento Kafka carrega o header `tipoEvento` com valor `ATIVACAO`

#### Scenario: Attribute SQS é ignorado
- **WHEN** uma mensagem chega com attribute SQS `tipoEvento` divergente do `status` do
  body (ou sem attribute algum)
- **THEN** o header Kafka reflete exclusivamente o valor derivado do `status` do body

#### Scenario: Status desconhecido é descartado como inválido
- **WHEN** uma mensagem cujo body tem `status` fora da faixa 1–8 é processada
- **THEN** a mensagem é classificada como inválida (log de erro + ack), sem produção
  no Kafka
