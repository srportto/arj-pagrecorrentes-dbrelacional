## Why

O monorepo cresceu como um conjunto de aplicações (`aplicacoes/`) sem separação entre código de aplicação e código de infraestrutura, sem contêiner das próprias apps (só o Postgres tem Dockerfile) e com o profile Spring fixo em `dev`. Antes de evoluir para uma arquitetura cloud-native (AWS ECS + Fargate, IaC com Terraform, ambiente local via Floci), é preciso **pavimentar a estrutura**: uma organização previsível de pastas, contêineres prontos para Fargate e uma configuração por profiles capaz de distinguir ambiente local de produção. Esta mudança é puramente estrutural — não provisiona nada na cloud e não altera o comportamento das aplicações.

## What Changes

- **BREAKING** (estrutura de pastas): renomear `aplicacoes/` → `code/` (nomes em inglês). Todos os caminhos em READMEs, docs e configs passam a apontar para `code/`.
- Criar a pasta de topo `infra/` como **esqueleto** preparado para Terraform (módulos, ambientes `local`/`prod`, bootstrap de state), com READMEs e placeholders — **sem** recursos AWS reais nesta fase.
- Adicionar um `Dockerfile` por aplicação (`contratocommand`, `contratoquery`), multi-stage e compatível com ECS/Fargate (JRE + JAR, imagem enxuta, health via `/actuator/health`, shutdown gracioso).
- Reorganizar a configuração Spring em profiles: `application.yml` (base, comum) + `application-local.yml` + `application-prod.yml`, **removendo** o `spring.profiles.active: dev` fixo. O profile ativo passa a vir do ambiente (`SPRING_PROFILES_ACTIVE`), com `local` como default de desenvolvimento.
- Mover o Dockerfile do Postgres (partman/cron) de `docs/` para `infra/local/` como artefato de infraestrutura **de desenvolvimento local**.
- Atualizar READMEs e a documentação de estrutura para refletir `code/` + `infra/`.

Fora de escopo (fases seguintes): Terraform funcional, provisionamento AWS, consumo de serviços AWS pela app (Secrets Manager, SSM, SQS…), migração de schema (Flyway/Liquibase), pipelines de CI/CD.

## Capabilities

### New Capabilities
- `monorepo-organization`: define a topologia de topo do monorepo (`code/` + `infra/`), os requisitos de contêiner por aplicação (Fargate-ready), a estratégia de profiles Spring (`local`/`prod`) e a localização dos artefatos de infraestrutura de desenvolvimento local.

### Modified Capabilities
<!-- Nenhuma capability de comportamento de aplicação muda; as specs existentes (health-check, listar-autorizacoes, etc.) permanecem intactas. -->

## Impact

- **Estrutura de diretórios**: `aplicacoes/` → `code/`; nova pasta `infra/`; Dockerfile do Postgres migra de `docs/run_postgres16_ja_com_cron_partman/` para `infra/local/`.
- **Novos arquivos**: `code/contratocommand/Dockerfile`, `code/contratoquery/Dockerfile`, `code/docker-compose.yml` (opcional, ambiente local), esqueleto `infra/**` (READMEs/placeholders).
- **Configuração**: cada app ganha `application-local.yml` e `application-prod.yml`; `application.yml` deixa de fixar o profile `dev`.
- **Documentação**: `README.md` raiz e READMEs das apps atualizados; caminhos em CLAUDE.md/AGENTS.md revisados.
- **Comportamento das aplicações**: inalterado — endpoints, portas (8080/8081), dependência de PostgreSQL 16 com partman/cron e a suíte de testes continuam idênticos.
- **Build/execução**: comandos `mvn` passam a rodar a partir de `code/<app>`; execução local ganha caminho via Docker além do `mvn spring-boot:run`.
