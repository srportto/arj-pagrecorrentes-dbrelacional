# envs/prod

**Status:** placeholder — sem código Terraform ainda.

## Propósito futuro

Composição dos módulos em [`../../modules/`](../../modules/) apontando para a
AWS real, com backend de state remoto (provisionado em
[`../../bootstrap/`](../../bootstrap/)). Provisiona a VPC, o RDS PostgreSQL e o
cluster ECS/Fargate que hospedam `contratocommand` e `contratoquery`
em produção.

Também compõe o módulo [`../../modules/elasticache-valkey/`](../../modules/elasticache-valkey/)
para o cluster Valkey usado pela aplicação `temporiza-autorizacao` (agendamento de
expiração da jornada 1 do PIX Automático — ver
`openspec/changes/temporizacao-jornada-01-pix-auto`), com
`allowed_security_group_ids` restrito ao security group dessa aplicação. Este módulo
**não** é instanciado nos roots locais (`envs/local`, `envs/local-messaging`) — o
ambiente local usa o Valkey autogerenciado em
[`infra/local/redis/`](../../local/redis/).
