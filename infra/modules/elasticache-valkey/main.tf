resource "aws_elasticache_subnet_group" "this" {
  name       = format("%s-subnet-group", var.name)
  subnet_ids = var.private_subnet_ids
  tags       = var.tags
}

resource "aws_security_group" "this" {
  name        = format("%s-sg", var.name)
  description = format("Ingresso na porta %s apenas a partir dos security groups autorizados; egress liberado", var.port)
  vpc_id      = var.vpc_id

  ingress {
    description     = "Trafego Redis/Valkey a partir dos servicos autorizados"
    from_port       = var.port
    to_port         = var.port
    protocol        = "tcp"
    security_groups = var.allowed_security_group_ids
  }

  egress {
    description = "Egress liberado"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, {
    Name = format("%s-sg", var.name)
  })
}

# Cluster single-node (num_cache_nodes = 1 por padrao): sem replicacao nem cluster mode
# nesta fase. Ver Open Questions do design.md da mudanca temporizacao-jornada-01-pix-auto
# sobre hash tag das chaves caso cluster mode seja habilitado no futuro.
resource "aws_elasticache_cluster" "this" {
  cluster_id         = var.name
  engine             = "valkey"
  engine_version     = var.engine_version
  node_type          = var.node_type
  num_cache_nodes    = var.num_cache_nodes
  port               = var.port
  subnet_group_name  = aws_elasticache_subnet_group.this.name
  security_group_ids = [aws_security_group.this.id]

  # ElastiCache gerenciado nao expoe append-only-file ao usuario; a durabilidade entre
  # reinicios de no e via snapshot automatico. snapshot_window so faz sentido com
  # snapshot_retention_limit > 0.
  snapshot_retention_limit = var.snapshot_retention_limit
  snapshot_window          = var.snapshot_retention_limit > 0 ? "03:00-04:00" : null

  tags = var.tags
}
