---
name: arquitetura-limpa-java
description: Use quando houver dúvida sobre em qual camada colocar um código, ao revisar fronteiras entre camadas, ao estruturar pacotes de uma aplicação Java hexagonal, ao decidir onde vive um novo componente, ou ao decompor um monólito em serviços com bounded contexts. Consolida o modelo hexagonal (entrypoint / application / domain / shared) com o mapa de DDD/microservices. Gatilhos - "onde coloco", "qual camada", "estrutura de pacotes", "arquitetura limpa", "arquitetura hexagonal", "bounded context", "decompor monólito". Uso: agent `java-revisor` (modo `auditoria`) ou invocação manual via `/arquitetura-limpa-java`; não deve ser carregada proativamente pela sessão principal.
---

# Arquitetura Limpa Java (Hexagonal + DDD)

## Visão geral

Referência de bolso para decidir **em qual camada um código deve viver** em uma aplicação Java/Spring
Boot hexagonal (`entrypoint` / `application` / `domain` / `shared`), e para aplicar **DDD** ao
decompor fronteiras entre contextos (microsserviço ou módulo).

**Quando NÃO usar:** para gerar o esqueleto de uma aplicação nova, use `criar-aplicacao-java`. Para
mensageria, use `mensageria-sqs-kafka`. Para persistência JPA, use `persistencia-jpa`. Para revisão
de código completa, use `revisao-de-codigo-java`.

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

- As setas só apontam **para dentro** — `entrypoint` depende de `application`, que depende de
  `domain`. Nunca o contrário.
- `shared` é **transversal**: qualquer camada pode depender dele (exceções, configs,
  interceptadores), mas ele não depende de nenhuma outra camada.
- **`domain` NUNCA importa Spring, Jakarta Servlet ou Jackson** (`org.springframework.*`,
  `jakarta.servlet.*`, `com.fasterxml.jackson.*`) — lógica de negócio pura, testável sem contexto
  Spring.
- **Exceção pragmática documentada:** entidades JPA em `domain/entities` levam anotações
  `jakarta.persistence.*` (`@Entity`, `@Table`, ...) — a entidade é ao mesmo tempo modelo de domínio e
  mapeamento ORM. Ainda assim, sem `org.springframework.*`/Jackson, e a regra de negócio (ex.:
  `validar()`) permanece pura dentro da própria entidade.

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
| `BusinessException` | 422 Unprocessable Entity | `domain` (regra pura) ou `application` (orquestração) | `Produto.validar()` lança quando `preco <= 0` |
| `ApplicationException` | 500 Internal Server Error | `application`/`domain`, falha técnica inesperada | falha ao serializar, erro de integração |
| `@Valid` (Bean Validation) | 400 Bad Request | **somente** em `entrypoint`, nos records de request | `ProdutoController.CriarProdutoRequest` |

O tratamento centralizado fica em `shared/` (`ApiExceptionHandler`, `@RestControllerAdvice`), que
mapeia cada exceção para o status HTTP correto — nenhuma camada monta `ResponseEntity` de erro por
conta própria fora desse handler.

## Anti-padrões

| # | Anti-padrão | Por que é errado | Correção |
|---|---|---|---|
| 1 | Lógica de negócio no controller (ex.: validar preço inline no `@PostMapping`) | Regra de negócio vaza para `entrypoint`, fica não-reutilizável e não testável sem HTTP | Regra vive em `domain` (`Produto.validar()`); controller só orquestra — ver exemplo abaixo |
| 2 | Entidade JPA retornada direto como resposta HTTP | Acopla o contrato REST ao schema do banco; expõe campos internos | DTO próprio do `entrypoint` + mapper convertem a entidade na borda |
| 3 | Service com parâmetro `HttpServletRequest` | `application` passa a depender de `jakarta.servlet.*`, detalhe do adaptador HTTP | Controller extrai o dado (`@RequestHeader`) e passa um tipo simples ao service |
| 4 | `domain` anotado com `@Component`/`@Service` | Domínio passa a depender do container Spring, deixa de ser testável isolado | Domínio puro, sem nenhuma anotação de framework — ver exemplo abaixo |
| 5 | Controller injeta `Repository` direto, pulando o service | Sem orquestração, sem tratamento de erro, sem DTO na borda | Controller depende só de `Service`; `Repository` fica encapsulado em `application/` |

```java
// #1 ERRADO - regra de negocio dentro do controller
@PostMapping
public ResponseEntity<ProdutoResponse> criar(@RequestBody CriarProdutoRequest request) {
    if (request.preco() == null || request.preco().signum() <= 0) {
        throw new BusinessException("Preco do produto deve ser maior que zero");
    }
    return ResponseEntity.ok(mapper.paraResposta(service.criar(mapper.paraEntidade(request))));
}

// #1 CORRETO - regra vive no dominio (Produto.validar()); controller so orquestra
// domain/entities/Produto.java
public void validar() {
    if (preco == null || preco.signum() <= 0) {
        throw new BusinessException("Preco do produto deve ser maior que zero");
    }
}
```

```java
// #4 ERRADO - record de dominio dependendo de Spring
package br.com.srportto.appbase.domain.model;
import org.springframework.stereotype.Component;

@Component // dominio nao deveria conhecer o container do Spring
public record Pedido(String id, BigDecimal valor) { }

// #4 CORRETO - dominio puro, sem nenhuma anotacao de framework
public record Pedido(String id, BigDecimal valor) {
    public void validar() {
        if (id == null || id.isBlank()) {
            throw new BusinessException("Pedido sem id nao pode ser processado");
        }
    }
}
```

## Gotchas comuns

- Agent importa `jakarta.persistence` em domain classes fora de `domain/entities` — domain deve ser
  framework-free.
- Agent injeta `JpaRepository` diretamente nos use cases — use as interfaces de porta de domínio.
- Agent põe `@Transactional` em domain services — pertence à camada `application`.
- Agent mistura driving e driven ports — `port/in` = o que a aplicação oferece, `port/out` = o que ela
  precisa.
- Agent cria domínio anêmico só com getters/setters — o comportamento deve viver nos próprios objetos.
- Agent usa `@MockBean` em testes — foi removido no Boot 4; use `@MockitoBean`.
- Agent usa `spring-boot-starter-aop` para proxies de porta — foi renomeado para
  `spring-boot-starter-aspectj` no Boot 4.

## Decomposição de monolito em bounded contexts (DDD aplicado)

Quando o problema deixa de ser "em qual camada" e passa a ser **"em qual serviço"**, aplique DDD antes
de partir para hexagonal:

1. **Identificar bounded contexts** — linguagem ubíqua própria por contexto (um `Pedido` em
   `contexto-vendas` não é o mesmo `Pedido` de `contexto-fulfillment`); identifique o subdomínio
   nuclear (vantagem competitiva real, fica na sua equipe) vs. subdomínios de suporte/genéricos;
   documente o context map (Shared Kernel, Customer/Supplier, Anti-Corruption Layer, Conformist).

2. **Critérios para uma nova fronteira de serviço** — antes de virar microsserviço, o candidato deve:
   ser dono **exclusivo** dos seus dados (database-per-service); ter **contrato público** versionado;
   ser **deployado independentemente**; ter **equipe dedicada** capaz de operar 24/7; tolerar
   **consistência eventual** (não vale a pena se exige ACID entre dois domínios).
   > **Regra prática:** comece com **monolito modular** e só extraia um microsserviço quando módulo,
   > release ou equipe precisarem de independência real — microsserviço prematuro é a causa #1 de
   > "distributed monolith".

3. **Communication pattern por fronteira**:

   | Relação | Padrão | Por quê |
   |---|---|---|
   | Query/command com SLA < 100 ms | Síncrono (REST/gRPC) | Coupling temporal curto é aceitável |
   | Operação cross-aggregate, demorado | **Assíncrono** (evento, fila) | Falha de um serviço não derruba o outro |
   | Replicação de dado para leitura | **Event-driven** (Kafka) | Cada lado tem sua cópia, evolui independente |
   | Tradução entre domínios legados | **Anti-Corruption Layer** | Impede vazamento de modelo antigo |

4. **Resiliência mínima por chamada síncrona entre serviços**: timeout explícito (nunca o default
   infinito do cliente HTTP), retry com budget (2-3 tentativas, backoff exponencial), circuit breaker,
   correlation ID (`X-Trace-Id`) propagado, `Idempotency-Key` em POST sujeito a reentrega (ver
   `mensageria-sqs-kafka`). Tracing distribuído: ver `monitoramento-java`.

5. **Health & readiness probe** — `/health/live` (200 se o processo está rodando; falha reinicia o
   pod) é distinto de `/health/ready` (200 só quando pode servir tráfego; falha zera réplicas, **não**
   reinicia):
   ```yaml
   livenessProbe:
     httpGet: { path: /health/live, port: 8080 }
     periodSeconds: 15
   readinessProbe:
     httpGet: { path: /health/ready, port: 8080 }
     periodSeconds: 10
   ```

## Quem aplica o quê

| Situação | Quem | Skill usada |
|---|---|---|
| Dúvida sobre em qual camada colocar uma classe | sessão principal | esta skill |
| Revisão arquitetural completa (camadas + DDD) | agent `java-revisor` (modo `auditoria`) | esta skill + `revisao-de-codigo-java` |
| Decompor monolito em microsserviços (design) | sessão principal (design, não há agent dedicado) | esta skill |
| Aplicar microsserviço novo (gerar) | agent `java-construtor` | `criar-aplicacao-java` + esta skill |
