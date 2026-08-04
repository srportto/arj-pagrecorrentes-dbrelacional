---
name: padroes-de-projeto-java
description: Use quando o usuário pedir para aplicar ou implementar um design pattern (factory, builder, strategy, observer, decorator...), ao refatorar código rígido ou acoplado, ou ao decidir se um pattern é necessário. Inclui exemplos antes/depois de 21 patterns GoF. Gatilhos - "aplique o pattern", "usa strategy", "refatorar com factory", "esse código está rígido". Uso: agents `java-revisor`/`refatorador-java` ou invocação manual via `/padroes-de-projeto-java`; não deve ser carregada proativamente pela sessão principal.
---

# Padrões de Projeto Java (GoF)

## Visão geral

Catálogo de referência rápida dos 21 padrões de projeto GoF (criacionais, estruturais e
comportamentais), com exemplos reais **ANTES/DEPOIS** extraídos dos projetos de referência em
`docs/patterns-arquitetura-java/`. Use para decidir **qual** pattern resolve um problema concreto e
para saber quando **não** aplicar nenhum pattern.

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

\* Mediator é **comportamental** no catálogo clássico do GoF; aqui o código de referência mora no
projeto `estructural-patterns-ref`, por isso está em
[references/estruturais.md](references/estruturais.md) (com nota explicando a divergência).

## Quando NÃO aplicar pattern

Nem todo código "rígido" precisa de um pattern. Aplicar pattern sem necessidade real é
over-engineering — adiciona indireção, classes e complexidade cognitiva sem reduzir problema algum.
Três armadilhas comuns:

1. **Interface com 1 implementação, sem variação prevista** — criar `interface PagamentoService` só
   porque "no futuro pode ter outro jeito de pagar" quando hoje só existe `PagamentoServiceImpl` é
   abstração especulativa. Espere a segunda implementação aparecer de verdade.
2. **Factory para `new` simples** — sem lógica condicional na criação, `PedidoFactory.criar()` que só
   faz `return new Pedido(...)` é indireção sem ganho. Chame `new Pedido(...)` direto.
3. **Singleton onde injeção resolve** — em Spring, um bean `@Service`/`@Component` já é singleton por
   padrão (gerenciado pelo container). `getInstance()` estático manual duplica essa responsabilidade
   e piora testabilidade (não dá para injetar mock/instância isolada por teste).

## O pattern preferido do projeto: Strategy por lista injetada

Quando o problema é "escolher um serviço/produto entre vários candidatos em runtime", o padrão deste
repositório **não** usa uma factory dedicada — usa `List<Interface>` injetada pelo Spring, com cada
implementação se autodeclarando capaz (ou não) de tratar a requisição. Fonte:
`docs/based-java-aplication.md` ("Strategy Pattern para Múltiplos Produtos") — reconstrução
ilustrativa, não existe `.java` literal em `docs/` para copiar:

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
// DdaAutoService, BoletoAutoService etc. seguem o mesmo formato — um @Service por produto

// Spring injeta TODAS as implementações de ContratacaoService automaticamente; sem factory,
// sem if/switch por tipo — adicionar produto novo = criar um @Service novo, nada existente muda
@Service
public class ContratacaoOrquestradorService {
    private final List<ContratacaoService> servicos;

    public ContratacaoOrquestradorService(List<ContratacaoService> servicos) { this.servicos = servicos; }

    public AutorizacaoResponse contratar(CriarAutorizacaoRequest request) {
        return servicos.stream()
                .filter(servico -> servico.validaContratacaoSuportada(request))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Produto não suportado"))
                .contratar(request);
    }
}
```

Prefira esta forma (Strategy + lista injetada) a uma `ProdutoStrategyFactory` sempre que as
implementações já são beans do Spring e a seleção pode virar um predicado simples
(`validaXSuportada(request)`).

## Referências por categoria

- **Criacionais** (Builder, Factory Method, Abstract Factory, Singleton, Prototype) —
  [references/criacionais.md](references/criacionais.md)
- **Estruturais** (Adapter, Bridge, Composite, Decorator, Facade, Flyweight, Proxy, Mediator) —
  [references/estruturais.md](references/estruturais.md)
- **Comportamentais** (Chain of Responsibility, Command, Iterator, Memento, Observer, State,
  Strategy, Template Method) — [references/comportamentais.md](references/comportamentais.md)

Cada entrada traz: problema (2-3 linhas), exemplo ANTES/DEPOIS resumido, e quando usar/evitar.

## Validação

Depois de aplicar um pattern em código real (não apenas em um exemplo didático), peça revisão ao
agent `java-revisor` antes de considerar a mudança concluída. O objetivo é confirmar que o pattern
resolveu o problema real do código (e não introduziu indireção desnecessária — ver "Quando NÃO
aplicar pattern" acima).
