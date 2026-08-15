## Context

O `contratocommand` organiza `application/` em subpacotes desde o refactor de coesão anterior (change `melhoria-coesao-contratocommand`, arquivada): `contratacao/` e `cancelamento/` são verticais por operação (cada uma com Rule interface, Validator, UseCase e `rules/`), enquanto `autorizacao/` guarda `AutorizacaoRepository` e `AutorizacaoMapper`, componentes compartilhados pelas duas verticais. Essa exploração (`/opsx:explore`) mapeou o pacote inteiro e identificou dois pontos de melhoria de coesão: (1) `autorizacao/` ocupa o mesmo nível hierárquico das verticais de feature sem ser uma, e colide de nome com `Autorizacao` (entidade), `AutorizacaoController` e `AutorizacaoCompletaResponseDto`; (2) todos os 10 beans Spring-gerenciados de `application/` usam `@Component`, sem diferenciar os 4 orquestradores (Validators/UseCases) das 6 rules (estratégias).

Um precedente já existe no próprio módulo: `TestFixtures.java` (em `src/test/.../application/`) já vive solto na raiz de `application/` nos testes, sem pacote próprio — o mesmo padrão que esta change aplica ao código de produção.

## Goals / Non-Goals

**Goals:**
- Eliminar o pacote `application/autorizacao/`, promovendo `AutorizacaoRepository` e `AutorizacaoMapper` (e o teste correspondente) para a raiz de `application/`.
- Diferenciar `@Service` (orquestradores: `ContratacaoValidator`, `CancelamentoValidator`, `CriarAutorizacaoUseCase`, `CancelarAutorizacaoUseCase`) de `@Component` (rules: as 6 implementações de `ContratacaoRule`/`CancelamentoRule`).
- Preservar 100% do comportamento observável: mesmos endpoints, mesmas respostas, mesma injeção de dependências (Spring trata `@Service` e `@Component` como beans candidatos de forma idêntica para injeção).
- Atualizar `CLAUDE.md`/`AGENTS.md` do módulo para refletir a nova estrutura e convenção.

**Non-Goals:**
- Não mexe em `contratacao/` nem `cancelamento/` além dos imports afetados pelo movimento de `autorizacao/` — a organização interna dessas verticais já está coesa.
- Não introduz `@Service` em nenhuma Rule — rules continuam `@Component` deliberadamente (ver Decisão 2).
- Não altera contratos REST, DTOs, exceções ou qualquer comportamento de negócio.
- Não propaga a convenção para `contratoquery` — escopo restrito a `contratocommand`.

## Decisions

### 1. `AutorizacaoRepository`/`AutorizacaoMapper` vão para a raiz de `application/`, não para dentro de `contratacao/` ou `cancelamento/`

Ambos são usados pelas duas verticais (`CriarAutorizacaoUseCase` e `CancelarAutorizacaoUseCase` injetam os dois). Colocá-los dentro de uma das verticais criaria uma dependência cruzada artificial (`cancelamento` importando de `contratacao`, ou vice-versa). A raiz de `application/` é o único lugar que não subordina um componente compartilhado a uma feature específica.

- **Alternativa considerada — manter `autorizacao/` como pacote**: rejeitada por ser a causa do problema (sugere feature, colide de nome).
- **Alternativa considerada — renomear para `application/shared/` ou `application/common/`**: mais explícito, mas adiciona uma palavra genérica sem trazer informação nova (o fato de estar na raiz, ao lado das verticais nomeadas, já comunica "isto é a base compartilhada"); também diverge do precedente já estabelecido por `TestFixtures.java` na raiz dos testes.

### 2. Rules continuam `@Component`; só Validators e UseCases viram `@Service`

`@Service` no Spring é semanticamente "a lógica de negócio principal da camada de aplicação" — encaixe direto para um Use Case (padrão de nomenclatura já usado: `CriarAutorizacaoUseCase`, `CancelarAutorizacaoUseCase`) e para o Validator que orquestra a lista de rules. As rules individuais são implementações do padrão Strategy, injetadas coletivamente (`List<ContratacaoRule>`) — cada uma é uma peça substituível, não "o" serviço da operação. Manter `@Component` nelas preserva a distinção: `@Service` = orquestrador único por operação; `@Component` = estratégia plugável.

- **Alternativa considerada — tudo `@Component` (status quo)**: uniformidade sem bikeshedding, também uma postura válida; descartada porque a exploração concluiu que a semântica de `@Service` for os orquestradores comunica melhor a arquitetura para quem lê o código pela primeira vez, e o custo da mudança é nulo (Spring resolve os dois estereótipos de forma idêntica para injeção).
- **Alternativa considerada — `@Service` em tudo (inclusive rules)**: rejeitada por diluir a distinção que motivou a mudança.

## Risks / Trade-offs

- **[Import quebrado em algum ponto não mapeado]** → Mitigado com busca por `application.autorizacao` antes de mover (já feita na exploração: só 6 arquivos referenciam o pacote, todos mapeados nas tasks) e `mvn clean compile` + `mvn test` como verificação final.
- **[Documentação (`CLAUDE.md`/`AGENTS.md`) ficar dessincronizada do código]** → Tarefa explícita de atualização incluída no plano; os dois arquivos são espelhos e devem ser editados juntos (conforme a própria instrução no topo deles).
- **[Convenção `@Service`/`@Component` não documentada faz a próxima rule nova usar o estereótipo errado]** → A atualização de `CLAUDE.md`/`AGENTS.md` inclui a regra explícita: "Validators e UseCases são `@Service`; Rules são `@Component`".

## Migration Plan

Mudança somente de código e organização de pacotes, sem migração de dados, sem alteração de contrato. Deploy padrão; rollback = reverter o commit (Spring não versiona bean definitions em banco, então não há estado externo a desfazer).

## Open Questions

Nenhuma — decisões fechadas durante a exploração (`/opsx:explore`) com confirmação do usuário sobre a diferenciação `@Service`/`@Component`.
