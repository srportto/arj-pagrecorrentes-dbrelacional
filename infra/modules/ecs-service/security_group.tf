resource "aws_security_group" "task" {
  name        = format("%s-sg", var.name)
  description = format("Ingresso na porta %s apenas a partir do ALB; egress liberado", var.container_port)
  vpc_id      = var.vpc_id

  ingress {
    description     = "Trafego do ALB para a aplicacao"
    from_port       = var.container_port
    to_port         = var.container_port
    protocol        = "tcp"
    security_groups = [var.alb_sg_id]
  }

  egress {
    description = "Egress liberado (DB, chamadas externas, pull de imagem)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, {
    Name = format("%s-sg", var.name)
  })
}
