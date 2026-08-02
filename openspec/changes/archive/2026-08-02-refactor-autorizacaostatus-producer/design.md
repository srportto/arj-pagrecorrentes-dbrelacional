## Context

`apps/autorizacaostatus-producer` é a ponte SQS → Kafka do monorepo: consome
`SQS-eventos-autorizacao`, converte o payload JSON para Avro e produz em
`eventos-autorizacao` com key SHA-256 e produce síncrono. Stack: Spring Boot 4.0.7,
Java 25, AWS SDK v2 puro (sem Spring Cloud AWS), `kafka-clients` puro (sem spring-kafka),
Avro + Confluent Schema Registry.

**Estado atual dos pacotes:**

```
application/eventos/          ProcessarEventoAutorizacaoUseCase, EventoAutorizacaoConverter,
                              IdempotenciaKeyGenerator, AutorizacaoEventoPayload,
                              StatusAutorizacao, TipoEventoAutorizacao,
                              EventoAutorizacaoInvalidoException
infrastructure/sqs/           SqsEventoAutorizacaoListener
infrastructure/kafka/         KafkaEventoAutorizacaoProducer,
                              EventoAutorizacaoKafkaIndisponivelException
shared/config/                AwsProperties, SqsClientConfig, KafkaProperties,
                              KafkaProducerClientConfig
```

O pacote `infrastructure/` não existe no modelo hexagonal do projeto
(`entrypoint` / `application` / `domain` / `shared`) e produz setas nos dois sentidos
entre `application` e `infrastructure`. A app irmã `eventos-consumer` foi realinhada
nesta semana e hoje as duas divergem.

**Restrições:**
- 28 testes passando, gate JaCoCo de 80% (LINE/COVEREDRATIO) cumprido — deve permanecer
- Nenhuma dependência nova: a mudança é interna
- O `.avsc` é espelho manual do de `eventos-consumer` e hoje são binariamente idênticos —
  não pode divergir
- A fila SQS não tem redrive policy (decisão registrada no `CLAUDE.md`); descarte
  consciente continua sendo a estratégia para mensagem não-retryable
- Contrato externo imutável: payload SQS, tópico/key/header Kafka, porta 8082, profiles

**Auditoria:** o agent `java-especialista` auditou a app e classificou cinco achados como
pré-requisito bloqueante desta refatoração, por viverem nos mesmos arquivos a serem
movidos. Esta mudança os absorve.

## Goals / Non-Goals

**Goals:**

- Alinhar a estrutura de pacotes ao modelo hexagonal do monorepo e ao precedente do
  `eventos-consumer`
- Eliminar a dependência da camada `application` sobre uma classe concreta de adaptador,
  introduzindo uma porta de saída
- Eliminar a reentrega infinita causada por campo obrigatório nulo no payload
- Eliminar PII dos logs
- Tornar o shutdown efetivamente gracioso e o outage do consumidor visível no health-check
- Manter todo o comportamento externo observável idêntico (exceto o conteúdo dos logs)

**Non-Goals:**

- Trocar o AWS SDK v2 puro por `spring-cloud-aws`/`@SqsListener` — decisão registrada no
  `CLAUDE.md`, merece mudança própria
- Introduzir DLQ/redrive policy na fila (mudança de infraestrutura, não de aplicação)
- Resolver a duplicata por ack falho pós-produce-OK (exige dedup no consumidor ou tópico
  compactado)
- Paralelizar o lote de 10 mensagens, revisar timeouts, `AUTO_REGISTER_SCHEMAS` por
  profile, MDC/`traceId`, `ObjectMapper` injetado, auth SASL/mTLS, remoção da dependência
  Lombok do `pom.xml`, `logging.structured.format.console` — todos registrados para a
  mudança seguinte

## Decisions

### 1. Destino de cada classe

| Classe | De | Para | Razão |
|---|---|---|---|
| `SqsEventoAutorizacaoListener` | `infrastructure/sqs/` | `entrypoint/sqs/` | Adaptador de ENTRADA (`mensageria-sqs-kafka` §1) |
| `KafkaEventoAutorizacaoProducer` | `infrastructure/kafka/` | `application/eventos/` | Adaptador de SAÍDA, encapsulado em `application` |
| `StatusAutorizacao` | `application/eventos/` | `domain/enums/` | Regra de negócio pura (grafo de transições) |
| `TipoEventoAutorizacao` | `application/eventos/` | `domain/enums/` | Idem |
| `EventoAutorizacaoInvalidoException` | `application/eventos/` | `shared/exceptions/` | Exceções vão em `shared` |
| `EventoAutorizacaoKafkaIndisponivelException` | `infrastructure/kafka/` | `shared/exceptions/` | Idem |
| `AutorizacaoEventoPayload` | `application/eventos/` | *(permanece)* | Contrato do evento consumido, não regra de negócio — precedente já documentado |
| `EventoAutorizacaoConverter`, `IdempotenciaKeyGenerator`, `ProcessarEventoAutorizacaoUseCase` | `application/eventos/` | *(permanecem)* | Já corretos |

Resultado:

```
entrypoint/sqs/       SqsEventoAutorizacaoListener
                      SqsListenerHealthIndicator
application/eventos/  ProcessarEventoAutorizacaoUseCase, EventoAutorizacaoConverter,
                      IdempotenciaKeyGenerator, AutorizacaoEventoPayload,
                      PublicadorEventoAutorizacao (porta),
                      KafkaEventoAutorizacaoProducer (adaptador)
domain/enums/         StatusAutorizacao, TipoEventoAutorizacao
shared/exceptions/    EventoAutorizacaoInvalidoException,
                      EventoAutorizacaoKafkaIndisponivelException
shared/config/        (inalterado)
```

**Alternativa descartada:** manter os enums em `application/eventos/` (situação atual,
codificada como exceção na spec `maquina-estados-autorizacao`). A justificativa original
era "a app não tem camada `domain/`" — circular, e o `eventos-consumer` já a desfez.

### 2. Porta de saída no mesmo pacote do adaptador

A interface `PublicadorEventoAutorizacao` fica em `application/eventos/`, junto do
adaptador que a implementa — não em `domain/port/out/`.

**Por quê:** o domínio desta app é minúsculo (dois enums) e não orquestra nada; uma porta
em `domain/` seria cerimônia sem ganho. O objetivo concreto é que
`ProcessarEventoAutorizacaoUseCase` deixe de importar a classe concreta e de conhecer
`org.apache.kafka.*` — isso a interface no mesmo pacote já entrega, e é substituível em
teste por um mock sem `@SpringBootTest`.

**Alternativa descartada:** `domain/port/out/PublicadorEventoAutorizacao`. Correto em
teoria, mas criaria uma camada `domain` que só existe para hospedar uma interface que o
domínio nunca usa.

### 3. Validação de campos obrigatórios: explícita, logo após a desserialização

O builder gerado pelo `avro-maven-plugin` valida `fieldSetFlags()` (ausência de `set`),
não nulidade:

```java
record.id_autorizacao = fieldSetFlags()[0] ? this.id_autorizacao : defaultValue(fields()[0]);
```

Como o converter sempre chama `.setIdAutorizacao(...)`, um `null` explícito passa e
produz um `SpecificRecord` inválido em silêncio. A falha só aparece adiante, em dois
pontos e de duas formas, ambas fora da classificação:

```
 payload com campo obrigatório nulo
   │
   ├─ A: id_autorizacao / data_hora_ultima_atlz
   │     └─ NPE em IdempotenciaKeyGenerator (chamado FORA do try/catch)
   │
   └─ B: data_fim_vigencia / data_hora_inclusao / codigo_canal_contratacao
         └─ SerializationException SÍNCRONA dentro de Producer.send()
            (o catch de produzir() só cobre Execution/Timeout/Interrupted)
   │
   └──▶ catch (Exception) genérico do listener → sem ack → reentrega infinita, sem DLQ
```

**Decisão:** validar explicitamente os **oito** campos declarados sem união
`["null", X]` no `.avsc` (`id_autorizacao`, `id_particao_conta`, `data_fim_vigencia`,
`tipo_produto`, `status`, `data_hora_inclusao`, `data_hora_ultima_atlz`,
`codigo_canal_contratacao`) imediatamente após a desserialização, lançando
`EventoAutorizacaoInvalidoException` com o nome do campo faltante. Fecha os dois caminhos
num único ponto, antes de qualquer conversão ou produce. Adicionalmente,
`IdempotenciaKeyGenerator.gerar()` passa para dentro do bloco de classificação — defesa em
profundidade, não substituto da validação.

> Os dois campos primitivos (`id_particao_conta: int`, `tipo_produto: long`) já eram
> pegos pelo `catch` do converter via NPE de auto-unboxing, mas com mensagem vaga.
> Validá-los junto dá diagnóstico nomeado ao custo de dois `if`.

**Alternativas descartadas:**
- `@JsonProperty(required = true)` — no Jackson, `required` só vale para
  `@JsonCreator`/deserialização de POJO e o comportamento com records é inconsistente;
  além disso a mensagem de erro resultante é ruim para diagnóstico.
- `spring-boot-starter-validation` + `@NotNull` no record — introduz dependência nova
  (fora do escopo) e um `Validator` para um check que são seis `if`.
- Alargar o `catch` de `KafkaEventoAutorizacaoProducer.produzir()` para
  `RuntimeException` — trataria o sintoma (caminho B) e classificaria erro de
  serialização como "Kafka indisponível" (retryable), piorando o problema.

### 4. `stop()` com `join()` limitado, sem bloquear o shutdown indefinidamente

`stop()` passa a: sinalizar `running=false` → `interrupt()` → `join(timeout)`. Esgotado o
timeout, loga aviso e retorna. O timeout deve ser maior que o pior caso de uma mensagem
(`GET_TIMEOUT_SECONDS = 20s`) — usar **30s**, alinhado ao visibility timeout da fila.

**Alternativa descartada:** `SmartLifecycle.stop(Runnable callback)`. Mais idiomático para
shutdown assíncrono, mas o `DefaultLifecycleProcessor` tem seu próprio
`timeoutPerShutdownPhase` (30s default) e a interação dos dois timeouts fica difícil de
raciocinar. O `stop()` síncrono com `join` limitado é mais previsível.

### 5. `HealthIndicator` separado do listener

`SqsListenerHealthIndicator` em `entrypoint/sqs/`, consultando o listener. Reporta:

| Estado | Health |
|---|---|
| listener ativo + thread viva | `UP` |
| listener ativo + thread morta | `DOWN` |
| listener parado (shutdown) | `UP` — parada intencional não é outage |

Exige expor no listener a informação de liveness da thread (hoje `isRunning()` só reflete
a flag `running`, que continua `true` se a thread morrer por `Error`).

**Alternativa descartada:** o próprio listener implementar `HealthIndicator`. Misturaria
duas responsabilidades numa classe que já tem seis.

### 6. `catch (Throwable)` no loop, não em `pollOnce()`

`pollOnce()` mantém `catch (Exception)`. O `catch (Throwable)` vai no `loopDeConsumo()`,
envolvendo a chamada a `pollOnce()` — assim um `Error` não mata a thread, e a distinção
entre "falha de um ciclo" e "falha catastrófica" fica legível. `pollOnce()` continua
package-private e testável isoladamente.

### 7. Logs: identificadores, nunca o body

| Onde | Hoje | Passa a ser |
|---|---|---|
| `ProcessarEventoAutorizacaoUseCase` sucesso (INFO) | `... : {mensagemJson}` | `idAutorizacao={} key={} tipoEvento={}` |
| `SqsEventoAutorizacaoListener` descarte (ERROR) | `(corpo: {message.body()})` | `messageId={}` + causa da exceção |
| Falha retryable (ERROR) | `messageId` (já ok) | inalterado |

As mensagens de `EventoAutorizacaoInvalidoException` também carregam `mensagemJson` hoje
(`"Body da mensagem não é um JSON válido: " + mensagemJson`) — como a exceção é logada com
stack trace, o body vazaria por ali. Passam a citar apenas a causa e o campo/posição.

**Trade-off aceito:** diagnosticar um payload malformado em produção fica mais difícil sem
o body no log. Mitigação: a mensagem de erro identifica a causa precisa (campo obrigatório
nulo `X`, JSON inválido na posição `N`, `status` desconhecido `Y`), e o `messageId` do SQS
permite recuperar a mensagem original enquanto ela existir. Uma mudança futura pode logar
o body sob `DEBUG` com mascaramento dos campos de PII.

### 8. Lombok no enum

`@NoArgsConstructor` em `StatusAutorizacao` é morto (nenhuma constante o invoca) e um
import de framework num arquivo que passa a viver em `domain/` — onde a regra é ser
livre de framework. A **anotação** sai nesta mudança, junto com a movimentação; o campo
`statusAutorizacao` passa a `final`. A **dependência Lombok no `pom.xml`** permanece,
para a mudança seguinte — depois desta remoção ela fica sem nenhum uso na app.

### 9. Correções da revalidação (2ª rodada)

A primeira rodada foi **reprovada** pelo agent `java-especialista`: os bloqueantes 1, 2 e 3
estavam apenas parcialmente fechados — cada um reabria a mesma falha por um gatilho
diferente. Decisões adicionais:

**9a. Precisão decimal, não só escala.** `escalar()` normaliza `setScale(2)` mas nunca a
precisão. Um `BigDecimal` com mais de 15 dígitos inteiros estoura `decimal(17,2)` na
conversão do Avro — que roda **dentro de `Producer.send()`**, e `publicador.produzir()`
estava fora de qualquer bloco de classificação. Duas defesas: validação de precisão no
`AutorizacaoEventoPayloadValidator`, e classificação de `SerializationException` no
adaptador Kafka como não-retryable (falha de serialização é problema de payload, não de
broker). *Alternativa descartada:* envolver `produzir()` num `catch (RuntimeException)`
amplo — reclassificaria indisponibilidade de broker como payload inválido, causando
descarte de mensagem boa.

**9b. PII vaza pela cadeia de causas.** O Jackson 3 redige o *source snippet* do JSON por
padrão, mas as mensagens de erro de coerção de tipo embutem o **valor do campo** — e
`id_pessoa_*` (UUID) e `valor`/`valor_limite` (BigDecimal) são exatamente os campos com
PII. Como o log de descarte imprime a stack trace inteira, o valor vazava pelo `cause`.
Decisão: nunca propagar exceção de terceiro como `cause`; usar
`JacksonException.getPathReference()`, que dá o caminho do campo **sem** o valor, mais o
nome da classe da exceção. Vale também para as exceções de conversão Avro.

**9c. Margem entre o join e o Spring.** `TIMEOUT_ENCERRAMENTO` era 30s, numericamente igual
ao default de `spring.lifecycle.timeout-per-shutdown-phase` — sem margem, o Spring podia
expirar a fase e destruir `SqsClient`/`Producer` com a thread ainda dentro do `join`,
anulando a Decisão 4. Passa a 25s, com `timeout-per-shutdown-phase: 40s` declarado
explicitamente no `application.yaml` para não depender do default.

**9d. Publicação segura de `pollingThread`.** O campo não era `volatile` e era atribuído
**depois** de `running = true` — ordem errada para publicação via variável de guarda
volátil. O health indicator (lido pela thread da requisição HTTP) podia ver
`running == true` com `pollingThread` ainda nulo, gerando falso-negativo. Passa a
`volatile`, atribuído antes de `running`, usando `Thread.ofVirtual().unstarted(...)`.

## Risks / Trade-offs

**[Movimentação em massa quebra imports não detectados]** → Os 28 testes existentes cobrem
todas as classes movidas e falham na compilação se um import ficar para trás. Rodar
`mvn clean package` (que também regenera as classes Avro) após cada bloco de movimentação,
não só ao final.

**[Validação nova rejeita mensagem que hoje é aceita]** → Se algum campo hoje marcado como
obrigatório no `.avsc` chegar nulo em produção com alguma frequência, a mudança passa a
descartar essas mensagens em vez de retê-las. Isso é o comportamento correto (hoje elas
travam a fila), mas é uma mudança de efeito observável. Mitigação: os campos validados são
exatamente os declarados obrigatórios no `.avsc`, que é o mesmo contrato que o
`eventos-consumer` já espera — nenhuma regra nova está sendo inventada.

**[`join(30s)` atrasa o shutdown]** → No pior caso o shutdown demora 30s a mais. Aceitável:
é exatamente a janela em que hoje se perde a garantia de ack. O `server.shutdown: graceful`
já existente opera na mesma ordem de grandeza.

**[`HealthIndicator` novo pode derrubar o health por engano]** → Se a lógica de liveness
tiver falso positivo, a app passa a reportar `DOWN` sem estar quebrada (e, em Kubernetes,
sai do balanceamento). Mitigação: reportar `DOWN` **apenas** na combinação
"listener ativo + thread não viva", nunca durante shutdown ou antes do start; teste
dedicado para os três estados.

**[PII deixa de estar no log e alguém dependia disso]** → Ver trade-off da decisão 7.
Comunicar a mudança de formato dos logs a quem opera.

**[Divergência do `.avsc` entre as duas apps]** → Esta mudança não toca o `.avsc`. Incluir
na verificação final um `diff` entre as duas cópias, que hoje são idênticas.

## Migration Plan

Sem migração de dados, sem mudança de contrato externo, sem feature flag. Ordem de
implementação (cada bloco compila e testa isoladamente):

1. **Correções de comportamento nos arquivos atuais** — validação de campos obrigatórios,
   `IdempotenciaKeyGenerator` sob classificação, logs sem body, `join()`,
   `catch (Throwable)`, `HealthIndicator`, testes novos. Feito **antes** de mover, para
   que o diff de comportamento seja legível separado do diff de movimentação.
2. **Movimentação de pacotes** — enums → `domain/enums/`, exceções →
   `shared/exceptions/`, listener → `entrypoint/sqs/`, producer + porta →
   `application/eventos/`. Diff puramente mecânico.
3. **Verificação** — `mvn clean package`, gate JaCoCo, `diff` dos dois `.avsc`, ausência
   de `infrastructure/` na árvore, revalidação pelo agent `java-especialista`.
4. **Documentação** — `CLAUDE.md` e `AGENTS.md` da app (espelhos, manter idênticos).

**Rollback:** `git revert` do merge. Nenhum estado externo é alterado — nenhuma migração
de schema, nenhuma mudança de configuração de fila ou tópico.

## Achado fora de escopo descoberto na 2ª rodada

**UUID malformado pode virar UUID fabricado, em silêncio.** Ao escrever o teste de PII
descobri que o `UUIDDeserializer` do Jackson só lança para strings de 36 caracteres (o
caminho de parse padrão). Comprimentos diferentes seguem outros caminhos — e uma string
de 22 caracteres foi **aceita e convertida** num UUID inventado:

```
"nao-e-uuid-12345678900"  →  9daa3e7b-ebae-89df-b5db-7e39ebbf3dd3
```

Ou seja: um `id_pessoa_pagadora` corrompido na origem não falha — é publicado no Kafka
como um UUID válido porém **errado**, que nenhum consumidor tem como distinguir de um
legítimo. É pior que uma exceção: corrupção silenciosa que atravessa a ponte.

Não corrigido nesta mudança por ser outra classe de problema (validação de formato do
contrato de entrada, não realinhamento de camadas nem os bloqueantes auditados). Registrar
na mudança seguinte, junto dos demais itens fora de escopo. A correção provável é validar
o formato dos campos UUID no `AutorizacaoEventoPayloadValidator`, ou desabilitar os
caminhos de coerção não-padrão do desserializador de UUID.

## Open Questions

- O timeout do `join()` deve ser configurável via `AwsProperties` ou constante no código?
  Proposta: constante (30s), alinhada às demais constantes já hardcoded no listener; a
  externalização de timeouts é item da mudança seguinte, que trata o conjunto todo.
- A validação de campos obrigatórios deve viver no `ProcessarEventoAutorizacaoUseCase` ou
  numa classe própria (`AutorizacaoEventoPayloadValidator`) em `application/eventos/`?
  Proposta: classe própria, pelo mesmo motivo que `EventoAutorizacaoConverter` e
  `IdempotenciaKeyGenerator` são classes próprias — mantém o use case como orquestrador e
  dá um alvo de teste unitário direto.
