## 0. Pré-requisito externo

- [x] 0.1 Confirmado: `limpar-codigo-das-apps` está 27/27 concluída e
      `docs/contrato-api-para-gateway.md` existe (16.333 bytes). Tarefas 2.4 e 3.1 desbloqueadas.

## 1. Resolver o que bloqueia

- [x] 1.1 Q1 — **respondida em 2026-08-10, ver D6.** Investigado o histórico completo
      (`git log --all --pretty=format: --name-only`, todas as branches): nada se perdeu.
      `docs/post-autorizacoes.txt` **existe em HEAD** e os 2 links para ele estão com base
      relativa errada; `docs/comandos-sql.txt` e `docs/resultado-poc/` **nunca existiram em
      commit algum** — são referências aspiracionais. Deixou de bloquear a fase 4.
- [x] 1.2 Q3 — **respondida em 2026-08-10, ver D7: remover**
      `docs/run_postgres16_ja_com_cron_partman/`. Os links antigos que o stub protege são internos
      e são corrigidos nesta mesma change; e o diretório diz `postgres16` enquanto o conteúdo diz
      PostgreSQL 18. A remoção vira a tarefa 3.6.
- [x] 1.3 Confirmado por código e teste: os dois `ApiExceptionHandler` mapeiam
      `MethodArgumentNotValidException` para `HttpStatus.UNPROCESSABLE_CONTENT` (422) —
      `BAD_REQUEST` não aparece em nenhum dos dois arquivos. Testes
      "MethodArgumentNotValidException → 422 com ocorrências por campo" já passam nas duas apps
      (confirmado nos runs da change `limpar-codigo-das-apps`: 12/12 command, 6/6 query). D4
      confirmado — a spec está errada, o código está certo. Tarefa 5.1 segue como planejada.

## 2. Podar o README do contratocommand

- [x] 2.1 Inventário feito antes de cortar: nada de único ficou fora. O que saiu era fictício
      (Strategy Pattern, `ContratacaoOrquestradorService`, `PixAutoService`/`PixAutoRepository`/
      `PixAutoMapper` — nenhuma dessas classes existe no código atual), duplicado do `CLAUDE.md`,
      ou genérico de baixo valor (links externos ao Spring/MapStruct, metadados de "Organização/
      Grupo/Versão"). Achado extra durante o inventário: a seção "⚙️ Configurações Spring Boot"
      mostrava `ddl-auto: update`; o `application.yaml` real usa `ddl-auto: none` — mais uma
      seção fabricada, não só duplicada. E "Testes de Integração" citava `mvn test -P integration`
      — não há `<profiles>` no `pom.xml`, também fictício.
- [x] 2.2 Seção "⚠️ Notas Importantes Sobre Java 25 Preview Features" removida.
- [x] 2.3 Seção "📝 Alterações Recentes - v0.0.1" removida (era só exemplos de código fictício —
      Strategy Pattern, DTOs, MapStruct, composite key — nenhum bate com o código atual).
- [x] 2.4 "📚 API REST Endpoints" reduzida à tabela de método/caminho/descrição; exemplos de
      request/response removidos. Já preservados em `docs/contrato-api-para-gateway.md` (tarefa
      0.1 confirmada).
- [x] 2.5 Deduplicação resolvida por reescrita completa — a versão nova tem uma seção de testes e
      nenhuma de "Padrões de Design" (fictícios, removidos por completo — ver 2.1).
- [x] 2.6 "🤝 Contribuindo", "📄 Licença", "👨‍💻 Informações do Projeto" e "📞 Suporte" removidas.
      Licença virou um link de uma linha para o `LICENSE` da raiz.
- [x] 2.7 Stack, arquitetura hexagonal, estrutura de pastas e diagrama de fluxo removidos —
      todos fictícios ou duplicados do `CLAUDE.md`. Ponteiro adicionado no topo do arquivo.
- [x] 2.8 Título corrigido: `# contratocommand` (o nome real do módulo, não "Contrato
      Command" nem o typo "Projetox").
- [x] 2.9 Resultado: 885 → **95 linhas**. Cobre pré-requisitos, variáveis de ambiente, build,
      execução (Maven/JAR/Docker), testes e a tabela de endpoints — nada além disso, com
      ponteiros para `CLAUDE.md` e `docs/contrato-api-para-gateway.md`.

## 3. Aplicar D1 nos demais arquivos de documentação de app

- [x] 3.1 `apps/contratoquery/README.md`: 356 → 93 linhas. Achado extra: o fluxo de "consulta
      por id" mostrava um `findById` único, sem a cascata de 3 níveis que o `CLAUDE.md` documenta
      (pós-`fallback-consulta-autorizacao-expurgada`); e a árvore de pacotes citava
      `domain/model/ContratoBase`, `CanaisConhecidosEnum`, `TipoConta`,
      `IdContaUUIDPartitionDistributor`/`AchaQtdeSemanas` — nenhuma dessas classes existe mais
      (confirmado por `find`). Removido, com ponteiro para `CLAUDE.md`.
- [x] 3.2 `apps/autorizacaostatus-producer/README.md` (198 → 68 linhas) e
      `apps/eventos-consumer/README.md` (182 → 62 linhas). **Achado grave no producer**: o README
      descrevia uma arquitetura inteiramente diferente da real — pacote `infrastructure/sqs/` com
      `SqsClient` bruto em loop de virtual thread (`SmartLifecycle`), versões antigas
      (`kafka-clients 3.7.1`, `avro 1.11.3`). O código real usa `entrypoint/sqs/` com
      `@SqsListener` (Spring Cloud AWS), e o `CLAUDE.md` afirma explicitamente "não existe pacote
      `infrastructure/`" — confirmado por `find`, e as versões reais são `avro 1.11.4`/
      `kafka-clients 3.9.2`. É a arquitetura anterior à migração `migrar-sqs-listener-spring-cloud-aws`,
      nunca atualizada no README. O `eventos-consumer` já estava fiel (só duplicava o `CLAUDE.md`,
      sem fabricação) — corte mais simples.
- [x] 3.3 Criado `apps/temporiza-autorizacao/README.md` (62 linhas, D5) — pré-requisitos,
      variáveis de ambiente, build/execução, testes, endpoint único (`/actuator/health`), ponteiro
      para `CLAUDE.md`. Era a única das 5 apps sem README.
- [x] 3.4 `apps/contratocommand/HELP.md` removido.
- [x] 3.5 `apps/contratocommand/docs/info_build-my-image-and-execute.md` (0 bytes) removido,
      junto do diretório `docs/` da app (ficou vazio). O conteúdo real segue em
      `docs/info_build-my-image-and-execute.md` (raiz).
- [x] 3.6 Confirmado por `grep -rln run_postgres16 --include=*.md .`: só changes **arquivadas**
      (histórico congelado) e esta própria change referenciam o caminho — nenhuma doc viva
      depende dele. `docs/run_postgres16_ja_com_cron_partman/` removido.

## 4. Corrigir os 26 links quebrados, por causa raiz

- [x] 4.1 Grupo "change arquivada" corrigido nos 6 pontos (producer, consumer, temporiza — os 3
      pares `CLAUDE.md`/`AGENTS.md`). Confirmado por `find`: as três changes citadas
      (`migrar-sqs-listener-spring-cloud-aws`, `refactor-eventos-consumer`,
      `temporizacao-jornada-01-pix-auto`) estão de fato arquivadas — exatamente o padrão previsto
      em D3. As três decisões viraram regra estável em capability (`consumo-eventos-autorizacao`,
      `consumo-eventos-kafka`, `temporizacao-jornada-01` + `agendamento-expiracao-valkey`) —
      aplicada a opção C (link para a spec). Dois links vizinhos que já apontavam para
      `changes/archive/<data>-<nome>/design.md` **já resolviam** e foram deixados intactos.
- [x] 4.2 Corrigido nos dois pares (`CLAUDE.md`+`AGENTS.md` do command):
      `docs/post-autorizacoes.txt` → `../../docs/post-autorizacoes.txt` e
      `docs/info_build-my-image-and-execute.md` → `../../docs/info_build-my-image-and-execute.md`
      (mesma causa — base relativa resolvia contra a pasta da app, não a raiz).
- [x] 4.2b Repontado nos dois pares do command e no `README.md` raiz:
      `docs/comandos-sql.txt` → `infra/local/postgres/exemplos-queries.sql`;
      `docs/resultado-poc/POC_PARTICIONAMENTO_BUFFER_RING_UUIDV7.md` →
      `docs/arquitetura/modelo-dados-e-dados-poc-testada-para-essa-implementacao.md` — confirmado
      por conteúdo: o título real do arquivo é "POC: Particionamento com Buffer Ring e UUID-V7
      Reversível", batendo exatamente com o link antigo. Achado extra: `docs/strategyProduto/`,
      citado na mesma lista, também não existe (Strategy Pattern já removido do código) — removida
      a linha, sem substituto (não há para onde repontar).
- [x] 4.2c Os 3 links restantes do grupo (`jornada-tecnica.txt`, `sql-comandos.txt`,
      `tradeoff-estrategias-particionamento-postgres.txt`) estão dentro de
      `docs/arquitetura/modelo-dados-...md`, não em `apps/`/`README.md` raiz — tratados na tarefa
      4.3 junto com os outros links desse mesmo arquivo.
- [x] 4.3 Corrigido em `docs/arquitetura/modelo-dados-...md`, seção "Referências e Recursos":
      2 dos 5 links de código (`PixAutoAutorizacaoService.java`, `PixAutoAutorizacaoMapper.java`)
      apontavam para classes que **não existem** — mesma fauna fictícia do Strategy Pattern
      encontrada no README do command; removidos sem substituto. Os outros 3
      (`IdContaUUIDPartitionDistributor`, `ControleExpurgoAutorizacao`, `ReversibleUUIDv7`) existem
      de verdade; caminho corrigido para `../../apps/contratocommand/src/...`. Dos 3 links de
      `docs/resultado-poc/*.txt` (tarefa 4.2c): `sql-comandos.txt` existe **no mesmo diretório**
      (`docs/arquitetura/sql-comandos.txt`, 143 linhas de DDL real) — link corrigido para
      `sql-comandos.txt`; `jornada-tecnica.txt` e `tradeoff-estrategias-particionamento-postgres.txt`
      nunca existiram em commit algum (confirmado por `git log --all`) — removidos sem
      substituto.
- [x] 4.4 `./LICENSE` corrigido: `apps/contratocommand/README.md` já resolvido na reescrita
      da fase 2 (`../../LICENSE`); `docs/arquitetura/based-java-aplication.md` corrigido agora
      para `../../LICENSE`.
- [x] 4.5 `./compatibility-tests/` em `docs/floci-aws-local/floci-aws-local.md`: nunca existiu
      neste repositório (`find`/`git log --all` confirmam). Investigado o contexto — descreve a
      estrutura do **próprio repositório do Floci** (ferramenta externa: `sdk-test-java`,
      `sdk-test-node` etc.), não deste monorepo. Link markup removido, mantida a prosa
      esclarecendo que é do repo do Floci.
- [x] 4.6 `README.md` raiz corrigido: linha 183 da tabela "Documentação" trocada de
      `apps/temporiza-autorizacao/CLAUDE.md` (o contorno da ausência) para
      `apps/temporiza-autorizacao/README.md` (criado na tarefa 3.3).

## 5. Corrigir a contradição de spec

- [x] 5.1 `openspec/specs/contrato-api-consistente/spec.md` corrigido: 400 → 422 no requisito de
      status por origem de erro e no cenário "Violação de Bean Validation retorna 400" (agora
      "...retorna 422"). **Achado extra no mesmo arquivo**: o requisito "Contrato OpenAPI publicado
      e derivado do código" exigia springdoc — que a change `limpar-codigo-das-apps` (executada
      antes desta, mesma sessão) removeu por completo. Reescrito para "Contrato de API não é
      documentado dentro do código dos serviços", alinhado à capability `doc-api-fora-do-codigo`.
- [x] 5.2 Razão registrada em bloco de citação no requisito (decisão D3, 2026-08-09): a distinção
      formato/regra é carregada pelo shape da resposta, não pelo status; e verificação empírica
      citada (`BAD_REQUEST` não aparece em nenhum dos dois `ApiExceptionHandler`).
- [x] 5.3 Varredura (`grep -rln "\b400\b" openspec/specs/*/spec.md`) encontrou 2 specs adicionais
      com `400`: `consultar-autorizacao-por-id` (UUID sintaticamente inválido no path) e
      `validacao-header-jornada` (header `tipoJornada` ausente). **Nenhuma delas era o mesmo caso
      do D3** — são falhas de *binding* do Spring MVC (`MethodArgumentTypeMismatchException`,
      `MissingRequestHeaderException`), não de `@Valid`/`MethodArgumentNotValidException`. Verificado
      por teste (`@WebMvcTest`, sem banco) em vez de assumir: as duas retornam **500**, não 400 —
      o catch-all `@ExceptionHandler(Exception.class)` intercepta essas exceções antes do
      tratamento default do Spring, porque nenhum dos dois `ApiExceptionHandler` tem handler
      dedicado para elas. **Defeito de comportamento real, não só de documentação** — cliente que
      erra a sintaxe do UUID ou esquece o header recebe "erro inesperado" 500 em vez de um 4xx
      informativo. As duas specs foram corrigidas para descrever o comportamento real (500),
      com nota explícita de que é defeito conhecido e que corrigi-lo é mudança de comportamento,
      fora do escopo desta change de documentação. Candidato a proposta de change própria.

## 6. Revisão de infra/ e fechamento

- [x] 6.1 Os 14 `README.md` de `infra/` lidos e conferidos. Spot-check de valores concretos
      (`vpc_name=vpc-arj`, `nat_gateway_count=3`, `cluster_name=arj-cluster` etc.) contra
      `variables.tf` real: bateram exatamente. **Um defeito real encontrado e corrigido**:
      `infra/local/redis/README.md` tinha os comandos de debug com as chaves Redis **erradas** —
      `agenda:pixauto:j1` e `stream:expiracoes:pixauto:j1` (sem hash tag, ordem trocada), quando a
      config real (`application.yaml` do temporiza) usa `agenda:{pixauto:j1}` e
      `stream:{pixauto:j1}:expiracoes`. Copiar e colar os comandos do README consultaria uma chave
      diferente da que a aplicação usa e retornaria vazio em silêncio — corrigido, com nota
      explicando por que a hash tag importa. Estrutura mantida conforme D7 (um README por
      módulo).
- [x] 6.2 Verificação final rodada no repositório inteiro (`apps/`, `docs/`, `infra/`, raiz):
      **zero links quebrados**. Controle de sanidade: injetado um link propositalmente quebrado
      num teste isolado e confirmado que o mecanismo o detecta — não é o falso negativo silencioso
      do `grep -P` que ocorreu na exploração inicial.
- [x] 6.3 `diff` dos 5 pares `CLAUDE.md`/`AGENTS.md`: zero diferenças em todos. Toda edição feita
      nesta change (correção do 404 fictício no command, links de `docs/` reescritos, links das
      changes arquivadas repontados) foi replicada nos dois arquivos de cada par.
- [x] 6.4 Confirmado: os dois offensores maiores (Java 25 preview features fictícias e
      `ddl-auto: update` fictício, ambos no README do command) foram removidos na fase 2.
      Varredura final (`grep -in "enable-preview|preview feature|add-modules|add-opens|ddl-auto"`)
      nos 5 `README.md` reescritos: vazia. Nenhuma afirmação de build sem correspondência
      sobrevive.
- [x] 6.5 Resultado de 4.2c registrado no `design.md` (D6): dos 3 links de
      `docs/resultado-poc/*.txt`, `sql-comandos.txt` tinha destino real (existia como
      `docs/arquitetura/sql-comandos.txt`, só com caminho relativo errado — corrigido);
      `jornada-tecnica.txt` e `tradeoff-estrategias-particionamento-postgres.txt` nunca existiram
      em commit algum (confirmado por `git log --all`) — removidos sem substituto.
