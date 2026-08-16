## Context

O `contratoquery` serve duas rotas de leitura sobre a tabela `autorizacoes`, particionada em ~989
partições e compartilhada com o `contratocommand`. Ele é somente leitura (`DB_READ_ONLY=true` por
default) e não publica evento.

```
HOJE — as três violações                        DEPOIS

entrypoint/                                     infrastructure/web/
  AutorizacaoController                           AutorizacaoController
  contratosrest/*Dto ◀──────┐                     contratosrest/*Dto
        ▲                   │ (1) importa           ▲ monta o DTO a partir do modelo
        │                   │                       │
application/                │                   application/usecase/
  ConsultarAutorizacaoService                     ConsultarAutorizacaoService
    ├─ retorna o DTO ───────┘                       └─ retorna domain/model/Autorizacao
    ├─ cascata de 3 níveis de partição             (a cascata saiu daqui)
    └─ AutorizacaoRepository                             │ usa
         extends JpaRepository ── (2)                    ▼
                                                domain/port/out/AutorizacaoRepository
domain/                                           buscarPorId(UUID): Optional<Autorizacao>
  entities/Autorizacao  @Entity ── (3)                   ▲ implementa
  converters/*  jakarta.persistence                      │
                                                infrastructure/persistence/
                                                  AutorizacaoJpaAdapter
                                                    └─ cascata de 3 níveis AQUI
                                                  AutorizacaoJpaEntity  @Entity
                                                  AutorizacaoPersistenceMapper
                                                  SpringDataAutorizacaoRepository (package-private)
                                                  TipoProdutoConverter, TipoJornadaAutorizacaoConverter
                                                  ReversibleUUIDv7
                                                domain/model/
                                                  Autorizacao (Java puro)
```

A restrição dominante do desenho é o **particionamento**: `ReversibleUUIDv7` embute a partição de
criação dentro do próprio UUID da autorização, e o expurgo depois **move** linhas em estado terminal
para a faixa 900–999. Por isso a partição deixa de ser derivável do id, e existe a cascata de três
níveis — sem ela, `GET /{id}` devolveria 404 para toda autorização cancelada, rejeitada ou expirada.

## Goals / Non-Goals

**Goals**

- Inverter as três setas: domínio livre de JPA, `application` livre de DTO de web e de Spring Data.
- Provar o padrão de mapper modelo ⇄ entidade JPA num contexto sem escrita, antes do `contratocommand`.
- Tirar a estratégia de armazenamento da camada de aplicação.

**Non-Goals**

- Mudar qualquer coisa do contrato REST, inclusive a divergência intencional com o `contratocommand`.
- Mudar o algoritmo da cascata, a paginação ou a ordenação.
- Unificar `Autorizacao` entre query e command.
- Otimizar consulta. Se a migração revelar oportunidade, ela vira proposta separada.

## Decisions

### D1 — `domain/model/Autorizacao` é imutável e sem setters; `AutorizacaoJpaEntity` guarda as anotações

O modelo de domínio do query é **lido e nunca mutado** — a app não escreve. Isso permite a forma mais
forte: um record ou classe imutável, sem Lombok `@Data`, sem construtor vazio, sem setter.

A entidade JPA guarda tudo que o Hibernate exige: `@Entity`, `@Table`, `@EmbeddedId`, `@Column`,
`@Convert`, `@Version`, construtor sem argumentos. O mapper converte num sentido só
(`AutorizacaoJpaEntity → Autorizacao`), porque não há escrita.

**Consequência para o `contratocommand`:** lá o mapper precisará dos dois sentidos e o modelo não
poderá ser um record puro (há mutação de status). O padrão herdado é o do mapper e o da nomenclatura,
não o da imutabilidade total.

### D2 — `@Version` fica só na entidade JPA e não sobe para o modelo

O query não escreve, logo não precisa da versão para nada. Mapear a coluna na entidade JPA (para que
o Hibernate não reclame do schema) e **não** expor no modelo de domínio mantém o modelo limpo de um
conceito que é puramente de controle de concorrência.

**Consequência para o `contratocommand`:** lá a decisão terá de ser outra — a versão precisa trafegar
de ida e volta para o lock otimista funcionar através do mapper. É a diferença central entre as duas
apps e o motivo pelo qual esta vem primeiro.

### D3 — A cascata de partições muda de camada: sai de `application`, entra no adaptador

Hoje `ConsultarAutorizacaoService` carrega `PARTICAO_MIN = 0`, `PARTICAO_MAX = 889`,
`PRIMEIRA_PARTICAO_EXPURGO = 900`, a flag `busca-em-particoes-inesperadas` e a lógica dos três níveis.

Nada disso é regra de negócio. A cascata existe **só** porque a tabela é particionada e o expurgo
move linhas — se o armazenamento fosse uma tabela única, a cascata sumiria sem que nenhuma regra de
autorização mudasse. É a definição de detalhe de persistência.

Depois: a porta expõe `Optional<Autorizacao> buscarPorId(UUID idAutorizacao)`, e o caso de uso vira
quase trivial — pede, e traduz ausência em `ResourceNotFoundException`. A cascata inteira, com as
constantes e a flag, vive em `AutorizacaoJpaAdapter`.

**Alternativa descartada:** manter a cascata no caso de uso e dar à porta um método por nível
(`buscarNaParticao`, `buscarNaFaixaQuente`, `buscarNaFaixaExpurgo`). Rejeitada porque a porta passaria
a expor o esquema de particionamento na assinatura — o domínio ficaria sabendo que existem partições,
que elas vão de 0 a 999 e que 900+ é expurgo. Seria trocar um vazamento por outro, pior.

**Benefício além da conformidade:** o número de queries por requisição vira propriedade de uma classe
só, testável sem subir caso de uso.

### D4 — `ReversibleUUIDv7` vai para `infrastructure/persistence/`

Ele codifica a partição dentro do UUID. Extrair partição de um id é operação sobre o layout físico da
tabela. No query só a extração é usada (a geração é do command), e ela é usada exclusivamente pela
cascata — que por D3 já está no adaptador. Então `ReversibleUUIDv7` acompanha, e **o domínio do query
deixa de mencionar partição em qualquer lugar**.

Isso realiza, do lado da leitura, a decisão de 2026-08-15 de isolar o particionamento atrás de porta:
aqui a porta é a própria `AutorizacaoRepository`, cuja assinatura fala em `UUID` e nada mais. A porta
de **geração** de identidade, de que só o command precisa, é introduzida em
`hexagonal-classico-contratocommand-dominio-puro`.

**Nota:** `ReversibleUUIDv7Test` mede propriedade matemática (ida e volta do encode/decode) e continua
válido onde estiver — só muda de pacote.

### D5 — `SpringDataAutorizacaoRepository` é package-private

A interface `JpaRepository` passa a se chamar `SpringDataAutorizacaoRepository` e perde o modificador
`public`. Só `AutorizacaoJpaAdapter`, no mesmo pacote, a enxerga.

É o mecanismo que **impede a regressão**: sem `public`, nenhuma classe de `application` consegue
injetá-la nem por acidente nem por autocomplete. É a diferença entre uma convenção documentada e uma
que o compilador sustenta. A skill lista isso como gotcha explícito.

### D6 — O controller monta o DTO; o caso de uso devolve modelo

`ConsultarAutorizacaoService` deixa de importar `AutorizacaoDetalheResponseDto`. Os métodos `from(...)`
que hoje existem nos DTOs continuam existindo, mas passam a receber `domain/model/Autorizacao`, e
quem os chama é o controller.

É o que resolve a violação (1) e o que permite, no futuro, reconciliar a representação com o
`contratocommand` sem tocar em caso de uso nenhum — a tradução fica num lugar só.

### D7 — Paginação: `PaginacaoResponseDto` é de web; a porta devolve conteúdo e total

`ListarAutorizacoesUseCase` não deve devolver `Page` do Spring Data nem `PaginacaoResponseDto`. A
porta de saída devolve a lista de `domain/model/Autorizacao` mais a contagem total; o controller monta
o envelope de paginação.

Os parâmetros de entrada (`pagina`, `tamanho`, `ordenarPor`) chegam ao caso de uso como tipos simples,
nunca como `Pageable` — `Pageable` é Spring Data, e a conversão para ele acontece no adaptador.

## Risks / Trade-offs

- **Risco alto: divergência silenciosa entre `AutorizacaoJpaEntity` e o schema real.** Ao recriar as
  anotações numa classe nova, uma coluna com nome errado ou `@Convert` esquecido não quebra a
  compilação — quebra em runtime, ou pior, devolve dado errado. Mitigação: conferência coluna a
  coluna contra a entidade atual **e** contra as migrations, mais os testes de integração contra
  Postgres real.
- **Risco: regressão de desempenho invisível.** A cascata mudando de camada pode alterar quantas
  queries são disparadas por requisição sem que nenhum teste unitário perceba. Mitigação: contar as
  queries antes e depois nos três cenários da cascata (achou no nível 1, no 2, no 3) e comparar.
- **Risco: `PostgresLocalDisponivelCondition` mascarar falha.** Os testes de integração pulam quando
  não há Postgres local. A change `integridade-fluxo-escrita` já foi mordida por "Tests run: 0"
  indistinguível de sucesso (task 3.7). Mitigação: registrar executados **e** pulados na linha de
  base, e rodar a suíte obrigatoriamente com Postgres no ar antes de fechar.
- **Trade-off aceito: duas representações de `Autorizacao` no query (modelo + entidade JPA).** É o
  preço do N3. Some com o custo de manutenção quando uma coluna nova chega: passa a exigir edição em
  dois arquivos desta app em vez de um — e o `CLAUDE.md` do command já manda conferir o query nesse
  caso.
- **Trade-off aceito: o `ConsultarAutorizacaoService` fica quase vazio depois de D3.** Um caso de uso
  que só delega e traduz ausência em exceção parece cerimônia. É o resultado correto: a app é uma
  consulta, e o que ela tinha de complexo era complexidade de armazenamento morando no lugar errado.

## Migration Plan

Duas etapas, com build verde entre elas:

**Etapa A — estrutura e portas, entidade ainda anotada.** Move pacotes, cria `port/in` e `port/out`,
esconde o `JpaRepository` no adapter, tira o DTO do caso de uso, move a cascata. `Autorizacao`
continua sendo a entidade JPA, agora em `domain/model/`. Ao final, `mvn test` verde. Já elimina as
violações (1) e (2).

**Etapa B — domínio puro.** Parte `Autorizacao` em modelo + `AutorizacaoJpaEntity` + mapper, move os
converters. Elimina a violação (3).

Separar assim garante que, se a suíte quebrar na etapa B, a causa é o mapper — e não uma das dezenas
de mudanças de import da etapa A. As duas etapas cabem na mesma mudança porque a app é pequena; no
`contratocommand` elas viram mudanças distintas.

## Open Questions — respondidas na implementação

- **`domain/model/Autorizacao` deve ser `record` ou classe imutável?** Resposta: **classe imutável**
  via Lombok `@Value @Builder` (não record). Motivo: os `.from()` dos DTOs e o resto do monorepo usam
  acesso `getX()`; um record de 24 componentes trocaria essa convenção só neste modelo (accessor
  posicional `x()`), sem ganho real — o modelo só é montado num lugar (`AutorizacaoPersistenceMapper`,
  sentido único). `@Value` dá imutabilidade e ausência de setter sem essa ruptura de convenção. O
  `Cancelamento` embutido segue o mesmo padrão. **Nota para o `contratocommand`:** lá `@Value` não
  serve — o modelo precisa de mutação de status (`inicializaCriacao`), o que empurra para `@Data`
  mutável (que é, de fato, o que ele já usa).
- **Vale extrair `IdAutorizacao` como value object no modelo, ou o domínio do query fala só em `UUID`?**
  Resposta: **só `UUID`**. `domain/model/Autorizacao.idAutorizacao` é um `UUID` plano; a chave composta
  (`IdAutorizacaoJpaEmbeddable`, UUID + partição) existe exclusivamente em `infrastructure/persistence/`.
  Nenhuma classe de `domain/` ou `application/` menciona partição em qualquer forma.

## Nota de implementação — o padrão de mapper e o que o `contratocommand` deve fazer diferente

O padrão (`AutorizacaoJpaEntity` + embeddables em `infrastructure/persistence/`, mapper com método(s)
`paraDominio`/`paraEntidade`, `SpringDataAutorizacaoRepository` package-private) funcionou sem atrito
para leitura: `AutorizacaoPersistenceMapper` tem **um método só** (`paraDominio`), porque não há
escrita. O `contratocommand` precisa dos dois sentidos (`paraDominio`/`paraEntidade`/`aplicarEm`) e,
diferente daqui, **expõe `version` no modelo de domínio** — lá o lock otimista depende de `version`
trafegar de ida e volta pelo mapper; aqui `version` fica só na entidade JPA (D2), porque não há
escrita para proteger. A cascata de partições confinada no adaptador (D3) e `ReversibleUUIDv7` como
detalhe de `infrastructure/persistence/` (D4) são os dois pontos que o `contratocommand` herda sem
alteração — extração de partição é a mesma operação nas duas apps.
