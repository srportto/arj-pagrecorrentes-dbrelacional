# local-valkey-environment

## Purpose

Descreve a instância Valkey local, provisionada via Docker Compose e independente do Floci,
usada pela aplicação `temporiza-autorizacao` em desenvolvimento.

## Requirements

### Requirement: Valkey local isolado em infra/local/redis

O ambiente local SHALL prover uma instância Valkey via Docker Compose em
`infra/local/redis/`, no mesmo padrão de `infra/local/kafka/` e `infra/local/postgres/`:
compose próprio, README de subida/validação/parada, e independência dos demais ambientes
locais. Subir o Valkey NÃO SHALL exigir o Floci, o Kafka nem o PostgreSQL no ar.

A instância SHALL habilitar persistência em append-only file com sincronização a cada
segundo, espelhando a garantia exigida em produção.

#### Scenario: Subida independente
- **WHEN** `docker compose up -d` é executado em `infra/local/redis/` sem nenhum outro
  ambiente local no ar
- **THEN** a instância Valkey fica disponível e responde a um comando de ping

#### Scenario: Persistência habilitada
- **WHEN** a configuração efetiva da instância local é inspecionada
- **THEN** o append-only file está habilitado com sincronização a cada segundo

#### Scenario: README com o ciclo completo
- **WHEN** um desenvolvedor abre `infra/local/redis/README.md`
- **THEN** encontra os comandos de subir, validar e parar, e o endereço/porta usados pela
  aplicação `temporiza-autorizacao`

### Requirement: Valkey local não é emulado pelo Floci

O Valkey local SHALL ser um container do próprio Valkey, e NÃO SHALL ser provisionado como
recurso ElastiCache dentro do emulador Floci. O root Terraform `infra/envs/local-messaging/`
NÃO SHALL passar a depender de recursos de cache.

#### Scenario: Nenhum recurso ElastiCache no ambiente local
- **WHEN** os roots Terraform de ambiente local são aplicados
- **THEN** nenhum recurso ElastiCache é criado no emulador
- **AND** a aplicação local conecta diretamente ao container Valkey
