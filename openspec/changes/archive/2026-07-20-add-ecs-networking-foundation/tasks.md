## 1. Pré-requisitos e ambiente Floci

- [x] 1.1 Documentar/versionar como subir o Floci localmente (`floci start` ou um `compose.yaml` com `image: floci/floci:latest`, porta `4566` e o socket `/var/run/docker.sock` montado para os serviços Docker-real)
- [x] 1.2 Validar Floci no ar: `aws --endpoint-url http://localhost:4566 sts get-caller-identity` responde com credenciais fake
- [x] 1.3 Confirmar Terraform `>= 1.10` e definir o pin do provider AWS `~> 5.x` (Terraform 1.15.8 instalado via Chocolatey; `versions.tf` pina `required_version = ">= 1.10"` e `aws ~> 5.0`)

## 2. Módulo networking (modules/networking)

- [x] 2.1 Criar `variables.tf` do módulo (project_name/vpc_name, region, cidr_block, mapas de CIDRs de subnets pública/privada, contagem de NAT)
- [x] 2.2 Criar a VPC `vpc-arj` (`10.0.0.0/16`, DNS support+hostnames, tag Name)
- [x] 2.3 Criar as 3 subnets públicas `/24` (`10.0.48/49/50`) em AZs `a/b/c` via `format("%s%s", region, ...)`
- [x] 2.4 Criar as 3 subnets privadas `/20` (`10.0.0/16/32`) em AZs `a/b/c`
- [x] 2.5 Criar Internet Gateway anexado à VPC
- [x] 2.6 Criar 3 EIPs + 3 NAT Gateways (um por AZ) nas subnets públicas
- [x] 2.7 Criar route table pública (rota `0.0.0.0/0` → IGW) e associá-la às 3 subnets públicas
- [x] 2.8 Criar 3 route tables privadas (rota `0.0.0.0/0` → NAT da AZ) e associá-las às subnets privadas
- [x] 2.9 Criar security group base da VPC
- [x] 2.10 Publicar `vpc_id` e IDs das 6 subnets no SSM Parameter Store sob `/vpc-arj/vpc/...`
- [x] 2.11 Expor outputs: `vpc_id`, listas de IDs de subnets públicas e privadas, e o SG base

## 3. Módulo ecs-cluster (modules/ecs-cluster)

- [x] 3.1 Criar `variables.tf` (vpc_id, public_subnet_ids, nome do cluster, tags)
- [x] 3.2 Criar o cluster ECS habilitando o capacity provider `FARGATE`
- [x] 3.3 Criar o security group do ALB (ingresso TCP `:80` de `0.0.0.0/0`)
- [x] 3.4 Criar o ALB `internet-facing` nas 3 subnets públicas
- [x] 3.5 Criar o listener HTTP `:80` no ALB (com ação default, ex.: fixed-response 404)
- [x] 3.6 Expor outputs: `cluster_id/arn`, `alb_arn`, `alb_dns_name`, `listener_arn`, `alb_sg_id`

## 4. Módulo ecs-service (modules/ecs-service)

- [x] 4.1 Criar `variables.tf` parametrizável (nome, imagem, porta, cpu, memory, env map, health_check_path, cluster/listener/subnets/SGs de entrada)
- [x] 4.2 Criar a task definition Fargate (`requires_compatibilities = ["FARGATE"]`, `network_mode = "awsvpc"`, container com porta e env)
- [x] 4.3 Injetar `SPRING_PROFILES_ACTIVE` e as credenciais de banco via env (recebidas como variável, sem hardcode)
- [x] 4.4 Criar o target group com health check em `/actuator/health` na porta da app
- [x] 4.5 Criar a listener rule no ALB roteando para o target group (path-based por serviço)
- [x] 4.6 Criar o security group da task (ingresso na porta da app a partir do SG do ALB)
- [x] 4.7 Criar o ECS Service em Fargate nas subnets privadas, associado ao target group
- [x] 4.8 Expor outputs: `service_name`, `task_definition_arn`, `target_group_arn`

## 5. Publicação de imagens no ECR do Floci

- [x] 5.1 Criar o(s) repositório(s) ECR (via Terraform ou script) para `arj-contratocommand` e `arj-contratoquery`
- [x] 5.2 `docker build` das imagens a partir de `apps/arj-contratocommand/Dockerfile` e `apps/arj-contratoquery/Dockerfile`
- [x] 5.3 `docker tag` + `docker push` para a URI do ECR emulado; script reproduzível documentado

## 6. Ambiente envs/local (composição Floci)

- [x] 6.1 Criar `providers.tf`/`versions.tf`: provider AWS região `us-east-1`, credenciais `test`, `skip_credentials_validation`, `skip_requesting_account_id`, `skip_metadata_api_check`, `s3_use_path_style`, e `endpoints{}` (ec2, ecs, elbv2, ssm, iam, sts, ecr, logs) → `http://localhost:4566`
- [x] 6.2 Configurar backend de state local
- [x] 6.3 Instanciar o módulo `networking`
- [x] 6.4 Instanciar o módulo `ecs-cluster` consumindo os outputs de rede
- [x] 6.5 Instanciar `ecs-service` para `arj-contratocommand` (porta `8080`, imagem do ECR)
- [x] 6.6 Instanciar `ecs-service` para `arj-contratoquery` (porta `8081`, imagem do ECR)
- [x] 6.7 Definir `terraform.tfvars` do ambiente local (nomes, CIDRs, perfil Spring, credenciais de banco)
- [x] 6.8 Expor outputs úteis do ambiente (ex.: `alb_dns_name`)

## 7. Validação end-to-end contra o Floci

- [x] 7.1 `terraform init` e `terraform validate` em `envs/local` sem erros
- [x] 7.2 `terraform plan` coerente com o escopo (VPC, 6 subnets, 3 NAT, cluster, ALB, 2 services)
- [x] 7.3 `terraform apply` contra o Floci em execução
- [x] 7.4 Validar rede via AWS CLI (`--endpoint-url http://localhost:4566`): VPC `vpc-arj`, 6 subnets, IGW, 3 NAT, parâmetros SSM `/vpc-arj/vpc/...`
- [x] 7.5 Validar compute: `ecs describe-clusters`, `ecs list-services`, tasks `RUNNING`, target health, e `curl` no `alb_dns_name` (cluster ACTIVE, 2 services ACTIVE, 1 task RUNNING cada, target health `healthy` nos dois target groups; curl direto no target IP via container na rede `floci_default` confirma `/actuator/health` `UP` com `db: UP`. O proxy HTTP público do ALB via `alb_dns_name` não funciona nesta edição do Floci — limitação de emulação já prevista em design.md/Risks; control-plane do ALB e health checks ativos funcionam normalmente)
- [x] 7.6 `terraform destroy` limpa o ambiente sem resíduos (validado via `terraform plan -destroy`: 59 to destroy, 0 erros de dependência; destroy real não executado a pedido do usuário, para manter o ambiente no ar após a validação)

## 8. Documentação

- [x] 8.1 Atualizar os READMEs dos módulos e de `envs/local` (deixaram de ser placeholders) com inputs/outputs e o passo a passo local
- [x] 8.2 Atualizar `infra/README.md` refletindo que networking/ecs-cluster/ecs-service/envs-local agora têm Terraform funcional
