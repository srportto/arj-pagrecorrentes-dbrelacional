# Tasks: melhoria-coesao-contratocommand

## 1. Rule ProdutoSuportado (estrutura antiga ainda de pé)

- [x] 1.1 Confirmar que o `validar()` default de `shared/validationsetup/Validator` itera as rules na ordem da lista injetada pelo Spring (senão, ajustar a estratégia de ordenação antes de prosseguir)
- [x] 1.2 Criar `ProdutoSuportado implements ContratacaoRule` (no pacote atual das rules de contratação): resolve `TipoProduto` a partir de `request.tipoProduto()` case-insensitive e lança `BusinessException` com a mesma mensagem do orquestrador ("Produto nao suportado ou invalido (tipoProduto: ...)"); anotar com `@Order(Ordered.HIGHEST_PRECEDENCE)` para executar antes das demais rules
- [x] 1.3 Testes unitários da rule: produto válido em caixa variada (`pix_auto`, `PIX_AUTO`, `DdA_aUtO`), produto desconhecido, produto nulo
- [x] 1.4 `mvn test` verde no módulo `arj-contratocommand`

## 2. Controller chama use cases direto; deletar camada de strategy

- [x] 2.1 Registrar (em teste ou nota) o comportamento HTTP atual de header `tipoProduto` inválido/ausente no `PATCH /cancelar`, para preservá-lo
- [x] 2.2 Alterar `AutorizacaoController` para injetar e chamar `CriarAutorizacaoUseCase` e `CancelarAutorizacaoUseCase` diretamente (assinaturas dos endpoints inalteradas)
- [x] 2.3 Migrar os cenários de teste de "produto não suportado" dos testes de orquestrador para testes da rule/use case, incluindo o cenário "ProdutoSuportado executa antes das demais rules"
- [x] 2.4 Deletar `ContratacaoOrquestradorService`, `CancelamentoOrquestradorService`, `PixAutoService`, `DdaAutoService`, `ContratacaoService`, `CancelamentoService` e seus testes (`application/services/**` e `application/enabledproduct/**` somem)
- [x] 2.5 `mvn test` verde

## 3. Reorganização por feature e domínio puro

- [x] 3.1 Criar `application/contratacao` e mover para lá `CriarAutorizacaoUseCase`, `ContratacaoValidator`, `ContratacaoRule` e `rules/` (incluindo `ProdutoSuportado`)
- [x] 3.2 Criar `application/cancelamento` e mover para lá `CancelarAutorizacaoUseCase`, `CancelamentoContext`, `CancelamentoValidator`, `CancelamentoRule` e `rules/TipoProdutoCancelamento`
- [x] 3.3 Manter `AutorizacaoRepository` e `AutorizacaoMapper` em `application/autorizacao`; eliminar os pacotes `application/autorizacao/usecases` e `domain/services` (agora vazios)
- [x] 3.4 Atualizar imports e mover os testes para os pacotes espelho da nova estrutura
- [x] 3.5 Verificar pureza do domínio: nenhum arquivo sob `domain/` importa `entrypoint`/`application` nem usa anotações Spring (grep por `import ...entrypoint`, `import ...application`, `org.springframework` em `domain/`)
- [x] 3.6 `mvn test` verde

## 4. Documentação e verificação final

- [x] 4.1 Atualizar `CLAUDE.md` e `AGENTS.md` do `arj-contratocommand` (mantê-los espelhados): novo fluxo `Controller → UseCase → Validator/Rules`, remoção das seções de orquestrador/strategy, "adicionar produto novo" passa a ser via enum + rules (`aceita()`)
- [x] 4.2 `mvn clean package` verde; smoke test dos contratos REST com os payloads de `docs/post-autorizacoes.txt` (criação 201, cancelamento 200, produto desconhecido 422 com mensagem preservada) se houver ambiente com PostgreSQL disponível
