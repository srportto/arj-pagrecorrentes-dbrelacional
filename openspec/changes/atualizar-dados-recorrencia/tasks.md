## 1. Comando e porta de entrada

- [ ] 1.1 Criar `domain/port/in/AtualizarDadosRecorrenciaCommand` (record: `idAutorizacao`,
      `tipoProduto`, `valorLimite`, `dataFimVigencia`, `indicadorUsoLimiteConta`,
      `quantidadeDividasCiclo`, `codigoCanalAtualizacao`, `idPessoaAtualizacao`)
- [ ] 1.2 Criar `domain/port/in/AtualizarDadosRecorrenciaUseCase` (interface, método
      `execute(AtualizarDadosRecorrenciaCommand)` retornando `Autorizacao`)

## 2. Framework de regras de atualização

- [ ] 2.1 Criar `domain/service/atualizacao/AtualizacaoRule` (marker interface, extends
      `Rule<AtualizarDadosRecorrenciaCommand>`)
- [ ] 2.2 Criar `domain/service/atualizacao/AtualizacaoValidator` (implements
      `Validator<AtualizacaoRule, AtualizarDadosRecorrenciaCommand>`)
- [ ] 2.3 Criar rule `TipoProdutoAtualizacao` (`@Order` antes das demais) — compara
      `tipoProduto` do comando com o produto persistido, `BusinessException` se divergir
- [ ] 2.4 Criar rule `StatusPermiteAtualizacao` — `BusinessException` se status
      persistido != `ATIVA`
- [ ] 2.5 Criar rule `DataFimVigenciaInvalidaAtualizacao` — mesma checagem de
      `DataFimVigenciaInvalida` (não pode ser no passado), só quando `dataFimVigencia !=
      null` no comando
- [ ] 2.6 Criar rule `ValorLimiteAtualizacaoInvalido` — `BusinessException` se
      `valorLimite != null && valorLimite.signum() <= 0`

## 3. Caso de uso

- [ ] 3.1 Criar `application/usecase/AtualizarDadosRecorrenciaService` (`@Transactional`,
      implementa `AtualizarDadosRecorrenciaUseCase`)
- [ ] 3.2 Carregar autorização por UUID + partição extraída (mesmo padrão de
      `CancelarAutorizacaoService`/`DecidirAutorizacaoService`)
- [ ] 3.3 Rodar `AtualizacaoValidator.validar(command)`
- [ ] 3.4 Aplicar sobre o modelo carregado só os campos não-null do comando (método no
      domínio, ex. `Autorizacao.atualizarDadosRecorrencia(...)`, seguindo o padrão de
      `aprovar()`/`cancelar()` — atualiza também `dataHoraUltimaAtualizacao`)
- [ ] 3.5 Persistir via `AutorizacaoPersistenceMapper.aplicarEm(modelo,
      entidadeGerenciada)` — nunca `paraEntidade` seguido de `save` (armadilha nº 11)
- [ ] 3.6 Publicar `AutorizacaoPersistidaEvent` ao final do `execute()`, mesmo padrão dos
      outros três use cases

## 4. Camada web

- [ ] 4.1 Criar `infrastructure/web/contratosrest/AtualizarDadosRecorrenciaRequest`
      (record: `valorLimite`, `dataFimVigencia`, `indicadorUsoLimiteConta`,
      `quantidadeDividasCiclo` todos opcionais/nuláveis; `codigoCanalAtualizacao` e
      `idPessoaAtualizacao` com `@NotNull`; `@Min(1)` em `quantidadeDividasCiclo` sem
      `@NotNull`)
- [ ] 4.2 Adicionar método `atualizar` em `AutorizacaoController`: `@PatchMapping("/
      {idAutorizacao}/atualizar")`, header `tipoProduto` obrigatório, monta o comando e
      chama `AtualizarDadosRecorrenciaUseCase.execute`, retorna 200 com
      `AutorizacaoCompletaResponseDto.from(...)`

## 5. Testes

- [ ] 5.1 Testes unitários das 4 novas rules (`TipoProdutoAtualizacao`,
      `StatusPermiteAtualizacao`, `DataFimVigenciaInvalidaAtualizacao`,
      `ValorLimiteAtualizacaoInvalido`)
- [ ] 5.2 Teste unitário de `AtualizarDadosRecorrenciaService` — atualização parcial (só
      um campo), todos os campos, campo ausente não altera valor existente
- [ ] 5.3 Teste unitário/webmvc do endpoint: 200 (sucesso), 422 (status != ATIVA, produto
      divergente, data no passado, valorLimite <= 0, autorização inexistente), 409
      (concorrência)
- [ ] 5.4 Teste de que o evento publicado carrega `tipoEvento=ATIVACAO` (reaproveitado,
      sem valor novo no enum) e o payload reflete os campos atualizados
- [ ] 5.5 Teste de que campos não enviados no PATCH permanecem com o valor anterior após
      reload do banco (garante que `null`/ausente realmente não sobrescreve)

## 6. Documentação

- [ ] 6.1 Atualizar `apps/contratocommand/CLAUDE.md` **e** `AGENTS.md` (espelhos): tabela
      de endpoints reais, seção "Arquitetura" (novo pacote
      `domain/service/atualizacao/`), nota sobre `DataFimVigenciaInvalida` duplicada
      entre criação e atualização
- [ ] 6.2 Atualizar `docs/contrato-api-para-gateway.md` se este for o canal usado para
      publicar contratos de API para o gateway (conferir se ainda é o processo vigente)
- [ ] 6.3 Rodar checklist de espelhamento do `CLAUDE.md` do `contratocommand` — confirmar
      que `AutorizacaoJpaEntity`, `Autorizacao` (domínio) e `AutorizacaoEventoPayload` não
      precisam de campo novo (os 4 campos já existem em todos), e que
      `apps/contratoquery` não precisa de mudança (mesma tabela/colunas já lidas)
- [ ] 6.4 Atualizar o graphify (`graphify-out/`) ao final da implementação, conforme
      convenção do monorepo
