## Context

`arj-contratoquery` (porta 8081) é o serviço de leitura do par de microserviços. Ele compartilha o mesmo banco de dados particionado com `arj-contratocommand` mas só realiza queries (DB_READ_ONLY=true). Possui uma arquitetura hexagonal mais simples — sem Strategy Pattern, sem orquestradores de contratação/cancelamento, sem mappers — apenas `ListarAutorizacoesService`, `ConsultarAutorizacaoService`, `AutorizacaoQueryRepository` e `AutorizacaoController`.

`arj-contratocommand` já possui os três arquivos (`AGENTS.md`, `CLAUDE.md`, `README.md`) bem estruturados. O objetivo é replicar essa estrutura no query, adaptando o conteúdo para refletir com precisão o que existe nele, evitando que agentes ou desenvolvedores apliquem incorretamente detalhes do command na query.

## Goals / Non-Goals

**Goals:**
- Criar `CLAUDE.md` e `AGENTS.md` (espelhos) com guia de orientação rápida focado na query: endpoints GET, serviços de leitura, sem Strategy/orquestradores
- Criar `README.md` completo com stack, estrutura de pacotes, como executar, exemplos de endpoints, testes e armadilhas específicas da query
- Manter a mesma seção estrutural do command (`Comece por aqui`, `Build & Testes`, `Pré-requisitos`, `Stack`, `Endpoints`, `Arquitetura`, `Armadilhas críticas`)

**Non-Goals:**
- Nenhuma alteração de código, configuração ou dependências
- Não copiar conteúdo específico do command que não existe na query (ex.: orquestradores, use cases de contratação, cancelamento, mappers)

## Decisions

**Decisão 1 — Estrutura idêntica ao command, conteúdo adaptado**
Os mesmos arquivos e seções são mantidos para consistência. Cada seção é reescrita ou simplificada para refletir a query. Alternativa considerada: estrutura totalmente diferente — rejeitada por gerar inconsistência entre os dois microserviços do mesmo par.

**Decisão 2 — `AGENTS.md` e `CLAUDE.md` são espelhos**
Os dois arquivos têm conteúdo idêntico, como no command. O `AGENTS.md` serve para ferramentas que leem aquele arquivo em vez de `CLAUDE.md`. Não há lógica em diferenciá-los.

**Decisão 3 — Destacar diferenças críticas query vs command**
O `CLAUDE.md`/`AGENTS.md` deve deixar explícito:
- Porta 8081 (command é 8080)
- DB_READ_ONLY=true
- Apenas endpoints GET (sem POST/PATCH)
- Sem Strategy Pattern, sem orquestradores
- Ponto de entrada para leitura: `ListarAutorizacoesService` e `ConsultarAutorizacaoService`

## Risks / Trade-offs

- [Risco de desatualização] Ao evoluir a query, os docs precisam ser mantidos em sincronia manualmente → Mitigação: incluir checklist de commit que lembra de atualizar docs se endpoints/classes mudarem.
