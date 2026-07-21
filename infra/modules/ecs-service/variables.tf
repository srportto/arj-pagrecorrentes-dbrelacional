variable "name" {
  description = "Nome do servico (usado como family da task definition, nome do ECS Service, target group e security group)."
  type        = string
}

variable "region" {
  description = "Regiao AWS (ou emulada pelo Floci) usada para o log driver awslogs."
  type        = string
}

variable "cluster_id" {
  description = "ID/ARN do cluster ECS onde o servico sera registrado (output do modulo ecs-cluster)."
  type        = string
}

variable "vpc_id" {
  description = "ID da VPC (output do modulo networking)."
  type        = string
}

variable "private_subnet_ids" {
  description = "IDs das subnets privadas onde as tasks Fargate rodam (output do modulo networking)."
  type        = list(string)
}

variable "alb_sg_id" {
  description = "ID do security group do ALB (output do modulo ecs-cluster), usado para liberar ingresso na task."
  type        = string
}

variable "listener_arn" {
  description = "ARN do listener HTTP do ALB (output do modulo ecs-cluster), onde a listener rule do servico sera anexada."
  type        = string
}

variable "listener_rule_priority" {
  description = "Prioridade da listener rule no ALB. Deve ser unica entre os servicos que compartilham o mesmo listener."
  type        = number
}

variable "path_pattern" {
  description = "Padrao de path (ex.: \"/command/*\") usado na listener rule para rotear para este servico."
  type        = string
}

variable "image" {
  description = "URI da imagem do container (ex.: repositorio ECR + tag)."
  type        = string
}

variable "container_port" {
  description = "Porta em que a aplicacao escuta dentro do container."
  type        = number
}

variable "health_check_path" {
  description = "Caminho do health check HTTP usado pelo target group."
  type        = string
  default     = "/actuator/health"
}

variable "cpu" {
  description = "CPU da task Fargate, em unidades AWS (256 = 0.25 vCPU)."
  type        = number
  default     = 256
}

variable "memory" {
  description = "Memoria da task Fargate, em MiB."
  type        = number
  default     = 512
}

variable "desired_count" {
  description = "Numero desejado de tasks em execucao."
  type        = number
  default     = 1
}

variable "assign_public_ip" {
  description = "Se as tasks devem receber IP publico. Deve ser false para tasks em subnet privada."
  type        = bool
  default     = false
}

variable "spring_profiles_active" {
  description = "Valor injetado em SPRING_PROFILES_ACTIVE."
  type        = string
  default     = "local"
}

variable "db_host" {
  description = "Host do banco de dados injetado via variavel de ambiente."
  type        = string
}

variable "db_port" {
  description = "Porta do banco de dados injetada via variavel de ambiente."
  type        = number
  default     = 5432
}

variable "db_name" {
  description = "Nome do banco de dados injetado via variavel de ambiente."
  type        = string
}

variable "db_user_name" {
  description = "Usuario do banco de dados injetado via variavel de ambiente."
  type        = string
}

variable "db_password" {
  description = "Senha do banco de dados injetada via variavel de ambiente. Evoluir para Secrets Manager em fase futura."
  type        = string
  sensitive   = true
}

variable "environment" {
  description = "Variaveis de ambiente adicionais, alem de SPRING_PROFILES_ACTIVE e das credenciais de banco."
  type        = map(string)
  default     = {}
}

variable "log_retention_days" {
  description = "Retencao em dias do CloudWatch Log Group da task."
  type        = number
  default     = 7
}

variable "tags" {
  description = "Tags adicionais aplicadas aos recursos do modulo."
  type        = map(string)
  default     = {}
}
