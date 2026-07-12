# Tasks: remocao-dead-code-aplicacoes

## 1. arj-contratoquery — remoções independentes

- [x] 1.1 Remover `domain/model/ContratoBase.java` e o pacote `domain/model/` (zero referências)
- [x] 1.2 Remover `domain/enums/TipoJornadaAutorizacao.java` (zero referências no main)
- [x] 1.3 Remover `domain/enums/CanaisConhecidosEnum.java` e `CanaisConhecidosEnumTest.java`
- [x] 1.4 Remover `domain/enums/TipoConta.java` e `TipoContaTest.java`
- [x] 1.5 Remover `domain/enums/MotivoStatusAutorizacao.java` e `MotivoStatusAutorizacaoTest.java`
- [x] 1.6 Remover `domain/utilities/AchaQtdeSemanas.java` e `AchaQtdeSemanasTest.java`
- [x] 1.7 Remover `domain/utilities/ControleExpurgoAutorizacao.java` e `ControleExpurgoAutorizacaoTest.java`
- [x] 1.8 Compilar o módulo (`mvn clean compile`) para confirmar que nada quebrou

## 2. arj-contratoquery — cadeia da entidade (ordem importa, ver D4 do design)

- [x] 2.1 Remover o método `inicializaCriacao()` de `domain/entities/Autorizacao.java` (com os imports de `IdContaUUIDPartitionDistributor` e `ReversibleUUIDv7` que ficarem órfãos) e remover `AutorizacaoTest.java`
- [x] 2.2 Remover anotações Lombok redundantes `@Getter`/`@Setter` da entidade `Autorizacao` (já cobertas por `@Data`)
- [x] 2.3 Remover `domain/utilities/IdContaUUIDPartitionDistributor.java` e `IdContaUUIDPartitionDistributorTest.java` (órfã após 2.1)
- [x] 2.4 Confirmar que `ReversibleUUIDv7.java` permanece íntegro (`generate()` + `extract()`) e que `ConsultarAutorizacaoService`/testes seguem compilando

## 3. arj-contratoquery — métodos de enum mortos

- [x] 3.1 Remover `obterTipoProdutoEnumPorNome` de `domain/enums/TipoProduto.java` e os casos correspondentes em `TipoProdutoTest.java` (manter os casos de `obterTipoProdutoEnumPorId`/`getTipoProduto`)
- [x] 3.2 Remover `isStatusFinalizador` de `domain/enums/StatusAutorizacao.java` (manter os demais casos de `StatusAutorizacaoTest.java`)
- [x] 3.3 Rodar `mvn test` no módulo `arj-contratoquery` — tudo verde

## 4. arj-contratocommand — remoções

- [x] 4.1 Remover `domain/enums/CanaisConhecidosEnum.java` e `CanaisConhecidosEnumTest.java`
- [x] 4.2 Remover `domain/enums/TipoConta.java` e `TipoContaTest.java`
- [x] 4.3 Mover `domain/utilities/AchaQtdeSemanas.java` de `src/main` para `src/test/java/br/com/srportto/contratocommand/domain/utilities/` (mesmo pacote; `AchaQtdeSemanasTest` e `GeraDatasPorParticao` continuam funcionando)
- [x] 4.4 Remover `isStatusFinalizador` de `domain/enums/StatusAutorizacao.java`
- [x] 4.5 Rodar `mvn test` no módulo `arj-contratocommand` — tudo verde

## 5. Documentação

- [x] 5.1 Atualizar `CLAUDE.md` e `AGENTS.md` de `arj-contratoquery`: remover menções aos testes/classes removidos (lista de classes de teste, armadilhas, etc.) e conferir `diff CLAUDE.md AGENTS.md` vazio
- [x] 5.2 Atualizar `CLAUDE.md` e `AGENTS.md` de `arj-contratocommand`: refletir a mudança de `AchaQtdeSemanas` para `src/test` e remoções, e conferir `diff CLAUDE.md AGENTS.md` vazio

## 6. Verificação final

- [x] 6.1 `mvn test` verde nos dois módulos
- [x] 6.2 Varredura de confirmação: grep pelos nomes removidos (`ContratoBase`, `TipoJornadaAutorizacao` na query, `CanaisConhecidosEnum`, `TipoConta`, `MotivoStatusAutorizacao` na query, `AchaQtdeSemanas` no main, `ControleExpurgoAutorizacao` na query, `IdContaUUIDPartitionDistributor` na query, `isStatusFinalizador`, `inicializaCriacao` na query) sem ocorrências indevidas
