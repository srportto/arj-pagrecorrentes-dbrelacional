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

  name       = "arj-contratocommand"
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

  name       = "arj-contratoquery"
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
