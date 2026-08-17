## MODIFIED Requirements

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
