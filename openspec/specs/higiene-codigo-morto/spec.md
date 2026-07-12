# Spec: higiene-codigo-morto

## Purpose

Define o padrão de higiene de código morto para as duas aplicações em `aplicacoes/` (`arj-contratocommand` e `arj-contratoquery`): classes e métodos do `src/main` devem ter uso real em código de produção, utilitários usados apenas por testes vivem em `src/test`, a app de leitura não pode conter lógica exclusiva do fluxo de escrita, e a documentação de módulo acompanha as remoções.

## Requirements

### Requirement: Código de produção não contém classes sem referência de produção
Toda classe em `src/main` das aplicações `aplicacoes/arj-contratocommand` e `aplicacoes/arj-contratoquery` SHALL ter ao menos uma referência a partir de outro código de `src/main` (de qualquer arquivo que não ela mesma) ou ser um ponto de entrada reconhecido (classe de aplicação Spring Boot, `@RestController`, `@Entity`/`@Embeddable`/`@Converter` registrados, configuração). Referências vindas exclusivamente de `src/test` MUST NOT contar como uso de produção.

#### Scenario: Classes fantasma do fluxo de escrita removidas da query
- **WHEN** o módulo `arj-contratoquery` é inspecionado após a limpeza
- **THEN** as classes `ContratoBase`, `TipoJornadaAutorizacao`, `CanaisConhecidosEnum`, `TipoConta`, `MotivoStatusAutorizacao`, `AchaQtdeSemanas`, `ControleExpurgoAutorizacao` e `IdContaUUIDPartitionDistributor` não existem em `src/main`

#### Scenario: Enums sem uso removidos da command
- **WHEN** o módulo `arj-contratocommand` é inspecionado após a limpeza
- **THEN** as classes `CanaisConhecidosEnum` e `TipoConta` não existem em `src/main`

#### Scenario: Compilação e testes permanecem verdes
- **WHEN** `mvn test` é executado em cada módulo após as remoções
- **THEN** a compilação conclui sem erros e todos os testes passam

### Requirement: Métodos sem chamador de produção são removidos
Métodos públicos de classes vivas em `src/main` SHALL ter ao menos um chamador em código de produção, ressalvados métodos que integram um par coeso cuja metade viva depende da outra para ser testável (ex.: `generate`/`extract` de `ReversibleUUIDv7`).

#### Scenario: Método write-side removido da entidade da query
- **WHEN** a entidade `Autorizacao` de `arj-contratoquery` é inspecionada
- **THEN** o método `inicializaCriacao` não existe

#### Scenario: Métodos de enum mortos removidos
- **WHEN** os enums das duas aplicações são inspecionados
- **THEN** `StatusAutorizacao.isStatusFinalizador` não existe em nenhum dos dois módulos e `TipoProduto.obterTipoProdutoEnumPorNome` não existe em `arj-contratoquery` (permanecendo em `arj-contratocommand`, onde é usado)

#### Scenario: Par coeso preservado
- **WHEN** a classe `ReversibleUUIDv7` de `arj-contratoquery` é inspecionada
- **THEN** ela mantém `generate()` e `extract()`, pois `extract()` tem uso de produção e seus testes dependem de `generate()` para construir UUIDs v7 válidos

### Requirement: Utilitários usados apenas por testes vivem em src/test
Uma classe utilitária referenciada exclusivamente por código de teste SHALL residir em `src/test`, não em `src/main`, para não ser empacotada no artefato de produção.

#### Scenario: AchaQtdeSemanas da command movida para o source set de teste
- **WHEN** o módulo `arj-contratocommand` é inspecionado após a limpeza
- **THEN** `AchaQtdeSemanas` existe em `src/test/java/.../domain/utilities/` e não existe em `src/main`, e o helper `GeraDatasPorParticao` e o teste `AchaQtdeSemanasTest` continuam compilando e passando

### Requirement: App de leitura não contém lógica exclusiva de escrita
O módulo `arj-contratoquery` (somente leitura) MUST NOT conter lógica exclusiva do fluxo de escrita/contratação: geração de id/partição para inserção, cálculo de partição de expurgo de escrita ou inicialização de entidade para criação.

#### Scenario: Sem lógica de criação na query
- **WHEN** o `src/main` de `arj-contratoquery` é pesquisado por lógica de criação
- **THEN** não há chamadas a `ReversibleUUIDv7.generate` nem cálculo de partição de escrita/expurgo em código de produção

### Requirement: Testes de código morto são removidos junto com o código
Ao remover uma classe ou método sem uso de produção, os testes que exercitavam exclusivamente esse código SHALL ser removidos na mesma mudança; testes de classes parcialmente afetadas SHALL perder apenas os casos dos membros removidos.

#### Scenario: Testes órfãos removidos na query
- **WHEN** o `src/test` de `arj-contratoquery` é inspecionado após a limpeza
- **THEN** os testes `AutorizacaoTest`, `MotivoStatusAutorizacaoTest`, `CanaisConhecidosEnumTest`, `TipoContaTest`, `AchaQtdeSemanasTest`, `ControleExpurgoAutorizacaoTest` e `IdContaUUIDPartitionDistributorTest` não existem

#### Scenario: Testes de membros vivos preservados
- **WHEN** `TipoProdutoTest` e `StatusAutorizacaoTest` de `arj-contratoquery` são executados
- **THEN** os casos dos métodos vivos (`obterTipoProdutoEnumPorId`, `obterStatusEnumPorIdStatus`, `getStatusAutorizacao`, `getTipoProduto`) continuam existindo e passando

### Requirement: Documentação de módulo reflete o código após a limpeza
Os arquivos `CLAUDE.md` e `AGENTS.md` de cada módulo SHALL ser atualizados na mesma mudança que remove classes ou testes por eles citados, e MUST permanecer idênticos entre si.

#### Scenario: Docs sem menção a classes removidas
- **WHEN** `CLAUDE.md` e `AGENTS.md` dos dois módulos são pesquisados pelos nomes das classes removidas
- **THEN** não há menção a elas como código existente, e `CLAUDE.md` é idêntico a `AGENTS.md` em cada módulo
