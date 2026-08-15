# ciclo-vida-consumidor-stream Specification

## Purpose

Definir o ciclo de vida dos consumidores do `temporiza-autorizacao` no consumer group do stream
de expirações no Valkey: remoção do próprio consumidor no encerramento gracioso, varredura
periódica de órfãos deixados por morte abrupta, a garantia de que nenhuma entrada pendente é
perdida pela limpeza, e a observabilidade da contagem de consumidores do grupo.

## Requirements

### Requirement: Consumidor é removido do grupo no encerramento da instância

Ao encerrar de forma graciosa, a instância do `temporiza-autorizacao` SHALL remover do consumer
group o consumidor que ela mesma registrou (`XGROUP DELCONSUMER` com o seu `consumidor-id`).

A remoção SHALL ser condicionada à ausência de entradas pendentes: havendo PEL não vazio, a
instância SHALL deixar o consumidor no grupo e registrar log, para que as entradas permaneçam
reivindicáveis pelo `PendenciasSchedulerReivindicador`.

Falha ao remover NÃO SHALL impedir o encerramento nem propagar exceção — é higiene, não
trabalho de negócio.

#### Scenario: Encerramento gracioso sem trabalho pendente

- **WHEN** uma instância recebe sinal de término e o seu consumidor tem `pending = 0`
- **THEN** o consumidor SHALL ser removido do grupo
- **AND** a contagem de consumidores do grupo SHALL cair em um

#### Scenario: Encerramento gracioso com trabalho pendente

- **WHEN** uma instância recebe sinal de término e o seu consumidor tem `pending > 0`
- **THEN** o consumidor NÃO SHALL ser removido
- **AND** as entradas pendentes SHALL permanecer reivindicáveis por outra instância
- **AND** a ocorrência SHALL ser registrada em log, com o `consumidor-id` e a quantidade pendente

#### Scenario: Falha na remoção não bloqueia o encerramento

- **WHEN** a remoção falha (Valkey indisponível, por exemplo) durante o encerramento
- **THEN** a instância SHALL encerrar normalmente
- **AND** a falha SHALL ser registrada em log, sem propagar exceção

### Requirement: Consumidores ociosos e sem pendência são removidos periodicamente

O `temporiza-autorizacao` SHALL remover periodicamente do consumer group os consumidores cujo
tempo ocioso ultrapasse um limiar configurável **e** cujo PEL esteja vazio — rede de segurança
para instâncias que morreram sem encerramento gracioso (`SIGKILL`, OOM, perda de nó), em que o
requisito anterior não teve chance de rodar.

O limiar SHALL ser configurável e SHALL ser maior que `stream-min-idle-time-ms`, de modo que a
reivindicação de entradas pendentes sempre ocorra antes de o consumidor se tornar elegível à
remoção.

A varredura SHALL rodar em todas as instâncias sem lock distribuído: `XGROUP DELCONSUMER` sobre
consumidor inexistente é operação sem efeito.

#### Scenario: Consumidor de instância morta é removido

- **WHEN** um consumidor está ocioso além do limiar configurado e tem `pending = 0`
- **THEN** ele SHALL ser removido do grupo
- **AND** a remoção SHALL ser registrada em log com o nome do consumidor e o tempo ocioso

#### Scenario: Consumidor ocioso COM pendência nunca é removido

- **WHEN** um consumidor está ocioso além do limiar mas tem `pending > 0`
- **THEN** ele NÃO SHALL ser removido, por mais tempo que permaneça ocioso
- **AND** as suas entradas SHALL seguir o caminho normal de reivindicação por tempo ocioso da
  entrada

#### Scenario: Instância viva não é removida

- **WHEN** uma instância está no ar e participando do grupo
- **THEN** o seu consumidor NÃO SHALL ser removido, mesmo em período sem expirações a processar

#### Scenario: Remoção concorrente entre instâncias não gera erro

- **WHEN** duas instâncias identificam o mesmo consumidor órfão no mesmo ciclo e ambas tentam
  removê-lo
- **THEN** as duas operações SHALL ser concluídas sem erro
- **AND** nenhum lock distribuído SHALL ser necessário

### Requirement: Nenhuma entrada pendente é perdida pela limpeza

Em nenhuma ordem de eventos a limpeza de consumidores SHALL causar descarte de entrada do PEL.
`XGROUP DELCONSUMER` descarta o PEL do consumidor removido, e entrada assim descartada não é
reivindicável nem reentregue — a autorização correspondente ficaria presa no status de origem
sem qualquer sinal.

A quantidade de entradas descartadas devolvida por `XGROUP DELCONSUMER` SHALL ser verificada; se
for diferente de zero, a ocorrência SHALL ser registrada como erro, por indicar violação da
verificação prévia.

#### Scenario: Remoção que descarta entradas é tratada como erro

- **WHEN** uma remoção devolve quantidade de entradas descartadas maior que zero
- **THEN** a ocorrência SHALL ser registrada com nível de erro, identificando o consumidor e a
  quantidade
- **AND** o log NÃO SHALL conter o corpo do evento

#### Scenario: Expiração agendada sobrevive à morte abrupta de uma instância

- **WHEN** uma instância é encerrada abruptamente enquanto processa uma expiração, e a limpeza
  periódica roda depois disso
- **THEN** a entrada SHALL continuar reivindicável
- **AND** a autorização SHALL acabar expirada, sem intervenção manual

### Requirement: Contagem de consumidores do grupo é observável

O `temporiza-autorizacao` SHALL expor a quantidade de consumidores registrados no consumer group
como sinal operacional, de modo que divergência entre consumidores e instâncias vivas seja
perceptível sem inspeção manual do Valkey.

A exposição NÃO SHALL derrubar o health-check por divergência: o número correto de consumidores
acompanha o autoscaling e não é conhecido de antemão pela aplicação.

#### Scenario: Contagem disponível para inspeção

- **WHEN** o sinal operacional é consultado
- **THEN** ele SHALL informar a quantidade atual de consumidores do grupo

#### Scenario: Divergência não derruba o health-check

- **WHEN** há mais consumidores registrados do que instâncias vivas
- **THEN** o `/actuator/health` SHALL continuar reportando `UP`
