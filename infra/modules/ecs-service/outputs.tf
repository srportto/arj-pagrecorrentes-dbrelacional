output "service_name" {
  description = "Nome do ECS Service."
  value       = aws_ecs_service.this.name
}

output "task_definition_arn" {
  description = "ARN da task definition registrada (com revisao)."
  value       = aws_ecs_task_definition.this.arn
}

output "target_group_arn" {
  description = "ARN do target group associado ao servico."
  value       = aws_lb_target_group.this.arn
}

output "security_group_id" {
  description = "ID do security group da task."
  value       = aws_security_group.task.id
}

output "log_group_name" {
  description = "Nome do CloudWatch Log Group da task."
  value       = aws_cloudwatch_log_group.this.name
}
