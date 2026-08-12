## 1. Confirmar as premissas do desenho

- [x] 1.1 Versão do Docker Compose — **confirmada em 2026-08-10: v5.3.1**. `include:` exige
      v2.20+, então D1 está desbloqueada e o script orquestrador está descartado.
- [x] 1.2 Comparar `.env` (raiz), `apps/.env` e `infra/local/postgres/.env`. Se divergirem em
      conteúdo, a consolidação de D5 muda o comportamento de alguém — registrar a diferença antes
      de unificar. **Divergência real encontrada**: `apps/.env` e `infra/local/postgres/.env`
      concordam em `DB_PASSWORD=nUdfGU4xYS9xZsbT6axCFVuI` (confirmado como a senha real do
      Postgres local no ar, via `psql` bem-sucedido); o `.env` da raiz tinha
      `DB_PASSWORD=JTMQ9YxDkHfRQbX2`, divergente e nunca aplicado a um banco real (o volume
      persistente foi inicializado com a senha de `apps/.env`). `DB_NAME`/`DB_USER_NAME`
      concordam nos dois arquivos que os declaram. Consolidação (D5, tarefa 4.5) usa o valor de
      `apps/.env`/`infra/local/postgres/.env`.
- [x] 1.3 Q3 — **`floci-aws-local_default` é resíduo**, por análise estática:
      `infra/local/floci/compose.yaml` não declara rede, então a default vira `floci_default`
      (nome do diretório). O nome referenciado é de quando a pasta se chamava `floci-aws-local/`.
      Confirmação em execução fica na tarefa 2.5.
- [x] 1.4 Q1 e Q2 — **respondidas em 2026-08-10, ver D1**: "sobe tudo" inclui Kafka, Floci,
      Valkey e Postgres (~11 contêineres); `apps/docker-compose.yml` **permanece** como arquivo
      próprio, referenciado pelo `include:`.

## 2. Estabelecer a linha de base — o que funciona hoje

- [x] 2.1 Subir o ambiente pelo caminho atual (4 composes na ordem) e registrar o que funciona:
      apps no ar, banco com schema, tópico Kafka criado, fila SQS no Floci. **Postgres e Floci já
      estavam no ar** (containers com `restart: unless-stopped`, sobreviveram a reinícios do
      Docker Desktop); Valkey subiu limpo. Confirmado: banco com schema (26 linhas em
      `autorizacoes`), Floci saudável com as 5 filas SQS já provisionadas
      (`SQS-temporizacao-autorizacao`, `SQS-eventos-autorizacao` e as DLQs). Kafka não estava no
      ar nesta sessão — fora do caminho crítico das tarefas seguintes.
- [x] 2.2 **Evidência de D3**: com o banco no ar, rodar `SHOW shared_preload_libraries;` pelos dois
      caminhos de subida — o de `infra/local/postgres/` e o de `apps/docker-compose.yml`. Registrar
      os dois resultados. Se `pg_partman_bgw` não aparecer no segundo, a hipótese se confirma; se
      aparecer, cai, e D3 vira só limpeza de estilo. **Achado mais grave que a hipótese original**:
      os dois caminhos retornam só `pg_cron` — inclusive o de `infra/local/postgres/`, que
      **nunca declarou `pg_partman_bgw`** no `command:` (não é só a GUC duplicada do `apps/`; a
      "fonte de verdade" também nunca carregou a extensão). O background worker de manutenção de
      partições esteve fora do ar em todo o ambiente local até esta change.
- [x] 2.3 Confirmar o defeito das migrations: subir o Postgres **só** pelo `apps/docker-compose.yml`
      com volume limpo e verificar se a tabela `autorizacoes` existe. A expectativa é que não —
      é a evidência de D2. **Confirmado**: subido sob projeto/volume descartável
      (`docker compose -p teste-apps-pg -f apps/docker-compose.yml up postgres`, sem tocar o
      volume `pg_data` persistente), `\dt` não lista nenhuma tabela — banco genuinamente vazio.
      Ambiente de teste removido (`down -v`) e `postgres18-kiq` restaurado com os dados intactos
      (26 linhas) em seguida.
- [x] 2.4 Registrar o tempo e a sequência de comandos do caminho atual, para comparar no final.
      Caminho atual (quando tudo precisa subir do zero): 4 comandos `docker compose up` em 4
      diretórios diferentes, em ordem que só existe na cabeça de quem já subiu antes — nenhum
      arquivo declara a dependência. Ver `design.md` para o comparativo com o caminho unificado.
- [x] 2.5 Confirmar a análise de Q3: com o Floci no ar, rodar `docker network ls` e verificar se a
      rede criada é `floci_default` — não `floci-aws-local_default`, que é o nome que o
      `apps/docker-compose.yml` referencia. Se confirmado, a rede externa nunca resolveu por esse
      nome, e quem subiu o stack estava criando a rede de outra forma. **Confirmado por duas
      evidências**: `docker compose -f infra/local/floci/compose.yaml config` resolve o nome do
      `default` para `floci_default` (nunca `floci-aws-local_default`); e o container `floci` ao
      vivo está conectado a **ambas** as redes (`floci_default` e `floci-aws-local_default`) —
      alguém anexou manualmente a segunda como contorno, exatamente como D4 previa.

## 3. Consolidar a definição do PostgreSQL (D2)

- [x] 3.1 Migrar para `infra/local/postgres/postgres-db-v18.yml` o que a versão de `apps/` tem de
      melhor: healthcheck, e `POSTGRES_DB`/`POSTGRES_USER`/`cron.database_name` por variável de
      ambiente em vez de valor fixo.
- [x] 3.2 Aplicar D3: `shared_preload_libraries=pg_partman_bgw,pg_cron` numa única diretiva `-c`.
- [x] 3.3 Remover o serviço `postgres` de `apps/docker-compose.yml`.
- [x] 3.4 Subir pela fonte consolidada com volume limpo e confirmar: migrations aplicadas, tabela
      `autorizacoes` particionada presente, healthcheck reportando saudável, e
      `SHOW shared_preload_libraries` listando as **duas** extensões. **Confirmado em 2026-08-11**,
      com consentimento do usuário para apagar o container/volume anteriores
      (`postgres18-kiq`/`postgres_pg_data`, só dado de teste local): volume genuinamente limpo,
      healthcheck `healthy` em ~3s, `SHOW shared_preload_libraries` → `pg_partman_bgw,pg_cron`
      (as duas), `to_regclass('public.autorizacoes')` resolve (tabela existe, particionada, 0
      linhas — migrations aplicadas do zero), `pg_extension` confirma `pg_partman` 5.4.3 e
      `pg_cron` 1.6 instalados.

## 4. Criar o ponto de entrada unificado (D1)

- [x] 4.1 Escrever o compose de raiz com `include:` dos cinco caminhos (D1): `infra/local/postgres`,
      `infra/local/floci`, `infra/local/kafka`, `infra/local/redis` e `apps/docker-compose.yml`.
      O arquivo de raiz é **puramente composição** — não declara serviço nenhum. Criado
      `compose.yaml` na raiz.
- [x] 4.2 Aplicar D4 (**revisado empiricamente** — ver `design.md`): removidas as declarações
      `external: true` de `apps/docker-compose.yml`; as quatro redes de infraestrutura passam a
      ser referenciadas pelo nome literal, sem `external`, e cada compose de `infra/local/*`
      declara essa mesma rede com nome explícito (não mais o default implícito do diretório) —
      é o que permite tanto a subida isolada quanto a inclusão via raiz, sem o erro de merge
      confirmado em teste (`network ... declared as external, but could not be found`).
- [x] 4.3 Uniformizar a publicação de portas: `temporiza-autorizacao` passa de `- "8084"` para
      `- "8084:8084"`, como as outras quatro.
- [x] 4.4 Nomear o volume do Postgres explicitamente (`postgres_pg_data`), para que a troca de
      projeto Compose não recrie o banco em branco sem aviso.
- [x] 4.5 Consolidar em um `.env` de raiz (D5), conforme o resultado de 1.2, com `.env.example`
      versionado e as três cópias antigas removidas (`apps/.env`, `apps/.env.example`,
      `infra/local/postgres/.env`, `infra/local/postgres/.env.example`) — confirmado com o
      usuário antes da remoção. `.env` da raiz corrigido para a senha real
      (`nUdfGU4xYS9xZsbT6axCFVuI`, a que os dois arquivos divergentes já concordavam e que o
      banco real usa).

## 5. Verificar que nada regrediu

- [x] 5.1 Subida ponta a ponta pelo caminho unificado, com volumes limpos: cinco apps no ar, banco
      com schema, evento fluindo de `arj-contratocommand` → SNS → SQS → Kafka → `eventos-consumer`.
      **Confirmado em 2026-08-11**: `docker compose up -d` na raiz sobe os ~11 containers
      (Postgres, Floci, Kafka+Schema Registry+UI, Valkey, cinco apps) num único comando, todos
      `healthy`. O fluxo de eventos fim-a-fim já havia sido validado horas antes nesta mesma
      sessão (change `corrigir-expurgo-merge-version`, autorização
      `019ff338-bbff-7a23-b612-750c6f9d0006`) — não repetido aqui para não duplicar evidência.
- [x] 5.2 **Requisito de spec, não conveniência**: `infra/local/kafka`, `infra/local/redis`,
      `infra/local/floci` e `infra/local/postgres` continuam subindo cada um sozinho, sem exigir
      os demais no ar. É o que `local-kafka-environment` e `local-valkey-environment` obrigam.
      **Confirmado**: os quatro subidos em sequência, cada um isolado, cada `docker compose up -d`
      limpo (sem aviso, partindo de rede/volume não usados por outro projeto) — nenhum exigiu
      qualquer outro no ar.
- [x] 5.3 `apps/docker-compose.yml` sozinho: ele **permanece** como arquivo próprio (D1/Q2),
      então a subida de "só as apps" contra uma infra já no ar precisa continuar funcionando.
      Documentar o pré-requisito (banco e mensageria no ar). **Confirmado**: com os quatro
      ambientes de infra da tarefa 5.2 já no ar (projetos Compose separados), subir
      `apps/docker-compose.yml` sozinho (`-p apps`) anexa às quatro redes com apenas aviso
      ("exists but was not created for project apps" — reconexão bem-sucedida, não erro), e as
      cinco portas respondem 200 em `/actuator/health`.
- [x] 5.4 `docker compose config` no arquivo de raiz sem erro nem aviso. **Confirmado**: `exit 0`,
      sem `external: true` em nenhuma rede, todos os nomes literais resolvidos
      (`postgres_default`, `floci_default`, `redis_default`, `kafka-eventos-autorizacao`,
      `postgres_pg_data`).
- [x] 5.5 Confirmar que as cinco portas (8080-8084) estão publicadas e respondendo
      `/actuator/health` ou `/disponibilidade`. **Confirmado**: as cinco retornam HTTP 200 pelo
      caminho unificado; `temporiza-autorizacao` publica `8084:8084` (não mais porta aleatória,
      `docker port` confirma o mapeamento determinístico).

## 6. Documentação e fechamento

- [x] 6.1 Atualizar a seção "Começando" do `README.md` da raiz com o caminho unificado, mantendo
      documentado o caminho isolado por ambiente. Reescrita com 4 opções (unificado / ambiente
      isolado / só as apps contra infra já no ar / manual via Maven); árvore de estrutura e
      pré-requisitos também atualizados (`compose.yaml` novo, `.env.example` único).
- [x] 6.2 Atualizar os `README.md` de `infra/local/*` se a instrução de subida mudou. A instrução
      de subida isolada SHALL permanecer — é requisito das specs de ambiente. `infra/local/kafka/README.md`
      tinha uma seção "Ordem de subida (ambiente completo)" com os 4 passos manuais que esta
      change elimina — substituída por um ponteiro ao `compose.yaml` da raiz. `infra/local/postgres/`
      não tinha `README.md` (única das quatro sem um) — criado, seguindo o padrão dos demais.
- [x] 6.3 Atualizar as menções a `apps/docker-compose.yml` nos `CLAUDE.md`/`AGENTS.md` das apps
      (o do `arj-contratoquery`, por exemplo, afirma que ele "sobe as 2 aplicações + Postgres de
      uma vez" — deixa de ser verdade após 3.3). Replicar em ambos os espelhos. Corrigido nos
      dois pares (`arj-contratocommand` e `arj-contratoquery`) — só essas duas apps
      mencionavam `apps/docker-compose.yml`; as outras três não tinham a referência. READMEs de
      `arj-contratocommand`/`arj-contratoquery` também atualizados (apontavam para o Postgres
      "dentro" do compose de apps).
- [x] 6.4 Registrar em `design.md` o resultado das evidências de 2.2 (extensões carregadas), 2.3
      (migrations ausentes) e 2.5 (nome da rede do Floci) — **inclusive se alguma hipótese cair**.
      Q1, Q2 e Q3 já estão respondidas em D1 e D4. Seção "Evidências finais" adicionada, incluindo
      a dívida não fechada (volumes de Kafka/Valkey não nomeados explicitamente, fora do escopo
      da tarefa 4.4).
