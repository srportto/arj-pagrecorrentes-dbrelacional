---
name: arquitetura-limpa-java
description: Use quando houver dúvida sobre em qual camada colocar um código, ao revisar fronteiras entre camadas, ao estruturar pacotes de uma aplicação Java hexagonal, ao decidir onde vive um novo componente, ou ao decompor um monólito em serviços com bounded contexts. Consolida o modelo hexagonal (entrypoint / application / domain / shared) com o mapa de DDD/microservices. Gatilhos - "onde coloco", "qual camada", "estrutura de pacotes", "arquitetura limpa", "arquitetura hexagonal", "bounded context", "decompor monólito".
---

# Arquitetura Limpa Java (Hexagonal + DDD)

## Visão geral

Referência de bolso para decidir **em qual camada um código deve viver** em uma aplicação Java/Spring
Boot que segue o modelo hexagonal (`entrypoint` / `application` / `domain` / `shared`) usado neste
projeto, e para aplicar **Domain-Driven Design** ao decompor fronteiras entre contextos (microsserviço
ou módulo). Use esta skill sempre que surgir dúvida sobre onde colocar uma classe nova, ao revisar um
PR para identificar violação de fronteira entre camadas, ou ao decidir onde lançar/tratar um erro.

**Quando NÃO usar:** para gerar o esqueleto de uma aplicação nova do zero, use a skill
`criar-aplicacao-java`. Para dúvidas específicas de mensageria (SQS/Kafka), use `mensageria-sqs-kafka`.
Para dúvidas de persistência JPA, use `persistencia-jpa`. Para revisão de código completa (não só
arquitetura), use `revisao-de-codigo-java`.

## A regra de dependência (hexagonal)

```
┌────────────┐        ┌─────────────┐        ┌──────────┐
│ entrypoint │ ─────▶ │ application │ ─────▶ │  domain  │
└────────────┘        └─────────────┘        └──────────┘
       │                     │                     │
       └─────────────────────┼─────────────────────┘
                              ▼
                        ┌──────────┐
                        │  shared  │   (transversal)
                        └──────────┘
```

- As setas só apontam **para dentro**: `entrypoint` depende de `application`, `application` depende de
  `domain`. Nunca o contrário — `domain` não conhece `application`, `application` não conhece
  `entrypoint`.
- `shared` é **transversal**: qualquer camada pode depender de `shared` (exceções, configs,
  interceptadores), mas `shared` não depende de nenhuma outra camada.
- **`domain` NUNCA importa Spring, Jakarta Servlet ou Jackson** (`org.springframework.*`,
  `jakarta.servlet.*`, `com.fasterxml.jackson.*`). É lógica de negócio pura, testável sem subir contexto
  Spring.
- **Exceção pragmática documentada:** entidades JPA em `domain/entities` levam anotações
  `jakarta.persistence.*` (`@Entity`, `@Table`, `@Id`, `@Column`, ...) — herdada do modelo deste projeto,
  onde a entidade é ao mesmo tempo o modelo de domínio e o mapeamento objeto-relacional. Mesmo assim,
  essas entidades continuam sem importar `org.springframework.*` nem `com.fasterxml.jackson.*`, e a regra
  de negócio (ex.: método `validar()`) permanece pura dentro da própria entidade.

## Que classe vai em qual camada

| Tipo de classe | Camada | Exemplo |
|---|---|---|
| Controller REST, DTOs de request/response | `entrypoint/` | `ProdutoController`, records de request |
| Listener SQS, consumer Kafka | `entrypoint/` (adaptador de ENTRADA) | `PedidoSqsListener`, `PedidoKafkaConsumer` |
| Service de orquestração, use case, mapper | `application/<contexto>/` | `ProdutoService`, `ProdutoMapper` |
| Repository (interface JPA) | `application/<contexto>/` | `ProdutoRepository` |
| Produtor Kafka/cliente HTTP (encapsulados em service) | `application/` (adaptador de SAÍDA) | `PublicarEventoService` |
| Record/modelo de negócio puro, regra de negócio | `domain/model/`, `domain/services/` | `Pedido`, `StatusAplicacao` |
| Entidade JPA | `domain/entities/` | `Produto`, `PedidoEntity` |
| Enum de negócio, converter | `domain/enums/`, `domain/converters/` | `TipoProduto` |
| Exceções, handler de erros, configs | `shared/` | `BusinessException`, `ApiExceptionHandler` |

## Mapa de erros e onde lançar

| Exceção/mecanismo | HTTP | Onde lançar | Exemplo |
|---|---|---|---|
| `BusinessException` | 422 Unprocessable Entity | `domain` (regra de negócio pura) ou `application` (orquestração) | `Produto.validar()` lança quando `preco <= 0`; `ProdutoService.buscarPorId` lança quando o recurso não existe |
| `ApplicationException` | 500 Internal Server Error | `application`/`domain`, para falha técnica inesperada (não é regra de negócio violada) | falha ao serializar, erro de integração, estado inconsistente que não deveria ocorrer |
| `@Valid` (Bean Validation) | 400 Bad Request | **somente** em `entrypoint` — anotações nos records de request (`@NotBlank`, `@NotNull`, `@DecimalMin`) | `ProdutoController.CriarProdutoRequest` |

O tratamento centralizado fica em `shared/` (`ApiExceptionHandler`, `@RestControllerAdvice`), que mapeia
cada exceção para o status HTTP correto — nenhuma camada deve montar `ResponseEntity` de erro por conta
própria fora desse handler.

## Anti-padrões

### 1. Lógica de negócio no controller

```java
// ERRADO - regra de negocio (validar preco) dentro do controller (entrypoint)
@PostMapping
public ResponseEntity<ProdutoResponse> criar(@RequestBody CriarProdutoRequest request) {
    if (request.preco() == null || request.preco().signum() <= 0) {
        throw new BusinessException("Preco do produto deve ser maior que zero");
    }
    Produto criado = service.criar(mapper.paraEntidade(request));
    return ResponseEntity.ok(mapper.paraResposta(criado));
}
```

```java
// CORRETO - regra de negocio vive no dominio; controller so orquestra a chamada
// domain/entities/Produto.java
public void validar() {
    if (preco == null || preco.signum() <= 0) {
        throw new BusinessException("Preco do produto deve ser maior que zero");
    }
}

// entrypoint/ProdutoController.java
@PostMapping
public ResponseEntity<ProdutoResponse> criar(@RequestBody @Valid CriarProdutoRequest request) {
    Produto criado = service.criar(mapper.paraEntidade(request)); // service chama produto.validar()
    return ResponseEntity.created(URI.create("/produtos/" + criado.getId()))
            .body(mapper.paraResposta(criado));
}
```

### 2. Entidade JPA exposta como contrato REST

```java
// ERRADO - entidade JPA (com anotacoes jakarta.persistence) e retornada direto como resposta HTTP
@GetMapping("/{id}")
public ResponseEntity<Produto> buscar(@PathVariable Long id) {
    return ResponseEntity.ok(service.buscarPorId(id)); // Produto e uma @Entity, nao um contrato REST
}
```

```java
// CORRETO - DTO proprio do entrypoint + mapper convertem a entidade antes de sair pela borda
@GetMapping("/{id}")
public ResponseEntity<ProdutoResponse> buscar(@PathVariable Long id) {
    return ResponseEntity.ok(mapper.paraResposta(service.buscarPorId(id)));
}

// entrypoint/ProdutoController.java
public record ProdutoResponse(Long id, String nome, BigDecimal preco) {}
```

### 3. Service acessando `HttpServletRequest`

```java
// ERRADO - application depende de jakarta.servlet.*, que e detalhe do adaptador HTTP (entrypoint)
@Service
public class ProdutoService {
    public Produto buscarPorId(Long id, HttpServletRequest request) {
        String origem = request.getHeader("X-Origem"); // vazamento de detalhe HTTP para application
        // ...
    }
}
```

```java
// CORRETO - o controller extrai o dado do request e passa um tipo simples para o service
// entrypoint/ProdutoController.java
@GetMapping("/{id}")
public ResponseEntity<ProdutoResponse> buscar(@PathVariable Long id,
                                               @RequestHeader("X-Origem") String origem) {
    return ResponseEntity.ok(mapper.paraResposta(service.buscarPorId(id, origem)));
}

// application/produto/ProdutoService.java
public Produto buscarPorId(Long id, String origem) {
    // recebe String, sem conhecer HttpServletRequest
}
```

### 4. `domain` importando `org.springframework.*`

```java
// ERRADO - record de dominio anotado com @Component/@Service, dependendo de Spring
package br.com.srportto.appbase.domain.model;

import org.springframework.stereotype.Component;

@Component // dominio nao deveria conhecer o container do Spring
public record Pedido(String id, BigDecimal valor) {
    public void validar() { /* ... */ }
}
```

```java
// CORRETO - dominio puro, sem nenhuma anotacao de framework
package br.com.srportto.appbase.domain.model;

// dominio puro: record imutavel com a regra de negocio do pedido
public record Pedido(String id, BigDecimal valor) {
    public void validar() {
        if (id == null || id.isBlank()) {
            throw new BusinessException("Pedido sem id nao pode ser processado");
        }
    }
}
```

### 5. Repository chamado direto do controller

```java
// ERRADO - controller (entrypoint) pula a application e fala direto com o repository
@RestController
@RequestMapping("/produtos")
public class ProdutoController {
    private final ProdutoRepository repository; // deveria depender de ProdutoService, nao do repository

    @GetMapping
    public List<Produto> listar() {
        return repository.findAll(); // sem orquestracao, sem tratamento de erro, sem DTO
    }
}
```

## Gotchas comuns

- Agent importa `jakarta.persistence` em domain classes — domain deve ser framework-free.
- Agent injeta `JpaRepository` diretamente nos use cases — use as interfaces de porta de domínio
  (`domain/port/out/...`).
- Agent põe `@Transactional` em domain services — pertence à camada `application`.
- Agent mistura driving e driven ports — `port/in` = o que a aplicação oferece, `port/out` = o que ela
  precisa.
- Agent cria domínio anêmico só com getters/setters — o comportamento deve viver nos próprios objetos
  de domínio.
- Agent usa `@MockBean` em testes — foi removido no Boot 4; use `@MockitoBean`.
- Agent usa `spring-boot-starter-aop` para proxies de porta — foi renomeado para
  `spring-boot-starter-aspectj` no Boot 4.

## Decomposição de monolito em bounded contexts (DDD aplicado)

Quando o problema deixa de ser "em qual camada" e passa a ser **"em qual serviço"** (decompor um
monólito ou desenhar uma nova fronteira entre microsserviços), aplique DDD antes de partir para
hexagonal:

### 1. Identificar bounded contexts

- **Linguagem ubíqua (ubiquitous language):** cada contexto tem seu próprio vocabulário. Um `Pedido`
  para o `contexto-vendas` não é o mesmo `Pedido` do `contexto-fulfillment` — podem até ter dados
  diferentes e regras distintas, desde que a **linguagem** dentro de cada contexto seja consistente.
- **Subdomínio nuclear (core domain):** o que gera vantagem competitiva real. Fica dentro da sua equipe,
  implementado com o melhor cuidado; o restante vira subdomínio de suporte ou genérico.
- **Context map:** documento explícito de como dois contextos se relacionam (Shared Kernel, Customer/
  Supplier, Anti-Corruption Layer, Conformist, etc.).

### 2. Critérios para decidir uma nova fronteira de serviço

Cada candidato a serviço deve validar, antes de virar microsserviço:

- [ ] É dono **exclusivo** dos seus dados (database-per-service, sem schema compartilhado).
- [ ] Tem **contrato público** claro (API versionada + documentação).
- [ ] Pode ser **deployado independentemente** dos outros.
- [ ] Tem **equipe dedicada** ou capacidade de operar 24/7 (caso contrário, começa como módulo e
  evolui).
- [ ] A **consistência eventual** é aceitável na sua fronteira (não faz sentido microsserviço em uma
  transação que precisa de ACID entre dois domínios).

> **Regra prática:** comece com **monolito modular** (módulos dentro do mesmo deploy, mesma base
> de dados) e só extraia um microsserviço quando o módulo precisar de escala, ciclo de release ou
> equipe **realmente independentes**. Microsserviço prematuro é a causa #1 de "distributed monolith".

### 3. Communication pattern por fronteira

| Relação | Padrão | Por quê |
|---|---|---|
| Query/command com SLA < 100 ms | Síncrono (REST/gRPC) | Aceitável coupling temporal curto |
| Operação cross-aggregate, demorado | **Assíncrono** (evento, fila) | Falha de um serviço não derruba o outro |
| Replicação de dado para leitura | **Event-driven** (Kafka) | Cada lado tem sua cópia, evolui independente |
| Tradução entre domínios legados | **Anti-Corruption Layer** (ACL) | Impede vazamento de modelo antigo para o novo |

### 4. Resiliência mínima por chamada externa

Toda chamada **síncrona** entre serviços precisa de:

- **Timeout** explícito (nunca o default infinito do cliente HTTP).
- **Retry** com budget — máx. 2-3 tentativas, com backoff exponencial.
- **Circuit breaker** — após N falhas, abre e devolve fallback rápido em vez de derrubar o caller.
- **Correlation ID** propagado no header (`X-Trace-Id`) para correlacionar logs entre serviços.
- **Idempotency-Key** para POST que precisa ser seguro contra reentrega (ver
  `mensageria-sqs-kafka`).

> Veja a skill `monitoramento-java` para os detalhes de tracing distribuído (OpenTelemetry) e a skill
> `mensageria-sqs-kafka` para idempotência, DLQ e DLT em fronteira assíncrona.

### 5. Health & readiness probe

- `/health/live` — 200 se o processo está rodando (liveness probe reinicia o pod se falhar).
- `/health/ready` — 200 **somente** quando o serviço pode servir tráfego (DB conectado, dependências
  críticas no ar). Readiness = 0 réplicas até voltar; **não** reinicia o pod.

```yaml
# Kubernetes - exemplo mínimo
livenessProbe:
  httpGet: { path: /health/live, port: 8080 }
  initialDelaySeconds: 10
  periodSeconds: 15
readinessProbe:
  httpGet: { path: /health/ready, port: 8080 }
  initialDelaySeconds: 5
  periodSeconds: 10
```

## Quem aplica o quê

| Situação | Quem | Skill usada |
|---|---|---|
| Dúvida sobre em qual camada colocar uma classe | sessão principal | esta skill |
| Revisão arquitetural completa (camadas + DDD) | agent `java-especialista` | esta skill + `revisao-de-codigo-java` |
| Decompor monolito em microsserviços (design) | agent `modernize` | esta skill |
| Aplicar microsserviço novo (gerar) | agent `java-construtor` | `criar-aplicacao-java` + esta skill |
