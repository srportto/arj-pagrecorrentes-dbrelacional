## Repositorios ECR emulados pelo Floci (registry:2 real por baixo) para as duas
## apps. Populados pelo script scripts/build-and-push.sh antes do deploy dos
## ECS Services (o ECS precisa da imagem ja publicada para conseguir puxa-la).

resource "aws_ecr_repository" "contratocommand" {
  name = "contratocommand"
}

resource "aws_ecr_repository" "contratoquery" {
  name = "contratoquery"
}

## Terceira imagem publicada pelo mesmo caminho ECR: a Lambda de reclamacao da
## particao de expurgo (change reclamar-particao-expurgo-ciclo). Nao e' Java/Spring,
## mas segue a mesma cadeia -- so o Dockerfile de origem muda
## (apps/expurgo-particao/Dockerfile, a partir de public.ecr.aws/lambda/python).
resource "aws_ecr_repository" "expurgo_particao" {
  name = "expurgo-particao"
}

## O Floci retorna repository_url com um host baseado em DNS
## (<conta>.dkr.ecr.<regiao>.localhost:<porta>) que nao resolve fora do proprio
## processo do Floci - nem no resolver do host, nem no Docker Desktop (que e quem
## efetivamente puxa a imagem para as tasks ECS). O registry real esta sempre
## acessivel em 127.0.0.1:<mesma porta>, entao as tasks referenciam a imagem por
## esse host, preservando porta e nome do repositorio. scripts/build-and-push.sh
## publica a imagem no mesmo host, entao os dois lados sempre batem.
locals {
  contratocommand_image_uri = format(
    "127.0.0.1:%s/%s:%s",
    split(":", split("/", aws_ecr_repository.contratocommand.repository_url)[0])[1],
    aws_ecr_repository.contratocommand.name,
    var.image_tag,
  )

  contratoquery_image_uri = format(
    "127.0.0.1:%s/%s:%s",
    split(":", split("/", aws_ecr_repository.contratoquery.repository_url)[0])[1],
    aws_ecr_repository.contratoquery.name,
    var.image_tag,
  )

  expurgo_particao_image_uri = format(
    "127.0.0.1:%s/%s:%s",
    split(":", split("/", aws_ecr_repository.expurgo_particao.repository_url)[0])[1],
    aws_ecr_repository.expurgo_particao.name,
    var.image_tag,
  )
}
