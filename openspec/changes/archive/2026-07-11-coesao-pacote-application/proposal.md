## Why

O pacote `application/` do `arj-contratocommand` tem hoje três subpacotes irmãos — `autorizacao/`, `contratacao/`, `cancelamento/` — mas apenas dois deles são verticais de feature (contratar, cancelar). `autorizacao/` contém só `AutorizacaoRepository` e `AutorizacaoMapper`, infraestrutura compartilhada pela entidade `Autorizacao`, não uma operação de negócio; ficar como pacote irmão sugere (erroneamente) que é "mais uma feature" e ainda colide de nome com `Autorizacao` (entidade), `AutorizacaoController` e `AutorizacaoCompletaResponseDto`. Além disso, todos os beans de `application/` usam `@Component` uniformemente, sem distinguir os orquestradores de regra de negócio (Validators, UseCases) das estratégias individuais (Rules), perdendo a semântica que o próprio Spring oferece para isso.

## What Changes

- `AutorizacaoRepository` e `AutorizacaoMapper` saem de `application/autorizacao/` e passam a residir na raiz de `application/`, ao lado de `contratacao/` e `cancelamento/`. O pacote `application/autorizacao/` deixa de existir. O teste `AutorizacaoMapperTest` acompanha o mesmo movimento.
- `ContratacaoValidator`, `CancelamentoValidator`, `CriarAutorizacaoUseCase` e `CancelarAutorizacaoUseCase` trocam `@Component` por `@Service`, marcando-os como os orquestradores de regra de negócio de cada operação.
- As 6 rules (`DataFimVigenciaInvalida`, `MetadadoRule`, `ValorLimiteContrato`, `ProdutoSuportado`, `TipoProdutoCancelamento`, `ProdutoSuportadoCancelamento`) permanecem `@Component`, reforçando que são estratégias individuais injetadas nas listas `List<ContratacaoRule>`/`List<CancelamentoRule>`, não "o" serviço da operação.
- `AutorizacaoRepository` (`@Repository`), `AutorizacaoMapper` (`@Mapper(componentModel = "spring")`), `AutorizacaoController` (`@RestController`), `ApiExceptionHandler` (`@ControllerAdvice`) e `TipoProdutoConverter` (`@Converter`) já estão no estereótipo correto e não mudam.
- Nenhuma mudança de comportamento observável: mesma injeção de dependências, mesmos endpoints, mesmos testes (ajustados apenas nos imports/localização).

## Capabilities

### New Capabilities
(nenhuma — este é um refactor de organização interna, não introduz comportamento novo)

### Modified Capabilities
- `coesao-contratocommand`: o requirement de organização por feature passa a exigir que componentes compartilhados entre features (não específicos de uma operação) residam na raiz de `application/`, e que os componentes marcados `@Service` sejam exatamente os orquestradores de regra de negócio (Validators e UseCases), com as Rules permanecendo `@Component`.

## Impact

- **Código**: 6 arquivos movidos/renomeados de pacote (`AutorizacaoRepository`, `AutorizacaoMapper`, `AutorizacaoMapperTest` + imports em `CriarAutorizacaoUseCase`, `CancelarAutorizacaoUseCase` e seus testes); 4 arquivos com troca de anotação (`@Component` → `@Service`).
- **Testes**: nenhum teste muda de comportamento; apenas pacote/imports são atualizados onde necessário. `mvn test` deve permanecer 100% verde.
- **APIs**: nenhuma — endpoints, contratos REST e mensagens de erro são inalterados.
- **Documentação**: `CLAUDE.md`/`AGENTS.md` do módulo `arj-contratocommand` descrevem a estrutura de pacotes e a convenção de anotações atuais; precisam ser atualizados para refletir a nova organização.
- **Escopo**: somente `arj-contratocommand`; nenhum outro módulo é afetado.
