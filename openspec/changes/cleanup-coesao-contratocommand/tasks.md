## 0. Fase 0 — Bugs de correção (sem mudar contrato REST)

- [x] 0.1 Mover `@Transactional` para o método público `execute()` em `CancelarPixAutoUseCase` e `CancelarDdaAutoUseCase`; remover a anotação do método privado `transferirParaNovaParticao`.
- [x] 0.2 Adicionar teste de rollback: simular falha na reinserção e verificar que a autorização original permanece (cenário "Reinserção falha após o delete").
- [x] 0.3 Substituir `org.springframework.context.ApplicationContextException` por `ApplicationException` em `obterAutorizacaoPorIdEParticao` (ambos os use cases de cancelamento).
- [x] 0.4 Verificar no `ApiExceptionHandler`/`ApiExceptionHandlerTest` que `ApplicationException`→500 e `BusinessException`→422; ajustar/expandir testes se necessário. _(já coberto: handler mapeia ambos; `ApiExceptionHandlerTest` valida 422/500)._
- [x] 0.5 Corrigir `String.format("...%i...")` em `MotivoStatusAutorizacao.obterMotivoStatusEnumPorIdMotivo` para `%d`.
- [x] 0.6 Corrigir o nome de coluna com espaço em `Autorizacao` (`indicador_tipo_mensageria ` → `indicador_tipo_mensageria`), validando contra o DDL real antes. _(schema vem da entidade via `ddl-auto: update`; sem DDL externo conflitante)._
- [x] 0.7 Rodar `mvn clean test` e confirmar verde antes de prosseguir. _(98 testes, 0 falhas, 1 skipped — BUILD SUCCESS)._

## 1. Fase 1 — Normalizar DTOs e contrato (eixo C/D)

- [ ] 1.1 Converter `CancelarAutorizacaoRequestDto` em record imutável contendo apenas campos de corpo (`codigoCanalCancelamento`, `idPessoaCancelamento`, `motivoCancelamento`); renomear para `CancelarAutorizacaoRequest` conforme convenção de sufixo.
- [ ] 1.2 Introduzir contexto/comando interno de cancelamento (ex.: record `CancelamentoContext`) carregando `idAutorizacao` (path), `tipoProdutoHeader` e `tipoProdutoDaAutorizacao` (lido do banco) como parâmetros explícitos.
- [ ] 1.3 Ajustar `AutorizacaoController.cancelar` para montar o contexto em vez de mutar o DTO via setters.
- [ ] 1.4 Ajustar `CancelamentoValidator`/`Validator` e a `Rule` `TipoProdutoCancelamento` para receber os dois produtos como parâmetros do contexto, sem ler campos injetados no request.
- [ ] 1.5 Padronizar sufixos de DTO (`...Request` / `...Response`) e remover o sufixo divergente; alinhar `CriarAutorizacaoRequest` se necessário.
- [ ] 1.6 Converter `tipoProduto` cru: trocar `TipoProduto.valueOf(...)` no mapper por `TipoProduto.obterTipoProdutoEnumPorNome(...)` (lança `BusinessException`/422).
- [ ] 1.7 Refatorar `AutorizacaoCompletaResponseDto` para record; remover o `ObjectMapper` estático interno e o método `from()` manual, movendo o mapeamento entidade→response para MapStruct.
- [ ] 1.8 Parar de expor a entidade de domínio `Cancelamento` no response: mapear para um tipo de resposta dedicado (ou campos planos).
- [ ] 1.9 Atualizar testes afetados (`AutorizacaoControllerTest`, `TipoProdutoCancelamentoTest`, `CancelamentoValidatorTest`, `AutorizacaoCompletaResponseDtoTest`) para o novo contrato imutável.
- [ ] 1.10 Rodar `mvn clean test` e confirmar verde.

## 2. Fase 2 — Eliminar duplicação Pix/DDA (decisão B1)

- [ ] 2.1 Verificar que `arj-contratoquery` não importa classes de `enabledproduct` do contratocommand antes de fundir (grep cross-app); registrar achado.
- [ ] 2.2 Criar `AutorizacaoMapper` compartilhado (unifica `PixAutoMapper` + `DdaAutoMapper` idênticos) e remover os dois mappers por produto.
- [ ] 2.3 Criar `AutorizacaoRepository` compartilhado (unifica `PixAutoRepository` + `DdaAutoRepository`) e atualizar referências.
- [ ] 2.4 Criar `CriarAutorizacaoUseCase` e `CancelarAutorizacaoUseCase` compartilhados (unificam os use cases por produto, parametrizando log por produto se necessário).
- [ ] 2.5 Tornar `PixAutoService`/`DdaAutoService` finos: declaram o `TipoProduto` suportado e delegam aos use cases compartilhados; manter implementação de `ContratacaoService`/`CancelamentoService`.
- [ ] 2.6 Avaliar transformar os métodos `default`-que-lançam-`UnsupportedOperationException` das interfaces Strategy em métodos abstratos (remover o default leaky), conforme requisito de strategy fino.
- [ ] 2.7 Consolidar os testes duplicados (`PixAutoMapperTest`+`DdaAutoMapperTest`, `Criar/Cancelar{Pix,Dda}UseCaseTest`, `DdaAutoServiceTest`+`PixAutoAutorizacaoServiceTest`) em testes compartilhados do fluxo + testes finos de strategy; garantir que cada cenário previamente coberto continue coberto.
- [ ] 2.8 Mover o teste `application/pixauto/PixAutoAutorizacaoServiceTest` para o pacote correto e/ou substituí-lo pelos testes consolidados.
- [ ] 2.9 Rodar `mvn clean test` e confirmar verde; comparar contagem/cobertura de cenários antes vs depois.

## 3. Fase 3 — Limpeza de domínio e dead code (eixo E + A3)

- [ ] 3.1 Refatorar `Autorizacao.inicializaCriacao(Autorizacao)` para `inicializaCriacao()` operando sobre `this`; atualizar chamadas no(s) mapper(s).
- [ ] 3.2 Introduzir conversão de `status` via `StatusAutorizacao` + JPA `@Converter` (análogo a `TipoProdutoConverter`) ou centralizar as constantes; eliminar os literais mágicos `1`/`5`.
- [ ] 3.3 Resolver a Open Question do valor "ativa" (`1` vs `4`) com o time e aplicar a decisão de forma consistente em código e testes.
- [ ] 3.4 Tornar `motivoStatus` consistente (sempre derivado de `MotivoStatusAutorizacao`), removendo o texto livre em `inicializaCriacao`.
- [ ] 3.5 Trocar `@JoinColumn` por `@Column` nos campos básicos de `IdAutorizacao` e `Cancelamento`.
- [ ] 3.6 Remover `domain/model/ContratoBase` (dead code) ou fazê-lo ser efetivamente usado por `Autorizacao`.
- [ ] 3.7 Corrigir comentários enganosos: tabela `autorizacoes` ("roles/perfis") e semântica de `frequenciaPagamento` (alinhar com `@Min(1)@Max(4)`).
- [ ] 3.8 (Opcional) Renomear o UUID interno de `IdAutorizacao.idAutorizacao` para nome não-ambíguo (ex.: `idAutos`/`uuid`) para evitar `getIdAutorizacao().getIdAutorizacao()`.
- [ ] 3.9 Rodar `mvn clean test` e confirmar verde.

## 4. Fechamento e verificação

- [ ] 4.1 `mvn clean package` sem erros; aplicação buildável e o JAR gerado.
- [ ] 4.2 Revisão final contra a spec `coesao-contratocommand`: cada requisito tem teste correspondente verde.
- [ ] 4.3 Atualizar `CLAUDE.md`/`AGENTS.md` e o doc de arquitetura para refletir o eixo produto unificado e os contratos normalizados.
- [ ] 4.4 Confirmar que os três contratos REST e o health-check permanecem inalterados (exceto `status` coerente com enum).
