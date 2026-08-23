# modules/lambda-scheduled

Módulo reutilizável para uma Lambda baseada em imagem de container, disparada
periodicamente por um EventBridge Scheduler. Usado pela app
[`apps/expurgo-particao`](../../../apps/expurgo-particao) (change
`reclamar-particao-expurgo-ciclo`), que a cada 30 minutos reclama a partição de
expurgo permitida do ciclo do ring buffer do `contratocommand`.

Consome a imagem publicada no ECR (ver
[`envs/local/scripts/build-and-push.sh`](../../envs/local/scripts/build-and-push.sh),
mesmo caminho das duas aplicações Java) e injeta `DB_HOST`, `DB_PORT`, `DB_NAME`,
`DB_USER_NAME`, `DB_PASSWORD` via variável de ambiente — mesmo contrato do módulo
[`ecs-service`](../ecs-service/), para que local e AWS real divirjam só no
**valor** de `db_host`, nunca no desenho.

## O que cria

- IAM execution role da Lambda (`AWSLambdaBasicExecutionRole` — só logging;
  privilégio sobre o banco vem do Postgres, não do IAM, ver Notas abaixo)
- IAM role assumida pelo EventBridge Scheduler, com permissão restrita a
  `lambda:InvokeFunction` só sobre esta função
- CloudWatch Log Group (`/aws/lambda/<name>`)
- Função Lambda `PackageType = Image` a partir de `var.image_uri`
- EventBridge Schedule (`var.schedule_expression`, padrão `rate(30 minutes)`)

## Inputs principais

| Nome | Descrição |
|---|---|
| `name` | Nome da função (roles, log group e schedule derivam dele) |
| `image_uri` | URI da imagem no ECR (repositório + tag) |
| `schedule_expression` | Padrão `rate(30 minutes)` |
| `db_host`, `db_port`, `db_name`, `db_user_name`, `db_password` | Credenciais de banco (sem default de senha) |
| `environment` | Variáveis extras — ex.: `EXPURGO_PARTICAO_DESARMAR_TRUNCATE` |

## Outputs principais

`function_name`, `function_arn`, `schedule_arn`.

## Notas de validação (Floci)

- **Spike de rede confirmado** (change `reclamar-particao-expurgo-ciclo`,
  design.md D4): um container de Lambda publicado no Floci
  (`public.ecr.aws/lambda/python:3.13`, Docker real) alcança
  `host.docker.internal:5432` normalmente — mesmo caminho já usado pelas tasks
  ECS do módulo `ecs-service`. A Lambda nasce como container **irmão** do
  projeto Compose (via `docker.sock`), não como membro dele; por isso a saída é
  pelo host, não por uma rede Docker compartilhada.
- O privilégio de banco **não** é concedido via IAM: a Lambda conecta como o
  role `expurgo_particao_rotina`, criado no Postgres com `GRANT TRUNCATE`
  granular só nas 100 partições de expurgo (`900..999`) e `SELECT` na tabela —
  nunca ownership. Ver
  `infra/local/postgres/migrations/v1.0.9.-roles-privilegio-minimo-expurgo-particao.sql`.
- Na AWS real, a função ganharia `vpc_config` apontando para as
  `private_subnets` do módulo `networking`, com security group liberando 5432
  para o SG do RDS — `db_host` deixa de ser `host.docker.internal` e passa a
  ser o endpoint do RDS, sem nenhuma outra mudança de desenho.
