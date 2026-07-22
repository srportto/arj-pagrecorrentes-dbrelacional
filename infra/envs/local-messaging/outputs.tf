output "sns_topic_arn" {
  description = "ARN do topico SNS de estados de autorizacao."
  value       = aws_sns_topic.estados_autorizacao.arn
}

output "sqs_queue_url" {
  description = "URL da fila SQS de eventos de autorizacao."
  value       = aws_sqs_queue.eventos_autorizacao.id
}

output "sqs_queue_arn" {
  description = "ARN da fila SQS de eventos de autorizacao."
  value       = aws_sqs_queue.eventos_autorizacao.arn
}
