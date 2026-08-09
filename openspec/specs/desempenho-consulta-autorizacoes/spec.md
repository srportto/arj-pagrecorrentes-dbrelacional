# desempenho-consulta-autorizacoes Specification

## Purpose
TBD - created by archiving change blindar-superficie-leitura. Update Purpose after archive.
## Requirements
### Requirement: Cobertura de índice para a listagem de autorizações

A listagem SHALL ter índice cobrindo as colunas usadas como filtro e ordenação padrão —
`id_unico_conta_contratante`, `status` e `data_hora_inclusao`. A listagem NÃO SHALL depender de
varredura sequencial das partições, uma vez que a chave de particionamento
(`id_particao_conta`) não coincide com a coluna de filtro.

#### Scenario: Plano de execução usa índice

- **WHEN** `EXPLAIN ANALYZE` é executado sobre a consulta de listagem filtrando por
  `id_unico_conta_contratante` em volume representativo
- **THEN** o plano SHALL indicar uso de índice para o filtro
- **AND** NÃO SHALL indicar varredura sequencial das partições

#### Scenario: Índice criado sem bloquear escrita

- **WHEN** o índice é criado em ambiente com tráfego
- **THEN** a criação SHALL usar `CONCURRENTLY`, sem bloquear escritas na tabela

#### Scenario: Índice cobre também o filtro por status

- **WHEN** a listagem é executada com filtro de `status` além da conta contratante
- **THEN** o plano de execução SHALL continuar utilizando o índice

### Requirement: Comparação de plano antes e depois

A criação do índice SHALL ser acompanhada de registro do plano de execução antes e depois, em
volume representativo, comprovando a mudança. Um índice cujo plano não demonstre melhoria NÃO
SHALL ser mantido, por adicionar custo de escrita sem contrapartida de leitura.

#### Scenario: Baseline registrado antes da criação

- **WHEN** a mudança é implementada
- **THEN** o plano de execução anterior à criação do índice SHALL estar registrado

#### Scenario: Melhoria comprovada após a criação

- **WHEN** o plano posterior é comparado ao baseline
- **THEN** SHALL haver evidência de redução de custo ou mudança do método de acesso
- **AND** não havendo, o índice SHALL ser revisto antes de ser mantido

### Requirement: Consultas de leitura declaradas como somente leitura

Os serviços de consulta do `arj-contratoquery` SHALL declarar suas operações como transações
somente leitura (`@Transactional(readOnly = true)`), tornando a intenção explícita na camada de
aplicação em vez de depender exclusivamente da configuração `read-only` do pool de conexões.

#### Scenario: Services de leitura declaram readOnly

- **WHEN** `ListarAutorizacoesService` e `ConsultarAutorizacaoService` são inspecionados
- **THEN** ambos SHALL declarar transação somente leitura

#### Scenario: Comportamento das consultas preservado

- **WHEN** os dois endpoints de leitura são exercitados após a mudança
- **THEN** SHALL retornar os mesmos resultados de antes, sem alteração de contrato

