## Context

O `contratocommand` expõe três rotas de escrita sobre autorizações de pagamento recorrente. Cada uma
segue o mesmo esqueleto: controller resolve header → monta um record de contexto → chama o use case
`@Transactional` → validator roda rules → persiste → publica evento de domínio → listener manda para
o SNS depois do commit.

```
HOJE                                                    DEPOIS (esta mudança)

entrypoint/                                             infrastructure/web/
  AutorizacaoController                                   AutorizacaoController
  contratosrest/CriarAutorizacaoRequest                   contratosrest/CriarAutorizacaoRequest
  contratosrest/AutorizacaoCompletaResponseDto ◀──┐       contratosrest/AutorizacaoCompletaResponseDto
                                                  │         ▲ montado AQUI a partir do modelo
application/                                      │       
  contratacao/CriarAutorizacaoUseCase ────────────┘       domain/port/in/
    @Service, retorna o DTO da web                          CriarAutorizacaoUseCase (interface)
    ├─ ContratacaoValidator ──▶ rules/                       CriarAutorizacaoCommand (record)
    ├─ AutorizacaoMapper (MapStruct)                       application/usecase/
    ├─ AutorizacaoRepository extends JpaRepository           CriarAutorizacaoService → devolve modelo
    └─ ApplicationEventPublisher                           domain/service/
  eventos/AutorizacaoEventoPublisher                         ContratacaoValidator + rules/
    SnsClient (AWS SDK) DENTRO de application                Rule, Validator (framework)
  ExpurgoAutorizacaoService                                domain/port/out/
    calcula partição, chama query nativa                     AutorizacaoRepository (interface própria)
                                                           domain/event/AutorizacaoPersistidaEvent
domain/                                                    infrastructure/persistence/
  entities/Autorizacao @Entity  ← fica p/ a próxima          SpringDataAutorizacaoRepository (pkg-private)
  utilities/*  ← fica p/ a próxima                           AutorizacaoJpaAdapter
                                                           infrastructure/messaging/
shared/                                                      AutorizacaoEventoPublisher (SNS)
  validationsetup/Rule, Validator                            AutorizacaoEventoPayload
  interceptors/api/ApiExceptionHandler                     infrastructure/web/ApiExceptionHandler
  config/SnsClientConfig, AwsProperties                    infrastructure/config/
  exceptions/*                                             domain/exception/
```

Três restrições moldam o desenho e nenhuma pode ser afrouxada:

1. **A publicação de evento acontece só depois do commit.** `@TransactionalEventListener(AFTER_COMMIT)`
   é o que garante que um rollback de validação nunca produz evento no SNS. Está documentado como
   armadilha nº 8 no `CLAUDE.md` da app.
2. **A ordem das rules é significativa.** `@Order` define, por exemplo, que
   `ProdutoSuportadoCancelamento` roda antes de `TipoProdutoCancelamento` para que divergência de
   produto falhe com mensagem específica. A change `integridade-fluxo-escrita` já teve de corrigir uma
   ordem que o comentário prometia e a anotação não entregava (task 6.5).
3. **A idempotência de `/decisao` exige `statusAtual == RECEBIDA` explicitamente**, não só
   alcançabilidade no grafo. Armadilha nº 9 do `CLAUDE.md`.

## Goals / Non-Goals

**Goals**

- Eliminar cinco dos sete anti-padrões da skill, deixando os dois de persistência para a mudança
  seguinte.
- Deixar a regra de dependência verificável por `import`, sem exceção nesta app.
- Terminar com build verde e comportamento idêntico, para que a mudança seguinte comece de base sólida.

**Non-Goals**

- Separar modelo de domínio de entidade JPA. É a mudança seguinte, por construção.
- Mexer em `@Version`, no comportamento de concorrência ou na movimentação física de partição.
- Alterar contrato REST, códigos de status, formato de erro ou message attributes do SNS.
- Reconciliar a representação com o `contratoquery`.

## Decisions

### D1 — Os records de contexto viram comandos em `domain/port/in/`

`ContratacaoContext`, `CancelamentoContext` e `DecisaoContext` já são records imutáveis que carregam
header + corpo + estado lido do banco. É exatamente o papel de **command** na tabela da skill
("Interface de use case + command → `domain/port/in/`").

Ficam em `domain/port/in/` junto das interfaces que os consomem. O nome muda de `*Context` para
`*Command`, alinhando ao vocabulário da skill e ao dos exemplos (`CriarPedidoCommand`).

**Consequência importante:** hoje `ContratacaoContext` carrega `dados` — o próprio
`CriarAutorizacaoRequest`, que é DTO de web com anotações `@Valid`. Isso não pode subir para
`domain/port/in/`, ou o domínio passaria a depender de `jakarta.validation` e do contrato HTTP. O
controller passa a traduzir o request nos campos do comando.

É a única mudança desta proposta que não é movimento de arquivo, e a de maior superfície: são 15
campos no comando de criação.

### D2 — Rules e validators vão para `domain/service/` e mantêm `@Component`

Decisão tomada na exploração de 2026-08-15. As dez rules concretas expressam regra de negócio —
"produto suportado", "data de fim de vigência inválida", "valor acima do limite", "transição de
status permitida". Pertencem ao domínio.

O custo: elas são `@Component`, injetadas coletivamente como `List<ContratacaoRule>` e ordenadas por
`@Order`. Isso põe `org.springframework.*` dentro de `domain/`, contrariando o anti-padrão #6 da
própria skill e o requisito geral "domínio não conhece framework" que esta capacidade estabeleceu na
primeira mudança.

**A exceção é consciente, estreita e escrita:** vale para `domain/service/`, vale só para anotações
de **injeção e ordenação** (`@Component`, `@Order`), e não vale para `domain/model/`,
`domain/port/`, `domain/enums/` nem `domain/exception/`, que permanecem 100% livres de framework.

**Alternativa descartada (D2-b): rules puras + `@Configuration` registrando a `List<Rule>`
explicitamente.** Mais limpa e sem exceção nenhuma, mas exige um bean de registro por feature, e cada
rule nova passa a precisar de duas edições (a classe e o registro) em vez de uma — trocando um
acoplamento de anotação por um ponto de esquecimento silencioso: rule criada e não registrada não
falha, só deixa de validar. Num fluxo que autoriza débito recorrente, uma validação que some sem
sinal é pior do que uma anotação no domínio.

**Alternativa descartada (D2-c): rules em `application/usecase/<feature>/rules/`.** Mantém o domínio
limpo, mas deixa regra de negócio fora da camada de negócio — que é o problema que a migração inteira
existe para resolver.

### D3 — `ApplicationEventPublisher` fica no caso de uso; o adaptador SNS vai para `infrastructure/messaging/`

O mecanismo atual já é bom: o caso de uso publica `AutorizacaoPersistidaEvent` no barramento
in-process do Spring, e `AutorizacaoEventoPublisher` — um `@TransactionalEventListener(AFTER_COMMIT)`
— traduz para SNS. O caso de uso **já não sabe que SNS existe**.

O que muda: `AutorizacaoPersistidaEvent` vira evento de domínio em `domain/event/`;
`AutorizacaoEventoPublisher` e `AutorizacaoEventoPayload` vão para `infrastructure/messaging/`.

`ApplicationEventPublisher` permanece injetado no caso de uso. É tipo do Spring, mas é o barramento
de eventos **do próprio processo**, acoplado ao ciclo de vida da transação — não é sistema externo. A
skill permite Spring em `application` (`@Service`, `@Transactional`); esta é a mesma categoria.

**Alternativa descartada:** porta de saída `PublicadorEventoAutorizacao` chamada direto pelo caso de
uso. Rejeitada porque destruiria a garantia AFTER_COMMIT: a chamada aconteceria **dentro** da
transação, e um rollback posterior publicaria um evento sobre um estado que não existe. Trocar uma
garantia de correção por conformidade de layout seria o pior negócio possível desta migração.

### D4 — A porta expressa expurgo como intenção, não como número de partição

`ExpurgoAutorizacaoService` hoje calcula a partição de destino
(`ControleExpurgoAutorizacao.obterParticaoExpurgoWrite`) e chama a query nativa `moverParaParticao`.
Pelo requisito "estratégia de armazenamento não vaza para a aplicação", estabelecido pelo
`contratoquery`, isso não pode ficar em `application`.

A porta passa a expor `transferirParaExpurgo(...)`. O adaptador calcula a partição e executa o
`UPDATE` nativo do `id_particao_conta` (row movement do PostgreSQL ≥ 11), preservando o texto da query
literalmente.

O contrato de retorno é preservado: a query devolve a quantidade de linhas afetadas e o chamador
trata valor diferente de 1 — essa checagem migra junto para o adaptador, e o `ConcurrencyFailureException`
resultante continua chegando ao `ApiExceptionHandler` como 409.

`ControleExpurgoAutorizacao` fica em `domain/utilities/` **nesta** mudança e migra para
`infrastructure/persistence/` na seguinte, junto com as demais utilities de partição.

### D5 — `AutorizacaoMapper` (MapStruct) muda de origem: do request HTTP para o comando

Hoje ele mapeia `CriarAutorizacaoRequest` → `Autorizacao`, com `@AfterMapping` chamando
`inicializaCriacao()`. Depois de D1, ele mapeia `CriarAutorizacaoCommand` → `Autorizacao` e vive em
`application/usecase/`.

MapStruct em `application` é aceitável: gera código, não é dependência de transporte. O
`@AfterMapping` que chama `inicializaCriacao()` **permanece como está nesta mudança** — desmontá-lo
depende da porta de geração de identidade, que é da mudança seguinte.

### D6 — `SpringDataAutorizacaoRepository` é package-private

Mesmo mecanismo e mesmo nome adotados no `contratoquery` (D5 de lá). As três queries JPQL, o
`existsBy...` e o `@Modifying` nativo migram literalmente, sem reescrita.

## Risks / Trade-offs

- **Risco alto: a ordem das rules mudar sem ninguém perceber.** Ao mover dez `@Component` de pacote,
  um `@Order` esquecido ou um `@Order` implícito (`LOWEST_PRECEDENCE`) muda qual mensagem de erro o
  cliente recebe. Já aconteceu nesta app (`integridade-fluxo-escrita`, task 6.5). Mitigação: registrar
  a ordem efetiva de cada validador **antes** (por log da lista injetada, não por leitura das
  anotações) e comparar depois.
- **Risco alto: quebrar a garantia AFTER_COMMIT.** Endereçado por D3, mas mover o listener de pacote
  exige confirmar que ele continua sendo registrado como `@TransactionalEventListener` e não como
  `@EventListener` comum. Mitigação: teste que provoca rollback e afirma que nenhum evento sai.
- **Risco: D1 perder campo na tradução request → comando.** São 15 campos na criação. Um campo
  esquecido não quebra a compilação se o comando tiver default. Mitigação: comparar as respostas
  capturadas antes e depois, e conferir campo a campo contra o request.
- **Risco: `@Valid` deixar de rodar.** As anotações de Bean Validation ficam no DTO de web e o
  `MethodArgumentNotValidException` → 422 depende de o `@Valid` continuar no parâmetro do controller.
  Mitigação: teste de payload inválido conferindo 422 e o shape `LayoutErrosApiValidationsResponse`.
- **Trade-off aceito: `@Component` e `@Order` dentro de `domain/service/` (D2).** Exceção escrita,
  com escopo delimitado e alternativas descartadas registradas.
- **Trade-off aceito: `ApplicationEventPublisher` do Spring dentro de `application` (D3).**
  Justificado pela garantia transacional.

## Migration Plan

Etapa única, mas com ordem interna que mantém o compilador como rede:

1. Exceções e enums para `domain/` (movimento puro).
2. Portas de saída e adaptador de persistência — `SpringDataAutorizacaoRepository` package-private.
3. Rules, validators e framework de validação para `domain/service/`.
4. Portas de entrada e comandos (D1) — a etapa de maior superfície.
5. Casos de uso para `application/usecase/`, devolvendo modelo.
6. Web e messaging para `infrastructure/`.
7. Testes.

A mudança seguinte (`hexagonal-classico-contratocommand-dominio-puro`) só começa com esta entregue e
verde. Se a suíte quebrar lá, a causa é o mapper, não uma das centenas de mudanças de import daqui.

## Open Questions

- **O nome dos comandos: `*Command` ou manter `*Context`?** D1 propõe `*Command` por alinhamento com
  a skill. Renomear toca também os testes e a documentação, que citam `ContratacaoContext` por nome em
  várias seções. Se o churn de renome pesar mais que o alinhamento de vocabulário, manter `*Context`
  em `domain/port/in/` é aceitável — decidir na implementação e registrar aqui.

  **Decidido (implementação):** `*Command`, nomeado pelo caso de uso (`CriarAutorizacaoCommand`,
  `CancelarAutorizacaoCommand`, `DecidirAutorizacaoCommand`), não pelo antigo nome da feature. Alinha
  com o vocabulário da skill e com os nomes dos três use cases/serviços. O padrão "header + corpo +
  estado lido do banco, com wither `comAutorizacaoCarregada`" foi preservado sem alteração de forma —
  só mudou pacote e nome.

## Notas de implementação (registradas ao final da etapa)

- **`metadados` no comando de criação é `String` (JSON pré-serializado), não `JsonNode`.** O requisito
  "comando não importa biblioteca de serialização" (spec `layout-hexagonal-classico`) proíbe Jackson no
  próprio record de comando. O controller já serializa (`request.metadados().toString()`) antes de
  montar o comando; `MetadadoRule`, em `domain/service/contratacao/rules/`, reparseia a string com um
  `ObjectMapper` local para inspecionar `nomePessoaRecebedora`/`apelidoPessoaRecebedora` — mesmo padrão
  de uso de Jackson que já existia em `domain/service/` antes desta etapa (a própria `MetadadoRule` já
  importava `JsonNode` desde que rules foram movidas para lá). Não é uma exceção nova, só reafirma a
  existente.
- **Divergência de D2/requisito "só `@Component`/`@Order`" em `domain/service/`:** os três validadores
  (`ContratacaoValidator`, `CancelamentoValidator`, `DecisaoValidator`) usam `@Service`, não `@Component`
  — comportamento herdado sem alteração do código pré-migração (a proposta não pediu a troca, só o
  movimento de pacote). `@Service` é uma especialização de `@Component` (mesma semântica de descoberta
  e injeção para o container), então não há diferença de comportamento — mas é, à letra, uma anotação
  fora das duas explicitamente listadas no requisito. Registrado aqui como dívida aceita e conhecida;
  se a próxima auditoria de arquitetura tratar isso como bloqueante, a correção é mecânica (trocar a
  anotação nos três arquivos).
- Ordem efetiva das rules conferida por execução real da suíte (`ContratacaoValidatorTest`,
  `CancelamentoValidatorTest` e os testes de `ComValidacaoReal` nos serviços) antes e depois da
  migração de pacote — nenhuma mudança de `@Order` foi feita, só movimento de arquivo, então a ordem
  efetiva é garantidamente a mesma.
- Verificação ponta a ponta local (`mvn spring-boot:run` + `curl`): criação de `PIX_AUTO` (`RECEBIDA`),
  aprovação via `/decisao` (`ATIVA`), criação de `DDA_AUTO` (`ATIVA` direto) e cancelamento — todos
  responderam com o mesmo shape e status de antes da migração.
