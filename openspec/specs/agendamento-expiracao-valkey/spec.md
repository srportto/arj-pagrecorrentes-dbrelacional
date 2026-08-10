# agendamento-expiracao-valkey

## Purpose

TBD — capacidade criada a partir da mudança `temporizacao-jornada-01-pix-auto`. Descreve
como a aplicação `temporiza-autorizacao` usa o Valkey para agendar e disparar, de forma
idempotente e resiliente, a expiração de autorizações `PIX_AUTO` na jornada `SPI_J1`.

## Requirements

### Requirement: Agenda em sorted set, trabalho em stream com consumer group

O agendamento de expirações SHALL usar dois objetos distintos no Valkey, com papéis
separados: um **sorted set** como relógio, cujo *member* é o id da autorização e cujo
*score* é o instante de vencimento em epoch millis; e um **stream com consumer group** como
fila de trabalho, alimentado apenas no vencimento.

A entrada no stream NÃO SHALL ser criada no momento da recepção do evento, porque entradas
de stream não expiram — não existe entrega com atraso em streams. Keyspace notifications de
expiração (`__keyevent@*__:expired`) NÃO SHALL ser usadas como mecanismo de disparo, por
serem pub/sub sem durabilidade, sem confirmação e sem reentrega.

#### Scenario: Recepção agenda no sorted set
- **WHEN** um evento de recepção válido é consumido
- **THEN** o id da autorização é inserido no sorted set com score igual ao instante de
  vencimento
- **AND** nenhuma entrada é criada no stream nesse momento

#### Scenario: Vencimento cria o trabalho no stream
- **WHEN** o instante de vencimento de um agendamento é ultrapassado
- **THEN** uma entrada correspondente àquele id é criada no stream de expirações

### Requirement: Agendamento idempotente por id de autorização

O id da autorização SHALL ser o *member* do sorted set, de modo que o reagendamento do mesmo
id sobrescreva o anterior em vez de criar uma segunda entrada. Consumir duas vezes o mesmo
evento de recepção NÃO SHALL produzir dois agendamentos nem dois disparos de expiração.

#### Scenario: Evento duplicado não duplica agendamento
- **WHEN** o mesmo evento de recepção é consumido duas vezes
- **THEN** existe exatamente um agendamento para aquele id no sorted set

### Requirement: Varredura atômica elege um único executor por vencimento

A varredura dos vencimentos SHALL poder rodar simultaneamente em todas as instâncias da
aplicação, sem lock distribuído externo. A seleção dos vencidos e a criação do trabalho
SHALL ocorrer em uma operação atômica no servidor (script Lua), na qual a **remoção do id do
sorted set é o que concede o direito** de criar a entrada no stream: apenas a instância cuja
remoção retornar sucesso SHALL criar a entrada. A varredura SHALL operar em lotes limitados
por execução.

#### Scenario: Duas instâncias varrem ao mesmo tempo
- **WHEN** duas instâncias executam a varredura simultaneamente sobre o mesmo agendamento
  vencido
- **THEN** exatamente uma entrada é criada no stream para aquele id
- **AND** o agendamento deixa de existir no sorted set

#### Scenario: Agendamento futuro não é colhido
- **WHEN** a varredura executa antes do instante de vencimento de um agendamento
- **THEN** nenhuma entrada é criada no stream para aquele id
- **AND** o agendamento permanece no sorted set

### Requirement: Consumo do trabalho com confirmação explícita e recuperação de pendências

O consumo das entradas do stream SHALL usar consumer group com leitura por consumidor
identificado, e a entrada SHALL ser confirmada explicitamente **apenas após** o trabalho ser
concluído. Trabalho não concluído — incluindo o caso de a instância morrer entre a leitura e
a confirmação — SHALL permanecer na lista de pendentes do grupo e SHALL ser reivindicável por
outra instância após um tempo mínimo de ociosidade. Nenhuma entrada SHALL ser confirmada
antes do desfecho do acionamento.

A reivindicação de pendências ociosas SHALL observar o número de entregas de cada entrada
(contador nativo do grupo consumidor). Uma entrada que atinja 5 entregas SEM desfecho
conclusivo SHALL ser confirmada diretamente, sem nova tentativa de acionamento, e um log de
erro SHALL registrar `streamId` e `idAutorizacao` (nunca o corpo do evento) para investigação
manual — a aplicação NÃO SHALL manter essa entrada recirculando indefinidamente entre o PEL e
o reivindicador.

#### Scenario: Trabalho concluído é confirmado
- **WHEN** o acionamento da expiração termina com desfecho conclusivo
- **THEN** a entrada é confirmada e deixa a lista de pendentes

#### Scenario: Falha antes da confirmação mantém a pendência
- **WHEN** o acionamento falha de forma retryable
- **THEN** a entrada NÃO é confirmada e permanece na lista de pendentes do grupo

#### Scenario: Instância morta tem o trabalho reivindicado
- **WHEN** uma instância lê uma entrada e é encerrada antes de confirmá-la
- **THEN** após o tempo mínimo de ociosidade outra instância reivindica a entrada e a
  processa

#### Scenario: Entrada esgota o teto de tentativas
- **WHEN** uma entrada é reivindicada e falha de forma retryable pela 5ª vez
- **THEN** a entrada é confirmada sem nova tentativa de acionamento
- **AND** um log de erro registra `streamId` e `idAutorizacao`, sem o corpo do evento
- **AND** a entrada não é reivindicada novamente

### Requirement: Persistência do Valkey e escopo de chaves

A instância Valkey SHALL operar com persistência em append-only file com sincronização a
cada segundo, de modo que uma reinicialização não descarte agendamentos e pendências. As
chaves do sorted set e do stream SHALL ser nomeadas de forma que, em topologia de cluster,
residam no mesmo slot.

#### Scenario: Reinício preserva agendamentos
- **WHEN** o Valkey é reiniciado com agendamentos pendentes e entradas não confirmadas
- **THEN** os agendamentos e a lista de pendentes continuam disponíveis após o restart

#### Scenario: Chaves co-localizadas
- **WHEN** as chaves do sorted set e do stream são inspecionadas
- **THEN** elas compartilham o mesmo escopo de hash, permitindo operação em cluster
