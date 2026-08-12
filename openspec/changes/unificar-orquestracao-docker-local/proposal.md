## Why

Subir o ambiente local exige quatro `docker compose up` em ordem correta, e nada no repositório
diz qual é a ordem. O `apps/docker-compose.yml` declara **quatro redes externas** —
`postgres_default`, `floci_local`, `redis_default`, `kafka-eventos-autorizacao` — que só existem
depois que outros composes subiram. Errar a ordem dá erro de rede, não mensagem útil.

```
                       apps/docker-compose.yml
                                 │
        networks: external ──────┼─────────────────┬──────────────┬─────────────┐
                                 ▼                 ▼              ▼             ▼
                          postgres_default    floci_local    redis_default   kafka-...
                                 │                 │              │             │
                                 ✗            infra/local/    infra/local/  infra/local/
                          (não há compose      floci/          redis/        kafka/
                           que crie essa       compose.yaml    compose.yaml  compose.yaml
                           rede com esse nome)
```

Mas o defeito grave não é a ergonomia — é o **PostgreSQL estar definido duas vezes, com
configurações divergentes**:

| | `infra/local/postgres/postgres-db-v18.yml` | `apps/docker-compose.yml` |
|---|---|---|
| Monta `./migrations` como initdb | **sim** | **não** |
| `shared_preload_libraries` | `pg_cron` | `pg_partman_bgw` **e** `pg_cron`, em dois `-c` |
| `cron.database_name` | fixo `db-csp-postgres` | `${DB_NAME:-...}` |
| Healthcheck | não tem | tem |
| `POSTGRES_DB` / `POSTGRES_USER` | fixos | por variável |
| `container_name` | `postgres18-kiq` | default |

Duas consequências concretas:

1. **Um banco subido pelo `apps/docker-compose.yml` nasce sem schema.** Ele não monta
   `./migrations:/docker-entrypoint-initdb.d`, que é o mecanismo que cria a tabela `autorizacoes`
   particionada na primeira subida. Quem seguir o caminho de "sobe tudo pelo apps/" recebe um
   PostgreSQL vazio e as apps falhando, sem pista do motivo.
2. **`pg_partman_bgw` provavelmente não carrega.** O `apps/docker-compose.yml` passa
   `shared_preload_libraries` em dois `-c` separados; no PostgreSQL, o último valor da mesma GUC
   na linha de comando prevalece — o que faria `pg_cron` sobrescrever `pg_partman_bgw`, deixando
   o background worker de manutenção de partições fora do ar em silêncio. A confirmar por
   `SHOW shared_preload_libraries`.

Há ainda três arquivos `.env` espalhados (raiz, `apps/`, `infra/local/postgres/`), e
`temporiza-autorizacao` publica `- "8084"` (porta aleatória no host) enquanto as outras quatro
apps mapeiam `porta:porta`.

## Restrição que o desenho precisa respeitar

Fundir os composes num arquivo só **violaria specs vigentes**. `local-kafka-environment` exige:

> Subir ou derrubar esse compose NÃO SHALL criar, alterar ou depender de recursos do compose de
> apps (`apps/docker-compose.yml`)

E `local-valkey-environment` exige independência do Floci, do Kafka e do PostgreSQL. O isolamento
é requisito, não acidente de organização. O desenho tem que dar **um ponto de entrada único sem
tirar a independência de cada peça** — o que a diretiva `include:` do Compose faz, e a fusão não.

## What Changes

- Criar um ponto de entrada único na raiz que componha os ambientes locais via `include:`,
  preservando a capacidade de subir cada um isoladamente.
- Eliminar a definição duplicada de PostgreSQL: uma única fonte, com as migrations montadas e o
  healthcheck presente nos dois caminhos de subida.
- Corrigir o `shared_preload_libraries` para uma única diretiva com lista, confirmando por
  `SHOW` que `pg_partman_bgw` e `pg_cron` carregam ambos.
- Substituir as quatro redes externas por composição declarada, de modo que a ordem de subida
  deixe de ser conhecimento tácito.
- Uniformizar a publicação de portas: `temporiza-autorizacao` passa a `8084:8084`.
- Consolidar a configuração de ambiente em um `.env` de referência único, com `.env.example`
  correspondente.

## Capabilities

### New Capabilities

- `orquestracao-local-unificada`: ponto de entrada único para o ambiente local, com garantia de
  que cada ambiente continua subindo isoladamente, e regra de fonte única por serviço de
  infraestrutura.

### Modified Capabilities

Nenhuma. `local-kafka-environment`, `local-valkey-environment`, `local-aws-environment` e
`local-messaging-environment` descrevem cada ambiente isoladamente e seguem válidas — a nova
capacidade adiciona a camada de composição **acima** delas, sem alterar seus requisitos. A
independência que elas exigem é preservada por construção (ver a restrição acima).

## Impact

**Arquivos de orquestração**
- `apps/docker-compose.yml` (131 linhas) — remoção do serviço `postgres` duplicado e das redes
  declaradas `external: true`
- `infra/local/postgres/postgres-db-v18.yml` — vira a fonte única do PostgreSQL local
- `infra/local/kafka/compose.yaml` — inalterado no conteúdo, referenciado pelo `include:` (já
  nomeia sua rede explicitamente)
- `infra/local/floci/compose.yaml`, `infra/local/redis/compose.yaml` — **revisão em 2026-08-11
  (D4)**: precisam de rede nomeada explicitamente, ao contrário do que esta seção estimava
  originalmente — ver `design.md` › D4 para a evidência empírica
- Novo arquivo de composição na raiz

**Configuração**
- `.env` / `apps/.env` / `infra/local/postgres/.env` — consolidação
- `.env.example` correspondente

**Documentação**
- `README.md` raiz, seção "Começando" — hoje descreve a subida serviço a serviço
- Os `README.md` de `infra/local/*` — a instrução de subida isolada continua válida e SHALL
  permanecer, porque a independência é requisito

**Fora de escopo**
- Terraform (`infra/envs/`, `infra/modules/`). Esta change trata só de Docker Compose local.
- Fundir os composes de Kafka, Valkey e Floci — proibido pelas specs vigentes.
- Enxugar os `README.md` de `infra/` — é a change `enxugar-documentacao-repo`.
