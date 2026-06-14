## 1. arj-contratocommand — Enum TipoJornadaAutorizacao

- [x] 1.1 Adicionar método `obterJornadaAutorizacaoEnumPorNome(String nome)` ao enum `TipoJornadaAutorizacao` com busca case-insensitive e lançamento de `BusinessException` para valor inválido
- [x] 1.2 Corrigir o método existente `obterJornadaAutorizacaoEnumPorIdJornada()` para lançar `BusinessException` em vez de `IllegalArgumentException`

## 2. arj-contratocommand — Record CriarAutorizacaoRequest

- [x] 2.1 Adicionar campo `TipoJornadaAutorizacao tipoJornada` ao record `CriarAutorizacaoRequest` como último campo do construtor canônico

## 3. arj-contratocommand — Controller

- [x] 3.1 Adicionar `@RequestHeader String tipoJornada` ao método `insert()` do `AutorizacaoController`
- [x] 3.2 No método `insert()`, converter o header para enum via `TipoJornadaAutorizacao.obterJornadaAutorizacaoEnumPorNome(tipoJornada)` e recriar o record `CriarAutorizacaoRequest` com o enum antes de passar ao orquestrador

## 4. arj-contratocommand — Mappers (motivoStatus)

- [x] 4.1 No `@AfterMapping` do `PixAutoMapper`, após chamar `autorizacao.inicializaCriacao(autorizacao)`, setar `autorizacao.setMotivoStatus(MotivoStatusAutorizacao.obterMotivoStatusEnumPorIdMotivo(request.tipoJornada().getCodigoJornada()).name())`
- [x] 4.2 Aplicar a mesma mudança no `@AfterMapping` do `DdaAutoMapper`

## 5. arj-contratocommand — Testes

- [x] 5.1 Atualizar `TestFixtures.criarRequest()` para incluir o parâmetro `TipoJornadaAutorizacao tipoJornada` e passá-lo ao construtor do record
- [x] 5.2 Atualizar `TestFixtures.criarRequestPix()` e `criarRequestDda()` para passar uma jornada padrão (ex: `SPI_J1`)
- [x] 5.3 Atualizar `AutorizacaoControllerTest.insertRetornaCreated()` para passar o header `tipoJornada` ao `controller.insert()` e ajustar o mock do orquestrador conforme nova assinatura
- [x] 5.4 Atualizar `PixAutoMapperTest` para incluir `tipoJornada` no request de fixture e verificar que `motivoStatus` da entidade gerada é o nome do enum correto
- [x] 5.5 Atualizar `DdaAutoMapperTest` da mesma forma que o item 5.4
- [x] 5.6 Revisar e ajustar `CriarPixAutoUseCaseTest` e `CriarDdaAutoUseCaseTest` caso montem `CriarAutorizacaoRequest` diretamente
- [x] 5.7 Executar `mvn test` no módulo `arj-contratocommand` e garantir que todos os testes passam

## 6. arj-contratoquery — DTOs de resposta

- [x] 6.1 Adicionar campo `String motivoStatus` em `AutorizacaoDetalheResponseDto` e popular via `autorizacao.getMotivoStatus()` no método `from()`
- [x] 6.2 Adicionar campo `String motivoStatus` em `AutorizacaoResumidaResponseDto` e popular via `autorizacao.getMotivoStatus()` no método `from()`

## 7. arj-contratoquery — Testes

- [x] 7.1 Atualizar `AutorizacaoDetalheResponseDtoTest` para verificar que `motivoStatus` é corretamente mapeado no `from()`
- [x] 7.2 Atualizar `AutorizacaoResumidaResponseDtoTest` para verificar que `motivoStatus` é corretamente mapeado no `from()`
- [x] 7.3 Executar `mvn test` no módulo `arj-contratoquery` e garantir que todos os testes passam
