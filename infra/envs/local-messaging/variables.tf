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
