## MODIFIED Requirements

### Requirement: Expiração aciona a rota de decisão do command

No vencimento, a aplicação SHALL acionar
`PATCH /api/autorizacoes/{idAutorizacao}/decisao` no `arj-contratocommand` com
`acao: EXPIRAR`. A aplicação NÃO SHALL consultar a base de autorizações para decidir se
aciona: a revalidação de status é responsabilidade transacional do command. A resposta SHALL
classificar o desfecho do trabalho:

- resposta de sucesso (2xx) — expiração aplicada, trabalho concluído;
- resposta 409 (conflito de concorrência) — trabalho **não** concluído, sujeito a nova
  tentativa; a transação do command foi revertida e a autorização pode ainda estar em
  `RECEBIDA`;
- resposta 4xx exceto 409, incluindo o 422 de "status não permite a transição" — nada a
  fazer, trabalho concluído, registrado em log informativo;
- resposta 5xx, timeout ou falha de conexão — trabalho **não** concluído, sujeito a nova
  tentativa.

Nenhum log SHALL conter o corpo do evento, que carrega dado pessoal.

#### Scenario: Expiração aplicada com sucesso
- **WHEN** o vencimento é atingido e a autorização ainda está em `RECEBIDA`
- **THEN** o command responde 200 e o trabalho é concluído
- **AND** a autorização passa a `REJEITADA` com motivo `REJEITADA_SISTEMA_TIMEOUT_J1`

#### Scenario: Cliente decidiu antes do vencimento
- **WHEN** o vencimento é atingido e a autorização já não está em `RECEBIDA`
- **THEN** o command responde 422
- **AND** o trabalho é concluído sem nova tentativa
- **AND** um log informativo registra o id da autorização, sem o corpo do evento

#### Scenario: Conflito de concorrência no command
- **WHEN** o command responde 409 (conflito de lock otimista com outro chamador concorrente
  sobre a mesma autorização)
- **THEN** o trabalho NÃO é concluído e permanece elegível a nova tentativa
- **AND** nenhuma confirmação é feita no stream de expirações

#### Scenario: Command indisponível
- **WHEN** o acionamento falha por 5xx, timeout ou erro de conexão
- **THEN** o trabalho NÃO é concluído e permanece elegível a nova tentativa
- **AND** um log de erro registra o id da autorização, sem o corpo do evento
