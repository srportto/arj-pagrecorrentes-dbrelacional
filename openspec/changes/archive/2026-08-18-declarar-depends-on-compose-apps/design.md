## Context

`apps/docker-compose.yml` define os 5 serviços de aplicação sem nenhum `depends_on`. A auditoria
que originou esta change (pente fino de coerência entre `docker-compose` e documentação) confirmou,
por leitura direta do arquivo e grep, zero ocorrências de `depends_on` nesse compose — as únicas
existentes no ambiente unificado estão em `infra/local/kafka/compose.yaml` (schema-registry e
kafbat-ui dependendo de `kafka`).

Isso contradiz o requisito já vigente em `orquestracao-local-unificada` ("a ordem de subida MUST
NOT ser conhecimento tácito") e o cenário "Healthcheck presente em qualquer caminho", que já exigia
que serviços dependentes do Postgres esperassem por `service_healthy` — mas nunca foi verificado
contra `apps/docker-compose.yml` especificamente, só contra o serviço Postgres em si.

Todos os healthchecks necessários já existem:
- `postgres` (`infra/local/postgres/postgres-db-v18.yml`): `pg_isready -U ... -d ...`
- `kafka` (`infra/local/kafka/compose.yaml`): `kafka-broker-api-versions --bootstrap-server localhost:9092`
- `schema-registry` (`infra/local/kafka/compose.yaml`): `curl -f http://localhost:8081/subjects`
- `valkey` (`infra/local/redis/compose.yaml`): `valkey-cli ping`
- `floci` (`infra/local/floci/compose.yaml`): sem `healthcheck:` explícito no compose, mas a
  imagem `floci/floci:latest` embute o seu próprio `HEALTHCHECK` — confirmado empiricamente via
  `docker ps`, que mostra `floci-arj ... (healthy)` mesmo sem declaração no compose. Docker Compose
  usa o healthcheck da imagem quando o serviço não declara um próprio, então `condition:
  service_healthy` funciona para `floci` sem exigir nenhuma mudança em `infra/local/floci/compose.yaml`.

## Goals / Non-Goals

**Goals:**
- Fazer `apps/docker-compose.yml` declarar explicitamente a dependência de cada app contra sua
  infraestrutura, com `condition: service_healthy`.
- Tornar o requisito "ordem de subida não é conhecimento tácito" verificável por um cenário
  concreto, não só pela frase genérica já existente.

**Non-Goals:**
- Não adiciona healthcheck a nenhum serviço de infraestrutura — todos já existem (ou vêm da
  imagem, no caso do Floci).
- Não resolve a lacuna de que o tópico/filas do Floci (SNS + SQS) só existem depois de
  `terraform apply` manual em `infra/envs/local-messaging/` — isso é um passo documentado
  separadamente no README e fora do escopo de "container saudável"; `depends_on: floci: condition:
  service_healthy` garante que o processo do Floci está no ar e respondendo, não que os recursos
  AWS emulados já foram provisionados.
- Não mexe na documentação de `pg_partman`/`pg_cron`/`pgvector` (achado intencional, fora de
  escopo por decisão do autor do repositório).
- Não adiciona `depends_on` entre as próprias aplicações (ex.: `temporiza-autorizacao` →
  `contratocommand`) — a comunicação `temporiza-autorizacao` → `contratocommand` via PATCH já é
  resiliente a indisponibilidade temporária (classificada como retryable, ver CLAUDE.md da app),
  então a ordem entre elas não é um requisito de arranque, só de mensageria elegível a retry.

## Decisions

### D1 — `condition: service_healthy` em vez da forma curta de `depends_on`

**Decisão:** todo `depends_on` desta change usa a forma longa com `condition: service_healthy`,
nunca a forma curta (`depends_on: [postgres]`), que no Compose só espera o container **iniciar**,
não ficar pronto para aceitar conexões.

**Alternativas descartadas:**
| | Como | Por que não |
|---|---|---|
| Forma curta | `depends_on: - postgres` | Container "iniciado" não é container "pronto" — Postgres aceita conexões TCP antes de terminar de aplicar as migrations; a app ainda correria a mesma corrida que hoje, só um pouco mais estreita |
| `wait-for-it`/script de espera no entrypoint | script shell checando porta aberta | Duplica o que o Compose já resolve nativamente com `healthcheck` + `condition`; motivo exato pelo qual a spec já pede healthcheck no requisito do Postgres |

### D2 — Mapeamento de dependência por app, não um `depends_on` global

**Decisão:** cada app declara só as dependências que realmente usa (contratoquery não depende de
Floci, por exemplo), replicando a mesma granularidade que já existe nas seções `networks:` de cada
serviço em `apps/docker-compose.yml` hoje.

**Racional:** um `depends_on` genérico (todo app esperando toda infra) esconderia a topologia real
e tornaria o arquivo menos legível como documentação executável — objetivo central da capability
`orquestracao-local-unificada`.

### D3 — `depends_on` em arquivo overlay separado, não dentro de `apps/docker-compose.yml`

**Descoberto durante a implementação (tarefa 2.4):** `depends_on: postgres` dentro de
`apps/docker-compose.yml` quebra a subida standalone desse arquivo (Opção C do README, apps
sozinhos contra infra já no ar) com `service "contratocommand" depends on undefined service
"postgres": invalid compose project`. `depends_on` do Compose só resolve contra serviços
definidos no **mesmo projeto resolvido** — não existe modo "resolve contra containers Docker já
rodando, se existirem". A suposição original desta change (that cross-project `depends_on`
resolveria em runtime) estava errada; confirmado por reprodução direta antes de qualquer decisão.

**Decisão:** os 5 blocos `depends_on` vivem em `apps/docker-compose.depends-on.yml`, um overlay
que só adiciona `depends_on:` aos serviços já definidos em `apps/docker-compose.yml` (mesmo padrão
usado pela feature nativa `include: - path: [...]` do Compose, que mescla múltiplos arquivos num
único sub-projeto antes de incluí-lo). O `compose.yaml` da raiz passa a incluir os dois juntos:

```yaml
include:
  - infra/local/postgres/postgres-db-v18.yml
  - infra/local/floci/compose.yaml
  - infra/local/kafka/compose.yaml
  - infra/local/redis/compose.yaml
  - path:
      - apps/docker-compose.yml
      - apps/docker-compose.depends-on.yml
```

**Validado por teste isolado antes de tocar os arquivos reais** (mesmo método usado pela change
`unificar-orquestracao-docker-local` para o D4 de redes): dois arquivos de serviço + um terceiro só
com `depends_on`, incluídos via `path:` de uma lista — `docker compose config` confirma o merge e a
resolução correta do `depends_on` contra o serviço definido no arquivo irmão.

**Alternativas descartadas:**
| | Como | Por que não |
|---|---|---|
| `depends_on` direto em `apps/docker-compose.yml` (decisão original) | um arquivo só | Quebra a Opção C do README — confirmado por reprodução |
| Duplicar `apps/docker-compose.yml` com/sem `depends_on` | dois arquivos completos, um para cada caminho | Duas cópias divergentes do mesmo serviço é exatamente o defeito que `orquestracao-local-unificada` (requisito "Cada serviço de infraestrutura tem uma única definição") já existe para evitar — mesmo que a duplicação aqui seja de app, não de infra, o princípio é o mesmo |
| Stub dos serviços de infra dentro de `apps/docker-compose.yml`, marcados `external` | permitiria `depends_on` resolver sempre | Reabre exatamente o problema que o D4 da change `unificar-orquestracao-docker-local` já resolveu (`external: true` fundido via `include:` quebra a criação da rede) |

**Consequência:** `apps/docker-compose.yml` continua idêntico ao estado anterior a esta change —
zero linhas alteradas nele. Toda a mudança de comportamento vem do arquivo novo + do `include:` da
raiz.

## Riscos / Trade-offs

- **[Risco]** Se algum serviço de infraestrutura nunca atingir `healthy` (ex.: Postgres com volume
  corrompido), o app correspondente nunca inicia — antes ele ao menos tentava e crash-loopava
  visivelmente. → **Mitigação:** esse é o comportamento correto e desejado: falhar cedo e claro
  (Compose reporta o serviço travado em "waiting") é preferível a um app tentando conectar num
  banco que nunca vai ficar pronto.
- **[Risco]** `floci` depende do healthcheck embutido na imagem, não de um declarado neste repo —
  se uma atualização de imagem remover esse healthcheck, `condition: service_healthy` passaria a
  travar a subida indefinidamente (Compose nunca marca "healthy" sem healthcheck configurado).
  → **Mitigação:** documentar essa dependência implícita no próprio
  `apps/docker-compose.depends-on.yml` (comentário), e validar `docker compose up` de ponta a
  ponta na task de verificação desta change.
- **[Risco]** Achado na tarefa 2.4: `terraform apply` de `infra/envs/local-messaging/` precisa ser
  rerodado toda vez que o container do Floci é recriado (ex.: após `docker compose down` sem `-v`
  ainda derruba o Floci, que não tem volume persistente) — o `terraform.tfstate` acha que os
  recursos existem, mas o Floci novo está vazio. Isso já era verdade **antes** desta change (não é
  regressão introduzida aqui) e já está documentado no README; registrado porque apareceu durante a
  validação e vale reforçar: `depends_on: floci: condition: service_healthy` prova que o *processo*
  está no ar, nunca que os recursos AWS emulados existem (ver Non-Goals). → **Mitigação:** nenhuma
  nova nesta change; `terraform apply` continua sendo passo manual documentado.

## Migration Plan

Mudança local, sem estado a migrar. Passos:
1. Criar `apps/docker-compose.depends-on.yml` com os 5 blocos `depends_on` (D3) —
   `apps/docker-compose.yml` não é alterado.
2. Atualizar `include:` do `compose.yaml` da raiz para trazer os dois arquivos juntos via
   `path: [...]`.
3. `docker compose down -v` seguido de `docker compose up -d --build` a partir de um ambiente limpo
   (sem containers de infra já saudáveis de uma execução anterior) para provar que a ordem
   declarada, e não o timing incidental, é o que garante a subida correta.
4. Validar `apps/docker-compose.yml` sozinho (Opção C) continua subindo sem erro de
   `depends_on undefined service`.
5. Sem rollback especial — reverter os dois arquivos é suficiente; nenhuma migração de dado
   envolvida (exceto reaplicar `terraform apply` em `infra/envs/local-messaging/` se o Floci tiver
   sido recriado durante a validação — não é rollback desta change, é o passo normal documentado
   no README sempre que o Floci reinicia do zero).

## Open Questions

Nenhuma. O mapeamento de dependência por app já está determinado pelas variáveis de ambiente e
redes que cada serviço já declara hoje em `apps/docker-compose.yml`.
