## Why

A maior parte das duas aplicações está sem testes: `contratoquery` tem 2 classes de teste para 29 classes de produção, e `contratocommand` tem 2 para 48. Lógica crítica — orquestradores (Strategy), validators, regras de negócio, use cases, mappers, utilitários de particionamento/UUID, enums e DTOs com mapeamento — não tem rede de segurança. Além disso, a cobertura sequer é medida: nenhum dos `pom.xml` tem JaCoCo, apesar de a documentação citar `jacoco:report`. Sem medição nem gate, regressões passam despercebidas.

## What Changes

- **Medição de cobertura** com JaCoCo configurado em `contratocommand` e `contratoquery` (relatório em `target/site/jacoco`).
- **Gate de cobertura** no build via `jacoco:check`: o build SHALL falhar abaixo de um mínimo de cobertura de linhas nas classes não-excluídas.
- **Exclusões de cobertura** definidas para código gerado/não-unit-testável: classes `*Application` (`main`), interfaces `*Repository` (JPA), e entidades/DTOs puramente Lombok sem lógica própria.
- **Testes unitários novos** (JUnit 5 + Mockito) cobrindo as classes com lógica de ambas as apps, mirando **~90%+** de cobertura de linhas nas classes não-excluídas:
  - `contratoquery`: utilitários (`ReversibleUUIDv7`, `IdContaUUIDPartitionDistributor`, `AchaQtdeSemanas`, `ControleExpurgoAutorizacao`), enums (`StatusAutorizacao`, `TipoProduto`, `MotivoStatusAutorizacao`, `TipoConta`, `CanaisConhecidosEnum`), converter (`TipoProdutoConverter`), DTOs com `from()` (`AutorizacaoResumidaResponseDto`, `AutorizacaoDetalheResponseDto`), `ApiExceptionHandler`, entidade `Autorizacao.inicializaCriacao`.
  - `contratocommand`: orquestradores (`ContratacaoOrquestradorService`, `CancelamentoOrquestradorService`), validators (`ContratacaoValidator`, `CancelamentoValidator`), regras (`DataFimVigenciaInvalida`, `ValorLimiteContrato`, `MetadadoRule`, `TipoProdutoCancelamento`), use cases (`Criar/Cancelar` Pix/Dda), services (`DdaAutoService`, `CancelamentoService`), mappers (`PixAutoMapper`, `DdaAutoMapper`), `ApiExceptionHandler`, enums/converter/utilitários ainda sem teste.

## Capabilities

### New Capabilities
- `cobertura-testes-unitarios`: configuração de medição de cobertura (JaCoCo), gate mínimo no build, exclusões padronizadas e exigência de testes unitários para as classes com lógica das duas aplicações.

### Modified Capabilities
<!-- Nenhuma. Não há mudança de comportamento de produção; apenas testes e build. -->

## Impact

- **contratocommand** e **contratoquery** — `pom.xml`: adiciona o plugin `jacoco-maven-plugin` (goals `prepare-agent`, `report`, `check`) com regras de exclusão e limite mínimo.
- **Testes novos** em `src/test/java/...` de ambas as apps (sem alterar código de produção).
- **Build** — `mvn verify`/`mvn test` passa a gerar relatório de cobertura e a falhar se abaixo do gate.
- **Sem mudança de comportamento de produção**, schema ou API. Testes são unitários (Mockito), sem necessidade de PostgreSQL; os `@SpringBootTest` de contexto permanecem `@Disabled` (exigem banco) e ficam fora do escopo de unidade/gate.
