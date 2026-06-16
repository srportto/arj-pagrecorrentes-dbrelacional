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
