# bootstrap

**Status:** placeholder — sem código Terraform ainda.

## Propósito futuro

Infraestrutura mínima e pré-requisito dos demais ambientes: bucket S3 para
armazenar o Terraform state e tabela DynamoDB para lock de state, usados pelo
backend remoto de [`../envs/prod/`](../envs/prod/) (e futuramente
[`../envs/local/`](../envs/local/), se o backend local também apontar para o
emulador).

Aplicado uma única vez, manualmente, antes de qualquer outro ambiente — não
depende dos módulos em `../modules/`.
