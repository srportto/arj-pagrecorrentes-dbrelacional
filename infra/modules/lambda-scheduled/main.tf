locals {
  # DB_PASSWORD e as demais credenciais sao sempre injetadas; var.environment pode
  # complementar (nunca sobrescrever silenciosamente sem intencao, por isso vem por
  # ultimo no merge) -- mesmo padrao do modulo ecs-service.
  function_environment = merge({
    DB_HOST      = var.db_host
    DB_PORT      = tostring(var.db_port)
    DB_NAME      = var.db_name
    DB_USER_NAME = var.db_user_name
    DB_PASSWORD  = var.db_password
  }, var.environment)
}

resource "aws_cloudwatch_log_group" "this" {
  name              = format("/aws/lambda/%s", var.name)
  retention_in_days = var.log_retention_days

  tags = var.tags
}

resource "aws_lambda_function" "this" {
  function_name = var.name
  role          = aws_iam_role.lambda_execution.arn

  package_type = "Image"
  image_uri    = var.image_uri

  timeout     = var.timeout
  memory_size = var.memory_size

  environment {
    variables = local.function_environment
  }

  depends_on = [aws_cloudwatch_log_group.this]

  tags = var.tags
}

resource "aws_scheduler_schedule" "this" {
  name = format("%s-schedule", var.name)

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression = var.schedule_expression

  target {
    arn      = aws_lambda_function.this.arn
    role_arn = aws_iam_role.scheduler.arn
  }
}
