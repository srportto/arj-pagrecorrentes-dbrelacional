## Context

O monorepo hoje tem uma única pasta de aplicações (`aplicacoes/`) com dois microserviços Spring Boot 4 / Java 25 (`arj-contratocommand` :8080 escrita, `arj-contratoquery` :8081 leitura), ambos sobre um PostgreSQL 16 com `pg_partman` + `pg_cron`. Não há Dockerfile das aplicações (só do banco, em `docs/`), não há nenhuma linha de Terraform e o profile Spring está fixo em `dev`. As apps consomem apenas JDBC — nenhuma API AWS ainda.

Esta mudança é a primeira de uma sequência rumo a cloud-native (ECS+Fargate, Terraform, Floci local). Seu papel é **pavimentar a estrutura** sem alterar comportamento e sem tocar na cloud. Decisões de escopo vieram do dono do projeto: pastas em inglês, profiles `local`/`prod` (sem `dev`), um Dockerfile por app já nesta fase, esqueleto de `infra/` sem provisionamento real, e o código deve continuar funcionando exatamente como está.

## Goals / Non-Goals

**Goals:**
- Estabelecer a topologia de topo `code/` + `infra/` (nomes em inglês).
- Entregar um `Dockerfile` por aplicação, compatível com ECS/Fargate, sem otimização prematura.
- Reorganizar a config Spring em `application.yml` + `application-local.yml` + `application-prod.yml`, removendo o profile fixo `dev`.
- Mover a infraestrutura local de banco (Postgres partman/cron) para `infra/local/`.
- Deixar `infra/` com um esqueleto legível que oriente a fase seguinte de Terraform.
- Manter `mvn test` verde e os contratos REST idênticos.

**Non-Goals:**
- Escrever Terraform funcional ou provisionar qualquer recurso AWS.
- Fazer a app consumir serviços AWS (Secrets Manager, SSM, SQS, S3…).
- Introduzir Floci na prática (fica preparado, não configurado).
- Adicionar migração de schema (Flyway/Liquibase) ou pipelines de CI/CD.
- Otimizar imagem (GraalVM native, CDS) ou mudar o servidor web/portas.

## Decisions

### D1. `aplicacoes/` → `code/`, nova `infra/` no topo
Consolidar em inglês (`code/`) alinha com o vocabulário cloud-native e com o requisito. `infra/` separada deixa claro o limite entre o que é empacotado no contêiner e o que provisiona ambiente. **Alternativa considerada**: manter `aplicacoes/` e só adicionar `infra/` — rejeitada por deixar o monorepo bilíngue e inconsistente. Custo: quebra caminhos em docs e exige revisão de referências (mitigado com busca global por `aplicacoes/`).

### D2. Um Dockerfile por app, multi-stage JRE+JAR
Estágio de build com Maven + JDK 25 gera o JAR; estágio de runtime com um JRE 25 enxuto executa `java -jar`. Escolhas Fargate-ready: usuário não-root, `EXPOSE` da porta, dependência de `/actuator/health` para health check do ECS, e `server.shutdown=graceful` para SIGTERM. **Alternativa considerada**: buildpacks (`spring-boot:build-image`) ou GraalVM native — adiadas; native com JPA/MapStruct/Lombok é campo minado e buildpack esconde o Dockerfile que queremos versionar explicitamente. Começar simples e legível.

### D3. Profiles: base + `local` + `prod`, ativo via ambiente
`application.yml` guarda o que é comum (datasource com `${…}`, actuator, JPA). `application-local.yml` e `application-prod.yml` carregam só o diferencial (ex.: verbosidade de log, `show-sql`). Remover `spring.profiles.active: dev`; o profile passa a vir de `SPRING_PROFILES_ACTIVE`, com `local` como default de desenvolvimento (via `spring.config` default ou documentação de execução). **Alternativa considerada**: manter `dev` e adicionar `prod` — rejeitada porque o dono pediu explicitamente `local`/`prod` e remover `dev`. Segredos continuam vindo de env vars nesta fase (Secrets Manager fica para depois).

### D4. Postgres local vai para `infra/local/`
O Dockerfile do banco é infraestrutura de desenvolvimento, não documentação e não imagem de produção (na cloud vira RDS gerido). Movê-lo para `infra/local/` coloca-o no lugar conceitualmente correto e o mantém perto do futuro `docker-compose` local. Deixar um ponteiro em `docs/` para não quebrar links existentes.

### D5. `infra/` como esqueleto documentado, não vazio
Criar `infra/modules/`, `infra/envs/{local,prod}` e `infra/bootstrap/` apenas com READMEs/placeholders que descrevem o que cada área conterá (VPC, RDS+parameter group para partman/cron, cluster ECS/Fargate, service reutilizável, state remoto). Isso "pavimenta" a próxima fase sem custo de manutenção de código morto. **Alternativa considerada**: não criar `infra/` ainda — rejeitada porque o requisito é justamente estabelecer a separação de topo agora.

## Risks / Trade-offs

- **Quebra de caminhos após renomear `aplicacoes/`** → varredura global por `aplicacoes/` em READMEs, CLAUDE.md/AGENTS.md, docs e `.vscode`; validar com `mvn test` em ambos os apps após o move.
- **IDE/`.idea` e `target/` referenciando caminho antigo** → `target/` é gerado (ignorado); recompilar limpo (`mvn clean`) após o move; artefatos de IDE não são normativos.
- **Dockerfile que não sobe por config de profile** → validar cada imagem localmente (build + run) apontando para o Postgres local antes de considerar concluído; garantir que `local` é o profile default ao rodar sem env.
- **`infra/` esqueleto virar código morto** → manter apenas READMEs/placeholders, sem `.tf` funcional, para não induzir uso prematuro; a próxima proposta preenche.
- **Perfil default incorreto em produção** → documentar que produção DEVE setar `SPRING_PROFILES_ACTIVE=prod` explicitamente; o default `local` é só conveniência de desenvolvimento.
- **`server.shutdown=graceful` alterar comportamento** → é ajuste de resiliência aditivo, coberto por teste manual de start/stop; não afeta contratos REST.

## Migration Plan

1. Renomear `aplicacoes/` → `code/` preservando histórico git (`git mv`).
2. Criar esqueleto `infra/` (`modules/`, `envs/local`, `envs/prod`, `bootstrap/`, `local/`) com READMEs.
3. Mover o Dockerfile do Postgres para `infra/local/` e deixar ponteiro em `docs/`.
4. Reorganizar `application.yml` de cada app em base + `local` + `prod`; remover `active: dev`.
5. Adicionar `Dockerfile` a cada app; validar build+run local contra o Postgres.
6. Atualizar README raiz, READMEs das apps e guias de agentes para os novos caminhos.
7. Rodar `mvn clean test` em ambos os apps; confirmar contratos REST inalterados.

Rollback: por ser mudança estrutural sob git, reverter o commit restaura a topologia anterior; nenhum recurso externo é criado.

## Resolved Decisions

- **Profile default**: fixar `spring.profiles.default: local` no `application.yml` base de cada app (não apenas documentar). Produção continua obrigada a setar `SPRING_PROFILES_ACTIVE=prod` explicitamente.
- **docker-compose local**: incluir já nesta fase um `code/docker-compose.yml` mínimo com as 2 apps + Postgres local, **sem** Floci (Floci fica para a fase seguinte).
- **Ponteiro em docs/**: após mover o Postgres para `infra/local/postgres/`, **deixar uma referência** em `docs/run_postgres16_ja_com_cron_partman/` apontando para o novo caminho (não remover), para não quebrar links existentes.
