output "function_name" {
  description = "Nome da funcao Lambda criada."
  value       = aws_lambda_function.this.function_name
}

output "function_arn" {
  description = "ARN da funcao Lambda criada."
  value       = aws_lambda_function.this.arn
}

output "schedule_arn" {
  description = "ARN do EventBridge Schedule que dispara a funcao."
  value       = aws_scheduler_schedule.this.arn
}
