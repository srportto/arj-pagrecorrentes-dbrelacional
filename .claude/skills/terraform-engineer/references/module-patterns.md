# Padrões de Módulo — Referência

## Estrutura

```
terraform-aws-vpc/
├── main.tf           # Recursos principais
├── variables.tf      # Variáveis de entrada
├── outputs.tf        # Valores de saída
├── versions.tf       # Constraints de provider
├── README.md         # Documentação
├── examples/
│   └── complete/
│       ├── main.tf
│       └── variables.tf
└── tests/
    └── vpc_test.go
```

## Padrão básico

**main.tf**
```hcl
resource "aws_vpc" "this" {
  cidr_block           = var.cidr_block
  enable_dns_hostnames = var.enable_dns_hostnames
  enable_dns_support   = var.enable_dns_support

  tags = merge(
    var.tags,
    {
      Name = var.nome
    }
  )
}

resource "aws_subnet" "privada" {
  for_each = var.subnets_privadas

  vpc_id            = aws_vpc.this.id
  cidr_block        = each.value.cidr_block
  availability_zone = each.value.az

  tags = merge(
    var.tags,
    {
      Name = "${var.nome}-privada-${each.key}"
      Type = "private"
    }
  )
}
```

**variables.tf**
```hcl
variable "nome" {
  description = "Prefixo de nome para todos os recursos"
  type        = string

  validation {
    condition     = length(var.nome) > 0 && length(var.nome) <= 32
    error_message = "Nome deve ter 1-32 caracteres."
  }
}

variable "cidr_block" {
  description = "Bloco CIDR da VPC"
  type        = string

  validation {
    condition     = can(cidrhost(var.cidr_block, 0))
    error_message = "Deve ser um bloco IPv4 CIDR válido."
  }
}

variable "subnets_privadas" {
  description = "Mapa de configurações de subnets privadas"
  type = map(object({
    cidr_block = string
    az         = string
  }))
  default = {}
}

variable "tags" {
  description = "Tags comuns para todos os recursos"
  type        = map(string)
  default     = {}
}

variable "enable_dns_hostnames" {
  description = "Habilitar DNS hostnames na VPC"
  type        = bool
  default     = true
}
```

**outputs.tf**
```hcl
output "vpc_id" {
  description = "ID da VPC"
  value       = aws_vpc.this.id
}

output "vpc_cidr_block" {
  description = "Bloco CIDR da VPC"
  value       = aws_vpc.this.cidr_block
}

output "subnet_privada_ids" {
  description = "IDs das subnets privadas"
  value       = { for k, v in aws_subnet.privada : k => v.id }
}
```

**versions.tf**
```hcl
terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}
```

## Composição de módulos

```hcl
module "rede" {
  source = "./modules/vpc"

  nome       = "producao"
  cidr_block = "10.0.0.0/16"

  subnets_privadas = {
    app1 = { cidr_block = "10.0.1.0/24", az = "us-east-1a" }
    app2 = { cidr_block = "10.0.2.0/24", az = "us-east-1b" }
  }

  tags = local.tags_comuns
}

module "seguranca" {
  source = "./modules/security-groups"

  vpc_id = module.rede.vpc_id
}
```

## Blocos dinâmicos

```hcl
resource "aws_security_group" "this" {
  name   = var.nome
  vpc_id = var.vpc_id

  dynamic "ingress" {
    for_each = var.regras_ingress
    content {
      from_port   = ingress.value.from_port
      to_port     = ingress.value.to_port
      protocol    = ingress.value.protocol
      cidr_blocks = ingress.value.cidr_blocks
      description = ingress.value.description
    }
  }

  dynamic "egress" {
    for_each = var.regras_egress
    content {
      from_port   = egress.value.from_port
      to_port     = egress.value.to_port
      protocol    = egress.value.protocol
      cidr_blocks = egress.value.cidr_blocks
      description = egress.value.description
    }
  }
}
```
