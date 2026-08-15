# Tasks: add-eventos-autorizacao-sns-sqs

## 1. Infra — root Terraform `infra/envs/local-messaging/`

- [x] 1.1 Criar o root `infra/envs/local-messaging/` (`versions.tf`, `providers.tf`
      com endpoints sns/sqs/sts em `http://localhost:4566`, credenciais `test`/`test`,
      flags `skip_*`, state local; `variables.tf` com região `us-east-1`)
- [x] 1.2 Criar `main.tf` com `aws_sns_topic` `sns-estados-autorizacao`,
      `aws_sqs_queue` `SQS-eventos-autorizacao`, `aws_sqs_queue_policy` (permitindo
      `sns.amazonaws.com` → `sqs:SendMessage` condicionado ao ARN do tópico) e
      `aws_sns_topic_subscription` com `raw_message_delivery = true`
- [x] 1.3 Criar `outputs.tf` (ARN do tópico e URL da fila) e `README.md` do root
      (pré-requisito Floci, comandos de apply/destroy)
- [x] 1.4 Validar: com o Floci no ar, `terraform init && terraform apply`; conferir
      tópico via `sns list-topics`, fila via `sqs list-queues`; publicar mensagem de
      teste no tópico via CLI e confirmar body cru na fila (`sqs receive-message`)

## 2. Publicador — `contratocommand`

- [x] 2.1 Adicionar ao `pom.xml` o BOM `software.amazon.awssdk:bom` e a dependência
      `software.amazon.awssdk:sns`
- [x] 2.2 Criar propriedades de configuração (`aws.endpoint`, `aws.region`,
      `aws.sns.topic-arn`) com defaults do Floci no `application-local.yaml` e
      placeholders via variáveis de ambiente no `application-prod.yaml`; criar
      `@Configuration` do `SnsClient` (endpoint override + credenciais estáticas no
      profile local)
- [x] 2.3 Criar o record de payload com chaves = nomes das colunas da tabela
      `autorizacoes` (incluindo colunas embutidas de cancelamento e `metadados` como
      objeto JSON) e o mapeamento a partir da entidade `Autorizacao`
- [x] 2.4 Criar o evento de domínio interno (record com a entidade persistida + tipo
      `CRIACAO`/`CANCELAMENTO`) e publicá-lo via `ApplicationEventPublisher` ao final
      de `CriarAutorizacaoUseCase.execute()` e `CancelarAutorizacaoUseCase.execute()`
      (1 evento lógico por operação, estado final)
- [x] 2.5 Criar o listener `@TransactionalEventListener(phase = AFTER_COMMIT)` que
      serializa o payload (Jackson 3) e publica no tópico com message attribute
      `tipoEvento`; falha de publish apenas loga erro com id da autorização e tipo do
      evento (não propaga)
- [x] 2.6 Testes: publicação disparada na criação e no cancelamento (estado final,
      evento único na troca de partição), rollback não publica, falha de SNS não
      afeta a resposta; mapeamento entidade→payload com chaves das colunas
- [x] 2.7 Validar: `mvn test` e `mvn clean compile` passam em
      `apps/contratocommand`

## 3. Consumidor — nova app `apps/autorizacaostatus-producer`

- [x] 3.1 Criar o esqueleto da app a partir da `contratocommand` e do modelo
      `docs/arquitetura/based-java-aplication.md`: `pom.xml` (Boot 4.0.7, Java 25,
      **sem** data-jpa/postgresql; BOM AWS + `software.amazon.awssdk:sqs`), pacote
      `br.com.srportto.autorizacaostatusproducer`, porta 8082, actuator,
      `application.yaml` + profiles `local`/`prod`, `lombok.config`
- [x] 3.2 Criar configuração do `SqsClient` e propriedades (`aws.endpoint`,
      `aws.region`, `aws.sqs.queue-url`) com defaults do Floci no profile `local`
- [x] 3.3 Criar o record do payload do evento (espelho das colunas) e o use case
      `ProcessarEventoAutorizacaoUseCase` que loga o consumo com sucesso incluindo a
      representação da entidade
- [x] 3.4 Criar o adapter de consumo: `SmartLifecycle` que inicia virtual thread com
      loop de long polling (`WaitTimeSeconds=20`, `MaxNumberOfMessages=10`) → use case
      → `DeleteMessage` (ack) somente após sucesso; erro não dá ack; backoff em erros
      consecutivos de `ReceiveMessage`; shutdown gracioso no `stop()`
- [x] 3.5 Criar `Dockerfile` multi-stage (padrão do monorepo, não usado nesta fase),
      `README.md`, `CLAUDE.md`/`AGENTS.md` espelhados da app
- [x] 3.6 Testes: use case loga e retorna sucesso; adapter dá ack só após sucesso e
      segura o ack em erro; app sobe sem banco (context load)
- [x] 3.7 Validar: `mvn test` passa em `apps/autorizacaostatus-producer` e
      `/actuator/health` responde UP na porta 8082

## 4. Ponta a ponta e documentação

- [x] 4.1 Fluxo completo local: Floci up → apply do `local-messaging` → Postgres up →
      command up → POST `/api/autorizacoes` → confirmar log de consumo com a entidade
      na `autorizacaostatus-producer`; repetir com PATCH cancelar
- [x] 4.2 Atualizar documentação: README raiz (nova app e novo root de infra),
      `apps/contratocommand/CLAUDE.md`+`AGENTS.md` (publicação de eventos, novas
      propriedades), `infra/README.md` (root `local-messaging`)
