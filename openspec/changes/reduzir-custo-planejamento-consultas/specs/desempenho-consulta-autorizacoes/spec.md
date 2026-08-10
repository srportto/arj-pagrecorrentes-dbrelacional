## ADDED Requirements

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
