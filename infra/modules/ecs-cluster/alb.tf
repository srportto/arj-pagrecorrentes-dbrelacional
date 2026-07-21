resource "aws_security_group" "alb" {
  name        = format("%s-sg", var.alb_name)
  description = "ALB internet-facing: ingresso HTTP :80 da internet, egress liberado para as tasks"
  vpc_id      = var.vpc_id

  ingress {
    description = "HTTP publico"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "Egress liberado para os target groups dos servicos"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, {
    Name = format("%s-sg", var.alb_name)
  })
}

resource "aws_lb" "this" {
  name               = var.alb_name
  internal           = false
  load_balancer_type = "application"
  subnets            = var.public_subnet_ids
  security_groups    = [aws_security_group.alb.id]

  tags = merge(var.tags, {
    Name = var.alb_name
  })
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.this.arn
  port              = 80
  protocol          = "HTTP"

  # Acao default: 404 fixo. Cada ecs-service adiciona sua propria listener rule
  # (roteamento path-based) apontando para o target group do servico.
  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "text/plain"
      message_body = "Not Found"
      status_code  = "404"
    }
  }
}
