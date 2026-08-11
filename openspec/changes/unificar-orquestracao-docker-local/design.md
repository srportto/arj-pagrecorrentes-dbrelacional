## Topologia atual

```
raiz/
├── apps/docker-compose.yml ──────── 6 serviços, 4 redes EXTERNAS
│     ├── postgres          (build ../infra/local/postgres)  ◄── DUPLICATA
│     ├── arj-contratocommand  8080:8080
│     ├── arj-contratoquery    8081:8081
│     ├── autorizacaostatus-producer 8082:8082
│     ├── eventos-consumer     8083:8083
│     └── temporiza-autorizacao  "8084"  ◄── porta aleatória no host
│
└── infra/local/
      ├── postgres/postgres-db-v18.yml ── postgres  ◄── FONTE ORIGINAL (com migrations)
      ├── floci/compose.yaml ──────────── floci                     (13 linhas)
      ├── kafka/compose.yaml ──────────── kafka + schema-reg + UI    (95 linhas)
      └── redis/compose.yaml ──────────── valkey                    (18 linhas)

redes externas exigidas por apps/:  postgres_default · floci_local
                                    redis_default    · kafka-eventos-autorizacao
```

Nenhum arquivo declara a ordem de subida. `floci_local` ainda usa
`name: floci-aws-local_default` — nome derivado de um diretório que não é mais o que existe.

## Decisões

### D1 — `include:` em vez de fusão

**Decisão:** um compose de raiz que usa `include:` para trazer os quatro ambientes locais.

**Alternativas descartadas:**

| | Como | Por que não |
|---|---|---|
| Fusão num arquivo | tudo em `compose.yaml` na raiz | **Viola** `local-kafka-environment` e `local-valkey-environment`, que exigem subida isolada |
| Perfis (`profiles:`) | um arquivo, serviços por perfil | Mesma violação: um arquivo só significa um ciclo de vida só |
| Script `sobe-tudo.sh` | orquestra os 4 `docker compose up` | Resolve a ordem, não resolve a duplicata do Postgres nem as redes externas; e vira um segundo lugar onde a topologia vive |
| Manter como está + documentar | só escrever a ordem no README | Não corrige nenhum dos dois defeitos reais |

**Racional:** `include:` é a única opção que dá ponto de entrada único **e** preserva o requisito
de isolamento. Cada `compose.yaml` de `infra/local/*` continua sendo um compose válido e completo,
subível sozinho — que é literalmente o que as specs exigem. O arquivo de raiz não redefine nada:
ele referencia.

**Pré-requisito — resolvido em 2026-08-10:** `include:` exige Docker Compose v2.20+. O ambiente
tem **v5.3.1** (`docker compose version`). Desenho confirmado; a alternativa do script
orquestrador está descartada.

**Forma decidida (eram Q1 e Q2):**

```
compose.yaml (raiz)          ← ponto de entrada único, PURAMENTE composição
  include:
    ├── infra/local/postgres/    ─┐
    ├── infra/local/floci/        │ tudo por padrão: um comando entrega
    ├── infra/local/kafka/        │ o fluxo ponta a ponta (command → SNS →
    ├── infra/local/redis/       ─┘ SQS → Kafka → eventos-consumer)
    └── apps/docker-compose.yml  ← PERMANECE como arquivo próprio
```

- **Tudo por padrão** (Q1): ~11 contêineres. O caminho leve não foi escolhido porque 3 das 5 apps
  dependem de Kafka/Floci/Valkey — subir só Postgres + apps as faria falhar, que é a pior forma
  de "ambiente mínimo".
- **`apps/docker-compose.yml` permanece** (Q2), referenciado pelo `include:`. Mantém a simetria
  com `infra/local/*` — cada peça é um compose completo — e preserva a subida de "só as apps"
  contra uma infra que já esteja no ar. O arquivo de raiz não declara serviço nenhum.

### D2 — Fonte única do PostgreSQL

**Decisão:** `infra/local/postgres/` é a fonte; `apps/docker-compose.yml` deixa de definir o
serviço `postgres`.

**Racional:** a divergência entre as duas definições não é cosmética — a versão de `apps/` não
monta `./migrations:/docker-entrypoint-initdb.d`, e portanto **cria um banco sem schema**. A
versão de `infra/` é a que tem o mecanismo completo de inicialização, e mora junto do Dockerfile
e dos scripts de migração. É a fonte natural.

O que a versão de `apps/` tem de melhor — healthcheck, e uso de `${DB_NAME}`/`${DB_USER_NAME}` em
vez de valores fixos — **migra para a fonte**, não se perde. Consolidar não é escolher um lado:
é escolher o melhor de cada.

### D3 — `shared_preload_libraries` numa diretiva só

O `apps/docker-compose.yml` faz:

```yaml
command: [ "postgres",
  "-c", "shared_preload_libraries=pg_partman_bgw",
  "-c", "shared_preload_libraries=pg_cron",       # ← mesma GUC, segunda vez
]
```

**Decisão:** uma diretiva com lista — `-c shared_preload_libraries=pg_partman_bgw,pg_cron`.

**Racional:** repetir a mesma GUC na linha de comando do PostgreSQL não acumula: o último valor
prevalece. Se for esse o comportamento aqui, `pg_partman_bgw` nunca carregou por este caminho, e
o background worker que mantém as partições esteve fora do ar em silêncio — sem erro no log,
porque não há erro: a configuração é válida, só não é a pretendida.

**Este é o item que precisa de evidência antes de qualquer conclusão.** A tarefa 2.2 confirma por
`SHOW shared_preload_libraries` no banco vigente. Se `pg_partman_bgw` aparecer, a hipótese cai e
sobra só a limpeza de estilo — o que também é um resultado válido.

### D4 — Redes

**Decisão:** com o `include:`, os serviços passam a compartilhar o projeto Compose de raiz, e as
declarações `external: true` deixam de ser necessárias para o caminho unificado.

**Ressalva:** cada compose de `infra/local/*` SHALL continuar declarando sua própria rede, para
seguir subindo isolado. O que sai é a dependência de `apps/` em redes que ele não cria.

**`floci-aws-local_default` é resíduo** — analisado em 2026-08-10 (era Q3). O
`infra/local/floci/compose.yaml` **não declara rede nenhuma**, então usa a rede default do
projeto, cujo nome deriva do diretório: `floci` → `floci_default`. O `apps/docker-compose.yml`
aponta para `floci-aws-local_default`, nome de quando a pasta se chamava `floci-aws-local/`, antes
da reorganização `apps/`+`infra/`.

Análise estática — o daemon Docker não estava no ar para confirmar em execução. A tarefa 2.5
verifica com `docker network ls`. Se estiver certo, é mais uma evidência a favor de D1: a rede
externa nomeada à mão é frágil justamente porque nada valida o nome.

### D5 — `.env` único

Três arquivos hoje: `.env` (raiz), `apps/.env`, `infra/local/postgres/.env`. Todos aparentemente
para a mesma `DB_PASSWORD`.

**Decisão:** um `.env` na raiz, com `.env.example` versionado; os composes de `infra/local/*`
leem dele por caminho relativo.

**Verificar antes:** se os três divergem em conteúdo hoje, a consolidação muda o comportamento de
alguém. Tarefa 1.2 compara os três antes de unificar.

## Riscos

| Risco | Probabilidade | Mitigação |
|---|---|---|
| ~~`include:` indisponível~~ | **descartado** | Compose v5.3.1 confirmado em 2026-08-10 |
| RAM insuficiente para ~11 contêineres | Média | Q1 escolheu "tudo por padrão"; a subida isolada por ambiente continua disponível e documentada |
| Consolidar `.env` quebra ambiente de alguém | Média | D5/1.2: comparar os três antes |
| Remover `postgres` de `apps/` quebra quem usa só aquele compose | Alta | Tarefa 5.1: `README` da raiz atualizado; e o caminho unificado passa a ser o documentado |
| Volume `pg_data` recriado, banco local perdido | Média | Tarefa 4.4: nomear o volume explicitamente e avisar antes; ambiente local, mas perder o banco custa uma recarga |
| Isolamento dos ambientes quebra sem ninguém notar | Média | Tarefa 5.2 testa cada um subindo sozinho — é requisito de spec, não conveniência |

## Questões em aberto

- ~~**Q1:** Kafka e Floci no "sobe tudo" padrão?~~ **Respondida em 2026-08-10: tudo por padrão**
  — ver D1. A subida isolada por ambiente continua disponível para quem precisar do caminho leve.
- ~~**Q2:** `apps/docker-compose.yml` permanece ou migra?~~ **Respondida: permanece**, referenciado
  pelo `include:` — ver D1.
- ~~**Q3:** `floci-aws-local_default` é intencional ou resíduo?~~ **Resíduo**, por análise estática
  — ver D4. Confirmação em execução na tarefa 2.5.

Nenhuma questão em aberto. O que resta é verificação em execução (fase 2), que depende do daemon
Docker no ar.
