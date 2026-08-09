## ADDED Requirements

### Requirement: Fila de temporização com DLQ e subscription filtrada

O root `infra/envs/local-messaging/` SHALL provisionar a fila `SQS-temporizacao-autorizacao`
com sua DLQ correspondente e `redrive_policy`, no mesmo padrão de
`SQS-eventos-autorizacao`, além de uma subscription do tópico `sns-estados-autorizacao` para
essa fila com `raw_message_delivery` habilitado e política que autorize o SNS a publicar
nela.

A subscription SHALL declarar **filter policy sobre message attributes** restringindo a
entrega a `tipoEvento = RECEPCAO`, `tipoProduto = PIX_AUTO` e `tipoJornada = SPI_J1`. A
subscription existente para `SQS-eventos-autorizacao` SHALL permanecer **sem** filter policy,
recebendo todos os eventos.

#### Scenario: Apply cria fila, DLQ e subscription filtrada
- **WHEN** `terraform apply` é executado em `infra/envs/local-messaging/` com o emulador no ar
- **THEN** a fila `SQS-temporizacao-autorizacao`, sua DLQ e a subscription filtrada existem
- **AND** a fila e a subscription já existentes permanecem inalteradas

#### Scenario: Apenas eventos elegíveis chegam à fila de temporização
- **WHEN** eventos de recepção de `PIX_AUTO` em `SPI_J1` e eventos de outros produtos,
  jornadas ou tipos são publicados no tópico
- **THEN** apenas os primeiros são entregues em `SQS-temporizacao-autorizacao`
- **AND** todos continuam sendo entregues em `SQS-eventos-autorizacao`

#### Scenario: Falha persistente cai na DLQ
- **WHEN** uma mensagem excede o número máximo de recebimentos configurado
- **THEN** ela é movida para a DLQ da fila de temporização, em vez de reentregue
  indefinidamente

### Requirement: Divergência de filtragem no emulador é isolada no Terraform

Caso o emulador local não suporte filter policy por message attribute, o root local SHALL
poder provisionar a subscription sem filtro, e a diferença SHALL permanecer restrita ao
código Terraform de ambiente — a aplicação `temporiza-autorizacao` NÃO SHALL ganhar lógica
condicional por ambiente para compensar. A limitação SHALL estar documentada no README do
root.

#### Scenario: Limitação documentada
- **WHEN** um desenvolvedor abre `infra/envs/local-messaging/README.md`
- **THEN** encontra o comportamento esperado da filter policy e o que muda caso o emulador
  não a suporte

#### Scenario: Aplicação não conhece o ambiente
- **WHEN** o código da aplicação `temporiza-autorizacao` é inspecionado
- **THEN** não há ramificação de comportamento entre ambiente local e AWS quanto à filtragem
