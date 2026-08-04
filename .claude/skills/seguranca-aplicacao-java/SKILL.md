---
name: seguranca-aplicacao-java
description: Use ao implementar autenticação/autorização, validar entrada, prevenir OWASP Top 10, hashing de senha, queries parametrizadas, configurar CORS/CSP, emitir/validar JWT, ou auditar dependências vulneráveis. Gatilhos - "segurança", "OWASP", "JWT", "bcrypt", "SQL injection", "XSS", "headers de segurança", "segredo hardcoded", "CVE". Uso: agents `engenheiro-seguranca`/`java-revisor`/`engenheiro-devops` ou invocação manual via `/seguranca-aplicacao-java`; não deve ser carregada proativamente pela sessão principal.
---

# Segurança de Aplicação Java

## Visão geral

Guia de segurança focado em **código de aplicação Java/Spring Boot** (não infraestrutura de nuvem,
redes ou compliance corporativo): OWASP Top 10 aplicado a Java, hashing de senha, validação de
entrada, queries parametrizadas, JWT, headers de segurança, CORS e varredura de dependências.

**Quando NÃO usar:** infraestrutura de nuvem profunda (redes, IAM, KMS) ou compliance corporativo
(SOC2, ISO27001) — use o agent `engenheiro-seguranca`. Para segredo
em log, `padrao-de-logs-java` (seção "Regras de ouro") é a fonte.

## Workflow de implementação segura

**Threat model** (superfície de ataque e ameaças: autenticação, validação, exposição de dados,
dependências) → **projete controles** (hash de senha, JWT, validação, headers) → **implemente com
defense in depth** (várias camadas, cada uma com propósito claro) → **valide** (checkpoints
abaixo) → **documente** as decisões de segurança.

### Checkpoints de validação

- **Autenticação:** brute-force protection (lockout/rate limit), resistência a session fixation,
  expiração de token, mensagens de credencial inválida (não devem vazar existência de usuário).
- **Autorização:** horizontal e vertical privilege escalation bloqueadas; teste com tokens de
  roles/users diferentes.
- **Validação de entrada:** payloads de SQL injection (`' OR 1=1--`) rejeitados; payloads de XSS
  (`<script>alert(1)</script>`) escapados ou rejeitados.
- **Headers/CORS:** valide com scanner (`curl -I`, Mozilla Observatory) que headers estão
  presentes e que a allowlist de origem CORS está correta.

# OWASP Top 10 aplicado a Java

## A01 — Broken Access Control

**Sintomas:** endpoint que confia no client para passar o `userId` na URL, ou que não verifica
ownership do recurso.

```java
// ERRADO - o proprio client informa o userId; o backend confia
@DeleteMapping("/users/{userId}/orders/{orderId}")
public void deletar(@PathVariable Long userId, @PathVariable Long orderId) {
    orderRepository.deleteById(orderId);
}

// CORRETO - o userId vem do token (autenticado), nao da URL; ownership e verificado
@DeleteMapping("/orders/{orderId}")
public void deletar(@PathVariable Long orderId, @AuthenticationPrincipal User user) {
    Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException("Pedido nao encontrado"));
    if (!order.belongsTo(user.id())) {   // vertical E horizontal
        throw new BusinessException("Pedido nao pertence ao usuario");
    }
    orderRepository.delete(order);
}
```

## A02 — Cryptographic Failures (senhas, dados em repouso)

**Regra de ouro:** hash de senha com **bcrypt** (cost ≥ 10) ou **argon2id** — nunca MD5/SHA-1/
SHA-256 unsalted, nunca reversível ("encrypt" simétrico de senha).

```java
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

private static final BCRYPT_COST = 12;   // >= 10, balanceia seguranca e performance

public String hashPassword(String plaintext) {
    return new BCryptPasswordEncoder(BCRYPT_COST).encode(plaintext);
}

public boolean verifyPassword(String plaintext, String hash) {
    return new BCryptPasswordEncoder(BCRYPT_COST).matches(plaintext, hash);
}
```

**Dados em repouso:** campos sensíveis (CPF, número de cartão) em coluna criptografada (ex.: JPA
`@Convert` com `AttributeConverter` via AES-GCM) ou vault/tokenização (nunca armazene PAN de
cartão sem tokenizar via gateway de pagamento).

## A03 — Injection (SQL, JPQL, NoSQL, command)

**Regra de ouro:** **nunca** concatene entrada do usuário em string de query (SQL nativo, JPQL)
nem em shell — vale para ambos os estilos de query abaixo.

```java
// ERRADO - concatenacao em JPQL/SQL nativo; abre brecha para injecao
@Query("FROM Pedido WHERE id = " + id)   // NUNCA FACA ISSO
@Query(value = "SELECT * FROM pedidos WHERE id = " + id, nativeQuery = true)

// CORRETO - parametro nomeado (JPQL ou SQL nativo)
@Query("FROM Pedido WHERE id = :id")
Optional<Pedido> buscarPorId(@Param("id") Long id);

@Query(value = "SELECT * FROM pedidos WHERE id = :id", nativeQuery = true)
Optional<PedidoEntity> buscarPorIdNativo(@Param("id") Long id);
```

Para queries dinâmicas com muitos filtros opcionais, use `Criteria` API ou `QueryDSL` em vez de
montar string.

**Command injection:** nunca passe entrada do usuário como argumento de `Runtime.exec()`/
`ProcessBuilder` sem validação rigorosa (whitelist de caracteres, allowlist de comandos).

## A04 — Insecure Design

Padrões inseguros por design (ex.: fluxo de "esqueci senha" que revela se email existe; endpoint
de admin sem rate limit; IDOR — Insecure Direct Object Reference).

```java
// ERRADO - "esqueci senha" revela se o email existe (informacao vaza)
if (userRepository.existsByEmail(email)) {
    sendResetLink(email);
    return "Link enviado se o email existir";  // diferente se nao existe = info leak
} else {
    return "Link enviado se o email existir";
}

// CORRETO - resposta identica independente da existencia
sendResetLinkIfExists(email);   // sempre, sem condicionar a resposta
return "Se o email estiver cadastrado, um link sera enviado";
```

## A05 — Security Misconfiguration (headers, CORS, error handling)

```yaml
# application.yaml — headers minimos via Spring Security
spring:
  security:
    headers:
      content-security-policy: "default-src 'self'"
      x-content-type-options: nosniff
      x-frame-options: DENY
      referrer-policy: strict-origin-when-cross-origin
      strict-transport-security: max-age=31536000 ; includeSubDomains
```

**CORS — não abra `allowedOrigins("*")` com `allowCredentials(true)`:**

```java
// ERRADO - qualquer origem pode fazer requisicao autenticada (CSRF total)
CorsConfiguration cfg = new CorsConfiguration();
cfg.addAllowedOriginPattern("*");
cfg.setAllowCredentials(true);

// CORRETO - allowlist explicita, sem wildcard quando ha credenciais
@Bean
CorsConfigurationSource corsConfig() {
    CorsConfiguration cfg = new CorsConfiguration();
    cfg.setAllowedOrigins(List.of("https://app.exemplo.com"));
    cfg.setAllowCredentials(true);
    cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
    cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", cfg);
    return source;
}
```

**Error handling — não exponha stack trace em produção:**

```yaml
server:
  error:
    include-stacktrace: never
    include-message: never
    include-binding-errors: never
```

## A07 — Identification and Authentication Failures

- **Brute force:** rate limit em `/login` (10 tentativas / 15 min por IP — janela curta o
  suficiente para parar força bruta, longa o suficiente para não bloquear usuário legítimo).
- **Sessão:** JWT de curta duração (15 min access token + refresh token) ou session cookie
  `httpOnly; secure; sameSite=strict`.
- **Logout:** invalidar refresh token em servidor (não só no client) para impedir reuso de token
  roubado.

## A08 — Software and Data Integrity Failures

- **Dependências:** escanear CVEs conhecidos — ver seção "Dependências vulneráveis" abaixo.
- **Updates de schema:** Flyway/Liquibase com checksum verificado, nunca `ddl-auto: update` em
  produção.
- **Deserialização:** nunca `ObjectInputStream`/`readObject` com dados do client; prefira JSON com
  Jackson (`@JsonIgnoreProperties(ignoreUnknown = true)`).

## A09 — Security Logging and Monitoring Failures

- **Logar** falhas de autenticação, tentativas de privilege escalation, falhas de autorização,
  rate limit triggers — **sem** logar o segredo que falhou (ver `padrao-de-logs-java`).
- **Alertar** quando há pico de falhas de login (possível credential stuffing).

## A10 — Server-Side Request Forgery (SSRF)

Quando o backend faz request a uma URL fornecida pelo client (webhook, import por URL, avatar):

```java
// ERRADO - aceita qualquer URL; atacante aponta para http://169.254.169.254/... (metadata service)
String url = request.getUrl();
HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();

// CORRETO - resolve o host e bloqueia ranges privados/loopback antes de conectar
URI uri = new URI(url);
InetAddress addr = InetAddress.getByName(uri.getHost());
if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isAnyLocalAddress()) {
    throw new BusinessException("URL nao permitida");
}
HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
```

# Validação de entrada (Bean Validation)

Toda entrada do client passa por `@Valid` no DTO de request (ver `api-rest-design`). Nunca confie
em validação só no client — o backend sempre revalida.

```java
public record CriarUsuarioRequest(
    @NotBlank @Size(max = 200) String nome,
    @NotBlank @Email @Size(max = 254) String email,    // max 254 (RFC 5321)
    @NotBlank @Size(min = 8, max = 128) String senha, // min 8 impede senhas triviais
    @Pattern(regexp = "\\d{11}") String cpf             // formato, mas regra de negocio fica no service
) {}
```

**Mass assignment:** DTOs de entrada não devem expor campos sensíveis (`role`, `id`, status
administrativo, `createdAt`) que o cliente não pode setar — use DTOs específicos por operação, não
a entidade JPA direto no `@RequestBody`. Exemplo: `AdminAtualizarUsuarioRequest` com campo `role`,
`CriarUsuarioRequest` sem.

# JWT — emissão e validação

```java
// Emissao
String token = Jwts.builder()
        .subject(user.getId().toString())
        .claim("role", user.getRole())
        .issuer("sua-app")
        .audience().add("sua-app").and()
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + 15 * 60 * 1000))   // 15 min
        .signWith(Keys.hmacShaKeyFor(secret.getBytes()), Jwts.SIG.HS256)
        .compact();

// Validacao (em filtro do Spring Security)
Jws<Claims> parsed = Jwts.parser()
        .verifyWith(Keys.hmacShaKeyFor(secret.getBytes()))
        .requireIssuer("sua-app")
        .requireAudience("sua-app")
        .build()
        .parseSignedClaims(token);
```

**Checklist JWT:**
- `algorithm` allowlist explícito (rejeitar `none` e algoritmos fracos).
- `issuer` e `audience` validados.
- `expiration` curta (≤ 15 min para access token).
- Refresh token: persistido em servidor (banco) com revogação.
- Secret em variável de ambiente / vault, **nunca** em código-fonte ou `application.yml` versionado.

# Segredos — onde guardar

**Nunca em código-fonte, `application.yml` versionado, ou logs.** Dev: `application-local.yml`
(no `.gitignore`) ou env vars. CI: GitHub Actions / Azure DevOps Secrets. Produção: Azure Key
Vault, AWS Secrets Manager, HashiCorp Vault, ou env var injetada pelo orquestrador (Kubernetes
`Secret` montado como env).

```java
// ERRADO - secret em codigo
private static final String JWT_SECRET = "minha-chave-secreta-123";

// CORRETO - secret de variavel de ambiente
@Value("${jwt.secret}")
private String jwtSecret;
```

# Dependências vulneráveis (CVEs)

Inclua na rotina do projeto (CI ou pre-commit):

```bash
# OWASP Dependency-Check (Maven)
mvn org.owasp:dependency-check-maven:check

# Snyk (CLI ou via GitHub Action)
snyk test

# GitHub Dependabot (PRs automaticas para versoes novas)
# .github/dependabot.yml
```

Atualize dependências com CVEs críticos/altos **antes do merge** — mantenha uma SLA de
remediação (ex.: crítico em 24h, alto em 7 dias).

# Checklist de segurança por feature (use ao implementar)

- [ ] Entrada validada no DTO (`@Valid` + Bean Validation)
- [ ] Query parametrizada (nunca concatenação)
- [ ] Autorização por ownership/role verificada (não só autenticação)
- [ ] Senha hasheada com bcrypt(≥10) ou argon2id
- [ ] JWT com `alg` allowlist, `iss`/`aud` validados, expiração curta
- [ ] Segredo em env/secret manager (nunca em código)
- [ ] Headers de segurança (CSP, HSTS, X-Frame-Options) ativos
- [ ] CORS com allowlist explícita (sem `*` com credenciais)
- [ ] Error response sem stack trace em produção
- [ ] Logs de evento de segurança (sem logar o dado sensível)
- [ ] Rate limit em `/login` e endpoints sensíveis
- [ ] Dependências sem CVE crítico/alto
- [ ] Migrations validadas (Flyway/Liquibase), sem `ddl-auto: update` em prod

# Constraints

Regras inegociáveis — cada uma reforça um item do checklist acima; violá-las é bloqueante em
qualquer revisão de segurança.

## MUST DO
- Hash de senha com bcrypt/argon2id; queries sempre parametrizadas; valide/sanitize toda entrada.
- Rate limiting em endpoints de autenticação; security headers (CSP, HSTS, X-Frame-Options).
- Logue eventos de segurança (failed auth, privilege escalation) sem logar o segredo.
- Segredos em env vars/secret manager; HTTPS obrigatório em produção (HSTS).

## MUST NOT DO
- Senha em plaintext ou encriptada reversivelmente; algoritmo fraco (MD5, SHA-1, DES, ECB).
- Confiar em entrada do usuário sem validação; expor dado sensível em log ou error response.
- Hardcode de segredo/credencial em código; `allowedOrigins("*")` com `allowCredentials(true)`;
  stack trace em error response de produção.

## Quem aplica o quê

| Situação | Quem | Skill |
|---|---|---|
| Implementar feature com segurança (auth, validação) | sessão principal | esta skill |
| Auditar segurança completa de um serviço (pré-produção) | agent `engenheiro-seguranca` | esta skill + `padrao-de-logs-java` |
| Configurar Spring Security (filter chain, JWT, CORS) | sessão principal | esta skill |
| Escanear dependências em CI | sessão principal | esta skill |
| Revisão arquitetural completa | agent `java-revisor` (modo `auditoria`) | `revisao-de-codigo-java` + esta skill |
