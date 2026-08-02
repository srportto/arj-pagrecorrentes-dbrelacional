---
name: revisao-de-codigo-java
description: Use quando o usuário pedir revisão de código Java ("revise", "code review", "está bom?", "melhore este código"), antes de um merge, ou após qualquer geração significativa de código. Consolida clean code, tratamento de erros, imutabilidade e testes em um checklist único por severidade. Uso: agents `java-revisor`/`java-especialista`/`projetista-api` ou invocação manual via `/revisao-de-codigo-java`; não deve ser carregada proativamente pela sessão principal.
---

# Revisão de Código Java

## Visão geral

Checklist único de revisão de código Java/Spring Boot, organizado por severidade. Consolida três
fontes: princípios de clean code (DRY/KISS/YAGNI) e contrato de API, padrões de código Java
(nomenclatura, imutabilidade, `Optional`, streams) e categorias de revisão de um revisor genérico
(correção, testes, complexidade). Use sempre que for revisar um diff, uma classe, um PR, ou logo
depois de gerar código Java significativo.

**Quando NÃO usar:** para dúvida pontual sobre em qual camada um código deve viver, use
`arquitetura-limpa-java` diretamente (esta skill só referencia o checklist dela no grupo
"Arquitetura"). Para revisar somente o padrão de logs, use `padrao-de-logs-java`. Esta skill é a
fonte de verdade usada tanto para autorrevisão quanto pelos agents `java-revisor` e
`java-especialista` — veja "Quem revisa o quê" abaixo para saber qual agent invocar.

## Fluxo de revisão

1. **Entender a intenção da mudança** — antes de aplicar qualquer item do checklist, entenda o que o
   diff faz e por quê, para não reportar como "problema" uma decisão consciente do autor (ex.: um
   loop no lugar de stream porque é mais claro naquele caso específico).
2. **Passar o checklist** — aplique os 10 grupos da seção "Checklist" abaixo sobre o código/diff.
3. **Reportar achados agrupados por severidade** — cada achado leva `arquivo:linha` e uma explicação
   objetiva do porquê é um problema (siga o modelo em "Formato do relatório").
4. **Reconhecer o que está bom** — toda revisão termina com pontos positivos, não só críticas, para
   reforçar práticas corretas e manter a revisão construtiva.

## Severidades

| Severidade | Definição | Efeito |
|---|---|---|
| **Crítico** | Bug real, vazamento de recurso, falha de segurança, ou quebra de contrato (API, dados, retrocompatibilidade) | **Bloqueia** — não deve ir para produção/merge sem correção |
| **Importante** | Problema de manutenibilidade ou performance com impacto provável em produção, mas que não quebra nada hoje | Deveria ser corrigido antes do merge; pode virar débito técnico registrado se houver justificativa |
| **Menor** | Estilo, nomenclatura, preferência — não afeta comportamento nem manutenibilidade de forma relevante | Sugestão; não bloqueia |

## Checklist

### 1. Correção

**Null-safety** — métodos `find*` retornam `Optional`; nunca chame `Optional.get()` sem verificar
presença:

```java
// ERRADO - Optional.get() sem verificar presenca, risco de NoSuchElementException
public Produto buscarPorId(Long id) {
    Optional<Produto> produto = produtoRepository.findById(id);
    return produto.get();
}

// CORRETO - metodos find* retornam Optional; a borda decide o que fazer na ausencia
public Produto buscarPorId(Long id) {
    return produtoRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Produto nao encontrado: " + id));
}
```

**Exceções com contexto** — preserve a causa original e diga o que estava sendo feito:

```java
// ERRADO - perde a causa original (e) e nao diz o que estava sendo feito
try {
    integracaoClient.enviar(pedido);
} catch (IOException e) {
    throw new RuntimeException(e.getMessage());
}

// CORRETO - preserva a causa (e) e adiciona contexto do que falhou
try {
    integracaoClient.enviar(pedido);
} catch (IOException e) {
    throw new ApplicationException("Falha ao enviar pedido " + pedido.id() + " para integracao", e);
}
```

**Recursos com try-with-resources** — `close()` manual não executa se o código anterior lançar:

```java
// ERRADO - close() nao executa se ler() lancar excecao, vazando o recurso
InputStream in = new FileInputStream(arquivo);
String conteudo = ler(in);
in.close();

// CORRETO - try-with-resources garante o fechamento mesmo em caso de excecao
try (InputStream in = new FileInputStream(arquivo)) {
    return ler(in);
}
```

### 2. Contrato HTTP

Status correto por origem do erro (400 validação / 422 negócio / 500 técnico), e DTOs de borda
imutáveis:

```java
// ERRADO - regra de negocio violada devolvendo excecao generica, que o handler mapeia como 500
@PostMapping
public ResponseEntity<ProdutoResponse> criar(@RequestBody CriarProdutoRequest request) {
    if (request.preco().signum() <= 0) {
        throw new RuntimeException("preco invalido"); // handler generico -> 500, deveria ser 422
    }
    Produto criado = service.criar(request);
    return ResponseEntity.ok(mapper.paraResposta(criado));
}

// CORRETO - 400 (formato) via @Valid no record de request, 422 (negocio) via BusinessException
@PostMapping
public ResponseEntity<ProdutoResponse> criar(@RequestBody @Valid CriarProdutoRequest request) {
    // @NotNull/@DecimalMin no record cobrem o 400 (falha de validacao de formato)
    Produto criado = service.criar(request); // service delega a produto.validar(), que lanca
                                              // BusinessException (422) se a regra de negocio falhar
    return ResponseEntity.created(URI.create("/produtos/" + criado.getId()))
            .body(mapper.paraResposta(criado));
}
```

```java
// ERRADO - DTO mutavel com setters: o contrato de borda pode ser alterado apos a criacao
public class ProdutoResponse {
    private Long id;
    private String nome;
    public void setId(Long id) { this.id = id; }
    public void setNome(String nome) { this.nome = nome; }
}

// CORRETO - record imutavel: contrato de borda fixado na construcao, sem setters
public record ProdutoResponse(Long id, String nome, BigDecimal preco) {}
```

### 3. Imutabilidade

Records para dados, `final` em campos, sem setters desnecessários.

```java
// ERRADO - campo mutavel com setter publico: qualquer chamador pode alterar o pedido apos criado
public class Pedido {
    private BigDecimal valor;
    public void setValor(BigDecimal valor) { this.valor = valor; }
    public BigDecimal getValor() { return valor; }
}

// CORRETO - record: imutavel por natureza, sem setter, igualdade/hashCode/toString gerados
public record Pedido(String id, BigDecimal valor) {
    public void validar() {
        if (valor == null || valor.signum() <= 0) {
            throw new BusinessException("Valor do pedido deve ser maior que zero");
        }
    }
}
```

### 4. Streams

Pipelines curtos, sem efeitos colaterais; loop quando for mais claro.

```java
// ERRADO - forEach com efeito colateral (mutacao de lista externa): dificil de ler e testar
List<String> nomesAtivos = new ArrayList<>();
produtos.stream().forEach(p -> {
    if (p.ativo()) {
        nomesAtivos.add(p.nome().toUpperCase());
    }
});

// CORRETO - pipeline curto, sem efeito colateral, transformacao pura
List<String> nomesAtivos = produtos.stream()
        .filter(Produto::ativo)
        .map(p -> p.nome().toUpperCase())
        .toList();
```

Quando o pipeline exigiria múltiplos `flatMap`/estado acumulado só para simular um `for`, prefira o
loop explícito — clareza vale mais que "tudo em stream".

### 5. Nomenclatura

`PascalCase` para tipos, `camelCase` para métodos/campos, `UPPER_SNAKE_CASE` para constantes; nomes
que revelam intenção. Use português ou inglês, mas seja consistente com o restante do projeto — não
misture os dois no mesmo pacote/classe.

```java
// ERRADO - nomes nao revelam intencao, abreviacoes obscuras
public List<Produto> get(String s) { ... }
public boolean chk(String str) { ... }
private static final int N = 100;

// CORRETO - nomes revelam intencao; convencao de caixa respeitada
public List<Produto> buscarAtivosPorCategoria(String categoria) { ... }
public boolean precoEhValido(BigDecimal preco) { ... }
private static final int TAMANHO_MAXIMO_PAGINA = 100;
```

### 6. Complexidade

Métodos curtos, early return, sem aninhamento acima de 3 níveis.

```java
// ERRADO - aninhamento profundo (4 niveis), dificil de acompanhar o fluxo principal
public void processar(Pedido pedido) {
    if (pedido != null) {
        if (pedido.itens() != null) {
            if (!pedido.itens().isEmpty()) {
                if (pedido.valor().signum() > 0) {
                    executar(pedido);
                }
            }
        }
    }
}

// CORRETO - early return elimina o aninhamento, cada guarda e uma linha
public void processar(Pedido pedido) {
    if (pedido == null || pedido.itens() == null || pedido.itens().isEmpty()) {
        return;
    }
    if (pedido.valor().signum() <= 0) {
        return;
    }
    executar(pedido);
}
```

### 7. DRY com bom senso

Extraia duplicação real; não abstraia prematuramente — regra das 3 ocorrências (na 1ª e 2ª vez,
duplicar pode ser mais barato que a abstração errada; extraia quando a 3ª ocorrência confirmar o
padrão). Não crie uma `EmailValidator` com interface e implementação única "para o futuro" — isso é
abstração especulativa (veja `padroes-de-projeto-java`, seção "Quando NÃO aplicar pattern"); um
método privado já resolve a duplicação real.

```java
// ERRADO - mesma validacao de email duplicada em tres pontos (criar, atualizar, importar em lote)
public void criar(UsuarioRequest req) {
    if (req.email() == null || !req.email().contains("@")) {
        throw new BusinessException("Email invalido");
    }
}

public void atualizar(UsuarioRequest req) {
    if (req.email() == null || !req.email().contains("@")) {
        throw new BusinessException("Email invalido");
    }
}

public void importarEmLote(List<UsuarioRequest> reqs) {
    for (UsuarioRequest req : reqs) {
        if (req.email() == null || !req.email().contains("@")) {
            throw new BusinessException("Email invalido");
        }
    }
}

// CORRETO - 3a ocorrencia confirma o padrao: extrai um metodo unico, sem criar interface/factory
private void validarEmail(String email) {
    if (email == null || !email.contains("@")) {
        throw new BusinessException("Email invalido");
    }
}
```

### 8. Testes

Caso feliz + bordas + erro; nomes descritivos; sem dependência de ordem.

```java
// ERRADO - nome nao descreve cenario, sem cobertura de borda/erro, so caminho feliz
@Test
void test1() {
    Produto produto = service.criar(request);
    assertEquals(produto.nome(), "Mouse");
}

// CORRETO - nomes descritivos (deveXQuandoY), casos feliz + erro independentes
// (exemplo com AssertJ fluente; JUnit 5 puro tambem e valido, ver observacao abaixo)
@Test
void deveCriarProdutoQuandoDadosValidos() {
    Produto produto = service.criar(requestValido());

    assertThat(produto.nome()).isEqualTo("Mouse");
}

@Test
void deveLancarBusinessExceptionQuandoPrecoForZero() {
    assertThatThrownBy(() -> service.criar(requestComPrecoZero()))
            .isInstanceOf(BusinessException.class);
}
```

Use JUnit 5. AssertJ (`assertThat(...)`) é bem-vindo quando já está disponível no projeto — confira o
`pom.xml` antes de sugeri-lo em uma revisão — mas JUnit 5 puro (`assertEquals`, `assertThrows`,
`assertDoesNotThrow`) também é válido e é o padrão usado nos assets deste catálogo (`ProdutoTest`,
`PedidoTest`, `PublicarEventoServiceTest`, nenhum dos quais depende de AssertJ). Não marque
`assertEquals`/`assertThrows` como achado de revisão só por não ser AssertJ. Independente da
biblioteca de asserção, cada teste deve poder rodar sozinho (sem depender de estado deixado por um
teste anterior) e cobrir pelo menos um caso de borda (lista vazia, valor limite) além do feliz e do
erro.

### 9. Logs

Siga a skill `padrao-de-logs-java` para o formato e a estrutura completos. Nesta revisão, verifique:

- nenhum dado sensível (senha, token, CPF, cartão) aparece em log, nem mesmo em nível `debug`;
- a mensagem carrega contexto suficiente para investigar sem precisar reproduzir (IDs de negócio,
  não só "erro ao processar");
- o nível (`info`/`warn`/`error`) condiz com a severidade real do evento.

### 10. Arquitetura

Siga a skill `arquitetura-limpa-java` para o mapa completo de camadas e o checklist arquitetural.
Nesta revisão, verifique pelo menos:

- a camada onde a mudança caiu é a correta (regra de negócio em `domain`, orquestração em
  `application`, adaptador em `entrypoint`);
- nenhuma dependência aponta "para fora" (`domain` não importa `org.springframework.*`,
  `application` não importa `jakarta.servlet.*`).

## Quem revisa o quê

| Situação | Quem revisa | Papel |
|---|---|---|
| Diff pontual durante o desenvolvimento (uma classe, um método, um PR pequeno) | agent `java-revisor` | Revisão **tempestiva** — feedback rápido durante o trabalho, aplicando este checklist |
| Pré-merge, mudança grande, ou revisão do trabalho de outro agent (ex.: saída do `java-construtor`) | agent `java-especialista` | **Veredicto final** — achados Críticos bloqueiam o merge |

Esta skill é o checklist que ambos os agents aplicam — a diferença entre eles é o momento e o peso
do veredicto, não o critério de revisão.

## Formato do relatório

```markdown
## Revisão de Código: <componente/PR>

### Crítico
- **<título curto>** (`Arquivo.java:42`) — descrição do problema e por que bloqueia o merge.

### Importante
- **<título curto>** (`Arquivo.java:17`) — descrição e sugestão objetiva de correção.

### Menor
- **<título curto>** (`Arquivo.java:5`) — descrição (estilo/nomenclatura/preferência).

### Pontos positivos
- <prática correta observada, para reforçar>
```

Omita uma seção inteira se não houver achados nela (não escreva "nenhum encontrado") — exceto
"Pontos positivos", que deve sempre trazer ao menos um item quando algo no código merece ser
reforçado.
