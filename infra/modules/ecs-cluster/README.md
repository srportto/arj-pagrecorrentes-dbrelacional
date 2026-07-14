# modules/ecs-cluster

**Status:** placeholder — sem código Terraform ainda.

## Propósito futuro

Módulo reutilizável para o cluster ECS (modo Fargate) e o Application Load
Balancer compartilhado que roteia para os serviços definidos em
[`../ecs-service/`](../ecs-service/).

Um único cluster deve comportar os dois serviços deste monorepo
(`arj-contratocommand` :8080, `arj-contratoquery` :8081) como ECS Services
independentes.
