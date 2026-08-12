## 1. Reproduzir e ancorar o defeito em teste

- [x] 1.1 Criar teste de integração contra PostgreSQL real (Testcontainers ou a instância
      local) que cancele uma autorização cuja partição de expurgo de destino difira da
      partição atual, e afirme sucesso + linha na partição de destino. **Deve falhar agora**,
      com `ObjectOptimisticLockingFailureException` / `409`.
- [x] 1.2 Estender o mesmo teste para `PATCH /{id}/decisao` com `acao: EXPIRAR` e com
      `acao: REJEITAR`. Ambos devem falhar antes da correção.
- [x] 1.3 Adicionar caso que afirme ausência de linha remanescente na partição de origem
      (proteção contra movimentação parcial).

## 2. Corrigir a movimentação de partição (D1 + D2)

- [x] 2.1 Adicionar em `AutorizacaoRepository` a operação nativa de movimentação
      (`@Modifying` + `UPDATE autorizacoes SET id_particao_conta = ? WHERE id_autorizacao = ?
      AND id_particao_conta = ?`), devolvendo a contagem de linhas afetadas.
- [x] 2.2 Reescrever `ExpurgoAutorizacaoService.transferirParaExpurgo` conforme D1:
      `saveAndFlush` (dirty-check grava colunas de negócio e incrementa `@Version`) →
      movimentação nativa → `detach` → ajuste do `idParticaoConta` no `@EmbeddedId`.
      Preservar o atalho de "partição de destino igual à atual".
- [x] 2.3 Implementar D2: contagem de linhas afetadas diferente de 1 aborta a transação com
      exceção que `ApiExceptionHandler` já mapeia para `409`.
- [x] 2.4 Substituir o comentário sobre `flush`/`detach` por um que registre a **premissa**,
      não só o mecanismo: por que não se faz `merge` de instância detached aqui, e o papel do
      `@Version` nisso. Comentário em português (convenção do repo).
- [x] 2.5 Confirmar que os testes de 1.1–1.3 passam.

## 3. Reescrever a cobertura de testes (D4)

- [x] 3.1 Reavaliar `ExpurgoAutorizacaoServiceTest`: remover as asserções de *ordem de
      chamadas* (`inOrder(deleteById, flush, detach, save)`), que passaram a verde durante todo
      o período em que a operação estava quebrada. Manter apenas o que é legítimo em teste
      unitário (cálculo da partição de destino, atalho quando destino == origem).
- [x] 3.2 Ajustar `CancelarAutorizacaoUseCaseTest` e `DecidirAutorizacaoUseCaseTest` aos
      novos contratos do serviço.
- [x] 3.3 Rodar `mvn test` em `arj-contratocommand` e em `arj-contratoquery` (lê a mesma
      tabela).

## 4. Varredura por defeitos irmãos

- [x] 4.1 Buscar no `arj-contratocommand` outros pontos que combinem remoção e re-persistência
      da mesma instância na mesma transação (`detach(`, `deleteById(` seguidos de `save(`).
      Registrar o resultado, mesmo que vazio.
- [x] 4.2 Confirmar que nenhum outro caminho de escrita depende da ausência de `@Version`
      (cenário "Nenhum caminho de escrita depende da ausência de versão" da spec de
      concorrência).

## 5. Escopo da unicidade em partição de expurgo (D3) — entregável separável

> **Decisão tomada (2026-08-09): D3a.** A unicidade de `id_autorizacao_empresa` é regra sobre
> autorizações **ativas**, não invariante da tabela. Índice único parcial restrito às
> partições quentes. O `SET NOT NULL` das colunas da chave fica **fora de escopo** (dívida
> pré-existente, registrada em `design.md` › D3-pré).

- [x] 5.1 Escrever teste que reproduza a colisão: duas autorizações de **contas distintas**
      com o mesmo `id_autorizacao_empresa`, ambas expurgadas no mesmo balde semanal. Deve
      falhar com `DataIntegrityViolationException` antes da correção.
- [x] 5.2 Criar migration em `infra/local/postgres/migrations/`: `DROP CONSTRAINT
      uk_autorizacao_empresa_particao` + `CREATE UNIQUE INDEX ... (id_particao_conta,
      id_autorizacao_empresa) WHERE id_particao_conta < 900`, com script de reversão.
- [x] 5.3 Remover a `@UniqueConstraint` de `@Table` na entidade `Autorizacao` — índice
      parcial não é expressável em JPA — e substituí-la por comentário apontando a migration.
- [x] 5.4 Revalidar `existsByIdAutorizacao_IdParticaoContaAndIdAutorizacaoEmpresa` e a
      verificação prévia do `CriarAutorizacaoUseCase`: ela consulta apenas a partição quente
      da conta, o que já é coerente com o novo escopo — confirmar, não alterar por reflexo.
- [x] 5.5 Confirmar que o teste de 5.1 passa e que a idempotência de criação segue devolvendo
      `409` no caso legítimo.

## 6. Documentação

- [x] 6.1 Atualizar `apps/arj-contratocommand/CLAUDE.md` e `AGENTS.md` (espelhos — manter
      idênticos): descrever a nova mecânica de movimentação de partição e registrar a
      armadilha do `merge` de instância detached com `@Version` na seção "Armadilhas críticas".
- [x] 6.2 Se o escopo da constraint mudar (D3), atualizar a linha correspondente na tabela de
      códigos de erro e na seção de particionamento.
- [x] 6.3 Verificar se `apps/temporiza-autorizacao/CLAUDE.md` precisa de ajuste na tabela
      "Contrato de conclusão com o command" — o `409` deixa de ser resultado esperado do
      caminho feliz (o retry continua correto, apenas deixa de ser exercitado aqui).

## 7. Validação fim-a-fim

> **Decisão tomada (2026-08-09):** as autorizações hoje presas em `RECEBIDA`
> (`019fe814-…0006` e `019fe853-…0006`) **não serão reprocessadas** — são de teste em
> ambiente local, sem dado real a recuperar.

- [x] 7.1 Teste fim-a-fim com autorização nova: `POST` → aguardar o prazo da jornada 1 →
      confirmar que `temporiza-autorizacao` conclui na **primeira** tentativa, sem `409` no log
      do command e sem `XCLAIM` de reivindicação. **Executado em 2026-08-11** com autorização
      `019ff338-bbff-7a23-b612-750c6f9d0006` (`PIX_AUTO`/`SPI_J1`, empresa
      `TESTE-E2E-EXPURGO-1786492009`): criada às 20:46:49, agendada para 20:56:49, varredura
      moveu o agendamento vencido para o stream às 20:56:53.769, `DecidirAutorizacaoUseCase`
      recebeu `EXPIRAR` às 20:56:53.881 e `ExpurgoAutorizacaoService` completou a transferência
      às 20:56:53.897 — sem nenhuma linha de `409`/exceção no log do command nem de `XCLAIM`/
      reivindicação no log do temporiza. Primeira tentativa, sem retry.
- [x] 7.2 Confirmar no banco que a autorização do teste 7.1 está `REJEITADA` na partição de
      expurgo do dia, ausente da partição de origem, e com `version` incrementada. **Confirmado**:
      `autorizacoes_pe953` contém a linha (`status=6` `REJEITADA`,
      `motivo_status=REJEITADA_SISTEMA_TIMEOUT_J1`, `version=1`); `autorizacoes_pa6` (partição de
      origem) tem 0 linhas para o id — a versão subiu de 0 para 1, confirmando que o `UPDATE`
      nativo (D1) fez `UPDATE` real, não `INSERT`.
