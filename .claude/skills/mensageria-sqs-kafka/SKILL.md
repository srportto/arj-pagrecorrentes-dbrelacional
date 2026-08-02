---
name: mensageria-sqs-kafka
description: Use quando precisar trabalhar com mensageria em aplicações Java - listeners de fila SQS, DLQ, idempotência, produtores e consumidores Kafka, retries, serialização e ordenação. Gatilhos - "consumir fila", "SQS", "Kafka", "DLQ", "mensagem duplicada", "consumer group", "retry de mensagem".
---

# Mensageria SQS e Kafka

## Visão geral

Referência de bolso para decisões de mensageria em aplicações Java/Spring Boot hexagonais neste
projeto: consumo de fila **AWS SQS**, produção e consumo de **Apache Kafka**, idempotência, Dead
Letter Queue/Topic e a escolha entre os dois modelos de mensageria. Use esta skill sempre que surgir
dúvida sobre visibility timeout, DLQ, idempotência de mensagem, ordenação por chave Kafka, consumer
group ou retry — em uma aplicação já existente.

**Quando NÃO usar:** para gerar o esqueleto de uma aplicação nova que já nasce consumindo fila ou
publicando em Kafka, use a skill `criar-aplicacao-java` (que tem as variantes `sqs-listener`,
`sqs-para-banco`, `sqs-para-kafka`, `kafka-consumer` e `rest-para-kafka` prontas e já validadas). Para
dúvida sobre em qual camada uma classe de mensageria deve viver, use `arquitetura-limpa-java`. Para
padrão de logging (o que logar em um listener/consumer), use `padrao-de-logs-java`.

## 1. Onde a mensageria vive na arquitetura

Listener SQS e consumer Kafka são **adaptadores de ENTRADA** — vivem em `entrypoint/`, no mesmo nível
de um `@RestController`: recebem a mensagem, delegam para um service de `application/` e não contêm
regra de negócio.

Produtor Kafka (`KafkaTemplate`) é um **adaptador de SAÍDA** — fica encapsulado dentro de um service
de `application/` (nunca é injetado direto num controller ou listener de outro contexto). O domínio
nunca conhece o broker: nenhuma classe em `domain/` importa `io.awspring.cloud.*`,
`org.springframework.kafka.*` ou `com.fasterxml.jackson.*`.

```
entrypoint/                          application/
  PedidoSqsListener      ────▶         ProcessarPedidoService / GravarPedidoService
  PedidoKafkaConsumer     (recebe)      (valida, garante idempotencia, ...)
                                            │
                                            ▼
                                          domain/
                                          Pedido.validar()   (regra de negocio pura)

  EventoController        ────▶         PublicarEventoService  ──▶  KafkaTemplate.send(...)
  (adaptador de entrada)                (adaptador de SAIDA, encapsula o KafkaTemplate)
```

Ver o mapa completo de camadas e o checklist de revisão arquitetural na skill
`arquitetura-limpa-java` — a tabela "Que classe vai em qual camada" já lista `PedidoSqsListener`,
`PedidoKafkaConsumer` e `PublicarEventoService` como exemplos.

## 2. SQS

### Visibility timeout

É o intervalo em que uma mensagem entregue a um consumidor fica **invisível** para os demais
consumidores da fila, dando tempo do processamento terminar antes de a mensagem voltar a ficar
disponível para nova entrega. Dimensione sempre **maior que o tempo máximo de processamento**
esperado da mensagem — se o processamento (chamada a banco, integração externa, etc.) puder demorar
mais que o visibility timeout, a mesma mensagem é entregue de novo a outro consumidor **enquanto a
primeira entrega ainda está em andamento**, gerando processamento concorrente duplicado da mesma
mensagem.

### DLQ (Dead Letter Queue) com `RedrivePolicy`

Sem DLQ, uma mensagem "venenosa" (que sempre lança exceção no processamento) fica sendo reentregue
indefinidamente. Configure uma DLQ com `maxReceiveCount=3` para mover a mensagem para investigação
manual após 3 falhas:

```bash
# 1. Criar a fila de DLQ
aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name fila-pedidos-dlq

# 2. Obter o ARN da DLQ
aws --endpoint-url=http://localhost:4566 sqs get-queue-attributes \
  --queue-url http://localhost:4566/000000000000/fila-pedidos-dlq \
  --attribute-names QueueArn

# 3. Configurar a fila principal para redirecionar apos 3 tentativas (usar o ARN obtido acima)
aws --endpoint-url=http://localhost:4566 sqs set-queue-attributes \
  --queue-url http://localhost:4566/000000000000/fila-pedidos \
  --attributes '{"RedrivePolicy":"{\"deadLetterTargetArn\":\"<ARN-DA-DLQ>\",\"maxReceiveCount\":\"3\"}"}'
```

Com `@SqsListener`: se o método do listener retornar normalmente, a mensagem é deletada
automaticamente da fila; se lançar exceção, a mensagem volta a ficar visível para nova tentativa (até
a DLQ, se configurada).

### Idempotência — SQS entrega ao-menos-uma-vez

SQS garante entrega **at-least-once**: a mesma mensagem pode chegar mais de uma vez ao consumidor
(reentrega após timeout, retry de rede, etc.). O processamento **precisa** ser idempotente — processar
a mesma mensagem duas vezes não pode duplicar o efeito (gravar duas vezes, cobrar duas vezes). Este
catálogo tem duas estratégias implementadas, com trade-offs diferentes:

| Aspecto | Idempotência em memória | Idempotência persistente |
|---|---|---|
| Mecanismo | `Set<String>` (`ConcurrentHashMap.newKeySet()`) dos ids já processados | `unique constraint` no banco (`id_pedido`) + `existsByIdPedido()` antes de gravar |
| Sobrevive a reinício da aplicação | Não — o `Set` é perdido | Sim — o dado está no banco |
| Funciona com múltiplas instâncias | Não — cada instância tem seu próprio `Set` | Sim — todas as instâncias consultam o mesmo banco |
| Race condition entre instâncias | Não protegido | Protegido pela constraint única (a segunda gravação falha/é ignorada mesmo em corrida) |
| Quando usar | Prova de conceito, exemplo didático, app com uma única instância sem exigência forte de durabilidade | Produção real, múltiplas instâncias, exigência de nunca duplicar o efeito |

Exemplos executáveis:
- `.claude/skills/criar-aplicacao-java/assets/overlays/sqs-listener/` — idempotência em memória
  (`ProcessarPedidoService`), didática, ver nota do próprio `LEIAME.md`: "em produção, use uma tabela
  de controle (banco) ou cache distribuído (Redis)".
- `.claude/skills/criar-aplicacao-java/assets/overlays/sqs-para-banco/` — idempotência persistente
  (`GravarPedidoService` + `PedidoEntity` com `@Column(name = "id_pedido", unique = true)` +
  `PedidoRepository.existsByIdPedido`), superior para produção pelos motivos da tabela acima.

## 3. Kafka produtor

- **Chave de partição define ordem**: mensagens com a mesma chave sempre vão para a mesma partição, e
  dentro de uma partição a ordem de leitura é garantida. Use um id de negócio estável como chave (ex.:
  id do pedido) para garantir que eventos do mesmo pedido sejam processados em ordem:

  ```java
  // chave = id do pedido: garante ordem por pedido dentro da particao
  kafkaTemplate.send(topicoPedidos, pedido.id(), mensagemJson);
  ```

- **`acks=all`** para durabilidade: o producer aguarda confirmação de todas as réplicas do broker antes
  de considerar a mensagem publicada com sucesso (em vez de `acks=0`/`1`, que arriscam perda em caso de
  falha do líder da partição).

  ```yaml
  spring:
    kafka:
      producer:
        key-serializer: org.apache.kafka.common.serialization.StringSerializer
        value-serializer: org.apache.kafka.common.serialization.StringSerializer
        acks: all   # durabilidade: espera confirmacao de todas as replicas
  ```

- **Serialização**: `StringSerializer` para chave e valor — o payload já sai como JSON serializado
  (string), sem exigir um serializer Kafka específico de JSON.

- **Dependência correta**: `org.springframework.boot:spring-boot-starter-kafka` (starter modular do
  Boot 4, autoconfigura o `KafkaTemplate`). **Não** use `org.springframework.kafka:spring-kafka`
  isolado — sem o starter, o `KafkaTemplate` não é autoconfigurado e o contexto falha com
  `NoSuchBeanDefinitionException`. O contexto sobe **sem** broker Kafka no ar: o producer conecta sob
  demanda, reconectando em background.

Exemplos executáveis:
- `.claude/skills/criar-aplicacao-java/assets/overlays/rest-para-kafka/` — endpoint REST (`POST /eventos`) que
  publica no Kafka via `PublicarEventoService`, produtor encapsulado como adaptador de saída na
  camada `application`.
- `.claude/skills/criar-aplicacao-java/assets/overlays/sqs-para-kafka/` — ponte SQS → Kafka: `RepassarPedidoService`
  consome da fila e republica no tópico, chave = id do pedido.

## 4. Kafka consumidor

- **Consumer group**: consumidores do mesmo `group-id` dividem as partições do tópico entre si — cada
  partição é lida por, no máximo, um consumidor do grupo por vez. É o mecanismo de escala horizontal:
  para processar mais rápido, aumente o número de partições e de instâncias do consumidor (até o
  limite de 1 partição = 1 consumidor ativo do grupo; instâncias além do número de partições ficam
  ociosas).

  ```yaml
  spring:
    kafka:
      consumer:
        group-id: ${spring.application.name}
        auto-offset-reset: earliest
  ```

- **`auto-offset-reset`**: define de onde o consumer começa a ler quando não há offset salvo para o
  grupo (primeira vez que o tópico é lido por aquele `group-id`). `earliest` lê desde o primeiro
  offset armazenado (não perde mensagens antigas); `latest` só lê mensagens publicadas a partir do
  momento em que o consumer se conecta.

- **Retry com backoff + Dead Letter Topic (DLT)**: em vez de deixar uma mensagem "venenosa" travar o
  consumer em loop infinito, configure um `DefaultErrorHandler` com backoff fixo e um
  `DeadLetterPublishingRecoverer`, que publica automaticamente no tópico `<topico>.DLT` após esgotar as
  tentativas:

  ```java
  package br.com.srportto.appbase.shared.config;

  import org.springframework.context.annotation.Bean;
  import org.springframework.context.annotation.Configuration;
  import org.springframework.kafka.core.KafkaTemplate;
  import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
  import org.springframework.kafka.listener.DefaultErrorHandler;
  import org.springframework.util.backoff.FixedBackOff;

  // resiliencia do consumer: 3 tentativas com 1s de intervalo, depois envia para o DLT
  @Configuration
  public class KafkaConsumerConfig {

      @Bean
      DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafkaTemplate) {
          var recuperador = new DeadLetterPublishingRecoverer(kafkaTemplate);
          return new DefaultErrorHandler(recuperador, new FixedBackOff(1000L, 3));
      }
  }
  ```

  Com `@KafkaListener`: se o método retornar normalmente, o offset avança (confirmação implícita); se
  lançar exceção, o offset não avança e a mensagem é reentregue conforme a política de retry acima —
  até esgotar as tentativas e ir para o DLT.

  Exemplo executável:
  - `.claude/skills/criar-aplicacao-java/assets/overlays/kafka-consumer/` — `PedidoKafkaConsumer` +
    `KafkaConsumerConfig` (código acima, idêntico) + `ProcessarPedidoService` com idempotência em
    memória.

## 5. Decisão SQS × Kafka

| Aspecto | SQS (fila ponto-a-ponto) | Kafka (log de eventos) |
|---|---|---|
| Modelo de consumo | Destrutivo — a mensagem some da fila após confirmação; um único consumidor lógico processa cada mensagem | Não destrutivo — a mensagem permanece no tópico pelo tempo de retenção configurado; múltiplos consumer groups podem ler o mesmo evento independentemente |
| Replay | Não é possível reler uma mensagem já confirmada (a menos que reprocessada manualmente antes da exclusão) | Possível — um novo consumer group (ou reset de offset) relê o histórico do tópico |
| Múltiplos consumidores independentes do mesmo dado | Não — cada mensagem é consumida uma única vez por um dos consumidores da fila | Sim — cada consumer group recebe sua própria cópia lógica do stream |
| Ordenação | Só com FIFO queue (não é o padrão) | Garantida por partição, via chave |
| Quando usar | Trabalho a ser executado exatamente uma vez por um processador (fila de tarefas, comando pontual, integração ponto-a-ponto) | Histórico de eventos de negócio que múltiplos consumidores independentes precisam ler, auditoria, replay, streaming |

Na prática deste catálogo: `sqs-listener`/`sqs-para-banco` são exemplos de fila de trabalho
ponto-a-ponto; `sqs-para-kafka` é a ponte que leva um evento de fila para um log de eventos com
replay, quando outros consumidores além do processador original também precisam do dado.

## 6. Erros comuns

| Erro | Consequência | Correção |
|---|---|---|
| Processar sem idempotência | SQS/Kafka entregam ao-menos-uma-vez; reentrega duplica o efeito (grava duas vezes, cobra duas vezes) | Ver seção 2 — `Set` em memória (didático) ou constraint única no banco (produção); overlays `sqs-listener`/`sqs-para-banco` |
| Ignorar DLQ/DLT | Mensagem venenosa fica em loop infinito de reentrega, consumindo throughput do consumer sem nunca progredir | Configurar `RedrivePolicy` (SQS, seção 2) ou `DeadLetterPublishingRecoverer` (Kafka, seção 4) |
| Commit manual de offset sem necessidade | Complexidade extra (`AckMode.MANUAL`) sem ganho — o commit automático do `@KafkaListener` (offset avança só quando o método retorna sem exceção) já cobre o caso comum de "processar e confirmar" | Use o commit automático padrão; só passe para manual quando precisar confirmar em batch ou desacoplar processamento de confirmação por razão explícita de negócio |
| Visibility timeout menor que o tempo de processamento | A mesma mensagem SQS é entregue de novo a outro consumidor **enquanto a primeira entrega ainda está processando**, causando processamento concorrente duplicado | Dimensione o visibility timeout acima do tempo máximo esperado de processamento (seção 2) |
| Logar payload inteiro com dados sensíveis | CPF, token, dado pessoal completo do payload da mensagem vazam para o log estruturado | Ver skill `padrao-de-logs-java` — regra de ouro "nunca logar" (seção 1) e "o que logar em cada camada hexagonal" (seção 5): no `entrypoint`, logue chegada/confirmação e ids, não o payload bruto completo |

## 7. Validação

- **Aplicação de mensageria nova** (listener SQS, consumer Kafka, ponte SQS→Kafka, produtor Kafka):
  gere via `criar-aplicacao-java`, que já invoca o agent `java-especialista` como validação obrigatória
  (achados críticos bloqueiam a entrega).
- **Mudança pontual em código de mensageria já existente** (ajustar idempotência, adicionar DLQ,
  revisar retry): revise com o agent `java-revisor` — feedback tempestivo sobre o diff, aplicando o
  checklist da skill `revisao-de-codigo-java` (que referencia `padrao-de-logs-java` para a parte de
  logs).

## Skills e agents relacionados

| Situação | Use |
|---|---|
| Criar uma aplicação nova que já nasce consumindo fila/publicando em Kafka | skill `criar-aplicacao-java` |
| Dúvida sobre em qual camada uma classe de mensageria deve viver | skill `arquitetura-limpa-java` |
| O que logar em um listener/consumer, e o que nunca logar | skill `padrao-de-logs-java` |
| Checklist completo de revisão de código (mensageria é um item entre vários) | skill `revisao-de-codigo-java` |
| Validação de aplicação nova gerada | agent `java-especialista` (acionado por `criar-aplicacao-java`) |
| Revisão de diff pontual em código de mensageria existente | agent `java-revisor` |
