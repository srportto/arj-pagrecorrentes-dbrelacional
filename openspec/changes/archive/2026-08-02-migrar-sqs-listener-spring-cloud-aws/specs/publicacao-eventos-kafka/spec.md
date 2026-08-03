## ADDED Requirements

### Requirement: Ordenação e deduplicação delegadas ao consumidor a jusante

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

## MODIFIED Requirements

### Requirement: Produce síncrono com timeouts abaixo do visibility timeout

A produção SHALL ser síncrona: o método do listener SHALL aguardar a confirmação do
broker Kafka antes de retornar, de modo que o ack no SQS ocorra somente após essa
confirmação. A aplicação NÃO SHALL adotar produce assíncrono com ack no retorno.

Os timeouts do produce SHALL somar menos que o visibility timeout da fila (60s), para que
uma falha de produção se resolva (sucesso ou exceção) antes de o SQS reentregar a
mensagem. Como o processamento passa a ser concorrente — cada mensagem processada em sua
própria execução, e não em série dentro de um lote — o requisito de dimensionamento SHALL
ser o pior caso de **uma** mensagem, não do lote inteiro.

Todos os caminhos de tempo do produce SHALL ter teto explícito. Além de
`max.block.ms=5s`, `request.timeout.ms=5s` e `delivery.timeout.ms=15s`, a aplicação SHALL
configurar explicitamente os timeouts de conexão e leitura do cliente HTTP do Schema
Registry. A serialização Avro e o round-trip ao Registry ocorrem **dentro** de
`Producer.send()`, antes de o `Future` existir — não são cobertos por `max.block.ms` nem
pelo timeout do `Future.get()`, e sem configuração explícita constituem o único caminho
do produce sem teto de tempo garantido.

#### Scenario: Falha rápida com broker fora do ar

- **WHEN** o Kafka está indisponível e uma mensagem é processada
- **THEN** o produce falha com exceção antes de o visibility timeout da fila expirar
- **AND** nenhuma segunda entrega da mesma mensagem encontra a primeira ainda em voo

#### Scenario: Schema Registry lento não excede o visibility timeout

- **WHEN** o Schema Registry está no ar mas responde lentamente durante a serialização
  dentro de `Producer.send()`
- **THEN** a chamada falha pelo timeout configurado do cliente do Registry
- **AND** o tempo total de processamento da mensagem permanece abaixo do visibility
  timeout da fila

#### Scenario: Pior caso é dimensionado por mensagem, não por lote

- **WHEN** várias mensagens de um mesmo lote são processadas concorrentemente
- **THEN** nenhuma mensagem aguarda o processamento das demais para iniciar o seu
- **AND** o tempo até seu ack é o de seu próprio produce, não a soma do lote
