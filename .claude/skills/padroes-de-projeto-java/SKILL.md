---
name: padroes-de-projeto-java
description: Use quando o usuário pedir para aplicar ou implementar um design pattern (factory, builder, strategy, observer, decorator...), ao refatorar código rígido ou acoplado, ou ao decidir se um pattern é necessário. Inclui exemplos antes/depois de 21 patterns GoF. Gatilhos - "aplique o pattern", "usa strategy", "refatorar com factory", "esse código está rígido".
---

# Padrões de Projeto Java (GoF)

## Visão geral

Catálogo de referência rápida dos 21 padrões de projeto GoF (criacionais, estruturais e
comportamentais), com exemplos reais **ANTES/DEPOIS** extraídos dos projetos de referência em
`docs/patterns-arquitetura-java/`. Use esta skill para decidir **qual** pattern resolve um problema
concreto, ver como ele fica em código antes e depois de aplicado, e saber quando **não** aplicar
nenhum pattern.

## Tabela problema → pattern

| Problema | Pattern | Categoria |
|----------|---------|-----------|
| Construção complexa (muitos parâmetros opcionais/obrigatórios) | Builder | Criacional |
| Criação por tipo em runtime | Factory Method | Criacional |
| Famílias de objetos relacionados | Abstract Factory | Criacional |
| Instância única (⚠️ alerta de testabilidade — ver "Quando NÃO aplicar") | Singleton | Criacional |
| Clonagem de objetos | Prototype | Criacional |
| Interfaces incompatíveis | Adapter | Estrutural |
| Abstração × implementação variando independentemente | Bridge | Estrutural |
| Árvore todo-parte (composição hierárquica) | Composite | Estrutural |
| Comportamento dinâmico (adicionado em runtime) | Decorator | Estrutural |
| Simplificar acesso a um subsistema | Facade | Estrutural |
| Muitos objetos baratos (compartilhar estado repetido) | Flyweight | Estrutural |
| Controle de acesso a um recurso | Proxy | Estrutural |
| Cadeia de tratadores | Chain of Responsibility | Comportamental |
| Ação como objeto | Command | Comportamental |
| Percorrer uma coleção sem expor sua estrutura | Iterator | Comportamental |
| Snapshot de estado (undo/histórico) | Memento | Comportamental |
| Notificar dependentes de uma mudança | Observer | Comportamental |
| Comportamento que muda por estado interno | State | Comportamental |
| Algoritmos intercambiáveis | Strategy | Comportamental |
| Esqueleto de algoritmo com passos variáveis | Template Method | Comportamental |
| Comunicação centralizada entre componentes | Mediator | Estrutural* |

\* Mediator é classificado como **comportamental** no catálogo clássico do GoF. Neste repositório, o
código de referência mora no projeto `estructural-patterns-ref` — por isso está documentado em
[references/estruturais.md](references/estruturais.md), junto com uma nota explicando a divergência.

## Quando NÃO aplicar pattern

Nem todo código "rígido" precisa de um pattern. Aplicar pattern sem necessidade real é
over-engineering — adiciona indireção, classes e complexidade cognitiva sem reduzir problema algum.
Três armadilhas comuns:

1. **Interface com 1 implementação, sem variação prevista** — criar uma `interface PagamentoService`
   só porque "no futuro pode ter outro jeito de pagar" quando hoje só existe `PagamentoServiceImpl`
   é abstração especulativa. Espere a segunda implementação aparecer de verdade antes de extrair a
   interface.
2. **Factory para `new` simples** — se só existe um tipo concreto sendo criado e não há lógica
   condicional nenhuma na criação, uma `PedidoFactory.criar()` que só faz `return new Pedido(...)` é
   indireção sem ganho. Chame `new Pedido(...)` direto.
3. **Singleton onde injeção resolve** — em uma aplicação Spring, um bean `@Service`/`@Component` já é
   singleton por padrão (gerenciado pelo container). Implementar `getInstance()` estático manual
   duplica essa responsabilidade e piora testabilidade (não dá para injetar um mock/instância isolada
   por teste).

## O pattern preferido do projeto: Strategy por lista injetada

Quando o problema é "escolher um serviço/produto entre vários candidatos em runtime", o padrão deste
repositório **não** usa uma factory dedicada — usa `List<Interface>` injetada pelo Spring, com cada
implementação se autodeclarando capaz (ou não) de tratar a requisição.

Fonte: `docs/based-java-aplication.md` (seção "Padrões de Design Utilizados" e "Strategy Pattern para
Múltiplos Produtos"). Reconstrução ilustrativa do comportamento documentado — não existe um arquivo
`.java` literal em `docs/` para copiar (o próprio documento afirma que os únicos exemplos em
`docs/strategyProduto/` são didáticos, não código de produção):

```java
// Cada produto se autodeclara capaz de tratar a requisição — sem factory dedicada
public interface ContratacaoService {
    boolean validaContratacaoSuportada(CriarAutorizacaoRequest request);
    AutorizacaoResponse contratar(CriarAutorizacaoRequest request);
}

@Service
public class PixAutoService implements ContratacaoService {
    @Override
    public boolean validaContratacaoSuportada(CriarAutorizacaoRequest request) {
        return TipoProduto.PIX_AUTO.equals(request.tipoProduto());
    }
    // ...
}

@Service
public class DdaAutoService implements ContratacaoService {
    @Override
    public boolean validaContratacaoSuportada(CriarAutorizacaoRequest request) {
        return TipoProduto.DDA_AUTO.equals(request.tipoProduto());
    }
    // ...
}

// Spring injeta TODAS as implementações de ContratacaoService automaticamente
@Service
public class ContratacaoOrquestradorService {
    private final List<ContratacaoService> servicos;

    public ContratacaoOrquestradorService(List<ContratacaoService> servicos) {
        this.servicos = servicos;
    }

    public AutorizacaoResponse contratar(CriarAutorizacaoRequest request) {
        return servicos.stream()
                .filter(servico -> servico.validaContratacaoSuportada(request))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Produto não suportado"))
                .contratar(request);
    }
}
```

**Por que sem factory:** adicionar um novo produto é criar uma nova classe `@Service` que implementa
`ContratacaoService` — nenhum código existente (nem uma factory) precisa ser tocado. O próprio Spring
monta a lista via injeção de dependência, então não há um ponto central de `if`/`switch` por tipo para
manter.

Prefira esta forma (Strategy + lista injetada) a criar uma `ProdutoStrategyFactory` sempre que:
- as implementações já são beans gerenciados pelo Spring;
- a seleção pode ser expressa como um predicado simples (`validaXSuportada(request)`).

## Referências por categoria

- **Criacionais** (Builder, Factory Method, Abstract Factory, Singleton, Prototype) —
  [references/criacionais.md](references/criacionais.md)
- **Estruturais** (Adapter, Bridge, Composite, Decorator, Facade, Flyweight, Proxy, Mediator) —
  [references/estruturais.md](references/estruturais.md)
- **Comportamentais** (Chain of Responsibility, Command, Iterator, Memento, Observer, State,
  Strategy, Template Method) — [references/comportamentais.md](references/comportamentais.md)

Cada entrada de reference traz: problema (2-3 linhas), exemplo ANTES resumido, exemplo DEPOIS
resumido, e quando usar/evitar.

## Validação

Depois de aplicar um pattern em código real (não apenas em um exemplo didático), peça revisão ao
agent `java-revisor` antes de considerar a mudança concluída. O objetivo é confirmar que o pattern
resolveu o problema real do código (e não introduziu indireção desnecessária — ver "Quando NÃO
aplicar pattern" acima).
