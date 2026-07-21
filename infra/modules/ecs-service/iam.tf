## Execution role: permite ao ECS puxar a imagem do ECR e escrever logs no CloudWatch
## em nome da task (nao e a role da aplicacao em si, e sim do agente ECS/Fargate).
resource "aws_iam_role" "execution" {
  name = format("%s-execution-role", var.name)

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Service = "ecs-tasks.amazonaws.com"
      }
      Action = "sts:AssumeRole"
    }]
  })

  tags = var.tags
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}
