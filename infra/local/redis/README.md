# infra/local/redis

Valkey local (compatível com o protocolo Redis) usado pela aplicação
`apps/temporiza-autorizacao` para agendar e disparar a expiração de autorizações
`PIX_AUTO` na jornada 1 (ver capacidade `agendamento-expiracao-valkey`). Não faz parte de
nenhum módulo Terraform — é só o runtime que a aplicação assume disponível em
`localhost:6379`.

Diferente do `infra/local/floci/`: o Valkey local **não** é provisionado como ElastiCache
dentro do emulador Floci. É um container do próprio Valkey, subido direto — mais simples
e com menos partes móveis do que emular um serviço gerenciado de cache localmente. Em AWS
real, o equivalente é o módulo `infra/modules/elasticache-valkey/`.

## Subir

```bash
docker compose -f compose.yaml up -d
```

## Validar que está no ar

```bash
docker exec valkey-temporiza-autorizacao valkey-cli ping
# PONG
```

## Inspecionar o agendamento e a fila de trabalho (debug)

```bash
docker exec valkey-temporiza-autorizacao valkey-cli ZRANGE 'agenda:{pixauto:j1}' 0 -1 WITHSCORES
docker exec valkey-temporiza-autorizacao valkey-cli XRANGE 'stream:{pixauto:j1}:expiracoes' - +
docker exec valkey-temporiza-autorizacao valkey-cli XPENDING 'stream:{pixauto:j1}:expiracoes' temporizaautorizacao
```

> As chaves usam **hash tag** (`{pixauto:j1}`) — obrigatório para operação em cluster mode do
> ElastiCache em produção (garante que agenda e stream caiam no mesmo slot). Sem as chaves `{}`,
> os comandos acima consultam uma chave diferente da que a aplicação usa e retornam vazio em
> silêncio. Ver `application.yaml` (`chave-agenda`/`chave-stream`) e a armadilha 7 do `CLAUDE.md`
> de `apps/temporiza-autorizacao`.

## Parar

```bash
docker compose -f compose.yaml down
```

## Notas

- `--appendonly yes --appendfsync everysec`: persistência AOF com sincronização a cada
  segundo — agendamentos e mensagens pendentes do stream sobrevivem a um restart do
  container, com janela de perda de até ~1s de escritas.
- Porta `6379`, a default do protocolo Redis/Valkey.
- `apps/temporiza-autorizacao` aponta para este endereço via `application-local.yaml`
  (profile `local`); em `prod`, aponta para o endpoint do módulo
  `infra/modules/elasticache-valkey/`.
