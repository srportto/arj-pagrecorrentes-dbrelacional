output "vpc_id" {
  description = "ID da VPC vpc-arj."
  value       = module.networking.vpc_id
}

output "public_subnet_ids" {
  value = module.networking.public_subnet_ids
}

output "private_subnet_ids" {
  value = module.networking.private_subnet_ids
}

output "alb_dns_name" {
  description = "DNS name publico do ALB. Use para testar as apps: curl http://<alb_dns_name>/command/actuator/health"
  value       = module.ecs_cluster.alb_dns_name
}

output "cluster_name" {
  value = module.ecs_cluster.cluster_name
}

output "contratocommand_ecr_repository_url" {
  value = aws_ecr_repository.contratocommand.repository_url
}

output "contratoquery_ecr_repository_url" {
  value = aws_ecr_repository.contratoquery.repository_url
}

output "contratocommand_service_name" {
  value = module.ecs_service_contratocommand.service_name
}

output "contratoquery_service_name" {
  value = module.ecs_service_contratoquery.service_name
}
