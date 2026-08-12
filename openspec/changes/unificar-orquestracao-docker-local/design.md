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

**Decisão original (2026-08-09):** com o `include:`, os serviços passam a compartilhar o projeto
Compose de raiz, e as declarações `external: true` deixam de ser necessárias para o caminho
unificado.

**Revisão empírica (2026-08-11, execução da tarefa 4.x):** a frase acima estava incompleta. Testado
diretamente com Compose v5.3.1 (dois arquivos de teste, um serviço `pg` num deles declarando uma
rede nomeada **sem** `external`, outro serviço `app` no segundo arquivo referenciando a mesma rede
**com** `external: true`, ambos trazidos por um `include:` de raiz):

```
$ docker compose -f root.yaml up -d
network pg_net_explicit declared as external, but could not be found
```

`include:` funde os dois arquivos no mesmo modelo — a entrada de rede resultante carrega **os dois**
atributos (`name: pg_net_explicit` e `external: true`), e a presença de `external: true` em
qualquer um dos lados faz o Compose tratar a rede inteira como externa: ele deixa de criá-la, e a
subida falha se ela não existir de antemão. Isso valeria não só para `postgres_default`/
`floci_local`/`redis_default`, mas **também** para `kafka-eventos-autorizacao` — que já é nomeada
explicitamente nos dois lados hoje, e sofreria a mesma quebra se `apps/docker-compose.yml`
continuasse marcando-a `external: true` dentro do caminho unificado.

**Segundo teste** — os dois lados declarando a rede com o **mesmo nome literal**, **nenhum**
marcado `external`: a subida cria a rede uma única vez e os dois serviços entram nela, sem aviso,
tanto isolados quanto via `include:`.

**Terceiro teste** — o cenário de `apps/docker-compose.yml` sozinho contra infra já no ar (tarefa
5.3): dois projetos Compose **separados**, cada um declarando a mesma rede pelo nome literal, sem
`external` em nenhum: o segundo `up` emite aviso (`a network with name ... exists but was not
created for project "..."`) mas **anexa com sucesso** — falha só ocorreria se a rede já existente
não tivesse rótulos de Compose (é exatamente o defeito de `floci-aws-local_default`: criada fora
do fluxo normal do Compose moderno, por isso o Compose recusa reanexar e aborta).

**Decisão revisada:** nenhum compose declara `external: true` para as quatro redes de
infraestrutura (`postgres_default`, `floci_default`, `redis_default`,
`kafka-eventos-autorizacao`). Cada compose de `infra/local/*` declara sua rede com **nome literal
explícito** (`name:`), não mais o nome implícito derivado do diretório do projeto — isso a
mantém estável entre subir isolado e subir via `include:`. `apps/docker-compose.yml` referencia
as mesmas quatro redes pelo nome literal, também sem `external: true`. Consequência que o
`proposal.md` (seção Impact) não previa: `infra/local/floci/compose.yaml` e
`infra/local/redis/compose.yaml` **precisam** de uma rede nomeada explícita — não ficam
"inalterados no conteúdo" como a change havia estimado. `infra/local/kafka/compose.yaml` já
declara a rede correta e não muda.

**`floci-aws-local_default` é resíduo** — analisado em 2026-08-10 (era Q3), confirmado em
2026-08-11 (tarefa 2.5): `docker compose -f infra/local/floci/compose.yaml config` resolve o nome
do `default` para `floci_default`; o container `floci` ao vivo está conectado às **duas** redes
(`floci_default` e `floci-aws-local_default`) — a segunda foi anexada manualmente por fora do
Compose, como contorno do nome errado. Corrigido nesta change: `apps/docker-compose.yml` passa a
referenciar `floci_default`.

### D5 — `.env` único

Três arquivos hoje: `.env` (raiz), `apps/.env`, `infra/local/postgres/.env`. Todos aparentemente
para a mesma `DB_PASSWORD`.

**Decisão:** um `.env` na raiz, com `.env.example` versionado; os composes de `infra/local/*`
leem dele por caminho relativo.

**Verificar antes:** se os três divergem em conteúdo hoje, a consolidação muda o comportamento de
alguém. Tarefa 1.2 compara os três antes de unificar.

## Evidências finais (2026-08-11, fechamento)

- **2.2 (extensões carregadas)**: achado mais grave que a hipótese original de D3. Não era só a
  GUC duplicada do `apps/docker-compose.yml` — `infra/local/postgres/postgres-db-v18.yml`, a
  fonte que a D2 elegeu como "a que tem o mecanismo completo", **nunca declarou
  `pg_partman_bgw`** no `command:`. Os dois caminhos de subida do Postgres local carregavam só
  `pg_cron`; o background worker de manutenção de partições esteve fora do ar em todo o ambiente
  local até esta change. Corrigido na fonte consolidada: `SHOW shared_preload_libraries` agora
  retorna `pg_partman_bgw,pg_cron` (confirmado com volume limpo).
- **2.3 (migrations ausentes)**: confirmado. `apps/docker-compose.yml`, subido isolado com
  volume descartável, produzia um banco sem nenhuma tabela — `\dt` vazio.
- **2.5 (nome da rede do Floci)**: confirmado por duas evidências independentes —
  `docker compose -f infra/local/floci/compose.yaml config` resolve `floci_default`; o container
  `floci` ao vivo estava conectado às duas redes (`floci_default` **e**
  `floci-aws-local_default`), a segunda anexada manualmente por fora do Compose como contorno do
  nome errado.
- **D4, revisão empírica**: testado com Compose v5.3.1 que `external: true` fundido via
  `include:` com a definição não-external do arquivo "dono" da rede **quebra** a subida
  (`network ... declared as external, but could not be found`) — reproduzido isoladamente antes
  de tocar os arquivos reais. A correção aplicada (nenhum arquivo marca `external: true`; cada
  compose de `infra/local/*` declara a rede com nome literal explícito; `apps/docker-compose.yml`
  referencia os mesmos nomes sem `external`) foi validada nos três cenários: `include:` da raiz
  (cria uma vez, sem aviso), cada ambiente isolado (cria a própria rede, sem aviso), e
  `apps/docker-compose.yml` sozinho contra infra já no ar (reanexa com aviso informativo, não
  erro — funciona porque as redes agora carregam rótulos do Compose moderno em todos os casos).
  Consequência de escopo: `infra/local/floci/compose.yaml` e `infra/local/redis/compose.yaml`
  precisaram de uma rede nomeada explicitamente — o `proposal.md` original previa que ficariam
  inalterados.
- **Dívida não fechada por esta change**: o volume nomeado só foi fixado para o Postgres
  (tarefa 4.4, requisito explícito). `kafka_data` e `valkey_data` continuam com nome derivado do
  projeto Compose — subir `infra/local/kafka`/`infra/local/redis` isolado vs. via `compose.yaml`
  da raiz usa volumes **diferentes** (mesma classe de risco que motivou a tarefa 4.4 para o
  Postgres, só que sem dado crítico por trás — tópicos/mensagens Kafka e agendamentos Valkey são
  reconstruíveis). Registrado aqui como candidato a change futura, não como bloqueio desta.
- **Validação completa**: subida unificada (5.1) com os ~11 containers saudáveis; os quatro
  ambientes de `infra/local/*` subindo isolados em sequência, sem exigir nada além de si (5.2);
  `apps/docker-compose.yml` sozinho contra a infra isolada já no ar, as cinco portas respondendo
  200 (5.3); `docker compose config` na raiz sem erro nem aviso (5.4); as cinco portas
  publicadas de forma determinística, incluindo `temporiza-autorizacao` em `8084:8084` (5.5).

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
