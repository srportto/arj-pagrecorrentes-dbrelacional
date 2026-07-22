# CLAUDE.md

> Guia para agentes de IA (Claude Code, Copilot, etc.) trabalharem neste repositório.
> **Este arquivo e `AGENTS.md` são espelhos — mantenha-os idênticos ao editar.**

Listener da fila SQS `SQS-eventos-autorizacao`, em **arquitetura hexagonal**. Consome os
eventos de estado de autorização publicados pelo `arj-contratocommand` (via
`sns-estados-autorizacao` → SQS), loga o consumo com sucesso incluindo a representação
da entidade e confirma (ack) a mensagem. Nesta fase não há processamento de negócio —
apenas log + ack.

## Comece por aqui

Leia nesta ordem:
1. [SqsEventoAutorizacaoListener.java](src/main/java/br/com/srportto/autorizacaostatusproducer/infrastructure/sqs/SqsEventoAutorizacaoListener.java) — adapter de consumo (long polling + ack)
2. [ProcessarEventoAutorizacaoUseCase.java](src/main/java/br/com/srportto/autorizacaostatusproducer/application/eventos/ProcessarEventoAutorizacaoUseCase.java) — valida e loga o evento consumido
3. [AutorizacaoEventoPayload.java](src/main/java/br/com/srportto/autorizacaostatusproducer/application/eventos/AutorizacaoEventoPayload.java) — espelho do payload publicado pelo `arj-contratocommand`
4. [SqsClientConfig.java](src/main/java/br/com/srportto/autorizacaostatusproducer/shared/config/SqsClientConfig.java) — configuração do `SqsClient` (AWS SDK v2 puro)

## Build & Testes

```bash
mvn clean package                            # Compilar + testes + JAR
mvn spring-boot:run                          # Rodar localmente (porta 8082)
mvn test                                     # Todos os testes
```

> **Maven Wrapper**: este app não possui `mvnw`/`mvnw.cmd` — use `mvn` diretamente
> (mesma orientação do `arj-contratocommand` no Windows).

## Pré-requisitos

- **Java 25** (JDK 25+) — usa `public static void main()`; a forma `void main()` do Java 25 está pendente de suporte do maven plugin
- **Sem banco de dados** — esta app não usa JPA/PostgreSQL
- **Floci no ar** com o tópico `sns-estados-autorizacao`, a fila `SQS-eventos-autorizacao`
  e a subscription entre eles já aplicados (`infra/envs/local-messaging/`, ver
  [README](../../infra/envs/local-messaging/README.md))
- Variáveis de ambiente obrigatórias em `prod`: `AWS_REGION`, `AWS_SQS_QUEUE_URL` (no
  profile `local` há defaults apontando para o Floci)
- Profiles Spring: `local` (padrão de desenvolvimento) e `prod` (deve ser setado
  explicitamente via `SPRING_PROFILES_ACTIVE=prod`)

## Stack

| Componente | Versão | Notas |
|---|---|---|
| Java | 25 | `void main()` pendente do maven plugin |
| Spring Boot | 4.0.7 | Web MVC (só para o Actuator), Actuator |
| AWS SDK v2 | 2.49.0 | `software.amazon.awssdk:sqs` — sem Spring Cloud AWS |
| Lombok | 1.18.40 | uso mínimo (sem entidades JPA) |

## Endpoints reais

| Método | Caminho | Descrição |
|--------|---------|-----------|
| GET | `/actuator/health` | Health-check (Actuator). → 200 (UP) |

> **Não há endpoints REST de negócio** — esta app não expõe API própria, apenas consome
> a fila em background.

## Arquitetura (hexagonal)

```
application/eventos/    → ProcessarEventoAutorizacaoUseCase, AutorizacaoEventoPayload
infrastructure/sqs/     → SqsEventoAutorizacaoListener (adapter de consumo, SmartLifecycle)
shared/config/          → AwsProperties, SqsClientConfig (bean do SqsClient)
```

Sem camada `entrypoint/` nem `domain/` de entidades: não há API REST de negócio nem
persistência. `AutorizacaoEventoPayload` fica em `application/eventos/` (não em
`domain/`) porque é o contrato do evento consumido, não uma regra de negócio pura.

### Fluxo de consumo

```
SqsEventoAutorizacaoListener (SmartLifecycle)
  └─ start(): inicia virtual thread → loopDeConsumo()
       └─ pollOnce(): ReceiveMessage (long polling, WaitTimeSeconds=20, MaxNumberOfMessages=10)
            └─ processarEDarAck() por mensagem:
                 ├─ ProcessarEventoAutorizacaoUseCase.processar(body)
                 │    ├─ desserializa em AutorizacaoEventoPayload (valida a forma do evento)
                 │    └─ loga sucesso com o JSON recebido
                 └─ DeleteMessage (ack) — só se processar() não lançar exceção
  └─ stop(): sinaliza parada e interrompe a thread (shutdown gracioso)
```

Erro em `processar()` (ex.: JSON malformado) é logado e a mensagem **não** recebe ack —
volta à fila após o visibility timeout (semântica at-least-once). Erro em
`ReceiveMessage` (ex.: Floci fora do ar) aplica backoff de 5s sem encerrar o loop.

### Exceções e tratamento de erros

Esta app não tem `ApiExceptionHandler` — não há API REST de negócio para tratar erros
HTTP. Erros de consumo são tratados dentro do próprio
`SqsEventoAutorizacaoListener` (log + retenção da mensagem na fila).

## Armadilhas críticas

1. **Porta 8082** — diferente de `arj-contratocommand` (8080) e `arj-contratoquery` (8081).
2. **Sem banco de dados** — não adicione JPA/Postgres aqui; se precisar persistir algo,
   isso é uma mudança de escopo desta app.
3. **`AutorizacaoEventoPayload` é um espelho manual** do payload equivalente em
   `arj-contratocommand` (`application/eventos/AutorizacaoEventoPayload.java`) — os dois
   não compartilham código; se o schema do evento mudar lá, replique aqui.
4. **`pollOnce()` e `processarEDarAck()` são package-private de propósito** — permitem
   testar o adapter sem precisar rodar a thread real de polling.
5. **Sem outbox/DLQ/retry customizado nesta fase** — ver `design.md` da mudança
   `add-eventos-autorizacao-sns-sqs` para os trade-offs aceitos.

## Documentação relacionada

- [design.md da mudança](../../openspec/changes/add-eventos-autorizacao-sns-sqs/design.md) — decisões técnicas do fluxo de eventos (SNS/SQS, payload, sem outbox)
- [infra/envs/local-messaging/README.md](../../infra/envs/local-messaging/README.md) — como provisionar o tópico/fila no Floci

## Checklist antes do commit

- [ ] `mvn test` passa
- [ ] `mvn clean compile` sem erros
- [ ] Se mudou o payload, conferir consistência com `arj-contratocommand`
- [ ] Erros de processamento continuam sem dar ack (semântica at-least-once preservada)
