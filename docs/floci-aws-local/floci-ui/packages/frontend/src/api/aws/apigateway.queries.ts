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
