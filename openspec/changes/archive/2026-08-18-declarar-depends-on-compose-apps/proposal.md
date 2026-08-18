## Why

`apps/docker-compose.yml` não declara nenhum `depends_on` para os 5 serviços de aplicação contra
suas dependências de infraestrutura (Postgres, Floci, Kafka, Schema Registry, Valkey), violando o
requisito vigente de `orquestracao-local-unificada` de que a ordem de subida não pode ser
conhecimento tácito. Hoje a subida só funciona por acidente de timing (o `build:` de cada app
demora o suficiente para a infra ficar pronta) somado a `restart: unless-stopped` mascarando a
corrida com um loop de crash-restart até a infra responder — não pela ordem declarada que o
próprio `README.md` da raiz afirma existir.

## What Changes

- Adiciona `depends_on` com `condition: service_healthy` para os 5 serviços de aplicação, mapeado
  1:1 com as dependências reais de cada um (banco, mensageria SNS/SQS via Floci, Kafka, Valkey) —
  nenhum healthcheck novo é necessário, todos já existem nos composes de `infra/local/*` ou na
  própria imagem (caso do Floci).
- Os `depends_on` vivem num arquivo overlay novo (`apps/docker-compose.depends-on.yml`), não dentro
  de `apps/docker-compose.yml` — descoberto durante a implementação que `depends_on` direto ali
  quebra a subida standalone desse arquivo (Opção C do README), porque `depends_on` só resolve
  contra serviços do mesmo projeto Compose, e `apps/docker-compose.yml` sozinho não conhece
  `postgres`/`floci`/etc. O `compose.yaml` da raiz passa a incluir os dois juntos via
  `include: path: [...]`, que o Compose mescla antes de trazer para o projeto unificado.
- Adiciona ao spec `orquestracao-local-unificada` um cenário que enumera explicitamente qual
  serviço depende de qual, tornando o requisito genérico existente ("a dependência SHALL estar
  declarada") verificável por caso concreto, e não só por inspeção geral do arquivo.

## Capabilities

### New Capabilities

(nenhuma)

### Modified Capabilities

- `orquestracao-local-unificada`: adiciona cenário que fixa o mapeamento concreto de
  `depends_on`/`condition: service_healthy` por serviço de aplicação — o requisito "ordem de
  subida não é conhecimento tácito" já existe; este cenário o torna verificável.

## Impact

- Código afetado: `apps/docker-compose.depends-on.yml` (novo, só `depends_on`) e `compose.yaml` da
  raiz (`include:` atualizado). `apps/docker-compose.yml` fica idêntico ao estado anterior — nenhum
  serviço novo, nenhuma rede nova, nenhuma linha alterada nele.
- Nenhum código de aplicação (Java) é tocado — mudança restrita a orquestração local.
- Fora de escopo (decisão do autor do repositório): a documentação pervasiva de `pg_partman`/
  `pg_cron`/`pgvector` como se já estivessem em uso na tabela `autorizacoes` — achado da mesma
  auditoria, mas intencional: expurgo via `pg_partman` e uso de `pgvector` estão planejados para
  uma iteração futura, não são inconsistência a corrigir agora.
