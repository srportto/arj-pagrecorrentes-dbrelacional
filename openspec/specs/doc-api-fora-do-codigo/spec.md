# doc-api-fora-do-codigo Specification

## Purpose

Manter a documentação do contrato de API fora do código de produção das aplicações de `apps/`:
sem anotações springdoc/OpenAPI, sem dependência de biblioteca de geração de doc, sem teste que
gere o artefato — preservando o tratamento explícito de caminho desconhecido, que existe por si e
não por causa da ferramenta de documentação.

## Requirements

### Requirement: Código de aplicação não carrega documentação de contrato de API

O código de produção (`src/main`) das aplicações em `apps/` MUST NOT conter anotações cuja única
função seja documentar o contrato de API para consumo externo — `@Operation`, `@ApiResponse`,
`@ApiResponses`, `@Tag`, `@Schema`, `@Parameter` e demais de `io.swagger.v3.oas.annotations.*`.

A documentação do contrato pertence ao **gateway**. Manter uma segunda fonte dentro do código cria
duas descrições concorrentes do mesmo contrato, sem garantia de convergência, e faz cada ajuste de
texto exigir recompilação e reimplantação da aplicação.

Anotações que determinam **comportamento** — `@GetMapping`, `@PostMapping`, `@RequestParam`
(inclusive `required` e `defaultValue`), `@PathVariable`, `@RequestBody`, `@Valid` e as
constraints de Bean Validation — não são documentação e SHALL permanecer.

#### Scenario: Controller sem anotação de documentação

- **WHEN** um `@RestController` de qualquer app de `apps/` é inspecionado
- **THEN** não há nenhuma anotação de `io.swagger.v3.oas.annotations.*` na classe, em seus métodos
  ou em seus parâmetros

#### Scenario: Anotação de comportamento preservada

- **WHEN** um parâmetro de endpoint declara `@RequestParam(required = false, defaultValue = "20")`
- **THEN** a anotação permanece integralmente, incluindo `required` e `defaultValue`
- **AND** apenas o `@Parameter` de descrição que a acompanhava é removido

### Requirement: Aplicações não dependem de biblioteca de geração de documentação de API

Os `pom.xml` das aplicações em `apps/` MUST NOT declarar `springdoc-openapi-starter-webmvc-ui`
nem qualquer outra dependência cuja finalidade seja gerar ou servir documentação de API, e
MUST NOT declarar propriedade de versão para ela.

#### Scenario: Nenhum pom referencia springdoc

- **WHEN** os cinco `pom.xml` de `apps/` são inspecionados
- **THEN** nenhum declara dependência `org.springdoc`
- **AND** nenhum declara a propriedade `springdoc.version`

#### Scenario: Endpoints de documentação não existem em runtime

- **WHEN** uma aplicação REST de `apps/` está no ar e recebe `GET /v3/api-docs` ou
  `GET /swagger-ui/index.html`
- **THEN** a resposta SHALL ser 404
- **AND** a resposta SHALL usar o formato `LayoutErrosApiResponse`, não uma página de erro do
  container

### Requirement: Caminho desconhecido responde 404, independentemente da geração de doc

Cada aplicação REST de `apps/` SHALL tratar `NoResourceFoundException` explicitamente, devolvendo
**404**. Esse tratamento SHALL existir por si — sem ele, um caminho desconhecido cai no handler
catch-all de `Exception` e vira **500**, o que é defeito de contrato.

O `@ExceptionHandler(NoResourceFoundException.class)` MUST NOT ser removido junto com as
dependências de documentação de API, e seu javadoc MUST NOT justificar sua existência por
`/v3/api-docs` ou por qualquer ferramenta de geração de doc.

#### Scenario: Caminho inexistente devolve 404

- **WHEN** uma requisição chega a um caminho não mapeado por nenhum controller
- **THEN** a resposta SHALL ser 404, não 500

#### Scenario: Justificativa do handler não cita ferramenta de documentação

- **WHEN** o javadoc do `@ExceptionHandler(NoResourceFoundException.class)` é lido
- **THEN** ele descreve o comportamento de 404 para caminho desconhecido
- **AND** não menciona springdoc, `/v3/api-docs` nem Swagger

### Requirement: Nenhum teste depende da geração de documentação de API

O `src/test` das aplicações MUST NOT conter teste cujo objeto seja gerar ou validar o artefato de
documentação de API (`openapi.json`, `/v3/api-docs`).

#### Scenario: Testes de geração de OpenAPI removidos

- **WHEN** o `src/test` de `contratocommand` e `contratoquery` é inspecionado
- **THEN** a classe `OpenApiGenerationTest` não existe em nenhum dos dois
