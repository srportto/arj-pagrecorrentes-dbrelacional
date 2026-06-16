# Implementation Notes

## What Changed

- Added the first unified Cloud Proxy API under `/api/clouds`.
- Added SPI contracts for clouds, services, schemas, fields, actions, table columns, resource queries, and normalized resources.
- Added a `CloudAdapterRegistry` to resolve a cloud/service pair to a concrete adapter.
- Added AWS Storage Adapter for S3 buckets using the existing Floci AWS Core S3 client.
- Added Azure Storage Adapter for Blob containers using `FLOCI_AZURE_ENDPOINT`.
- Added dynamic frontend types and components for cloud selection, service selection, schema-driven creation forms, normalized resource tables, and resource inspection.
- Added `CloudExplorerPage` at `/cloud-explorer`.
- Added GCP only as a coming-soon placeholder.
- Updated README with the new multi-cloud direction, architecture principles, initial scope, future scope, and architecture image.
- Added basic tests for adapter registry behavior and schema route behavior.

## Current Scope

- AWS S3 buckets are exposed as normalized `storage` resources with type `bucket`.
- Azure Blob containers are exposed as normalized `storage` resources with type `container`.
- The dynamic UI renders only the metadata exposed by the Cloud Proxy API.

## Pending

- Add real Floci-AZ lifecycle documentation once the runtime setup is finalized.
- Add object/blob-level browsing to the unified storage view.
- Add update/tag/versioning actions to the unified storage contract.
- Add adapter capability flags for partially supported runtimes.
- Add route-level error normalization for adapter failures.
- Add visual regression or browser smoke tests for `/cloud-explorer`.
- Add future adapters through the SPI without changing the UI contract.
