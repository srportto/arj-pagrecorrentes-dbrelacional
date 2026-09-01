# Providers — Referência

## AWS

**Configuração básica**
```hcl
terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.regiao_aws

  default_tags {
    tags = {
      Environment = var.ambiente
      ManagedBy   = "Terraform"
      Project     = var.nome_projeto
    }
  }
}
```

**Múltiplas contas/regiões**
```hcl
provider "aws" {
  alias  = "principal"
  region = "us-east-1"

  assume_role {
    role_arn     = "arn:aws:iam::123456789012:role/TerraformRole"
    session_name = "terraform-session"
  }
}

provider "aws" {
  alias  = "secundaria"
  region = "us-west-2"
}

resource "aws_vpc" "principal" {
  provider   = aws.principal
  cidr_block = "10.0.0.0/16"
}
```

**Métodos de autenticação**
```hcl
# 1. Variáveis de ambiente (recomendado para CI/CD)
# AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_SESSION_TOKEN

# 2. Arquivo de credenciais compartilhado
provider "aws" {
  region                   = "us-east-1"
  shared_credentials_files = ["~/.aws/credentials"]
  profile                  = "producao"
}

# 3. IAM role (recomendado para EC2/ECS)
provider "aws" {
  region = "us-east-1"
  # Usa instance profile automaticamente
}

# 4. Assume role
provider "aws" {
  region = "us-east-1"

  assume_role {
    role_arn     = var.terraform_role_arn
    session_name = "terraform-${var.ambiente}"
    external_id  = var.external_id
  }
}
```

**Features do provider**
```hcl
provider "aws" {
  region = "us-east-1"

  default_tags {
    tags = {
      Environment = "producao"
      ManagedBy   = "Terraform"
      CostCenter  = "engenharia"
    }
  }

  ignore_tags {
    keys = ["aws:autoscaling:groupName"]
  }

  endpoints {
    s3  = "http://localhost:4566"
    ec2 = "http://localhost:4566"
  }

  max_retries = 3
}
```

## Azure (azurerm)

```hcl
terraform {
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.0"
    }
  }
}

provider "azurerm" {
  features {
    resource_group {
      prevent_deletion_if_contains_resources = true
    }

    key_vault {
      purge_soft_delete_on_destroy    = false
      recover_soft_deleted_key_vaults = true
    }
  }
}
```

## GCP (google)

```hcl
terraform {
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
  }
}

provider "google" {
  project = var.projeto_id
  region  = var.regiao
}
```
