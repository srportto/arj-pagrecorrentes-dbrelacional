## Why

Quinta das seis mudanças que migram as aplicações de `apps/` para a arquitetura hexagonal clássica —
e a primeira das **duas** dedicadas ao `contratocommand`.

O `contratocommand` é a maior e mais perigosa app da frota: 59 classes em `main` (1942 linhas), 37
arquivos de teste, três fluxos de escrita transacional (criação, cancelamento, decisão), lock
otimista via `@Version`, publicação no SNS após commit, movimentação de linhas entre ~989 partições e
uma API com contrato público.

Fazer nele o que o `contratoquery` fez numa mudança só produziria um diff de ~96 arquivos misturando
duas naturezas muito diferentes de trabalho: mover pacotes (mecânico, verificável por compilador) e
inverter o modelo de persistência (arriscado, verificável só por teste de concorrência). Um diff
assim não é revisável, e quando a suíte quebrar não haverá como atribuir a causa.

Por isso o `contratocommand` é dividido:

```
  ESTA MUDANÇA                              A SEGUINTE
  hexagonal-classico-                       hexagonal-classico-
  contratocommand-portas                    contratocommand-dominio-puro

  • pacotes em 3 camadas                    • Autorizacao pura + AutorizacaoJpaEntity
  • port/in + port/out                      • mapper bidirecional com @Version
  • JpaRepository escondido no adapter      • porta de geração de identidade
  • use case para de retornar DTO           • utilities de partição p/ infrastructure
  • rules para domain/service               • converters JPA p/ infrastructure
  • SNS para infrastructure/messaging
                                            
  Autorizacao continua @Entity              Autorizacao vira Java puro
  em domain/model/                          
        │                                          │
        └────── build verde entre as duas ─────────┘
```

Ao final desta mudança, cinco dos sete anti-padrões da skill `arquitetura-limpa-java` já estão
resolvidos e a regra de dependência já é verificável por `import`. Os dois que sobram — entidade JPA
como modelo de domínio e domínio anotado — são o objeto da mudança seguinte.

O `contratoquery` já terá exercitado o padrão de mapper e respondido às questões abertas (record ×
classe, `IdAutorizacao` × `UUID`). Esta mudança herda essas respostas.

## What Changes

- Reorganizar as 59 classes de `main` para `domain` / `application` / `infrastructure`.

- **Introduzir portas de entrada e de saída:**
  - `domain/port/in/`: `CriarAutorizacaoUseCase`, `CancelarAutorizacaoUseCase`,
    `DecidirAutorizacaoUseCase` (interfaces) mais os records de comando hoje chamados
    `ContratacaoContext`, `CancelamentoContext`, `DecisaoContext`;
  - `application/usecase/`: `CriarAutorizacaoService`, `CancelarAutorizacaoService`,
    `DecidirAutorizacaoService`;
  - `domain/port/out/AutorizacaoRepository`: interface própria, sem `JpaRepository`. O
    `JpaRepository` vira `SpringDataAutorizacaoRepository`, **package-private** em
    `infrastructure/persistence/`.

- **Corrigir a seta invertida.** Hoje `CriarAutorizacaoUseCase.execute()` retorna
  `AutorizacaoCompletaResponseDto` — um DTO de `entrypoint`. Os três casos de uso passam a retornar
  `domain/model/Autorizacao`; quem monta o DTO é o controller.

- **Mover as regras de negócio para `domain/service/`** (decisão de 2026-08-15): o framework de
  validação (`Rule`, `Validator`), os três marcadores (`ContratacaoRule`, `CancelamentoRule`,
  `DecisaoRule`), os três validadores e as dez rules concretas. As rules mantêm `@Component` — exceção
  consciente e registrada, já que a injeção de `List<Rule>` depende do container.

- **Tirar o SNS da camada de aplicação.** `AutorizacaoEventoPublisher` (que usa `SnsClient` do AWS SDK)
  e `AutorizacaoEventoPayload` vão para `infrastructure/messaging/`. `AutorizacaoPersistidaEvent` vira
  evento de domínio em `domain/event/`. O mecanismo `@TransactionalEventListener(AFTER_COMMIT)` é
  preservado integralmente — é ele que garante que nenhum evento sai em rollback.

- **Expressar o expurgo como intenção na porta.** `ExpurgoAutorizacaoService` hoje calcula número de
  partição e chama a query nativa `moverParaParticao`. A porta passa a expor
  `transferirParaExpurgo(...)`, e o cálculo da partição de destino vira responsabilidade do adaptador
  — mesmo requisito que o `contratoquery` estabeleceu.

- Mover `shared/exceptions/*` para `domain/exception/`, `shared/interceptors/api/*` para
  `infrastructure/web/`, `shared/config/*` para `infrastructure/config/`, `entrypoint/*` para
  `infrastructure/web/`.
- Mover os 37 arquivos de teste para a árvore espelhada.
- Acrescentar à capacidade `layout-hexagonal-classico` os requisitos sobre regra de negócio no
  domínio e sobre evento de domínio, mais o requisito específico desta etapa.
- Atualizar `apps/contratocommand/CLAUDE.md` e `AGENTS.md` (espelhos idênticos).

- **Nenhuma mudança de contrato nem de comportamento.** As três rotas, os códigos de status (incluindo
  a convenção 422 para entrada inválida e 409 para conflito), o formato dos dois layouts de erro, a
  ordem das rules, o `tipoEvento` derivado do status, os message attributes do SNS e a garantia de
  publicação só após commit permanecem idênticos.

- **Fora de escopo, por construção:** separar `Autorizacao` de `AutorizacaoJpaEntity`. Ela continua
  `@Entity` em `domain/model/` ao final desta mudança — é o objeto declarado da mudança seguinte.
- **Fora de escopo, por construção:** `domain/utilities/{ControleExpurgoAutorizacao,
  IdContaUUIDPartitionDistributor, ReversibleUUIDv7}` e `domain/converters/*` permanecem onde estão.
  Movê-los depende da porta de geração de identidade, introduzida na mudança seguinte.
- **Fora de escopo:** a divergência de representação entre command e query. Continua como dívida
  aceita, sujeita aos gatilhos da D1 de `reconciliar-contrato-spec-doc`.

## Capabilities

### Modified Capabilities

- `layout-hexagonal-classico`: acrescenta (a) o requisito de que regra de negócio resida em
  `domain/service/`, com a exceção explícita da anotação de injeção; (b) o requisito de que evento de
  domínio seja declarado em `domain/event/` e que o adaptador que o traduz para mensagem externa viva
  em `infrastructure/messaging/`; e (c) o requisito específico desta etapa do `contratocommand`.

## Impact

- **Código afetado (59 arquivos em `main`):** todos mudam de pacote. Classes novas: 3 interfaces de
  porta de entrada, 1 de porta de saída, `AutorizacaoJpaAdapter`. `AutorizacaoRepository` deixa de
  estender `JpaRepository`. `AutorizacaoMapper` (MapStruct) muda de origem: deixa de mapear o request
  HTTP e passa a mapear o comando.
- **Testes (37 arquivos):** movidos. Os testes de rules e validators mudam de pacote sem mudar de
  conteúdo. `AutorizacaoControllerTest` muda: o controller passa a montar o DTO.
  `ConcorrenciaOptimisticaIntegrationTest` continua exercitando `@Version`, que nesta etapa ainda está
  na mesma classe de sempre.
- **Banco:** nenhuma migration. Nenhuma query muda de texto — as JPQL e a nativa `moverParaParticao`
  são preservadas literalmente, só mudam de arquivo.
- **Concorrência:** `@Version` permanece na mesma classe e o comportamento de
  `ObjectOptimisticLockingFailureException` → 409 e de `ConcurrencyFailureException` → 409 (o
  `tuple to be locked was already moved to another partition`, SQLSTATE 40001) é preservado.
- **`temporiza-autorizacao`:** chama `PATCH /decisao` desta app. Nenhuma alteração é necessária lá — a
  rota, o corpo e a semântica de idempotência não mudam.
- **`contratoquery`:** compartilha a tabela. Nenhuma alteração de schema, logo nenhum impacto.
- **Documentação:** `apps/contratocommand/CLAUDE.md` + `AGENTS.md`, que hoje descrevem a arquitetura
  em quatro camadas e listam caminhos de arquivo em quase toda seção.
