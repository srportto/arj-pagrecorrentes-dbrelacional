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
          ) : tab === "rest" ? (
            restApis.map((api) => (
              <ApiListItem
                key={api.id}
                id={api.id}
                name={api.name}
                meta={api.id}
                active={selectedId === api.id}
                onSelect={() => setSelectedRestId(api.id)}
              />
            ))
          ) : (
            httpApis.map((api) => (
              <ApiListItem
                key={api.id}
                id={api.id}
                name={api.name}
                meta={api.protocolType}
                active={selectedId === api.id}
                onSelect={() => setSelectedHttpId(api.id)}
              />
            ))
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
