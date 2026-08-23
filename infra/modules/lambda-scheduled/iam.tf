## Role de execucao da Lambda (assumida pelo runtime, nao pelo Scheduler) -- permite
## escrever logs no CloudWatch. Privilegio sobre o banco (SELECT + TRUNCATE granular nas
## particoes de expurgo) NAO vem daqui: e' concedido diretamente no Postgres ao role de
## aplicacao `expurgo_particao_rotina` (ver infra/local/postgres/migrations/
## v1.0.9.-roles-privilegio-minimo-expurgo-particao.sql), nao via IAM.
resource "aws_iam_role" "lambda_execution" {
  name = format("%s-execution-role", var.name)

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Service = "lambda.amazonaws.com"
      }
      Action = "sts:AssumeRole"
    }]
  })

  tags = var.tags
}

resource "aws_iam_role_policy_attachment" "lambda_execution" {
  role       = aws_iam_role.lambda_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

## Role assumida pelo EventBridge Scheduler para invocar a funcao -- escopo minimo:
## so lambda:InvokeFunction, e so sobre esta funcao especifica.
resource "aws_iam_role" "scheduler" {
  name = format("%s-scheduler-role", var.name)

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Service = "scheduler.amazonaws.com"
      }
      Action = "sts:AssumeRole"
    }]
  })

  tags = var.tags
}

resource "aws_iam_role_policy" "scheduler_invoke" {
  name = "invoke-lambda"
  role = aws_iam_role.scheduler.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "lambda:InvokeFunction"
      Resource = aws_lambda_function.this.arn
    }]
  })
}
