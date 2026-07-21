# ecs-fargate-cluster

## Purpose

Módulo Terraform (`modules/ecs-cluster`) que provisiona o cluster ECS em modo Fargate
e o Application Load Balancer internet-facing que roteia o tráfego público para os
ECS Services registrados, reutilizando a VPC provisionada pelo módulo `networking`.

## Requirements

### Requirement: Cluster ECS em modo Fargate

O módulo `ecs-cluster` SHALL provisionar um cluster ECS capaz de executar tasks em
Fargate, comportando múltiplos ECS Services independentes na mesma VPC provisionada
pelo módulo `networking`.

#### Scenario: Cluster criado
- **WHEN** o Terraform do módulo `ecs-cluster` é aplicado
- **THEN** existe um cluster ECS com capacidade de execução em Fargate

#### Scenario: Cluster comporta múltiplos serviços
- **WHEN** dois ECS Services distintos são registrados no cluster
- **THEN** ambos coexistem no mesmo cluster sem conflito

### Requirement: Application Load Balancer internet-facing

O módulo `ecs-cluster` SHALL provisionar um Application Load Balancer com esquema
`internet-facing`, posicionado nas 3 subnets públicas da VPC, para receber tráfego HTTP
da internet.

#### Scenario: ALB público nas subnets públicas
- **WHEN** o módulo é aplicado
- **THEN** existe um ALB `internet-facing` associado às 3 subnets públicas

### Requirement: Listener HTTP e roteamento para serviços

O módulo `ecs-cluster` SHALL expor um listener HTTP na porta `80` no ALB, capaz de
rotear requisições para os target groups dos serviços registrados.

#### Scenario: Listener na porta 80
- **WHEN** o módulo é aplicado
- **THEN** o ALB possui um listener HTTP escutando na porta `80`

#### Scenario: Roteamento para target group
- **WHEN** um serviço registra seu target group no ALB
- **THEN** o listener encaminha requisições correspondentes para esse target group

### Requirement: Security group do ALB

O módulo `ecs-cluster` SHALL criar um security group para o ALB que permita tráfego de
entrada na porta `80` a partir da internet (`0.0.0.0/0`) e libere o tráfego de saída
para as tasks dos serviços.

#### Scenario: Entrada HTTP liberada
- **WHEN** o módulo é aplicado
- **THEN** o security group do ALB permite ingresso TCP na porta `80` de `0.0.0.0/0`
