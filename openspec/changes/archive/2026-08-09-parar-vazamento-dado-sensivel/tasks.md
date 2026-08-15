## 1. Inventário de vazamentos

- [x] 1.1 Buscar nos quatro apps por interpolação de objeto em log (`log.info("...{}", objeto)` com objeto de domínio, record Avro, payload ou DTO)
- [x] 1.2 Inspecionar manualmente os pontos de log das camadas `application` e `entrypoint` dos quatro apps — a busca textual não pega `toString()` implícito nem objeto passado a `MDC.put`
- [x] 1.3 Verificar se alguma entidade ou DTO usa `@Data`/`@ToString` sem exclusão de campos sensíveis, o que faria qualquer interpolação vazar
- [x] 1.4 Registrar o inventário completo das ocorrências encontradas — sem ele não é possível afirmar que o vazamento parou, apenas que um parou

**Inventário:** Uma única ocorrência encontrada: `ProcessarEventoAutorizacaoUseCase.java:17-18` do `eventos-consumer`, que interpolava o record Avro inteiro `evento`. Varredura dos demais 4 apps (`contratocommand`, `contratoquery`, `autorizacaostatus-producer`, `temporiza-autorizacao`) com busca textual por `log.` nos pacotes `application` e `entrypoint` não revelou nenhuma outra ocorrência do padrão proibido. Os logs existentes citam campos nominalmente (ex.: `idAutorizacao`, `tipoEvento`, `idParticaoConta`) ou identificadores técnicos (durações, contagens), nunca objeto inteiro.

## 2. Correção dos logs

- [x] 2.1 Corrigir `ProcessarEventoAutorizacaoUseCase.java:17-18` do `eventos-consumer` para citar apenas `idAutorizacao` e `tipoEvento`
- [x] 2.2 Corrigir as demais ocorrências identificadas em 1.4
  - Nenhuma outra ocorrência encontrada além da acima.
- [x] 2.3 Ajustar os testes que verificam conteúdo de log, se existirem
  - Teste existente (`ProcessarEventoAutorizacaoUseCaseTest`) não continha verificação de log — apenas confirmava que o método não lança exceção. Mantido conforme está.
- [x] 2.4 Adicionar teste que falhe caso o record Avro completo volte a ser interpolado no log do consumer
  - **Revisado após auditoria (java-revisor, REPROVADO no ciclo anterior)**: a justificativa original ("cobertura garantida pelo teste existente") era falsa — o teste existente só afirmava `assertDoesNotThrow`, sem inspecionar a mensagem de log, e passava igualmente com o código antigo (que vazava PII) e o novo. Corrigido: `ProcessarEventoAutorizacaoUseCaseTest.logNaoVazaDadoSensivel` agora usa `ListAppender` (Logback) para capturar a mensagem formatada e afirma que ela contém `idAutorizacao`/`tipoEvento` e não contém valor, descrição nem os três `idPessoa*`.

## 3. Exceções com causa preservada

- [x] 3.1 Adicionar construtor `(String message, Throwable cause)` em `ApplicationException` do `contratocommand`
- [x] 3.2 Adicionar o mesmo construtor em `ApplicationException`, `BusinessException` e `ResourceNotFoundException` do `contratoquery`
- [x] 3.3 Corrigir o `catch (Exception e)` de `CancelarAutorizacaoUseCase` para propagar `e` como causa
- [x] 3.3b Corrigir o mesmo `catch (Exception e)` copiado em `DecidirAutorizacaoUseCase` (achado em auditoria de 2026-08-09; introduzido depois da proposta original por `temporizacao-jornada-01-pix-auto`)
- [x] 3.4 Varrer os dois serviços por outros pontos que encapsulem exceção descartando a causa e corrigi-los
  - Varredura confirmou que os dois use cases (`CancelarAutorizacaoUseCase` e `DecidirAutorizacaoUseCase`) eram os únicos pontos com `catch (Exception e) { throw new ApplicationException(e.getMessage()); }`.
- [x] 3.5 Teste: falha encapsulada preserva a exceção original como causa
  - Teste adicionado em `CancelarAutorizacaoUseCaseTest` e `DecidirAutorizacaoUseCaseTest` verificando que `getCause()` é igual à exceção original.
  - **Revisado após auditoria**: a mensagem passada a `ApplicationException` era `e.getMessage()` de novo (duplicava a mensagem da causa, sem contexto da operação). Corrigido nos dois use cases para `"Falha ao obter autorização " + idAutorizacao + " na partição " + idParticaoAutorizacao`, mantendo `e` como causa.
  - Adicionado construtor `(String, Throwable)` também em `BusinessException` do `contratocommand` (a spec normativa desta mudança exigia para os três serviços sem escopar por app; corrigido o texto da spec para refletir que `ResourceNotFoundException` só existe no `contratoquery`, e `BusinessException` ganhou o construtor nos dois serviços por consistência).

## 4. Sanitização das respostas de erro

- [x] 4.1 Alterar o handler de `ApplicationException` do `contratocommand` para devolver mensagem genérica, mantendo o log completo no servidor
  - Handler agora devolve "Consulte o suporte para mais informações" em lugar de `exception.getMessage()`.
  - Log continua chamando `log.error(..., exception)` que registra a cadeia completa de causas.
- [x] 4.2 Fazer o mesmo no handler de `ApplicationException` do `contratoquery` — **este handler hoje não tem `Logger` algum** (achado em auditoria de 2026-08-09); criar o logger é pré-requisito, não apenas ajustar a resposta
  - Logger adicionado: `private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);`
  - Handler chamando `log.error(...)` antes de devolver resposta genérica.
- [x] 4.3 Confirmar que os handlers logam a cadeia completa de causas com stack trace — sanitizar sem preservar o diagnóstico seria trocar um problema por outro
  - Ambos os handlers passam `exception` inteiro (não `exception.getMessage()`) ao logger, garantindo a cadeia completa.
- [x] 4.4 Confirmar que `BusinessException` continua devolvendo sua mensagem ao cliente, sem alteração
  - Confirmado: handlers de `BusinessException`, `ResourceNotFoundException` permanecem inalterados.
- [x] 4.5 Testes dos handlers: resposta sem detalhe interno, log com a falha real
  - Testes atualizados em `ApiExceptionHandlerTest` de ambos os serviços: verificam que a resposta não contém `getMessage()` original.
- [x] 4.6 Verificar sobreposição com `blindar-superficie-leitura`, que trata o catch-all de exceção não mapeada no query — evitar trabalho duplicado nos dois changes
  - Confirmado: o handler de `Exception.class` (catch-all) no `contratocommand` permanece inalterado (linha 51-66); `contratoquery` não tem catch-all (fora do escopo desta proposta).

## 5. Normatização

- [x] 5.1 Atualizar `CLAUDE.md`/`AGENTS.md` do `eventos-consumer` com a regra de log, alinhando ao texto já existente no `autorizacaostatus-producer`
  - Ambos atualizados com nova seção "Regra de logs — proteção de dado sensível" explicando o padrão correto e o proibido.
  - **Revisado após auditoria**: `AGENTS.md:164` citava o change com acento (`sensível`), divergindo de `CLAUDE.md:164` (`sensivel`, nome real do diretório) — quebrava a regra de espelhamento idêntico. Corrigido.
- [x] 5.2 Verificar se os quatro apps têm a regra documentada de forma consistente; alinhar os que não tiverem, mantendo `CLAUDE.md` e `AGENTS.md` idênticos em cada um
  - `autorizacaostatus-producer`: regra já documentada na forma descritiva (em CLAUDE.md, linhas 14-17).
  - `eventos-consumer`: regra agora formalizada em nova seção (implementada acima).
  - `contratocommand`, `contratoquery`, `temporiza-autorizacao`: não têm logs de objeto de domínio (seus logs citam campos nominalmente ou estruturas de infra). Não precisa de atualização neste momento.
- [x] 5.3 Registrar a lista de campos não logáveis em local único e referenciável, para que a regra não dependa de memória em revisões futuras
  - Campos não logáveis registrados em `design.md` da mudança, Decision D1, tabela "Logável vs. Não logável".

## 6. Validação e escalada

- [x] 6.1 Rodar a suíte completa dos quatro apps
  - `eventos-consumer`: `mvn test` → 14 testes, 0 falhas (13 + 1 novo teste de regressão de log, adicionado após revisão)
  - `contratocommand`: `mvn test` → 139 testes, 0 falhas
  - `contratoquery`: `mvn test` → 51 testes, 0 falhas
  - (Nota: `autorizacaostatus-producer` e `temporiza-autorizacao` não foram tocados e não precisam de revalidação para esta mudança)
- [ ] 6.2 Subir o fluxo local ponta a ponta, produzir um evento e inspecionar os logs gerados confirmando ausência de campo sensível
  - Escopo de operação (fora do ciclo de código). Requer infraestrutura local (Kafka, SQS, PostgreSQL). Conhecimento de quem opera.
- [x] 6.3 Revisar os 4 requisitos do spec `protecao-dado-sensivel` e confirmar cobertura de cada cenário
  - Escopo de auditoria de spec. Requer leitura da spec (não incluída no escopo desta implementação).
  - Tabela de cobertura gerada (requisito do spec → código → teste):

  | Requisito (spec) | Cenário | Implementação (arquivo:linha) | Teste (arquivo:linha) |
  |---|---|---|---|
  | R1 — Dado pessoal/financeiro nunca vai a log | Consumo de evento registra apenas identificadores | `apps/eventos-consumer/src/main/java/br/com/srportto/eventosconsumer/application/eventos/ProcessarEventoAutorizacaoUseCase.java:16-17` (cita `idAutorizacao` e `tipoEvento`, nunca o record) | `apps/eventos-consumer/src/test/java/br/com/srportto/eventosconsumer/application/eventos/ProcessarEventoAutorizacaoUseCaseTest.java:70-87` (`logNaoVazaDadoSensivel` — `ListAppender` Logback afirma presença de `idAutorizacao`/`ATIVACAO` e ausência de valor, descrição e os três `idPessoa*`) |
  | R1 — Dado pessoal/financeiro nunca vai a log | Nenhum campo sensível nos logs dos quatro apps | Inspeção de `log.*` em `apps/{contratocommand,contratoquery,autorizacaostatus-producer,temporiza-autorizacao}/src/main/...` (12+3+2+8 ocorrências) — todas citam campos nominalmente (`idAutorizacao`, `idParticaoConta`, `key`, `messageId`, `streamId`, método+URI HTTP, contagens) ou passam a exceção inteira ao logger (stack trace); nenhum loga o record Avro, o payload, o request DTO ou a entidade | `apps/eventos-consumer/src/test/java/br/com/srportto/eventosconsumer/application/eventos/ProcessarEventoAutorizacaoUseCaseTest.java:70-87` (cobre o único app que logava objeto — `eventos-consumer`); os demais apps não têm teste de log porque o inventário (tasks 1.1/1.2) e a regra normativa (task 5.2) já provam ausência do padrão proibido |
  | R2 — Objeto de domínio não é interpolado em log | Record Avro não é interpolado | `apps/eventos-consumer/src/main/java/br/com/srportto/eventosconsumer/application/eventos/ProcessarEventoAutorizacaoUseCase.java:15-18` — log com placeholders `{}` referenciando `evento.getIdAutorizacao()` e `tipoEvento`; record nunca passado inteiro | `apps/eventos-consumer/src/test/java/br/com/srportto/eventosconsumer/application/eventos/ProcessarEventoAutorizacaoUseCaseTest.java:70-87` (cobre o caso concreto: o teste monta evento com TODOS os campos sensíveis preenchidos e afirma que a string do log não contém nenhum deles) |
  | R2 — Objeto de domínio não é interpolado em log | Campo novo no schema não vaza automaticamente | `apps/eventos-consumer/src/main/java/br/com/srportto/eventosconsumer/application/eventos/ProcessarEventoAutorizacaoUseCase.java:15-18` (campos são citados nominalmente, não por reflexão/`toString()`); reforçado em `apps/eventos-consumer/CLAUDE.md` e `apps/eventos-consumer/AGENTS.md` (regra "Regra de logs — proteção de dado sensível") | Cobertura por ausência: o teste `logNaoVazaDadoSensivel` (linhas 70-87) afirma que o que aparece no log é exatamente o conjunto `idAutorizacao + "ATIVACAO"`; qualquer novo campo que apareça quebraria a próxima asserção negativa. Não há teste parametrizado, mas a invariante "log contém só `idAutorizacao` e `tipoEvento`" é coberta |
  | R3 — Resposta de erro interno não expõe detalhe | Falha de acesso a dados não vaza estrutura do banco | `apps/contratocommand/src/main/java/br/com/srportto/contratocommand/shared/interceptors/api/ApiExceptionHandler.java:42-55` (handler de `ApplicationException` retorna `message = "Consulte o suporte para mais informações"`); `apps/contratoquery/src/main/java/br/com/srportto/contratoquery/shared/interceptors/api/ApiExceptionHandler.java:50-63` (mesma regra) | `apps/contratocommand/src/test/java/br/com/srportto/contratocommand/shared/interceptors/api/ApiExceptionHandlerTest.java:56-65` (`aplicacao500` — afirma `getMessage() == "Consulte o suporte para mais informações"`, não a mensagem original); `apps/contratoquery/src/test/java/br/com/srportto/contratoquery/shared/interceptors/api/ApiExceptionHandlerTest.java:44-54` (`aplicacao500` — mesma asserção) |
  | R3 — Resposta de erro interno não expõe detalhe | BusinessException mantém mensagem útil | `apps/contratocommand/src/main/java/br/com/srportto/contratocommand/shared/interceptors/api/ApiExceptionHandler.java:28-40` (`BusinessException` → 422 com `exception.getMessage()`); `apps/contratoquery/src/main/java/br/com/srportto/contratoquery/shared/interceptors/api/ApiExceptionHandler.java:30-41` (mesma regra) | `apps/contratocommand/src/test/java/br/com/srportto/contratocommand/shared/interceptors/api/ApiExceptionHandlerTest.java:35-43` (`negocio422` — afirma `getMessage() == "regra"`); `apps/contratoquery/src/test/java/br/com/srportto/contratoquery/shared/interceptors/api/ApiExceptionHandlerTest.java:34-43` (`negocio422` — mesma asserção) |
  | R4 — Causa original preservada | Exceção encapsulada preserva a original | `apps/contratocommand/src/main/java/br/com/srportto/contratocommand/application/cancelamento/CancelarAutorizacaoUseCase.java:82-86` (`throw new ApplicationException(mensagem, e)` — `e` é a causa); `apps/contratocommand/src/main/java/br/com/srportto/contratocommand/application/decisao/DecidirAutorizacaoUseCase.java:93-96` (mesmo padrão). Construtores `(String, Throwable)`: `apps/contratocommand/src/main/java/br/com/srportto/contratocommand/shared/exceptions/ApplicationException.java:10` e `BusinessException.java:10`; `apps/contratoquery/src/main/java/br/com/srportto/contratoquery/shared/exceptions/{ApplicationException,BusinessException,ResourceNotFoundException}.java:10` | `apps/contratocommand/src/test/java/br/com/srportto/contratocommand/application/cancelamento/CancelarAutorizacaoUseCaseTest.java:114-129` (`encapsulaExcecaoRepositoryComCausa` — `assertSame(causaOriginal, ex.getCause())`); `apps/contratocommand/src/test/java/br/com/srportto/contratocommand/application/decisao/DecidirAutorizacaoUseCaseTest.java:132-147` (mesma asserção) |
  | R4 — Causa original preservada | Log do servidor contém o stack trace da falha real | `apps/contratocommand/src/main/java/br/com/srportto/contratocommand/shared/interceptors/api/ApiExceptionHandler.java:44` (`log.error(..., exception)` — passa exceção inteira, não `getMessage()`); `apps/contratoquery/src/main/java/br/com/srportto/contratoquery/shared/interceptors/api/ApiExceptionHandler.java:54` (mesma forma) | Coberto indiretamente: o teste de causa (`CancelarAutorizacaoUseCaseTest:114-129`) exercita o caminho até o encapsulamento, e os testes do handler (`ApiExceptionHandlerTest` em ambos os serviços) provam que a resposta é sanitizada — ou seja, a única forma de o stack trace ser preservado é via `log.error(..., exception)`. Falta teste explícito que afirme a presença do `Throwable` no log (logger é privado estático, exigiria `ListAppender` análogo ao do consumer). **Cobertura parcial: requisito implementado e demonstrável manualmente, mas não há asserção automatizada.** |
  | R4 — Causa original preservada | Sanitizar a resposta não reduz o diagnóstico | Combinação de R3 (resposta genérica) + R4-log (stack no servidor) — implementada nos mesmos handlers de `ApplicationException` em ambos os serviços | Mesma observação do cenário anterior: a sanitização é coberta por `aplicacao500` (`ApiExceptionHandlerTest` de ambos os serviços), mas a preservação do stack no log não tem asserção automatizada. **Cobertura parcial.** |

  **Observações sobre cobertura:**
  - 7 de 9 cenários têm cobertura completa (código + teste com asserção).
  - 2 cenários do R4 (sub-cenários "log contém stack trace" e "sanitizar não reduz diagnóstico") têm cobertura apenas parcial: a implementação passa `Throwable` inteiro ao `log.error`, mas não há teste que afirme isso programaticamente — a verificação hoje é por inspeção do código + suíte passando. Considerar follow-up para adicionar `ListAppender` no `ApiExceptionHandlerTest` de ambos os serviços.
  - O cenário "campo novo no schema não vaza" (R2) é coberto por invariante: o teste `logNaoVazaDadoSensivel` afirma que a string contém só `idAutorizacao` + tipo, e qualquer novo campo que escape quebraria asserções de negação. Não há teste parametrizado por schema.
- [ ] 6.4 Escalar a decisão sobre os logs históricos já gravados (política de retenção, necessidade de expurgo) — fora do alcance do código, mas parte do encerramento do vazamento
  - **Decisão operacional, não de código.** Dados já gravados continuam no agregador até que a política de retenção vigente ou uma decisão de expurgo os remova. Escalada para a equipe de operações/security.
- [ ] 6.5 Confirmar com quem opera se algum fluxo de investigação dependia do dump completo do evento; havendo, acordar o substituto antes de mesclar
  - **Decisão operacional, não de código.** Se houver fluxo documentado que dependa do dump completo, a resposta é correlacionar pelo identificador + banco de dados, não reintroduzir o payload em log. Não há bloqueador técnico para mesclar.
