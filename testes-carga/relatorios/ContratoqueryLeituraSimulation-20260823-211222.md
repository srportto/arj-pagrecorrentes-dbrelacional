# Relatorio de execucao: carga.scenarios.ContratoqueryLeituraSimulation

- Data/hora: 2026-08-23T21:17:26-03:00
- Codigo de saida do Gatling: 0 (concluido normalmente)
- Ambiente: SO LOCAL (docker-compose + Floci) -- nao representa capacidade de producao
  (Requirement "Escopo de ambiente local sem extrapolacao para producao").
- Configuracao vigente (baseline, sem recalibrar -- ver proposal.md):
  - DB_POOL_MAX_SIZE=10 (default)
  - MAX_CONCURRENT_MESSAGES (SQS listeners) = 10 (hardcoded em SqsListenerContainerFactoryConfig)
  - Kafka consumer concurrency = 1 (default, sem override em KafkaConsumerConfig)
- Limites de recursos por container (D5): ver deploy.resources.limits em compose.yaml/apps/docker-compose.yml/infra/local/*.
- Relatorio HTML detalhado do Gatling: testes-carga/target/gatling/ (pasta mais recente).
