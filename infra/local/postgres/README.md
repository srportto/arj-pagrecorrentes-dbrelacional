# infra/local/postgres

PostgreSQL 18 local com `pg_partman`, `pg_cron` e `pgvector`, usado pelas duas aplicações que
leem/escrevem a tabela particionada `autorizacoes` (`apps/contratocommand`,
`apps/contratoquery`). É a **fonte única** do serviço Postgres do ambiente local — nenhum
outro compose do repositório declara este serviço (ver a change `unificar-orquestracao-docker-local`).

## Subir

```bash
docker compose --env-file ../../../.env -f postgres-db-v18.yml up -d
```

Requer `DB_PASSWORD` (e opcionalmente `DB_NAME`/`DB_USER_NAME`) — copie `.env.example` da raiz
para `.env` antes da primeira subida.

Na primeira subida com volume vazio, os scripts de `migrations/` rodam em ordem alfabética
(`v1.0.0`, `v1.0.1`, ...) e criam a tabela `autorizacoes` particionada.

## Validar que está no ar

```bash
docker exec postgres18-kiq pg_isready -U docker -d db-csp-postgres
docker exec postgres18-kiq psql -U docker -d db-csp-postgres -c "SHOW shared_preload_libraries;"
# pg_partman_bgw,pg_cron
```

## Parar

```bash
docker compose --env-file ../../../.env -f postgres-db-v18.yml down
```

`down` sem `-v` preserva o volume nomeado `postgres_pg_data` — os dados sobrevivem à troca de
projeto Compose (subida isolada aqui vs. via `compose.yaml` da raiz).

## Massa sintética

Dois scripts populam a tabela `autorizacoes` com dado gerado, cada um numa faixa de partição:

- **`gerar-massa-sintetica-representativa.sql`** — partições quentes (`0..888`), para medir custo de
  planejamento/execução com volume representativo (~276 mil linhas, 80 mil contas, skew realista).
- **`gerar-massa-sintetica-expurgo.sql`** — partições de expurgo (`900..999`), para exercitar a
  reclamação do ring buffer (change `reclamar-particao-expurgo-ciclo`) sem depender da passagem real
  do tempo. O anel só completa a primeira volta e passa a ter dado nas gavetas de expurgo por volta de
  2028-04-20; até lá, este script é o único jeito de ter dado ali para testar contra Postgres real.

  Semeia a partição **alvo** (a que a rotina de reclamação miraria numa data de referência dada) e as
  duas **vizinhas**, coerentemente com a fórmula de `ControleExpurgoAutorizacao.obterParticaoExpurgoWrite`
  — cada linha recebe uma `data_hora_ultima_atlz` cuja semana, passada pela fórmula, produz de volta o
  número da partição em que ela foi inserida.

  ```bash
  # cenário normal: dado com ~98 semanas na alvo (a rotina deve esvaziar)
  docker exec -i postgres18-kiq psql -U docker -d db-csp-postgres \
    -v data_referencia=2026-08-22 -v cenario=ciclo_anterior -v qtd_linhas_por_particao=200 \
    < gerar-massa-sintetica-expurgo.sql

  # cenário de anomalia: dado recente demais na alvo (a rotina deve recusar, não expurgar)
  docker exec -i postgres18-kiq psql -U docker -d db-csp-postgres \
    -v data_referencia=2026-08-22 -v cenario=recente -v qtd_linhas_por_particao=200 \
    < gerar-massa-sintetica-expurgo.sql
  ```

  Os três parâmetros (`data_referencia`, `cenario`, `qtd_linhas_por_particao`) são obrigatórios, sem
  valor padrão. **Não inclua aspas simples no valor passado ao `-v`** — o script referencia as
  variáveis como `:'nome'`, forma do psql que já adiciona a quotação SQL sozinha; incluir aspas no
  valor produz uma string com aspas *dentro* dela, e a comparação de `cenario` deixa de bater sem
  nenhum erro visível.

  Para restaurar o banco ao estado anterior:

  ```sql
  SELECT DISTINCT id_particao_conta FROM autorizacoes WHERE id_particao_conta >= 900 ORDER BY 1;
  -- TRUNCATE autorizacoes_pe<numero>;  -- uma por vez, para as partições que a massa atingiu
  ```
