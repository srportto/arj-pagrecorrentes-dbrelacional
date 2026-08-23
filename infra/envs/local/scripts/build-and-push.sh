#!/usr/bin/env bash
# Builda as imagens de contratocommand, contratoquery e expurgo-particao e publica no
# ECR emulado pelo Floci. Pre-requisitos: Floci no ar (localhost:4566), os
# repositorios ECR ja criados (terraform apply do modulo ecr.tf) e Docker.
#
# Uso: infra/envs/local/scripts/build-and-push.sh [tag]
set -euo pipefail

TAG="${1:-local}"
FLOCI_ENDPOINT="${FLOCI_ENDPOINT:-http://localhost:4566}"
AWS_REGION="${AWS_REGION:-us-east-1}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"

# nome do repositorio ECR (igual ao aws_ecr_repository.*.name em ecr.tf) -> caminho do Dockerfile
declare -A APPS=(
  ["contratocommand"]="apps/contratocommand"
  ["contratoquery"]="apps/contratoquery"
  ["expurgo-particao"]="apps/expurgo-particao"
)

for repo_name in "${!APPS[@]}"; do
  app_dir="${REPO_ROOT}/${APPS[$repo_name]}"

  echo "==> Build ${repo_name} (${app_dir})"
  docker build -t "${repo_name}:${TAG}" "${app_dir}"

  echo "==> Resolvendo URI do repositorio ECR emulado para ${repo_name}"
  repo_uri="$(aws --endpoint-url "${FLOCI_ENDPOINT}" ecr describe-repositories \
    --repository-names "${repo_name}" \
    --region "${AWS_REGION}" \
    --query 'repositories[0].repositoryUri' \
    --output text)"

  # O Floci retorna um host baseado em DNS (<conta>.dkr.ecr.<regiao>.localhost:<porta>)
  # que nao resolve fora do processo do Floci (nem no resolver do Windows, nem no
  # Docker Desktop). O registry real esta sempre acessivel em 127.0.0.1:<mesma porta>,
  # entao reescrevemos o host preservando porta e path do repositorio.
  original_host="${repo_uri%%/*}"
  registry_port="${original_host##*:}"
  repo_path="${repo_uri#*/}"
  push_uri="127.0.0.1:${registry_port}/${repo_path}"

  echo "==> Login no ECR emulado (127.0.0.1:${registry_port})"
  aws --endpoint-url "${FLOCI_ENDPOINT}" ecr get-login-password --region "${AWS_REGION}" \
    | docker login --username AWS --password-stdin "127.0.0.1:${registry_port}"

  echo "==> Tag + push ${push_uri}:${TAG}"
  docker tag "${repo_name}:${TAG}" "${push_uri}:${TAG}"
  docker push "${push_uri}:${TAG}"

  echo "==> ${repo_name} publicado em ${push_uri}:${TAG}"
  echo
done
