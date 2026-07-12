# Delta: coesao-contratocommand (change: contratacao-context)

## MODIFIED Requirements

### Requirement: DTOs de request são imutáveis e não carregam estado interno

Os DTOs de entrada SHALL ser records imutáveis e MUST conter apenas os dados recebidos no body da requisição. Valores derivados durante o processamento ou recebidos fora do body (ex.: o tipo de produto lido do banco, o id da autorização vindo do path, o produto vindo do header no cancelamento, a jornada vinda do header na contratação) MUST ser passados como parâmetros/contexto explícitos entre as camadas — na contratação via `ContratacaoContext`, no cancelamento via `CancelamentoContext` —, e não injetados nem mutados dentro do DTO de request. O `CriarAutorizacaoRequest` MUST NOT conter campo derivado de header, e o `AutorizacaoController` MUST NOT reconstruir o record do body para carregar dado de contexto.

#### Scenario: Request de cancelamento é imutável

- **WHEN** o DTO de request de cancelamento é instanciado
- **THEN** ele é um record sem setters e nenhum campo é reatribuído após a construção

#### Scenario: Validação de divergência de produto sem mutar o request

- **WHEN** a regra que compara o produto do header com o produto da autorização é executada
- **THEN** ambos os valores chegam como parâmetros/contexto explícitos da validação, não como campos previamente injetados no DTO de request

#### Scenario: Request de criação representa exclusivamente o body

- **WHEN** o record `CriarAutorizacaoRequest` é inspecionado
- **THEN** ele contém apenas os campos do body da requisição, sem componente `tipoJornada` nem qualquer outro dado derivado de header

#### Scenario: Jornada viaja em contexto imutável na contratação

- **WHEN** o `AutorizacaoController` processa `POST /api/autorizacoes`
- **THEN** a jornada resolvida do header e o request do body são embrulhados em um `ContratacaoContext` imutável (via `ContratacaoContext.doRequest`), sem recriar nem copiar o record do body, e o use case, o validator e as rules de contratação recebem esse contexto

### Requirement: Organização por feature na aplicação e domínio puro

Os componentes de cada operação SHALL residir agrupados por feature na camada de aplicação: `application/contratacao` (use case de criação, `ContratacaoContext`, `ContratacaoValidator`, `ContratacaoRule` e suas rules, incluindo `ProdutoSuportado`) e `application/cancelamento` (use case de cancelamento, `CancelamentoContext`, `CancelamentoValidator`, `CancelamentoRule` e suas rules). Componentes compartilhados por mais de uma feature (não específicos de uma operação) SHALL residir na raiz de `application/`, sem subpacote próprio; `AutorizacaoRepository` e `AutorizacaoMapper` SHALL residir em `application/` (não em `application/autorizacao`). O `AutorizacaoMapper`, por ser compartilhado na raiz de `application/`, MUST NOT depender dos tipos de contexto internos das features (`ContratacaoContext`, `CancelamentoContext`); dados de contexto necessários ao mapeamento MUST chegar como parâmetros de origem explícitos. O pacote `application/autorizacao` MUST NOT existir. Os pacotes `application/services`, `application/enabledproduct`, `application/autorizacao/usecases` e `domain/services` MUST NOT existir. Nenhuma classe da camada de domínio (`domain/`) MUST importar `entrypoint` ou `application`, nem usar estereótipos/anotações Spring — o domínio contém apenas `entities`, `enums`, `converters` e `utilities`. O framework de validação (`shared/validationsetup`) permanece inalterado. Entre os beans Spring-gerenciados de `application/`, os orquestradores de regra de negócio por operação (`ContratacaoValidator`, `CancelamentoValidator`, `CriarAutorizacaoUseCase`, `CancelarAutorizacaoUseCase`) SHALL usar o estereótipo `@Service`; as rules individuais (implementações de `ContratacaoRule`/`CancelamentoRule`) SHALL usar `@Component`.

#### Scenario: Árvore de pacotes organizada por feature

- **WHEN** a árvore de pacotes da aplicação `contratocommand` é inspecionada
- **THEN** use case, validator, rules e contexto de cada operação estão sob `application/{contratacao,cancelamento}`, o repository e o mapper estão soltos na raiz de `application/`, e não existem os pacotes `application/autorizacao`, `application/services`, `application/enabledproduct`, `application/autorizacao/usecases` e `domain/services`

#### Scenario: Domínio sem dependências de borda ou framework

- **WHEN** as classes sob `domain/` são inspecionadas
- **THEN** nenhuma importa pacotes de `entrypoint` ou `application` e nenhuma usa anotações Spring (`@Component`, `@Service`, etc.)

#### Scenario: Comportamento preservado após a reorganização

- **WHEN** a suíte de testes é executada após a mudança de pacotes
- **THEN** todos os testes passam e os contratos REST (endpoints, headers, códigos HTTP e mensagens) permanecem os mesmos

#### Scenario: Orquestradores usam @Service

- **WHEN** as classes `ContratacaoValidator`, `CancelamentoValidator`, `CriarAutorizacaoUseCase` e `CancelarAutorizacaoUseCase` são inspecionadas
- **THEN** todas estão anotadas com `@Service`

#### Scenario: Rules usam @Component

- **WHEN** as implementações de `ContratacaoRule` e `CancelamentoRule` (`DataFimVigenciaInvalida`, `MetadadoRule`, `ValorLimiteContrato`, `ProdutoSuportado`, `TipoProdutoCancelamento`, `ProdutoSuportadoCancelamento`) são inspecionadas
- **THEN** todas estão anotadas com `@Component`, não `@Service`

#### Scenario: Mapper compartilhado neutro em relação às features

- **WHEN** o `AutorizacaoMapper` é inspecionado
- **THEN** ele não importa `ContratacaoContext` nem `CancelamentoContext`; a jornada necessária ao `@AfterMapping` chega como parâmetro de origem explícito (`toDomain(dados, tipoJornada)`)
