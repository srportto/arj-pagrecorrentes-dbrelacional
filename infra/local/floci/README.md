# infra/local/floci

Emulador AWS local ([Floci](../../../docs/floci-aws-local/floci-aws-local.md)) usado
pelo Terraform de [`envs/local`](../../envs/local/) para provisionar rede e ECS sem
conta AWS real e sem custo. Não é implantado na cloud.

## Subir

```bash
docker compose -f compose.yaml up -d
```

## Validar que está no ar

```bash
aws --endpoint-url http://localhost:4566 sts get-caller-identity
```

Qualquer valor não-vazio funciona como `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`; o
provider Terraform em `envs/local` já usa `test`/`test`.

## Parar

```bash
docker compose -f compose.yaml down
```

## Notas

- O socket do Docker (`/var/run/docker.sock`) é montado porque o Floci roda ECS, RDS e
  ElastiCache como containers Docker reais, não como mocks.
- `FLOCI_STORAGE_MODE=hybrid` mantém o estado entre reinícios do container sem o custo de
  persistência síncrona a cada escrita — suficiente para desenvolvimento local.
- Este diretório não faz parte de nenhum módulo Terraform; é só o runtime que os módulos
  de `envs/local` assumem estar disponível em `http://localhost:4566`.
