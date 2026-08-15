## Why

O consumer group `temporizaautorizacao` do stream de expirações acumula consumidores mortos
indefinidamente. O `consumidor-id` é `${HOSTNAME:worker-local}` — em Docker/ECS, o id do
container —, então **cada reinício, cada deploy e cada réplica nova cria um consumidor
diferente, e nenhum é removido**. Não há `XGROUP DELCONSUMER` em lugar algum do código.

Observado em ambiente local em 2026-08-09: 7 consumidores registrados para 2 pods vivos.
Os 5 órfãos incluíam containers de execuções anteriores e um `worker-local` (o default de
quando alguém roda a app fora do Docker), ociosos há ~3,6 h. Foram removidos manualmente —
o que resolve o sintoma daquele dia, não a causa.

Não é falha funcional hoje: consumidores órfãos com `pending = 0` não seguram trabalho, e o
`PendenciasSchedulerReivindicador` reivindica por tempo ocioso da **entrada**, não por dono.
Mas:

- Em produção (ECS, com deploys frequentes) o crescimento é ilimitado, e o comando
  `XINFO CONSUMERS` — o principal instrumento de diagnóstico do grupo — vira ruído: fica
  impossível distinguir a olho um consumidor travado de um container morto há semanas.
- Cada consumidor consome memória no Valkey e é serializado em toda resposta de
  `XINFO CONSUMERS`.
- Um órfão que morra **com** entradas no PEL fica indistinguível dos órfãos benignos,
  atrasando o diagnóstico do único caso que realmente importa.

## What Changes

- Remover do consumer group os consumidores que não correspondem a nenhuma instância viva,
  de forma automática, sem intervenção manual.
- **Nunca** remover consumidor que ainda tenha entradas pendentes no PEL: `XGROUP DELCONSUMER`
  descarta o PEL do consumidor removido, e essas entradas deixariam de ser reivindicáveis —
  autorizações parariam de expirar em silêncio, exatamente o tipo de falha que a change
  `corrigir-expurgo-merge-version` acabou de custar caro para descobrir.
- Expor o número de consumidores do grupo como sinal operacional, para que o crescimento
  anômalo seja visível antes de virar problema.

## Capabilities

### New Capabilities

- `ciclo-vida-consumidor-stream`: registro e remoção de consumidores no consumer group do
  stream de expirações — quem cria, quando remove, e a proteção contra remover consumidor
  com trabalho pendente.

### Modified Capabilities

Nenhuma. `agendamento-expiracao-valkey` descreve o contrato do sorted set e do stream
(chaves, atomicidade da varredura, ack manual); o ciclo de vida do **consumidor** não é
tratado por ela, então entra como capacidade nova em vez de delta.

## Impact

**Código (`temporiza-autorizacao`)**
- `entrypoint/stream/ValkeyStreamConfig.java` — cria o consumer group hoje; ponto natural
  para o registro/limpeza no encerramento
- `entrypoint/stream/PendenciasSchedulerReivindicador.java` — já varre o grupo
  periodicamente; candidato a hospedar a limpeza por tempo ocioso
- `shared/config/TemporizacaoProperties.java` — eventual propriedade de limiar de ociosidade
- `entrypoint/health/TemporizacaoHealthIndicator.java` — eventual exposição da contagem

**Operação**
- Nenhuma mudança de contrato com o `contratocommand` nem com o SQS.
- A limpeza dos órfãos já existentes em cada ambiente é manual e pontual (o comando está em
  `design.md`); esta mudança evita que voltem a acumular.

**Fora de escopo**
- Trocar a estratégia de `consumidor-id`. Usar o hostname é correto — o id **precisa** ser
  único por instância para que o PEL seja atribuído corretamente. O problema é a ausência de
  remoção, não a forma do id.
