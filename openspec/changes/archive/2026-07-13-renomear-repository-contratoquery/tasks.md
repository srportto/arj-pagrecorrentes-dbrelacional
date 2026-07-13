# Tasks: renomear-repository-contratoquery

## 1. Rename da interface

- [x] 1.1 Renomear `AutorizacaoQueryRepository.java` para `AutorizacaoRepository.java` (arquivo + nome da interface), mantendo `extends JpaRepository<Autorizacao, IdAutorizacao>` e as 2 queries JPQL explícitas inalteradas

## 2. Services

- [x] 2.1 Em `ConsultarAutorizacaoService`: trocar o tipo do campo para `AutorizacaoRepository` e renomear o campo de `autorizacaoQueryRepository` para `repository`; ajustar os usos internos (`repository.findById(...)`)
- [x] 2.2 Em `ListarAutorizacoesService`: trocar o tipo do campo para `AutorizacaoRepository` e renomear o campo de `autorizacaoQueryRepository` para `repository`; ajustar os usos internos (`repository.findByIdUnicoContaContratante(...)`, `repository.findByIdUnicoContaContratanteAndStatusIn(...)`)
- [x] 2.3 Compilar o módulo (`mvn clean compile` em `aplicacoes/arj-contratoquery`) e corrigir qualquer erro residual do rename

## 3. Testes

- [x] 3.1 Ajustar `ConsultarAutorizacaoServiceTest`: tipo/nome do mock (`AutorizacaoRepository repository`) e todos os `when(...)`/`verify(...)` correspondentes
- [x] 3.2 Ajustar `ListarAutorizacoesServiceTest`: tipo/nome do mock (`AutorizacaoRepository repository`) e todos os `when(...)`/`verify(...)` correspondentes
- [x] 3.3 Rodar `mvn test` no módulo `arj-contratoquery` e garantir suíte verde

## 4. Documentação

- [x] 4.1 Atualizar `aplicacoes/arj-contratoquery/CLAUDE.md`: toda menção a `AutorizacaoQueryRepository` (diagramas de fluxo, lista de componentes de `application/`, armadilhas críticas) passa a `AutorizacaoRepository`
- [x] 4.2 Replicar as mesmas edições em `aplicacoes/arj-contratoquery/AGENTS.md` e verificar com diff que os dois arquivos permanecem idênticos
- [x] 4.3 Atualizar `aplicacoes/arj-contratoquery/README.md`: todas as menções à classe (estrutura de pacotes, diagrama de fluxo, armadilha do JPQL explícito) e a linha da tabela de convenções de nomenclatura (`Repository | {Entidade}QueryRepository | AutorizacaoQueryRepository` → `Repository | {Entidade}Repository | AutorizacaoRepository`)

## 5. Verificação final

- [x] 5.1 Conferir o cenário do delta spec `documentacao-contratoquery`: `CLAUDE.md`/`AGENTS.md` descrevem `AutorizacaoRepository` (não `AutorizacaoQueryRepository`) na lista de classes da query
- [x] 5.2 `grep -r AutorizacaoQueryRepository aplicacoes/arj-contratoquery` não retorna nenhuma ocorrência (código e docs)
