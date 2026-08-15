---

name: arquitetura-limpa-java
description: "Reference for deciding which layer a code belongs to in a Java/Spring Boot hexagonal (ports & adapters) application — `domain` (model, port/in, port/out), `application` (use cases) and `infrastructure` (adapters) —, structuring packages, and applying DDD to decompose monoliths into bounded contexts. Use when there is doubt about layering, when reviewing layer boundaries, or when structuring a new hexagonal project. Uso: agent `java-revisor` (modo `auditoria`) ou invocação manual via `/arquitetura-limpa-java`; não deve ser carregada proativamente pela sessão principal."
license: MIT
metadata:
  author: https://github.com/srportto/srportto
  co-author: https://github.com/Jeffallan/claude-skills
  version: "2.0.0"
  domain: architecture
  triggers: onde coloco, qual camada, estrutura de pacotes, arquitetura limpa, arquitetura hexagonal, ports and adapters, porta, adaptador, bounded context, decompor monólito, hexagonal
  role: architect
  scope: code-organization
  output-format: document
  related-skills: java-architecture, design-system-architecture, criar-aplicacao-java, revisao-de-codigo-java
---
---

# Arquitetura Limpa Java (Hexagonal clássica + DDD)

## Visão geral

Referência de bolso para decidir **em qual camada um código deve viver** em uma aplicação Java/Spring
Boot que segue a **arquitetura hexagonal clássica (ports & adapters)** — `domain` / `application` /
`infrastructure` — e para aplicar **DDD** ao decompor fronteiras entre contextos (microsserviço ou
módulo).

**Quando NÃO usar:** para gerar o esqueleto de uma aplicação nova, use `criar-aplicacao-java` (que
aplica exatamente este layout). Para camadas clássicas não-hexagonais
(`controller`/`service`/`repository`), use `java-architecture`. Para mensageria, use
`mensageria-sqs-kafka`. Para persistência JPA, use `persistencia-jpa`. Para revisão de código
completa, use `revisao-de-codigo-java`.

## As três camadas e a regra de dependência

```
        ┌──────────────────── infrastructure ────────────────────┐
        │  driving adapters            driven adapters           │
        │  web/ · messaging/     persistence/ · external/        │
        │         │                          ▲                   │
        │         ▼                          │                   │
        │   ┌──────────── application (use cases) ─────────┐      │
        │   │                    │        ▲                │      │
        │   │                    ▼        │                │      │
        │   │   ┌──────────── domain ──────────────┐       │      │
        │   │   │  model/ · service/               │       │      │
        │   │   │  port/in  (o que a app oferece)  │       │      │
        │   │   │  port/out (o que a app precisa)  │       │      │
        │   │   └──────────────────────────────────┘       │      │
        │   └──────────────────────────────────────────────┘      │
        └────────────────────────────────────────────────────────┘
                     dependências apontam SEMPRE para dentro
```

- **`domain`** — Java puro. Zero `org.springframework.*`, zero `jakarta.persistence.*`, zero Jackson.
  Testável sem subir contexto Spring.
- **`application`** — implementa as `port/in` orquestrando `domain` + `port/out`. Spring é permitido
  aqui (`@Service`, `@Transactional`), mas nada de HTTP, JPA ou SDK de broker.
- **`infrastructure`** — todo detalhe de framework: controllers, listeners, entidades JPA, clientes
  HTTP, configs. **Driving adapters** (web, messaging de entrada) chamam `port/in`; **driven
  adapters** (persistence, external, messaging de saída) implementam `port/out`.
- Inversão de dependência é o coração do padrão: `domain` **declara** a interface de que precisa
  (`port/out`), `infrastructure` **implementa**. Assim a seta continua apontando para dentro mesmo
  quando o fluxo de execução vai para fora.

## Estrutura de pacotes

```
br.com.srportto.<app>/
├── domain/                        ← Java puro, sem framework
│   ├── model/                     ← entidades de negócio, value objects, agregados
│   ├── port/in/                   ← driving ports: interfaces de use case + commands
│   ├── port/out/                  ← driven ports: repositórios, gateways, publishers
│   ├── service/                   ← domain services (regra que não cabe num único agregado)
│   ├── enums/
│   └── exception/                 ← BusinessException e exceções de negócio
├── application/
│   └── usecase/                   ← @Service implementando port/in
└── infrastructure/
    ├── web/                       ← @RestController, DTOs de request/response, ApiExceptionHandler
    ├── messaging/                 ← listener SQS / consumer Kafka (in), producer (out)
    ├── persistence/               ← entidade JPA + Spring Data repo + adapter da port/out
    ├── external/                  ← clientes HTTP de outros serviços
    └── config/                    ← @Configuration, beans, properties
```

## Que classe vai em qual camada

| Tipo de classe | Camada | Exemplo |
|---|---|---|
| Modelo de negócio, value object, agregado | `domain/model/` | `Pedido`, `PedidoId`, `Money` |
| Interface de use case + command | `domain/port/in/` | `CriarPedidoUseCase`, `CriarPedidoCommand` |
| Interface de repositório/gateway/publisher | `domain/port/out/` | `PedidoRepository`, `EstoquePort` |
| Regra pura entre agregados | `domain/service/` | `CalculadoraDeFrete` |
| Enum de negócio, exceção de negócio | `domain/enums/`, `domain/exception/` | `StatusPedido`, `BusinessException` |
| Implementação do use case (orquestra) | `application/usecase/` | `CriarPedidoService` |
| Controller REST + DTOs de request/response | `infrastructure/web/` | `PedidoController`, `CriarPedidoRequest` |
| Handler global de erro (`@RestControllerAdvice`) | `infrastructure/web/` | `ApiExceptionHandler` |
| Listener SQS, consumer Kafka (driving adapter) | `infrastructure/messaging/` | `PedidoSqsListener` |
| Producer Kafka/SNS (driven adapter, implementa `port/out`) | `infrastructure/messaging/` | `KafkaEventoPublisher` |
| Entidade JPA, Spring Data repo, adapter de persistência | `infrastructure/persistence/` | `PedidoJpaEntity`, `PedidoJpaAdapter` |
| Cliente HTTP de outro serviço (implementa `port/out`) | `infrastructure/external/` | `EstoqueHttpClient` |
| `@Configuration`, beans, properties | `infrastructure/config/` | `KafkaConfig`, `ObjectMapperConfig` |

> **Entidade JPA ≠ modelo de domínio.** `PedidoJpaEntity` (com `@Entity`, `@Column`, `@Version`) vive
> em `infrastructure/persistence/`; `Pedido` (Java puro, com invariantes) vive em `domain/model/`. Um
> mapper no adapter converte um no outro. É isso que mantém o `domain` livre de `jakarta.persistence`.

## Exemplo mínimo (as quatro peças)

```java
// domain/port/in/CriarPedidoUseCase.java — driving port
public interface CriarPedidoUseCase {
    Pedido executar(CriarPedidoCommand command);
}

// domain/port/out/PedidoRepository.java — driven port (NÃO é JpaRepository)
public interface PedidoRepository {
    Pedido salvar(Pedido pedido);
    Optional<Pedido> buscarPorId(PedidoId id);
}

// application/usecase/CriarPedidoService.java — orquestra, sem conhecer JPA nem HTTP
@Service
@Transactional
public class CriarPedidoService implements CriarPedidoUseCase {
    private final PedidoRepository repository;   // port/out
    private final EstoquePort estoque;           // port/out

    @Override
    public Pedido executar(CriarPedidoCommand command) {
        estoque.reservar(command.itens());
        return repository.salvar(Pedido.criar(command.clienteId(), command.itens()));
    }
}

// infrastructure/persistence/PedidoJpaAdapter.java — driven adapter
@Component
public class PedidoJpaAdapter implements PedidoRepository {
    private final SpringDataPedidoRepository jpa;   // detalhe interno do pacote
    private final PedidoPersistenceMapper mapper;

    @Override
    public Pedido salvar(Pedido pedido) {
        return mapper.paraDominio(jpa.save(mapper.paraEntidade(pedido)));
    }
}
```

O `@RestController` (driving adapter) injeta a **porta** `CriarPedidoUseCase`, nunca a implementação
`CriarPedidoService`.

## Mapa de erros e onde lançar

| Exceção/mecanismo | HTTP | Onde lançar |
|---|---|---|
| `BusinessException` (definida em `domain/exception/`) | 422 | `domain` (regra pura) ou `application` (orquestração) |
| `ApplicationException` | 500 | `application`/`infrastructure`, falha técnica inesperada |
| `@Valid` (Bean Validation) | 422 neste monorepo | **somente** nos DTOs de `infrastructure/web/` |

O tratamento centralizado é o `ApiExceptionHandler` (`@RestControllerAdvice`) em
`infrastructure/web/` — nenhuma outra classe monta `ResponseEntity` de erro.

> **Convenção deste monorepo (decisão de 2026-08-09, D3 da change `reconciliar-contrato-spec-doc`):**
> tanto `@Valid` quanto `BusinessException` respondem **422**; a distinção formato × regra é carregada
> pelo *shape* do corpo (`LayoutErrosApiValidationsResponse` vs `LayoutErrosApiResponse`), não pelo
> status. Em projeto fora deste monorepo, o default de mercado para `@Valid` é 400.

## Anti-padrões

| # | Anti-padrão | Por que é errado | Correção |
|---|---|---|---|
| 1 | Use case injetando `JpaRepository` direto | `application` passa a depender de Spring Data; o domínio deixa de ditar o contrato | Injete a `port/out`; o `JpaRepository` fica escondido dentro do adapter |
| 2 | Entidade JPA usada como modelo de domínio | `@Entity` + setters gerados = domínio anêmico acoplado ao schema do banco | `domain/model/` puro + `*JpaEntity` no adapter + mapper entre os dois |
| 3 | Chamada HTTP (`RestClient`) dentro do use case | Detalhe de infraestrutura vazando para `application` | Declare uma `port/out` e implemente em `infrastructure/external/` |
| 4 | Lógica de negócio no controller | Regra vaza para o adapter, fica não-reutilizável e só testável via HTTP | Regra no agregado (`Pedido.adicionarItem()`); controller só traduz DTO ⇄ command |
| 5 | Entidade JPA retornada como resposta HTTP | Acopla contrato REST ao schema e expõe campo interno | DTO próprio de `infrastructure/web/` |
| 6 | Domínio anotado com `@Component`/`@Service`/`@Entity` | Domínio passa a depender do container/ORM e perde o teste isolado | Domínio sem nenhuma anotação de framework |
| 7 | Service com parâmetro `HttpServletRequest` | `application` depende de `jakarta.servlet.*` | Controller extrai o dado (`@RequestHeader`) e passa tipo simples no command |

```java
// ERRADO - infraestrutura vazando para o use case e dominio anemico
@Service
public class CriarPedidoService {
    private final PedidoJpaRepository repo;      // JPA direto, sem porta
    private final RestClient restClient;         // HTTP dentro da application

    public PedidoJpaEntity criar(CriarPedidoRequest req) {
        PedidoJpaEntity p = new PedidoJpaEntity();
        p.setStatus("PENDENTE");                 // regra fora do dominio, status como String
        restClient.post().uri("/reservar").body(req.itens()).retrieve();
        return repo.save(p);                     // devolve entidade JPA para a borda
    }
}

// CORRETO - ver "Exemplo minimo" acima: use case fala so com port/in e port/out
```

## Gotchas comuns

- Agent importa `jakarta.persistence` em `domain/` — a entidade JPA pertence a
  `infrastructure/persistence/`.
- Agent injeta `JpaRepository` no use case — use a `port/out`.
- Agent põe `@Transactional` em `domain/service` — pertence a `application/usecase`.
- Agent confunde os dois lados: `port/in` = o que a aplicação **oferece**, `port/out` = o que ela
  **precisa**.
- Agent cria domínio anêmico só com getters/setters — comportamento vive nos próprios objetos.
- Agent expõe o `SpringDataXRepository` fora de `infrastructure/persistence/` — mantenha
  package-private.
- Agent usa `@MockBean` em teste — removido no Boot 4; use `@MockitoBean`.
- Agent usa `spring-boot-starter-aop` — renomeado para `spring-boot-starter-aspectj` no Boot 4.

## Equivalência com a estrutura legada do monorepo

As cinco aplicações de `apps/` ainda usam o layout anterior
(`entrypoint`/`application`/`domain`/`shared`). A migração é trabalho à parte: **código existente
nesse layout não é defeito** até ser migrado — o alvo desta tabela é orientar a migração e impedir
que aplicação nova nasça no formato antigo.

| Layout legado | Layout de referência |
|---|---|
| `entrypoint/` (controller, DTOs) | `infrastructure/web/` |
| `entrypoint/sqs/`, `entrypoint/kafka/` | `infrastructure/messaging/` |
| `application/<contexto>/*Service` | `application/usecase/` + interface em `domain/port/in/` |
| `application/<contexto>/*Repository` (JPA) | `domain/port/out/` + `infrastructure/persistence/` |
| `domain/entities/*` (entidade JPA no domínio) | `domain/model/` (puro) + `*JpaEntity` em `infrastructure/persistence/` |
| `domain/model/`, `domain/enums/` | inalterados |
| `shared/` exceções de negócio | `domain/exception/` |
| `shared/` handler de erro, interceptadores | `infrastructure/web/` |
| `shared/config/` | `infrastructure/config/` |

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

   > Toda comunicação externa atravessa uma porta: o contrato do outro serviço entra como `port/out`,
   > e o ACL é justamente o adapter que traduz o modelo alheio para o seu `domain/model`.

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
