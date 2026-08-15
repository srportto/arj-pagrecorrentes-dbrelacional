# modules/ecs-service

Módulo reutilizável e **parametrizável** para uma ECS Service em Fargate — usado
duas vezes (uma instância por aplicação: `contratocommand` e
`contratoquery`), variando imagem, porta, variáveis de ambiente e
requisitos de CPU/memória.

Consome a imagem gerada pelo `Dockerfile` de cada aplicação em
`apps/<app>/Dockerfile` (publicada previamente no ECR — ver
[`envs/local/scripts/build-and-push.sh`](../../envs/local/scripts/build-and-push.sh))
e roda dentro do cluster de [`../ecs-cluster/`](../ecs-cluster/). Mapeia health
check para `/actuator/health` e injeta `SPRING_PROFILES_ACTIVE` e as
credenciais de banco via variável de ambiente (evoluindo para Secrets Manager
em fase futura).

## O que cria

- IAM execution role (permite ao ECS puxar a imagem do ECR e escrever logs)
- CloudWatch Log Group (`/ecs/<name>`)
- Task definition Fargate (`network_mode = awsvpc`), com `SPRING_PROFILES_ACTIVE`,
  `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER_NAME`, `DB_PASSWORD` injetados
- Security group da task (ingresso só a partir do SG do ALB, na porta do container)
- Target group (`target_type = ip`, health check em `var.health_check_path`)
- Listener rule no ALB (roteamento path-based via `var.path_pattern`)
- ECS Service em Fargate nas subnets privadas

## Inputs principais

| Nome | Descrição |
|---|---|
| `name` | Nome do serviço (family da task, nome do service, tags) |
| `cluster_id`, `vpc_id`, `private_subnet_ids`, `alb_sg_id`, `listener_arn` | Outputs de `networking`/`ecs-cluster` |
| `listener_rule_priority` | Única entre os serviços que compartilham o listener |
| `path_pattern` | Ex.: `/command/*` |
| `image`, `container_port` | Imagem e porta da aplicação |
| `db_host`, `db_port`, `db_name`, `db_user_name`, `db_password` | Credenciais de banco (sem default de senha) |

## Outputs principais

`service_name`, `task_definition_arn`, `target_group_arn`, `security_group_id`,
`log_group_name`.

## Notas de validação (Floci)

- As tasks só conseguem iniciar se existir uma rede Docker chamada exatamente
  `floci_default` — ver [`envs/local/README.md`](../../envs/local/README.md#pré-requisito-rede-docker-floci_default).
- `db_host = host.docker.internal` funciona para alcançar o Postgres local
  (`infra/local/postgres`) a partir das tasks — validado com `HikariPool`
  conectando e `/actuator/health` retornando `db: UP`.
