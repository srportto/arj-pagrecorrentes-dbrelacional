# Tasks: contratacao-context

## 1. Contexto da contratação

- [ ] 1.1 Criar o record `ContratacaoContext` em `application/contratacao/` com componentes `tipoJornada` (`TipoJornadaAutorizacao`) e `dados` (`CriarAutorizacaoRequest`), fábrica estática `doRequest(tipoJornada, dados)` e javadoc espelhando o estilo do `CancelamentoContext` (sem wither — não há enriquecimento pré-validação na contratação)

## 2. Retipagem do framework de validação da contratação

- [ ] 2.1 Alterar `ContratacaoRule` para `extends Rule<ContratacaoContext>`
- [ ] 2.2 Alterar `ContratacaoValidator` para `implements Validator<ContratacaoRule, ContratacaoContext>`
- [ ] 2.3 Migrar `ProdutoSuportado` para `aceita(ContratacaoContext)`/`validar(ContratacaoContext)` com acesso via `contexto.dados()`, preservando `@Order(Ordered.HIGHEST_PRECEDENCE)`, a resolução case-insensitive do produto e a mensagem atual da `BusinessException`
- [ ] 2.4 Migrar `DataFimVigenciaInvalida` para a assinatura de contexto, sem alterar a regra
- [ ] 2.5 Migrar `ValorLimiteContrato` para a assinatura de contexto, sem alterar limites nem mensagens
- [ ] 2.6 Migrar `MetadadoRule` para a assinatura de contexto, sem alterar a regra

## 3. Use case, mapper, controller e request

- [ ] 3.1 Alterar `CriarAutorizacaoUseCase.execute` para receber `ContratacaoContext`, validar com o contexto e chamar o mapper desembrulhando: `mapper.toDomain(context.dados(), context.tipoJornada())` (logs passam a ler de `context.dados()`)
- [ ] 3.2 Alterar `AutorizacaoMapper` para `toDomain(CriarAutorizacaoRequest dados, TipoJornadaAutorizacao tipoJornada)`: qualificar todos os `@Mapping(source = "dados.*")` e ajustar o `@AfterMapping` para receber os dois parâmetros de origem, trocando `request.tipoJornada().getCodigoJornada()` por `tipoJornada.getCodigoJornada()` (mapper não importa `ContratacaoContext`)
- [ ] 3.3 Remover o componente `tipoJornada` de `CriarAutorizacaoRequest` (record fica com os 15 campos do body)
- [ ] 3.4 Simplificar `AutorizacaoController.insert`: resolver o enum do header, montar `ContratacaoContext.doRequest(jornada, request)` e chamar o use case — eliminar a reconstrução de 16 argumentos
- [ ] 3.5 Compilar o módulo (`mvn clean compile` em `aplicacoes/arj-contratocommand`) e corrigir qualquer erro residual da retipagem

## 4. Testes

- [ ] 4.1 Atualizar `TestFixtures`: `criarRequest(...)` perde o parâmetro `tipoJornada`; adicionar `criarContext(...)` (e variantes `criarContextPix()`/`criarContextDda()`) ao lado do `cancelarContext(...)` existente
- [ ] 4.2 Migrar os testes das rules de contratação (`ProdutoSuportadoTest`, `DataFimVigenciaInvalidaTest` e demais) para montar `ContratacaoContext`
- [ ] 4.3 Migrar `CriarAutorizacaoUseCaseTest` para `execute(context)` e ajustar stubs/verificações do mapper para a nova assinatura
- [ ] 4.4 Ajustar `AutorizacaoMapperTest` para `toDomain(dados, tipoJornada)`, cobrindo a derivação do `motivoStatus` a partir do parâmetro de jornada
- [ ] 4.5 Ajustar `AutorizacaoControllerTest` (o body JSON não muda; conferir cenário de header `tipoJornada` obrigatório/inválido intacto)
- [ ] 4.6 Rodar `mvn test` no módulo `arj-contratocommand` e garantir suíte verde

## 5. Documentação

- [ ] 5.1 Atualizar `aplicacoes/arj-contratocommand/CLAUDE.md`: diagrama do fluxo POST (controller monta `ContratacaoContext`), seção do framework de validação (`ContratacaoRule → extends Rule<ContratacaoContext>`), instruções de "adicionar regra" (`aceita(contexto)`), convenções (contexto imutável em ambas as features; request só com dados do body) e lista de componentes de `application/contratacao`
- [ ] 5.2 Replicar as mesmas edições em `aplicacoes/arj-contratocommand/AGENTS.md` e verificar com diff que os dois arquivos permanecem idênticos
- [ ] 5.3 Atualizar `aplicacoes/arj-contratocommand/README.md` onde descreve o fluxo de criação/`CriarAutorizacaoRequest`

## 6. Verificação final

- [ ] 6.1 Conferir os cenários do delta spec `coesao-contratocommand`: request de criação sem campo de header, controller sem reconstrução do record, mapper sem importar contextos de feature, árvore de pacotes com contexto em ambas as features
- [ ] 6.2 Smoke manual opcional: `POST /api/autorizacoes` com header `tipoJornada: SPI_J1` → 201 com `motivoStatus = RECEPCAO_SPI_J1`; header inválido → 422; header ausente → 400
