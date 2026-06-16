# Design: API Gateway no console do floci-ui

## Contexto

O console web do floci ([floci-ui](https://github.com/floci-io/floci-ui), clonado localmente em
`docs/floci-aws-local/floci-ui/`, não rastreado pelo git deste repo) só sabe exibir um conjunto fixo
de serviços (`s3, sqs, dynamodb, sns, lambda, eks, secretsmanager, cognito, rds, elasticache, iam,
ssm, kms, cloudwatch` — ver `packages/frontend/src/api/types.ts`). API Gateway (v1 REST e v2 HTTP)
não está nessa lista: confirmado por busca no código-fonte (`packages/frontend/src` e
`packages/api/src`), zero ocorrências de "apigateway".

O floci core (porta 4566) já suporta API Gateway de verdade — validamos manualmente via AWS CLI e
curl criando uma HTTP API (v2) com integração `HTTP_PROXY` apontando para o serviço
`arj-contratoquery` (porta 8081), com passthrough de querystring, headers e códigos de erro
funcionando corretamente. O gap é só de visualização na UI.

## Objetivo

Tornar visível no console (`floci-ui`) qualquer API Gateway (REST v1 e HTTP v2) criado no floci,
incluindo suas rotas/recursos, integrations e stages — **somente leitura**, sem criar/editar/excluir
pela UI.

## Abordagem escolhida

Uma página única "API Gateway" (`/apigateway`), com duas abas internas — "REST APIs (v1)" e
"HTTP APIs (v2)" — em vez de duas páginas separadas ou de encaixar no mecanismo genérico multi-cloud
(`cloud-spi` adapters, hoje usado por storage/database/k8s/networking). Razões:

- Replica o modelo mental real da AWS: "API Gateway" é um produto só, com dois tipos de API.
- O mecanismo `cloud-spi` já usa a categoria `networking` para VPCs; forçar API Gateway lá seria
  confuso e mais trabalho do que o necessário para uma feature AWS-only.
- Não polui o grid da home com dois cards para o que o usuário entende como uma coisa só.

Padrão de referência seguido: `SecretsManagerPage` + `routes/secretsmanager.ts` — feature AWS-only,
fora do `cloud-spi`, com client do SDK dedicado em `aws.ts`. A diferença é que esta feature é
**somente leitura** (sem `CreateSecretForm`-equivalente, sem mutations).

## Arquitetura

```
Home grid (CloudConsoleHomePage)
   └─ card "API Gateway" → /apigateway  (status: available, só cloud === 'aws')

ApiGatewayPage.tsx
   ├─ Tab "REST APIs (v1)"  → lista RestApis → click → resources/methods + stages
   └─ Tab "HTTP APIs (v2)"  → lista Apis     → click → routes + integrations + stages

Frontend                         Backend (Hono)                       Floci core (4566)
useRestApisQuery()      ──GET──▶ /api/apigateway/rest/apis    ──────▶ APIGatewayClient (v1 SDK)
useRestApiDetailQuery() ──GET──▶ /api/apigateway/rest/apis/:id ─────▶ APIGatewayClient (v1 SDK)
useHttpApisQuery()      ──GET──▶ /api/apigateway/http/apis    ──────▶ ApiGatewayV2Client (v2 SDK)
useHttpApiDetailQuery() ──GET──▶ /api/apigateway/http/apis/:id ─────▶ ApiGatewayV2Client (v2 SDK)
                                  (mesmo padrão de aws.ts: endpoint = FLOCI_ENDPOINT)
```

## Componentes

### Backend (`packages/api`)

- **`aws.ts`** — adiciona dois clients novos ao objeto `awsClients`, seguindo o padrão existente
  (mesmo `base` com `region`/`credentials`/`endpoint`):
  - `apiGateway` (`@aws-sdk/client-api-gateway`, `APIGatewayClient`) — v1
  - `apiGatewayV2` (`@aws-sdk/client-apigatewayv2`, `ApiGatewayV2Client`) — v2
  - Ambos exportados como `export const apiGateway = ...` / `export const apiGatewayV2 = ...`,
    igual aos demais.

- **`routes/apigateway.ts`** (novo, Hono) — só leitura, 4 endpoints:
  - `GET /rest/apis` → `GetRestApisCommand`, retorna lista resumida (id, name, createdDate,
    endpointConfiguration).
  - `GET /rest/apis/:id` → compõe: `GetResourcesCommand` (recursos + métodos + integrations
    embutidas) + `GetStagesCommand`. Para cada stage, monta `executeUrl` =
    `${FLOCI_ENDPOINT}/execute-api/${id}/${stageName}`.
  - `GET /http/apis` → `GetApisCommand` (v2), lista resumida (apiId, name, protocolType,
    apiEndpoint, createdDate).
  - `GET /http/apis/:id` → compõe: `GetRoutesCommand` + `GetIntegrationsCommand` (junta por
    `integrationId` extraído de `route.target`) + `GetStagesCommand`. Mesma lógica de `executeUrl`
    por stage.
  - Erros do SDK (`NotFoundException` etc.) → repassados como `c.json({error: ...}, status)`,
    mesmo padrão de `routes/secretsmanager.ts`.

- **`index.ts`** — registra `app.route("/api/apigateway", apigateway)`.

### Frontend (`packages/frontend`)

- **`api/aws/apigateway.api.ts`** — funções `fetch`, tipos `RestApiSummary`, `RestApiDetail`,
  `HttpApiSummary`, `HttpApiDetail` (mesma forma de `secretsmanager.api.ts`).
- **`api/aws/apigateway.queries.ts`** — hooks react-query: `useRestApisQuery`,
  `useRestApiDetailQuery(id)`, `useHttpApisQuery`, `useHttpApiDetailQuery(id)`. Sem mutations.
- **`features/apigateway/ApiGatewayPage.tsx`** — página com:
  - Header + abas "REST APIs (v1)" / "HTTP APIs (v2)" (estado local, sem rota própria por aba).
  - Tabela de listagem por aba: Nome, Id, Endpoint/Protocolo, Criado em, contagem de
    rotas/recursos. Linha clicável.
  - Painel de detalhe: expansão inline abaixo da linha clicada (master-detail simples, sem modal
    nem drawer novo — menor componente novo para uma feature só-leitura), mostrando:
    - Rotas (v2) ou Recursos+Métodos (v1), cada um com o tipo de integration e a URI de destino.
    - Stages, com botão "Copiar URL" para a `executeUrl`.
  - Estado vazio (`EmptyState`, mesmo componente já usado) quando não há nenhuma API do tipo.
- **`App.tsx`** — `<Route path="/apigateway" element={<ApiGatewayPage/>}/>`.
- **`features/cloud-console/useCloudConsoleHomeData.ts`** — novo card no array `serviceCards`,
  análogo ao bloco `cloud === 'aws'` do `secretsmanager`:
  ```ts
  ...(cloud === 'aws' ? [{
      id: 'apigateway',
      label: 'API Gateway',
      status: 'available' as const,
      count: (restApisQuery.data?.length ?? 0) + (httpApisQuery.data?.length ?? 0),
      icon: Route, // lucide-react, já usado em CloudConsoleSections
      route: '/apigateway',
      meta: serviceMetaLabel(status, isLoading, 'apis'),
  }] : []),
  ```

## Fluxo de dados (exemplo: abrir detalhe de uma HTTP API)

```
Usuário clica na linha "floci-contratoquery-autorizacoes"
  → useHttpApiDetailQuery('f48573af47')
    → GET /api/apigateway/http/apis/f48573af47
      → ApiGatewayV2Client.send(GetRoutesCommand)      → [{routeKey: "GET /autorizacoes", target: "integrations/18fe0131"}]
      → ApiGatewayV2Client.send(GetIntegrationsCommand) → [{integrationId: "18fe0131", integrationType: "HTTP_PROXY", integrationUri: "http://host.docker.internal:8081/api/autorizacoes"}]
      → ApiGatewayV2Client.send(GetStagesCommand)       → [{stageName: "local", autoDeploy: true}]
      → monta executeUrl por stage: "http://localhost:4566/execute-api/f48573af47/local"
  ← painel de detalhe renderiza rota → integration → stage com botão copiar
```

## Tratamento de erro

- Floci core fora do ar / inalcançável → o `useCloudStatusQuery` já existente detecta isso
  (mecanismo igual ao usado pelas outras páginas); a página de API Gateway deve respeitar o mesmo
  estado de "runtime unreachable" já tratado em `CloudConsoleHomePage` (não duplicar lógica).
- API/rota/integration não encontrada (404 do SDK) → propagado como erro de query do react-query;
  renderizar mensagem inline, sem crash de página.
- Lista vazia (nenhuma API criada ainda) → `EmptyState` com texto explicando como criar uma via
  AWS CLI (já que a UI não cria).

## Testes

- Backend: testes de rota no mesmo estilo de `routes/secretsmanager.test.ts` e `routes/ec2.test.ts`
  — mockar `apiGateway`/`apiGatewayV2` clients, validar shape da resposta JSON e composição
  (routes+integrations+stages) para `GET /rest/apis/:id` e `GET /http/apis/:id`.
- Frontend: sem suite de testes de componente identificada para as outras feature pages
  (`SecretsManagerPage`, `RDSPage`) além de testes de rota no backend — seguir o mesmo nível,
  sem adicionar testes de componente novos além do que já é padrão no repo.
- Validação manual: usar a HTTP API `f48573af47` já criada (rota `GET /autorizacoes` →
  `host.docker.internal:8081/api/autorizacoes`) como caso real de ponta a ponta.

## Fora de escopo (explícito)

- Criar/editar/excluir API, rota, resource, method, integration ou stage pela UI.
- WebSocket APIs (v2 também suporta, mas não foi pedido).
- Autorizers, custom domains, usage plans, API keys.
- Suporte a Azure/GCP API Gateway equivalentes (escopo é só AWS, floci core).
