---

name: persistencia-jpa
description: "Pocket reference for the most common JPA/Hibernate problems — N+1, `LazyInitializationException`, misplaced transactions, lost-update concurrency, slow listings without pagination, entity/projection design, optimistic locking. Use whenever there is doubt about persistence performance or behavior, or when reviewing code that touches `Repository`/`@Entity`. Uso: agents `especialista-banco-dados` / `java-construtor` / `java-revisor` or manual invocation via `/persistencia-jpa`; não deve ser carregada proativamente pela sessão principal."
license: MIT
metadata:
  author: https://github.com/srportto/srportto
  co-author: https://github.com/Jeffallan/claude-skills
  version: "1.1.0"
  domain: persistence
  triggers: muitas queries, N+1, LazyInitializationException, transação, lock, paginação lenta, JPA, Hibernate, dirty checking
  role: specialist
  scope: persistence
  output-format: code
  related-skills: banco-de-dados-performance, arquitetura-limpa-java, qualidade-codigo-java
---
---

# Persistência JPA

## Visão geral

Referência de bolso para os problemas de JPA/Hibernate mais comuns: N+1, `LazyInitializationException`,
transações mal posicionadas, atualização concorrente perdida e listagens lentas sem paginação. Use
sempre que houver dúvida de performance ou comportamento de persistência, ou ao revisar código que
acessa `Repository`/`@Entity`.

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

Para confirmar a suspeita, habilite `hibernate.SQL: DEBUG` (ou `show-sql: true`) e conte as queries no
log.

**Solução 1 — `JOIN FETCH` (JPQL)**: uma única query traz pedidos e itens juntos. Use quando a
associação sempre é necessária para o caso de uso da consulta.

```java
// application/pedido/PedidoRepository.java
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @Query("SELECT p FROM Pedido p JOIN FETCH p.itens")
    List<Pedido> buscarTodosComItens();
}
```

**Solução 2 — `@EntityGraph`**: reaproveita o método padrão do `JpaRepository` (`findAll`) sem escrever
JPQL. Prefira quando a mesma query base precisa às vezes carregar a associação e às vezes não (múltiplos
métodos `@EntityGraph` sobre o mesmo `findById`, por exemplo).

```java
// application/pedido/PedidoRepository.java
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @EntityGraph(attributePaths = "itens")
    List<Pedido> findAll();
}
```

## Transações

`@Transactional` vive em `application/` (nos services) — **nunca** no `entrypoint/` (controller,
listener SQS) nem no `domain/`. O controller apenas orquestra a chamada ao service; é o service quem
delimita a fronteira transacional.

Padrão adotado neste projeto (`ProdutoService`, overlay `rest-crud-banco`): `readOnly = true` na
classe inteira, e `@Transactional` (leitura/escrita) sobrescrito nos métodos que gravam. Isso desliga o
dirty checking do Hibernate nos métodos de leitura (menos overhead) e deixa explícito, por método,
quais alteram dado:

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
`BusinessException` (422, já mapeada pelo `ApiExceptionHandler` do projeto) — nenhum tratamento
adicional é necessário no controller:

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

## Erros comuns

| Anti-padrão | Por que é errado | Correção |
|---|---|---|
| `FetchType.EAGER` em coleção | Carrega TODOS os itens em TODA consulta da entidade dona, mesmo quando não precisa | `FetchType.LAZY` por padrão; carregar explicitamente via `JOIN FETCH`/`@EntityGraph` quando o caso de uso exigir |
| `findAll()` sem paginação | Carrega a tabela inteira em memória; piora a cada registro novo | `Pageable` — `JpaRepository` já oferece `findAll(Pageable)` de graça |
| Entidade `@Entity` retornada pelo controller | Serializar a entidade acopla o contrato de API ao schema do banco | Mapper converte para DTO record antes de sair pela borda |

```java
// ERRADO - EAGER em colecao carrega TODOS os itens em TODA consulta de Pedido, mesmo quando nao precisa
@OneToMany(mappedBy = "pedido", fetch = FetchType.EAGER)
private List<ItemPedido> itens;

// CORRETO - LAZY por padrao; carrega a colecao explicitamente so quando o caso de uso precisa
@OneToMany(mappedBy = "pedido", fetch = FetchType.LAZY)
private List<ItemPedido> itens;
```

### `open-in-view` ligado

Por padrão, o Spring Boot mantém a sessão do Hibernate aberta durante toda a requisição HTTP
(`spring.jpa.open-in-view: true` é o default). Isso evita `LazyInitializationException` de forma
implícita, mas esconde o problema: a query real dispara durante a serialização da resposta, fora de
qualquer `@Transactional` visível, e prende a conexão de banco pelo tempo inteiro da requisição
(inclusive chamadas HTTP externas feitas depois). Recomendação:

```yaml
spring:
  jpa:
    open-in-view: false
```

Com `open-in-view: false`, qualquer acesso lazy fora da transação falha explicitamente com
`LazyInitializationException` no lugar certo (o service), forçando a resolver com `JOIN FETCH`,
`@EntityGraph` ou projeção DTO — nunca reabrindo a sessão.
