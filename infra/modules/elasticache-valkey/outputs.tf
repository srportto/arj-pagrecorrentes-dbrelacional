output "endpoint" {
  description = "Endereco (host) do endpoint de conexao do cluster."
  value       = aws_elasticache_cluster.this.cache_nodes[0].address
}

output "port" {
  description = "Porta de conexao do cluster."
  value       = aws_elasticache_cluster.this.port
}

output "security_group_id" {
  description = "ID do security group do cluster, para referencia por outros modulos se necessario."
  value       = aws_security_group.this.id
}
