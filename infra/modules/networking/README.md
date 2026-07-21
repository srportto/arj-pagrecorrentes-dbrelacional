# modules/networking

Módulo reutilizável de rede: VPC, subnets públicas/privadas, tabelas de rota,
Internet Gateway, NAT Gateways e um security group base compartilhado pelos
demais módulos (`ecs-cluster`, `ecs-service`).

Usado por [`envs/local`](../../envs/local/) (contra o emulador Floci) e, futuramente,
por `envs/prod` (AWS real), variando apenas os inputs.

## O que cria

- 1 VPC (`aws_vpc`), nome via `var.vpc_name` (default `vpc-arj`)
- 3 subnets públicas + 3 subnets privadas, uma por AZ (`var.az_suffixes`, default `a`/`b`/`c`)
- 1 Internet Gateway
- `var.nat_gateway_count` NAT Gateways + EIPs (default 3 = 1 por AZ)
- 1 route table pública (rota `0.0.0.0/0` → IGW) + 3 route tables privadas (rota
  `0.0.0.0/0` → NAT da própria AZ; se `nat_gateway_count` < nº de AZs, AZs excedentes
  compartilham NAT via round-robin)
- 1 security group base (tráfego interno da VPC liberado, egress liberado)
- Parâmetros SSM em `/{vpc_name}/vpc/...` (vpc_id + 6 subnets)

As AZs são derivadas via `format("%s%s", var.region, sufixo)` — não usa
`data "aws_availability_zones"` para não depender da fidelidade do
`DescribeAvailabilityZones` no Floci.

## Inputs principais

| Nome | Default | Descrição |
|---|---|---|
| `region` | — (obrigatório) | Região AWS (ou emulada) |
| `vpc_name` | `vpc-arj` | Nome da VPC e prefixo dos parâmetros SSM |
| `vpc_cidr` | `10.0.0.0/16` | CIDR da VPC |
| `public_subnet_cidrs` | `10.0.48/49/50.0/24` | CIDRs das 3 subnets públicas |
| `private_subnet_cidrs` | `10.0.0/16/32.0/20` | CIDRs das 3 subnets privadas |
| `nat_gateway_count` | `3` | Quantidade de NAT Gateways (1 a nº de AZs) |

## Outputs principais

`vpc_id`, `public_subnet_ids`, `private_subnet_ids`, `internet_gateway_id`,
`nat_gateway_ids`, `base_security_group_id`, `availability_zones`.

## Validado contra

Floci (`terraform apply` em [`envs/local`](../../envs/local/)) — ver histórico da
mudança `add-ecs-networking-foundation` em `openspec/changes/`.
