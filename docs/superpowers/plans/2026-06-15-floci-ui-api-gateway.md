# API Gateway no console do floci-ui — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tornar visível no console do floci-ui (REST v1 e HTTP v2), somente leitura, com rotas/recursos, integrations e stages — resolvendo o gap descoberto em que API Gateway não aparece em lugar nenhum da UI hoje.

**Architecture:** Uma página `/apigateway` com duas abas (REST v1 / HTTP v2), seguindo o padrão já estabelecido no repo (rota Hono dedicada usando os clients do AWS SDK em `aws.ts`, página React com layout `split` lista+detalhe igual ao `RDSPage`). Sem CRUD, sem tocar no mecanismo genérico `cloud-spi`.

**Tech Stack:** Hono (backend, Bun), `@aws-sdk/client-api-gateway` + `@aws-sdk/client-apigatewayv2` (novos), React + TanStack Query (frontend), `bun:test` para os testes de rota.

**Repositório de trabalho:** Todo o código deste plano vive em `docs/floci-aws-local/floci-ui/` — um clone próprio (`git clone https://github.com/floci-io/floci-ui`), **não rastreado** pelo repo principal (está no `.gitignore`). Todos os caminhos de arquivo abaixo são relativos a essa pasta. Todos os comandos `git`/`docker compose`/`bun`/`pnpm` deste plano devem ser executados com cwd em `docs/floci-aws-local/floci-ui/`.

**Spec de referência:** `docs/superpowers/specs/2026-06-15-floci-ui-api-gateway-design.md`

**Ajuste em relação à spec original:** a spec previa o backend computando a `executeUrl` a partir de `FLOCI_ENDPOINT`. Isso está errado: dentro do container `floci-api`, `FLOCI_ENDPOINT=http://floci:4566` (hostname interno da rede Docker), que não resolve no navegador do usuário. A URL pública correta (`http://localhost:4566`) só é conhecida no frontend, que já a resolve hoje via `runtimeEndpointLabel('aws', status)` (`packages/frontend/src/features/cloud-console/cloudConsoleHome.utils.ts:4`). Este plano monta a `executeUrl` no frontend, reaproveitando essa função existente, e o backend devolve apenas `stageName`/`autoDeploy`/`deploymentId`.

---

### Task 0: Branch local no clone do floci-ui

**Files:** nenhum (só git)

- [ ] **Step 1: Criar branch local** (não executado — esta pasta de trabalho não é um clone git real, não há `.git`; ver nota no fechamento do plano)

```bash
cd docs/floci-aws-local/floci-ui
git checkout -b feature/apigateway-console
```

Expected: `Switched to a new branch 'feature/apigateway-console'`. (Esse clone é local/scratch — os commits deste plano não serão enviados a `floci-io/floci-ui`; servem só para histórico local do trabalho.)

---

### Task 1: Clients do AWS SDK para API Gateway v1 e v2

**Files:**
- Modify: `packages/api/package.json`
- Modify: `packages/api/src/aws.ts`

- [x] **Step 1: Adicionar as duas dependências no `package.json`**

Em `packages/api/package.json`, dentro de `"dependencies"`, adicionar (mesma faixa de versão dos outros clients AWS já presentes):

```json
    "@aws-sdk/client-api-gateway": "^3.1063.0",
    "@aws-sdk/client-apigatewayv2": "^3.1063.0",
```

(ordem alfabética junto aos demais `@aws-sdk/client-*` já existentes).

- [x] **Step 2: Adicionar os clients em `aws.ts`**

Arquivo completo após a mudança (`packages/api/src/aws.ts`):

```ts
import { S3Client } from "@aws-sdk/client-s3";
import { LambdaClient } from "@aws-sdk/client-lambda";
import { EKSClient } from "@aws-sdk/client-eks";
import { EC2Client } from "@aws-sdk/client-ec2";
import { RDSClient } from "@aws-sdk/client-rds";
import { SecretsManagerClient } from "@aws-sdk/client-secrets-manager";
import { APIGatewayClient } from "@aws-sdk/client-api-gateway";
import { ApiGatewayV2Client } from "@aws-sdk/client-apigatewayv2";
const endpoint = process.env.FLOCI_ENDPOINT;
const region = process.env.AWS_REGION || "us-east-1";
const credentials = {
  accessKeyId: process.env.AWS_ACCESS_KEY_ID || "test",
  secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY || "test",
};
const base = {
  region,
  credentials,
  ...(endpoint ? { endpoint, forcePathStyle: true } : {}),
};

export const awsClients = {
  s3: new S3Client({ ...base, forcePathStyle: true }),
  lambda: new LambdaClient(base),
  eks: new EKSClient(base),
  ec2: new EC2Client(base),
  rds: new RDSClient(base),
  secretsManager: new SecretsManagerClient(base),
  apiGateway: new APIGatewayClient(base),
  apiGatewayV2: new ApiGatewayV2Client(base),
} as const;

export type AwsClientName = keyof typeof awsClients;

export const s3 = awsClients.s3;
export const lambda = awsClients.lambda;
export const eks = awsClients.eks;
export const ec2 = awsClients.ec2;
export const rds = awsClients.rds;
export const secretsManager = awsClients.secretsManager;
export const apiGateway = awsClients.apiGateway;
export const apiGatewayV2 = awsClients.apiGatewayV2;
```

- [x] **Step 3: Rebuildar a imagem do `floci-api` (node_modules não é volume-mounted, precisa reconstruir)**

```bash
docker compose build floci-api
docker compose up -d floci-api
```

Expected: build passa sem erro (`bun install` resolve as duas novas dependências), container `floci-ui-floci-api-1` volta a `Up`.

- [x] **Step 4: Confirmar que o container subiu saudável**

```bash
docker compose ps floci-api
```

Expected: `STATUS` = `Up ... seconds`.

- [x] **Step 5: Commit**

```bash
git add packages/api/package.json packages/api/src/aws.ts
git commit -m "feat(api): add API Gateway v1/v2 SDK clients"
```

---

### Task 2: Rota backend — `GET /rest/apis` (lista REST APIs v1)

**Files:**
- Create: `packages/api/src/routes/apigateway.ts`
- Create: `packages/api/src/routes/apigateway.test.ts`

- [x] **Step 1: Escrever o teste que falha**

Criar `packages/api/src/routes/apigateway.test.ts`:

```ts
import {beforeEach, describe, expect, mock, test} from 'bun:test'

// O router importa `apiGateway`/`apiGatewayV2` diretamente de `../aws`, então
// substituímos o módulo por um stub cujo `send()` é controlado por teste —
// mesmo padrão usado em `secretsmanager.test.ts`.
let lastRestCommand: {constructor: {name: string}; input: Record<string, unknown>} | null = null
let restResponder: (commandName: string, command: {input: Record<string, unknown>}) => unknown
let lastHttpCommand: {constructor: {name: string}; input: Record<string, unknown>} | null = null
let httpResponder: (commandName: string, command: {input: Record<string, unknown>}) => unknown

mock.module('../aws', () => ({
    apiGateway: {
        async send(command: {constructor: {name: string}; input: Record<string, unknown>}) {
            lastRestCommand = command
            return restResponder(command.constructor.name, command)
        },
    },
    apiGatewayV2: {
        async send(command: {constructor: {name: string}; input: Record<string, unknown>}) {
            lastHttpCommand = command
            return httpResponder(command.constructor.name, command)
        },
    },
}))

const {default: app} = await import('./apigateway')

beforeEach(() => {
    lastRestCommand = null
    restResponder = () => ({})
    lastHttpCommand = null
    httpResponder = () => ({})
})

describe('GET /rest/apis', () => {
    test('follows position pagination and maps each entry', async () => {
        const pages = [
            {items: [{id: 'a1', name: 'api-a', createdDate: new Date('2026-01-01T00:00:00Z'), endpointConfiguration: {types: ['REGIONAL']}}], position: 'page2'},
            {items: [{id: 'a2', name: 'api-b'}], position: undefined},
        ]
        let call = 0
        restResponder = () => pages[call++]

        const res = await app.request('/rest/apis')
        expect(res.status).toBe(200)
        const body = await res.json()

        expect(call).toBe(2)
        expect(body).toHaveLength(2)
        expect(body[0]).toMatchObject({
            id: 'a1',
            name: 'api-a',
            createdDate: '2026-01-01T00:00:00.000Z',
            endpointTypes: ['REGIONAL'],
        })
        expect(body[1]).toMatchObject({id: 'a2', name: 'api-b', endpointTypes: []})
    })
})
```

- [x] **Step 2: Rodar o teste e confirmar que falha**

```bash
docker compose exec floci-api bun test src/routes/apigateway.test.ts
```

Expected: FAIL — `Cannot find module './apigateway'` (arquivo ainda não existe).

- [x] **Step 3: Implementação mínima**

Criar `packages/api/src/routes/apigateway.ts`:

```ts
import {Hono} from 'hono'
import {GetRestApisCommand, type RestApi} from '@aws-sdk/client-api-gateway'
import {apiGateway} from '../aws'

const app = new Hono()

function iso(date?: Date): string | undefined {
    return date ? date.toISOString() : undefined
}

app.get('/rest/apis', async (c) => {
    const items: RestApi[] = []
    let position: string | undefined
    do {
        const res = await apiGateway.send(new GetRestApisCommand({position}))
        items.push(...(res.items ?? []))
        position = res.position
    } while (position)

    return c.json(items.map((api) => ({
        id: api.id ?? '',
        name: api.name ?? '',
        createdDate: iso(api.createdDate),
        endpointTypes: api.endpointConfiguration?.types ?? [],
    })))
})

export default app
```

- [x] **Step 4: Rodar o teste e confirmar que passa**

```bash
docker compose exec floci-api bun test src/routes/apigateway.test.ts
```

Expected: `1 pass`.

- [x] **Step 5: Commit**

```bash
git add packages/api/src/routes/apigateway.ts packages/api/src/routes/apigateway.test.ts
git commit -m "feat(api): list REST APIs (v1) endpoint"
```

---

### Task 3: Rota backend — `GET /rest/apis/:apiId` (detalhe REST v1)

**Files:**
- Modify: `packages/api/src/routes/apigateway.ts`
- Modify: `packages/api/src/routes/apigateway.test.ts`

- [x] **Step 1: Escrever os testes que falham**

Adicionar ao final de `packages/api/src/routes/apigateway.test.ts`:

```ts
describe('GET /rest/apis/:apiId', () => {
    test('composes resources, methods and stages with executeUrl-ready stage data', async () => {
        restResponder = (name) => {
            if (name === 'GetRestApiCommand') {
                return {id: 'a1', name: 'api-a', createdDate: new Date('2026-01-01T00:00:00Z'), endpointConfiguration: {types: ['REGIONAL']}}
            }
            if (name === 'GetResourcesCommand') {
                return {items: [{id: 'r1', path: '/autorizacoes', resourceMethods: {GET: {}}}], position: undefined}
            }
            if (name === 'GetMethodCommand') {
                return {httpMethod: 'GET', authorizationType: 'NONE', methodIntegration: {type: 'AWS_PROXY', uri: 'arn:aws:lambda:...'}}
            }
            if (name === 'GetStagesCommand') {
                return {item: [{stageName: 'prod', deploymentId: 'dep1'}]}
            }
            return {}
        }

        const res = await app.request('/rest/apis/a1')
        expect(res.status).toBe(200)
        const body = await res.json()

        expect(body.id).toBe('a1')
        expect(body.resources).toEqual([{
            id: 'r1',
            path: '/autorizacoes',
            methods: [{httpMethod: 'GET', authorizationType: 'NONE', integrationType: 'AWS_PROXY', integrationUri: 'arn:aws:lambda:...'}],
        }])
        expect(body.stages).toEqual([{stageName: 'prod', deploymentId: 'dep1'}])
    })

    test('returns 404 when the RestApi does not exist', async () => {
        restResponder = (name) => {
            if (name === 'GetRestApiCommand') {
                const err = new Error('Invalid Rest API identifier specified')
                err.name = 'NotFoundException'
                throw err
            }
            return {}
        }

        const res = await app.request('/rest/apis/missing')
        expect(res.status).toBe(404)
    })
})
```

- [x] **Step 2: Rodar o teste e confirmar que falha**

```bash
docker compose exec floci-api bun test src/routes/apigateway.test.ts
```

Expected: FAIL — rota `/rest/apis/:apiId` retorna 404 genérico do Hono (rota não definida) em vez do corpo esperado.

- [x] **Step 3: Implementação**

Substituir o conteúdo de `packages/api/src/routes/apigateway.ts` por:

```ts
import {Hono} from 'hono'
import {
    GetMethodCommand,
    GetResourcesCommand,
    GetRestApiCommand,
    GetRestApisCommand,
    GetStagesCommand,
    type Resource,
    type RestApi,
} from '@aws-sdk/client-api-gateway'
import {apiGateway} from '../aws'

const app = new Hono()

function iso(date?: Date): string | undefined {
    return date ? date.toISOString() : undefined
}

function isNotFound(e: unknown): boolean {
    return e instanceof Error && e.name === 'NotFoundException'
}

app.get('/rest/apis', async (c) => {
    const items: RestApi[] = []
    let position: string | undefined
    do {
        const res = await apiGateway.send(new GetRestApisCommand({position}))
        items.push(...(res.items ?? []))
        position = res.position
    } while (position)

    return c.json(items.map((api) => ({
        id: api.id ?? '',
        name: api.name ?? '',
        createdDate: iso(api.createdDate),
        endpointTypes: api.endpointConfiguration?.types ?? [],
    })))
})

app.get('/rest/apis/:apiId', async (c) => {
    const apiId = c.req.param('apiId')

    let api: RestApi
    try {
        api = await apiGateway.send(new GetRestApiCommand({restApiId: apiId}))
    } catch (e) {
        if (isNotFound(e)) return c.json({error: `RestApi not found: ${apiId}`}, 404)
        throw e
    }

    const resources: Resource[] = []
    let position: string | undefined
    do {
        const res = await apiGateway.send(new GetResourcesCommand({restApiId: apiId, position}))
        resources.push(...(res.items ?? []))
        position = res.position
    } while (position)

    const resourcesWithMethods = await Promise.all(resources.map(async (resource) => {
        const httpMethods = Object.keys(resource.resourceMethods ?? {})
        const methods = await Promise.all(httpMethods.map(async (httpMethod) => {
            const method = await apiGateway.send(new GetMethodCommand({
                restApiId: apiId,
                resourceId: resource.id,
                httpMethod,
            }))
            return {
                httpMethod,
                authorizationType: method.authorizationType ?? 'NONE',
                integrationType: method.methodIntegration?.type,
                integrationUri: method.methodIntegration?.uri,
            }
        }))
        return {
            id: resource.id ?? '',
            path: resource.path ?? '/',
            methods,
        }
    }))

    // GetStagesCommand (v1) is the one collection in this API that comes back
    // under `item` (singular) instead of `items` — a long-standing quirk of
    // the REST API Gateway service model.
    const stagesRes = await apiGateway.send(new GetStagesCommand({restApiId: apiId}))
    const stages = (stagesRes.item ?? []).map((stage) => ({
        stageName: stage.stageName ?? '',
        deploymentId: stage.deploymentId,
    }))

    return c.json({
        id: api.id ?? apiId,
        name: api.name ?? '',
        createdDate: iso(api.createdDate),
        endpointTypes: api.endpointConfiguration?.types ?? [],
        resources: resourcesWithMethods,
        stages,
    })
})

export default app
```

- [x] **Step 4: Rodar o teste e confirmar que passa**

```bash
docker compose exec floci-api bun test src/routes/apigateway.test.ts
```

Expected: `3 pass` (lista + os 2 novos).

- [x] **Step 5: Commit**

```bash
git add packages/api/src/routes/apigateway.ts packages/api/src/routes/apigateway.test.ts
git commit -m "feat(api): describe REST API (v1) endpoint with resources, methods and stages"
```

---

### Task 4: Rota backend — `GET /http/apis` (lista HTTP APIs v2)

**Files:**
- Modify: `packages/api/src/routes/apigateway.ts`
- Modify: `packages/api/src/routes/apigateway.test.ts`

- [x] **Step 1: Escrever o teste que falha**

Adicionar ao final de `packages/api/src/routes/apigateway.test.ts`:

```ts
describe('GET /http/apis', () => {
    test('follows NextToken pagination and maps each entry', async () => {
        const pages = [
            {Items: [{ApiId: 'h1', Name: 'http-a', ProtocolType: 'HTTP', ApiEndpoint: 'http://localhost:4566', CreatedDate: new Date('2026-01-01T00:00:00Z')}], NextToken: 'page2'},
            {Items: [{ApiId: 'h2', Name: 'http-b', ProtocolType: 'HTTP'}], NextToken: undefined},
        ]
        let call = 0
        httpResponder = () => pages[call++]

        const res = await app.request('/http/apis')
        expect(res.status).toBe(200)
        const body = await res.json()

        expect(call).toBe(2)
        expect(body).toHaveLength(2)
        expect(body[0]).toMatchObject({
            id: 'h1',
            name: 'http-a',
            protocolType: 'HTTP',
            apiEndpoint: 'http://localhost:4566',
            createdDate: '2026-01-01T00:00:00.000Z',
        })
        expect(body[1]).toMatchObject({id: 'h2', name: 'http-b', protocolType: 'HTTP'})
    })
})
```

- [x] **Step 2: Rodar o teste e confirmar que falha**

```bash
docker compose exec floci-api bun test src/routes/apigateway.test.ts
```

Expected: FAIL — rota `/http/apis` não existe (404 do Hono).

- [x] **Step 3: Implementação**

Adicionar ao final de `packages/api/src/routes/apigateway.ts`, antes de `export default app`:

```ts
app.get('/http/apis', async (c) => {
    const items: Api[] = []
    let nextToken: string | undefined
    do {
        const res = await apiGatewayV2.send(new GetApisCommand({NextToken: nextToken}))
        items.push(...(res.Items ?? []))
        nextToken = res.NextToken
    } while (nextToken)

    return c.json(items.map((api) => ({
        id: api.ApiId ?? '',
        name: api.Name ?? '',
        protocolType: api.ProtocolType ?? '',
        apiEndpoint: api.ApiEndpoint,
        createdDate: iso(api.CreatedDate),
    })))
})
```

E atualizar os imports no topo do arquivo:

```ts
import {
    GetMethodCommand,
    GetResourcesCommand,
    GetRestApiCommand,
    GetRestApisCommand,
    GetStagesCommand,
    type Resource,
    type RestApi,
} from '@aws-sdk/client-api-gateway'
import {GetApisCommand, type Api} from '@aws-sdk/client-apigatewayv2'
import {apiGateway, apiGatewayV2} from '../aws'
```

- [x] **Step 4: Rodar o teste e confirmar que passa**

```bash
docker compose exec floci-api bun test src/routes/apigateway.test.ts
```

Expected: `4 pass`.

- [x] **Step 5: Commit**

```bash
git add packages/api/src/routes/apigateway.ts packages/api/src/routes/apigateway.test.ts
git commit -m "feat(api): list HTTP APIs (v2) endpoint"
```

---

### Task 5: Rota backend — `GET /http/apis/:apiId` (detalhe HTTP v2)

**Files:**
- Modify: `packages/api/src/routes/apigateway.ts`
- Modify: `packages/api/src/routes/apigateway.test.ts`

- [x] **Step 1: Escrever os testes que falham**

Adicionar ao final de `packages/api/src/routes/apigateway.test.ts`:

```ts
describe('GET /http/apis/:apiId', () => {
    test('joins routes to their integration and maps stages', async () => {
        httpResponder = (name) => {
            if (name === 'GetApiCommand') {
                return {ApiId: 'h1', Name: 'http-a', ProtocolType: 'HTTP', ApiEndpoint: 'http://localhost:4566', CreatedDate: new Date('2026-01-01T00:00:00Z')}
            }
            if (name === 'GetRoutesCommand') {
                return {Items: [{RouteId: 'r1', RouteKey: 'GET /autorizacoes', AuthorizationType: 'NONE', Target: 'integrations/i1'}], NextToken: undefined}
            }
            if (name === 'GetIntegrationsCommand') {
                return {Items: [{IntegrationId: 'i1', IntegrationType: 'HTTP_PROXY', IntegrationUri: 'http://host.docker.internal:8081/api/autorizacoes'}], NextToken: undefined}
            }
            if (name === 'GetStagesCommand') {
                return {Items: [{StageName: 'local', AutoDeploy: true}]}
            }
            return {}
        }

        const res = await app.request('/http/apis/h1')
        expect(res.status).toBe(200)
        const body = await res.json()

        expect(body.id).toBe('h1')
        expect(body.routes).toEqual([{
            routeKey: 'GET /autorizacoes',
            authorizationType: 'NONE',
            integrationType: 'HTTP_PROXY',
            integrationUri: 'http://host.docker.internal:8081/api/autorizacoes',
        }])
        expect(body.stages).toEqual([{stageName: 'local', autoDeploy: true}])
    })

    test('leaves integrationType/integrationUri undefined when a route has no matching integration', async () => {
        httpResponder = (name) => {
            if (name === 'GetApiCommand') return {ApiId: 'h1', Name: 'http-a', ProtocolType: 'HTTP'}
            if (name === 'GetRoutesCommand') return {Items: [{RouteId: 'r1', RouteKey: 'GET /orphan', AuthorizationType: 'NONE', Target: 'integrations/missing'}]}
            if (name === 'GetIntegrationsCommand') return {Items: []}
            if (name === 'GetStagesCommand') return {Items: []}
            return {}
        }

        const res = await app.request('/http/apis/h1')
        const body = await res.json()

        expect(body.routes).toEqual([{
            routeKey: 'GET /orphan',
            authorizationType: 'NONE',
            integrationType: undefined,
            integrationUri: undefined,
        }])
    })

    test('returns 404 when the Api does not exist', async () => {
        httpResponder = (name) => {
            if (name === 'GetApiCommand') {
                const err = new Error('Invalid API identifier specified')
                err.name = 'NotFoundException'
                throw err
            }
            return {}
        }

        const res = await app.request('/http/apis/missing')
        expect(res.status).toBe(404)
    })
})
```

- [x] **Step 2: Rodar o teste e confirmar que falha**

```bash
docker compose exec floci-api bun test src/routes/apigateway.test.ts
```

Expected: FAIL — rota `/http/apis/:apiId` não existe.

- [x] **Step 3: Implementação**

Adicionar ao final de `packages/api/src/routes/apigateway.ts`, antes de `export default app`:

```ts
app.get('/http/apis/:apiId', async (c) => {
    const apiId = c.req.param('apiId')

    let api: Api
    try {
        api = await apiGatewayV2.send(new GetApiCommand({ApiId: apiId}))
    } catch (e) {
        if (isNotFound(e)) return c.json({error: `Api not found: ${apiId}`}, 404)
        throw e
    }

    const routes: Route[] = []
    let routesNextToken: string | undefined
    do {
        const res = await apiGatewayV2.send(new GetRoutesCommand({ApiId: apiId, NextToken: routesNextToken}))
        routes.push(...(res.Items ?? []))
        routesNextToken = res.NextToken
    } while (routesNextToken)

    const integrations: HttpIntegration[] = []
    let integrationsNextToken: string | undefined
    do {
        const res = await apiGatewayV2.send(new GetIntegrationsCommand({ApiId: apiId, NextToken: integrationsNextToken}))
        integrations.push(...(res.Items ?? []))
        integrationsNextToken = res.NextToken
    } while (integrationsNextToken)
    const integrationsById = new Map(integrations.map((i) => [i.IntegrationId, i]))

    const stagesRes = await apiGatewayV2.send(new GetHttpStagesCommand({ApiId: apiId}))
    const stages = (stagesRes.Items ?? []).map((stage) => ({
        stageName: stage.StageName ?? '',
        autoDeploy: stage.AutoDeploy ?? false,
    }))

    return c.json({
        id: api.ApiId ?? apiId,
        name: api.Name ?? '',
        protocolType: api.ProtocolType ?? '',
        apiEndpoint: api.ApiEndpoint,
        createdDate: iso(api.CreatedDate),
        routes: routes.map((route) => {
            const integrationId = route.Target?.startsWith('integrations/')
                ? route.Target.slice('integrations/'.length)
                : undefined
            const integration = integrationId ? integrationsById.get(integrationId) : undefined
            return {
                routeKey: route.RouteKey,
                authorizationType: route.AuthorizationType ?? 'NONE',
                integrationType: integration?.IntegrationType,
                integrationUri: integration?.IntegrationUri,
            }
        }),
        stages,
    })
})
```

E atualizar os imports no topo do arquivo (substituir o import de `@aws-sdk/client-apigatewayv2` inteiro):

```ts
import {
    GetApiCommand,
    GetApisCommand,
    GetIntegrationsCommand,
    GetRoutesCommand,
    GetStagesCommand as GetHttpStagesCommand,
    type Api,
    type Integration as HttpIntegration,
    type Route,
} from '@aws-sdk/client-apigatewayv2'
```

- [x] **Step 4: Rodar o teste e confirmar que passa**

```bash
docker compose exec floci-api bun test src/routes/apigateway.test.ts
```

Expected: `7 pass` (4 anteriores + 3 novos).

- [x] **Step 5: Type-check do pacote**

```bash
docker compose exec floci-api bun run type-check
```

Expected: sem erros (confirma que os imports/aliases de tipos com nomes repetidos entre `client-api-gateway` e `client-apigatewayv2` — `Stage`, `Integration` — não colidem, já que só os de `client-apigatewayv2` foram importados como tipo nesta rota e os de v1 não precisaram de alias).

- [x] **Step 6: Commit**

```bash
git add packages/api/src/routes/apigateway.ts packages/api/src/routes/apigateway.test.ts
git commit -m "feat(api): describe HTTP API (v2) endpoint with routes, integrations and stages"
```

---

### Task 6: Registrar a rota e validar contra o gateway real já criado

**Files:**
- Modify: `packages/api/src/index.ts`

- [x] **Step 1: Registrar `/api/apigateway`**

Em `packages/api/src/index.ts`, adicionar o import e a rota:

```ts
import "dotenv/config";
import { Hono } from "hono";
import { serveStatic } from "hono/bun";
import { cors } from "hono/cors";
import { logger } from "hono/logger";
import eks from "./routes/eks";
import rds from "./routes/rds";
import ec2 from "./routes/ec2";
import secretsmanager from "./routes/secretsmanager";
import apigateway from "./routes/apigateway";
import clouds from "./routes/clouds";
const app = new Hono();
```

(o restante do arquivo até `app.route("/api/secretsmanager", secretsmanager);` fica igual; adicionar a linha nova logo depois)

```ts
app.route("/api/eks", eks);
app.route("/api/rds", rds);
app.route("/api/ec2", ec2);
app.route("/api/secretsmanager", secretsmanager);
app.route("/api/apigateway", apigateway);
app.route("/api/clouds", clouds);
```

- [x] **Step 2: Rebuildar e reiniciar o container**

```bash
docker compose up -d --build floci-api
```

Expected: container recriado e `Up`.

- [x] **Step 3: Validar contra o HTTP API real criado nesta sessão (`f48573af47`)**

```bash
curl -s http://localhost:4501/api/apigateway/http/apis | head -c 500
echo
curl -s http://localhost:4501/api/apigateway/http/apis/f48573af47
```

Expected: o primeiro `curl` lista pelo menos a API `floci-contratoquery-autorizacoes` (id `f48573af47`); o segundo retorna `routes: [{routeKey: "GET /autorizacoes", authorizationType: "NONE", integrationType: "HTTP_PROXY", integrationUri: "http://host.docker.internal:8081/api/autorizacoes"}]` e `stages: [{stageName: "local", autoDeploy: true}]`.

Se a API não existir mais (container do floci foi reiniciado desde a criação), recriar com os comandos já documentados na conversa anterior (`aws apigatewayv2 create-api` / `create-integration` / `create-route` / `create-stage` contra `http://localhost:4566`) antes de validar.

- [x] **Step 4: Validar a lista de REST APIs (v1) não quebra quando vazia**

```bash
curl -s http://localhost:4501/api/apigateway/rest/apis
```

Expected: `[]` (nenhuma REST API v1 foi criada nesta sessão — confirma que o endpoint não lança erro com lista vazia).

- [x] **Step 5: Commit**

```bash
git add packages/api/src/index.ts
git commit -m "feat(api): mount /api/apigateway routes"
```

---

### Task 7: Frontend — endpoint keys e API client

**Files:**
- Modify: `packages/frontend/src/api/api.ts`
- Create: `packages/frontend/src/api/aws/apigateway.api.ts`

- [x] **Step 1: Adicionar as endpoint keys**

Em `packages/frontend/src/api/api.ts`, dentro de `apiEndpointKeys.aws`, logo após o bloco `secretsmanager: {...}` (antes de `ec2: {`):

```ts
    apigateway: {
      rest: {
        apis: {
          list: "aws.apigateway.rest.apis.list",
          describe: "aws.apigateway.rest.apis.describe",
        },
      },
      http: {
        apis: {
          list: "aws.apigateway.http.apis.list",
          describe: "aws.apigateway.http.apis.describe",
        },
      },
    },
```

- [x] **Step 2: Registrar as entradas no `endpointRegistry`**

No mesmo arquivo, logo após o bloco `// AWS Secrets Manager` (depois da entrada `apiEndpointKeys.aws.secretsmanager.secrets.value.put`, antes de `// AWS EC2`):

```ts
  // AWS API Gateway
  [
    apiEndpointKeys.aws.apigateway.rest.apis.list,
    {
      path: "/apigateway/rest/apis",
      method: "GET",
      telemetry: { provider: "aws", service: "apigateway" },
    },
  ],
  [
    apiEndpointKeys.aws.apigateway.rest.apis.describe,
    {
      path: "/apigateway/rest/apis/:apiId",
      method: "GET",
      telemetry: { provider: "aws", service: "apigateway" },
    },
  ],
  [
    apiEndpointKeys.aws.apigateway.http.apis.list,
    {
      path: "/apigateway/http/apis",
      method: "GET",
      telemetry: { provider: "aws", service: "apigateway" },
    },
  ],
  [
    apiEndpointKeys.aws.apigateway.http.apis.describe,
    {
      path: "/apigateway/http/apis/:apiId",
      method: "GET",
      telemetry: { provider: "aws", service: "apigateway" },
    },
  ],
```

- [x] **Step 3: Criar o client**

Criar `packages/frontend/src/api/aws/apigateway.api.ts`:

```ts
import { apiClient, apiEndpointKeys } from "@/api/api";

export interface RestApiSummary {
  id: string;
  name: string;
  createdDate?: string;
  endpointTypes: string[];
}

export interface RestApiMethod {
  httpMethod: string;
  authorizationType: string;
  integrationType?: string;
  integrationUri?: string;
}

export interface RestApiResource {
  id: string;
  path: string;
  methods: RestApiMethod[];
}

export interface RestApiStageInfo {
  stageName: string;
  deploymentId?: string;
}

export interface RestApiDetail extends RestApiSummary {
  resources: RestApiResource[];
  stages: RestApiStageInfo[];
}

export interface HttpApiSummary {
  id: string;
  name: string;
  protocolType: string;
  apiEndpoint?: string;
  createdDate?: string;
}

export interface HttpApiRoute {
  routeKey: string;
  authorizationType: string;
  integrationType?: string;
  integrationUri?: string;
}

export interface HttpApiStageInfo {
  stageName: string;
  autoDeploy: boolean;
}

export interface HttpApiDetail extends HttpApiSummary {
  routes: HttpApiRoute[];
  stages: HttpApiStageInfo[];
}

export async function listRestApis(
  signal?: AbortSignal,
): Promise<RestApiSummary[]> {
  const res = await apiClient.call<RestApiSummary[]>(
    apiEndpointKeys.aws.apigateway.rest.apis.list,
    { signal },
  );
  return res.data;
}

export async function describeRestApi(
  apiId: string,
  signal?: AbortSignal,
): Promise<RestApiDetail> {
  const res = await apiClient.call<RestApiDetail>(
    apiEndpointKeys.aws.apigateway.rest.apis.describe,
    { signal, params: { apiId } },
  );
  return res.data;
}

export async function listHttpApis(
  signal?: AbortSignal,
): Promise<HttpApiSummary[]> {
  const res = await apiClient.call<HttpApiSummary[]>(
    apiEndpointKeys.aws.apigateway.http.apis.list,
    { signal },
  );
  return res.data;
}

export async function describeHttpApi(
  apiId: string,
  signal?: AbortSignal,
): Promise<HttpApiDetail> {
  const res = await apiClient.call<HttpApiDetail>(
    apiEndpointKeys.aws.apigateway.http.apis.describe,
    { signal, params: { apiId } },
  );
  return res.data;
}

export const apiGatewayClient = {
  listRestApis,
  describeRestApi,
  listHttpApis,
  describeHttpApi,
};
```

- [x] **Step 4: Type-check do frontend**

```bash
docker compose exec floci-ui npx tsc --noEmit
```

Expected: sem erros.

- [x] **Step 5: Commit**

```bash
git add packages/frontend/src/api/api.ts packages/frontend/src/api/aws/apigateway.api.ts
git commit -m "feat(frontend): API Gateway endpoint keys and client"
```

---

### Task 8: Frontend — hooks react-query

**Files:**
- Create: `packages/frontend/src/api/aws/apigateway.queries.ts`

- [x] **Step 1: Criar o arquivo de hooks**

```ts
import { useQuery } from "@tanstack/react-query";
import { apiGatewayClient } from "./apigateway.api";

export const apiGatewayQueryKeys = {
  restList: ["apigateway", "rest", "list"] as const,
  restDetail: (id: string | null) => ["apigateway", "rest", "detail", id] as const,
  httpList: ["apigateway", "http", "list"] as const,
  httpDetail: (id: string | null) => ["apigateway", "http", "detail", id] as const,
};

export function useRestApisQuery(enabled = true) {
  return useQuery({
    queryKey: apiGatewayQueryKeys.restList,
    queryFn: ({ signal }) => apiGatewayClient.listRestApis(signal),
    enabled,
  });
}

export function useRestApiDetailQuery(id: string | null) {
  return useQuery({
    queryKey: apiGatewayQueryKeys.restDetail(id),
    queryFn: ({ signal }) => apiGatewayClient.describeRestApi(id!, signal),
    enabled: Boolean(id),
  });
}

export function useHttpApisQuery(enabled = true) {
  return useQuery({
    queryKey: apiGatewayQueryKeys.httpList,
    queryFn: ({ signal }) => apiGatewayClient.listHttpApis(signal),
    enabled,
  });
}

export function useHttpApiDetailQuery(id: string | null) {
  return useQuery({
    queryKey: apiGatewayQueryKeys.httpDetail(id),
    queryFn: ({ signal }) => apiGatewayClient.describeHttpApi(id!, signal),
    enabled: Boolean(id),
  });
}
```

- [x] **Step 2: Type-check**

```bash
docker compose exec floci-ui npx tsc --noEmit
```

Expected: sem erros.

- [x] **Step 3: Commit**

```bash
git add packages/frontend/src/api/aws/apigateway.queries.ts
git commit -m "feat(frontend): API Gateway react-query hooks"
```

---

### Task 9: Frontend — página `ApiGatewayPage`

**Files:**
- Create: `packages/frontend/src/features/apigateway/ApiGatewayPage.tsx`

- [x] **Step 1: Criar o componente**

```tsx
import { useEffect, useMemo, useState } from "react";
import { Info, Network, RefreshCw, Route as RouteIcon } from "lucide-react";
import { EmptyState } from "@/components/EmptyState";
import {
  useRestApiDetailQuery,
  useRestApisQuery,
  useHttpApiDetailQuery,
  useHttpApisQuery,
} from "@/api/aws/apigateway.queries";
import { useCloudStatusQuery } from "@/features/cloud-console/cloudConsoleHome.queries";
import { runtimeEndpointLabel } from "@/features/cloud-console/cloudConsoleHome.utils";
import type {
  HttpApiSummary,
  RestApiSummary,
} from "@/api/aws/apigateway.api";

type Tab = "rest" | "http";

function ApiListItem({
  id,
  name,
  meta,
  active,
  onSelect,
}: {
  id: string;
  name: string;
  meta: string;
  active: boolean;
  onSelect: () => void;
}) {
  return (
    <button
      className={`list-item ${active ? "active" : ""}`}
      onClick={onSelect}
      type="button"
    >
      <strong>{name || id}</strong>
      <span>{meta}</span>
    </button>
  );
}

function Meta({ label, value }: { label: string; value: string }) {
  return (
    <div className="meta-row">
      <span className="meta-label">{label}</span>
      <span className="meta-value">{value}</span>
    </div>
  );
}

function CopyButton({ value }: { value: string }) {
  const [copied, setCopied] = useState(false);
  return (
    <button
      className="button"
      type="button"
      onClick={() => {
        void navigator.clipboard.writeText(value);
        setCopied(true);
        setTimeout(() => setCopied(false), 1500);
      }}
    >
      {copied ? "Copied" : "Copy URL"}
    </button>
  );
}

function RestApiDetailPanel({ apiId, baseEndpoint }: { apiId: string; baseEndpoint: string }) {
  const detailQuery = useRestApiDetailQuery(apiId);
  const detail = detailQuery.data;

  if (detailQuery.isLoading) {
    return <div className="empty compact"><p>Loading API...</p></div>;
  }
  if (detailQuery.isError || !detail) {
    return (
      <EmptyState
        icon={RouteIcon}
        title="Cannot load REST API"
        description="The Floci endpoint did not return details for this API."
      />
    );
  }

  return (
    <div className="content">
      <div className="page-title" style={{ marginBottom: 16 }}>
        <RouteIcon size={18} color="var(--accent)" />
        <h2>{detail.name || detail.id}</h2>
      </div>

      <div className="grid two">
        <div className="widget">
          <div className="widget-header">
            <Network size={13} color="var(--accent)" />
            <h3>API</h3>
          </div>
          <div className="widget-body">
            <div className="meta-grid">
              <Meta label="Id" value={detail.id} />
              <Meta label="Endpoint types" value={detail.endpointTypes.join(", ") || "-"} />
              <Meta label="Created" value={detail.createdDate ?? "-"} />
            </div>
          </div>
        </div>
      </div>

      <div className="table-panel section-space">
        <div className="widget-header">
          <h3>Resources ({detail.resources.length})</h3>
        </div>
        {detail.resources.length === 0 ? (
          <div className="empty compact"><p>No resources.</p></div>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Path</th>
                <th>Method</th>
                <th>Auth</th>
                <th>Integration</th>
              </tr>
            </thead>
            <tbody>
              {detail.resources.flatMap((resource) =>
                resource.methods.length === 0 ? (
                  <tr key={resource.id}>
                    <td className="mono">{resource.path}</td>
                    <td colSpan={3}>-</td>
                  </tr>
                ) : (
                  resource.methods.map((method) => (
                    <tr key={`${resource.id}-${method.httpMethod}`}>
                      <td className="mono">{resource.path}</td>
                      <td>{method.httpMethod}</td>
                      <td>{method.authorizationType}</td>
                      <td className="mono">
                        {method.integrationType ?? "-"}
                        {method.integrationUri ? ` → ${method.integrationUri}` : ""}
                      </td>
                    </tr>
                  ))
                ),
              )}
            </tbody>
          </table>
        )}
      </div>

      <div className="table-panel section-space">
        <div className="widget-header">
          <h3>Stages ({detail.stages.length})</h3>
        </div>
        {detail.stages.length === 0 ? (
          <div className="empty compact"><p>No stages.</p></div>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Stage</th>
                <th>Execute URL</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {detail.stages.map((stage) => {
                const executeUrl = `${baseEndpoint}/execute-api/${apiId}/${stage.stageName}`;
                return (
                  <tr key={stage.stageName}>
                    <td>{stage.stageName}</td>
                    <td className="mono">{executeUrl}</td>
                    <td><CopyButton value={executeUrl} /></td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

function HttpApiDetailPanel({ apiId, baseEndpoint }: { apiId: string; baseEndpoint: string }) {
  const detailQuery = useHttpApiDetailQuery(apiId);
  const detail = detailQuery.data;

  if (detailQuery.isLoading) {
    return <div className="empty compact"><p>Loading API...</p></div>;
  }
  if (detailQuery.isError || !detail) {
    return (
      <EmptyState
        icon={RouteIcon}
        title="Cannot load HTTP API"
        description="The Floci endpoint did not return details for this API."
      />
    );
  }

  return (
    <div className="content">
      <div className="page-title" style={{ marginBottom: 16 }}>
        <RouteIcon size={18} color="var(--accent)" />
        <h2>{detail.name || detail.id}</h2>
      </div>

      <div className="grid two">
        <div className="widget">
          <div className="widget-header">
            <Network size={13} color="var(--accent)" />
            <h3>API</h3>
          </div>
          <div className="widget-body">
            <div className="meta-grid">
              <Meta label="Id" value={detail.id} />
              <Meta label="Protocol" value={detail.protocolType} />
              <Meta label="Created" value={detail.createdDate ?? "-"} />
            </div>
          </div>
        </div>
      </div>

      <div className="table-panel section-space">
        <div className="widget-header">
          <h3>Routes ({detail.routes.length})</h3>
        </div>
        {detail.routes.length === 0 ? (
          <div className="empty compact"><p>No routes.</p></div>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Route</th>
                <th>Auth</th>
                <th>Integration</th>
              </tr>
            </thead>
            <tbody>
              {detail.routes.map((route) => (
                <tr key={route.routeKey}>
                  <td className="mono">{route.routeKey}</td>
                  <td>{route.authorizationType}</td>
                  <td className="mono">
                    {route.integrationType ?? "-"}
                    {route.integrationUri ? ` → ${route.integrationUri}` : ""}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="table-panel section-space">
        <div className="widget-header">
          <h3>Stages ({detail.stages.length})</h3>
        </div>
        {detail.stages.length === 0 ? (
          <div className="empty compact"><p>No stages.</p></div>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Stage</th>
                <th>Auto-deploy</th>
                <th>Execute URL</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {detail.stages.map((stage) => {
                const executeUrl = `${baseEndpoint}/execute-api/${apiId}/${stage.stageName}`;
                return (
                  <tr key={stage.stageName}>
                    <td>{stage.stageName}</td>
                    <td>{stage.autoDeploy ? "Enabled" : "Disabled"}</td>
                    <td className="mono">{executeUrl}</td>
                    <td><CopyButton value={executeUrl} /></td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

export function ApiGatewayPage() {
  const [tab, setTab] = useState<Tab>("http");
  const statusQuery = useCloudStatusQuery("aws");
  const baseEndpoint = runtimeEndpointLabel("aws", statusQuery.data);

  const restApisQuery = useRestApisQuery(tab === "rest");
  const httpApisQuery = useHttpApisQuery(tab === "http");
  const restApis = useMemo<RestApiSummary[]>(() => restApisQuery.data ?? [], [restApisQuery.data]);
  const httpApis = useMemo<HttpApiSummary[]>(() => httpApisQuery.data ?? [], [httpApisQuery.data]);

  const [selectedRestId, setSelectedRestId] = useState<string | null>(null);
  const [selectedHttpId, setSelectedHttpId] = useState<string | null>(null);

  useEffect(() => {
    if (tab === "rest" && !selectedRestId && restApis[0]) setSelectedRestId(restApis[0].id);
  }, [tab, restApis, selectedRestId]);
  useEffect(() => {
    if (tab === "http" && !selectedHttpId && httpApis[0]) setSelectedHttpId(httpApis[0].id);
  }, [tab, httpApis, selectedHttpId]);

  const activeList = tab === "rest" ? restApis : httpApis;
  const isLoading = tab === "rest" ? restApisQuery.isLoading : httpApisQuery.isLoading;
  const isError = tab === "rest" ? restApisQuery.isError : httpApisQuery.isError;
  const selectedId = tab === "rest" ? selectedRestId : selectedHttpId;

  return (
    <>
      <div className="page-header">
        <div className="page-title">
          <h2>API Gateway</h2>
          <span className="info-link">
            <Info size={11} />
            REST and HTTP APIs
          </span>
        </div>
        <button
          className="button"
          onClick={() => {
            void restApisQuery.refetch();
            void httpApisQuery.refetch();
          }}
          type="button"
        >
          <RefreshCw size={13} />
          Refresh
        </button>
      </div>

      <div className="console-service-grid" style={{ marginBottom: 16, gridTemplateColumns: "repeat(2, max-content)" }}>
        <button
          className={`button ${tab === "http" ? "primary" : ""}`}
          type="button"
          onClick={() => setTab("http")}
        >
          HTTP APIs (v2)
        </button>
        <button
          className={`button ${tab === "rest" ? "primary" : ""}`}
          type="button"
          onClick={() => setTab("rest")}
        >
          REST APIs (v1)
        </button>
      </div>

      <div className="split">
        <aside className="list-pane">
          <div className="widget-header">
            <RouteIcon size={13} color="var(--text-2)" />
            <h3>APIs ({activeList.length})</h3>
          </div>

          {isLoading ? (
            <div className="empty compact"><p>Loading APIs...</p></div>
          ) : isError ? (
            <EmptyState
              icon={RouteIcon}
              title="Cannot load APIs"
              description="API Gateway did not respond from the Floci endpoint."
            />
          ) : activeList.length === 0 ? (
            <EmptyState
              icon={RouteIcon}
              title={tab === "rest" ? "No REST APIs" : "No HTTP APIs"}
              description="Create one with the AWS CLI against the Floci endpoint — this console is read-only."
            />
          ) : (
            activeList.map((api) =>
              tab === "rest" ? (
                <ApiListItem
                  key={api.id}
                  id={api.id}
                  name={api.name}
                  meta={api.id}
                  active={selectedId === api.id}
                  onSelect={() => setSelectedRestId(api.id)}
                />
              ) : (
                <ApiListItem
                  key={api.id}
                  id={api.id}
                  name={api.name}
                  meta={(api as HttpApiSummary).protocolType}
                  active={selectedId === api.id}
                  onSelect={() => setSelectedHttpId(api.id)}
                />
              ),
            )
          )}
        </aside>

        <section className="detail-pane">
          {!selectedId ? (
            <EmptyState
              icon={RouteIcon}
              title="Select an API"
              description="Choose an API to inspect its routes, integrations and stages."
            />
          ) : tab === "rest" ? (
            <RestApiDetailPanel apiId={selectedId} baseEndpoint={baseEndpoint} />
          ) : (
            <HttpApiDetailPanel apiId={selectedId} baseEndpoint={baseEndpoint} />
          )}
        </section>
      </div>
    </>
  );
}
```

- [x] **Step 2: Type-check**

```bash
docker compose exec floci-ui npx tsc --noEmit
```

Expected: sem erros. Se `tsc` reclamar do cast `(api as HttpApiSummary).protocolType` por incompatibilidade de união, ajustar substituindo o `.map` único por dois `.map` separados (um por aba) — mais verboso, mas elimina o cast.

- [x] **Step 3: Commit**

```bash
git add packages/frontend/src/features/apigateway/ApiGatewayPage.tsx
git commit -m "feat(frontend): API Gateway page with REST/HTTP tabs and list+detail split"
```

---

### Task 10: Registrar rota e card na home

**Files:**
- Modify: `packages/frontend/src/App.tsx`
- Modify: `packages/frontend/src/features/cloud-console/useCloudConsoleHomeData.ts`

- [x] **Step 1: Registrar a rota em `App.tsx`**

Arquivo completo após a mudança:

```tsx
import {BrowserRouter, Navigate, Route, Routes} from 'react-router-dom'
import {Layout} from '@/components/Layout'
import {SecretsManagerPage} from '@/features/secretsmanager/SecretsManagerPage'
import {ApiGatewayPage} from '@/features/apigateway/ApiGatewayPage'
import {CloudExplorerPage} from '@/pages/CloudExplorerPage'
import {CloudConsoleHomePage} from '@/pages/CloudConsoleHomePage'

export default function App() {
    return (
        <BrowserRouter>
            <Routes>
                <Route element={<Layout/>}>
                    <Route index element={<Navigate to="/console/aws" replace/>}/>
                    <Route path="/dashboard" element={<Navigate to="/console/aws" replace/>}/>
                    <Route path="/console" element={<Navigate to="/console/aws" replace/>}/>
                    <Route path="/console/:cloud" element={<CloudConsoleHomePage/>}/>
                    <Route path="/cloud-explorer" element={<Navigate to="/cloud-explorer/aws/storage" replace/>}/>
                    <Route path="/cloud-explorer/:cloud/:service" element={<CloudExplorerPage/>}/>
                    <Route path="/secretsmanager" element={<SecretsManagerPage/>}/>
                    <Route path="/apigateway" element={<ApiGatewayPage/>}/>
                    <Route path="*" element={<Navigate to="/console/aws" replace/>}/>
                </Route>
            </Routes>
        </BrowserRouter>
    )
}
```

- [x] **Step 2: Adicionar o card na home**

Em `packages/frontend/src/features/cloud-console/useCloudConsoleHomeData.ts`:

1. Atualizar o import de ícones (adicionar `Route`):

```ts
import {Cpu, Database, KeyRound, MessageSquare, Route, Table2, Zap} from 'lucide-react'
```

2. Adicionar os imports dos hooks de API Gateway, junto ao import de `useSecretsQuery`:

```ts
import {useSecretsQuery} from '@/api/aws/secretsmanager.queries'
import {useHttpApisQuery, useRestApisQuery} from '@/api/aws/apigateway.queries'
```

3. Dentro de `useCloudConsoleHomeData`, junto às outras queries (logo após `secretsQuery`):

```ts
    const secretsQuery = useSecretsQuery(cloud === 'aws' && status?.runtime === 'reachable')
    const restApisQuery = useRestApisQuery(cloud === 'aws' && status?.runtime === 'reachable')
    const httpApisQuery = useHttpApisQuery(cloud === 'aws' && status?.runtime === 'reachable')
```

4. No array `serviceCards`, adicionar um novo bloco logo após o bloco `secretsmanager` (ainda dentro do `...(cloud === 'aws' ? [...] : [])`, como um segundo elemento do mesmo array condicional):

```ts
            ...(cloud === 'aws' ? [
              {
                id: 'secretsmanager',
                label: 'Secrets Manager',
                status: 'available' as const,
                count: secretsQuery.data?.length,
                icon: KeyRound,
                route: '/secretsmanager',
                meta: serviceMetaLabel(status, secretsQuery.isLoading, 'secrets'),
              },
              {
                id: 'apigateway',
                label: 'API Gateway',
                status: 'available' as const,
                count: (restApisQuery.data?.length ?? 0) + (httpApisQuery.data?.length ?? 0),
                icon: Route,
                route: '/apigateway',
                meta: serviceMetaLabel(status, restApisQuery.isLoading || httpApisQuery.isLoading, 'apis'),
              },
            ] : []),
```

5. Adicionar as quatro novas queries ao array de dependências do `useMemo` (junto às já existentes):

```ts
    [
        databaseResourcesQuery.data,
        databaseResourcesQuery.isLoading,
        cloud,
        k8sResourcesQuery.data,
        k8sResourcesQuery.isLoading,
        httpApisQuery.data,
        httpApisQuery.isLoading,
        restApisQuery.data,
        restApisQuery.isLoading,
        secretsQuery.data,
        secretsQuery.isLoading,
        servicesQuery.data,
        status,
        storageResourcesQuery.data,
        storageResourcesQuery.isLoading,
    ],
```

- [x] **Step 3: Type-check**

```bash
docker compose exec floci-ui npx tsc --noEmit
```

Expected: sem erros.

- [x] **Step 4: Commit**

```bash
git add packages/frontend/src/App.tsx packages/frontend/src/features/cloud-console/useCloudConsoleHomeData.ts
git commit -m "feat(frontend): register /apigateway route and home grid card"
```

---

### Task 11: Verificação final de ponta a ponta

**Files:** nenhum (só validação)

- [x] **Step 1: Suite completa do backend**

```bash
docker compose exec floci-api bun test
```

Expected: todos os testes passam, incluindo os 7 novos de `apigateway.test.ts`.

- [x] **Step 2: Type-check dos dois pacotes**

```bash
docker compose exec floci-api bun run type-check
docker compose exec floci-ui npx tsc --noEmit
```

Expected: sem erros em nenhum dos dois.

- [x] **Step 3: Confirmar que o frontend recompilou sem erro (hot-reload via Vite)**

```bash
docker compose logs --tail=30 floci-ui
```

Expected: sem stack trace/erro de compilação após os commits dos Tasks 9-10 (Vite já deve ter recarregado os módulos automaticamente, já que `./packages/frontend/src` é volume-mounted).

- [x] **Step 4: Smoke test do back-end real (gateway `f48573af47` desta sessão)**

```bash
curl -s http://localhost:4500/api/apigateway/http/apis/f48573af47
```

(porta 4500 é o proxy do Vite dev server para `/api/*` → `floci-api:4501` — confirma que o frontend e o backend conversam pelo mesmo caminho que o navegador vai usar)

Expected: mesmo corpo já validado no Task 6 (`routes`/`stages` da API real).

- [x] **Step 5: Checagem manual no navegador (não automatizável neste ambiente)**

Abrir `http://localhost:4500/console/aws` e confirmar:
- Card "API Gateway" aparece no grid, com contagem ≥ 1 (a HTTP API `f48573af47`).
- Clicar no card abre `/apigateway`.
- Aba "HTTP APIs (v2)" já vem selecionada, lista mostra `floci-contratoquery-autorizacoes`.
- Clicar nela mostra a rota `GET /autorizacoes`, integration `HTTP_PROXY → http://host.docker.internal:8081/api/autorizacoes`, stage `local` com Execute URL `http://localhost:4566/execute-api/f48573af47/local` e botão "Copy URL" funcional.
- Aba "REST APIs (v1)" mostra o empty state (nenhuma REST API v1 foi criada nesta sessão).

Reportar ao usuário o resultado dessa checagem manual antes de considerar a tarefa concluída.

---

### Status final (2026-06-16)

Implementação concluída e validada end-to-end; checagem manual no navegador confirmada pelo usuário.

Notas:
- **Task 0 não executado**: `docs/floci-aws-local/floci-ui/` não é um clone git real (sem `.git`), então os "commits" de cada task neste plano nunca existiram como histórico — só o código ficou presente na pasta.
- **Bug de infra corrigido fora do escopo do plano**: `docker-compose.yml` não montava `./packages/api/tsconfig.json` no serviço `floci-api` (só montava `src`), o que fazia `bun run type-check` exibir o help do `tsc` em vez de checar o código. Corrigido adicionando o volume mount, no mesmo padrão já usado pelo `floci-ui`.
- Suite de testes backend: `7 pass / 0 fail` em `apigateway.test.ts`; suite completa `143 pass / 1 fail` (falha pré-existente e não relacionada — timeout de status do runtime GCP).
- Type-check backend e frontend: sem erros.
- Smoke test via curl (portas 4501 direta e 4500 via proxy do Vite): lista HTTP APIs, detalhe de `f48573af47` e lista REST v1 vazia, todos conforme esperado.
