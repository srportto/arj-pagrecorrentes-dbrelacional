# modules/ecs-cluster

Módulo reutilizável para o cluster ECS (modo Fargate) e o Application Load
Balancer compartilhado que roteia para os serviços definidos em
[`../ecs-service/`](../ecs-service/).

Um único cluster comporta os dois serviços deste monorepo
(`contratocommand` :8080, `contratoquery` :8081) como ECS Services
independentes.

## O que cria

- 1 cluster ECS com capacity providers `FARGATE`/`FARGATE_SPOT` (default `FARGATE`)
- 1 security group do ALB (ingresso `:80` de `0.0.0.0/0`, egress liberado)
- 1 Application Load Balancer `internet-facing` nas subnets públicas
- 1 listener HTTP `:80` com ação default `fixed-response 404` — cada `ecs-service`
  registra sua própria listener rule (path-based) apontando para o listener

## Inputs principais

| Nome | Descrição |
|---|---|
| `vpc_id` | Output do módulo `networking` |
| `public_subnet_ids` | Output do módulo `networking` |
| `cluster_name` | Default `arj-cluster` |
| `alb_name` | Default `arj-alb` |

## Outputs principais

`cluster_id`, `cluster_arn`, `cluster_name`, `alb_arn`, `alb_dns_name`,
`listener_arn` (consumido pelos `ecs-service` para registrar listener rules),
`alb_sg_id` (consumido pelos `ecs-service` para liberar ingresso nas tasks).

## Limitação conhecida no Floci

O control-plane do ALB (criação, listener, target group, health checks ativos)
funciona normalmente e foi validado — os target groups reportam `healthy`
corretamente. O **data-plane** (proxy HTTP real de um cliente externo através do
`alb_dns_name` até o target) não funciona na edição gratuita do Floci testada;
para validar a aplicação fim-a-fim localmente, acesse o IP da task diretamente
a partir de um container na mesma rede Docker (`floci_default`).
