## Context

`AutorizacaoQueryRepository` é a única classe/interface em todo o `arj-contratoquery` com "Query" no nome — `ListarAutorizacoesService`, `ConsultarAutorizacaoService`, `AutorizacaoController` e os DTOs não carregam esse sufixo. O próprio artefato Maven (`arj-contratoquery`) e o pacote raiz (`br.com.srportto.contratoquery`) já deixam claro que é o lado de leitura, tornando o sufixo redundante dentro da classe. O `arj-contratocommand` usa a convenção enxuta `{Entidade}Repository` (`AutorizacaoRepository`, campo `repository`). O objetivo desta mudança é uniformizar a convenção entre os dois módulos irmãos.

Único ponto de spec afetado: a capability `documentacao-contratoquery` cita `AutorizacaoQueryRepository` nominalmente em um cenário do requirement de `CLAUDE.md`/`AGENTS.md`.

## Goals / Non-Goals

**Goals:**
- `AutorizacaoRepository` como nome único e simétrico entre `arj-contratocommand` e `arj-contratoquery` para o repository de `Autorizacao`.
- Nome de campo consistente com a convenção enxuta do `arj-contratocommand` (`repository`, não `autorizacaoRepository` ou `autorizacaoQueryRepository`).
- Documentação (`CLAUDE.md`/`AGENTS.md`/`README.md`, spec `documentacao-contratoquery`) fiel ao código resultante.

**Non-Goals:**
- Nenhuma mudança de comportamento observável: mesmas queries JPQL, mesmo particionamento, mesmos contratos REST.
- Nenhuma mudança em `arj-contratocommand`.
- Não renomear `ListarAutorizacoesService`/`ConsultarAutorizacaoService` nem reestruturar `application/autorizacao/`.

## Decisions

### D1 — Renomear tipo e arquivo

`AutorizacaoQueryRepository.java` → `AutorizacaoRepository.java`, interface `AutorizacaoQueryRepository extends JpaRepository<...>` → `AutorizacaoRepository extends JpaRepository<...>`. Corpo da interface (as 2 queries JPQL explícitas) permanece idêntico — rename puro, sem mudança de assinatura de método.

### D2 — Nome do campo também muda, para `repository`

Nos dois services que injetam o repository (`ConsultarAutorizacaoService`, `ListarAutorizacoesService`), o campo `autorizacaoQueryRepository` passa a `repository`, igual ao `arj-contratocommand`. Cada service tem exatamente uma dependência de repository, então o nome curto não perde clareza dentro da classe — e maximiza a simetria entre os dois módulos, que era o motivador explícito desta mudança.

*Alternativa considerada*: manter o campo como `autorizacaoRepository` (só trocar o tipo, preservando um nome mais descritivo já que dois services distintos compartilham o mesmo repository). Descartada porque o objetivo declarado é uniformidade com o command, não descritividade adicional — e "repository" já é suficientemente claro no escopo de uma classe com uma única dependência desse tipo.

### D3 — Sincronizar a spec `documentacao-contratoquery`

O cenário "Conteúdo reflete apenas o que existe na query" cita `AutorizacaoQueryRepository` no texto. Delta `MODIFIED Requirements` troca a citação para `AutorizacaoRepository`, sem alterar o restante do requirement (que trata do conjunto de classes documentadas, não do nome específico em si).

## Risks / Trade-offs

- **[Grep cego atinge as duas apps]** Depois do rename, `grep -r AutorizacaoRepository` no monorepo acerta `arj-contratocommand` e `arj-contratoquery` sem distinção — hoje o sufixo `Query` é o que permite diferenciar num stack trace sem pacote completo à vista → Mitigação: aceito conscientemente; o pacote (`br.com.srportto.contratoquery` vs `contratocommand`) sempre aparece em stack traces reais, e o ganho de uniformidade supera esse custo de busca textual.
- **[Retipagem espalhada]** 2 services + 2 testes usam o tipo/campo antigo → Mitigação: rename mecânico, sem lógica nova; `mvn clean test` no módulo valida tudo de uma vez.
- **[Docs desatualizadas]** `CLAUDE.md`/`AGENTS.md`/`README.md` e a spec citam o nome antigo → Mitigação: tasks explícitas de atualização, com verificação por diff entre `CLAUDE.md` e `AGENTS.md`.

## Migration Plan

Refactor interno sem migração de dados, sem mudança de API. Deploy normal. Rollback = revert do commit. Verificação: `mvn test` no módulo `arj-contratoquery` verde e diff vazio entre `CLAUDE.md` e `AGENTS.md`.

## Open Questions

Nenhuma — a decisão sobre o nome do campo (D2) já foi resolvida a favor da uniformidade máxima com o `arj-contratocommand`.
