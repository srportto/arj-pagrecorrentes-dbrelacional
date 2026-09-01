# Testes — Referência

## Validação de plano

```bash
terraform init
terraform fmt -check
terraform validate

terraform plan -out=tfplan
terraform show -json tfplan | jq .

terraform apply tfplan
```

**Plano com variáveis**
```bash
terraform plan -var-file="producao.tfvars"
terraform plan -var="instancias=5"
terraform plan -var-file="comum.tfvars" -var-file="producao.tfvars"
```

**Análise de plano**
```bash
terraform plan -target=aws_vpc.principal
terraform plan -refresh-only
terraform plan -destroy
```

## terraform test (1.6+)

**Estrutura**
```
tests/
├── unitario/
│   ├── vpc_test.tftest.hcl
│   └── security_group_test.tftest.hcl
└── integracao/
    └── completo_test.tftest.hcl
```

**Teste básico**
```hcl
run "validar_cidr_vpc" {
  command = plan

  variables {
    cidr_block = "10.0.0.0/16"
    nome       = "teste-vpc"
  }

  assert {
    condition     = aws_vpc.principal.cidr_block == "10.0.0.0/16"
    error_message = "CIDR da VPC não corresponde ao esperado"
  }

  assert {
    condition     = aws_vpc.principal.enable_dns_hostnames == true
    error_message = "DNS hostnames deveria estar habilitado"
  }
}

run "validar_tags" {
  command = plan

  variables {
    cidr_block = "10.0.0.0/16"
    nome       = "teste-vpc"
    tags = {
      Environment = "teste"
    }
  }

  assert {
    condition     = aws_vpc.principal.tags["Environment"] == "teste"
    error_message = "Tag Environment não configurada corretamente"
  }
}
```

**Teste de integração**
```hcl
run "criar_stack_completo" {
  command = apply

  variables {
    cidr_block = "10.0.0.0/16"
    nome       = "teste-integracao"
    subnets_privadas = {
      app = { cidr_block = "10.0.1.0/24", az = "us-east-1a" }
    }
  }

  assert {
    condition     = length(aws_subnet.privada) == 1
    error_message = "Deveria criar exatamente uma subnet privada"
  }
}
```

**Executar testes**
```bash
terraform test
terraform test tests/vpc_test.tftest.hcl
terraform test -verbose
terraform test -no-cleanup
```

## Terratest (Go)

```go
package test

import (
    "testing"
    "github.com/gruntwork-io/terratest/modules/terraform"
    "github.com/stretchr/testify/assert"
)

func TestVPC(t *testing.T) {
    terraformOptions := terraform.WithDefaultRetryableErrors(t, &terraform.Options{
        TerraformDir: "../examples/complete",
    })

    defer terraform.Destroy(t, terraformOptions)
    terraform.InitAndApply(t, terraformOptions)

    vpcId := terraform.Output(t, terraformOptions, "vpc_id")
    assert.NotEmpty(t, vpcId)
}
```
