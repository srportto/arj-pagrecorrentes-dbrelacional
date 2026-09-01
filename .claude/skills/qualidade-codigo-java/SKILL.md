---

name: qualidade-codigo-java
description: 'Guia de clean code aplicado a Java — DRY, KISS, YAGNI, naming, imutabilidade, `Optional`, streams, tratamento de exceção, Object Calisthenics — e refactorings do Fowler (Remove Parameter, Extract Method, Replace Magic Number, etc.). É o lado "ativo" da revisão: `revisao-de-codigo-java` diz o que revisar; esta diz como aplicar. Uso: sessão principal e agent `java-construtor` (carregada proativamente quando código Java for gerado/alterado); também `java-revisor`/`refatorador-java` ou `/qualidade-codigo-java`.'
license: MIT
metadata:

  author: https://github.com/srportto/srportto
  version: "1.3.0"
  domain: code-quality
  triggers: clean code, boas praticas, refatorar, DRY, KISS, YAGNI, imutabilidade, Optional, streams, Fowler, Object Calisthenics, Wrap All Primitives, First Class Collections, Law of Demeter, Tell Don't Ask
  role: reference
  scope: code-quality
  output-format: code
  related-skills: revisao-de-codigo-java, padroes-de-projeto-java, refatorador-java, java-moderno
---

# Qualidade de Codigo Java (clean code + refactoring + Object Calisthenics)

## Visao geral

Guia de **aplicacao** de clean code em Java - DRY, KISS, YAGNI, nomenclatura, imutabilidade,
`Optional`, streams, exception handling - e de refactorings do catalogo do Fowler (Remove Parameter,
Extract Method, Replace Magic Number, etc.) e de **Object Calisthenics** (Tell Don't Ask, Wrap All
Primitives, First Class Collections, One Dot Per Line, No Classes With More Than Two Instance
Variables, Don't Use Else, Don't Abbreviate). Esta skill e o "lado ativo" da revisao: a
`revisao-de-codigo-java` diz **o que revisar** com checklist e severidades; esta skill diz **como
aplicar** o que a revisao aponta.

**Carregamento proativo:** esta skill deve ser consultada **durante a geracao** de codigo Java -
nao so depois, na revisao. Sempre que a sessao principal ou o agent `java-construtor` for
escrever uma classe, metodo ou refactoring Java novo, aplique DRY/KISS/YAGNI, Object Calisthenics
e as convencoes de nomenclatura abaixo antes de entregar o codigo - nao espere o `java-revisor`
apontar a violacao depois.

**Quando NAO usar:** para revisar um diff/PR com checklist por severidade, use
`revisao-de-codigo-java` (ela referencia esta aqui). Para a regra de dependencia entre camadas
(`domain`/`application`/`infrastructure`), use `arquitetura-limpa-java`. Para JPA/Hibernate (N+1, dirty
checking), use `persistencia-jpa`. Para logging (formato, MDC), use `padrao-de-logs-java`.

## Clean code - principios com exemplo

> **Coesao com `revisao-de-codigo-java`:** esta skill e o "lado ativo" (o **como** aplicar cada
> refactoring). A `revisao-de-codigo-java` e o "lado passivo" (o **o que** revisar com checklist
> e severidades). Mesmo formato de exemplo (Codigo Nao Aderente / Violacao e Explicacao /
> Exemplo de Refatoracao), mesmas terminologias (`Magic Number`, `Primitive Obsession`, `Guard
> Clause`, `Tell Don't Ask`).

> **Principio-mestre (Clean Code for AI):** alem de bom para humanos, todo codigo deste
> catalogo deve estar **otimizado para a janela de contexto do LLM** - nomes grepaveis,
> metodos curtos, arquivos pequenos, tipos explicitos e comentarios "por que". Cada secao
> abaixo reforca esse objetivo.

### DRY - Don't Repeat Yourself

**[Codigo Nao Aderente]:**
```java
// logica de validacao duplicada em dois metodos
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

**[Violacao e Explicacao]:** mesma validacao em 2 lugares - a 3a ocorrencia (em
`importarEmLote`, por exemplo) confirma o padrao. Manter a duplicacao significa N lugares para
corrigir quando a regra mudar.

**[Exemplo de Refatoracao]:**
```java
// fonte unica: metodo privado resolve sem criar interface/factory para o futuro
public class UsuarioService {
    public void criarUsuario(UsuarioRequest req)  { validarEmail(req.getEmail()); /* ... */ }
    public void atualizarUsuario(UsuarioRequest req) { validarEmail(req.getEmail()); /* ... */ }

    private void validarEmail(String email) {
        if (email == null || !email.contains("@")) {
            throw new ValidationException("Email invalido");
        }
    }
}
```

> **DRY com bom senso:** regra das 3 ocorrencias - na 1a e 2a, duplicar pode ser mais barato que a
> abstracao errada; extraia na 3a. Nao crie `EmailValidator` com interface e implementacao unica "para
> o futuro" - abstracao especulativa e over-engineering (ver `padroes-de-projeto-java`, secao "Quando
> NAO aplicar pattern").

### KISS - Keep It Simple / YAGNI - You Aren't Gonna Need It

**[Codigo Nao Aderente]:**
```java
// sobre-engenharia para 1 implementacao, sem segunda variacao a vista
public interface UserFactory {
    User createUser();
}
public class ConcreteUserFactory implements UserFactory {
    public User createUser() { return new User(); }
}
```

**[Violacao e Explicacao]:** interface + implementacao unica **"para o futuro"** e a abstracao
especulativa classica (YAGNI). O custo (mais arquivos para ler, mais para o agente raciocinar)
nao traz beneficio enquanto houver 1 variante.

**[Exemplo de Refatoracao]:**
```java
// chamada direta; implemente a abstracao quando a segunda variacao aparecer de fato
public User createUser() { return new User(); }
```

## Convencoes de nomenclatura (Object Calisthenics: Don't Abbreviate)

A regra do Object Calisthenics "Don't Abbreviate" orienta a nunca usar nomes abreviados: nomes
com significado completo ajudam no entendimento, tornam o `rg "NomeClasse"` efetivo e previnem
falhas de design. Nomes com 3+ letras continuam legiveis para o LLM.

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

**Nomes que revelam intencao e sao grepaveis** (nao abrevie sem motivo):

**[Codigo Nao Aderente]:**
```java
// abreviacoes obscuras e nomes genericos nao sao grepaveis
public List<Produto> get(String s) { ... }
public boolean chk(String str) { ... }
private static final int N = 100;
public class Handler { public void handle(String p, String rng) { ... } }
```

**[Violacao e Explicacao]:** nomes genericos (`Handler`, `get`, `N`, `chk`, `p`, `rng`)
poluem a busca lexical, escondem a intencao e violam a regra "Don't Abbreviate" do Object
Calisthenics. O agente tem que ler o corpo para descobrir o que o metodo faz. Se pesquisar pelo
nome retorna coisas irrelevantes, o nome esta ruim para a IA.

**[Exemplo de Refatoracao]:**
```java
// nome diz o que faz; busca lexical (rg "AutorizacaoExpiradaHandler") cai direto
public List<Produto> buscarAtivosPorCategoria(String categoria) { ... }
public boolean precoEhValido(BigDecimal preco) { ... }
private static final int TAMANHO_MAXIMO_PAGINA = 100;
public class AutorizacaoExpiradaHandler {
    public void expirarAutorizacao(AutorizacaoId id, MotivoExpiracao motivo) { ... }
}
```

> **Nomes genericos proibidos** (poluem `grep`, escondem intencao): `Handler`, `Manager`,
> `Helper`, `Util`, `Data`, `Process`, `Info`, `Common`, `Base`. Use nomes de dominio.
> Excecao: `Manager` e aceitavel **quando** o dominio e o proprio gerenciado
> (`PixBufferRingPartitionPurgeManager`), nunca sozinho.

> Use portugues ou ingles consistentemente dentro do mesmo pacote/classe - nao misture.

### Parametros de metodo ricos e nao abreviados

A regra "Don't Abbreviate" vale tambem para **parametros**: nome completo, sem sigla, que revela o
que o valor representa - inclusive a unidade de medida quando for numerico ou temporal.

**[Codigo Nao Aderente]:**
```java
// parametros abreviados obrigam o agente a abrir o corpo do metodo para decifrar o dominio
public void register(String fn, String ln, int age, double amt) { ... }
public void schedule(long timeout, long delay) { ... }
```

**[Violacao e Explicacao]:** `fn`, `ln`, `amt` escondem nome/sobrenome/valor; `timeout` e `delay`
sem unidade obrigam o caller a abrir a implementacao (ou a documentacao) para saber se e
milissegundos ou segundos - erro classico de integracao entre servicos.

**[Exemplo de Refatoracao]:**
```java
// nomes completos e, quando numerico/temporal, com a unidade explicita no proprio nome
public void registerUser(String firstName, String lastName, int ageInYears, double transactionAmount) { ... }
public void schedule(long timeoutInMilliseconds, long delayInSeconds) { ... }
```

> **Grupo de parametros relacionados:** quando os mesmos parametros viajam juntos em varios
> metodos (ex.: `latitude`/`longitude` sempre juntos), nao adicione mais parametros individuais -
> agrupe em um value object (`Coordinate`) - ver "Introduce Parameter Object" e "Primitive
> Obsession" mais abaixo.

> **Valor restrito a um conjunto conhecido:** parametro tipo `String status` ou `int tipo` que so
> aceita alguns valores validos deve virar `enum` (`BookingStatus status`), nao um primitivo
> generico - ver "Replace Magic Number with Symbolic Constant" mais abaixo.

## Imutabilidade

**[Codigo Nao Aderente]:**
```java
// classe com setters publicos: estado mutavel depois da construcao
public class Market {
    private Long id;
    private String name;
    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
}
```

**[Violacao e Explicacao]:** setters publicos expoem o estado interno mutavel; o chamador
pode alterar `id`/`name` apos a construcao, quebrando invariantes do dominio e criando bugs
sutis em fluxos assincronos.

**[Exemplo de Refatoracao]:**
```java
// record para DTOs e value objects (imutavel, equals/hashCode/toString gerados)
public record MarketDto(Long id, String name, MarketStatus status) {}

// ou classe com final fields e getters only
public class Market {
    private final Long id;
    private final String name;
    // getters only, no setters
}
```

Records sao o padrao deste catalogo (ver `java-moderno`): use para DTOs, value objects, chaves
compostas (`IdAutorizacao`). Nao use records quando precisar de mutabilidade ou heranca.

## Optional - uso correto

**[Codigo Nao Aderente]:**
```java
// get() sem verificar presenca
public Market buscarPorSlug(String slug) {
    Optional<Market> market = marketRepository.findBySlug(slug);
    return market.get();   // NoSuchElementException se vazio
}
```

**[Violacao e Explicacao]:** `Optional.get()` sem `.orElse`/`.orElseThrow`/`.isPresent()` joga
a decisao para o `NoSuchElementException` em runtime; o caller nao tem como reagir.

**[Exemplo de Refatoracao]:**
```java
// retorne Optional de metodos find*, use map/flatMap em vez de get() direto
public Market buscarPorSlug(String slug) {
    return marketRepository.findBySlug(slug)
        .orElseThrow(() -> new EntityNotFoundException("Market not found: " + slug));
}
```

## Streams - pipelines curtos, sem efeito colateral

**[Codigo Nao Aderente]:**
```java
// forEach com mutacao de lista externa
List<String> nomesAtivos = new ArrayList<>();
markets.stream().forEach(m -> {
    if (m.isAtivo()) {
        nomesAtivos.add(m.name().toUpperCase());
    }
});
```

**[Violacao e Explicacao]:** `forEach` capturando variavel externa e o anti-pattern classico
de stream; forca o agente a rastrear o efeito colateral e quebra paralelizacao futura.

**[Exemplo de Refatoracao]:**
```java
// pipeline curto, transformacao pura
List<String> names = markets.stream()
    .filter(Market::isAtivo)
    .map(m -> m.name().toUpperCase())
    .toList();
```

Quando o pipeline exigiria multiplos `flatMap`/estado acumulado so para simular um `for`, prefira o
loop explicito - clareza vale mais que "tudo em stream".

## Exception handling

- Use **unchecked exceptions** para erros de dominio (`BusinessException` - mapeada para 422 pelo
  handler central; ver `arquitetura-limpa-java`).
- **Crie excecoes especificas do dominio** (`MarketNotFoundException`) em vez de `RuntimeException`
  generica.
- **Evite** `catch (Exception ex)` amplo, a menos que seja para relancar/logar centralmente.
- **Sempre preserve a causa** (`throw new ApplicationException(msg, e)`) - perder a stack trace
  original torna investigacao quase impossivel.
- **Recursos** - sempre try-with-resources; `close()` manual nao executa se o codigo anterior lancar.
- **Mensagens de erro claras** - a mensagem deve dizer o que deu errado + identificador da
  operacao. Mensagens vagas forcam o agente a gastar turnos extras para descobrir a causa.

**[Codigo Nao Aderente]:**
```java
// perde a causa
try {
    integracaoClient.enviar(pedido);
} catch (IOException e) {
    throw new RuntimeException(e.getMessage());
}
```

**[Violacao e Explicacao]:** perde a `Throwable cause` (stack trace original) e produz
mensagem generica sem contexto da operacao; investigacao quase impossivel depois.

**[Exemplo de Refatoracao]:**
```java
// especifica, com causa preservada
try {
    integracaoClient.enviar(pedido);
} catch (IOException e) {
    throw new ApplicationException("Falha ao enviar pedido " + pedido.id() + " para integracao", e);
}
```

## Genericos e type safety (tipos explicitos para IA)

**[Codigo Nao Aderente]:**
```java
// raw type; agente precisa inferir
public Map indexById(Collection items) { ... }   // sem type safety
```

**[Violacao e Explicacao]:** codigo sem anotacoes de tipo ou com raw types obriga agentes e
humanos a inferirem o que entra e sai, gerando falhas. O agente poupa trabalho de descoberta em
codigos tipados.

**[Exemplo de Refatoracao]:**
```java
// generic explicito
public <T extends Identifiable> Map<Long, T> indexById(Collection<T> items) { ... }
```

### Tipo de parametro: prefira interface a implementacao concreta

Assinatura de metodo deve receber (e retornar, quando fizer sentido) o tipo mais generico que
atenda o contrato - normalmente uma interface (`List`, `Map`, `Set`) - nunca a implementacao
concreta (`ArrayList`, `HashMap`, `HashSet`). Isso desacopla o chamador da escolha de estrutura
interna e permite trocar a implementacao sem quebrar callers.

**[Codigo Nao Aderente]:**
```java
// amarra o caller a ArrayList; List.of(...) (imutavel) ou LinkedList exigiriam copia so pra chamar
public void processarClientes(ArrayList<String> customerNames) { ... }
```

**[Violacao e Explicacao]:** o parametro exige especificamente `ArrayList`; o metodo nao deveria
se importar com a implementacao, so com o contrato (`List`). Um caller com `List.of(...)` ou
`LinkedList` precisa copiar a colecao so para satisfazer a assinatura.

**[Exemplo de Refatoracao]:**
```java
// aceita qualquer List; caller escolhe a implementacao que fizer sentido
public void processarClientes(List<String> customerNames) { ... }
```


## Tell, Don't Ask & No Getters/Setters (Object Calisthenics)

O codigo cliente **nao deve** perguntar o estado interno de um objeto para tomar uma decisao por
ele - a propria classe deve expor **metodos comportamentais** que realizam a acao com seus
proprios dados. A regra "No Getters/Setters/Properties" do Object Calisthenics foca no
encapsulamento de comportamentos e evita a exposicao indevida que viola a orientacao a objetos.
Getters/setters em entidades e value objects de dominio produzem **dominio anemico**: as regras
ficam espalhadas no service e a classe vira so um saco de dados. Uma classe exposta a manipulacao
de estado por fora gera separacao entre dados e comportamento.

> **Quando NAO aplicar:** DTOs de borda (request/response HTTP, mensagens de fila) **precisam**
> de getters para serializacao Jackson/Avro. A regra vale para entidades de dominio e value
> objects. Ver `java-moderno` secao "Records" para quando usar `record` vs classe cheia.

**[Codigo Nao Aderente]:**
```java
// Dominio anemico: o objeto e um saco de dados, a acao e feita de fora
public void aplicarDesconto(Produto produto) {
    if (produto.getPreco().compareTo(new BigDecimal("50")) > 0) {
        produto.setPreco(produto.getPreco().subtract(new BigDecimal("10")));
    }
}
```

**[Violacao e Explicacao]:**
1. **Ferimento de encapsulamento e do principio "Tell, Don't Ask"**: o cliente pergunta o preco
   para decidir a regra de negocio.
2. **Object-Orientation Abuser** (Refactoring Guru): a classe `Produto` e um saco de dados; toda
   a regra "desconto" vive no `Service`, espalhada.
3. **Concorrencia**: duas operacoes simultaneas podem ler o mesmo preco e terminar em
   inconsistencia (lost update) - encapsular permite usar lock otimista dentro de `Produto`.
4. **Janela de contexto do LLM**: o agente precisa ler 2 arquivos (Produto + Service) para
   entender uma unica regra; encapsular reduz para 1.
5. Se a regra mudar, sera necessario cacar onde esse getter/setter foi usado no codigo.

**[Exemplo de Refatoracao]:**
```java
public class Produto {
    private BigDecimal preco;

    // O objeto controla e protege sua propria regra
    public void aplicarDesconto(BigDecimal valorDesconto) {
        if (this.preco.compareTo(new BigDecimal("50")) > 0) {
            this.preco = this.preco.subtract(valorDesconto);
        }
    }
}

// O cliente so envia o comando
public void processar(Produto produto) {
    produto.aplicarDesconto(new BigDecimal("10"));
}
```

## Primitive Obsession & Wrap All Primitives And Strings (Object Calisthenics)

Nao use tipos primitivos (`long`, `int`, `String`, `double`, `BigDecimal` solto) para
representar conceitos com comportamento, validacao ou semantica proprios. A regra "Wrap All
Primitives And Strings" do Object Calisthenics determina que variaveis primitivas com
comportamento especifico de dominio (como validacoes proprias) devem ser encapsuladas em Objetos
(value objects imutaveis) que carregam parsing, validacao e operacoes.

> **Exemplos de encapsulamento obrigatorio neste catalogo:** `Money` (valor + moeda), `Cpf`,
> `Cnpj`, `AutorizacaoId`, `IdContrato`, `PartitionId`, `PurgeRange`, `PeriodoVigencia`
> (inicio + fim), `ChavePix`.

**[Codigo Nao Aderente]:**
```java
public class Pessoa {
    private String cpf; // Obsessao por tipo primitivo

    public Pessoa(String cpf) {
        if (cpf == null || cpf.length() != 11) {
            throw new IllegalArgumentException("CPF invalido");
        }
        this.cpf = cpf;
    }
}
```

**[Violacao e Explicacao]:** o CPF em formato `String` espalha logica de validacao pela classe.
O agente de IA precisa inferir o formato, consumindo janela de contexto. A regra de validacao
nao pertence estruturalmente a `Pessoa`.

**[Exemplo de Refatoracao]:**
```java
public record Cpf(String numero) { // Value object imutavel que contem suas regras
    public Cpf {
        if (numero == null || numero.length() != 11) {
            throw new IllegalArgumentException("CPF invalido");
        }
    }
}

public class Pessoa {
    private Cpf cpf; // Propriedade tipada e protegida
}
```

Exemplo mais rico (com operacoes de dominio):

**[Codigo Nao Aderente]:**
```java
public class PixBufferRingPartitionPurgeManager {
    public void executePurge(String particaoStr, String rangeStr) {
        // parsing fragil + regra de dominio solta em if
        int inicio = Integer.parseInt(rangeStr.split("-")[0]);
        int fim    = Integer.parseInt(rangeStr.split("-")[1]);
        int part   = Integer.parseInt(particaoStr);
        if (part >= inicio && part <= fim) {
            bufferRing.purge(part);
        }
    }
}
```

**[Violacao e Explicacao]:**
1. **Primitive Obsession** (Refactoring Guru): `String` carregando semantica de range, parsing
   repetido em todo lugar, validacao fragil (`split("-")` quebra com `"900-999-1000"`).
2. **Nomes nao grepaveis**: `Handler`/`Manager` solto, parametros `p`/`rng` ilegiveis.
3. **Janela de contexto do LLM**: o agente precisa inferir o formato da string e a regra de
   range a cada leitura. Encapsular torna o tipo auto-documentado.

**[Exemplo de Refatoracao]:**
```java
public record PartitionId(int value) {
    public PartitionId {
        if (value < 0) throw new IllegalArgumentException("partition deve ser >= 0");
    }
}

public record PurgeRange(int inicio, int fim) {
    public PurgeRange {
        if (inicio > fim) throw new IllegalArgumentException("range invalido");
    }
    public boolean contains(PartitionId p) {
        return p.value() >= inicio && p.value() <= fim;
    }
}

public class PixBufferRingPartitionPurgeManager {
    public void executePurge(PartitionId currentPartition, PurgeRange purgeRange) {
        if (purgeRange.contains(currentPartition)) {
            bufferRing.purge(currentPartition);
        }
    }
}
```

## First Class Collections (Object Calisthenics)

Qualquer classe que contenha uma colecao nao deve conter **outras** variaveis de membro. Se voce
tem um conjunto de elementos, crie uma classe dedicada exclusivamente a essa colecao, com seus
comportamentos de filtro, agrupamento e adicao encapsulados.

**[Codigo Nao Aderente]:**
```java
// Colecao misturada com outros atributos
public class Empresa {
    private String razaoSocial;
    private List<Funcionario> funcionarios;

    // Metodos que manipulam a lista se misturam com metodos da empresa
    public List<Funcionario> buscarContadores() {
        return funcionarios.stream()
            .filter(f -> f.getCargo().equals("contador"))
            .toList();
    }
}
```

**[Violacao e Explicacao]:** os comportamentos de filtro e agrupamento de funcionarios poluem a
classe `Empresa`. O acoplamento entre a colecao e a classe hospedeira dificulta evolucao e teste
isolado.

**[Exemplo de Refatoracao]:**
```java
public class QuadroFuncionarios { // First Class Collection
    private final List<Funcionario> funcionarios;

    public QuadroFuncionarios(List<Funcionario> funcionarios) {
        this.funcionarios = funcionarios;
    }

    // Comportamentos especificos tem um lar
    public List<Funcionario> buscarContadores() {
        return funcionarios.stream()
            .filter(f -> f.getCargo().equals("contador"))
            .toList();
    }
}

public class Empresa {
    private String razaoSocial;
    private QuadroFuncionarios quadro;
}
```

## One Dot Per Line / Law of Demeter (Object Calisthenics)

Evite cadeias extensas de chamadas que atravessam varios objetos. Se voce usa mais de um ponto
na mesma linha, o seu objeto e um intermediario sabendo demais sobre a estrutura dos outros
(quebra de encapsulamento - "Only talk to your immediate friends").

**[Codigo Nao Aderente]:**
```java
// Navegando a estrutura interna (quebra de encapsulamento)
String nomeChefe = funcionario.getDepartamento().getChefe().getNome();
```

**[Violacao e Explicacao]:** a estrutura interna do objeto fica exposta, dificultando a leitura
e acoplando fortemente as classes. Mudancas na hierarquia `Funcionario -> Departamento -> Chefe`
quebram todos os call sites.

**[Exemplo de Refatoracao]:**
```java
// O objeto expressa intencoes via metodos comportamentais diretos
String nomeChefe = funcionario.getNomeChefeDepartamento();
```

> **Excecoes controladas:** DTOs flattenizados para transporte de borda (`endereco.cidade.uf`)
> e fluent builders encadeados (`PedidoBuilder.com(cliente).com(item).build()`) sao
> aceitaveis - a regra se aplica a chamadas de **comportamento** que atravessam dominios.

## No Classes With More Than Two Instance Variables (Object Calisthenics)

Classes nao devem ter mais do que **duas** variaveis de instancia, forcando um alto nivel de
coesao, composicao e abstracao. O objetivo pratico e agrupar variaveis em logicas menores quando
a classe assume muitas responsabilidades - quem viola essa regra quase sempre tem mais de uma
razao para mudar (SRP ferida).

**[Codigo Nao Aderente]:**
```java
public class Funcionario {
    private String nome;
    private int idade;
    private String cargo;
    private String departamento;
}
```

**[Violacao e Explicacao]:** a classe tem muitos atributos e assume informacoes tanto pessoais
quanto contratuais. Mudancas em "informacoes pessoais" exigem mexer na mesma classe que cuida
de "informacoes de trabalho".

**[Exemplo de Refatoracao]:**
```java
public class Funcionario {
    private InformacoesPessoais dadosPessoais;   // Agrupa nome e idade
    private InformacoesTrabalho dadosTrabalho;   // Agrupa cargo e departamento
}
```

> **Quando NAO aplicar a risca:** entidades JPA e DTOs de borda naturalmente carregam
> varios campos. A regra orienta o **codigo de dominio** (agregados, value objects,
> servicos); adapte para `record` quando o objeto for puramente dados, ou divida em
> composicoes de 2 grupos.

## Replace Magic Number with Symbolic Constant (regra de negocio)

Qualquer literal numerico ou `String` com **significado de dominio** e proibido no meio de
validacoes, formulas ou `switch`/`if`. Deve virar constante nomeada, `enum` ou value object.

> **Quando NAO aplicar:** constantes matematicas universais triviais (`0`, `1`, `100` como
> percentual, indices de array) podem permanecer. A regra se aplica a literais com semantica
> de negocio desconhecida para quem nao e SME do dominio.

**[Codigo Nao Aderente]:**
```java
public void validarTransacaoPix(Conta conta, BigDecimal valor, int tipo) {
    if (valor.compareTo(new BigDecimal("50000")) > 0) {           // 50000 = ?
        throw new BusinessException("valor excede limite");
    }
    if (tipo == 1) {                                               // 1 = ?
        conta.sacar(valor);
    } else if (tipo == 2) {                                        // 2 = ?
        conta.depositar(valor);
    }
}
```

**[Violacao e Explicacao]:**
1. **Magic Numbers** (Refactoring Guru + Object Calisthenics): o significado de `50000`, `1` e
   `2` esta oculto - o agente precisa adivinhar a regra de negocio.
2. **Acoplamento de mudanca**: se o Banco Central alterar o limite regulatorio, o agente tem
   que cacar `50000` no projeto inteiro (e pode errar um).
3. **Custo de janela de contexto**: cada literal exige uma volta ao dominio para entender.

**[Exemplo de Refatoracao]:**
```java
// Limite regulado pelo Banco Central na resolucao BCB 123/2024, art. 7o paragrafo 2o.
// Nao alterar sem alinhamento com compliance.
private static final BigDecimal LIMITE_MAXIMO_PIX_AUTOMATICO = new BigDecimal("50000");

public enum TipoTransacao { SAQUE, DEPOSITO }

public void validarTransacaoPix(Conta conta, Money valor, TipoTransacao tipo) {
    if (valor.isGreaterThan(LIMITE_MAXIMO_PIX_AUTOMATICO)) {
        throw new BusinessException(String.format(
            "Valor %s excede limite PIX Automatico de %s",
            valor, LIMITE_MAXIMO_PIX_AUTOMATICO));
    }
    switch (tipo) {
        case SAQUE    -> conta.sacar(valor);
        case DEPOSITO -> conta.depositar(valor);
    }
}
```

> **Por que `enum` em vez de `int`?** Porque o compilador garante exhaustive switch em
> `sealed` types, e o `rg "TipoTransacao.SAQUE"` cai direto onde o tipo e usado.

**Constantes tecnicas** (retry, timeouts, tamanhos de pagina) seguem o mesmo principio -
qualquer literal repetido em mais de um lugar vira constante nomeada:

**[Codigo Nao Aderente]:**
```java
if (tentativas > 3) { ... }
Thread.sleep(1000L);
```

**[Exemplo de Refatoracao]:**
```java
private static final int MAX_TENTATIVAS = 3;
private static final long INTERVALO_RETRY_MS = 1_000L;
```

## Guard Clauses & Don't Use Else (Object Calisthenics)

O `else` e **proibido** neste catalogo (regra "Don't Use Else" do Object Calisthenics). Assuma o
fluxo padrao e faca validacoes atraves de Fail-Fast, Early Return ou Guard Clauses. Cada nivel
de indentacao extra multiplica o custo cognitivo do LLM para rastrear o estado da execucao.

> **Excecao:** o `else` e tolerado em `switch` expressions (Java 14+) e em pattern matching
> exaustivo de `sealed` types, que sao esgotamento, nao aninhamento.

**[Codigo Nao Aderente]:**
```java
public void processar(Pedido pedido) {
    if (pedido != null) {
        if (pedido.itens() != null) {
            if (!pedido.itens().isEmpty()) {
                if (pedido.valor().signum() > 0) {
                    executar(pedido);
                } else {
                    throw new BusinessException("valor invalido");
                }
            } else {
                throw new BusinessException("sem itens");
            }
        } else {
            throw new BusinessException("itens nulos");
        }
    }
}
```

**[Violacao e Explicacao]:**
1. **Aninhamento profundo (4 niveis)** - piramide de `if/else` torna impossivel seguir o fluxo
   principal sem perder o estado.
2. **Else explicito** - quando o `if` retorna/throw, o `else` e ruido.
3. **Metodo longo** - excede 20 linhas; viola regra de janela de contexto.
4. Para LLMs, o "else" desvia a atencao da logica continua.

**[Exemplo de Refatoracao]:**
```java
public void processar(Pedido pedido) {
    if (pedido == null || pedido.itens() == null || pedido.itens().isEmpty()) {
        return;
    }
    if (pedido.valor().signum() <= 0) {
        throw new BusinessException("valor invalido");
    }
    executar(pedido);
}
```

## Clean Code for AI (arquitetura para o agente)

Regras vitais para quando o LLM interage e edita a base de codigo:

1. **Only One Level Of Indentation Per Method & Manter Metodos Pequenos**: metodos curtos,
   contendo 1 nivel de indentacao (4-20 linhas), cabem na mesma tool call do LLM, evitando
   perda de contexto. Acima disso, **Extract Method** ate caber.
2. **Tamanho de arquivo**: 300-500 linhas. Acima disso, dividir por responsabilidade (SRP).
   Arquivos grandes sao truncados em diffs e forcam o agente a carregar contexto irrelevante.
3. **SRP (Responsabilidade Unica)**: permite edicoes isoladas via AI sem efeito colateral
   destrutivo. Uma classe com mais de uma razao para mudar gera conflitos de merge e
   dificuldade do agente em raciocinar sobre impacto.
4. **Comentarios de proveniencia**: embora comentarios obvios (`// incrementa i`,
   `// verifica se o saldo e maior que zero`) consumam tokens de IA a toa e devam sumir,
   documentacoes que explicam o **motivo** da decisao de negocio sao cruciais. Regra pratica:
   se o `git blame` + nome do metodo ja respondem "o que", o comentario e redundante. Se a
   regra veio de um oficio, um ADR ou um workaround de bug antigo, **esse** comentario
   precisa existir.
5. **Testes que o agente consegue rodar (TDD headless rapido)**: testes automatizados
   funcionam como bussola, diferenciando o agente agil do que trabalha "chutando". Sem
   feedback rapido, o LLM nao consegue validar refactorings.
6. **Injecao de Dependencias**: instanciar dependencias hardcoded (`new EmailService()` no
   construtor) impede o isolamento no momento do teste. Usar DI por construtor facilita a
   refatoracao e as suites de teste.

**[Codigo Nao Aderente]:**
```java
// ruido que come janela de contexto
// incrementa i
i++;
// verifica se o saldo e maior que zero
if (saldo.compareTo(BigDecimal.ZERO) > 0) { ... }
```

**[Violacao e Explicacao]:** `// incrementa i` e `// verifica se o saldo e maior que zero` sao
traducoes literais do codigo - `git blame` + nome do metodo ja respondem "o que". Gastam tokens
e atrapalham a leitura do agente.

**[Exemplo de Refatoracao]:**
```java
// comentario de proveniencia: explica decisao nao-obvia
// Limite regulado pelo Banco Central na resolucao BCB 123/2024, art. 7o paragrafo 2o.
// Nao alterar sem alinhamento com compliance.
private static final BigDecimal LIMITE_MAXIMO_PIX_AUTOMATICO = new BigDecimal("50000");
```

**Tipagem explicita:** assinaturas devem ser fortemente tipadas. `Map`/`List`/`Set` sem tipo,
`Object`, `String` para tudo, ou raw types obrigam o agente a inferir tipos a cada leitura.

## Bloaters e Change Preventers (centralizacao de mudanca)

Se uma unica alteracao de regra de negocio exige editar 20 arquivos diferentes, o codigo esta
mal distribuido. Tipos comuns a vigiar:

- **Primitive Obsession** (ver secao acima) - quando a regra de dominio esta no `if` solto, mudar
  a regra exige varrer o projeto.
- **Shotgun Surgery** - uma feature nova precisa tocar 5 classes? Falta um *aggregate root* ou
  *use case* que concentre a operacao. Ver `arquitetura-limpa-java`.
- **Divergent Change** - uma classe muda por motivos nao-relacionados? Extrair por
  responsabilidade (SRP).
- **Configuracao dispersa** - `@Value("${limite.pix}")` espalhado por 10 arquivos? Mover para
  um unico `@ConfigurationProperties` injetado por construtor.

---

# Refactorings do Fowler - guia rapido

Catalogo dos refactorings mais comuns em Java moderno, com exemplo (Codigo Nao Aderente /
Violacao e Explicacao / Exemplo de Refatoracao) unificado com a skill `revisao-de-codigo-java`.
Cada refactoring resolve um **cheiro** (code smell) especifico - nao aplique por aplicar.

> **Mapeamento smell -> refactoring:** Tell-Don't-Ask, Primitive Obsession, Magic Numbers e
> Guard Clauses tem secoes dedicadas acima. Esta parte cobre os refactorings mecanicos
> (Remove Parameter, Extract Method, Replace Conditional with Polymorphism, Introduce Parameter
> Object, Replace Loop with Pipeline) sem duplicar conteudo ja presente.

## Remove Parameter

**Quando:** um parametro nunca e usado, ou seu valor pode ser obtido de outro lugar (campo da classe,
constante, chamada de metodo).

**[Codigo Nao Aderente]:**
```java
// "isCloud" e recebido mas nunca influencia o resultado
public Backend selecionarBackend(long tableId, ConnectContext context, boolean isCloud) {
    return sistemaInfo.getBackend(selecionarBackendInterno(tableId, context.getCluster()));
}
```

**[Violacao e Explicacao]:** parametro morto infla a assinatura, confunde o caller sobre
qual valor passar, e e candidato permanente a "ser usado no futuro" - abstracao especulativa
(YAGNI).

**[Exemplo de Refatoracao]:**
```java
public Backend selecionarBackend(long tableId, ConnectContext context) {
    return sistemaInfo.getBackend(selecionarBackendInterno(tableId, context.getCluster()));
}
```

> Veja a skill dedicada `refactoring-remove-parameter` para a versao focada e passo-a-passo desse
> refactoring.

## Extract Method

**Quando:** um trecho de codigo tem um proposito claro e pode ser nomeado, ou voce quer reusa-lo.
**Regra pratica:** metodo com mais de 20 linhas, ou que misture "preparar/validar" com "executar",
sempre tem um `Extract Method` a oferecer.

**[Codigo Nao Aderente]:**
```java
public void processar(Pedido pedido) {
    if (pedido.getValor() == null || pedido.getValor().signum() <= 0) {
        throw new BusinessException("Valor invalido");
    }
    if (pedido.getItens() == null || pedido.getItens().isEmpty()) {
        throw new BusinessException("Pedido sem itens");
    }
    // ... logica principal comeca aqui
}
```

**[Violacao e Explicacao]:** metodo com 2 responsabilidades (validar + executar) e > 20
linhas; validacao inline polui o fluxo principal e impede teste isolado da regra.

**[Exemplo de Refatoracao]:**
```java
public void processar(Pedido pedido) {
    validar(pedido);
    // ... logica principal comeca aqui
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

## Replace Conditional with Polymorphism

**Quando:** um `switch`/`if` chain decide por **tipo** e cada ramo tem logica distinta.

**[Codigo Nao Aderente]:**
```java
// instanceof chain decide por tipo, com throw generico para tipo desconhecido
public BigDecimal calcularTaxa(Pagamento pagamento) {
    if (pagamento instanceof Pix) return BigDecimal.ZERO;
    if (pagamento instanceof Cartao) return BigDecimal.valueOf(0.03);
    throw new IllegalStateException("Tipo desconhecido");
}
```

**[Violacao e Explicacao]:** `instanceof` chain e aberta a extensao (cada novo tipo exige
editar o metodo) e o `throw` final so e detectado em runtime; o compilador nao ajuda a lembrar
todos os tipos.

**[Exemplo de Refatoracao]:**
```java
// sealed type + switch exaustivo (ver java-moderno)
public BigDecimal calcularTaxa(Pagamento pagamento) {
    return switch (pagamento) {
        case Pix p     -> BigDecimal.ZERO;
        case Cartao c  -> BigDecimal.valueOf(0.03);
        // compilador exige todos os casos se Pagamento for sealed
    };
}
```

## Introduce Parameter Object

**Quando:** um grupo de parametros viaja junto em varios metodos (e o oposto de "Primitive
Obsession" - aqui o grupo e heterogeneo, mas sempre os mesmos campos).

**[Codigo Nao Aderente]:**
```java
// grupo de 4 parametros viaja junto em varios metodos
public void buscar(LocalDate inicio, LocalDate fim, String status, int pagina) { ... }
public void exportar(LocalDate inicio, LocalDate fim, String status) { ... }
```

**[Violacao e Explicacao]:** os mesmos 4 parametros se repetem; adicionar/remover um campo
exige editar a assinatura de cada metodo e cada caller; propensao a erros de ordem (trocar
`inicio` por `fim`).

**[Exemplo de Refatoracao]:**
```java
public record FiltroPedido(LocalDate inicio, LocalDate fim, String status) {}

public void buscar(FiltroPedido filtro, int pagina) { ... }
public void exportar(FiltroPedido filtro) { ... }
```

## Replace Loop with Pipeline

**Quando:** um loop acumula resultado em uma colecao com transformacoes triviais.

**[Codigo Nao Aderente]:**
```java
// loop mutando lista externa
List<String> nomes = new ArrayList<>();
for (Produto p : produtos) {
    if (p.isAtivo()) {
        nomes.add(p.getNome().toUpperCase());
    }
}
```

**[Violacao e Explicacao]:** loop imperativo com mutacao; impede paralelizacao futura e e
mais verboso que um pipeline curto para transformacoes triviais.

**[Exemplo de Refatoracao]:**
```java
List<String> nomes = produtos.stream()
    .filter(Produto::isAtivo)
    .map(p -> p.getNome().toUpperCase())
    .toList();
```

> Ver `revisao-de-codigo-java` (item 4 - Streams) e `java-moderno` (secao Stream) para quando loop
> e preferivel a pipeline (clareza > "tudo em stream").

---

# Quem aplica o que

| Situacao | Quem | Skill |
|---|---|---|
| Aplicar refactoring em uma classe/metodo | sessao principal | esta skill |
| Revisar diff/PR com checklist de severidade | agent `java-revisor` | `revisao-de-codigo-java` |
| Remocao de parametro focada (passo-a-passo) | sessao principal | `refactoring-remove-parameter` |
| Limpar imports nao usados | sessao principal | `remover-imports-nao-usados` |
| Centralizar configuracao dispersa (Shotgun Surgery) | session/engenheiro-devops | `java-architecture` |
| Decidir onde mora um value object novo | session/java-construtor | `arquitetura-limpa-java` |
