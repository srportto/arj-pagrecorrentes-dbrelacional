---

name: mensageria-sqs-kafka
description: "Reference for messaging in Java/Spring Boot hexagonal applications — SQS (visibility timeout, DLQ with `RedrivePolicy`, idempotency), Kafka (ordering by key, consumer group, retry/DLT, central error interceptor). Use when there is doubt about DLQ, idempotency, listener retry, or central error classification. Uso: agents `java-revisor` / `java-construtor` or manual invocation via `/mensageria-sqs-kafka`; não deve ser carregada proativamente pela sessão principal."
license: MIT
metadata:
  author: https://github.com/srportto/srportto
  co-author: https://github.com/Jeffallan/claude-skills
  version: "1.1.0"
  domain: messaging
  triggers: DLQ, SQS, Kafka, idempotência, visibility timeout, consumer group, retry, listener, DLT, redrive
  role: specialist
  scope: messaging
  output-format: code
  related-skills: arquitetura-limpa-java, criar-aplicacao-java, persistencia-jpa, monitoramento-java
---
---

# Mensageria SQS e Kafka

## Visão geral

Referência de bolso para mensageria em aplicações Java/Spring Boot hexagonais deste projeto: SQS,
Kafka, idempotência, DLQ/DLT e a escolha entre os dois modelos.

**Quando NÃO usar:** para gerar uma aplicação nova que já nasce consumindo fila/publicando em Kafka,
use `criar-aplicacao-java`. Para dúvida de camada, use `arquitetura-limpa-java`. Para o que logar em
um listener/consumer, use `padrao-de-logs-java`.

## 1. Onde a mensageria vive na arquitetura

Listener SQS e consumer Kafka são **adaptadores de ENTRADA** — vivem em `entrypoint/`, no mesmo nível
de um `@RestController`: recebem a mensagem e delegam para um service de `application/`, sem regra de
negócio própria. Produtor Kafka é **adaptador de SAÍDA**, encapsulado em `application/`. O domínio
nunca conhece o broker (nenhuma classe em `domain/` importa `io.awspring.cloud.*`,
`org.springframework.kafka.*` ou `com.fasterxml.jackson.*`).

```
entrypoint/                          application/
  PedidoSqsListener      ────▶         ProcessarPedidoService (valida, garante idempotencia)
  PedidoKafkaConsumer                      │
                                            ▼
                                          domain/ (regra de negocio pura)

  EventoController        ────▶         PublicarEventoService ──▶ KafkaTemplate.send(...)
                                        (adaptador de SAIDA, encapsula o KafkaTemplate)
```

## 2. Regra de ouro: toda fila SQS nasce com sua DLQ

**Nenhuma fila SQS deve ser criada — em Terraform, CLI ou qualquer IaC — sem uma DLQ e um
`RedrivePolicy` associados, nem em ambiente local.** Sem DLQ, uma mensagem "venenosa" (que sempre
lança exceção) fica em loop infinito de reentrega até o visibility timeout expirar de novo,
consumindo throughput sem nunca progredir e sem deixar rastro para investigação.

```hcl
# Terraform - fila principal + DLQ SEMPRE juntas, nunca uma sem a outra
resource "aws_sqs_queue" "fila_dlq" {
  name = "fila-pedidos-dlq"
}

resource "aws_sqs_queue" "fila" {
  name = "fila-pedidos"
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.fila_dlq.arn
    maxReceiveCount     = 3
  })
}
```

Via AWS CLI (LocalStack/Floci), a mesma regra em 3 passos: criar a DLQ, obter seu ARN, e só então
criar/atualizar a fila principal com `RedrivePolicy` apontando para ela:

```bash
aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name fila-pedidos-dlq
ARN_DLQ=$(aws --endpoint-url=http://localhost:4566 sqs get-queue-attributes \
  --queue-url http://localhost:4566/000000000000/fila-pedidos-dlq \
  --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)
aws --endpoint-url=http://localhost:4566 sqs set-queue-attributes \
  --queue-url http://localhost:4566/000000000000/fila-pedidos \
  --attributes "{\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"$ARN_DLQ\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"}"
```

`maxReceiveCount=3` é o default recomendado — 3 tentativas de entrega antes de mover para a DLQ.
**Toda auditoria de código de mensageria (agent `java-revisor` no modo `auditoria`, seção 8) deve reprovar uma fila
SQS nova ou alterada em IaC que não tenha DLQ associada.**

### Visibility timeout

Intervalo em que uma mensagem entregue fica invisível para os demais consumidores. Dimensione sempre
**maior que o tempo máximo de processamento** esperado — se o processamento puder demorar mais que o
visibility timeout, a mesma mensagem é entregue de novo a outro consumidor **enquanto a primeira
entrega ainda está em andamento**, gerando processamento concorrente duplicado.

Com `@SqsListener`: retorno normal deleta a mensagem automaticamente; exceção deixa a mensagem voltar
a ficar visível (até a DLQ, se configurada). Em listener manual (cliente SDK puro, sem
`@SqsListener`), o ack (`DeleteMessage`) é responsabilidade explícita do código — ver seção 3.

### Idempotência — SQS entrega ao-menos-uma-vez

SQS garante **at-least-once**: a mesma mensagem pode chegar mais de uma vez. O processamento precisa
ser idempotente.

| Aspecto | Em memória (`Set` concorrente) | Persistente (constraint única no banco) |
|---|---|---|
| Sobrevive a reinício | Não | Sim |
| Múltiplas instâncias | Não — cada uma tem seu `Set` | Sim — todas consultam o mesmo banco |
| Race condition | Não protegido | Protegido pela constraint (a 2ª gravação falha/é ignorada) |
| Quando usar | PoC, app de instância única | Produção real, múltiplas instâncias |

## 3. Interceptor central de erro de consumo (equivalente ao `ApiExceptionHandler`)

Assim como o lado REST tem um único `@ControllerAdvice`/`ApiExceptionHandler` classificando toda
exceção HTTP num só lugar, **todo erro ocorrido no escopo de consumo de uma mensageria (SQS ou
Kafka) deve passar por um ponto único de classificação** — nunca por `catch` espalhados dentro do
próprio listener/consumer, um por cenário descoberto ao longo do tempo.

A forma concreta desse ponto único **muda conforme o framework de consumo**, mas o princípio é o
mesmo: uma classe/config dedicada decide, para cada exceção, se a mensagem é descartada
(retryable=false) ou devolvida para nova tentativa (retryable=true), com log ERROR do identificador
da mensagem — nunca do body (ver `padrao-de-logs-java`).

**Listener manual (SDK puro, sem `@SqsListener`)** — uma classe dedicada, injetada no listener, que
recebe a exceção e devolve a decisão de ack:

```java
// entrypoint/sqs/PedidoErrorInterceptor.java — ponto unico de classificacao, listener so delega
@Component
public class PedidoErrorInterceptor {

    private static final Logger log = LoggerFactory.getLogger(PedidoErrorInterceptor.class);

    /** @return true se deve dar ack (descarte consciente); false se deve voltar a fila. */
    public boolean tratar(Message message, Exception e) {
        if (e instanceof PedidoInvalidoException) {
            log.error("Mensagem não-retryable descartada: messageId={}", message.messageId(), e);
            return true;
        }
        log.error("Falha ao processar messageId={}. Retorna a fila.", message.messageId(), e);
        return false;
    }
}

// no listener: processarEDarAck() so chama o use case e delega a excecao ao interceptor
try {
    useCase.processar(message.body());
    ack(queueUrl, message);
} catch (Exception e) {
    if (errorInterceptor.tratar(message, e)) {
        ack(queueUrl, message);
    }
}
```

**`@KafkaListener` (spring-kafka)** — o framework já oferece o ponto único pronto: um
`DefaultErrorHandler` central com `DeadLetterPublishingRecoverer`, configurado uma vez em
`shared/config/`, nunca com `try/catch` dentro do método do listener:

```java
@Bean
DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafkaTemplate) {
    var recuperador = new DeadLetterPublishingRecoverer(kafkaTemplate);
    return new DefaultErrorHandler(recuperador, new FixedBackOff(1000L, 3));
}
```

Com `@SqsListener` gerenciado pelo Spring (não cliente SDK puro), o equivalente é um
`SqsMessageListenerErrorHandler` central, pelo mesmo motivo.

**O que reprova em revisão:** classificação de exceção duplicada em mais de um lugar do código;
`try/catch` genérico dentro do método do listener que decide ack/retry inline em vez de delegar;
qualquer novo tipo de exceção de mensageria tratado ad-hoc fora do ponto central existente.

## 4. Kafka produtor

- **Chave de partição define ordem** — mensagens com a mesma chave vão para a mesma partição, ordem
  garantida dentro dela. Use um id de negócio estável (ex.: id do pedido):
  ```java
  kafkaTemplate.send(topicoPedidos, pedido.id(), mensagemJson);
  ```
- **`acks=all`** para durabilidade — aguarda confirmação de todas as réplicas antes de considerar
  publicado (em vez de `acks=0`/`1`, que arriscam perda na falha do líder).
- **Dependência correta**: `spring-boot-starter-kafka` (autoconfigura `KafkaTemplate`). **Não** use
  `spring-kafka` isolado — sem o starter, falta o bean e o contexto quebra com
  `NoSuchBeanDefinitionException`. O contexto sobe **sem** broker no ar; o producer reconecta em
  background.

```yaml
spring:
  kafka:
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: all
```

## 5. Kafka consumidor

- **Consumer group**: consumidores do mesmo `group-id` dividem as partições entre si — 1 partição =
  no máximo 1 consumidor ativo do grupo por vez. Escala horizontal = mais partições + mais instâncias.
- **`auto-offset-reset`**: `earliest` lê desde o início (não perde mensagens antigas); `latest` só lê
  a partir da conexão do consumer.
- **Retry + DLT**: ver `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` na seção 3 — 3
  tentativas com 1s de backoff, depois publica em `<topico>.DLT` automaticamente. Com
  `@KafkaListener`: retorno normal avança o offset; exceção mantém o offset e reentrega conforme a
  política de retry, até esgotar e ir para o DLT.

## 6. Decisão SQS × Kafka

| Aspecto | SQS (fila ponto-a-ponto) | Kafka (log de eventos) |
|---|---|---|
| Consumo | Destrutivo — mensagem some após ack; 1 consumidor lógico por mensagem | Não destrutivo — permanece pelo tempo de retenção; múltiplos consumer groups leem o mesmo evento |
| Replay | Não (salvo reprocessamento manual antes da exclusão) | Sim — novo consumer group ou reset de offset |
| Ordenação | Só com FIFO queue (não é o padrão) | Garantida por partição, via chave |
| Quando usar | Trabalho a executar exatamente uma vez por um processador | Histórico de eventos que múltiplos consumidores independentes precisam ler, auditoria, replay |

## 7. Erros comuns

| Erro | Consequência | Correção |
|---|---|---|
| Fila SQS sem DLQ | Mensagem venenosa reentrega para sempre, sem rastro para investigação | Seção 2 — DLQ + `RedrivePolicy` obrigatórios em toda fila |
| Erro de consumo tratado inline, sem ponto central | Classificação duplicada/inconsistente entre mensagens; novo cenário de falha vira mais um `catch` solto | Seção 3 — interceptor/`DefaultErrorHandler` central, único |
| Processar sem idempotência | Reentrega at-least-once duplica o efeito (grava/cobra duas vezes) | Seção 2 — `Set` em memória (didático) ou constraint única (produção) |
| Commit manual de offset sem necessidade | Complexidade extra sem ganho — o commit automático do `@KafkaListener` já cobre o caso comum | Use o commit automático padrão |
| Visibility timeout menor que o tempo de processamento | Mesma mensagem entregue de novo a outro consumidor **durante** o processamento em andamento | Dimensione acima do tempo máximo esperado |
| Logar payload inteiro com dados sensíveis | PII vaza para o log estruturado | Ver `padrao-de-logs-java` — nunca o body, só ids |

## 8. Validação

- **Aplicação de mensageria nova**: gere via `criar-aplicacao-java`, que invoca `java-revisor` (modo `auditoria`)
  como validação obrigatória (achados críticos bloqueiam a entrega).
- **Mudança pontual em código de mensageria existente**: revise com `java-revisor`, aplicando
  `revisao-de-codigo-java` (que referencia `padrao-de-logs-java` para logs).
- **`java-revisor` (modo `auditoria`) valida explicitamente**, quando o código tocar mensageria: (1) toda fila SQS
  nova/alterada em IaC tem DLQ + `RedrivePolicy`; (2) existe um ponto único de classificação de erro
  de consumo (interceptor dedicado ou `DefaultErrorHandler`/`SqsMessageListenerErrorHandler`), não
  `catch` espalhados no listener.

## Skills e agents relacionados

| Situação | Use |
|---|---|
| Criar aplicação nova que já nasce consumindo fila/publicando em Kafka | skill `criar-aplicacao-java` |
| Dúvida sobre em qual camada uma classe de mensageria deve viver | skill `arquitetura-limpa-java` |
| O que logar em um listener/consumer, e o que nunca logar | skill `padrao-de-logs-java` |
| Checklist completo de revisão de código (mensageria é um item entre vários) | skill `revisao-de-codigo-java` |
| Validação de aplicação nova gerada, DLQ e interceptor de erro | agent `java-revisor` (modo `auditoria`) |
| Revisão de diff pontual em código de mensageria existente | agent `java-revisor` |
