---
name: persistencia-jpa
description: Use quando houver problemas ou dúvidas de JPA/Hibernate - N+1 queries, LazyInitializationException, transações, locking otimista, paginação, projeções ou modelagem de entidades e relacionamentos. Gatilhos - "muitas queries", "N+1", "LazyInitializationException", "transação", "lock", "paginação lenta".
---

# Persistência JPA

## Visão geral

Referência de bolso para os problemas de JPA/Hibernate mais comuns em uma aplicação Java/Spring Boot:
excesso de queries (N+1), `LazyInitializationException`, transações mal posicionadas, atualização
concorrente perdida e listagens lentas sem paginação. Use esta skill sempre que houver dúvida de
performance ou de comportamento de persistência, ou ao revisar código que acessa `Repository`/`@Entity`.

**Quando NÃO usar:** para dúvida sobre em qual camada uma classe deve viver (ex.: onde fica o
repository), use `arquitetura-limpa-java`. Para gerar o esqueleto de uma aplicação nova com banco de
dados (overlay `rest-crud-banco`), use `criar-aplicacao-java`. Para revisão de código completa (não só
persistência), use `revisao-de-codigo-java`. Para tuning de banco (índices, `EXPLAIN ANALYZE`,
configuração do SGBD), use `banco-de-dados-performance`.

## Tabela problema → solução

| Problema | Sintoma | Solução |
|---|---|---|
| N+1 queries | Muitos `SELECT` no log para uma única listagem | `JOIN FETCH` (JPQL) ou `@EntityGraph` |
| `LazyInitializationException` | Erro ao acessar associação fora da transação | Projeção DTO ou `JOIN FETCH` — **nunca** `enable_lazy_load_no_trans` |
| Update lento | Overhead de dirty checking em consultas grandes | `@Transactional(readOnly = true)` nos métodos de leitura |
| Lost update | Duas transações concorrentes sobrescrevem uma a outra | Locking otimista com `@Version` |
| Listagem lenta | Página carrega tudo de uma vez, sem limite | `Pageable` + projeção (retornar só os campos necessários) |
| Insert em lote lento | Uma query de `INSERT` por registro | `hibernate.jdbc.batch_size` + `saveAll` |

## N+1 em detalhe

> O problema de performance mais comum em JPA/Hibernate.

### O problema

```java
// domain/entities/Pedido.java
@Entity
@Table(name = "pedidos")
@Getter
@Setter
@NoArgsConstructor
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "pedido", fetch = FetchType.LAZY)
    private List<ItemPedido> itens;
}
```

```java
// ERRADO - N+1: 1 query para buscar os pedidos + 1 query por pedido para buscar os itens
List<Pedido> pedidos = pedidoRepository.findAll();   // 1 query
for (Pedido pedido : pedidos) {
    pedido.getItens().size();                        // 1 query POR pedido (lazy)
}
// 50 pedidos = 51 queries
```

Para confirmar a suspeita, habilite o log de SQL e conte as queries:

```yaml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
logging:
  level:
    org.hibernate.SQL: DEBUG
```

### Solução 1: JOIN FETCH (JPQL)

```java
// application/pedido/PedidoRepository.java
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @Query("SELECT p FROM Pedido p JOIN FETCH p.itens")
    List<Pedido> buscarTodosComItens();
}
```

Uma única query traz pedidos e itens juntos (equivalente a um `JOIN` SQL). Use quando a associação
sempre é necessária para o caso de uso da consulta.

### Solução 2: @EntityGraph

```java
// application/pedido/PedidoRepository.java
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @EntityGraph(attributePaths = "itens")
    List<Pedido> findAll();
}
```

`@EntityGraph` tem a vantagem de reaproveitar o método padrão do `JpaRepository` (`findAll`) sem
escrever JPQL — prefira quando a mesma query base precisa às vezes carregar a associação e às vezes
não (múltiplos métodos `@EntityGraph` sobre o mesmo `findById`, por exemplo).

## Transações

`@Transactional` vive em `application/` (nos services) — **nunca** no `entrypoint/` (controller,
listener SQS) nem no `domain/`. O controller apenas orquestra a chamada ao service; é o service quem
delimita a fronteira transacional.

Padrão adotado neste projeto (`ProdutoService`, overlay `rest-crud-banco`): `readOnly = true` na
classe inteira, e `@Transactional` (leitura/escrita) sobrescrito nos métodos que gravam:

```java
// application/produto/ProdutoService.java
@Service
@Transactional(readOnly = true)
public class ProdutoService {

    private final ProdutoRepository repository;

    public ProdutoService(ProdutoRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Produto criar(Produto produto) {
        produto.validar();
        return repository.save(produto);
    }

    public Produto buscarPorId(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException("Produto nao encontrado: " + id));
    }

    public List<Produto> listar() {
        return repository.findAll();
    }

    @Transactional
    public void excluir(Long id) {
        repository.delete(buscarPorId(id));
    }
}
```

`readOnly = true` desliga o dirty checking do Hibernate para os métodos de leitura (menos overhead) e
deixa explícito, por método, a intenção de gravação — quem lê o código já sabe quais métodos alteram
dado sem precisar abrir a query.

### Pitfall: auto-invocação não passa pelo proxy

`@Transactional` funciona via proxy do Spring. Uma chamada interna (`this.metodo(...)`) não passa pelo
proxy, então a anotação é **ignorada silenciosamente**:

```java
// ERRADO - chamada interna (this.criar) nao passa pelo proxy Spring; @Transactional de criar() e ignorado
@Service
@Transactional(readOnly = true)
public class ProdutoService {

    public void processarLote(List<Produto> produtos) {
        produtos.forEach(this::criar); // this.criar() -> sem transacao real aqui
    }

    @Transactional
    public Produto criar(Produto produto) {
        produto.validar();
        return repository.save(produto);
    }
}
```

```java
// CORRETO - extrai o metodo transacional para outro bean, chamado de fora (passa pelo proxy)
@Service
public class ProcessadorLoteService {

    private final ProdutoService produtoService;

    public ProcessadorLoteService(ProdutoService produtoService) {
        this.produtoService = produtoService;
    }

    public void processarLote(List<Produto> produtos) {
        produtos.forEach(produtoService::criar); // chamada externa, passa pelo proxy
    }
}
```

## Convenções do projeto

- **Entidade em `domain/entities`** com Lombok `@Getter @Setter @NoArgsConstructor` — nunca `@Data` em
  entidade JPA (`@Data` gera `equals`/`hashCode` a partir de todos os campos, o que quebra com proxies
  do Hibernate e coleções lazy). Veja `Produto` e `PedidoEntity` no overlay `rest-crud-banco`/
  `sqs-para-banco`.
- **Repository em `application/<contexto>`**, interface `JpaRepository`, sem implementação manual —
  ver `ProdutoRepository extends JpaRepository<Produto, Long>`. Camadas descritas em detalhe na skill
  `arquitetura-limpa-java`.
- **Idempotência persistente via unique constraint**: `PedidoEntity` (overlay `sqs-para-banco`) marca
  `@Column(name = "id_pedido", unique = true)` e `PedidoRepository` expõe
  `existsByIdPedido(String idPedido)` — checagem de duplicidade delegada ao banco, sem lógica extra na
  application.
- **DTO record nas bordas via MapStruct**: a entidade nunca atravessa `entrypoint/`; `ProdutoMapper`
  (`@Mapper(componentModel = "spring")`) converte `Produto` para os records `CriarProdutoRequest`/
  `ProdutoResponse` definidos no controller.
- **`ddl-auto`**: `update` só em desenvolvimento (`application-fragmento.yaml` do overlay
  `rest-crud-banco`); em produção use `validate` — o schema é gerenciado por migration (Flyway/Liquibase),
  não pelo Hibernate.

## Locking otimista

Para evitar *lost update* (duas transações leem o mesmo registro e a segunda grava por cima da
primeira sem saber que ele mudou), adicione `@Version` na entidade:

```java
// domain/entities/Produto.java
@Entity
@Table(name = "produtos")
@Getter
@Setter
@NoArgsConstructor
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long versao;

    private String nome;
    private BigDecimal preco;

    public void validar() {
        if (preco == null || preco.signum() <= 0) {
            throw new BusinessException("Preco do produto deve ser maior que zero");
        }
    }
}
```

O Hibernate incrementa `versao` a cada `UPDATE` e compara o valor lido com o valor atual no banco; se
divergirem, lança `OptimisticLockingFailureException`. Trate essa exceção na application e traduza para
`BusinessException` (422) — o mesmo padrão de erro de negócio já usado no projeto:

```java
// application/produto/ProdutoService.java
@Transactional
public Produto atualizar(Produto produto) {
    try {
        return repository.save(produto);
    } catch (OptimisticLockingFailureException e) {
        throw new BusinessException("Produto foi alterado por outro processo, tente novamente");
    }
}
```

`BusinessException` já é mapeada pelo `ApiExceptionHandler` do projeto para
`HttpStatus.UNPROCESSABLE_ENTITY` (422), então nenhum tratamento adicional é necessário no controller.

## Erros comuns

**`FetchType.EAGER` em coleção**

```java
// ERRADO - EAGER em colecao carrega TODOS os itens em TODA consulta de Pedido, mesmo quando nao precisa
@OneToMany(mappedBy = "pedido", fetch = FetchType.EAGER)
private List<ItemPedido> itens;
```

```java
// CORRETO - LAZY por padrao; carrega a colecao explicitamente so quando o caso de uso precisa (JOIN FETCH/@EntityGraph)
@OneToMany(mappedBy = "pedido", fetch = FetchType.LAZY)
private List<ItemPedido> itens;
```

**`findAll()` sem paginação**

```java
// ERRADO - carrega a tabela inteira em memoria; listagem fica mais lenta a cada registro novo
public List<Produto> listar() {
    return repository.findAll();
}
```

```java
// CORRETO - Pageable limita o resultado; repository JpaRepository ja oferece findAll(Pageable) de graca
public Page<Produto> listar(Pageable pageable) {
    return repository.findAll(pageable);
}
```

**Entidade retornada pelo controller**

```java
// ERRADO - Produto e uma @Entity; serializa-la direto na resposta HTTP acopla o contrato de API ao schema do banco
@GetMapping("/{id}")
public ResponseEntity<Produto> buscar(@PathVariable Long id) {
    return ResponseEntity.ok(service.buscarPorId(id));
}
```

```java
// CORRETO - mapper converte a entidade para o DTO de resposta antes de sair pela borda
@GetMapping("/{id}")
public ResponseEntity<ProdutoResponse> buscar(@PathVariable Long id) {
    return ResponseEntity.ok(mapper.paraResposta(service.buscarPorId(id)));
}
```

**`open-in-view` ligado**

Por padrão, o Spring Boot mantém a sessão do Hibernate aberta durante toda a requisição HTTP
(`spring.jpa.open-in-view: true` é o default). Isso evita `LazyInitializationException` de forma
implícita, mas esconde o problema em vez de resolvê-lo: a query real acaba disparando durante a
serialização da resposta, fora de qualquer `@Transactional` visível, dificultando saber onde a query
foi disparada e prendendo a conexão de banco pelo tempo inteiro da requisição (inclusive chamadas HTTP
externas feitas depois). Recomendação:

```yaml
spring:
  jpa:
    open-in-view: false
```

Com `open-in-view: false`, qualquer acesso lazy fora da transação falha explicitamente com
`LazyInitializationException` no lugar certo (o service), forçando a resolver com `JOIN FETCH`,
`@EntityGraph` ou projeção DTO — nunca reabrindo a sessão.
