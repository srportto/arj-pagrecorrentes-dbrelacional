## MODIFIED Requirements

### Requirement: Ordenação e deduplicação — bridge oferece key estável; dedup é ônus do consumidor

A ponte NÃO SHALL oferecer garantia de ordem entre eventos, nem mesmo entre eventos da
mesma autorização. A ordenação SHALL ser responsabilidade do consumidor a jusante,
ordenando pelo campo `data_hora_ultima_atlz` do evento.

A ausência de garantia de ordem é consequência de três propriedades já existentes e
deliberadas do desenho, e não um efeito colateral da concorrência:

- a fila SQS é standard (não FIFO), que não garante ordem de entrega;
- a key Kafka é `SHA-256(id_autorizacao + data_hora_ultima_atlz)`, única por transição —
  eventos da mesma autorização caem em partições distintas por construção;
- o processamento é concorrente dentro de cada instância e distribuído entre instâncias.

Por decorrência, elevar a concorrência de processamento NÃO SHALL ser tratado como risco
de ordenação.

Como a garantia de entrega é at-least-once, o mesmo evento SHALL poder ser produzido mais
de uma vez no tópico. A deduplicação SHALL ser responsabilidade do consumidor a jusante,
pela key — que é idêntica entre reentregas do mesmo evento. Este é um **contrato
explícito** com os consumidores do tópico, não um comportamento tácito.

A delegação acima é uma **obrigação transferida, não uma garantia oferecida**. Enquanto um
consumidor não implementar reordenação por `data_hora_ultima_atlz` e deduplicação por key, as
garantias correspondentes NÃO existem no fluxo. Por consequência:

- Um consumidor do tópico `eventos-autorizacao` que ainda não implemente deduplicação por key
  NÃO SHALL aplicar efeito colateral persistente a partir do evento — persistir estado, disparar
  cobrança, notificar terceiros ou qualquer ação não idempotente.
- Um consumidor que ainda não implemente reordenação NÃO SHALL derivar estado a partir da ordem de
  chegada dos eventos.
- Consumo apenas para log, métrica ou auditoria de conectividade SHALL permanecer permitido sem
  essas implementações, por não produzir efeito colateral.

Esta exigência existe porque a delegação foi declarada quando nenhum consumidor a implementava, e o
aviso precisa alcançar quem for adicionar lógica de negócio ao consumidor — no contrato do fluxo, e
não apenas em documento de mudança arquivado.

O tópico `eventos-autorizacao` NÃO SHALL ser configurado com log compaction: com key
única por transição, a compactação nunca teria efeito. A retenção SHALL ser por tempo.

#### Scenario: Eventos da mesma autorização não garantem ordem

- **WHEN** duas transições de estado da mesma autorização são processadas
- **THEN** os dois eventos podem ser produzidos em partições diferentes e consumidos fora
  da ordem cronológica
- **AND** o campo `data_hora_ultima_atlz` de cada evento permite ao consumidor reordená-los

#### Scenario: Concorrência elevada não altera a garantia de ordem

- **WHEN** o `maxConcurrentMessages` do listener é elevado
- **THEN** nenhuma garantia de ordenação é perdida, porque nenhuma existia

#### Scenario: Reentrega produz evento duplicado deduplicável

- **WHEN** a mesma mensagem SQS é entregue duas vezes e produzida duas vezes no tópico
- **THEN** os dois registros Kafka possuem key idêntica
- **AND** o consumidor a jusante os reconhece como o mesmo evento

#### Scenario: Consumidor sem deduplicação não persiste estado

- **WHEN** um consumidor do tópico não implementa deduplicação por key
- **THEN** ele NÃO SHALL aplicar efeito colateral persistente a partir do evento consumido

#### Scenario: Consumo apenas para log permanece permitido

- **WHEN** um consumidor apenas registra o evento em log ou métrica, sem efeito colateral
- **THEN** SHALL poder operar sem implementar deduplicação nem reordenação

#### Scenario: Adicionar lógica de negócio exige as implementações

- **WHEN** um consumidor passa a persistir estado ou disparar ação não idempotente a partir do
  evento
- **THEN** SHALL implementar deduplicação por key e reordenação por `data_hora_ultima_atlz` na
  mesma mudança
