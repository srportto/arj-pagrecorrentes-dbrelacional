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

## Extensões

Este ambiente tem dois propósitos simultâneos: servir as aplicações que leem/escrevem
`autorizacoes` (`apps/contratocommand`, `apps/contratoquery`) **e** demonstrar a construção de
um PostgreSQL com extensões auxiliares — pacote PGDG e compilação da fonte. É por isso que a
imagem é **construída** a partir do `dockerfile` deste diretório em vez de consumir a imagem
oficial pronta: sem essa capacidade, não haveria onde demonstrar a receita.

A receita de somar uma extensão nova vive em **três arquivos diferentes**, e nenhum deles se
refere aos outros — ler qualquer um isolado não revela o procedimento completo:

| Etapa | Arquivo | O que fazer |
|---|---|---|
| 1. instalar | `dockerfile` | pacote PGDG via `apt` ou compilação da fonte |
| 2. declarar (quando exigido) | `postgres-db-v18.yml` | acrescentar a biblioteca em `shared_preload_libraries` |
| 3. criar | `migrations/vX.Y.Z...sql` | `CREATE EXTENSION IF NOT EXISTS <nome>;` |

### 1. Instalar na imagem

O `dockerfile` exercita os dois caminhos de instalação que o projeto se propõe a demonstrar:

- **Pacote pré-compilado do repositório PGDG** (já configurado na imagem oficial `postgres:18`),
  instalado via `apt-get install`. Exemplo real: `postgresql-18-partman` e `postgresql-18-cron`
  (`pg_partman` e `pg_cron`). Use este caminho sempre que houver pacote para a versão do
  PostgreSQL em uso — é o caminho mais barato e mais fácil de manter atualizado.
- **Compilação a partir do código-fonte**, para quando não há pacote PGDG disponível para a
  versão do PostgreSQL em uso. Exemplo real: `pgvector` — `git clone` + `make` + `make install`.
  Este caminho exige instalar as dependências de build (`postgresql-server-dev-18`,
  `build-essential`, `git`) e **removê-las ao final do mesmo estágio**
  (`apt-get purge -y --auto-remove ...`), para que não sobrevivam na imagem de runtime.

#### Reprodutibilidade: o que é fixado e o que continua flutuando

`git clone --depth 1` sem `--branch` traz o último commit do branch padrão **no instante do
build** — dois builds em datas diferentes produzem versões diferentes da extensão, sem que nada
registre qual foi usada. Por isso o clone do `pgvector` fixa uma tag de release explícita
(`--branch v0.8.6`, hoje), e o `LABEL` da imagem (`pgvector.version`) declara essa versão sem
exigir inspecionar o banco.

**Atualizar a versão fixada** é decisão consciente, não efeito colateral de reconstruir: troque a
tag em `--branch` no `dockerfile` e reconstrua sem cache (`docker compose build --no-cache
postgres`). Reconstruir **sem** trocar a tag não muda a versão — `--depth 1` com `--branch`
sempre resolve para o mesmo commit da tag.

Isto **não** torna a imagem inteira reprodutível — só a versão do `pgvector`, que é a única
origem compilada de um commit arbitrário. As outras duas continuam flutuando, deliberadamente:

- **Imagem base `postgres:18`** — flutua dentro da major de propósito, para receber correção de
  segurança sem que este repositório precise acompanhar releases do PostgreSQL.
- **Pacotes PGDG** (`postgresql-18-partman`, `postgresql-18-cron`) — não são commits arbitrários;
  são versões curadas pelo repositório da distribuição para a major em uso.

### 2. Declarar em `shared_preload_libraries` (quando exigido)

Nem toda extensão precisa ser carregada na inicialização do servidor. O critério é a
**natureza** da extensão, não uma lista fixa — para que somar uma extensão nova não vire
tentativa e erro:

- **Exige preload e reinício**: a extensão registra um *background worker* (ex.: `pg_partman_bgw`)
  ou se engancha em algum ponto de extensão do servidor (ex.: `pg_cron`, que precisa interceptar o
  agendador). Sem preload, `CREATE EXTENSION` até funciona, mas a parte que depende do processo
  já estar no ar (o worker, o hook) nunca roda.
- **Basta `CREATE EXTENSION`**: a extensão só acrescenta tipos, funções, operadores ou índices —
  não precisa que nada seja carregado antes do banco aceitar conexões. Exemplo real: `pgvector`
  (tipo `vector` e operadores de distância), que está criado no banco e **não** está em
  `shared_preload_libraries`.

Classificação das três extensões hoje presentes, como ilustração do critério (não como sua
definição):

| Extensão | Biblioteca | Background worker / hook? | Preload? |
|---|---|---|---|
| `pg_partman` | `pg_partman_bgw` | sim (worker de manutenção de partição) | sim |
| `pg_cron` | `pg_cron` | sim (agendador) | sim |
| `vector` (pgvector) | — | não | não |

Passo prático: antes de tentar, verifique a documentação da extensão nova em busca de
"background worker" ou "shared_preload_libraries" — se ela mencionar qualquer um dos dois, vá
direto para o `postgres-db-v18.yml`; senão, pule esta etapa.

Quando a etapa for necessária, acrescente a biblioteca na **mesma** diretiva `-c` já existente
(`shared_preload_libraries=pg_partman_bgw,pg_cron`), nunca em um `-c` separado: a mesma GUC
passada em duas diretivas `-c` não acumula, o último valor prevalece e a primeira é descartada
sem erro, sem aviso e sem entrada de log (armadilha já especificada e resolvida em
`orquestracao-local-unificada`, D3 da change `unificar-orquestracao-docker-local` — ver o
comentário acima da diretiva em `postgres-db-v18.yml`).

### 3. Criar a extensão no banco

Na migration apropriada (ex.: `v1.0.0.-create-database-and-tables.sql`, onde as três extensões
atuais são criadas):

```sql
CREATE EXTENSION IF NOT EXISTS <nome_da_extensao>;
```

As migrations só rodam automaticamente na **primeira** subida do banco com volume vazio (ver
"Subir" acima). Para acrescentar uma extensão a um banco que já existe, rode o `CREATE EXTENSION`
manualmente ou recrie o volume.

### Verificar cada etapa

Uma extensão tem **dois estados independentes**: biblioteca carregada no processo (etapa 2) e
extensão criada no banco (etapa 3). Uma pode estar presente sem a outra — `pgvector` está criado
e nunca esteve no preload; o inverso também é possível (biblioteca no preload, `CREATE EXTENSION`
nunca executado). Diagnosticar "a extensão não funciona" sem separar os dois estados leva a
investigar o arquivo errado — mexer no compose quando o problema está na migration, ou
vice-versa.

Bibliotecas carregadas no processo (ver também "Validar que está no ar" acima):

```bash
docker exec postgres18-kiq psql -U docker -d db-csp-postgres -c "SHOW shared_preload_libraries;"
# pg_partman_bgw,pg_cron
```

Extensões criadas no banco, com versão:

```bash
docker exec postgres18-kiq psql -U docker -d db-csp-postgres -c "SELECT extname, extversion FROM pg_extension ORDER BY extname;"
#  extname   | extversion
# ------------+------------
#  pg_cron    | 1.6
#  pg_partman | 5.5.0
#  plpgsql    | 1.0
#  vector     | 0.8.6
```

Note que `vector` aparece nesta lista mesmo **fora** do preload — confirma que os dois estados
são independentes.

### Extensão sem consumidor é deliberada, não dívida técnica

`pg_partman` está instalado, com *background worker* rodando, e **nenhum código do monorepo o
usa** — o ring buffer de expurgo é gerido por fórmula em `ControleExpurgoAutorizacao`, não por
partman. `pgvector` é compilado da fonte, criado no banco, e também não tem consumidor. Isso é
**intencional**, não sobra de refactor: o segundo propósito deste ambiente (demonstrar a
construção de um PostgreSQL com extensões auxiliares, pelos dois caminhos de instalação) é
satisfeito pela presença da extensão, independentemente de haver consumidor.

Se uma auditoria de código morto, de dependências ou de higiene de configuração encontrar
`pg_partman` ou `pgvector` sem chamador: **isso não é defeito**. Não remova o `dockerfile` nem a
entrada em `shared_preload_libraries` apenas por ausência de uso. A única justificativa válida
para remover é a decisão explícita de que a demonstração daquela extensão deixou de ser desejada
— não a ausência de consumidor.

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
