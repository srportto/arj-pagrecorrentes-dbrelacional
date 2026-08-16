## Why

Terceira das seis mudanças que migram as aplicações de `apps/` para a arquitetura hexagonal clássica
(motivo geral em `hexagonal-classico-eventos-consumer`; convenções de categoria de adaptador em
`hexagonal-classico-temporiza-autorizacao`).

O `autorizacaostatus-producer` já tem uma porta de saída bem definida — `PublicadorEventoAutorizacao`,
cujo javadoc declara o papel em letra: *"Existe para que o use case não dependa da classe concreta do
adaptador nem conheça `org.apache.kafka.*`"*. Como no `temporiza-autorizacao`, a inversão está feita e
o que falta é o endereço: interface e implementação (`KafkaEventoAutorizacaoProducer`) dividem o
pacote `application/eventos/`.

Mas esta app expõe uma questão que as duas anteriores não expunham, e que precisa de decisão
explícita: **ela é uma ponte de formatos.** Todo o seu trabalho é traduzir o JSON recebido do SNS/SQS
para o record Avro publicado no Kafka. Sete das suas 18 classes de `main` estão nesse caminho de
tradução, e o próprio caso de uso hoje recebe uma `String` JSON crua e a desserializa com
`ObjectMapper`:

```
SQS (JSON) ──▶ ProcessarEventoAutorizacaoUseCase
                 ├─ ObjectMapper.readValue(...)        ← desserialização DENTRO do use case
                 ├─ AutorizacaoEventoPayloadValidator
                 ├─ EventoAutorizacaoConverter          JSON ──▶ Avro
                 ├─ IdempotenciaKeyGenerator            SHA-256(id + data_hora_ultima_atlz)
                 └─ PublicadorEventoAutorizacao ──▶ Kafka (Avro)
```

Uma aplicação cujo negócio *é* a tradução entre dois formatos de fio tem domínio quase vazio. Fingir
o contrário — inventando um terceiro modelo de 25 campos só para ficar no meio — pioraria o
espelhamento manual de schema que o `CLAUDE.md` da raiz já sinaliza como armadilha. Esta mudança
decide onde fica a linha e **registra a dívida** em vez de mascará-la.

## What Changes

- Reorganizar as 18 classes de `main` para `domain` / `application` / `infrastructure`:

  | Hoje | Depois |
  |---|---|
  | `application/eventos/PublicadorEventoAutorizacao` | `domain/port/out/PublicadorEventoAutorizacao` |
  | `application/eventos/KafkaEventoAutorizacaoProducer` | `infrastructure/messaging/KafkaEventoAutorizacaoProducer` |
  | `application/eventos/ProcessarEventoAutorizacaoUseCase` | `domain/port/in/` (interface) + `application/usecase/ProcessarEventoAutorizacaoService` |
  | `application/eventos/IdempotenciaKeyGenerator` | `domain/service/IdempotenciaKeyGenerator` |
  | `application/eventos/AutorizacaoEventoPayload` | `infrastructure/messaging/AutorizacaoEventoPayload` |
  | `application/eventos/AutorizacaoEventoPayloadValidator` | `infrastructure/messaging/` |
  | `application/eventos/EventoAutorizacaoConverter` | `infrastructure/messaging/` |
  | `entrypoint/sqs/SqsEventoAutorizacaoListener`, `...ErrorInterceptor` | `infrastructure/messaging/` |
  | `entrypoint/sqs/SqsListenerHealthIndicator` | `infrastructure/web/` |
  | `shared/config/*` | `infrastructure/config/` |
  | `shared/exceptions/*` | `domain/exception/` |
  | `domain/enums/*` | inalterados |

- **Tirar a desserialização de dentro do caso de uso.** O `ObjectMapper` sai de
  `ProcessarEventoAutorizacaoService` e vai para o listener SQS: o adaptador desserializa, valida e
  converte para Avro; o caso de uso recebe o evento já tipado e orquestra chave de idempotência +
  publicação. É a única mudança estrutural além de movimento de arquivo.
- Mover `IdempotenciaKeyGenerator` para `domain/service/`: derivar a chave de idempotência de uma
  transição de estado a partir de `(idAutorizacao, dataHoraUltimaAtualizacao)` é regra de negócio,
  não detalhe de transporte — ele já opera sobre campos tipados, nunca sobre a string JSON.
- Mover os 11 arquivos de teste para a árvore espelhada.
- Acrescentar à capacidade `layout-hexagonal-classico` o requisito desta app e o requisito geral
  sobre aplicações-ponte.
- Atualizar `apps/autorizacaostatus-producer/CLAUDE.md` e `AGENTS.md` (espelhos idênticos).

- **Nenhuma mudança de comportamento.** Fila SQS, tópico Kafka, schema Avro, chave de idempotência,
  classificação de erro do interceptor, DLQ e health permanecem idênticos. Nenhum log passa a
  carregar dado pessoal — a restrição documentada no javadoc do caso de uso vale igual depois.
- **Dívida assumida e registrada (ver `design.md` D2):** o tipo Avro `EventoAutorizacao` continua
  aparecendo na assinatura da porta de saída, logo `application` importa um tipo gerado do `.avsc`.
  Um domínio 100% livre de formato de fio exigiria um terceiro modelo espelhado de 25 campos.

- **Fora de escopo:** o `EventoAutorizacao.avsc` e seu espelhamento com o `eventos-consumer`.
- **Fora de escopo:** a cópia própria de `AutorizacaoEventoPayload` que esta app mantém em relação
  ao `contratocommand`. O espelhamento manual continua como está; só muda de pacote.

## Capabilities

### Modified Capabilities

- `layout-hexagonal-classico`: acrescenta (a) o requisito de que a desserialização de payload seja
  responsabilidade do adaptador e nunca do caso de uso; (b) a regra para **aplicações-ponte**, cujo
  domínio é a própria tradução — o que se exige delas e o que se dispensa; e (c) o requisito
  específico do `autorizacaostatus-producer`.

## Impact

- **Código afetado (18 arquivos em `main`):** todos mudam de pacote; 1 interface nova (porta de
  entrada); 1 classe renomeada para `*Service`; a assinatura do caso de uso muda de `String` para o
  evento tipado.
- **Testes (11 arquivos):** movidos. `ProcessarEventoAutorizacaoUseCaseTest` precisa de ajuste real —
  hoje ele alimenta o caso de uso com JSON cru; passa a alimentá-lo com o evento já convertido, e a
  cobertura de JSON malformado migra para o teste do listener.
- **Classificação de erro:** `SqsEventoAutorizacaoErrorInterceptor` distingue erro retryável de
  não-retryável para decidir entre reentrega e DLQ. Como a desserialização muda de lugar, confirmar
  que JSON malformado continua classificado como **não-retryável** — se virar retryável, a app entra
  em reentrega infinita de uma mensagem que nunca vai processar.
- **Documentação:** `apps/autorizacaostatus-producer/CLAUDE.md` + `AGENTS.md`.
