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
    { signal },
    { apiId },
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
    { signal },
    { apiId },
  );

  return res.data;
}

export const apiGatewayClient = {
  listRestApis,
  describeRestApi,
  listHttpApis,
  describeHttpApi,
};
