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
import {
    GetApiCommand,
    type GetApiCommandOutput,
    GetApisCommand,
    GetIntegrationsCommand,
    GetRoutesCommand,
    GetStagesCommand as GetHttpStagesCommand,
    type Api,
    type Integration as HttpIntegration,
    type Route,
} from '@aws-sdk/client-apigatewayv2'
import {apiGateway, apiGatewayV2} from '../aws'

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

app.get('/http/apis/:apiId', async (c) => {
    const apiId = c.req.param('apiId')

    let api: GetApiCommandOutput
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

export default app
