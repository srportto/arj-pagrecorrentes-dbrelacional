## Provider AWS apontado para o Floci (emulador AWS local, ver
## docs/floci-aws-local/floci-aws-local.md). Nenhuma credencial real e usada;
## nenhum recurso de nuvem real e tocado por este ambiente.
provider "aws" {
  region = var.region

  access_key = "test"
  secret_key = "test"

  skip_credentials_validation = true
  skip_requesting_account_id  = true
  skip_metadata_api_check     = true
  s3_use_path_style           = true

  endpoints {
    ec2            = var.floci_endpoint
    ecs            = var.floci_endpoint
    elbv2          = var.floci_endpoint
    ssm            = var.floci_endpoint
    iam            = var.floci_endpoint
    sts            = var.floci_endpoint
    ecr            = var.floci_endpoint
    cloudwatchlogs = var.floci_endpoint
  }
}
