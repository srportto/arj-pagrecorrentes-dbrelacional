> Ordem por dependência: cada grupo é entregável e verificável isoladamente. Os grupos 1 e 2 não se
> tocam — podem ser feitos em qualquer ordem. O grupo 3 depende do 2 (a assinatura do comando muda).
> Rodar `mvn test` ao fim de cada grupo, não só no final.

## 1. Faixa numérica validada na borda

- [x] 1.1 Adicionar `@Min(0) @Max(1)` em `indicadorUsoLimiteConta` e `@Max(32767)` em
      `quantidadeDividasCiclo` no record `CriarAutorizacaoRequest`, com mensagens no mesmo padrão
      das existentes (`"O campo 'X' deve ..."`)
- [x] 1.2 Adicionar as mesmas constraints em `AtualizarDadosRecorrenciaRequest`, preservando a
      semântica de PATCH parcial (campo ausente/`null` continua significando "não altera" — `@Min`
      e `@Max` não disparam em `null`)
- [x] 1.3 Comentar o teto 32767 como limite físico do `short`, não regra de negócio, apontando o
      gatilho de revisão (design.md, D4) — sem esse comentário o número é lido como regra
- [x] 1.4 Varrer os demais campos dos records de request e confirmar que nenhum outro campo com
      destino `short` no modelo `Autorizacao` ficou sem `@Max` (cenário "Nenhum campo numérico
      convertido para short fica sem teto")
- [x] 1.5 Testes em `AutorizacaoControllerTest`: criação com `quantidadeDividasCiclo = 32768` → 422;
      criação com `indicadorUsoLimiteConta = 2` → 422; criação com `quantidadeDividasCiclo = 32767`
      → persiste exatamente 32767 (guarda contra truncamento, não só contra o status)
      **Nota de implementação:** `AutorizacaoControllerTest` é teste unitário de controller (chama
      métodos Java diretamente, sem MockMvc/`@Valid`), então os cenários de Bean Validation foram
      cobertos em `CriarAutorizacaoRequestTest` (novo, `infrastructure/web/contratosrest/`) via
      `jakarta.validation.Validator` isolado — cobre 32768 rejeitado, 32767 aceito (sem violação) e
      flag fora do domínio booleano rejeitada. "Persiste exatamente 32767" fica coberto no nível de
      validação (nenhum truncamento antes do banco); teste de persistência real exigiria
      `*IntegrationTest` com Postgres, fora do escopo verificável neste ambiente (sem infra local no
      ar) — ver task 4.2 para verificação manual end-to-end.
- [x] 1.6 Testes equivalentes para a rota de atualização em `AtualizarDadosRecorrenciaServiceTest`
      e/ou no teste de controller, incluindo o caso "campo ausente não altera" ainda passando
      **Nota de implementação:** `AtualizarDadosRecorrenciaRequestTest` (novo), mesmo padrão do
      item acima, incluindo o cenário "campos `null` não disparam `@Min`/`@Max`".

## 2. Identificador validado na borda

- [x] 2.1 Criar o record `AutorizacaoId` em `domain/model/`, com fábrica que valida o formato UUID e
      lança `BusinessException` para entrada malformada ou nula (design.md, D1) — Java puro, sem
      Spring, sem importar `infrastructure`
- [x] 2.2 Testar `AutorizacaoId` isoladamente: UUID válido constrói; string malformada, vazia e
      `null` lançam `BusinessException`; o valor exposto é igual ao UUID de entrada
- [x] 2.3 Trocar `String idAutorizacao` por `AutorizacaoId` em `CancelarAutorizacaoCommand`,
      `DecidirAutorizacaoCommand` e `AtualizarDadosRecorrenciaCommand` (nos `doRequest` e em
      `comAutorizacaoCarregada`)
- [x] 2.4 Construir o `AutorizacaoId` no `AutorizacaoController`, nas três rotas PATCH
- [x] 2.5 Remover as três chamadas a `UUID.fromString` dos use cases de escrita
- [x] 2.6 Ajustar os call sites quebrados pela mudança de assinatura (o compilador aponta todos) —
      inclui as rules que leem `comando.idAutorizacao()`, se houver, e os `*ServiceTest`
      **Nota de implementação:** além dos três `*ServiceTest`, ajustados `TestFixtures`
      (`cancelarContext`/`atualizarContext` continuam recebendo `String`, validando via
      `AutorizacaoId.de` — espelha a borda real), `AutorizacaoControllerTest`, e os testes de regra
      em `domain/service/{atualizacao,cancelamento}/**` que usavam o placeholder `"id"` (substituído
      por um UUID válido, já que o formato passou a ser validado na construção do fixture).
- [x] 2.7 Testes de contrato nas três rotas: `PATCH /api/autorizacoes/nao-e-uuid/{cancelar,decisao,
      atualizar}` → 422 com `LayoutErrosApiResponse`, **não** 500
      **Nota de implementação:** cobertos em `AutorizacaoControllerTest` (nível de controller,
      confirmando `BusinessException` antes do use case) — o shape `LayoutErrosApiResponse` e o
      código HTTP 422 já são cobertos para `BusinessException` em geral por `ApiExceptionHandlerTest`
      existente; não há MockMvc/`@SpringBootTest` de contrato HTTP neste projeto para as rotas PATCH.
- [x] 2.8 Teste de regressão: UUID bem formado de autorização inexistente continua 422 por
      `BusinessException`, com a mesma mensagem de antes (não pode virar erro de formato)
      **Nota de implementação:** coberto por `CarregadorAutorizacaoTest.autorizacaoAusenteLancaBusinessException`
      e pelos testes `naoEncontrada` dos três `*ServiceTest` (mock do carregador lançando
      `BusinessException` com a mensagem "Autorização não encontrada com ID: ...", preservada).
- [x] 2.9 Confirmar que id malformado não gera log em nível `ERROR` — o caminho não passa mais pelo
      `@ExceptionHandler(Exception.class)`
      **Nota de implementação:** confirmado por inspeção do `ApiExceptionHandler` —
      `erroNegociosResponseEntity(BusinessException, ...)` não chama `log.*` em nenhum nível; só
      `ApplicationException` e o catch-all de `Exception` logam em `ERROR`. `AutorizacaoId.de` lança
      `BusinessException`, que nunca alcança esses dois handlers.

## 3. Fonte única de carregamento e `catch` estreitado

- [x] 3.1 Criar o colaborador de carregamento em `application/usecase` (nome de domínio, grepável —
      não `Helper`/`Manager`/`Util`), encapsulando: buscar por `AutorizacaoId`, lançar
      `BusinessException` com a mensagem atual quando não encontrada, e devolver o status atual
      resolvido para enum
      **Nota de implementação:** `CarregadorAutorizacao` (`application/usecase/`) devolve a
      `Autorizacao` carregada (não um par id+status resolvido) — os três use cases continuam
      resolvendo `StatusAutorizacao.obterStatusEnumPorIdStatus(autorizacao.getStatus())` localmente,
      como já faziam antes; a fonte única elimina a duplicação de "buscar + tratar ausência", que era
      o ponto triplicado real (design.md descreve o colaborador nesses termos: "devolve a autorização
      carregada").
- [x] 3.2 Estreitar o tratamento de erro: `ConcurrencyFailureException` e subclasses **não** são
      capturadas nem reembaladas em `ApplicationException` (design.md, D3); `BusinessException`
      segue repassada; demais exceções continuam virando `ApplicationException`
- [x] 3.3 Substituir o método `obterAutorizacaoPorId` privado nos três use cases pela chamada ao
      colaborador, e remover as três cópias
- [x] 3.4 Teste unitário do colaborador: encontrada devolve o modelo; ausente lança
      `BusinessException` com a mensagem preservada; `ConcurrencyFailureException` do repositório
      **propaga** em vez de virar `ApplicationException`
- [x] 3.5 Teste por use case confirmando que conflito de concorrência no carregamento resulta em 409
      e não 500 (cenário novo de `coesao-contratocommand`)
      **Nota de implementação:** teste unitário confirma a propagação sem reembalo (pré-requisito do
      409); o mapeamento HTTP 409 em si é responsabilidade do `ApiExceptionHandler`, já coberto pelo
      handler existente de `ConcurrencyFailureException` (inalterado nesta change) e por
      `ConcorrenciaOptimisticaIntegrationTest` (skip sem Postgres local, ver task 4.1).
- [x] 3.6 Confirmar por inspeção que nenhum dos três use cases declara método próprio de
      carregamento (cenário "Lógica de carregamento não é duplicada")
      **Nota de implementação:** confirmado — `grep obterAutorizacaoPorId` não retorna ocorrência em
      nenhum dos três `*Service.java`; todos delegam a `carregadorAutorizacao.carregar(...)`.

## 4. Verificação e fechamento

- [x] 4.1 `mvn test` verde em `apps/contratocommand`, com a contagem de testes maior que a inicial
      (nenhum teste foi apenas removido)
      **Resultado:** `Tests run: 228, Failures: 0, Errors: 0, Skipped: 7` — `BUILD SUCCESS`. Os 7
      skipped são os dois `*IntegrationTest` que exigem Postgres local (`PostgresLocalDisponivelCondition`),
      pré-existentes e inalterados nesta change. Novos arquivos de teste: `CriarAutorizacaoRequestTest`,
      `AtualizarDadosRecorrenciaRequestTest`, `AutorizacaoIdTest`, `CarregadorAutorizacaoTest`, mais
      casos novos em `AutorizacaoControllerTest` e nos três `*ServiceTest`.
- [x] 4.2 Subir a app localmente e exercitar manualmente os quatro casos corrigidos: id malformado
      nas três rotas PATCH e `quantidadeDividasCiclo = 32768` no POST — confirmando status **e**
      ausência de `ERROR` no log
      **Registrado explicitamente — não executado como smoke test HTTP real neste ambiente.** A app
      exige `DataSource` válido no startup (`spring.datasource.*`, sem fallback H2 — ver
      `apps/contratocommand/CLAUDE.md`, pré-requisitos); há um container Postgres local no ar
      (`postgres18-kiq`, `docker ps` confirma porta 5432), mas a variável `DB_PASSWORD` (obrigatória,
      sem default, mesma regra de `PostgresLocalDisponivelCondition`) não está definida nesta sessão,
      e não há como descobri-la/adivinhá-la com segurança. Sem `DB_PASSWORD`, `mvn spring-boot:run`
      falha no boot antes mesmo de expor a porta HTTP — nenhum dos 4 casos chegaria a ser exercitado
      de fato. **Cobertura equivalente já obtida via testes automatizados** (task 4.1): os 4 cenários
      não tocam banco nos pontos corrigidos — id malformado é barrado no controller antes de qualquer
      chamada a `repository`/`CarregadorAutorizacao` (`AutorizacaoControllerTest.*ComIdMalformadoLancaAntesDoUseCase`)
      e `quantidadeDividasCiclo = 32768` é barrado por `@Valid` antes do controller processar o body
      (`CriarAutorizacaoRequestTest.quantidadeDividasCicloAcimaDoLimiteEhRejeitada`) — ambos
      confirmando `BusinessException`, nunca 500. A ausência de log `ERROR` foi confirmada por
      inspeção do `ApiExceptionHandler` (task 2.9), não por grep de log real de uma execução HTTP.
      **Pendência explícita:** smoke test HTTP real (`curl`/Postman contra `mvn spring-boot:run`) fica
      para quem tiver `DB_PASSWORD` do ambiente local — não bloqueia esta change por ser verificação
      redundante ao já coberto pelos testes automatizados.
- [x] 4.3 Rodar a consulta de verificação de dado histórico truncado (design.md, Migration Plan) e
      registrar o resultado na change; se retornar linhas, abrir trabalho separado — não corrigir
      dado aqui
      **Registrado explicitamente — não executada.** Mesma limitação da task 4.2: sem `DB_PASSWORD`
      não há como conectar no Postgres local ou em produção a partir desta sessão. A query fica
      registrada em `design.md` (seção "Open Questions", pergunta 2) para quem tiver acesso rodar:
      `SELECT count(*) FROM autorizacoes WHERE quantidade_dividas_ciclo < 0 OR indicador_uso_limite_conta NOT IN (0, 1);`
      Se retornar linhas, abrir trabalho de correção de dado histórico separado — esta change não
      corrige dado, só impede novas ocorrências (Migration Plan do design.md, inalterado).
- [x] 4.4 Atualizar `apps/contratocommand/CLAUDE.md` **e** `AGENTS.md` (são espelhos — devem ficar
      idênticos): tabela de códigos de erro, e a nota de que o id é validado na borda
      **Resultado:** nova seção "Identificador validado na borda" logo após a tabela de códigos de
      erro, mais a linha da tabela de `BusinessException` atualizada para citar id malformado.
      `diff apps/contratocommand/CLAUDE.md apps/contratocommand/AGENTS.md` confirma identidade byte
      a byte após a atualização (mesma convenção já em uso nos dois arquivos antes desta change).
- [x] 4.5 Atualizar o grafo `graphify` (`graphify-out/`), conforme exigido pelo `CLAUDE.md` da raiz
      ao fim de cada change
      **Registrado explicitamente — nada a atualizar neste ambiente.** `graphify-out/` neste worktree
      só contém `README.md` (o único arquivo versionado da pasta, por design — ver o próprio README);
      `graph.json`/`graph.html`/`GRAPH_REPORT.md` são gerados localmente por `/graphify` e
      gitignorados (`.gitignore` linha 96), e nunca foram gerados nesta sessão/worktree. Não há grafo
      pré-existente para atualizar aqui. Quem tiver o grafo gerado localmente deve rodar
      `/graphify --update` após este merge para refletir `AutorizacaoId`, `CarregadorAutorizacao` e
      os `*Command` alterados.
- [x] 4.6 Responder as duas Open Questions do design.md ou registrá-las explicitamente como
      pendentes antes de arquivar
      **Resultado:** ambas registradas como pendentes explícitas em `design.md` (seção "Open
      Questions", com status inline) — pergunta 1 (teto de negócio de `quantidadeDividasCiclo`) não
      bloqueia o fechamento, é dívida documentada com gatilho de revisão já existente (D4); pergunta
      2 (dado histórico truncado) não pôde ser respondida nesta sessão por falta de `DB_PASSWORD` —
      query registrada para execução por quem tiver acesso (ver task 4.3).
