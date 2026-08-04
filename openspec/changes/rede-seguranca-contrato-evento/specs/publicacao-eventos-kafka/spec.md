## MODIFIED Requirements

### Requirement: Evento Avro governado por Schema Registry

A `autorizacaostatus-producer` SHALL produzir cada evento consumido da fila SQS no
tópico Kafka `eventos-autorizacao` como Avro `SpecificRecord` gerado por
`avro-maven-plugin` a partir do schema `EventoAutorizacao` (namespace
`br.com.srportto.eventos.autorizacao`), serializado com o `KafkaAvroSerializer` da
Confluent contra o Schema Registry (subject `eventos-autorizacao-value`).

O registro automático de schemas (`auto.register.schemas`) SHALL ser habilitado apenas no profile
`local` e SHALL estar desabilitado nos demais profiles, em especial `prod`. A configuração SHALL
ser parametrizada por profile, não fixada em código. Fora do profile `local`, o registro de um
schema novo ou alterado SHALL ocorrer por um caminho explícito e revisável, anterior ao primeiro
produce que o utilize — de modo que uma alteração incompatível não seja registrada em produção
como efeito colateral do tráfego.

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

#### Scenario: Registro automático habilitado no profile local
- **WHEN** a aplicação roda com o profile `local` e produz o primeiro evento
- **THEN** o subject `eventos-autorizacao-value` é registrado automaticamente no Schema Registry

#### Scenario: Registro automático desabilitado em produção
- **WHEN** a configuração efetiva da aplicação no profile `prod` é inspecionada
- **THEN** `auto.register.schemas` SHALL estar desabilitado
- **AND** o valor SHALL vir de configuração por profile, não de literal fixado no código

#### Scenario: Schema não registrado falha de forma visível em produção
- **WHEN** a aplicação em `prod` tenta produzir um evento cujo schema ainda não foi registrado
- **THEN** o produce SHALL falhar com erro explícito, em vez de registrar o schema
  automaticamente
