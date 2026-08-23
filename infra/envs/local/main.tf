module "networking" {
  source = "../../modules/networking"

  region            = var.region
  vpc_name          = var.vpc_name
  nat_gateway_count = var.nat_gateway_count
}

module "ecs_cluster" {
  source = "../../modules/ecs-cluster"

  vpc_id            = module.networking.vpc_id
  public_subnet_ids = module.networking.public_subnet_ids
  cluster_name      = var.cluster_name
  alb_name          = var.alb_name
}

module "ecs_service_contratocommand" {
  source = "../../modules/ecs-service"

  name       = "contratocommand"
  region     = var.region
  cluster_id = module.ecs_cluster.cluster_id

  vpc_id             = module.networking.vpc_id
  private_subnet_ids = module.networking.private_subnet_ids
  alb_sg_id          = module.ecs_cluster.alb_sg_id
  listener_arn       = module.ecs_cluster.listener_arn

  listener_rule_priority = 10
  path_pattern           = "/command/*"

  image          = local.contratocommand_image_uri
  container_port = 8080

  spring_profiles_active = var.spring_profiles_active
  db_host                = var.db_host
  db_port                = var.db_port
  db_name                = var.db_name
  db_user_name           = var.db_user_name
  db_password            = var.db_password
}

module "ecs_service_contratoquery" {
  source = "../../modules/ecs-service"

  name       = "contratoquery"
  region     = var.region
  cluster_id = module.ecs_cluster.cluster_id

  vpc_id             = module.networking.vpc_id
  private_subnet_ids = module.networking.private_subnet_ids
  alb_sg_id          = module.ecs_cluster.alb_sg_id
  listener_arn       = module.ecs_cluster.listener_arn

  listener_rule_priority = 20
  path_pattern           = "/query/*"

  image          = local.contratoquery_image_uri
  container_port = 8081

  spring_profiles_active = var.spring_profiles_active
  db_host                = var.db_host
  db_port                = var.db_port
  db_name                = var.db_name
  db_user_name           = var.db_user_name
  db_password            = var.db_password
}

## Reclamacao periodica da particao de expurgo permitida do ciclo (change
## reclamar-particao-expurgo-ciclo) -- fecha o ring buffer cujo lado de escrita e'
## descrito por transferirParaExpurgo (contratocommand). Nao e' um ECS Service: e' uma
## Lambda agendada, provisionada pelo mesmo Floci que emula o resto do ambiente local.
##
## db_host = var.db_host (host.docker.internal por padrao, D4 do design.md) segue o
## MESMO precedente ja validado pelas duas ECS Services acima -- o modulo
## lambda-scheduled recebe db_host como variavel opaca, exatamente como ecs-service.
module "expurgo_particao" {
  source = "../../modules/lambda-scheduled"

  name      = "expurgo-particao"
  region    = var.region
  image_uri = local.expurgo_particao_image_uri

  schedule_expression = var.expurgo_particao_schedule_expression

  db_host      = var.db_host
  db_port      = var.db_port
  db_name      = var.db_name
  db_user_name = var.expurgo_particao_db_user_name
  db_password  = var.expurgo_particao_db_password
}
