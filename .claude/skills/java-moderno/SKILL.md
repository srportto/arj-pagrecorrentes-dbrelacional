---
name: java-moderno
description: Use quando precisar escrever código novo que pode aproveitar features modernas do Java (records, sealed classes, pattern matching, virtual threads, text blocks), migrar código de Java 8/11/17/21 para 25, ao revisar se uma feature moderna se aplica, ou quando o usuário perguntar "qual a forma moderna de fazer X em Java".
---

# Java Moderno

## Visão geral

Guia de referência rápida das features modernas do Java (records, sealed classes, pattern matching,
switch expressions, text blocks, virtual threads, `var`) na stack fixa deste catálogo — **Java 25 +
Spring Boot 4.0.4** (confira `.claude/skills/criar-aplicacao-java/assets/app-base/pom.xml`).
Use esta skill para decidir se uma feature moderna resolve um código específico, ver o exemplo
antes/depois, e para orientar uma migração de código escrito em uma versão anterior do Java.

**Quando NÃO usar:** para aplicar um design pattern GoF (Strategy, Factory, Builder...), use
`padroes-de-projeto-java`. Para uma revisão completa de código (não só modernização), use
`revisao-de-codigo-java`.

## 1. Records

Tipo imutável para modelar dados: o compilador gera construtor, accessors, `equals`/`hashCode`/
`toString` a partir dos componentes declarados. Substitui a classe "de dados" manual.

```java
// Java classico: classe manual com campo final, getters, equals, hashCode e toString escritos a mao
public final class Pedido {
    private final String id;
    private final BigDecimal valor;

    public Pedido(String id, BigDecimal valor) {
        this.id = id;
        this.valor = valor;
    }

    public String getId() { return id; }
    public BigDecimal getValor() { return valor; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Pedido pedido)) return false;
        return Objects.equals(id, pedido.id) && Objects.equals(valor, pedido.valor);
    }

    @Override
    public int hashCode() { return Objects.hash(id, valor); }
}
```

```java
// Java moderno: record de 1 linha - construtor, accessors, equals/hashCode/toString gerados
public record Pedido(String id, BigDecimal valor) {}
```

Records já em uso neste catálogo: `StatusAplicacao` (`domain/model/` do app-base), `Pedido` (exemplo
em `arquitetura-limpa-java/SKILL.md`), `IdAutorizacao` (chave composta `(UUID, Integer)`, ver
`docs/based-java-aplication.md`).

**Quando usar:** DTO de request/response, value object, chave composta imutável (`IdAutorizacao`).
**Quando evitar:** quando precisa de mutabilidade (setter após a criação) ou de herdar de uma classe —
records são implicitamente `final` e só podem implementar interfaces, nunca estender outra classe.

## 2. Sealed classes/interfaces

Hierarquia fechada em tempo de compilação: só as classes/interfaces listadas em `permits` podem
implementar o tipo selado. Modela domínios finitos e conhecidos (tipos de pagamento, estados).

```java
// Java classico: interface aberta - qualquer classe em qualquer lugar do codigo pode implementar
public interface Pagamento {
    BigDecimal valor();
}

public class Pix implements Pagamento { /* ... */ }
public class Cartao implements Pagamento { /* ... */ }
// nada impede uma classe Boleto implements Pagamento aparecer depois, sem o compilador avisar
```

```java
// Java moderno: hierarquia fechada - so Pix e Cartao podem implementar Pagamento
public sealed interface Pagamento permits Pix, Cartao {
    BigDecimal valor();
}

public record Pix(String chave, BigDecimal valor) implements Pagamento {}
public record Cartao(String numeroMascarado, BigDecimal valor) implements Pagamento {}
```

**Quando usar:** hierarquia de domínio finita e conhecida (tipos de pagamento, estados de um fluxo).
Casa diretamente com switch exaustivo (item 3) — o compilador acusa erro se um novo tipo (`Boleto`)
for adicionado ao `permits` e nenhum `switch` existente for atualizado para tratá-lo.
**Quando evitar:** quando a hierarquia precisa ser extensível por módulos/plugins externos que o
autor do tipo selado não controla.

## 3. Pattern matching

Elimina o cast manual depois de um `instanceof`, permite `switch` com patterns exaustivo sobre
sealed types, e desestrutura records diretamente na condição (record patterns).

**`instanceof` com binding:**

```java
// Java classico: instanceof seguido de cast manual
if (pagamento instanceof Pix) {
    Pix pix = (Pix) pagamento;
    processar(pix.chave());
}
```

```java
// Java moderno: instanceof com binding - "pix" ja nasce com o tipo certo, sem cast
if (pagamento instanceof Pix pix) {
    processar(pix.chave());
}
```

**`switch` com patterns e exaustividade sobre sealed:**

```java
// Java classico: cadeia de if/else sem garantia do compilador se surgir um tipo novo
BigDecimal taxa;
if (pagamento instanceof Pix) {
    taxa = BigDecimal.ZERO;
} else if (pagamento instanceof Cartao) {
    taxa = BigDecimal.valueOf(0.03);
} else {
    throw new IllegalStateException("Tipo de pagamento desconhecido");
}
```

```java
// Java moderno: switch exaustivo sobre sealed interface - sem "default", compilador exige
// tratar todos os casos de "permits"; se Boleto for adicionado depois, o build quebra ate
// o switch ser atualizado
BigDecimal taxa = switch (pagamento) {
    case Pix p -> BigDecimal.ZERO;
    case Cartao c -> BigDecimal.valueOf(0.03);
};
```

**Record patterns (desestruturação direta):**

```java
// Java classico: extrai os campos do record manualmente apos o instanceof
if (pagamento instanceof Cartao cartao) {
    String numero = cartao.numeroMascarado();
    BigDecimal valor = cartao.valor();
    processar(numero, valor);
}
```

```java
// Java moderno: record pattern desestrutura o record direto na condicao
if (pagamento instanceof Cartao(String numero, BigDecimal valor)) {
    processar(numero, valor);
}
```

**Quando usar:** sempre que houver `instanceof` seguido de cast manual, e especialmente sobre
hierarquias `sealed`, onde o compilador garante que nenhum caso fica esquecido.
**Quando evitar:** quando o comportamento por tipo já pode ser resolvido por polimorfismo simples
(método sobrescrito na hierarquia) — pattern matching não substitui um bom design orientado a
objetos, é para os casos em que é preciso decidir por tipo concreto.

## 4. Switch expressions

`switch` que produz um valor, com `->` (sem fallthrough — cada ramo é isolado) e `yield` quando o
ramo precisa de mais de uma instrução antes do valor final.

```java
// Java classico: switch statement, exige break em cada case para evitar fallthrough
String descricao;
switch (status) {
    case ATIVO:
        descricao = "Em vigor";
        break;
    case CANCELADO:
        descricao = "Cancelado";
        break;
    default:
        descricao = "Desconhecido";
}
```

```java
// Java moderno: switch expression com "->", sem fallthrough, atribui o valor direto
String descricao = switch (status) {
    case ATIVO -> "Em vigor";
    case CANCELADO -> "Cancelado";
    default -> "Desconhecido";
};
```

```java
// yield quando o ramo precisa de mais de uma instrucao antes do valor final
int prioridade = switch (status) {
    case ATIVO -> 1;
    case CANCELADO -> {
        log.warn("Pagamento cancelado sendo repriorizado");
        yield 0;
    }
    default -> -1;
};
```

**Quando usar:** sempre que o `switch` produz um valor a ser atribuído/retornado.
**Quando evitar:** quando cada ramo só executa um efeito colateral distinto (sem produzir valor) — um
`switch` statement com `->` (ainda sem fallthrough) já resolve, sem precisar de `yield`.

## 5. Text blocks

String literal multilinha delimitada por `"""`, que preserva formatação e remove indentação
incidental automaticamente — sem concatenação nem `\n` manual.

```java
// Java classico: concatenacao com \n, dificil de ler e de manter formatado
String sql = "SELECT p.id, p.nome, p.preco\n" +
             "FROM produto p\n" +
             "WHERE p.ativo = true\n" +
             "ORDER BY p.nome";
```

```java
// Java moderno: text block preserva a formatacao do SQL, sem concatenacao
String sql = """
        SELECT p.id, p.nome, p.preco
        FROM produto p
        WHERE p.ativo = true
        ORDER BY p.nome
        """;
```

**Quando usar:** SQL, JSON, HTML ou qualquer conteúdo com quebras de linha significativas (fixtures
de teste, payloads de exemplo).
**Quando evitar:** strings de uma linha (overhead sintático sem ganho) ou quando o conteúdo exige
indentação dinâmica incompatível com a remoção automática de whitespace incidental do text block —
nesses casos, `String.format`/concatenação continuam mais previsíveis.

## 6. Virtual threads

Threads leves gerenciadas pela JVM (não mapeadas 1:1 com uma thread do sistema operacional),
permitindo dezenas de milhares de threads concorrentes com baixo custo de memória e de troca de
contexto. Ativação em aplicações Spring Boot:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

**Quando ajudam:** cargas **I/O-bound** com muitas requisições concorrentes — chamadas HTTP a outros
serviços, queries JDBC, leitura de arquivo. Cada requisição ocupa uma virtual thread barata enquanto
espera o I/O, sem esgotar um pool fixo de threads do SO.

**Quando NÃO ajudam / atenção:**
- **CPU-bound:** processamento pesado (cálculo, criptografia, serialização grande) não ganha nada
  com virtual threads — o gargalo é a CPU, não a espera por I/O; o número de núcleos continua sendo
  o limite real.
- **`synchronized` com I/O dentro (pinning):** quando uma virtual thread bloqueia dentro de um bloco
  `synchronized`, ela pode "prender" (pin) a thread carregadora (carrier thread) do SO, impedindo
  que outras virtual threads usem esse carrier enquanto o bloco não termina — anula o ganho de
  escala. A partir do JDK 24 (JEP 491) a maioria dos casos de `synchronized` deixou de causar
  pinning, mas ainda vale medir sob carga antes de assumir o ganho automático, especialmente com
  código nativo (JNI) ou bibliotecas mais antigas que seguram locks durante I/O.

## 7. `var`

Inferência de tipo para variável **local** (desde o Java 10): o compilador deduz o tipo a partir do
lado direito da atribuição. Não é tipagem dinâmica — o tipo continua fixo e checado em compilação.

```java
// Java classico: tipo repetido nos dois lados da atribuicao
List<Produto> produtos = new ArrayList<Produto>();
Map<String, Pedido> pedidosPorId = new HashMap<String, Pedido>();
```

```java
// Java moderno: var - tipo obvio pelo lado direito, sem repeticao
var produtos = new ArrayList<Produto>();
var pedidosPorId = new HashMap<String, Pedido>();
```

```java
// ERRADO - var esconde o tipo de retorno; quem le precisa abrir o metodo para saber o que e
var resultado = servicoExterno.processar(request);
```

```java
// CORRETO - tipo explicito quando o retorno do metodo nao deixa o tipo obvio para quem le
ResultadoProcessamento resultado = servicoExterno.processar(request);
```

**Quando usar:** o tipo já é óbvio pelo lado direito (`new ArrayList<Produto>()`, literal, retorno
de um método com nome descritivo). **Quando evitar:** quando `var` obscurece o tipo para quem lê o
código (retorno de método genérico, tipo relevante para o entendimento). Vale só para variáveis
locais — não existe `var` em campo, parâmetro de método ou tipo de retorno.

## 8. Guia de migração por versão

| Vindo do Java | Já pode usar | Ainda falta para chegar no 25 |
|---|---|---|
| **8** | lambdas, streams, `Optional` (já eram do próprio 8) | records, sealed classes, pattern matching, switch expressions, text blocks, `var`, virtual threads |
| **11** | tudo do 8 + `var` (inferência de tipo local, finalizada no LTS 11) | records, sealed classes, pattern matching, switch expressions, text blocks, virtual threads |
| **17** | tudo do 11 + records, sealed classes, switch expressions, text blocks, pattern matching para `instanceof` (todos finalizados até o LTS 17) | pattern matching para `switch`, record patterns, virtual threads (finalizados no LTS 21) |
| **21** | tudo do 17 + pattern matching completo para `switch`, record patterns, virtual threads (LTS 21 finaliza o Project Loom) | nada estrutural — o 21 já cobre todas as features desta skill |

**Nota JDK 25 + Spring Boot:** nenhuma feature desta lista é obrigatória ao migrar do 21 para o 25 —
o ponto de atenção é o entrypoint da aplicação. O JDK 25 introduz instance main methods (classe sem
nome, `void main()` sem `args`), mas o **plugin do Spring Boot ainda não suporta `void main()` do
JDK 25** — o `spring-boot-maven-plugin` (confirmado na versão 4.0.4 usada neste catálogo, ver
`.claude/skills/criar-aplicacao-java/assets/app-base/pom.xml`) exige o entrypoint clássico
para gerar o jar executável com o `Main-Class` correto no manifest. Por isso
`AppbaseApplication.java` mantém:

```java
@SpringBootApplication
public class AppbaseApplication {
    // JDK 25: o plugin do Spring Boot ainda exige o main classico
    public static void main(String[] args) {
        SpringApplication.run(AppbaseApplication.class, args);
    }
}
```

Não troque por `void main()` em aplicações Spring Boot deste catálogo.

## 9. Validação

Depois de aplicar qualquer modernização em código real (trocar uma classe por record, introduzir
sealed, aplicar pattern matching, etc.), peça revisão ao agent `java-revisor` antes de considerar a
mudança concluída. Ele aplica o checklist da skill `revisao-de-codigo-java` e confirma que a
modernização não alterou o comportamento nem quebrou o contrato existente.
