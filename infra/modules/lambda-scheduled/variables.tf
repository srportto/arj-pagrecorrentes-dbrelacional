variable "name" {
  description = "Nome da funcao Lambda (tambem usado no nome das roles e do schedule)."
  type        = string
}

variable "region" {
  description = "Regiao AWS (ou emulada pelo Floci) usada para o log group da funcao."
  type        = string
}

variable "image_uri" {
  description = "URI da imagem do container da Lambda (ex.: repositorio ECR + tag). PackageType Image."
  type        = string
}

variable "schedule_expression" {
  description = "Expressao do EventBridge Scheduler que dispara a funcao."
  type        = string
  default     = "rate(30 minutes)"
}

variable "timeout" {
  description = "Timeout da funcao Lambda, em segundos."
  type        = number
  default     = 30
}

variable "memory_size" {
  description = "Memoria da funcao Lambda, em MB."
  type        = number
  default     = 128
}

variable "db_host" {
  description = "Host do banco de dados injetado via variavel de ambiente. Local: host.docker.internal (ver design.md D4 da change reclamar-particao-expurgo-ciclo). AWS real: endpoint do RDS, com a funcao em vpc_config."
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
  description = "Senha do banco de dados injetada via variavel de ambiente. Evoluir para Secrets Manager em fase futura (mesma nota do modulo ecs-service)."
  type        = string
  sensitive   = true
}

variable "environment" {
  description = "Variaveis de ambiente adicionais, alem de DB_HOST/DB_PORT/DB_NAME/DB_USER_NAME/DB_PASSWORD."
  type        = map(string)
  default     = {}
}

variable "log_retention_days" {
  description = "Retencao em dias do CloudWatch Log Group da funcao."
  type        = number
  default     = 7
}

variable "tags" {
  description = "Tags adicionais aplicadas aos recursos do modulo."
  type        = map(string)
  default     = {}
}
