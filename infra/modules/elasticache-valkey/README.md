# modules/elasticache-valkey

Módulo reutilizável de cluster ElastiCache com engine **Valkey**, usado pela aplicação
`temporiza-autorizacao` (ver `openspec/changes/temporizacao-jornada-01-pix-auto`) para
agendamento e disparo de expiração via sorted set + stream com consumer group.

Segue o mesmo padrão dos demais módulos (`ecs-cluster`, `ecs-service`, `networking`): sem
valores fixos de ambiente, rede recebida de fora, security group próprio restrito por
`allowed_security_group_ids`.

## O que este módulo NÃO faz

- Não cria VPC, subnets nem internet gateway — recebe `vpc_id` e `private_subnet_ids` do
  módulo `networking`.
- Não habilita nenhuma forma de acesso público — sem `allowed_security_group_ids`
  configurado corretamente, o cluster fica inacessível (comportamento intencional: falha
  segura, não exposição acidental).
- Não configura append-only-file: o ElastiCache gerenciado não expõe essa opção ao
  usuário. A durabilidade entre reinícios de nó é via snapshot automático
  (`snapshot_retention_limit`). Para AOF de verdade, ver o Valkey autogerenciado em
  `infra/local/redis/` (ambiente local apenas).

## Uso típico

```hcl
module "valkey_temporiza_autorizacao" {
  source = "../../modules/elasticache-valkey"

  name                       = "valkey-temporiza-autorizacao"
  vpc_id                     = module.networking.vpc_id
  private_subnet_ids         = module.networking.private_subnet_ids
  allowed_security_group_ids = [module.temporiza_autorizacao_service.security_group_id]
}
```

## Variáveis principais

| Nome | Descrição | Default |
|---|---|---|
| `name` | Nome do cluster | — |
| `vpc_id` / `private_subnet_ids` | Rede (do módulo `networking`) | — |
| `allowed_security_group_ids` | Quem pode acessar a porta do Valkey | — (obrigatório, sem default) |
| `node_type` | Tipo de instância do nó | `cache.t4g.micro` |
| `num_cache_nodes` | Número de nós (sem cluster mode nesta fase) | `1` |
| `snapshot_retention_limit` | Dias de retenção de snapshot automático | `1` |

## Saídas

- `endpoint` — host de conexão
- `port` — porta de conexão
- `security_group_id` — para referência por outros módulos
