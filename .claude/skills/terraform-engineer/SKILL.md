---

name: terraform-engineer
description: "Referência para Terraform IaC — módulos reutilizáveis com validação, state remoto com locking, providers pinados, multi-ambiente, testes com terraform test. Use ao criar ou revisar código Terraform na pasta `infra/`. Uso: sessão principal ou invocação manual via `/terraform-engineer`; não carregar proativamente."
license: MIT
metadata:
  author: https://github.com/srportto/srportto
  version: "1.0.0"
  domain: infrastructure
  triggers: Terraform, IaC, módulo, state, backend, provider, plan, apply, infra
  role: specialist
  scope: implementation
  output-format: code
  related-skills: cloud-architect, devops-cicd
---
---

# Terraform Engineer

Especialista em Terraform para infraestrutura como código (IaC) multi-cloud (AWS, Azure, GCP),
com foco em módulos reutilizáveis, state management seguro e padrões production-grade. No
monorepo, aplica-se à pasta `infra/`.

**Quando NÃO usar:** para deploy de aplicação Java (pipeline, Dockerfile, K8s), use
`devops-cicd`. Para topologia de nuvem de alto nível (VPC, IAM, DR), use `cloud-architect`.

## Workflow

1. **Analise** — requisitos, código existente, plataformas cloud envolvidas.
2. **Modele módulos** — crie módulos compostos com interfaces claras (`variables.tf`, `outputs.tf`).
3. **Configure state** — backend remoto com locking e criptografia.
4. **Aplique segurança** — least privilege, criptografia em repouso/transito, tags obrigatórias.
5. **Valide** — `terraform fmt` + `terraform validate` + `tflint`; corrija até limpar.
6. **Planeje e aplique** — `terraform plan -out=tfplan`, revise, `terraform apply tfplan`.

### Recuperação de erro

- **Validação falha (passo 5):** corrija os erros reportados e re-execute `terraform validate`.
- **Plano falha (passo 6):**
  - *Drift de state:* `terraform refresh` ou `terraform state rm` / `terraform import` para realinhar, depois re-plan.
  - *Erro de auth do provider:* verifique credenciais e variáveis de ambiente; re-execute `terraform init` se plugins estiverem desatualizados.
  - *Erro de dependência:* adicione `depends_on` explícito ou reestruture outputs de módulo.

## Referências (carregue sob demanda)

| Tópico | Referência | Carregue quando |
|--------|-----------|-----------------|
| Padrões de módulo | `references/module-patterns.md` | Criar módulos, inputs/outputs, composição |
| State | `references/state-management.md` | Backends remotos, locking, workspaces, migração |
| Providers | `references/providers.md` | Configuração AWS/Azure/GCP, autenticação |
| Testes | `references/testing.md` | `terraform test`, terratest, validação de plano |
| Boas práticas | `references/best-practices.md` | DRY, naming, segurança, custo |

## Regras

### FAÇA
- Versionamento semântico e pin de versões de provider (`~> 5.0`).
- State remoto com locking e criptografia (S3 + DynamoDB, Azure Blob, GCS).
- Blocos `validation` em todas as variáveis de entrada.
- Naming consistente e tags em todos os recursos.
- Documente interfaces de módulo (`description` em `variable` e `output`).
- `terraform fmt` e `terraform validate` antes de qualquer commit.

### NÃO FAÇA
- Armazenar segredos em texto plano ou valores hardcoded por ambiente.
- Usar state local em produção ou pular locking.
- Misturar versões de provider sem constraints.
- Criar dependência circular entre módulos ou pular validação de input.
- Commitar diretórios `.terraform`.

## Exemplos sucintos

### Módulo mínimo

**`main.tf`**
```hcl
resource "aws_s3_bucket" "this" {
  bucket = var.nome_bucket
  tags   = var.tags
}
```

**`variables.tf`**
```hcl
variable "nome_bucket" {
  description = "Nome do bucket S3"
  type        = string

  validation {
    condition     = length(var.nome_bucket) > 3
    error_message = "nome_bucket deve ter mais de 3 caracteres."
  }
}

variable "tags" {
  description = "Tags aplicadas a todos os recursos"
  type        = map(string)
  default     = {}
}
```

**`outputs.tf`**
```hcl
output "bucket_id" {
  description = "ID do bucket criado"
  value       = aws_s3_bucket.this.id
}
```

### Backend remoto (S3 + DynamoDB)

```hcl
terraform {
  backend "s3" {
    bucket         = "meu-tf-state"
    key            = "env/prod/terraform.tfstate"
    region         = "us-east-1"
    encrypt        = true
    dynamodb_table = "terraform-lock"
  }
}
```

### Pin de providers

```hcl
terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.0"
    }
  }
}
```

## Saída esperada ao implementar

1. Estrutura de módulo (`main.tf`, `variables.tf`, `outputs.tf`, `versions.tf`).
2. Configuração de backend e provider.
3. Exemplo de uso com `tfvars`.
4. Breve explicação das decisões de design.

## Conhecimento de referência

Terraform 1.5+, HCL, backends (S3, Azure Blob, GCS), workspaces, `for_each`, `dynamic`,
`terraform test`, terratest, tflint, módulos, providers AWS/Azure/GCP.
