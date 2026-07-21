variable "vpc_id" {
  description = "ID da VPC onde o cluster e o ALB serao criados (output do modulo networking)."
  type        = string
}

variable "public_subnet_ids" {
  description = "IDs das subnets publicas onde o ALB internet-facing sera posicionado (output do modulo networking)."
  type        = list(string)
}

variable "cluster_name" {
  description = "Nome do cluster ECS."
  type        = string
  default     = "arj-cluster"
}

variable "alb_name" {
  description = "Nome do Application Load Balancer."
  type        = string
  default     = "arj-alb"
}

variable "tags" {
  description = "Tags adicionais aplicadas aos recursos do modulo."
  type        = map(string)
  default     = {}
}
