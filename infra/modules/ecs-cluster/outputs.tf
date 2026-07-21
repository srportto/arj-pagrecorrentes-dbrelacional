output "cluster_id" {
  description = "ID do cluster ECS."
  value       = aws_ecs_cluster.this.id
}

output "cluster_arn" {
  description = "ARN do cluster ECS."
  value       = aws_ecs_cluster.this.arn
}

output "cluster_name" {
  description = "Nome do cluster ECS."
  value       = aws_ecs_cluster.this.name
}

output "alb_arn" {
  description = "ARN do Application Load Balancer."
  value       = aws_lb.this.arn
}

output "alb_dns_name" {
  description = "DNS name publico do ALB."
  value       = aws_lb.this.dns_name
}

output "listener_arn" {
  description = "ARN do listener HTTP :80 do ALB, usado pelos ecs-service para registrar listener rules."
  value       = aws_lb_listener.http.arn
}

output "alb_sg_id" {
  description = "ID do security group do ALB, usado pelos ecs-service para liberar ingresso nas tasks."
  value       = aws_security_group.alb.id
}
