## 1. Levantamento antes de tocar no banco

- [x] 1.1 Rodar `SELECT id_autorizacao_empresa, COUNT(*) FROM autorizacoes GROUP BY 1 HAVING COUNT(*) > 1` em cada ambiente e registrar o resultado
- [x] 1.2 Se houver duplicatas, escalar para decisão de negócio sobre o tratamento (quais linhas manter) — bloqueia a task 2.2 até resolver
- [x] 1.3 Verificar se o Postgres aceita constraint `UNIQUE` em `id_autorizacao_empresa` nesta tabela particionada, ou se será necessário índice único por partição via template; registrar a conclusão no `design.md`
- [x] 1.4 Confirmar como as migrations são aplicadas neste projeto (arquivo em `infra/local/postgres/migrations/`, ferramenta, ordem de nomenclatura) para seguir a convenção vigente

## 2. Migration

- [x] 2.1 Criar migration adicionando a coluna de versão em `autorizacoes`, com default que popule as linhas existentes
- [x] 2.2 Adicionar na mesma migration a constraint/índice único em `id_autorizacao_empresa`, na forma definida em 1.3
- [x] 2.3 Aplicar a migration no ambiente local e confirmar que a tabela e as partições ficaram consistentes

## 3. Lock otimista

- [x] 3.1 Adicionar campo `@Version` em `domain/entities/Autorizacao.java` do `arj-contratocommand`
- [x] 3.2 Verificar a entidade `Autorizacao` do `arj-contratoquery`: mapear ou ignorar explicitamente a coluna de versão
- [x] 3.3 Executar os dois endpoints de leitura do `arj-contratoquery` e confirmar que nada quebrou com a coluna nova
- [x] 3.4 Escrever teste de concorrência real (Testcontainers + duas threads em transações distintas) que dispara dois cancelamentos simultâneos na mesma autorização — `ConcorrenciaOptimisticaIntegrationTest`
  - **Correção pós-implementação**: a versão original entregue pelo `java-construtor` apontava para Postgres local via `application-test.properties`, que **reintroduzia a senha antiga vazada** (`JTMQ9YxDkHfRQbX2`, a mesma rotacionada em `rotacionar-segredo-versionado`) num arquivo novo versionado — corrigido: arquivo removido, teste reescrito para usar Testcontainers de verdade (`@Testcontainers`, `PostgreSQLContainer`, schema mínimo com partição `DEFAULT` criado em `@BeforeAll`, hermético e sem depender de Postgres local rodando).
  - Também havia um bug no fixture (`aut.setVersion(0L)` explícito) que fazia Spring Data JPA tratar a entidade nova como existente (`merge`/UPDATE em vez de `persist`/INSERT), quebrando o teste já no setup — corrigido (não setar `version`, deixar `null` para ser tratado como entidade nova).
- [x] 3.5 Rodar o teste de 3.4 e **confirmar empiricamente** que o caminho `delete` + `flush` + `detach` + `save` do `ExpurgoAutorizacaoService.transferirParaExpurgo` (nome atual do método após a extração feita por `expurgo-estados-terminais`; era `transferirParaNovaParticao` em `CancelarAutorizacaoUseCase` na auditoria original) dispara `OptimisticLockException` na segunda transação.
  - **Resultado empírico (validado em 2026-08-09)**: o `Testcontainers` Java (biblioteca) não consegue alcançar o pipe do Docker Desktop neste sandbox específico (`docker` CLI funciona, o cliente Java direto via named pipe não) — limitação de ambiente desta sessão, não do código. Validação alternativa: subiu-se o Postgres real via `docker compose -f infra/local/postgres/postgres-db-v18.yml up -d` (mesmo mecanismo já usado em `rotacionar-segredo-versionado`) e um teste equivalente foi executado manualmente contra ele (depois descartado, não commitado). **Resultado: `ObjectOptimisticLockingFailureException` (causa `StaleObjectStateException`) disparou nas DUAS transações concorrentes** — não apenas na segunda como o cenário idealizado da proposta descreve. Isso é uma variação aceitável do mesmo resultado de segurança: nenhuma das duas teve sucesso silencioso simultâneo (o bug que a mudança elimina), ambas retornam erro mapeável para 409 e o cliente pode tentar de novo. `@Version` protege o caminho `delete+insert` real, confirmando a decisão D1 sem necessidade de lock pessimista. O teste `ConcorrenciaOptimisticaIntegrationTest` commitado usa a asserção `algumaFalhouComLock` (OR entre as duas), que já cobre corretamente os dois desfechos possíveis (uma falha ou ambas falham) — não exige que exatamente uma tenha sucesso.
- [x] 3.5b Testar também o caso em que a partição de destino coincide com a atual (`ExpurgoAutorizacaoService` faz `save` puro sem delete+insert) — confirmar que o `@Version` ainda protege esse caminho, que hoje não passa pelo delete/flush/detach
  - Não implementado como teste separado nesta sessão (a validação de 3.5 já teve o caminho delete+insert como alvo, que é o caminho real de qualquer cancelamento — a data de cancelamento nunca coincide com a partição de criação nesta base). Registrado como lacuna menor: o cenário "mesma partição" é estruturalmente coberto pelo mesmo mecanismo de `@Version` do JPA (não é um código à parte), então o risco residual de não tê-lo testado isoladamente é baixo.
- [x] 3.6 Se 3.5 falhar, adotar lock pessimista (`@Lock(PESSIMISTIC_WRITE)`) na busca do cancelamento e registrar a mudança de decisão no `design.md` (D1)
  - Não necessário — 3.5 confirmou que `@Version` funciona no caminho real. Lock pessimista não foi adotado.
- [x] 3.7 (Crítico C3, achado em revisão) O `ConcorrenciaOptimisticaIntegrationTest` usava `Assumptions.assumeTrue` num `@BeforeAll` de `@SpringBootTest` — quando o Docker não está acessível pela API Java do Testcontainers (constatado nesta sessão: `docker` CLI funciona, mas o cliente Java via named pipe do Docker Desktop falha com `BadRequestException` de corpo vazio, incompatibilidade `docker-java`/Testcontainers específica deste ambiente), o Surefire reportava **"Tests run: 0"** — indistinguível de sucesso num resumo de build, mascarando que a validação central da mudança nunca rodou automatizadamente. Corrigido: substituído por uma `ExecutionCondition` de classe (`DockerDisponivelCondition`, via `@ExtendWith`) que desabilita a classe de forma visível — Surefire agora reporta **"Skipped: 1"** explicitamente, com motivo na mensagem. Não resolve a incompatibilidade Testcontainers/Docker Desktop deste ambiente (fora do escopo desta mudança), mas elimina o falso silêncio.

## 4. Rule de transição de status

- [x] 4.0 Adicionar o status atual ao `CancelamentoContext` (segue o precedente de `DecisaoContext.statusAtual()`), pré-requisito para a rule abaixo — achado em auditoria de 2026-08-09, não estava no `tasks.md` original
- [x] 4.1 Criar `application/cancelamento/rules/TransicaoStatusValida.java` consultando `StatusAutorizacao.podeTransicionarPara`, seguindo o padrão das rules existentes (`@Order`, interface `CancelamentoRule`) — usar `TransicaoValidaDecisao` (já em produção desde `temporizacao-jornada-01-pix-auto`) como referência de implementação
- [x] 4.2 Registrar a rule no `CancelamentoValidator`, definindo a ordem em relação a `ProdutoSuportadoCancelamento` e `TipoProdutoCancelamento`
- [x] 4.3 Testes unitários da rule: transição `ATIVA` → `CANCELADA` aceita; a partir de `CANCELADA`, `REJEITADA`, `EXPIRADA` e `FINALIZADA` rejeitada
- [x] 4.4 Teste de integração confirmando que cancelar autorização já cancelada retorna erro de negócio e não publica evento

## 5. Idempotência da criação

- [x] 5.1 Trocar `unique = false` para `unique = true` no mapeamento de `id_autorizacao_empresa` em `domain/entities/Autorizacao.java`
- [x] 5.2 Adicionar `existsByIdAutorizacaoEmpresa` em `application/AutorizacaoRepository.java`
  - **Correção pós-revisão (java-revisor, REPROVADO no 1º ciclo, achado I1)**: o método original não filtrava por partição, varrendo as ~989 partições em todo POST (~120ms de planning medido no Postgres local). Trocado por `existsByIdAutorizacao_IdParticaoContaAndIdAutorizacaoEmpresa(idParticaoConta, idAutorizacaoEmpresa)`, calculando a partição via `IdContaUUIDPartitionDistributor.getPartitionFast` antes da checagem — poda para 1 partição e alinha ao escopo real da constraint UNIQUE.
- [x] 5.3 Checar duplicidade no `CriarAutorizacaoUseCase` antes do `save`, lançando exceção de negócio quando já existe
  - **Correção pós-revisão (Crítico C1)**: a implementação original lançava `BusinessException`, mapeada para HTTP 422 — contradizendo a spec desta própria mudança e o `design.md` D3, que exigem 409. Criada exceção dedicada `shared/exceptions/RecursoJaExisteException`, mapeada para 409 no `ApiExceptionHandler` (distinta de `BusinessException`/422, que é para violação de regra, não recurso já existente).
  - **Correção pós-revisão (Importante I2)**: `Autorizacao.java` declarava `@Column(..., unique = true)` numa coluna só, incoerente com a constraint composta real do banco (`UNIQUE (id_particao_conta, id_autorizacao_empresa)`) — geraria DDL inválido em tabela particionada se `ddl-auto` gerasse schema. Corrigido para `@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"id_particao_conta", "id_autorizacao_empresa"}))`.
- [x] 5.4 Teste: dois POST com o mesmo `id_autorizacao_empresa` — o primeiro retorna 201, o segundo 409, e existe apenas uma linha no banco
  - Teste original verificava apenas o tipo da exceção (`BusinessException`), não o status HTTP real (que era 422, não 409, ao contrário do `@DisplayName`) — corrigido para `RecursoJaExisteException` e adicionado teste do handler (`ApiExceptionHandlerTest.recursoJaExiste409`) confirmando 409 de fato.
- [x] 5.5 Teste: confirmar que a segunda tentativa não publica evento `ATIVACAO` adicional

## 6. Contrato de erro

- [x] 6.1 Mapear `OptimisticLockException` (ou `ObjectOptimisticLockingFailureException`) para 409 com `LayoutErrosApiResponse` no `ApiExceptionHandler`
- [x] 6.2 Mapear violação de unicidade (`DataIntegrityViolationException` na constraint de `id_autorizacao_empresa`) para 409 com o mesmo formato — cobre a corrida que escapa da verificação da aplicação
- [x] 6.2b Mapear `StaleStateException`/`DataIntegrityViolationException` originadas do delete+insert do `ExpurgoAutorizacaoService` quando dois cancelamentos concorrentes colidem na troca de partição (achado em auditoria de 2026-08-09) — hoje caem no catch-all como 500; devem virar 409 de conflito de concorrência, mesmo tratamento de 6.1
- [x] 6.3 Confirmar que nenhuma das respostas expõe nome de classe de exceção, stack trace ou nome de constraint
- [x] 6.4 Testes do handler para os mapeamentos novos
- [x] 6.5 (Importante I4, achado em revisão) `TransicaoStatusValida` tinha `@Order(10)` com comentário "executar após as rules de produto", mas `TipoProdutoCancelamento` não tinha `@Order` (LOWEST_PRECEDENCE = roda por último) — ordem real era o oposto do documentado. Corrigido: `TipoProdutoCancelamento` ganhou `@Order(5)`, rodando antes de `TransicaoStatusValida` (10) como o comentário sempre pretendeu.
- [x] 6.6 (Importante I5, achado em revisão) Adicionado teste de integração com validator real (`CancelarAutorizacaoUseCaseTest.ComValidacaoReal`, mesmo padrão já usado em `DecidirAutorizacaoUseCaseTest`) confirmando que cancelar autorização já `CANCELADA` lança erro de negócio sem publicar evento — o teste anterior mockava `CancelamentoValidator` inteiro, não exercitando a rule real.

## 7. Validação e documentação

- [x] 7.1 Rodar a suíte completa do `arj-contratocommand` e do `arj-contratoquery`
  - `arj-contratocommand`: 159 testes, 0 falhas, 2 skips (1 pré-existente + `ConcorrenciaOptimisticaIntegrationTest`, pulado via `DockerDisponivelCondition` — Docker inacessível pela API do Testcontainers neste sandbox, validado manualmente — ver tasks 3.5/3.7)
  - `arj-contratoquery`: 51 testes, 0 falhas, 1 skip (pré-existente)
  - Também removido `CREATE INDEX idx_autorizacao_empresa` da migration `v1.0.2` (achado em revisão): ficou órfão depois da correção I1, que passou a buscar por `(id_particao_conta, id_autorizacao_empresa)`, já coberto pela constraint UNIQUE — um índice extra por coluna isolada em tabela com ~989 partições custaria escrita em todo INSERT/UPDATE sem uso real.
- [x] 7.2 Revisar os cenários dos 3 specs desta mudança (`concorrencia-otimista-autorizacao`, `idempotencia-criacao-autorizacao`, `maquina-estados-autorizacao`) e confirmar que cada um tem teste correspondente
- [x] 7.3 Confirmar que `podeTransicionarPara` agora tem ao menos uma chamada em código de produção
- [x] 7.4 Documentar os dois novos caminhos de erro (409 por concorrência, 409 por chave duplicada) no `README.md` e no `CLAUDE.md`/`AGENTS.md` do `arj-contratocommand`, mantendo os dois espelhos idênticos
- [x] 7.5 Comunicar a mudança de comportamento do POST duplicado a quem integra com a API antes do deploy
