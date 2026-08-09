variable "name" {
  description = "Nome do cluster ElastiCache (usado como cluster_id, subnet group e security group)."
  type        = string
}

variable "vpc_id" {
  description = "ID da VPC onde o cluster reside (output do modulo networking)."
  type        = string
}

variable "private_subnet_ids" {
  description = "IDs das subnets privadas do subnet group do cluster."
  type        = list(string)
}

variable "allowed_security_group_ids" {
  description = "IDs dos security groups autorizados a acessar o cluster na porta do Valkey (ex.: security group da task de temporiza-autorizacao). Nao SHALL ficar vazio em producao — acesso irrestrito nao e permitido por este modulo."
  type        = list(string)
}

variable "node_type" {
  description = "Tipo de instancia do(s) no(s) do cluster (ex.: cache.t4g.micro)."
  type        = string
  default     = "cache.t4g.micro"
}

variable "engine_version" {
  description = "Versao do engine Valkey."
  type        = string
  default     = "8.0"
}

variable "port" {
  description = "Porta do protocolo Redis/Valkey."
  type        = number
  default     = 6379
}

variable "num_cache_nodes" {
  description = "Numero de nos do cluster. 1 nesta fase (sem replicacao/cluster mode) — ver Open Questions do design.md sobre cluster mode."
  type        = number
  default     = 1
}

variable "snapshot_retention_limit" {
  description = "Dias de retencao dos snapshots automaticos do cluster (0 desabilita snapshots)."
  type        = number
  default     = 1
}

variable "tags" {
  description = "Tags adicionais aplicadas aos recursos do modulo."
  type        = map(string)
  default     = {}
}
