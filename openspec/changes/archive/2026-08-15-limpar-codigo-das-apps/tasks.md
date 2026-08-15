## 1. Preservar o contrato antes de remover  ← bloqueante, e destrava a change B

> Confirmado em 2026-08-10: **o gateway ainda não tem o contrato**. Estas tarefas vêm antes de
> qualquer remoção — depois de apagar, recuperar exige `git show`.
>
> **A change `enxugar-documentacao-repo` não pode começar antes desta fase**, porque ela apaga
> duas das três fontes de contrato (D1b).

- [x] 1.1 Criado `docs/contrato-api-para-gateway.md` consolidando as três fontes (D1b), por
      endpoint. Achado durante a consolidação: os exemplos JSON dos dois READMEs estavam
      **desatualizados** em 6 pontos (shape de erro do query, `idAutorizacao` aninhado que não
      existe mais, resposta 201 incompleta, corpo de cancelamento com campo inexistente e faltando
      2 obrigatórios, listagem do query com campo fantasma `tipoProduto` e campos reais faltando,
      consulta por id incompleta). Verificado contra os DTOs reais
      (`AutorizacaoCompletaResponseDto`, `AutorizacaoResumidaResponseDto`,
      `AutorizacaoDetalheResponseDto`, `CancelarAutorizacaoRequest`, os dois
      `LayoutErrosApiResponse`); o consolidado usa os shapes reais, não os exemplos dos READMEs.
      As 6 divergências estão documentadas na seção final do arquivo.
- [x] 1.2 Rascunho conferido contra as três fontes durante a escrita (as divergências da tarefa
      1.1 só foram encontradas por essa conferência).
- [x] 1.3 Nota de prazo de validade no topo do arquivo.
- [x] 1.4 Fase 1 concluída — `enxugar-documentacao-repo` está destravada.

## 2. Ancorar em teste o que não pode regredir

- [x] 2.1 Linha de base: command 167/167 (7 skip), query 67/67 (6 skip), producer 61/64 (3 falhas
      de integração — Floci ausente), consumer 14/14, temporiza 29/33 (4 falhas — Valkey ausente).
      As falhas de producer/temporiza são pré-existentes (infra local fora do ar nesta sessão),
      confirmadas por stack trace: `Connection refused: localhost:4566` (Floci) e
      `RedisConnectionFailureException` (Valkey) — não regressão desta change.
- [x] 2.2 Cobertura verificada: `AutorizacaoControllerTest` cobre 200/201 (delegação);
      `ApiExceptionHandlerTest` cobre 422/409/500 nas duas apps. **Achado maior que uma lacuna de
      teste**: as anotações do command documentavam 404 em `cancelar`/`decidir` para "autorização
      inexistente" via uma classe `ResourceNotFoundException` que **não existe em lugar nenhum do
      código** (`grep -rl ResourceNotFoundException` no app inteiro não encontra nada — nem
      `src/main`, nem `src/test`). O `CLAUDE.md`/`AGENTS.md` do command replicava a mesma
      afirmação falsa na tabela de erros. Comportamento real, confirmado em
      `CancelarAutorizacaoUseCase`/`DecidirAutorizacaoUseCase.obterAutorizacaoPorIdEParticao`:
      autorização inexistente lança `BusinessException` → **422**, já coberto pelos testes de
      use case ("lança BusinessException quando a autorização não é encontrada") e por
      `ApiExceptionHandlerTest` (`BusinessException → 422`). Nenhum teste novo é necessário — o
      código já está correto e coberto; era a documentação (anotação e `CLAUDE.md`) que mentia.
      `docs/contrato-api-para-gateway.md` foi corrigido para refletir 422, não 404 (divergência 7
      do arquivo). Correção do `CLAUDE.md`/`AGENTS.md` do command fica para a tarefa 6.3.
- [x] 2.3 Adicionado `recursoEstaticoNaoEncontrado404` em `ApiExceptionHandlerTest` das duas
      apps — chama `handler.recursoEstaticoNaoEncontrado(new NoResourceFoundException(...), req())`
      e afirma 404. Nenhum teste existia para esse handler antes. Verde: command 12/12, query 6/6.
      Trava D2 antes da remoção do springdoc (fase 3).

## 3. Remover a documentação de API do código

- [x] 3.1 `contratoquery`: anotações removidas de `entrypoint/AutorizacaoController.java`,
      junto dos 7 `import io.swagger.v3.oas.*`. `@GetMapping`, `@RequestParam` (com
      `required`/`defaultValue`) e `@PathVariable` preservados intactos.
- [x] 3.2 `contratocommand`: mesma remoção em `entrypoint/AutorizacaoController.java`.
- [x] 3.3 `springdoc-openapi-starter-webmvc-ui` e `<springdoc.version>` removidos dos dois
      `pom.xml`, junto do comentário que citava `reconciliar-contrato-spec-doc`.
- [x] 3.4 `entrypoint/OpenApiGenerationTest.java` removido das duas apps.
- [x] 3.5 Javadoc do `@ExceptionHandler(NoResourceFoundException.class)` reescrito nos dois
      `ApiExceptionHandler` — não cita mais springdoc, explica a razão pelo comportamento (D2).
      Handler preservado.
- [x] 3.6 `mvn clean test`: command 167/167 verde (7 skip de integração, infra local ausente),
      query 67/67 verde (6 skip). Igual à linha de base — o teste de `OpenApiGenerationTest`
      removido foi compensado pelo `recursoEstaticoNaoEncontrado404` adicionado na fase 2.
- [x] 3.7 **Docker Desktop sem daemon no ar nesta sessão** (`docker info` conecta, `docker ps`
      falha: "failed to connect to the docker API") — não foi possível subir Postgres nem as apps
      completas para confirmar via HTTP real de ponta a ponta. Verificação equivalente feita com
      `@WebMvcTest` + `MockMvc` (mesma técnica que o `OpenApiGenerationTest` removido usava — sem
      banco, mas com o `DispatcherServlet` real, não chamada direta ao método): `GET
      /swagger-ui/index.html`, `GET /v3/api-docs` e um caminho arbitrário desconhecido responderam
      **404** nas duas apps. Teste escrito como scratch, rodado, e removido em seguida — a
      cobertura permanente já está em `ApiExceptionHandlerTest` (tarefa 2.3); manter os dois
      arquivos seria duplicar superfície de teste sem ganho. Se quiser confirmação com Postgres
      real no ar, rode `mvn spring-boot:run` nas duas apps com o Postgres local ativo e repita os
      três `GET`.

## 4. Higiene de código residual

- [x] 4.1 Removidos os 2 imports sem uso. `test-compile` verde nas duas apps.
- [x] 4.2 `-Xlint:all` testado no `maven-compiler-plugin` do command e **revertido no mesmo
      commit** — não existe categoria de lint para parâmetro sem uso no javac padrão (confirmado
      empiricamente: só emitiu avisos de MapStruct, processamento de anotação e
      `serialVersionUID`). Premissa original de D4 estava errada — ver D4 atualizado.
      Substituído por varredura heurística (script Python ad-hoc em
      `apps/**/src/main/**/*.java`): 22 ocorrências brutas.
- [x] 4.3 Triados os 22 achados: 12 falso-positivo de record (`@ConfigurationProperties`,
      `AutorizacaoPersistidaEvent`), 9 override de `Rule.aceita(context)` (framework/interface —
      confirma a ressalva original), 2 `@ExceptionHandler` com colisão de assinatura se o
      parâmetro sair (`conflitoLockOtimista`, registrado no Backlog identificado do `design.md`),
      1 genuinamente morto e sem risco de colisão.
- [x] 4.4 Removido o único candidato confirmado:
      `ApiExceptionHandler.conflitoEstadoObsoleto(StaleStateException exception, HttpServletRequest req)`
      → `conflitoEstadoObsoleto(HttpServletRequest req)` (`@ExceptionHandler(StaleStateException.class)`
      já declara a classe; Spring não exige o parâmetro para rotear). Teste ajustado (removida a
      construção agora sem uso da exceção) e import correspondente removido. `mvn clean test`:
      167/167 verde.
- [x] 4.5 Não há `-Xlint:all` a remover — nunca chegou a permanecer além do teste que o refutou
      em 4.2.

## 5. Marcação de refatoração futura

- [x] 5.1 Inventário real: dos "13 TODO/FIXME" contados na exploração inicial, **8 eram falso
      positivo** de `grep` — a string `AUTORIZACAO_ACEITA_POR_TODOS` contém a substring "TODO".
      Sobram **5 reais**, idênticos entre si (um por app):
      `// TODO: migrar para void main() (Java 25) quando o maven plugin suportar.` Todos os 5
      satisfazem o critério de D3 (gatilho 2 — bloqueio externo nomeado). Nenhuma remoção ou
      correção necessária.
- [x] 5.2 Dois candidatos novos identificados e marcados, ambos com medição **e** change aberta
      citada (gatilhos 1 e 3):
      - `AutorizacaoRepository.java` (contratoquery), sobre `findByIdUnicoContaContratanteAndStatusIn`:
        `// TODO: 148ms de planejamento por chamada, sem podar partição — ver change reduzir-custo-planejamento-consultas`
      - `ValkeyStreamConfig.java` (temporiza-autorizacao), sobre `expiracaoStreamSubscription`:
        `// TODO: consumidor nunca é removido do grupo (7 órfãos p/ 2 pods em 2026-08-09) — ver change limpar-consumidores-orfaos-stream`
      Total no repositório após a change: 7 (5 preexistentes + 2 novos) — dentro da estimativa de
      4-8. `mvn compile` verde nas duas apps.
- [x] 5.3 Os dois `TODO` novos cabem em uma linha cada e nomeiam a causa (custo de planejamento
      medido; ausência de `XGROUP DELCONSUMER`), não o sintoma.
- [x] 5.4 Uma oportunidade identificada não atendeu o critério de remoção segura e foi para o
      Backlog identificado do `design.md`, não virou `TODO` no código: os dois overloads de
      `ApiExceptionHandler.conflitoLockOtimista` têm parâmetro de exceção sem uso, mas removê-lo
      dos dois colide em assinatura idêntica — exige renomear método, fora do escopo de "parâmetro
      sem uso".

## 6. Fechamento

- [x] 6.1 `mvn clean test` nas cinco apps: command 167/167, query 67/67, consumer 14/14 — verdes.
      Producer 61/64 (3 falhas de integração, Floci ausente) e temporiza 29/33 (4 falhas,
      Valkey ausente) — **idêntico à linha de base da fase 2**, nenhuma regressão introduzida por
      esta change.
- [x] 6.2 Confirmado: `grep -rl "io\.swagger\|springdoc" --include=*.java apps/` e
      `grep -rl springdoc apps/*/pom.xml` retornam vazio nas cinco apps.
- [x] 6.3 Divergência real encontrada e corrigida (a mesma da tarefa 2.2): a tabela de códigos de
      erro do `CLAUDE.md`/`AGENTS.md` do `contratocommand` afirmava
      `404 | ResourceNotFoundException | Autorização inexistente` — classe inexistente no código.
      Linha removida; a entrada de `BusinessException` (422) passou a citar explicitamente que
      cobre "autorização inexistente" em `cancelar`/`decidir`, com a nota de que não existe 404
      nessas rotas. `contratoquery` não teve divergência equivalente — o
      `ResourceNotFoundException` de lá é real (usado em `consultarPorId`) e a documentação já
      estava correta.
- [x] 6.4 `CLAUDE.md` e `AGENTS.md` do command confirmados idênticos (`diff` vazio) após a
      correção.
