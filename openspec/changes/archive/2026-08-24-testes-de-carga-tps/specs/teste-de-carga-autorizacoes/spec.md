## ADDED Requirements

### Requirement: Cenários de carga isolados por serviço
A capability SHALL prover cenários de carga isolados que exercitam separadamente o
`contratocommand` (criação, cancelamento e decisão de autorização) e o `contratoquery`
(consulta/listagem), sem depender um do outro, antes de qualquer cenário composto.

#### Scenario: Cenário isolado de escrita mede TPS do contratocommand
- **WHEN** o cenário de carga do `contratocommand` é executado isoladamente
- **THEN** o resultado reporta TPS sustentado de criação, cancelamento e decisão de
  autorização, sem tráfego direcionado ao `contratoquery` durante a mesma execução

#### Scenario: Cenário isolado de leitura mede TPS do contratoquery
- **WHEN** o cenário de carga do `contratoquery` é executado isoladamente
- **THEN** o resultado reporta TPS sustentado de `GET /api/autorizacoes`, sem tráfego
  direcionado ao `contratocommand` durante a mesma execução

### Requirement: Cenário de jornada composta
A capability SHALL prover um cenário que encadeia criação, decisão e o pipeline assíncrono
de eventos completo (SNS → SQS → `autorizacaostatus-producer` → Kafka → `eventos-consumer`,
e SNS → SQS filtrada → `temporiza-autorizacao` → Valkey), para observar se o teto de um
componente muda quando todos operam sob carga simultânea.

#### Scenario: Jornada composta usa patamar fixo, não ramp-up
- **WHEN** o cenário de jornada composta é executado
- **THEN** a carga é aplicada em patamar fixo sustentado (não crescente) por um período
  suficiente para observar se o lag da fila/consumer group diverge ao longo do tempo

### Requirement: Execução em baseline sem recalibração de tetos
A primeira execução de cada cenário SHALL medir o sistema com a configuração vigente do
momento — pool HikariCP, `MAX_CONCURRENT_MESSAGES` dos listeners SQS e concorrência do
consumer Kafka não SHALL ser alterados antes da execução de baseline.

#### Scenario: Baseline reporta a configuração vigente junto com o resultado
- **WHEN** um cenário de carga é executado em modo baseline
- **THEN** o relatório de resultado registra os valores vigentes de pool HikariCP,
  `MAX_CONCURRENT_MESSAGES` e concorrência do consumer Kafka usados durante a execução

### Requirement: Critério de colapso multi-sinal
O critério de parada de um cenário síncrono SHALL considerar, em conjunto, conexões
pendentes no pool HikariCP (`hikaricp_connections_pending`), p99 de latência e taxa de erro
classificada — não SHALL depender exclusivamente de taxa de erro HTTP bruta.

#### Scenario: Pool pendente sustentado é sinal de colapso mesmo sem erro HTTP
- **WHEN** `hikaricp_connections_pending` permanece maior que zero de forma sustentada
  durante um cenário síncrono
- **THEN** o executor de carga sinaliza degradação, independentemente de a taxa de erro
  HTTP ainda estar em zero

### Requirement: Classificação de erro em três categorias
Toda falha observada durante um cenário de carga SHALL ser classificada em uma de três
categorias antes de contar para o critério de abort: esperado-por-design (ex.: HTTP 409 de
idempotência), esperado-mas-monitorado (ex.: `CannotAcquireLockException` do expurgo de
partição) ou colapso real (timeout, 5xx genérico, conexão recusada). Somente falhas
classificadas como colapso real SHALL contar para os kill switches automáticos.

#### Scenario: HTTP 409 de idempotência não dispara abort
- **WHEN** o cenário de carga gera HTTP 409 por violação da regra de idempotência de
  `idAutorizacaoEmpresa`
- **THEN** essa ocorrência é registrada como esperado-por-design
- **AND** não conta para o critério de abort automático

#### Scenario: Timeout ou 5xx genérico conta para o critério de abort
- **WHEN** o cenário de carga observa timeout ou HTTP 5xx não classificado como
  esperado-por-design ou esperado-mas-monitorado
- **THEN** essa ocorrência é classificada como colapso real
- **AND** conta para os kill switches automáticos definidos na capability

### Requirement: Kill switches automáticos em múltiplos níveis
A capability SHALL interromper automaticamente a execução de um cenário, sem depender de
intervenção manual, ao ultrapassar limiares definidos em pelo menos três níveis: aplicação
(pool esgotado, p99, taxa de erro real), fila/lag (profundidade de fila SQS, lag de consumer
group Kafka) e host (saturação de CPU/memória).

#### Scenario: Execução é abortada automaticamente ao ultrapassar limiar de aplicação
- **WHEN** a taxa de erro real (colapso real, conforme classificação de três categorias)
  ultrapassa o limiar configurado durante um cenário em execução
- **THEN** o executor de carga interrompe a execução automaticamente, sem exigir ação manual

#### Scenario: Execução é abortada automaticamente ao ultrapassar limiar de lag assíncrono
- **WHEN** a profundidade de fila SQS ou o lag do consumer group Kafka ultrapassa o limiar
  configurado durante o cenário de jornada composta
- **THEN** o executor de carga interrompe a execução automaticamente, sem exigir ação manual

### Requirement: Isolamento de recursos por container como pré-requisito
A execução de qualquer cenário desta capability SHALL ocorrer contra um ambiente
`docker-compose` cujos serviços tenham limites explícitos de CPU e memória
(`deploy.resources.limits` ou equivalente) — execução contra um ambiente sem esses limites
NÃO SHALL ser considerada uma medição válida de capacidade do serviço.

#### Scenario: Execução sem limites de recursos é sinalizada como inválida
- **WHEN** um cenário de carga é executado contra um ambiente cujos containers não têm
  `deploy.resources.limits` configurado
- **THEN** o relatório de resultado sinaliza explicitamente que a medição reflete a
  capacidade do host, não do serviço, e não deve ser tratada como TPS de referência

### Requirement: Convenção de massa de teste identificável e limpável
Toda autorização criada por um cenário de carga SHALL usar `idAutorizacaoEmpresa` prefixado
com `LOADTEST-` (formato `LOADTEST-{timestamp}-{seq}`), permitindo identificação e limpeza
posterior independente de o cenário ter terminado por conclusão normal, abort automático ou
falha.

#### Scenario: Massa de teste é identificável após execução interrompida
- **WHEN** um cenário de carga é interrompido por um kill switch antes do fim planejado
- **THEN** as autorizações já criadas continuam identificáveis pelo prefixo `LOADTEST-` em
  `idAutorizacaoEmpresa`, incluindo as que foram movidas para a faixa de expurgo (900-999)

### Requirement: Escopo de ambiente local sem extrapolação para produção
Os resultados produzidos por esta capability SHALL ser reportados com a ressalva explícita
de que a execução ocorre apenas contra o ambiente local (`docker-compose` + Floci) e não
SHALL ser apresentados como capacidade de um ambiente de produção.

#### Scenario: Relatório de resultado inclui a ressalva de ambiente local
- **WHEN** um cenário de carga desta capability produz um relatório de resultado
- **THEN** o relatório inclui explicitamente que a medição foi feita em ambiente local e não
  representa capacidade de produção
