## ADDED Requirements

### Requirement: VPC nomeada vpc-arj

O módulo `networking` SHALL provisionar uma VPC com CIDR `10.0.0.0/16`, com
`enable_dns_support` e `enable_dns_hostnames` habilitados, e com a tag `Name` igual
a `vpc-arj`.

#### Scenario: VPC criada com nome e CIDR corretos
- **WHEN** o Terraform do módulo `networking` é aplicado
- **THEN** existe uma VPC com CIDR `10.0.0.0/16` e tag `Name = vpc-arj`
- **AND** resolução e hostnames DNS estão habilitados na VPC

### Requirement: Seis subnets distribuídas em três AZs

O módulo `networking` SHALL criar exatamente 6 subnets na VPC: 3 públicas (`/24`,
uma por AZ) e 3 privadas (`/20`, uma por AZ), distribuídas nas AZs `a`, `b` e `c` da
região. O módulo SHALL NOT criar um tier separado de subnets de banco de dados nesta
fase.

#### Scenario: Subnets públicas criadas
- **WHEN** o módulo é aplicado
- **THEN** existem 3 subnets públicas com CIDRs `10.0.48.0/24`, `10.0.49.0/24` e
  `10.0.50.0/24`, cada uma em uma AZ distinta (`a`, `b`, `c`)

#### Scenario: Subnets privadas criadas
- **WHEN** o módulo é aplicado
- **THEN** existem 3 subnets privadas com CIDRs `10.0.0.0/20`, `10.0.16.0/20` e
  `10.0.32.0/20`, cada uma em uma AZ distinta (`a`, `b`, `c`)

#### Scenario: Sem tier de databases
- **WHEN** o módulo é aplicado
- **THEN** o total de subnets criadas é exatamente 6 (nenhuma subnet de `databases`)

### Requirement: Conectividade de entrada e saída

O módulo `networking` SHALL criar um Internet Gateway anexado à VPC e 3 NAT Gateways
(um por AZ), cada NAT Gateway com seu próprio Elastic IP e alocado em uma subnet
pública.

#### Scenario: Internet Gateway anexado
- **WHEN** o módulo é aplicado
- **THEN** existe um Internet Gateway associado à VPC `vpc-arj`

#### Scenario: NAT Gateway por AZ
- **WHEN** o módulo é aplicado
- **THEN** existem 3 NAT Gateways, cada um com um Elastic IP dedicado e residindo na
  subnet pública da sua AZ

### Requirement: Roteamento público e privado

O módulo `networking` SHALL prover uma route table pública com rota `0.0.0.0/0` para o
Internet Gateway, associada às 3 subnets públicas, e 3 route tables privadas (uma por
AZ) com rota `0.0.0.0/0` para o NAT Gateway da respectiva AZ, cada uma associada à
subnet privada correspondente.

#### Scenario: Rota pública para a internet
- **WHEN** o módulo é aplicado
- **THEN** as 3 subnets públicas estão associadas a uma route table cuja rota padrão
  aponta para o Internet Gateway

#### Scenario: Rota privada isolada por AZ
- **WHEN** o módulo é aplicado
- **THEN** cada subnet privada está associada a uma route table cuja rota padrão aponta
  para o NAT Gateway da mesma AZ

### Requirement: Publicação de identificadores da rede

O módulo `networking` SHALL publicar o ID da VPC e os IDs das 6 subnets no SSM
Parameter Store sob o prefixo `/vpc-arj/vpc/`, e SHALL expô-los também como outputs do
módulo, para consumo pelos módulos de compute.

#### Scenario: Parâmetros SSM criados
- **WHEN** o módulo é aplicado
- **THEN** existem parâmetros SSM `/vpc-arj/vpc/vpc_id` e um parâmetro por subnet
  (pública e privada) com os respectivos IDs

#### Scenario: Outputs disponíveis para composição
- **WHEN** outro módulo consome o módulo `networking`
- **THEN** o `vpc_id` e as listas de IDs de subnets públicas e privadas estão
  acessíveis via outputs

### Requirement: Security group base

O módulo `networking` SHALL disponibilizar ao menos um security group base na VPC para
ser reutilizado pelos módulos de compute (cluster e serviços).

#### Scenario: Security group base exposto
- **WHEN** o módulo é aplicado
- **THEN** existe um security group base na VPC exposto como output do módulo
