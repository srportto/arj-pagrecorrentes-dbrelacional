# modules/networking

**Status:** placeholder — sem código Terraform ainda.

## Propósito futuro

Módulo reutilizável de rede: VPC, subnets públicas/privadas, tabelas de rota,
gateways (Internet Gateway / NAT) e security groups de base compartilhados pelos
demais módulos (`rds-postgres`, `ecs-cluster`, `ecs-service`).

Usado tanto por `envs/local` (contra o emulador AWS) quanto por `envs/prod`
(AWS real), variando apenas os parâmetros de entrada (CIDR, número de AZs, etc.).
