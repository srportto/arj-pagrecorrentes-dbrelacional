---
name: api-rest-design
description: Use ao projetar API REST, modelar recursos, escrever OpenAPI 3.1, decidir versionamento, paginação, HATEOAS, error handling com RFC 9457 Problem Details, ou revisar o contrato HTTP de uma API Java/Spring Boot. Gatilhos - "desenhar API", "OpenAPI", "swagger", "versionar endpoint", "RFC 9457", "Problem Details", "HATEOAS". Uso: agent `projetista-api` ou invocação manual via `/api-rest-design`; não deve ser carregada proativamente pela sessão principal.
---

# API REST Design (Java/Spring Boot)

## Visão geral

Guia de design de APIs REST aplicadas ao stack Java/Spring Boot deste catálogo. Cobre desde
modelagem de recursos e versionamento até o contrato HTTP concreto (status, Problem Details RFC 9457,
paginação, HATEOAS, validação de borda).

**Quando NÃO usar:** para implementar controllers (`@RestController`), use `arquitetura-limpa-java`
(camada e padrão de DTOs). Para validar a API gerada (testes de contrato, mocks), use
`revisao-de-codigo-java` ou o agent `java-especialista`. Para design de microsserviços (borda entre
serviços), use `arquitetura-limpa-java` (seção DDD) — esta skill é só o **contrato HTTP** de um
único serviço.

## Workflow de design

1. **Analise o domínio** — requisitos de negócio, modelos de dados, necessidades dos clientes.
2. **Modele os recursos** — identifique recursos, relacionamentos e operações antes de escrever
   qualquer linha de OpenAPI.
3. **Defina endpoints** — URI patterns, métodos HTTP, schemas de request/response (seção
   "Convenções REST" abaixo como checklist).
4. **Especifique o contrato** — escreva o `openapi.yaml` (3.1); valide com
   `npx @redocly/cli lint openapi.yaml`.
5. **Moque e verifique** — `npx @stoplight/prism-cli mock openapi.yaml` antes de implementar.
6. **Planeje a evolução** — versionamento, deprecation, política de breaking changes.

## Convenções REST (aplicadas a este catálogo)

### Response Envelope

Todos os endpoints retornam um envelope consistente — opcional, mas útil quando a API é consumida por
múltiplos clientes que precisam de um ponto único de metadados (timestamp, errorCode). Em sucesso,
`data` é preenchido e `error` é `null`; em falha, o inverso — `error` traz `code`/`message`/`details`.

```json
{
  "success": false,
  "data": null,
  "error": { "code": "ORDER_NOT_FOUND", "message": "Order with id 123 not found", "details": [] },
  "timestamp": "2026-04-13T10:00:00Z"
}
```

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    boolean success,
    T data,
    ApiError error,
    Instant timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }
    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, new ApiError(code, message, List.of()), Instant.now());
    }
}

public record ApiError(String code, String message, List<String> details) {}
```

> **Alternativa:** Problem Details (RFC 9457) via `ProblemDetail` nativo do Boot 4.x — ver seção
> dedicada abaixo. Escolha **um** dos dois; misturar os dois causa inconsistência.

### HTTP Status Mapping

| Cenário | Status |
|---------|--------|
| GET — encontrado | 200 |
| POST — recurso criado | 201 |
| PUT/PATCH — atualizado | 200 |
| DELETE — deletado | 204 (sem corpo) |
| Falha de validação (formato) | 400 |
| Não autenticado | 401 |
| Não autorizado | 403 |
| Não encontrado | 404 |
| Conflito (duplicado, otimista) | 409 |
| Regra de negócio violada | 422 |
| Erro técnico inesperado | 500 |

> **Validação vs regra de negócio:** 400 é **formato** errado (campo vazio, `email` mal-formado),
> sempre via Bean Validation (`@Valid`, ver `arquitetura-limpa-java` mapa de erros). 422 é
> **regra de negócio** violada (`BusinessException`) — formato ok, valor não faz sentido no domínio.

### Convenções de URL

- **Plural para recursos**: `/orders`, `/users`, `/products`.
- **Kebab-case** para multi-palavra: `/order-items`, nunca `/orderItems`.
- **Versionamento em path**: `/api/v1/orders` — preferir route nativo com API versioning (Spring
  Boot 4) a duplicar controllers por versão.
- **Recursos aninhados no máximo 2 níveis**: `/orders/{id}/items` ✅,
  `/orders/{id}/items/{itemId}/notes` ❌.
- **IDs como UUID** na URL, nunca inteiros auto-incremento (vazam volume e permitem enumeração).

### Versionamento de API (nativo no Boot 4)

Spring Boot 4 / Framework 7 roteia por versão nativamente — sem `@RequestMapping` com prefixo manual
por controller:

```yaml
spring:
  mvc:
    apiversion:
      use:
        path-segment: 1
      supported: [1.0, 1.1, 2.0]
      default: 1.0
```

## Paginação

### Padrão de payload

```json
{ "success": true, "data": { "content": [...], "page": 0, "size": 20, "totalElements": 150, "totalPages": 8, "last": false } }
```

Query params: `?page=0&size=20&sort=createdAt,desc`

### Limite o tamanho da página

```yaml
spring:
  data:
    web:
      pageable:
        default-page-size: 20
        max-page-size: 100
```

### Quando usar cursor em vez de offset

| Caso | Use |
|---|---|
| UI com "próxima página" e "página anterior", dataset pequeno-médio | Offset (`page`/`size`) — simples, suporta saltar para página N |
| Feed infinito, dataset grande, alta concorrência de inserts | Cursor (`?cursor=<opaco>`) — estável quando itens são inseridos no meio da lista; offset fica inconsistente |
| Export de relatórios | Cursor — não há "fim" previsível |

## Problem Details — RFC 9457

Spring Boot 4.x tem suporte nativo a RFC 9457 via `ProblemDetail` — é o padrão IETF recomendado
para o payload de erro, em vez de um envelope customizado. Ative com `spring.mvc.problemdetails.enabled: true`
no `application.yaml`. Shape da resposta:

```json
{
  "type": "https://api.example.com/errors/order-not-found",
  "title": "Order Not Found",
  "status": 404,
  "detail": "No order found with id: 550e8400-e29b-41d4-a716-446655440000",
  "instance": "/api/v1/orders/550e8400-e29b-41d4-a716-446655440000",
  "errorCode": "ORDER_NOT_FOUND",
  "timestamp": "2026-04-13T10:00:00Z"
}
```

Handler global:

```java
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        pd.setType(URI.create("https://api.example.com/errors/" + ex.getCode()));
        pd.setTitle("Business rule violation");
        pd.setInstance(URI.create(req.getRequestURI()));
        pd.setProperty("errorCode", ex.getCode());
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
```

`BusinessException`, `ApplicationException` e `@Valid` (Bean Validation) são todos tratados no mesmo
handler central em `shared/`. Veja o mapa completo em `arquitetura-limpa-java` (seção "Mapa de
erros e onde lançar").

## HATEOAS (hypermedia)

Use quando a API precisa ser descobrível — clientes navegam pelos relacionamentos via links
incluídos nas respostas, sem hardcode de URL.

```json
{
  "id": "550e8400-...",
  "status": "PENDING",
  "customerId": "abc-123",
  "_links": {
    "self":    { "href": "/api/v1/orders/550e8400-..." },
    "approve": { "href": "/api/v1/orders/550e8400-.../approve" },
    "items":   { "href": "/api/v1/orders/550e8400-.../items" }
  }
}
```

**Quando usar:** APIs públicas com clientes de longa duração (mobile, parceiros B2B). **Quando
evitar:** APIs internas entre microsserviços, CRUD simples, integrações máquina-a-máquina — preferem
contrato explícito e estável.

## OpenAPI 3.1 (especificação)

A fonte da verdade do contrato. Gere o `openapi.yaml` **antes** de implementar o controller; o
controller é uma consequência do contrato, não o contrário.

```yaml
openapi: 3.1.0
info:
  title: Orders API
  version: 1.0.0
paths:
  /api/v1/orders/{id}:
    get:
      summary: Get order by id
      parameters:
        - in: path
          name: id
          required: true
          schema: { type: string, format: uuid }
      responses:
        '200':
          description: Order found
          content:
            application/json:
              schema: { $ref: '#/components/schemas/Order' }
        '404':
          description: Not found
components:
  schemas:
    Order:
      type: object
      required: [id, status, customerId]
      properties:
        id: { type: string, format: uuid }
        status: { type: string, enum: [PENDING, APPROVED, CANCELLED] }
        customerId: { type: string, format: uuid }
```

Em vez de escrever DTO e controller à mão, gere-os a partir do `openapi.yaml` com
`openapi-generator-maven-plugin` — o contrato vira a fonte única de verdade.

## Validação de borda — Bean Validation

Toda entrada do cliente passa por `@Valid` no DTO de request; o handler global traduz
`MethodArgumentNotValidException` em `ProblemDetail` 400. Anotações vão **no record de request**,
nunca na entidade JPA:

```java
public record CriarProdutoRequest(
    @NotBlank @Size(max = 200) String nome,
    @NotNull @DecimalMin(value = "0.01") BigDecimal preco,
    @NotNull @Min(0) Integer estoque
) {}

@PostMapping
public ResponseEntity<ProdutoResponse> criar(@RequestBody @Valid CriarProdutoRequest request) {
    // 400 via @Valid se formato errado; 422 via BusinessException se regra falhar
    return ResponseEntity.status(201).body(mapper.paraResposta(service.criar(mapper.paraEntidade(request))));
}
```

## Tradeoffs comuns (decidir antes de implementar)

| Decisão | Opção A | Opção B | Quando escolher A | Quando escolher B |
|---|---|---|---|---|
| Envelope de resposta | Customizado (`{success, data, error, ...}`) | Problem Details (RFC 9457) | API interna, cliente único, quer timestamp em toda resposta | API pública/padrão IETF, múltiplos clientes, evolução a longo prazo |
| Versionamento | Em path (`/api/v1`) | Em header (`Accept-Version`) | Cacheable, fácil de debugar (`curl /api/v1`) | "URLs limpas", múltiplas versões ativas simultaneamente |
| Paginação | Offset (`page`/`size`) | Cursor (`?cursor=`) | UI com páginas numeradas, dataset pequeno | Feed infinito, dataset grande, inserts concorrentes |
| Identificador | UUID | Long auto-incremento | Distribuído, sem enumeração | Humano-legível, debugging fácil |
| Documentação | OpenAPI manual | Anotações Spring (`@Operation`) | Fonte de verdade versionada, gera SDK | Documentação "viva" só no backend, sem cliente gerado |
| Validação | Bean Validation (`@Valid`) | Schema custom no service | Padrão JSR-380, mensagens i18n | Lógica muito específica que anotações não expressam |

## Quem aplica o quê

| Situação | Quem | Skill |
|---|---|---|
| Desenhar API nova, modelar recursos | sessão principal | esta skill |
| Implementar controller e DTOs | session principal ou `java-construtor` | `arquitetura-limpa-java` |
| Auditar contrato de API existente | agent `java-especialista` | esta skill + `revisao-de-codigo-java` |
| Definir estratégia de microsserviço (fronteira entre serviços) | sessão principal | `arquitetura-limpa-java` (seção DDD) |
