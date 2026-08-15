## 1. Reorganização de pastas de topo

- [x] 1.1 `git mv aplicacoes code` preservando o histórico das duas aplicações
- [x] 1.2 Rodar `mvn clean test` em `code/contratocommand` e `code/contratoquery` para confirmar que o move não quebrou nada
- [x] 1.3 Buscar globalmente por `aplicacoes/` no repositório (README raiz, READMEs das apps, CLAUDE.md/AGENTS.md, docs, `.vscode`) e listar as referências a atualizar
      Resultado: `README.md` (raiz), `.claude/skills/create-based-aplication-java/SKILL.md`, e 4 specs ativas em `openspec/specs/` (`readme-raiz`, `higiene-codigo-morto`, `documentacao-contratoquery`, `higiene-comentarios-codigo`) — ver tasks 6.5/6.6. Arquivos sob `openspec/changes/archive/**` e desta própria proposta NÃO são tocados (registro histórico). `.vscode/`, `code/**` e `docs/**` já estão limpos.

## 2. Esqueleto de infra/

- [x] 2.1 Criar `infra/` com subpastas `modules/`, `envs/local/`, `envs/prod/`, `bootstrap/` e `local/`
- [x] 2.2 Adicionar `infra/README.md` descrevendo a topologia-alvo (Terraform, módulos, ambientes, state remoto) e o que é/não é escopo desta fase
- [x] 2.3 Adicionar READMEs/placeholders em `modules/` (networking, rds-postgres, ecs-cluster, ecs-service, observability) e em `envs/{local,prod}` e `bootstrap/` descrevendo o propósito de cada um
- [x] 2.4 Garantir que nenhum `.tf` funcional ou credencial AWS é adicionado (só documentação/placeholders)

## 3. Migração da infraestrutura local de banco

- [x] 3.1 Mover o Dockerfile do Postgres (partman/cron) de `docs/run_postgres16_ja_com_cron_partman/` para `infra/local/postgres/`
- [x] 3.2 Mover/ajustar o `postgres-db-v16.yml` (compose do banco) junto, atualizando caminhos internos se necessário
- [x] 3.3 Deixar uma referência em `docs/run_postgres16_ja_com_cron_partman/` apontando para o novo local `infra/local/postgres/` (não remover)
- [x] 3.4 Validar que o banco local ainda sobe a partir do novo caminho
      Resultado: `docker compose build` a partir de `infra/local/postgres/` teve sucesso; teste isolado (`docker run` com nome/porta temporários, sem afetar o container de dev existente `postgres16-partman-cron-kiq`) confirmou `pg_partman` e `pg_cron` inicializando e "database system is ready to accept connections". Ficaram órfãos inofensivos `postgres_pg_data` (volume vazio) e `postgres_default` (rede) do teste — não removidos por precaução (bloqueio do classificador de auto mode); não afetam nada em uso.

## 4. Configuração Spring por profiles (por aplicação)

- [x] 4.1 Em `contratocommand`: extrair o comum para `application.yml` (base) e criar `application-local.yml` + `application-prod.yml`
- [x] 4.2 Em `contratocommand`: remover `spring.profiles.active: dev` e fixar `spring.profiles.default: local` no `application.yml` base
- [x] 4.3 Em `contratoquery`: repetir a extração base + `local` + `prod` e remover o profile `dev` fixo
- [x] 4.4 Confirmar que configs comuns (datasource `${…}`, actuator, JPA) ficam só na base, sem duplicação nos profiles
- [x] 4.5 Rodar cada app com `SPRING_PROFILES_ACTIVE=local` e depois `=prod` para validar a resolução de profile
      Resultado: validado nos 4 casos (command/query × local/prod) via `mvn spring-boot:run` contra o Postgres de dev — log confirma "The following 1 profile is active" e `/actuator/health` reflete `show-details` correto por perfil (always em local, never em prod). Decisões tomadas durante a implementação (fora do texto original das tasks, documentadas para revisão): (1) datasource url passou a usar `${DB_HOST:localhost}:${DB_PORT:5432}` em vez do host fixo `localhost:5432` — default idêntico ao comportamento anterior, necessário para o app alcançar o Postgres por nome de serviço no futuro `docker-compose.yml` (task 5.6); (2) `management.endpoint.health.show-details` virou o diferencial real entre perfis (`always` em local, `never` em prod) — antes era `always` fixo em ambas; endurece produção sem afetar nenhum comportamento hoje em uso (nada roda em prod ainda).

## 5. Dockerfile por aplicação (Fargate-ready)

- [x] 5.1 Criar `code/contratocommand/Dockerfile` multi-stage (build Maven+JDK 25 → runtime JRE 25 enxuto), usuário não-root, `EXPOSE 8080`
      Multi-stage `maven:3.9-eclipse-temurin-25` (build, `-DskipTests` — testes já são gate separado via `mvn test`) → `eclipse-temurin:25-jre-alpine` (runtime), usuário `app` não-root, `HEALTHCHECK` via `/actuator/health`. `.dockerignore` adicionado.
- [x] 5.2 Criar `code/contratoquery/Dockerfile` análogo com `EXPOSE 8081`
- [x] 5.3 Adicionar `server.shutdown=graceful` na config base de cada app (encerramento gracioso para SIGTERM no Fargate)
- [x] 5.4 Build + run de cada imagem localmente apontando para o Postgres local; validar `/actuator/health` = 200 (UP)
      Build OK nas duas imagens; `docker run` isolado (nome/porta temporários) contra o Postgres de dev via `host.docker.internal` — `status: UP`, `HEALTHCHECK: healthy`, processo rodando como `app` (não-root). Containers e imagens de teste removidos por nome explícito ao final.
- [x] 5.5 Confirmar que a imagem lê `SPRING_PROFILES_ACTIVE`, `DB_NAME`, `DB_USER_NAME`, `DB_PASSWORD` do ambiente e loga em stdout
      Validado junto da 5.4: env vars injetadas via `docker run -e` refletidas no boot da app; logs visíveis via `docker logs` (stdout).
- [x] 5.6 Criar `code/docker-compose.yml` mínimo com as 2 apps + Postgres local, sem Floci
      Stack completa validada de ponta a ponta sob projeto isolado `reorg-validate` (sem publicar a porta 5432, para não colidir com o container de dev existente): Postgres subiu `Healthy`, as duas apps iniciaram só depois (via `depends_on: condition: service_healthy`) e alcançaram o banco pelo nome de serviço `postgres` (DNS interno do compose) — `/actuator/health` = UP nas duas. Stack de validação (containers, rede, volume, imagens) removida ao final por nome de projeto explícito; container de dev do usuário (`postgres16-partman-cron-kiq`) permaneceu intacto.

## 6. Documentação

- [x] 6.1 Atualizar `README.md` raiz: árvore de diretórios para `code/` + `infra/` e comandos de build/execução com os novos caminhos
      Adicionada seção "Docker Compose (recomendado)" com `code/docker-compose.yml`.
- [x] 6.2 Atualizar READMEs das aplicações e CLAUDE.md/AGENTS.md com caminhos e instruções de execução via Docker/profiles
      Edits pontuais (caminho do Postgres, Dockerfile próprio, profiles local/prod) — não reescrevi as seções de arquitetura desatualizadas dos READMEs (Strategy Pattern/orquestradores que já não existem no código): pré-existente, fora do escopo desta reorganização.
- [x] 6.3 Documentar que produção DEVE setar `SPRING_PROFILES_ACTIVE=prod` explicitamente (default `local` é só conveniência de dev)
- [x] 6.4 Atualizar as referências ao caminho do Postgres local (`infra/local/postgres/`)
- [x] 6.5 Atualizar `.claude/skills/create-based-aplication-java/SKILL.md` (descrição, referência viva, comandos) para `code/`
      Também atualizado o template `application.yaml` gerado pelo skill (`profiles.active` → `profiles.default`), para novas apps já nascerem na convenção local/prod estabelecida por esta mudança.
- [x] 6.6 Corrigir caminhos `aplicacoes/` nas specs ativas de `openspec/specs/`: `readme-raiz`, `higiene-codigo-morto`, `documentacao-contratoquery`, `higiene-comentarios-codigo` (correção de referência, não muda comportamento)
      Verificação final: nenhuma referência viva a `aplicacoes/` resta no repositório — os únicos 28 hits restantes são a própria proposta desta mudança (menção proposital ao "antes") e arquivos sob `openspec/changes/archive/**` (registro histórico, não tocado).

## 7. Verificação final

- [x] 7.1 `mvn clean test` verde nas duas aplicações
      94 testes (command) + 40 testes (query), 0 falhas, BUILD SUCCESS nas duas.
- [x] 7.2 Contratos REST e portas (8080/8081) inalterados (smoke test manual dos endpoints principais)
      Fluxo completo validado: `POST /api/autorizacoes` (201) na command → `GET /api/autorizacoes/{id}` (200) e `GET /api/autorizacoes?idUnicoContaContratante=...` (200) na query, confirmando que as duas apps continuam compartilhando o mesmo banco após a reorganização.
- [x] 7.3 Nenhuma referência remanescente a `aplicacoes/` no repositório
      Único hit fora do esperado (proposta desta mudança + `openspec/changes/archive/**`) foi `.idea/workspace.xml` — coberto por `.gitignore`, estado local de IDE não rastreado.
- [x] 7.4 `openspec validate reorganizar-monorepo-code-infra` sem erros
      `Change 'reorganizar-monorepo-code-infra' is valid`.
