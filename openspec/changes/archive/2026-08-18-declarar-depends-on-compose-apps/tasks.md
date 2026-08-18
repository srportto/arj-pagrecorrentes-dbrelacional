## 1. Criar apps/docker-compose.depends-on.yml e ligar via include: da raiz (D3)

> Revisado durante a implementação: os `depends_on` NÃO entram em `apps/docker-compose.yml`
> (quebraria a subida standalone da Opção C — ver achado na task 2.4). Vivem num overlay próprio,
> incluído só pela raiz junto com `apps/docker-compose.yml` via `include: path: [...]`.

- [x] 1.1 Criar `apps/docker-compose.depends-on.yml` com `depends_on: { postgres: { condition: service_healthy }, floci: { condition: service_healthy } }` para `contratocommand`
- [x] 1.2 Adicionar `depends_on: { postgres: { condition: service_healthy } }` para `contratoquery` no mesmo arquivo
- [x] 1.3 Adicionar `depends_on: { floci: { condition: service_healthy }, kafka: { condition: service_healthy }, schema-registry: { condition: service_healthy } }` para `autorizacaostatus-producer`
- [x] 1.4 Adicionar `depends_on: { kafka: { condition: service_healthy }, schema-registry: { condition: service_healthy } }` para `eventos-consumer`
- [x] 1.5 Adicionar `depends_on: { floci: { condition: service_healthy }, valkey: { condition: service_healthy } }` para `temporiza-autorizacao`
- [x] 1.6 Adicionar comentário no overlay explicando que o healthcheck do `floci` vem da imagem `floci/floci:latest`, não de declaração local
- [x] 1.7 Atualizar `include:` do `compose.yaml` da raiz para `path: [apps/docker-compose.yml, apps/docker-compose.depends-on.yml]` no lugar da entrada única anterior

## 2. Verificação

- [x] 2.1 `docker compose config` na raiz sem erro nem aviso
- [x] 2.2 `docker compose down -v` seguido de `docker compose up -d --build` a partir de ambiente limpo (sem infra pré-aquecida) — confirmar que nenhum dos 5 apps entra em `Restarting` antes da infra ficar `healthy`
- [x] 2.3 `docker compose ps` mostrando os 5 apps e os serviços de infra como `healthy`/`Up`, sem nenhum em ciclo de restart
- [x] 2.4 Confirmar que `apps/docker-compose.yml` sozinho (Opção C do README, infra já no ar) continua subindo corretamente — **achado durante a implementação:** `depends_on` cross-project NÃO resolve contra containers já rodando (Compose valida contra o modelo do projeto, não contra o Docker ao vivo); `depends_on: postgres` dentro de `apps/docker-compose.yml` quebrava a Opção C com "depends_on undefined service". Corrigido movendo os 5 blocos `depends_on` para `apps/docker-compose.depends-on.yml`, incluído só pela raiz via `include: path: [apps/docker-compose.yml, apps/docker-compose.depends-on.yml]` (Compose mescla os dois antes de incluir). Validado: `apps/` sozinho continua sem `depends_on` e sobe normal; a raiz unificada tem o `depends_on` real.

## 3. Sincronizar specs e arquivar

- [x] 3.1 Rodar `openspec archive declarar-depends-on-compose-apps` após 1 e 2 completos, sincronizando o delta spec com `openspec/specs/orquestracao-local-unificada/spec.md`
