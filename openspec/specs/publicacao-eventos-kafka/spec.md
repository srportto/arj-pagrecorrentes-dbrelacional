# publicacao-eventos-kafka

## Purpose

TBD — capability criada a partir da mudança `add-eventos-autorizacao-kafka`. Descreve
como a `autorizacaostatus-producer` produz eventos Avro no tópico Kafka
`eventos-autorizacao` a partir das mensagens consumidas da fila SQS.

## Requirements

### Requirement: Produtor Kafka como adaptador de saída atrás de porta

O produtor Kafka SHALL ser um adaptador de **saída** residente em
`application/eventos/`, conforme o modelo hexagonal do monorepo — NÃO SHALL residir em
um pacote `infrastructure/`. O use case `ProcessarEventoAutorizacaoUseCase` SHALL
depender de uma **porta** (interface) que declare a operação de publicação, e não da
classe concreta do adaptador, de modo que a camada `application` não conheça
`org.apache.kafka.*` fora do adaptador.

As exceções de classificação de falha (`EventoAutorizacaoInvalidoException` e
`EventoAutorizacaoKafkaIndisponivelException`) SHALL residir em `shared/`.

#### Scenario: Use case não conhece o cliente Kafka
- **WHEN** os imports de `ProcessarEventoAutorizacaoUseCase` são inspecionados
- **THEN** não há import de `org.apache.kafka.*` nem da classe concreta do adaptador
- **AND** a dependência declarada é a interface da porta de publicação

#### Scenario: Pacote infrastructure não existe
- **WHEN** a árvore de pacotes de `apps/autorizacaostatus-producer` é inspecionada
- **THEN** existem apenas `entrypoint/`, `application/`, `domain/` e `shared/`
- **AND** nenhuma classe reside em `infrastructure/`

#### Scenario: Produção continua funcionando pela porta
- **WHEN** uma mensagem válida é processada
- **THEN** o evento Avro é produzido no tópico `eventos-autorizacao` através da porta,
  com a mesma key, header e semântica síncrona de antes

### Requirement: Campo obrigatório ausente é classificado antes do produce

O payload consumido SHALL ser validado quanto aos campos obrigatórios do schema Avro
imediatamente após a desserialização, antes de qualquer tentativa de conversão, geração
de key ou produção. São obrigatórios e NÃO SHALL ser nulos os 8 campos declarados sem
união `["null", X]` no `.avsc`: `id_autorizacao`, `id_particao_conta`,
`data_fim_vigencia`, `tipo_produto`, `status`, `data_hora_inclusao`,
`data_hora_ultima_atlz` e `codigo_canal_contratacao`.

A validação SHALL cobrir também a **precisão** dos campos decimais `valor` e
`valor_limite`: o `.avsc` os declara como `decimal(precision=17, scale=2)`, admitindo no
máximo 15 dígitos inteiros. A conversão normaliza a escala (`setScale(2)`) mas não a
precisão — um valor acima da faixa estoura na conversão decimal do Avro, que ocorre
dentro de `Producer.send()` e escaparia da classificação.

Como defesa em profundidade, toda `KafkaException` lançada sincronamente por
`Producer.send()` SHALL ser classificada, nunca escapando sem classificação até o listener.

A classificação SHALL adotar **retryable como default**, e reservar o não-retryable
exclusivamente para falha comprovada do dado — cadeia de causas contendo
`AvroRuntimeException` ou `ClassCastException`, que são o que a conversão Avro produz para
um record incompatível com o schema.

A direção é deliberada: a indisponibilidade do Schema Registry não chega por um tipo único
nem sempre por `IOException`. O despacho por status HTTP do cliente Confluent produz
`TimeoutException`, `DisconnectException`, `ThrottlingQuotaExceededException` e também
`SerializationException` com causa `RestClientException` (Registry no ar, respondendo 4xx
ou 5xx). Enumerar os casos de indisponibilidade é frágil — qualquer um esquecido vira ack e
perda **definitiva** de mensagem legítima. Enumerar as causas de dado inválido é
verificável, e o pior caso do erro oposto (reter na fila indevidamente) preserva o dado.

A percorrida da cadeia de causas SHALL ser protegida contra ciclo.

A validação é necessária porque o builder gerado pelo `avro-maven-plugin` valida apenas
a **ausência** de `set`, não o valor `null` explícito — um `null` setado explicitamente
produz um `SpecificRecord` inválido sem lançar exceção, cuja falha só aparece adiante,
de forma não classificada (`NullPointerException` na geração da key, ou
`SerializationException` **síncrona** lançada dentro de `Producer.send()`, fora do
alcance do tratamento de `ExecutionException`/`TimeoutException`).

Campo obrigatório ausente ou nulo SHALL resultar em `EventoAutorizacaoInvalidoException`
(não-retryable: log de erro sem o body + ack/descarte), NUNCA em falha não classificada
que impeça o ack e cause reentrega infinita. A geração da key de idempotência SHALL
estar sob o mesmo tratamento de classificação de falha do restante do processamento.

#### Scenario: id_autorizacao nulo é rejeitado como inválido
- **WHEN** uma mensagem com `status` válido mas `id_autorizacao` nulo é processada
- **THEN** a mensagem é classificada como não-retryable antes de gerar a key
- **AND** um log ERROR identifica o campo obrigatório ausente, sem o body
- **AND** a mensagem recebe ack, sem produção no Kafka

#### Scenario: Campo obrigatório nulo que passaria pela key é rejeitado
- **WHEN** uma mensagem com `id_autorizacao` e `data_hora_ultima_atlz` presentes, mas
  `codigo_canal_contratacao` nulo, é processada
- **THEN** a mensagem é classificada como não-retryable antes de chamar `send()`
- **AND** nenhuma `SerializationException` não tratada escapa do processamento
- **AND** a mensagem recebe ack, sem produção no Kafka

#### Scenario: Nenhuma reentrega infinita por payload inválido
- **WHEN** qualquer combinação de campos obrigatórios nulos chega à fila
- **THEN** a mensagem é sempre confirmada (ack) após o log de erro
- **AND** nunca permanece na fila em ciclo de reentrega

#### Scenario: Payload completo segue o fluxo normal
- **WHEN** uma mensagem com todos os campos obrigatórios preenchidos é processada
- **THEN** a validação passa e o evento é produzido normalmente no tópico

#### Scenario: Decimal acima da precisão do schema é rejeitado antes do produce
- **WHEN** uma mensagem traz `valor` ou `valor_limite` com mais de 15 dígitos inteiros
- **THEN** a mensagem é classificada como não-retryable na validação
- **AND** nenhuma `SerializationException` não tratada escapa do processamento
- **AND** a mensagem recebe ack, sem produção no Kafka

#### Scenario: Incompatibilidade com o schema não trava a fila
- **WHEN** `Producer.send()` lança `SerializationException` sincronamente por
  incompatibilidade do dado com o schema registrado (causa sem `IOException`)
- **THEN** a falha é classificada como não-retryable (problema de payload)
- **AND** a mensagem recebe ack, sem permanecer em ciclo de reentrega

#### Scenario: Schema Registry fora do ar continua sendo retryable
- **WHEN** `Producer.send()` lança `SerializationException` cuja cadeia de causas inclui
  `IOException` (conexão recusada ou timeout de rede)
- **THEN** a falha é classificada como retryable
- **AND** a mensagem NÃO recebe ack, voltando à fila após o visibility timeout

#### Scenario: Registry respondendo erro HTTP também é retryable
- **WHEN** o Schema Registry está no ar mas responde com status de erro (ex.: 500),
  produzindo `SerializationException` com causa `RestClientException` — sem `IOException`
  na cadeia
- **THEN** a falha é classificada como retryable
- **AND** a mensagem NÃO recebe ack

#### Scenario: Falha não catalogada não descarta mensagem
- **WHEN** `Producer.send()` lança qualquer `KafkaException` cuja cadeia de causas não
  contenha `AvroRuntimeException` nem `ClassCastException`
- **THEN** a falha é classificada como retryable
- **AND** nenhuma mensagem legítima é descartada por uma falha que o código não cataloga

#### Scenario: Cadeia de causas cíclica não trava a classificação
- **WHEN** a exceção recebida tem cadeia de causas com ciclo
- **THEN** a classificação termina normalmente, sem laço infinito

### Requirement: Evento Avro governado por Schema Registry

A `autorizacaostatus-producer` SHALL produzir cada evento consumido da fila SQS no
tópico Kafka `eventos-autorizacao` como Avro `SpecificRecord` gerado por
`avro-maven-plugin` a partir do schema `EventoAutorizacao` (namespace
`br.com.srportto.eventos.autorizacao`), serializado com o `KafkaAvroSerializer` da
Confluent contra o Schema Registry (subject `eventos-autorizacao-value`,
`auto.register.schemas=true` no profile `local`).

O schema SHALL espelhar a linha da tabela `autorizacoes`: campos em snake_case com os
nomes das colunas; nulabilidade conforme o DDL (`["null", X]` com `"default": null`
para colunas anuláveis); `local-timestamp-micros` para colunas `timestamp` sem fuso;
`date` para colunas DATE; `string` com logicalType `uuid` para UUIDs;
`decimal(17,2)` (bytes) para `valor` e `valor_limite`, aplicando `setScale(2)` na
conversão; `long` para `tipo_produto`; `int` para os indicadores; e `string` com o
JSON serializado para `metadados`.

#### Scenario: Evento publicado em Avro válido
- **WHEN** uma mensagem JSON da fila é processada com sucesso
- **THEN** um evento Avro `EventoAutorizacao` é produzido no tópico
  `eventos-autorizacao`, decodificável via Schema Registry
- **AND** os campos espelham as chaves/valores do JSON consumido

#### Scenario: Decimal com scale divergente não estoura
- **WHEN** o JSON traz `valor` com scale diferente de 2 (ex.: `150.5`)
- **THEN** a conversão aplica scale 2 e o evento é produzido normalmente

#### Scenario: Timestamps sem fuso preservados
- **WHEN** o JSON traz `data_hora_ultima_atlz` com precisão de microssegundos
- **THEN** o campo Avro `local-timestamp-micros` preserva o valor sem truncamento nem
  conversão de fuso

### Requirement: Key de idempotência por transição de estado

A key da mensagem Kafka SHALL ser o hash SHA-256 (hex) da concatenação de
`id_autorizacao` com `data_hora_ultima_atlz`, calculado a partir dos campos tipados do
payload desserializado usando formatter fixo (`ISO_LOCAL_DATE_TIME`) — nunca a partir
da string JSON crua. A geração da key SHALL ocorrer somente após a validação de campos
obrigatórios, de modo que nunca receba argumento nulo. O producer SHALL usar
`enable.idempotence=true` e `acks=all`.

A garantia oferecida é **at-least-once com key estável**, não exactly-once:
`enable.idempotence` protege apenas os retries internos de uma mesma chamada de `send()`
e não impede que uma reentrega SQS (por exemplo, após um `DeleteMessage` que falhou
depois de um produce bem-sucedido) publique o evento novamente. A key idêntica é o que
permite a deduplicação do lado do consumidor.

#### Scenario: Reentrega gera a mesma key
- **WHEN** a mesma mensagem SQS é entregue duas vezes (at-least-once) e produzida duas
  vezes no Kafka
- **THEN** as duas mensagens Kafka possuem key idêntica, permitindo deduplicação por
  consumidores

#### Scenario: Transições distintas geram keys distintas
- **WHEN** a criação e o cancelamento da mesma autorização são processados
- **THEN** os dois eventos possuem keys diferentes (o par id + data de última
  atualização identifica a transição, não a autorização)

#### Scenario: Key nunca é gerada a partir de campo nulo
- **WHEN** um payload sem `id_autorizacao` ou sem `data_hora_ultima_atlz` é processado
- **THEN** a mensagem é rejeitada na validação antes de a key ser gerada

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

### Requirement: Produce síncrono com timeouts abaixo do visibility timeout

A produção SHALL ser síncrona (aguardar a confirmação do broker antes do ack no SQS) e
os timeouts do producer SHALL somar menos que o visibility timeout da fila (30s) — na
ordem de `max.block.ms=5s`, `request.timeout.ms=5s` e `delivery.timeout.ms=15s` — para
que uma falha de produção se resolva (sucesso ou exceção) antes de o SQS reentregar a
mensagem.

#### Scenario: Falha rápida com broker fora do ar
- **WHEN** o Kafka está indisponível e uma mensagem é processada
- **THEN** o produce falha com exceção antes de 30s decorridos
- **AND** nenhuma segunda entrega da mesma mensagem encontra a primeira ainda em voo
