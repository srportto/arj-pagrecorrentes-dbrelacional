locals {
  # SPRING_PROFILES_ACTIVE e as credenciais de banco sao sempre injetadas; var.environment
  # pode complementar (nunca sobrescrever silenciosamente sem intencao, por isso vem por ultimo no merge).
  container_environment = merge({
    SPRING_PROFILES_ACTIVE = var.spring_profiles_active
    DB_HOST                = var.db_host
    DB_PORT                = tostring(var.db_port)
    DB_NAME                = var.db_name
    DB_USER_NAME           = var.db_user_name
    DB_PASSWORD            = var.db_password
  }, var.environment)
}

resource "aws_cloudwatch_log_group" "this" {
  name              = format("/ecs/%s", var.name)
  retention_in_days = var.log_retention_days

  tags = var.tags
}

resource "aws_ecs_task_definition" "this" {
  family                   = var.name
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = tostring(var.cpu)
  memory                   = tostring(var.memory)
  execution_role_arn       = aws_iam_role.execution.arn

  container_definitions = jsonencode([
    {
      name  = var.name
      image = var.image

      portMappings = [
        {
          containerPort = var.container_port
          protocol      = "tcp"
        }
      ]

      environment = [
        for key, value in local.container_environment : {
          name  = key
          value = value
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.this.name
          "awslogs-region"        = var.region
          "awslogs-stream-prefix" = var.name
        }
      }
    }
  ])

  tags = var.tags
}
