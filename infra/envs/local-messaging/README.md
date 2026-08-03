# envs/local-messaging

Root Terraform independente de [`envs/local`](../local/) que provisiona apenas os
recursos de mensageria de eventos de autorização no
[Floci](../../../docs/floci-aws-local/floci-aws-local.md) (emulador AWS local):

- Tópico SNS `sns-estados-autorizacao`
- Fila SQS `SQS-eventos-autorizacao`, com `redrive_policy` apontando para a DLQ
  `SQS-eventos-autorizacao-dlq` (`maxReceiveCount = 10`, ver `var.sqs_dlq_max_receive_count`)
  — sem DLQ, uma mensagem "venenosa" reentregaria para sempre
- `visibility_timeout_seconds = 60` (ver `var.sqs_visibility_timeout_seconds`) —
  dimensionado acima do pior caso de processamento de uma mensagem pela
  `autorizacaostatus-producer` (produce síncrono no Kafka), com margem. Combinado ao
  `maxReceiveCount = 10`, o orçamento de retry antes da DLQ passa de ~90s para ~10min,
  tolerando uma indisponibilidade transitória do Kafka sem esvaziar a fila
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
