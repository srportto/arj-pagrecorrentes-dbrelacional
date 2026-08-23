variable "floci_endpoint" {
  description = "Endpoint HTTP do Floci (emulador AWS local)."
  type        = string
  default     = "http://localhost:4566"
}

variable "region" {
  description = "Regiao AWS emulada pelo Floci."
  type        = string
  default     = "us-east-1"
}

#### Networking ####

variable "vpc_name" {
  description = "Nome da VPC."
  type        = string
  default     = "vpc-arj"
}

variable "nat_gateway_count" {
  description = "Quantidade de NAT Gateways (1 por AZ = HA real)."
  type        = number
  default     = 3
}

#### ECS cluster ####

variable "cluster_name" {
  description = "Nome do cluster ECS."
  type        = string
  default     = "arj-cluster"
}

variable "alb_name" {
  description = "Nome do Application Load Balancer."
  type        = string
  default     = "arj-alb"
}

#### Imagens ####

variable "image_tag" {
  description = "Tag das imagens publicadas no ECR local (ver scripts/build-and-push.sh)."
  type        = string
  default     = "local"
}

#### Aplicacao ####

variable "spring_profiles_active" {
  description = "Valor de SPRING_PROFILES_ACTIVE injetado nas duas apps."
  type        = string
  default     = "local"
}

#### Banco de dados ####
#
# Aponta para o Postgres local ja existente em infra/local/postgres (fora do
# escopo desta mudanca criar um modulo rds-postgres). host.docker.internal
# resolve o host do Docker Desktop a partir de qualquer container, incluindo
# as tasks ECS emuladas pelo Floci via Docker real.

variable "db_host" {
  description = "Host do Postgres local, alcancavel a partir das tasks ECS do Floci."
  type        = string
  default     = "host.docker.internal"
}

variable "db_port" {
  description = "Porta publicada do Postgres local no host."
  type        = number
  default     = 5432
}

variable "db_name" {
  description = "Nome do banco de dados."
  type        = string
  default     = "db-csp-postgres"
}

variable "db_user_name" {
  description = "Usuario do banco de dados."
  type        = string
  default     = "docker"
}

variable "db_password" {
  description = "Senha do banco de dados."
  type        = string
  sensitive   = true
}

#### Reclamacao da particao de expurgo (change reclamar-particao-expurgo-ciclo) ####
#
# A Lambda conecta com um usuario proprio, de privilegio minimo -- nao o superuser
# `docker` usado pelas duas aplicacoes Java. O role e' criado por
# infra/local/postgres/migrations/v1.0.9.-roles-privilegio-minimo-expurgo-particao.sql
# (SELECT + GRANT TRUNCATE granular nas 100 particoes de expurgo, sem ownership).

variable "expurgo_particao_db_user_name" {
  description = "Usuario do banco de dados usado pela Lambda de reclamacao de particao de expurgo (role de privilegio minimo, nao o superuser das apps Java)."
  type        = string
  default     = "expurgo_particao_rotina"
}

variable "expurgo_particao_db_password" {
  description = "Senha do role expurgo_particao_rotina (definida ao aplicar a migration v1.0.9 -- ver README de infra/local/postgres)."
  type        = string
  sensitive   = true
}

variable "expurgo_particao_schedule_expression" {
  description = "Expressao do EventBridge Scheduler que dispara a reclamacao da particao de expurgo."
  type        = string
  default     = "rate(30 minutes)"
}
