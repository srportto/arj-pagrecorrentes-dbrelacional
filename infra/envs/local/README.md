# envs/local

Composição dos módulos em [`../../modules/`](../../modules/) apontando para o
[Floci](../../../docs/floci-aws-local/floci-aws-local.md) (emulador AWS local),
permitindo validar o Terraform (`plan`/`apply`) sem custo e sem conta AWS real,
antes de aplicar em `envs/prod` (fase futura).

Provisiona: VPC `vpc-arj` (6 subnets, 3 NAT), cluster ECS Fargate + ALB
internet-facing, e os dois ECS Services (`arj-contratocommand` :8080,
`arj-contratoquery` :8081).

## Pré-requisitos

1. **Floci no ar** — ver [`infra/local/floci/README.md`](../../local/floci/README.md).
   Se você já tem um Floci de outro projeto rodando na porta `4566`, pode reaproveitar
   (é um emulador de máquina inteira, não por-projeto).
2. **Terraform `>= 1.10`**.
3. **Rede Docker `floci_default`** — o controlador ECS do Floci espera essa rede
   pelo nome exato pra anexar os containers das tasks. Se o seu Floci não foi subido
   com um `docker compose` cujo projeto se chame `floci` (rede resultante
   `floci_default`), crie manualmente e conecte o container do Floci a ela:
   ```bash
   docker network create floci_default
   docker network connect floci_default <nome-do-container-floci>
   ```
   Sem o segundo passo, as tasks até sobem, mas o health check dos target groups
   nunca fica `healthy` (o simulador de ALB do Floci não está na mesma rede pra
   alcançar o IP da task).
4. **Postgres local no ar** — `infra/local/postgres/postgres-db-v18.yml`, na porta
   `5432` do host. As tasks alcançam via `host.docker.internal:5432`.
5. **`db_password` via variável de ambiente** — a variável `db_password` (ver
   `variables.tf`) não tem default nem `terraform.tfvars` versionado (removido do git em
   2026-08; segredo não pertence a arquivo versionado). Exporte antes do `terraform plan`/`apply`:
   ```bash
   export TF_VAR_db_password="<mesma senha do DB_PASSWORD no .env da raiz do repositório>"
   ```
   Sem isso, o Terraform pede o valor interativamente.

## Rodar

```bash
terraform init
terraform plan

# 1) Repos ECR primeiro (as tasks referenciam a imagem, que so existe apos o push)
terraform apply -target=aws_ecr_repository.contratocommand -target=aws_ecr_repository.contratoquery

# 2) Build + push das duas imagens
./scripts/build-and-push.sh local

# 3) Resto da infra (rede, cluster, ALB, services)
terraform apply
```

## Validar

```bash
export FE="--endpoint-url http://localhost:4566 --region us-east-1"

aws $FE ecs describe-services --cluster arj-cluster --services arj-contratocommand arj-contratoquery \
  --query 'services[].{Name:serviceName,Desired:desiredCount,Running:runningCount}'

# target health
aws $FE elbv2 describe-target-health --target-group-arn "$(aws $FE elbv2 describe-target-groups \
  --names arj-contratocommand-tg --query 'TargetGroups[0].TargetGroupArn' --output text)"
```

No Git Bash (Windows), use `MSYS_NO_PATHCONV=1` antes de comandos `aws` cujo
argumento comece com `/` (ex.: `ssm get-parameter --name /vpc-arj/...`,
`logs get-log-events --log-group-name /ecs/...`) — sem isso o MSYS reescreve o
path como se fosse um caminho de arquivo do Windows.

## Limitação conhecida: proxy HTTP do ALB

O control-plane do ALB funciona (criação, listener, target group, health checks
ativos reportam `healthy` corretamente), mas o **data-plane** — um cliente
externo fazendo `curl` no `alb_dns_name` público — não funciona na edição
gratuita do Floci testada. Para validar a aplicação fim-a-fim localmente, acesse
o IP da task diretamente a partir de outro container na rede `floci_default`:

```bash
docker run --rm --network floci_default curlimages/curl:latest \
  -s http://<ip-da-task>:8080/actuator/health
```

O IP da task aparece em `aws $FE elbv2 describe-target-health --target-group-arn ...`.

## Limpar

```bash
terraform destroy
```

`terraform plan -destroy` foi validado (59 recursos, 0 erros de dependência) sem
executar o destroy de fato, para deixar o ambiente no ar após a validação inicial.
