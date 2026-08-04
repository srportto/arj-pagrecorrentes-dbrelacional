---

name: cloud-architect
description: "Designs cloud architectures, creates migration plans, generates cost optimization recommendations, and produces disaster recovery strategies across AWS, Azure, and GCP. Use when designing cloud topologies, planning migrations, applying the Well-Architected Framework, building landing zones, or optimizing multi-cloud deployments. Invoke for security architecture (zero-trust, IAM), FinOps/cost optimization, disaster recovery (RTO/RPO), managed services selection, and serverless design. Uso: agent `cloud-architect` ou invocação manual via `/cloud-architect`; não deve ser carregada proativamente pela sessão principal."
license: MIT
metadata:
  author: https://github.com/srportto/srportto
  co-author: https://github.com/Jeffallan/claude-skills
  version: "1.1.0"
  domain: infrastructure
  triggers: AWS, Azure, GCP, Google Cloud, cloud migration, cloud architecture, multi-cloud, cloud cost, Well-Architected, landing zone, cloud security, disaster recovery, cloud native, serverless architecture
  role: architect
  scope: infrastructure
  output-format: architecture
  related-skills: devops-cicd, design-system-architecture, seguranca-aplicacao-java, monitoramento-java, java-architecture
---
---

# Cloud Architect

Referência para desenhar topologias de nuvem (AWS, Azure, GCP), planejar migrações,
otimizar custo (FinOps) e estruturar disaster recovery (RTO/RPO). Cobre seleção de
serviços gerenciados, rede (VPC, peering, subnets), IAM com least-privilege, e o
Well-Architected Framework.

**Quando NÃO usar:**

- Para design de **sistemas** (escolha entre monolito e microsserviços, ADRs,
  topologia de aplicação), use `design-system-architecture`.
- Para deploy de uma aplicação Java específica (Dockerfile, manifest K8s, pipeline
  CI), use `devops-cicd` (via `engenheiro-devops`).
- Para segurança de aplicação (OWASP, JWT, headers), use `seguranca-aplicacao-java`.
- Para observabilidade de aplicação (Prometheus, OTel, Grafana), use
  `monitoramento-java`.

## Quando aplicar

- Desenhar topologia de nuvem para workload novo (VPC, subnets, IAM, networking).
- Planejar migração on-premises → cloud aplicando o framework 6Rs.
- Implementar disaster recovery com RTO/RPO definidos.
- Otimizar custo (right-sizing, reserved capacity, spot, FinOps).
- Aplicar Well-Architected Framework em arquitetura existente.
- Configurar landing zone multi-conta.

## Workflow

1. **Discovery** — levantar estado atual, requisitos, restrições, compliance.
2. **Design** — selecionar serviços, topologia, arquitetura de dados.
3. **Security** — zero-trust, identity federation, encryption (at rest e in transit).
4. **Cost Model** — right-sizing, reserved/spot, auto-scaling, cost allocation tags.
5. **Migration** — framework 6Rs, waves, validar conectividade antes do cutover.
6. **Operate** — monitoramento, automação, otimização contínua.

### Checkpoints de validação

**Após Design:** confirmar redundância em todo componente — sem single point of
failure.

**Antes do cutover de migração:** validar peering/connectivity estabelecido:

```bash
# AWS: confirmar peering connection Active antes de prosseguir
aws ec2 describe-vpc-peering-connections \
  --filters "Name=status-code,Values=active"

# Azure: confirmar VNet peering state
az network vnet peering list \
  --resource-group myRG --vnet-name myVNet \
  --query "[].{Name:name,State:peeringState}"
```

**Após migração:** verificar saúde da aplicação e roteamento:

```bash
# AWS: checar target group health no ALB
aws elbv2 describe-target-health \
  --target-group-arn arn:aws:elasticloadbalancing:...
```

**Após teste de DR:** confirmar RTO/RPO atingidos; documentar tempos reais.

## Guia de referências

| Tópico | Referência | Quando carregar |
|---|---|---|
| AWS Services | `references/aws.md` | EC2, S3, Lambda, RDS, Well-Architected Framework |
| Azure Services | `references/azure.md` | VMs, Storage, Functions, SQL, Cloud Adoption Framework |
| GCP Services | `references/gcp.md` | Compute Engine, Cloud Storage, BigQuery |
| Multi-Cloud | `references/multi-cloud.md` | Camadas de abstração, portabilidade, lock-in |
| Cost Optimization | `references/cost.md` | Reserved/spot, right-sizing, FinOps |

## Constraints

### MUST DO

- Projetar para alta disponibilidade (99.9%+).
- Security by design (zero-trust, least-privilege).
- Infrastructure as code (Terraform, CloudFormation).
- Cost allocation tags e monitoramento de gasto habilitados.
- DR com RTO/RPO definidos e testados periodicamente.
- Multi-região para workloads críticos.
- Preferir serviços gerenciados (reduz complexidade operacional).
- Documentar decisões arquiteturais (ADR — ver `design-system-architecture`).

### MUST NOT DO

- Guardar credenciais em código ou repositórios públicos.
- Pular encryption (at rest e in transit).
- Criar single point of failure.
- Ignorar oportunidades de cost optimization.
- Deploy sem monitoramento.
- Arquiteturas desnecessariamente complexas (YAGNI).
- Ignorar compliance (LGPD, PCI, SOC2 quando aplicável).
- Pular teste de DR.

## Padrões comuns com exemplos

### Least-Privilege IAM (Zero-Trust)

Políticas escopadas em recursos e ações específicas — nunca permissões amplas:

```bash
# AWS: criar role escopada para uma aplicação
aws iam create-role \
  --role-name AppRole \
  --assume-role-policy-document file://trust-policy.json

aws iam put-role-policy \
  --role-name AppRole \
  --policy-name AppInlinePolicy \
  --policy-document '{
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Action": ["s3:GetObject", "s3:PutObject"],
      "Resource": "arn:aws:s3:::my-app-bucket/*"
    }]
  }'
```

````hcl
# Equivalente em Terraform
resource "aws_iam_role" "app_role" {
  name               = "AppRole"
  assume_role_policy = data.aws_iam_policy_document.trust.json
}

resource "aws_iam_role_policy" "app_policy" {
  role = aws_iam_role.app_role.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3:GetObject", "s3:PutObject"]
      Resource = "${aws_s3_bucket.app.arn}/*"
    }]
  })
}
````

### VPC com subnets pública/privada (Terraform)

````hcl
resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
  tags = { Name = "main", CostCenter = var.cost_center }
}

resource "aws_subnet" "private" {
  count             = 2
  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet("10.0.0.0/16", 8, count.index)
  availability_zone = data.aws_availability_zones.available.names[count.index]
}

resource "aws_subnet" "public" {
  count                   = 2
  vpc_id                  = aws_vpc.main.id
  cidr_block              = cidrsubnet("10.0.0.0/16", 8, count.index + 10)
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = true
}
````

### Auto-Scaling com target tracking (Terraform)

````hcl
resource "aws_autoscaling_group" "app" {
  desired_capacity    = 2
  min_size            = 1
  max_size            = 10
  vpc_zone_identifier = aws_subnet.private[*].id

  launch_template {
    id      = aws_launch_template.app.id
    version = "$Latest"
  }

  tag {
    key                 = "CostCenter"
    value               = var.cost_center
    propagate_at_launch = true
  }
}

resource "aws_autoscaling_policy" "cpu_target" {
  autoscaling_group_name = aws_autoscaling_group.app.name
  policy_type            = "TargetTrackingScaling"
  target_tracking_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ASGAverageCPUUtilization"
    }
    target_value = 60.0
  }
}
````

### Análise de custo (CLI)

```bash
# AWS: top drivers de custo nos últimos 30 dias
aws ce get-cost-and-usage \
  --time-period Start=$(date -d '30 days ago' +%Y-%m-%d),End=$(date +%Y-%m-%d) \
  --granularity MONTHLY \
  --metrics "UnblendedCost" \
  --group-by Type=DIMENSION,Key=SERVICE \
  --query 'ResultsByTime[0].Groups[*].{Service:Keys[0],Cost:Metrics.UnblendedCost.Amount}' \
  --output table

# Azure: gasto por resource group
az consumption usage list \
  --start-date $(date -d '30 days ago' +%Y-%m-%d) \
  --end-date $(date +%Y-%m-%d) \
  --query "[].{ResourceGroup:resourceGroup,Cost:pretaxCost,Currency:currency}" \
  --output table
```

## Templates de saída

Toda entrega deve conter:

1. Diagrama de arquitetura com serviços e fluxo de dados.
2. Justificativa de seleção de serviços (compute, storage, database, networking).
3. Arquitetura de segurança (IAM, segmentação de rede, encryption).
4. Estimativa de custo e estratégia de otimização.
5. Plano de deploy e rollback.

## Quem aplica o quê

| Cenário | Agent / Modo | Skills complementares |
|---|---|---|
| Desenhar topologia cloud nova | `cloud-architect` (sessão dedicada) | `design-system-architecture`, `devops-cicd` |
| Auditar arquitetura cloud existente | `cloud-architect` (modo `auditoria`) | `seguranca-aplicacao-java` |
| Plano de migração | `cloud-architect` (sessão dedicada) | `devops-cicd`, `design-system-architecture` |
| Otimização FinOps | `cloud-architect` (sessão dedicada) | `monitoramento-java` |
| Deploy de app Java específica | `engenheiro-devops` (variante `k8s`) | `devops-cicd` |

[Documentação base](https://jeffallan.github.io/claude-skills/skills/infrastructure/cloud-architect/)
