## 1. Extrair serviço compartilhado de expurgo

- [x] 1.1 Criar `application/ExpurgoAutorizacaoService` (`@Service`) com o método
      `transferirParaExpurgo(Autorizacao autorizacao, LocalDate dataReferenciaExpurgo)`,
      movendo o corpo de `CancelarAutorizacaoUseCase.transferirParaNovaParticao` sem alterar
      o algoritmo (delete → flush → detach → ajusta partição do `@EmbeddedId` → save).
- [x] 1.2 Injetar `AutorizacaoRepository` e `EntityManager` no novo serviço (mesmas
      dependências hoje usadas por `CancelarAutorizacaoUseCase`).
- [x] 1.3 Remover `transferirParaNovaParticao` de `CancelarAutorizacaoUseCase` e substituir a
      chamada por `expurgoService.transferirParaExpurgo(autorizacao, dataCancelamento)`.

## 2. Testes do serviço compartilhado

- [x] 2.1 Mover/adaptar os casos de teste relevantes de
      `CancelarAutorizacaoUseCaseTest` (cenário de transferência de partição) para uma nova
      classe `ExpurgoAutorizacaoServiceTest`, cobrindo partição de destino diferente e igual
      à atual.
- [x] 2.2 Confirmar que `CancelarAutorizacaoUseCaseTest` continua passando após o refactor
      (mock/spy do novo serviço onde fizer sentido, sem duplicar a cobertura do algoritmo de
      transferência).

## 3. Expurgo na rejeição e expiração da jornada 1

- [x] 3.1 Em `DecidirAutorizacaoUseCase.execute`, após `aplicarDecisao` e
      `setDataHoraUltimaAtualizacao`, checar se o status resultante é `REJEITADA` e, nesse
      caso, chamar `expurgoService.transferirParaExpurgo(autorizacao,
      dataHoraUltimaAtualizacao.toLocalDate())` no lugar do `repository.save(autorizacao)`
      direto.
- [x] 3.2 Manter o `save()` direto (sem expurgo) para o caso `APROVAR` → `ATIVA`, já que não
      é estado terminal.
- [x] 3.3 Injetar `ExpurgoAutorizacaoService` em `DecidirAutorizacaoUseCase`.

## 4. Testes de decisão

- [x] 4.1 Adicionar cenários em `DecidirAutorizacaoUseCaseTest` cobrindo `REJEITAR` e
      `EXPIRAR`: autorização passa a residir na partição de expurgo calculada a partir de
      `dataHoraUltimaAtualizacao`, e não mais na partição derivada de `dataFimVigencia`.
- [x] 4.2 Adicionar cenário confirmando que `APROVAR` não aciona o serviço de expurgo (sem
      transferência de partição).
- [x] 4.3 Rodar `mvn test -Dtest=ControleExpurgoAutorizacaoTest` para garantir que a lógica
      pura de cálculo de partição (não tocada nesta mudança) continua correta.

## 5. Validação final

- [x] 5.1 `mvn clean compile` sem erros em `arj-contratocommand`.
- [x] 5.2 `mvn test` completo em `arj-contratocommand`.
- [x] 5.3 Revisar se `AutorizacaoEventoPayload`, o `.avsc` espelhado e o contrato do endpoint
      `/decisao` permanecem inalterados (nenhuma mudança de schema é esperada nesta
      entrega).
