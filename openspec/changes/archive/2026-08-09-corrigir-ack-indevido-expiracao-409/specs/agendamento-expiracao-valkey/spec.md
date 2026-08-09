## MODIFIED Requirements

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
