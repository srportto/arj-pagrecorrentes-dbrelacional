variable "region" {
  description = "Regiao AWS (ou emulada pelo Floci) onde a rede sera provisionada."
  type        = string
}

variable "vpc_name" {
  description = "Nome da VPC, usado como base para tags e para o prefixo dos parametros SSM."
  type        = string
  default     = "vpc-arj"
}

variable "vpc_cidr" {
  description = "Bloco CIDR da VPC."
  type        = string
  default     = "10.0.0.0/16"
}

variable "az_suffixes" {
  description = "Sufixos de AZ (a, b, c, ...) combinados com var.region para formar as availability zones. Uma entrada por subnet publica/privada."
  type        = list(string)
  default     = ["a", "b", "c"]
}

variable "public_subnet_cidrs" {
  description = "CIDRs das subnets publicas, na mesma ordem de var.az_suffixes."
  type        = list(string)
  default     = ["10.0.48.0/24", "10.0.49.0/24", "10.0.50.0/24"]

  validation {
    condition     = length(var.public_subnet_cidrs) == length(var.az_suffixes)
    error_message = "public_subnet_cidrs deve ter o mesmo numero de entradas que az_suffixes."
  }
}

variable "private_subnet_cidrs" {
  description = "CIDRs das subnets privadas, na mesma ordem de var.az_suffixes."
  type        = list(string)
  default     = ["10.0.0.0/20", "10.0.16.0/20", "10.0.32.0/20"]

  validation {
    condition     = length(var.private_subnet_cidrs) == length(var.az_suffixes)
    error_message = "private_subnet_cidrs deve ter o mesmo numero de entradas que az_suffixes."
  }
}

variable "nat_gateway_count" {
  description = "Quantidade de NAT Gateways a criar (1 = compartilhado entre todas as AZs; igual ao numero de AZs = 1 NAT por AZ, para HA real)."
  type        = number
  default     = 3

  validation {
    condition     = var.nat_gateway_count >= 1 && var.nat_gateway_count <= length(var.az_suffixes)
    error_message = "nat_gateway_count deve estar entre 1 e o numero de AZs (length(az_suffixes))."
  }
}

variable "tags" {
  description = "Tags adicionais aplicadas a todos os recursos do modulo."
  type        = map(string)
  default     = {}
}
