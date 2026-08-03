variable "floci_endpoint" {
  description = "Endpoint HTTP do Floci (emulador AWS local)."
  type        = string
  default     = "http://localhost:4566"
}

variable "region" {
  description = "Regiao AWS emulada pelo Floci."
  type        = string
  default     = "us-east-1"
}

variable "sns_topic_name" {
  description = "Nome do topico SNS de estados de autorizacao."
  type        = string
  default     = "sns-estados-autorizacao"
}

variable "sqs_queue_name" {
  description = "Nome da fila SQS de eventos de autorizacao."
  type        = string
  default     = "SQS-eventos-autorizacao"
}

variable "sqs_dlq_max_receive_count" {
  description = "Quantidade de tentativas de entrega antes de mover a mensagem para a DLQ."
  type        = number
  default     = 10
}

variable "sqs_visibility_timeout_seconds" {
  description = <<-EOT
    Tempo em que uma mensagem entregue fica invisivel para os demais consumidores.
    Dimensionado acima do pior caso de processamento de UMA mensagem (produce
    sincrono no Kafka, com todos os caminhos de tempo com teto explicito), com
    margem: max.block.ms (5s) + delivery.timeout.ms (15s) + timeout do Schema
    Registry (~3s) ~= 23s; 60s da uma folga de ~2,5x.
  EOT
  type        = number
  default     = 60
}
