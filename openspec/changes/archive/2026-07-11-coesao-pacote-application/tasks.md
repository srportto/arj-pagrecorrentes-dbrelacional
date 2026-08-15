## 1. Mover AutorizacaoRepository e AutorizacaoMapper para a raiz de application/

- [x] 1.1 Mover `AutorizacaoRepository.java` de `application/autorizacao/` para `application/` (pacote `br.com.srportto.contratocommand.application`)
- [x] 1.2 Mover `AutorizacaoMapper.java` de `application/autorizacao/` para `application/` (pacote `br.com.srportto.contratocommand.application`)
- [x] 1.3 Atualizar imports de `AutorizacaoRepository`/`AutorizacaoMapper` em `CriarAutorizacaoUseCase` e `CancelarAutorizacaoUseCase`
- [x] 1.4 Mover `AutorizacaoMapperTest.java` de `application/autorizacao/` (test) para `application/` (test), atualizando o pacote
- [x] 1.5 Confirmar que o pacote `application/autorizacao` (main e test) não existe mais e que nenhum arquivo referencia `br.com.srportto.contratocommand.application.autorizacao`

## 2. Diferenciar @Service dos orquestradores

- [x] 2.1 Trocar `@Component` por `@Service` em `ContratacaoValidator`
- [x] 2.2 Trocar `@Component` por `@Service` em `CancelamentoValidator`
- [x] 2.3 Trocar `@Component` por `@Service` em `CriarAutorizacaoUseCase`
- [x] 2.4 Trocar `@Component` por `@Service` em `CancelarAutorizacaoUseCase`
- [x] 2.5 Confirmar que as 6 rules (`DataFimVigenciaInvalida`, `MetadadoRule`, `ValorLimiteContrato`, `ProdutoSuportado`, `TipoProdutoCancelamento`, `ProdutoSuportadoCancelamento`) permanecem `@Component` (sem alteração)

## 3. Atualizar documentação do módulo

- [x] 3.1 Atualizar `CLAUDE.md` (contratocommand) refletindo: `AutorizacaoRepository`/`AutorizacaoMapper` na raiz de `application/`; convenção `@Service` para Validators/UseCases e `@Component` para Rules
- [x] 3.2 Replicar a mesma atualização em `AGENTS.md` (contratocommand), mantendo os dois arquivos espelhados

## 4. Verificação final

- [x] 4.1 Rodar `mvn clean compile` em `aplicacoes/contratocommand` — sem erros de import
- [x] 4.2 Rodar `mvn test` em `aplicacoes/contratocommand` — suíte completa verde, sem mudança de comportamento
- [x] 4.3 Conferir que nenhum contrato REST mudou (endpoints, headers, códigos HTTP e mensagens intactos)
