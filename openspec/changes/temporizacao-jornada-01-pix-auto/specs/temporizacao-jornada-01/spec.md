## ADDED Requirements

### Requirement: Aplicação temporiza-autorizacao no monorepo

O monorepo SHALL conter a aplicação `apps/temporiza-autorizacao`, em arquitetura hexagonal
(`entrypoint` / `application` / `domain` / `shared`), servindo HTTP na porta **8084** apenas
para o Actuator. A aplicação NÃO SHALL depender de JPA nem de banco de dados relacional, e
NÃO SHALL expor endpoints REST de negócio. O `/actuator/health` SHALL refletir o estado do
consumo da fila e da conexão com o Valkey.

#### Scenario: Aplicação sem dependência de banco
- **WHEN** as dependências e o código-fonte de `apps/temporiza-autorizacao` são inspecionados
- **THEN** não há dependência de JPA, driver PostgreSQL ou entidade mapeando `autorizacoes`

#### Scenario: Porta dedicada
- **WHEN** a aplicação sobe junto às demais do monorepo
- **THEN** ela escuta em 8084, sem conflitar com 8080, 8081, 8082 e 8083

#### Scenario: Health reflete o consumo
- **WHEN** o container do listener SQS está parado fora de um shutdown intencional
- **THEN** `/actuator/health` reporta DOWN

### Requirement: Consumo restrito a recepções de PIX_AUTO na jornada 1

A aplicação SHALL consumir a fila `SQS-temporizacao-autorizacao`, cuja subscription no
tópico `sns-estados-autorizacao` SHALL restringir a entrega, por filter policy sobre message
attributes, a `tipoEvento = RECEPCAO`, `tipoProduto = PIX_AUTO` e `tipoJornada = SPI_J1`. A
aplicação NÃO SHALL reimplementar esse filtro lendo `motivo_status` do corpo. Um evento que
chegue à fila sem os campos necessários ao agendamento SHALL ser tratado como não-retryable:
log de erro sem o corpo da mensagem, seguido de confirmação.

#### Scenario: Recepção de PIX_AUTO em J1 é agendada
- **WHEN** uma autorização `PIX_AUTO` com jornada `SPI_J1` é criada e o evento de recepção é
  publicado
- **THEN** a mensagem é entregue à fila `SQS-temporizacao-autorizacao`
- **AND** a aplicação registra o agendamento da expiração

#### Scenario: Outros produtos, jornadas e eventos não chegam
- **WHEN** eventos de `DDA_AUTO`, de `PIX_AUTO` em `QRC_J2`/`QRC_J3`/`QRC_J4`, ou de tipo
  diferente de `RECEPCAO` são publicados no tópico
- **THEN** nenhum deles é entregue à fila `SQS-temporizacao-autorizacao`

#### Scenario: Payload sem os campos do agendamento é descartado
- **WHEN** uma mensagem sem `id_autorizacao` ou sem `data_hora_inclusao` chega à fila
- **THEN** um log de erro registra o `messageId` sem o corpo da mensagem
- **AND** a mensagem é confirmada, não reentregue indefinidamente

### Requirement: Prazo contado a partir da inclusão da autorização

O instante de vencimento SHALL ser calculado como `data_hora_inclusao` do payload **mais 10
minutos**, e NÃO como o instante do consumo da mensagem. Atraso, retenção ou reentrega da
mensagem SQS NÃO SHALL adiar o vencimento. Uma mensagem cujo vencimento já esteja no passado
no momento do consumo SHALL ser agendada para disparo imediato, e não descartada. O prazo de
10 minutos SHALL ser configurável, com esse valor como padrão.

#### Scenario: Reentrega não adia o prazo
- **WHEN** a mesma mensagem de recepção é consumida duas vezes com 3 minutos de intervalo
- **THEN** o vencimento agendado é o mesmo nas duas vezes, derivado de `data_hora_inclusao`

#### Scenario: Mensagem atrasada dispara imediatamente
- **WHEN** uma mensagem é consumida mais de 10 minutos após a `data_hora_inclusao` da
  autorização
- **THEN** a expiração é elegível a disparo na primeira varredura seguinte

### Requirement: Expiração aciona a rota de decisão do command

No vencimento, a aplicação SHALL acionar
`PATCH /api/autorizacoes/{idAutorizacao}/decisao` no `arj-contratocommand` com
`acao: EXPIRAR`. A aplicação NÃO SHALL consultar a base de autorizações para decidir se
aciona: a revalidação de status é responsabilidade transacional do command. A resposta SHALL
classificar o desfecho do trabalho:

- resposta de sucesso (2xx) — expiração aplicada, trabalho concluído;
- resposta 4xx, incluindo o 422 de "status não permite a transição" — nada a fazer, trabalho
  concluído, registrado em log informativo;
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

#### Scenario: Command indisponível
- **WHEN** o acionamento falha por 5xx, timeout ou erro de conexão
- **THEN** o trabalho NÃO é concluído e permanece elegível a nova tentativa
- **AND** um log de erro registra o id da autorização, sem o corpo do evento
