# desempenho-consulta-autorizacoes Specification

## Purpose

Definir como o desempenho das consultas do `arj-contratoquery` sobre a tabela particionada
`autorizacoes` é projetado e avaliado — cobertura de índice, comparação de plano antes/depois,
declaração de transação somente leitura, e a exigência de medir o custo de **planejamento**
separadamente do de execução, em volume representativo.

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

### Requirement: Custo de planejamento das consultas é medido e limitado

O desempenho das consultas sobre a tabela particionada SHALL ser avaliado considerando o custo
de **planejamento**, e não apenas o de execução.

A justificativa é empírica: com a tabela contendo 24 linhas, a listagem paginada gasta 147,6 ms
planejando contra 17,8 ms executando. O custo de planejamento é linear no número de partições
consideradas, é pago em CPU a cada chamada, e **não diminui com menos dados nem melhora com
índice** — logo, não é observável por nenhum critério baseado apenas no plano de execução.

Toda avaliação de desempenho de consulta SHALL reportar os dois tempos separadamente.

#### Scenario: Avaliação de consulta reporta planejamento e execução

- **WHEN** o desempenho de uma consulta sobre `autorizacoes` é avaliado
- **THEN** a avaliação SHALL registrar `Planning Time` e `Execution Time` separadamente
- **AND** NÃO SHALL concluir sobre o desempenho a partir do tempo de execução isolado

#### Scenario: Redução do número de partições é considerada como alavanca

- **WHEN** o custo de planejamento é identificado como dominante numa consulta
- **THEN** a análise SHALL considerar explicitamente o número de partições consideradas como
  variável de projeto, e não como constante do ambiente

### Requirement: Latência de referência por endpoint de leitura

As aplicações de leitura SHALL ter uma medida de referência de latência ponta a ponta por
endpoint, registrada e datada, de modo que regressão de desempenho seja detectável por
comparação em vez de percebida em incidente.

A medida SHALL indicar o ambiente e o volume de dados em que foi tomada — medida obtida em base
vazia não é comparável a medida de produção, e tratá-las como equivalentes é pior do que não ter
medida alguma.

#### Scenario: Medida de referência é registrada com contexto

- **WHEN** uma medida de referência de latência é registrada
- **THEN** ela SHALL indicar endpoint, ambiente, volume de dados e data
- **AND** SHALL distinguir tempo de planejamento de tempo de execução quando a consulta atingir
  a tabela particionada

#### Scenario: Conclusão sobre plano de consulta exige volume representativo

- **WHEN** se avalia a escolha de plano do PostgreSQL (custom contra genérico) ou o efeito de
  seletividade
- **THEN** a conclusão SHALL ser tomada em ambiente com volume representativo
- **AND** NÃO SHALL ser extrapolada de ambiente local com base praticamente vazia, onde
  estimativas de seletividade não têm significado

