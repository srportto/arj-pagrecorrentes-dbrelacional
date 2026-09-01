# Gerenciamento de State — Referência

## Backend remoto — S3 (AWS)

```hcl
terraform {
  backend "s3" {
    bucket         = "meu-terraform-state"
    key            = "producao/vpc/terraform.tfstate"
    region         = "us-east-1"
    encrypt        = true
    dynamodb_table = "terraform-state-lock"
  }
}
```

**Setup do bucket S3**
```hcl
resource "aws_s3_bucket" "terraform_state" {
  bucket = "meu-terraform-state"

  lifecycle {
    prevent_destroy = true
  }

  tags = {
    Name        = "Terraform State"
    Environment = "global"
  }
}

resource "aws_s3_bucket_versioning" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_dynamodb_table" "terraform_lock" {
  name         = "terraform-state-lock"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }

  tags = {
    Name        = "Terraform State Lock"
    Environment = "global"
  }
}
```

## Backend remoto — Azure Blob

```hcl
terraform {
  backend "azurerm" {
    resource_group_name  = "terraform-state-rg"
    storage_account_name = "tfstatestorage"
    container_name       = "tfstate"
    key                  = "producao.terraform.tfstate"
    use_azuread_auth     = true
  }
}
```

## Backend remoto — GCS (GCP)

```hcl
terraform {
  backend "gcs" {
    bucket = "meu-terraform-state"
    prefix = "producao/vpc"
  }
}
```

## Workspaces

```bash
terraform workspace list
terraform workspace new staging
terraform workspace select producao
terraform workspace show
terraform workspace delete dev
```

**Configuração workspace-aware**
```hcl
locals {
  ambiente = terraform.workspace

  vpc_cidr = {
    producao = "10.0.0.0/16"
    staging  = "10.1.0.0/16"
    dev      = "10.2.0.0/16"
  }

  instancias = {
    producao = 5
    staging  = 2
    dev      = 1
  }
}

resource "aws_vpc" "principal" {
  cidr_block = local.vpc_cidr[local.ambiente]

  tags = {
    Name        = "${local.ambiente}-vpc"
    Environment = local.ambiente
  }
}
```

## Migração de state

```bash
# Migrar para backend remoto
terraform init -migrate-state

# Importar recurso existente
terraform import aws_vpc.existente vpc-12345678

# Remover recurso do state
terraform state rm aws_instance.legado

# Mover recurso para outro endereço
terraform state mv aws_instance.app aws_instance.web
```
