---
name: qualidade-codigo-java
description: Use ao aplicar princípios de clean code (DRY/KISS/YAGNI), convenções de nomenclatura, imutabilidade, uso correto de Optional, melhores práticas de streams, exception handling, ou ao aplicar refactorings do Fowler em código Java. Complementa a skill revisao-de-codigo-java com foco em "como aplicar" (não em "como revisar"). Gatilhos - "clean code", "boas práticas", "refatorar", "DRY", "KISS", "YAGNI", "imutabilidade", "Optional", "streams".
---

# Qualidade de Código Java (clean code + refactoring)

## Visão geral

Guia de **aplicação** de clean code em Java — DRY, KISS, YAGNI, nomenclatura, imutabilidade,
`Optional`, streams, exception handling — e de refactorings do catálogo do Fowler (Remove Parameter,
Extract Method, Replace Magic Number, etc.). Esta skill é o "lado ativo" da revisão: a
`revisao-de-codigo-java` diz **o que revisar** com checklist e severidades; esta skill diz **como
aplicar** o que a revisão aponta.

**Quando NÃO usar:** para revisar um diff/PR com checklist por severidade, use
`revisao-de-codigo-java` (ela referencia esta aqui). Para a regra de dependência entre camadas
(domain/application/entrypoint), use `arquitetura-limpa-java`. Para JPA/Hibernate (N+1, dirty
checking), use `persistencia-jpa`. Para logging (formato, MDC), use `padrao-de-logs-java`.

## Clean code — princípios com exemplo

### DRY — Don't Repeat Yourself

```java
// RUIM - logica de validacao duplicada
public void criarUsuario(UsuarioRequest req) {
    if (req.getEmail() == null || !req.getEmail().contains("@")) {
        throw new ValidationException("Email invalido");
    }
}

public void atualizarUsuario(UsuarioRequest req) {
    if (req.getEmail() == null || !req.getEmail().contains("@")) {
        throw new ValidationException("Email invalido");
    }
}
```

```java
// BOM - fonte unica
public class EmailValidator {
    public void validate(String email) {
        if (email == null || !email.contains("@")) {
            throw new ValidationException("Email invalido");
        }
    }
}
```

> **DRY com bom senso:** a regra das 3 ocorrências (na 1ª e 2ª, duplicar pode ser mais barato
> que a abstração errada; extraia na 3ª). Não crie `EmailValidator` com interface e implementação
> única "para o futuro" — abstração especulativa é over-engineering (ver `padroes-de-projeto-java`,
> seção "Quando NÃO aplicar pattern").

### KISS — Keep It Simple

```java
// RUIM - sobre-engenharia para 1 implementacao
public interface UserFactory {
    User createUser();
}
public class ConcreteUserFactory implements UserFactory {
    public User createUser() { return new User(); }
}

// BOM - chamada direta, sem indirecao
public User createUser() { return new User(); }
```

### YAGNI — You Aren't Gonna Need It

```java
// RUIM - abstracao prematura para um caso que nao existe
public class ConfigurableUserServiceFactoryProvider { }

// BOM - implemente quando a segunda variacao aparecer
public class UserService { }
```

## Convenções de nomenclatura

```java
// Classes/Records: PascalCase
public class MarketService {}
public record Money(BigDecimal amount, Currency currency) {}

// Metodos/campos: camelCase
private final MarketRepository marketRepository;
public Market findBySlug(String slug) {}

// Constantes: UPPER_SNAKE_CASE
private static final int MAX_PAGE_SIZE = 100;
```

**Nomes que revelam intenção** (não abrevie sem motivo):

```java
// RUIM - abreviacoes obscuras
public List<Produto> get(String s) { ... }
public boolean chk(String str) { ... }
private static final int N = 100;

// BOM - nome diz o que faz
public List<Produto> buscarAtivosPorCategoria(String categoria) { ... }
public boolean precoEhValido(BigDecimal preco) { ... }
private static final int TAMANHO_MAXIMO_PAGINA = 100;
```

> Use português ou inglês consistentemente dentro do mesmo pacote/classe — não misture.

## Imutabilidade

```java
// BOM - record para DTOs e value objects (imutavel, equals/hashCode/toString gerados)
public record MarketDto(Long id, String name, MarketStatus status) {}

// BOM - classe com final fields e getters only
public class Market {
    private final Long id;
    private final String name;
    // getters only, no setters
}
```

Records são o padrão deste catálogo (ver `java-moderno`): use para DTOs, value objects, chaves
compostas (`IdAutorizacao`). Não use records quando precisar de mutabilidade ou herança.

## Optional — uso correto

```java
// BOM - retorne Optional de metodos find*
Optional<Market> market = marketRepository.findBySlug(slug);

// BOM - use map/flatMap em vez de get() — mais funcional e seguro
return market
    .map(MarketResponse::from)
    .orElseThrow(() -> new EntityNotFoundException("Market not found"));

// RUIM - get() sem verificar presenca
return market.get();   // NoSuchElementException se vazio
```

## Streams — pipelines curtos, sem efeito colateral

```java
// BOM - pipeline curto, transformacao pura
List<String> names = markets.stream()
    .map(Market::name)
    .filter(Objects::nonNull)
    .toList();

// RUIM - forEach com mutacao de lista externa
List<String> nomesAtivos = new ArrayList<>();
markets.stream().forEach(m -> {
    if (m.isAtivo()) {
        nomesAtivos.add(m.name().toUpperCase());
    }
});
```

Quando o pipeline exigiria múltiplos `flatMap`/estado acumulado só para simular um `for`, prefira
o loop explícito — clareza vale mais que "tudo em stream".

## Exception handling

- Use **unchecked exceptions** para erros de domínio (`BusinessException` — mapeada para 422 pelo
  handler central; ver `arquitetura-limpa-java`).
- **Crie exceções específicas do domínio** (`MarketNotFoundException`) em vez de `RuntimeException`
  genérica.
- **Evite** `catch (Exception ex)` amplo, a menos que seja para relançar/logar centralmente.
- **Sempre preserve a causa** (`throw new ApplicationException(msg, e)`) — perder a stack trace
  original torna investigação quase impossível.

```java
// BOM - especifica, com causa preservada
try {
    integracaoClient.enviar(pedido);
} catch (IOException e) {
    throw new ApplicationException("Falha ao enviar pedido " + pedido.id() + " para integracao", e);
}

// RUIM - perde a causa
try {
    integracaoClient.enviar(pedido);
} catch (IOException e) {
    throw new RuntimeException(e.getMessage());
}
```

**Recursos** — sempre try-with-resources:

```java
// BOM - fecha mesmo em caso de excecao
try (InputStream in = new FileInputStream(arquivo)) {
    return ler(in);
}

// RUIM - close() nao executa se ler() lancar
InputStream in = new FileInputStream(arquivo);
String conteudo = ler(in);
in.close();
```

## Genéricos e type safety

```java
// BOM - generic explicito
public <T extends Identifiable> Map<Long, T> indexById(Collection<T> items) { ... }

// RUIM - raw type
public Map indexById(Collection items) { ... }   // sem type safety
```

---

# Refactorings do Fowler — guia rápido

Catálogo dos refactorings mais comuns em Java moderno, com exemplo antes/depois. Cada refactoring
resolve um **cheiro** (code smell) específico — não aplique por aplicar.

## Remove Parameter

**Quando:** um parâmetro nunca é usado, ou seu valor pode ser obtido de outro lugar (campo da
classe, constante, chamada de método).

**Antes:**

```java
public Backend selectBackendForGroupCommit(long tableId, ConnectContext context, boolean isCloud) {
    if (!Env.getCurrentEnv().isMaster()) {
        long backendId = new MasterOpExecutor(context)
                .getGroupCommitLoadBeId(tableId, context.getCloudCluster(), isCloud);
        return Env.getCurrentSystemInfo().getBackend(backendId);
    } else {
        return Env.getCurrentSystemInfo()
                .getBackend(selectBackendForGroupCommitInternal(tableId, context.getCloudCluster(), isCloud));
    }
}
```

**Depois** (`isCloud` removido — não é mais usado nas chamadas internas):

```java
public Backend selectBackendForGroupCommit(long tableId, ConnectContext context) {
    if (!Env.getCurrentEnv().isMaster()) {
        long backendId = new MasterOpExecutor(context)
                .getGroupCommitLoadBeId(tableId, context.getCloudCluster());
        return Env.getCurrentSystemInfo().getBackend(backendId);
    } else {
        return Env.getCurrentSystemInfo()
                .getBackend(selectBackendForGroupCommitInternal(tableId, context.getCloudCluster()));
    }
}
```

> Veja a skill dedicada `refactoring-remove-parameter` para a versão focada e passo-a-passo
> desse refactoring.

## Extract Method

**Quando:** um trecho de código tem um propósito claro e pode ser nomeado, ou você quer reusá-lo.

**Antes:**

```java
public void processar(Pedido pedido) {
    // validacao
    if (pedido.getValor() == null || pedido.getValor().signum() <= 0) {
        throw new BusinessException("Valor invalido");
    }
    if (pedido.getItens() == null || pedido.getItens().isEmpty()) {
        throw new BusinessException("Pedido sem itens");
    }
    // ... logica principal
}
```

**Depois:**

```java
public void processar(Pedido pedido) {
    validar(pedido);
    // ... logica principal
}

private void validar(Pedido pedido) {
    if (pedido.getValor() == null || pedido.getValor().signum() <= 0) {
        throw new BusinessException("Valor invalido");
    }
    if (pedido.getItens() == null || pedido.getItens().isEmpty()) {
        throw new BusinessException("Pedido sem itens");
    }
}
```

## Replace Magic Number with Symbolic Constant

**Quando:** um número ou string literal tem significado de negócio e aparece em mais de um lugar.

**Antes:**

```java
if (tentativas > 3) { ... }
Thread.sleep(1000L);
```

**Depois:**

```java
private static final int MAX_TENTATIVAS = 3;
private static final long INTERVALO_RETRY_MS = 1_000L;

if (tentativas > MAX_TENTATIVAS) { ... }
Thread.sleep(INTERVALO_RETRY_MS);
```

## Replace Conditional with Polymorphism

**Quando:** um `switch`/`if` chain decide por **tipo** e cada ramo tem lógica distinta.

**Antes:**

```java
public BigDecimal calcularTaxa(Pagamento pagamento) {
    if (pagamento instanceof Pix) return BigDecimal.ZERO;
    if (pagamento instanceof Cartao) return BigDecimal.valueOf(0.03);
    throw new IllegalStateException("Tipo desconhecido");
}
```

**Depois** (sealed type + switch exaustivo, ver `java-moderno`):

```java
public BigDecimal calcularTaxa(Pagamento pagamento) {
    return switch (pagamento) {
        case Pix p     -> BigDecimal.ZERO;
        case Cartao c  -> BigDecimal.valueOf(0.03);
        // compilador exige todos os casos se Pagamento for sealed
    };
}
```

## Introduce Parameter Object

**Quando:** um grupo de parâmetros viaja junto em vários métodos.

**Antes:**

```java
public void buscar(LocalDate inicio, LocalDate fim, String status, int pagina) { ... }
public void exportar(LocalDate inicio, LocalDate fim, String status) { ... }
```

**Depois:**

```java
public record FiltroPedido(LocalDate inicio, LocalDate fim, String status) {}

public void buscar(FiltroPedido filtro, int pagina) { ... }
public void exportar(FiltroPedido filtro) { ... }
```

## Replace Loop with Pipeline

**Quando:** um loop acumula resultado em uma coleção com transformações triviais.

**Antes:**

```java
List<String> nomes = new ArrayList<>();
for (Produto p : produtos) {
    if (p.isAtivo()) {
        nomes.add(p.getNome().toUpperCase());
    }
}
```

**Depois:**

```java
List<String> nomes = produtos.stream()
    .filter(Produto::isAtivo)
    .map(p -> p.getNome().toUpperCase())
    .toList();
```

> Ver `revisao-de-codigo-java` (item 4 — Streams) e `java-moderno` (seção Stream) para quando loop
> é preferível a pipeline (clareza > "tudo em stream").

## Quem aplica o quê

| Situação | Quem | Skill |
|---|---|---|
| Aplicar refactoring em uma classe/método | session principal | esta skill |
| Revisar diff/PR com checklist de severidade | agent `java-revisor` | `revisao-de-codigo-java` |
| Remoção de parâmetro focada (passo-a-passo) | session principal | `refactoring-remove-parameter` |
| Limpar imports não usados | session principal | `remover-imports-nao-usados` |
