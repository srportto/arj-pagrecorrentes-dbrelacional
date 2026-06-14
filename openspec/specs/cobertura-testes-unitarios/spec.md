# cobertura-testes-unitarios

## Purpose

Garantir cobertura de testes unitários para as classes com lógica de negócio das aplicações `contratocommand` e `contratoquery`, com medição automatizada via JaCoCo, gate mínimo de cobertura no build e exclusões padronizadas de código não unit-testável ou gerado.

## Requirements

### Requirement: Medição de cobertura via JaCoCo nas duas aplicações
As aplicações `contratocommand` e `contratoquery` SHALL incluir o `jacoco-maven-plugin` configurado para instrumentar os testes (`prepare-agent`) e gerar relatório de cobertura (`report`) em `target/site/jacoco`.

#### Scenario: Relatório de cobertura é gerado no build
- **WHEN** `mvn verify` (ou `mvn test` seguido de `jacoco:report`) é executado em qualquer das duas aplicações
- **THEN** um relatório de cobertura SHALL ser gerado em `target/site/jacoco/index.html`

### Requirement: Gate mínimo de cobertura no build
O build de cada aplicação SHALL falhar quando a cobertura de linhas das classes não-excluídas ficar abaixo do mínimo configurado (0.80) via `jacoco:check`.

#### Scenario: Build falha abaixo do mínimo
- **WHEN** a cobertura de linhas das classes não-excluídas está abaixo de 0.80 e `mvn verify` é executado
- **THEN** o goal `jacoco:check` SHALL falhar o build

#### Scenario: Build passa no mínimo ou acima
- **WHEN** a cobertura de linhas das classes não-excluídas é maior ou igual a 0.80
- **THEN** o `jacoco:check` SHALL passar e o build concluir com sucesso

### Requirement: Exclusões padronizadas da medição de cobertura
A medição de cobertura SHALL desconsiderar código não unit-testável ou gerado: classes `*Application` (entry point Spring), interfaces `*Repository` (JPA) e código gerado pelo Lombok (anotado com `@lombok.Generated` via `lombok.config` com `lombok.addLombokGeneratedAnnotation = true`).

#### Scenario: Código Lombok não conta para a cobertura
- **WHEN** o relatório de cobertura é gerado
- **THEN** getters/setters/`equals`/`hashCode`/builders gerados pelo Lombok NÃO SHALL ser contabilizados como linhas a cobrir

#### Scenario: Classes de entry point e repositórios são excluídas
- **WHEN** o `jacoco:check` avalia a cobertura
- **THEN** as classes `*Application` e as interfaces `*Repository` NÃO SHALL ser consideradas no cálculo do gate

### Requirement: Cobertura de testes unitários das classes com lógica
As classes com lógica de negócio de ambas as aplicações — orquestradores, validators, regras, use cases, services, mappers, utilitários, enums, converters, DTOs com mapeamento (`from()`) e `ApiExceptionHandler` — SHALL possuir testes unitários (JUnit 5 + Mockito), cobrindo seus caminhos principais e de erro, sem dependência de banco de dados.

#### Scenario: Seleção de produto no orquestrador é coberta
- **WHEN** os testes do orquestrador de contratação/cancelamento são executados
- **THEN** SHALL existir cenário que verifica a seleção do primeiro service suportado e o lançamento de `BusinessException` quando nenhum produto é suportado

#### Scenario: Regras de negócio têm cenários de violação
- **WHEN** os testes das regras (`DataFimVigenciaInvalida`, `ValorLimiteContrato`, `MetadadoRule`, `TipoProdutoCancelamento`) são executados
- **THEN** cada regra SHALL ter cenário de caso válido e cenário de violação

#### Scenario: Mapeamento de DTO e enum é coberto
- **WHEN** os testes dos DTOs com `from()` e dos enums com lógica (`StatusAutorizacao`, `TipoProduto`) são executados
- **THEN** SHALL cobrir o mapeamento de `status` para nome do enum, o parsing de `metadado` e os ramos de valor inválido/desconhecido

#### Scenario: Suíte unitária roda sem banco de dados
- **WHEN** `mvn test` é executado sem PostgreSQL disponível
- **THEN** todos os testes unitários SHALL passar (os `@SpringBootTest` de contexto permanecem `@Disabled`)
