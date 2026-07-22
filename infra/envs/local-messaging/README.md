# envs/local-messaging

Root Terraform independente de [`envs/local`](../local/) que provisiona apenas os
recursos de mensageria de eventos de autorização no
[Floci](../../../docs/floci-aws-local/floci-aws-local.md) (emulador AWS local):

- Tópico SNS `sns-estados-autorizacao`
- Fila SQS `SQS-eventos-autorizacao`
- Subscription SNS → SQS com `raw_message_delivery = true` (o body entregue na
  fila é o JSON puro publicado no tópico, sem o envelope SNS)

Separado de `envs/local` para que o `apply` não suba VPC/ECS junto — aplica em
segundos e não interfere no ambiente de containers.

## Pré-requisitos

1. **Floci no ar** — ver [`infra/local/floci/README.md`](../../local/floci/README.md).
2. **Terraform `>= 1.10`**.

## Rodar

```bash
terraform init
terraform plan
terraform apply
```

## Validar

```bash
export FE="--endpoint-url http://localhost:4566 --region us-east-1"

aws $FE sns list-topics
aws $FE sqs list-queues

# publicar uma mensagem de teste e conferir que chega crua na fila
aws $FE sns publish --topic-arn "$(terraform output -raw sns_topic_arn)" \
  --message '{"teste":"ok"}'

aws $FE sqs receive-message --queue-url "$(terraform output -raw sqs_queue_url)"
```

## Limpar

```bash
terraform destroy
```
