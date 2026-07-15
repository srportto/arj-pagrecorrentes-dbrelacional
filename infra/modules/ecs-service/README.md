# modules/ecs-service

**Status:** placeholder — sem código Terraform ainda.

## Propósito futuro

Módulo reutilizável e **parametrizável** para uma ECS Service em Fargate —
usado duas vezes (uma instância por aplicação: `arj-contratocommand` e
`arj-contratoquery`), variando imagem, porta, variáveis de ambiente e
requisitos de CPU/memória.

Consome a imagem gerada pelo `Dockerfile` de cada aplicação em
`apps/<app>/Dockerfile` e roda dentro do cluster de
[`../ecs-cluster/`](../ecs-cluster/). Deve mapear health check para
`/actuator/health` e injetar `SPRING_PROFILES_ACTIVE=prod` e as credenciais de
banco (via variável de ambiente, evoluindo para Secrets Manager em fase futura).
