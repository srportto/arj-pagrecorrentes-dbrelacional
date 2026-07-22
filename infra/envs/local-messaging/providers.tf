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

  endpoints {
    sns = var.floci_endpoint
    sqs = var.floci_endpoint
    sts = var.floci_endpoint
  }
}
