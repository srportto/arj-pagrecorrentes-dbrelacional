# envs/prod

**Status:** placeholder — sem código Terraform ainda.

## Propósito futuro

Composição dos módulos em [`../../modules/`](../../modules/) apontando para a
AWS real, com backend de state remoto (provisionado em
[`../../bootstrap/`](../../bootstrap/)). Provisiona a VPC, o RDS PostgreSQL e o
cluster ECS/Fargate que hospedam `arj-contratocommand` e `arj-contratoquery`
em produção.
