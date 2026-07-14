# envs/local

**Status:** placeholder — sem código Terraform ainda.

## Propósito futuro

Composição dos módulos em [`../../modules/`](../../modules/) apontando para um
emulador AWS local (Floci), permitindo validar o Terraform (`plan`/`apply`) sem
custo e sem conta AWS real, antes de aplicar em [`../prod/`](../prod/).

Nesta fase, o desenvolvimento local **não** usa Floci nem Terraform — usa
Docker puro (ver [`../../local/`](../../local/) e `code/docker-compose.yml`).
Este diretório existe para a fase seguinte, quando o Floci for introduzido.
