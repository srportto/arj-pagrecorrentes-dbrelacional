# Spec: higiene-codigo-morto

## Purpose

Define o padrão de higiene de código morto no monorepo: classes e métodos do `src/main` devem ter uso real em código de produção, utilitários usados apenas por testes vivem em `src/test`, a app de leitura não pode conter lógica exclusiva do fluxo de escrita, e a documentação de módulo acompanha as remoções — requisitos escritos originalmente para `contratocommand` e `contratoquery`. Os requisitos sobre imports sem uso, parâmetros mortos e marcações `TODO` valem para as cinco aplicações de `apps/`.

## Requirements

### Requirement: Código de produção não contém classes sem referência de produção
Toda classe em `src/main` das aplicações `apps/contratocommand` e `apps/contratoquery` SHALL ter ao menos uma referência a partir de outro código de `src/main` (de qualquer arquivo que não ela mesma) ou ser um ponto de entrada reconhecido (classe de aplicação Spring Boot, `@RestController`, `@Entity`/`@Embeddable`/`@Converter` registrados, configuração). Referências vindas exclusivamente de `src/test` MUST NOT contar como uso de produção.

#### Scenario: Classes fantasma do fluxo de escrita removidas da query
- **WHEN** o módulo `contratoquery` é inspecionado após a limpeza
- **THEN** as classes `ContratoBase`, `TipoJornadaAutorizacao`, `CanaisConhecidosEnum`, `TipoConta`, `MotivoStatusAutorizacao`, `AchaQtdeSemanas`, `ControleExpurgoAutorizacao` e `IdContaUUIDPartitionDistributor` não existem em `src/main`

#### Scenario: Enums sem uso removidos da command
- **WHEN** o módulo `contratocommand` é inspecionado após a limpeza
- **THEN** as classes `CanaisConhecidosEnum` e `TipoConta` não existem em `src/main`

#### Scenario: Compilação e testes permanecem verdes
- **WHEN** `mvn test` é executado em cada módulo após as remoções
- **THEN** a compilação conclui sem erros e todos os testes passam

### Requirement: Métodos sem chamador de produção são removidos
Métodos públicos de classes vivas em `src/main` SHALL ter ao menos um chamador em código de produção, ressalvados métodos que integram um par coeso cuja metade viva depende da outra para ser testável (ex.: `generate`/`extract` de `ReversibleUUIDv7`).

#### Scenario: Método write-side removido da entidade da query
- **WHEN** a entidade `Autorizacao` de `contratoquery` é inspecionada
- **THEN** o método `inicializaCriacao` não existe

#### Scenario: Métodos de enum mortos removidos
- **WHEN** os enums das duas aplicações são inspecionados
- **THEN** `StatusAutorizacao.isStatusFinalizador` não existe em nenhum dos dois módulos e `TipoProduto.obterTipoProdutoEnumPorNome` não existe em `contratoquery` (permanecendo em `contratocommand`, onde é usado)

#### Scenario: Par coeso preservado
- **WHEN** a classe `ReversibleUUIDv7` de `contratoquery` é inspecionada
- **THEN** ela mantém `generate()` e `extract()`, pois `extract()` tem uso de produção e seus testes dependem de `generate()` para construir UUIDs v7 válidos

#### Scenario: Métodos de repositório que furam a poda de partição são removidos do contratocommand

- **WHEN** `apps/contratocommand/src/main/java/.../infrastructure/persistence/SpringDataAutorizacaoRepository.java`
  é inspecionado após a limpeza desta rodada
- **THEN** os métodos `findByStatus` e `findByIdAutorizacao` não existem — nenhum tinha chamador de
  produção, e ambos consultam sem `id_particao_conta`, varrendo as ~989 partições

#### Scenario: Utilitários de partição sem uso removidos do contratocommand

- **WHEN** `infrastructure/persistence/IdContaUUIDPartitionDistributor.java` e
  `ControleExpurgoAutorizacao.java` do `contratocommand` são inspecionados após a limpeza
- **THEN** `getPartitionPrecision` e `obterParticaoExpurgoDrop` não existem em `src/main` — cada um
  só tinha chamador em `src/test`

#### Scenario: Grafo de transição sem uso removido da app-ponte autorizacaostatus-producer

- **WHEN** `domain/enums/StatusAutorizacao.java` de `autorizacaostatus-producer` é inspecionado após
  a limpeza
- **THEN** `TRANSICOES` e `podeTransicionarPara` não existem — a app é uma ponte de formatos sem
  regra de máquina de estados própria, e nenhum caminho de produção os chamava
- **AND** `obterStatusEnumPorIdStatus` continua existindo, pois `TipoEventoAutorizacao.porStatus` o
  usa em produção

### Requirement: Utilitários usados apenas por testes vivem em src/test
Uma classe utilitária referenciada exclusivamente por código de teste SHALL residir em `src/test`, não em `src/main`, para não ser empacotada no artefato de produção.

#### Scenario: AchaQtdeSemanas da command movida para o source set de teste
- **WHEN** o módulo `contratocommand` é inspecionado após a limpeza
- **THEN** `AchaQtdeSemanas` existe em `src/test/java/.../domain/utilities/` e não existe em `src/main`, e o helper `GeraDatasPorParticao` e o teste `AchaQtdeSemanasTest` continuam compilando e passando

### Requirement: App de leitura não contém lógica exclusiva de escrita
O módulo `contratoquery` (somente leitura) MUST NOT conter lógica exclusiva do fluxo de escrita/contratação: geração de id/partição para inserção, cálculo de partição de expurgo de escrita ou inicialização de entidade para criação.

#### Scenario: Sem lógica de criação na query
- **WHEN** o `src/main` de `contratoquery` é pesquisado por lógica de criação
- **THEN** não há chamadas a `ReversibleUUIDv7.generate` nem cálculo de partição de escrita/expurgo em código de produção

### Requirement: Testes de código morto são removidos junto com o código
Ao remover uma classe ou método sem uso de produção, os testes que exercitavam exclusivamente esse código SHALL ser removidos na mesma mudança; testes de classes parcialmente afetadas SHALL perder apenas os casos dos membros removidos.

#### Scenario: Testes órfãos removidos na query
- **WHEN** o `src/test` de `contratoquery` é inspecionado após a limpeza
- **THEN** os testes `AutorizacaoTest`, `MotivoStatusAutorizacaoTest`, `CanaisConhecidosEnumTest`, `TipoContaTest`, `AchaQtdeSemanasTest`, `ControleExpurgoAutorizacaoTest` e `IdContaUUIDPartitionDistributorTest` não existem

#### Scenario: Testes de membros vivos preservados
- **WHEN** `TipoProdutoTest` e `StatusAutorizacaoTest` de `contratoquery` são executados
- **THEN** os casos dos métodos vivos (`obterTipoProdutoEnumPorId`, `obterStatusEnumPorIdStatus`, `getStatusAutorizacao`, `getTipoProduto`) continuam existindo e passando

### Requirement: Documentação de módulo reflete o código após a limpeza
Os arquivos `CLAUDE.md` e `AGENTS.md` de cada módulo SHALL ser atualizados na mesma mudança que remove classes ou testes por eles citados, e MUST permanecer idênticos entre si.

#### Scenario: Docs sem menção a classes removidas
- **WHEN** `CLAUDE.md` e `AGENTS.md` dos dois módulos são pesquisados pelos nomes das classes removidas
- **THEN** não há menção a elas como código existente, e `CLAUDE.md` é idêntico a `AGENTS.md` em cada módulo

### Requirement: Nenhum arquivo Java contém import sem uso

Nenhum arquivo `.java` das cinco aplicações de `apps/` — em `src/main` **ou** `src/test` — SHALL
declarar um `import` cujo tipo não seja referenciado no arquivo.

O escopo é deliberadamente maior que o dos requisitos originais desta capacidade (que cobrem
apenas `contratocommand` e `contratoquery`): import sem uso é defeito de mesma natureza
nas cinco apps, e o custo de verificar é o mesmo.

#### Scenario: Import sem uso não existe em nenhuma app

- **WHEN** os arquivos `.java` de `apps/contratocommand`, `apps/contratoquery`,
  `apps/autorizacaostatus-producer`, `apps/eventos-consumer` e `apps/temporiza-autorizacao` são
  inspecionados
- **THEN** todo `import` declarado tem ao menos uma referência ao tipo importado no corpo do
  arquivo

#### Scenario: Código de teste tem o mesmo critério

- **WHEN** um arquivo de `src/test` declara um import que deixou de ser usado após uma refatoração
  do teste
- **THEN** o import é removido na mesma mudança que o tornou inútil

### Requirement: Parâmetros de método sem uso são removidos ou justificados

Um parâmetro de método em `src/main` que não é referenciado no corpo do método SHALL ser removido,
**exceto** quando exigido pela assinatura de um contrato externo.

São exceções legítimas, que MAY permanecer sem uso:
- parâmetro de método `@ExceptionHandler` do Spring MVC;
- parâmetro de callback de listener (SQS, Kafka) exigido pela assinatura do container;
- parâmetro de método que implementa interface ou sobrescreve método de superclasse;
- parâmetro exigido por assinatura de biblioteca (`main(String[] args)`).

Quando o parâmetro permanece por uma dessas razões e isso não é óbvio pela leitura, a razão SHALL
estar registrada — em javadoc ou comentário de uma linha, conforme `higiene-comentarios-codigo`.

#### Scenario: Parâmetro morto de método próprio é removido

- **WHEN** um método definido pela própria aplicação declara um parâmetro nunca referenciado no
  corpo, e nenhuma assinatura externa o exige
- **THEN** o parâmetro é removido, junto de todas as chamadas que o passavam

#### Scenario: Parâmetro exigido por framework permanece

- **WHEN** um método `@ExceptionHandler` declara `HttpServletRequest req` e não o usa no corpo
- **THEN** o parâmetro permanece, porque removê-lo quebra o binding do Spring MVC
- **AND** a remoção não é proposta como "código morto"

#### Scenario: Varredura usa o compilador, não busca textual

- **WHEN** a ausência de parâmetro morto é verificada
- **THEN** a verificação SHALL usar os avisos do compilador (`-Xlint`), não busca por padrão de
  texto, porque a determinação exige análise de fluxo

### Requirement: Marcação TODO exige custo concreto identificado

Um comentário `// TODO` que sinaliza oportunidade de refatoração SHALL satisfazer ao menos um
destes gatilhos:

1. uma medição registrada (latência, contagem, consumo de recurso);
2. um bloqueio externo nomeado (limitação de ferramenta, versão de biblioteca pendente);
3. uma change OpenSpec aberta que o endereça, citada pelo nome.

`TODO` que não satisfaz nenhum gatilho MUST NOT existir no código. A oportunidade percebida sem
custo mensurado é registro de backlog, não comentário — e comentário que não explica um porquê não
óbvio já é vedado por `higiene-comentarios-codigo`.

Cada `TODO` SHALL caber em uma linha e nomear a causa, não o sintoma.

#### Scenario: TODO com medição é aceito

- **WHEN** um trecho tem custo medido e registrado (ex.: 148 ms de planejamento por chamada na
  listagem do `contratoquery`)
- **THEN** um `// TODO` de uma linha nomeando a medição e a change que a endereça é aceito

#### Scenario: TODO genérico é removido

- **WHEN** existe um `// TODO: otimizar`, `// TODO: refatorar` ou equivalente, sem medição, sem
  bloqueio nomeado e sem change citada
- **THEN** esse comentário não existe no código

#### Scenario: TODO nomeia causa, não sintoma

- **WHEN** um `// TODO` é escrito
- **THEN** ele descreve o que causa o problema e o que o desbloqueia
- **AND** não se limita a nomear o sintoma percebido
