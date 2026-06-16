import type {FieldSchema, ServiceSchema, TableColumnSchema} from './types'

const networkingColumns: TableColumnSchema[] = [
    {name: 'name',    label: 'Name'},
    {name: 'version', label: 'CIDR'},
    {name: 'status',  label: 'State'},
    {name: 'type',    label: 'Type'},
]

const networkingFilters: FieldSchema[] = [
    {name: 'search', label: 'Search', type: 'text', required: false},
]

export function awsNetworkingSchema(): ServiceSchema {
    return {
        cloud: 'aws',
        service: 'networking',
        displayName: 'Networking',
        fields: [],
        actions: ['list'],
        filters: networkingFilters,
        columns: networkingColumns,
        capabilities: {
            resourceActions: [
                {name: 'list',    label: 'VPCs',            enabled: true, status: 'available', runtimeRequired: true},
                {name: 'inspect', label: 'Inspect',          enabled: true, status: 'available', runtimeRequired: true},
                {name: 'create',  label: 'Create resources', enabled: true, status: 'available', runtimeRequired: true},
                {name: 'delete',  label: 'Delete resources', enabled: true, status: 'available', runtimeRequired: true},
            ],
        },
    }
}
