## 1. Setup de medição (ambas as apps)

- [x] 1.1 Adicionar `lombok.config` na raiz de `arj-contratoquery` e `arj-contratocommand` com `lombok.addLombokGeneratedAnnotation = true`
- [x] 1.2 Adicionar `jacoco-maven-plugin` (0.8.15, com goals `prepare-agent` e `report`) ao `pom.xml` de ambas as apps
- [x] 1.3 Confirmado: JaCoCo 0.8.15 instrumenta o bytecode Java 25 e gera `target/site/jacoco/index.html` (contratoquery rodou verde com agent)
- [x] 1.4 Baseline contratoquery: 41% linhas (133/324, antes dos testes novos)

## 2. Testes do contratoquery

- [x] 2.1 Utilitários: `ReversibleUUIDv7`, `IdContaUUIDPartitionDistributor`, `AchaQtdeSemanas`, `ControleExpurgoAutorizacao`
- [x] 2.2 Enums: `StatusAutorizacao`, `TipoProduto`, `MotivoStatusAutorizacao`, `TipoConta`, `CanaisConhecidosEnum`
- [x] 2.3 Converter `TipoProdutoConverter`
- [x] 2.4 Entidade `Autorizacao.inicializaCriacao`
- [x] 2.5 DTOs `AutorizacaoResumidaResponseDto.from` e `AutorizacaoDetalheResponseDto.from`
- [x] 2.6 `ApiExceptionHandler` (422/500/404/validação)
- [x] 2.7 `AutorizacaoController` (delegação + status HTTP) — contratoquery em 93,1%

## 3. Testes do contratocommand — domínio e shared

- [x] 3.1 Utilitários ainda sem teste: `ReversibleUUIDv7`, `IdContaUUIDPartitionDistributor`, `AchaQtdeSemanas`
- [x] 3.2 Enums e converter: `StatusAutorizacao`, `TipoProduto`, `MotivoStatusAutorizacao`, `TipoConta`, `CanaisConhecidosEnum`, `TipoProdutoConverter`
- [x] 3.3 Entidade `Autorizacao.inicializaCriacao`
- [x] 3.4 `shared/validationsetup` (`Validator.validar` default que itera regras) e `ApiExceptionHandler`
- [x] 3.5 DTO `AutorizacaoCompletaResponseDto` (mapeamento/builder com lógica, se houver)

## 4. Testes do contratocommand — contratação

- [x] 4.1 `ContratacaoOrquestradorService` (seleciona primeiro suportado; `BusinessException` quando nenhum)
- [x] 4.2 `ContratacaoValidator` (itera regras; caminho válido e violação)
- [x] 4.3 Regras: `DataFimVigenciaInvalida`, `ValorLimiteContrato`, `MetadadoRule` (aceita + validar; válido e violação)
- [x] 4.4 `CriarPixAutoUseCase` e `CriarDdaAutoUseCase` (mock de validator/mapper/repository; verifica save)
- [x] 4.5 `PixAutoMapper` e `DdaAutoMapper` (instanciar impl gerado; mapeamento + `@AfterMapping`)

## 5. Testes do contratocommand — cancelamento

- [x] 5.1 `CancelamentoOrquestradorService` (seleção; `BusinessException` quando nenhum)
- [x] 5.2 `CancelamentoValidator` e regra `TipoProdutoCancelamento` (válido e violação)
- [x] 5.3 `CancelamentoService`, `CancelarPixAutoUseCase` e `CancelarDdaAutoUseCase`
- [x] 5.4 `DdaAutoService` (criar/cancelar conforme interfaces que implementa)

## 6. Gate e validação final

- [x] 6.1 Adicionar o goal `jacoco:check` (regra BUNDLE, LINE, mínimo 0.80, com exclusões `**/*Application.class` e `**/*Repository.class`) ao `pom.xml` de ambas as apps
- [x] 6.2 Rodar `mvn verify` em `arj-contratoquery`: testes verdes + cobertura ≥ 0.80 (alvo ≥ 0.90) nas classes não-excluídas
- [x] 6.3 Rodar `mvn verify` em `arj-contratocommand`: testes verdes + cobertura ≥ 0.80 (alvo ≥ 0.90) nas classes não-excluídas
- [x] 6.4 Registrar a cobertura final de cada app e ajustar o gate se necessário
