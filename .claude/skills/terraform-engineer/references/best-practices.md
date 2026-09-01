# Boas Práticas — Referência

## DRY

**Use módulos para reuso**
```hcl
# Ruim — código repetido
resource "aws_vpc" "app1" {
  cidr_block = "10.0.0.0/16"
  tags = { Name = "app1-vpc", Environment = "prod" }
}

resource "aws_vpc" "app2" {
  cidr_block = "10.1.0.0/16"
  tags = { Name = "app2-vpc", Environment = "prod" }
}

# Bom — use módulo
module "vpc_app1" {
  source      = "./modules/vpc"
  nome        = "app1"
  cidr_block  = "10.0.0.0/16"
  ambiente    = "prod"
}

module "vpc_app2" {
  source      = "./modules/vpc"
  nome        = "app2"
  cidr_block  = "10.1.0.0/16"
  ambiente    = "prod"
}
```

**Use locals para valores repetidos**
```hcl
locals {
  tags_comuns = {
    Environment = var.ambiente
    ManagedBy   = "Terraform"
    Project     = var.nome_projeto
    CostCenter  = var.centro_custo
  }

  prefixo_nome = "${var.nome_projeto}-${var.ambiente}"
  vpc_cidr     = var.ambiente == "producao" ? "10.0.0.0/16" : "10.1.0.0/16"
}
```

**Use data sources em vez de hardcoding**
```hcl
# Ruim — AMI hardcoded
resource "aws_instance" "web" {
  ami           = "ami-0c55b159cbfafe1f0"
  instance_type = "t3.micro"
}

# Bom — lookup dinâmico
data "aws_ami" "amazon_linux_2" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["amzn2-ami-hvm-*-x86_64-gp2"]
  }
}

resource "aws_instance" "web" {
  ami           = data.aws_ami.amazon_linux_2.id
  instance_type = "t3.micro"
}
```

**Use for_each para múltiplos recursos similares**
```hcl
variable "subnets_privadas" {
  type = map(object({
    cidr_block = string
    az         = string
  }))
  default = {
    subnet1 = { cidr_block = "10.0.1.0/24", az = "us-east-1a" }
    subnet2 = { cidr_block = "10.0.2.0/24", az = "us-east-1b" }
  }
}

resource "aws_subnet" "privada" {
  for_each = var.subnets_privadas

  vpc_id            = aws_vpc.principal.id
  cidr_block        = each.value.cidr_block
  availability_zone = each.value.az

  tags = {
    Name = "${var.nome}-privada-${each.key}"
  }
}
```

## Convenções de naming

**Recursos Terraform**
```hcl
# Padrão: {tipo_recurso}_{nome_descritivo}
resource "aws_vpc" "principal" {}
resource "aws_subnet" "privada" {}
resource "aws_security_group" "web" {}

# Evite nomes genéricos
resource "aws_vpc" "vpc" {}        # Ruim
resource "aws_subnet" "subnet" {}  # Ruim
```

**Tags de nome AWS**
```hcl
locals {
  prefixo_nome = "${var.nome_projeto}-${var.ambiente}"
}

resource "aws_vpc" "principal" {
  cidr_block = var.cidr_block

  tags = merge(local.tags_comuns, {
    Name = "${local.prefixo_nome}-vpc"
  })
}
```

**Variáveis**
```hcl
variable "tipo_instancia" {}      # Bom — snake_case
variable "tipoInstancia" {}       # Ruim
variable "TipoInstancia" {}       # Ruim

variable "cidr_block_vpc" {}      # Bom — descritivo
variable "cidr" {}                # Ruim — vago

variable "habilitar_nat" {}       # Bom — booleano como pergunta
```

## Segurança

- Nunca commite `.terraform`, `*.tfstate`, `*.tfvars` com segredos.
- Use `sensitive = true` em outputs que contenham segredos.
- Criptografe state em repouso e em trânsito.
- Aplique least privilege em IAM policies gerenciadas pelo Terraform.

## Custo

- Use `default_tags` para cost allocation.
- Considere `lifecycle { prevent_destroy = true }` em recursos críticos.
- Use `terraform plan` para revisar custo estimado antes de aplicar.
