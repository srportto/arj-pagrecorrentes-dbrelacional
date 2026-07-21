## Security group base da VPC: liberado para trafego interno entre recursos da
## propria VPC e para todo o egress. Modulos de compute (ecs-cluster, ecs-service)
## criam seus proprios security groups mais restritivos (ALB :80, task :porta-da-app).
resource "aws_security_group" "base" {
  name        = format("%s-base", var.vpc_name)
  description = "Security group base: trafego interno da VPC liberado, egress liberado"
  vpc_id      = aws_vpc.this.id

  ingress {
    description = "Trafego interno da VPC"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = [var.vpc_cidr]
  }

  egress {
    description = "Todo o egress liberado"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, {
    Name = format("%s-base", var.vpc_name)
  })
}
