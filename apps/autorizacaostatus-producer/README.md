# Autorizacaostatus Producer

Listener Java 25 / Spring Boot 4.0.7 da fila SQS `SQS-eventos-autorizacao`, em
**arquitetura hexagonal**. Consome os eventos de estado de autorização (criação e
cancelamento) publicados pelo `arj-contratocommand` no tópico `sns-estados-autorizacao`,
loga o consumo com sucesso incluindo a representação da entidade e confirma (ack) a
mensagem. Nesta fase não há processamento de negócio — apenas log + ack, comprovando o
fluxo de eventos ponta a ponta.

## Funcionalidades

- **Consumo via long polling**: `SqsClient` (AWS SDK v2 puro, sem Spring Cloud AWS) em
  loop numa virtual thread dedicada
- **Log de sucesso**: cada mensagem consumida é logada com a representação JSON da
  autorização
- **Ack após sucesso**: `DeleteMessage` só ocorre se o processamento não lançar exceção
  — erro mantém a mensagem na fila (semântica at-least-once)
- **Health-check**: `GET /actuator/health` via Spring Actuator

## Stack Técnico

| Componente | Versão | Descrição |
|---|---|---|
| **Java** | 25 | `void main()` pendente de suporte do maven plugin |
| **Spring Boot** | 4.0.7 | Web MVC (Actuator), IoC |
| **AWS SDK v2** | 2.49.0 | `software.amazon.awssdk:sqs` |
| **Lombok** | 1.18.40 | uso mínimo — sem entidades JPA |
| **Maven** | 3.9+ | Build e gerenciamento de dependências |

## Estrutura do Projeto

```
src/main/java/br/com/srportto/autorizacaostatusproducer/
├── AutorizacaostatusProducerApplication.java
├── application/
│   └── eventos/
│       ├── AutorizacaoEventoPayload.java        # espelho do payload publicado pelo command
│       └── ProcessarEventoAutorizacaoUseCase.java
├── infrastructure/
│   └── sqs/
│       └── SqsEventoAutorizacaoListener.java    # adapter de consumo (SmartLifecycle)
└── shared/
    └── config/
        ├── AwsProperties.java
        └── SqsClientConfig.java
```

Sem `entrypoint/` nem `domain/`: esta app não expõe API REST de negócio e não tem
entidades persistidas — apenas consome uma fila e loga.

## Arquitetura Hexagonal

| Camada | Pacote | Responsabilidade |
|--------|--------|-----------------|
| **Application** | `application/eventos/` | Valida e loga o evento consumido |
| **Infrastructure** | `infrastructure/sqs/` | Adapter de consumo SQS (porta de entrada) |
| **Shared** | `shared/config/` | Configuração do `SqsClient` e propriedades AWS |

### Fluxo de consumo

```
SqsEventoAutorizacaoListener (SmartLifecycle)
  start() → virtual thread → loopDeConsumo()
    pollOnce(): ReceiveMessage (long polling, WaitTimeSeconds=20, MaxNumberOfMessages=10)
      processarEDarAck() por mensagem:
        ProcessarEventoAutorizacaoUseCase.processar(body)
          → desserializa em AutorizacaoEventoPayload (valida a forma do evento)
          → loga sucesso com o JSON recebido
        DeleteMessage (ack) — só se processar() não lançar exceção
  stop() → sinaliza parada e interrompe a thread (shutdown gracioso)
```

Erro em `processar()` (ex.: JSON malformado) é logado e a mensagem **não** recebe ack —
volta à fila após o visibility timeout. Erro em `ReceiveMessage` (ex.: Floci fora do ar)
aplica backoff de 5s sem encerrar o loop.

## Como Executar

### Pré-requisitos

- **Java 25** (JDK 25+)
- **Maven 3.9+** (use `mvn` diretamente — este app não tem `mvnw`)
- **Floci no ar** com o tópico, a fila e a subscription já aplicados via
  [`infra/envs/local-messaging/`](../../infra/envs/local-messaging/)

### Variáveis de Ambiente

```bash
# Obrigatórias apenas em prod (o profile local já tem defaults do Floci)
AWS_REGION=us-east-1
AWS_SQS_QUEUE_URL=https://sqs.us-east-1.amazonaws.com/<conta>/SQS-eventos-autorizacao

# Spring Profiles (opcional; padrão de desenvolvimento é "local" quando omitido)
SPRING_PROFILES_ACTIVE=local    # local ou prod — produção DEVE setar explicitamente "prod"
```

### Build & Execução

```bash
# Compilar + testes + JAR
mvn clean package

# Rodar localmente (porta 8082)
mvn spring-boot:run

# Via JAR
java -jar target/autorizacaostatus-producer-0.0.1-SNAPSHOT.jar
```

### Testar o fluxo local

```bash
# 1. Suba o Floci e aplique infra/envs/local-messaging (ver READMEs correspondentes)
# 2. Suba esta app (mvn spring-boot:run)
# 3. Em outro terminal, publique um evento de teste diretamente no tópico:
export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test
aws --endpoint-url http://localhost:4566 --region us-east-1 sns publish \
  --topic-arn arn:aws:sns:us-east-1:000000000000:sns-estados-autorizacao \
  --message '{"id_autorizacao":"00000000-0000-0000-0000-000000000000","status":4}'
# 4. Confira o log desta app: deve aparecer "consumida com sucesso"
```

## Testes

```bash
# Todos os testes
mvn test

# Com relatório de cobertura (JaCoCo)
mvn clean verify
# Abrir: target/site/jacoco/index.html
```

> Testes unitários rodam sem infraestrutura externa — o `SqsClient` é mockado.

## Armadilhas Críticas

1. **Porta 8082**, não 8080 (`arj-contratocommand`) nem 8081 (`arj-contratoquery`).
2. **Sem banco de dados** — não há JPA/Postgres nesta app.
3. **`AutorizacaoEventoPayload` é um espelho manual** do payload publicado pelo
   `arj-contratocommand` — os dois não compartilham código-fonte; mudanças no schema do
   evento precisam ser replicadas nos dois lados.
4. **`pollOnce()`/`processarEDarAck()` são package-private de propósito**, para permitir
   testar o adapter sem rodar a thread real de polling.
5. **Sem outbox/DLQ/retry customizado nesta fase** — ver `design.md` da mudança
   `add-eventos-autorizacao-sns-sqs` no repositório para os trade-offs aceitos.

## Informações do Projeto

**Grupo:** br.com.srportto
**Artifact:** autorizacaostatus-producer
**Versão:** 0.0.1-SNAPSHOT
**Java:** 25 | **Spring Boot:** 4.0.7 | **Porta:** 8082
