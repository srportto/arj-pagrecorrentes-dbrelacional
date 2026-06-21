## ADDED Requirements

### Requirement: Cancelamento executa dentro de transação

O fluxo de cancelamento, que persiste a mudança de status e transfere a autorização entre partições via `delete + insert`, SHALL executar dentro de um único limite transacional. A anotação `@Transactional` MUST estar no método público de entrada do use case (`execute`), nunca em método privado ou auto-invocado, de modo que falha na reinserção faça rollback do delete.

#### Scenario: Reinserção falha após o delete

- **WHEN** o cancelamento remove a autorização da partição antiga e a reinserção na nova partição falha
- **THEN** a transação faz rollback e a autorização original permanece persistida (nenhuma linha é perdida)

#### Scenario: Anotação transacional efetiva

- **WHEN** o código do use case de cancelamento é inspecionado
- **THEN** `@Transactional` está no método público `execute` e não há `@Transactional` em método privado

### Requirement: Erros inesperados usam exceções do próprio projeto

Falhas inesperadas de sistema SHALL ser sinalizadas com `ApplicationException` (mapeada para HTTP 500) e violações de regra de negócio com `BusinessException` (HTTP 422). O código MUST NOT lançar exceções de framework (ex.: `org.springframework.context.ApplicationContextException`) para representar erros de aplicação.

#### Scenario: Falha inesperada ao buscar autorização

- **WHEN** ocorre um erro inesperado ao obter a autorização por id/partição no cancelamento
- **THEN** é lançada `ApplicationException` e a resposta HTTP é 500

#### Scenario: Autorização não encontrada

- **WHEN** a autorização informada no cancelamento não existe
- **THEN** é lançada `BusinessException` e a resposta HTTP é 422

### Requirement: Status da autorização tem o enum como fonte da verdade

O valor numérico de `status` SHALL derivar do enum `StatusAutorizacao` em vez de números mágicos espalhados no código. Criação MUST gravar o status correspondente a "ativa" conforme o enum, e cancelamento MUST gravar `CANCELADA`. Nenhum literal numérico de status pode contradizer o enum.

#### Scenario: Criação grava status coerente com o enum

- **WHEN** uma autorização é criada com sucesso
- **THEN** o `status` persistido corresponde à constante do enum `StatusAutorizacao` definida para autorização ativa, e não a um literal divergente

#### Scenario: Cancelamento grava status CANCELADA

- **WHEN** uma autorização é cancelada
- **THEN** o `status` persistido corresponde a `StatusAutorizacao.CANCELADA`

### Requirement: DTOs de request são imutáveis e não carregam estado interno

Os DTOs de entrada SHALL ser records imutáveis e MUST conter apenas dados recebidos do cliente. Valores derivados durante o processamento (ex.: o tipo de produto lido do banco, o id da autorização vindo do path, o produto vindo do header) MUST ser passados como parâmetros/contexto explícitos entre as camadas, e não mutados dentro do DTO.

#### Scenario: Request de cancelamento é imutável

- **WHEN** o DTO de request de cancelamento é instanciado
- **THEN** ele é um record sem setters e nenhum campo é reatribuído após a construção

#### Scenario: Validação de divergência de produto sem mutar o request

- **WHEN** a regra que compara o produto do header com o produto da autorização é executada
- **THEN** ambos os valores chegam como parâmetros/contexto explícitos da validação, não como campos previamente injetados no DTO de request

### Requirement: Variação por produto vive em strategy fino, sem duplicação de encanamento

A persistência (`Repository`), o mapeamento request→entidade (`Mapper`) e o fluxo de cada operação (`UseCase`) sobre a entidade `Autorizacao` SHALL ser compartilhados entre produtos. Cada produto (PIX_AUTO, DDA_AUTO) MUST ser representado por uma classe de strategy fina que declara o `TipoProduto` suportado e delega ao fluxo compartilhado. Adicionar um novo produto MUST NOT exigir duplicar Mapper, Repository ou UseCases.

#### Scenario: Seleção de produto continua via orquestrador

- **WHEN** uma requisição de criação/cancelamento chega com um `tipoProduto` suportado
- **THEN** o orquestrador seleciona a strategy correspondente e o resultado é idêntico ao comportamento anterior ao refactor

#### Scenario: Produto não suportado

- **WHEN** uma requisição chega com `tipoProduto` não suportado
- **THEN** é lançada `BusinessException` (HTTP 422) com mensagem de produto não suportado

#### Scenario: Sem duplicação de componentes por produto

- **WHEN** o código da camada de aplicação é inspecionado
- **THEN** existe um único Mapper, um único Repository e use cases compartilhados para a entidade `Autorizacao`, sem cópias por produto

### Requirement: Contratos REST públicos preservados

O refactor SHALL preservar os contratos REST existentes: `POST /api/autorizacoes`, `PATCH /api/autorizacoes/{idAutorizacao}/cancelar` (com header `tipoProduto`) e o health-check. O corpo de request, os headers exigidos e os códigos HTTP MUST permanecer inalterados; a única mudança observável permitida é o valor de `status` na resposta passar a refletir o enum.

#### Scenario: Criação mantém contrato

- **WHEN** um cliente envia `POST /api/autorizacoes` com o mesmo corpo e header `tipoJornada` de antes
- **THEN** a resposta é 201 com o mesmo conjunto de campos, exceto `status` agora coerente com o enum

#### Scenario: Cancelamento mantém contrato

- **WHEN** um cliente envia `PATCH /api/autorizacoes/{id}/cancelar` com header `tipoProduto` e o mesmo corpo de antes
- **THEN** a resposta é 200 com a autorização cancelada

### Requirement: Domínio sem dead code e com anotações corretas

A entidade e seus value objects SHALL estar livres de inicialização que recebe a si própria como parâmetro, de anotações de persistência incorretas e de código morto. `inicializaCriacao` MUST operar sobre `this`; campos básicos de `@Embeddable` MUST usar `@Column` (não `@JoinColumn`); nomes de coluna MUST NOT conter espaços; classes não utilizadas (ex.: `ContratoBase`) MUST ser removidas ou efetivamente utilizadas.

#### Scenario: Inicialização opera sobre this

- **WHEN** uma autorização é inicializada para criação
- **THEN** o método de inicialização não recebe a própria autorização como parâmetro e opera sobre `this`

#### Scenario: Anotações de embeddable corretas

- **WHEN** os value objects embutidos (`IdAutorizacao`, `Cancelamento`) são inspecionados
- **THEN** seus campos básicos usam `@Column` e nenhum nome de coluna contém espaços em branco
