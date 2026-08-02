---
name: java-architecture
description: Use ao desenhar a arquitetura interna de uma aplicação Java/Spring Boot empresarial (camadas, módulos, estrutura de pacotes, padrões de injeção de dependência), ou ao revisar decisões arquiteturais (qual framework, qual versão, qual estilo de API). Consolida Spring Boot 4 + Java 25 com DDD e padrões de projeto. Gatilhos - "arquitetura Spring Boot", "design interno", "estrutura de pacotes", "Spring Security", "Spring Data JPA", "WebFlux", "testcontainers".
---

# Arquitetura de Aplicações Java (Spring Boot 4 + Java 25)

## Visão geral

Guia consolidado de arquitetura interna de aplicações **Java/Spring Boot 4** + **Java 25**:
estrutura de pacotes em camadas (`controller` / `service` / `repository` / `domain` / `dto`),
padrões de injeção de dependência, escolha de módulos Spring (Web vs WebFlux, JPA, Security,
Cache, Data Redis), e estrutura de testes (slice, integration, Testcontainers). Use esta skill ao
desenhar a arquitetura de uma aplicação nova, ao escolher entre alternativas (MVC vs reativo, JPA
vs JDBC), ou ao revisar a estrutura de pacotes existente.

**Quando NÃO usar:** para a regra de dependência entre camadas hexagonais (`entrypoint`/
`application`/`domain`/`shared`) — que é o padrão **deste** catálogo — use `arquitetura-limpa-java`.
Para aplicar um design pattern GoF, use `padroes-de-projeto-java`. Para a regra de revisão de
código, use `revisao-de-codigo-java`. Para tuning de JPA/Hibernate, use `persistencia-jpa`.

## Workflow de arquitetura

1. **Análise** — revise estrutura do projeto, dependências, configuração Spring.
2. **Design de domínio** — crie modelos seguindo DDD e Clean Architecture; **verifique as
   fronteiras antes de prosseguir** — se houver ambiguidade, resolva antes de implementar.
3. **Implementação** — construa services com boas práticas de Spring Boot seguindo arquitetura em
   camadas.
4. **Camada de dados** — otimize queries JPA, implemente repositories; rode `./mvnw verify -pl
   <modulo>` para confirmar correção. Se testes de integração falharem: reveja log SQL do
   Hibernate, ajuste queries ou mappings, re-rodar.
5. **Segurança & config** — aplique Spring Security, externalize configuração, adicione
   observabilidade; rode `./mvnw verify` para confirmar filter chain e JWT. Se falhar: cheque
   ordem do `SecurityFilterChain` bean e validação de token, re-rodar.
6. **Quality assurance** — rode `./mvnw verify` (Maven) ou `./gradlew check` (Gradle) para
   confirmar testes + cobertura ≥ 85%. Se cobertura abaixo: identifique branches não testados
   pelo relatório JaCoCo (`target/site/jacoco/index.html`), adicione casos, re-rodar.

---

# Arquitetura em camadas (estilo "clássico" / não-hexagonal)

```
@RestController        ← HTTP only. Sem lógica de negócio. Sem entidades JPA na resposta.
      ↓ DTOs
@Service               ← Toda lógica de negócio vive aqui. Orquestra repositories.
      ↓ Domain objects / Entities
@Repository            ← Acesso a dados only. Sem lógica de negócio. Retorna entities/projections.
      ↓ JPA / JDBC
Database
```

> **Diferente do hexagonal:** este é o estilo "clássico" (controller→service→repository) usado
> como referência para times que ainda não migraram para hexagonal. Para o padrão hexagonal deste
> catálogo (`entrypoint`/`application`/`domain`/`shared`), use `arquitetura-limpa-java`.

## Camada Controller

- Lida com HTTP: parsing, validação (`@Valid`), montagem de response.
- Chama **um** método de service por endpoint — sem orquestração no controller.
- **Nunca** retorna `@Entity` direto — sempre DTO de resposta.
- **Nunca** injeta `@Repository` — sempre via `@Service`.
- Tratamento de exceção via `@ControllerAdvice`, nunca `try/catch` no controller.

```java
// BOM
@PostMapping("/orders")
public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
    Order order = orderService.createOrder(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
}

// RUIM - logica de negocio no controller
@PostMapping("/orders")
public ResponseEntity<Order> createOrder(@RequestBody CreateOrderRequest request) {
    if (request.getItems().isEmpty()) throw new RuntimeException("No items");
    Order order = orderRepository.save(new Order(request));   // acesso direto ao repo
    return ResponseEntity.ok(order);                            // entity na resposta
}
```

## Camada Service

- Contém toda lógica de negócio, validação de regras e orquestração.
- `@Transactional` vive **aqui** — não em controllers nem em repositories.
- **Injeção por construtor** apenas — nunca `@Autowired` em field.
- Um service por aggregate root (`OrderService`, não `OrderAndPaymentService`).
- Retorna domain objects ou DTOs — nunca `HttpServletRequest` / `HttpServletResponse`.

## Camada Repository

- Estende `JpaRepository<Entity, ID>` ou `CrudRepository`.
- Queries custom via `@Query` ou query derivation — sem SQL raw a menos que inevitável.
- Retorna entities ou projections — nunca `Object[]` raw.
- **Sem lógica de negócio** — acesso a dados puro.

## DTOs

- **Request e Response separados** — nunca a mesma classe para os dois.
- Anotações de validação (`@NotNull`, `@Size`) **só nos Request DTOs**.
- Método factory estático `ResponseDto.from(Entity entity)` para mapeamento.
- **Use records** para DTOs imutáveis (Java 16+).

```java
// BOM
public record OrderResponse(UUID id, String status, List<LineItemResponse> items) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getStatus().name(),
            order.getItems().stream().map(LineItemResponse::from).toList());
    }
}
```

---

# Estrutura de pacotes (estilo "clássico")

```
src/main/java/com/example/app/
  config/        ← @Configuration classes (beans, security, etc.)
  controller/    ← @RestController
  service/       ← @Service
  repository/    ← @Repository
  domain/        ← entidades, value objects, regras de domínio
  dto/           ← records de request/response
  util/          ← helpers, validators, mappers
src/main/resources/
  application.yml
src/test/java/... (espelha main)
```

> **Alternativa hexagonal** (`entrypoint`/`application`/`domain`/`shared`) é o padrão deste
> catálogo. Ver `arquitetura-limpa-java`.

---

# Injeção de dependência — convenções

- **Construtor com `final`** (preferido pelo time):
  ```java
  @Service
  @RequiredArgsConstructor
  public class OrderService {
      private final OrderRepository orderRepository;
      // Lombok gera o construtor com todos os campos final
  }
  ```
- **Evite `@Autowired` em field** — dificulta teste (precisa de reflection para mockar) e esconde
  dependências.

---

# Escolha de stack Spring

| Necessidade | Módulo Spring | Observação |
|---|---|---|
| REST síncrono | `spring-boot-starter-webmvc` (Tomcat) ou `-webflux` (Netty) | Default do Boot 4 ainda é MVC. Use WebFlux só se precisar de backpressure real ou alto paralelismo I/O |
| Persistência JPA | `spring-boot-starter-data-jpa` | Padrão para PostgreSQL/MySQL. Para queries reativas: `spring-boot-starter-data-r2dbc` |
| Cache | `spring-boot-starter-cache` + provider (Caffeine, Redis) | `@Cacheable` em método de leitura. Cuidado com cache de entidade JPA (lazy) |
| Mensageria | `spring-boot-starter-kafka` | Produtor e consumer. Veja `mensageria-sqs-kafka` |
| Segurança | `spring-boot-starter-security` + `spring-boot-starter-oauth2-resource-server` | JWT. Veja `seguranca-aplicacao-java` |
| Validação | `spring-boot-starter-validation` | Bean Validation 3.0 (Jakarta) |
| Observabilidade | `spring-boot-starter-actuator` + `micrometer-registry-prometheus` | Veja `monitoramento-java` |

---

# Spring Security — JWT resource server

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/disponibilidade", "/actuator/health/**").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
            .build();
    }
}
```

Veja `seguranca-aplicacao-java` para JWT details, CORS, headers, mass assignment.

---

# Testes

## Tipos de teste

| Tipo | Anotação Spring | Velocidade | Quando usar |
|---|---|---|---|
| Unitário | (nenhuma) | Muito rápido | Lógica pura, sem Spring |
| Slice — web | `@WebMvcTest` | Rápido | Controller isolado, mocka service |
| Slice — JPA | `@DataJpaTest` | Médio | Repository, com H2/Testcontainers |
| Integração | `@SpringBootTest` | Lento | Contexto completo, smoke test |
| E2E | Testcontainers + cliente HTTP | Muito lento | Validação fim a fim |

## Testcontainers (preferido para integração com infra real)

```java
@SpringBootTest
@Testcontainers
class OrderServiceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("pedidos")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void deveProcessarPedidoQuandoDadosValidos() {
        // ...
    }
}
```

> **Mock `@MockBean` foi removido no Boot 4** — use `@MockitoBean` em vez disso.

---

# Features modernas do Java

Veja a skill dedicada `java-moderno` para records, sealed classes, pattern matching, switch
expressions, text blocks, virtual threads, `var`. Em arquitetura, os pontos mais relevantes:

- **Records** para DTOs e value objects — imutabilidade nativa.
- **Sealed types** para hierarquia de domínio finita (ex.: `Pagamento` com `Pix`/`Cartao`/`Boleto`).
- **Virtual threads** (`spring.threads.virtual.enabled: true`) para cargas I/O-bound com muita
  concorrência.

---

# Quem aplica o quê

| Situação | Quem | Skill |
|---|---|---|
| Desenhar arquitetura de aplicação Spring Boot nova | session principal | esta skill |
| Implementar camada em arquitetura hexagonal deste catálogo | session principal | `arquitetura-limpa-java` |
| Revisar estrutura de pacotes e escolhas de stack | agent `java-especialista` | `revisao-de-codigo-java` |
| Tuning de JPA/Hibernate | session principal | `persistencia-jpa` |
| Configurar segurança (JWT, CORS, headers) | session principal | `seguranca-aplicacao-java` |
| Configurar observabilidade | session principal | `monitoramento-java` |
