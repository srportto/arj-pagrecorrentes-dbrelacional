## 1. Client de decisão — distinguir 409 do restante do 4xx

- [x] 1.1 Em `CommandDecisaoAutorizacaoClient.expirar()`, adicionar catch de
      `HttpClientErrorException.Conflict` **antes** do catch genérico de
      `HttpClientErrorException`, lançando `ExpiracaoRetryavelException` com mensagem
      identificando o conflito (sem body).
- [x] 1.2 Confirmar que o catch genérico de `HttpClientErrorException` (demais 4xx) continua
      inalterado (log info, sem lançar).
- [x] 1.3 Teste em `CommandDecisaoAutorizacaoClientTest`: `RestClient` mockado devolvendo 409
      → `expirar()` deve lançar `ExpiracaoRetryavelException` (não engolir).
- [x] 1.4 Teste em `CommandDecisaoAutorizacaoClientTest`: confirmar que 422 continua sem
      lançar (regressão do comportamento existente — já coberto por
      `status422NaoLancaExcecao`, verificado sem alterações necessárias).

## 2. Reivindicador — teto de tentativas via contador nativo do PEL

- [x] 2.1 Em `PendenciasSchedulerReivindicador.reivindicarPendenciasOciosas()`, ao filtrar
      `idsOciosos`, também consultar `PendingMessage.getTotalDeliveryCount()` para cada
      pendência.
- [x] 2.2 Separar as pendências ociosas em duas listas: as que atingiram o teto (5 entregas)
      e as que ainda não atingiram.
- [x] 2.3 Para as que atingiram o teto: confirmar (XACK) diretamente, sem
      `processarEConfirmarSeConcluido`, e registrar `log.error` com `streamId` e
      `idAutorizacao` (nunca o corpo do evento). (Usa `XCLAIM` internamente só para obter o
      `id_autorizacao` a logar antes do ACK — não reaciona o command.)
- [x] 2.4 Para as demais: manter o fluxo atual (`XCLAIM` + `processarEConfirmarSeConcluido`).
- [x] 2.5 Extrair o teto de tentativas como constante nomeada (`MAX_TENTATIVAS_EXPIRACAO = 5`),
      sem propriedade externa nova.
- [x] 2.6 Teste em `PendenciasSchedulerReivindicadorTest`: pendência com
      `totalDeliveryCount == 5` (ociosa) não aciona `processarEConfirmarSeConcluido` e é
      confirmada diretamente (`acknowledge`).
- [x] 2.7 Teste em `PendenciasSchedulerReivindicadorTest`: pendência com
      `totalDeliveryCount < 5` continua sendo reivindicada e reprocessada normalmente
      (regressão do comportamento existente).

## 3. Verificação

- [x] 3.1 `mvn test` em `apps/temporiza-autorizacao` passa (17 classes, 0 falhas/erros).
- [x] 3.2 `mvn clean compile` sem erros.
- [x] 3.3 Revisar `apps/temporiza-autorizacao/CLAUDE.md` (seção "Contrato de conclusão com o
      command") e `AGENTS.md` espelho: atualizar a tabela para refletir 409 como retryable e
      documentar o teto de 5 tentativas — mantendo os dois arquivos idênticos (`diff` confirma
      identidade).
