## Context

A aplicação `contratocommand` segue arquitetura hexagonal. Hoje os serviços de regra de contratação e cancelamento estão em `application/defaultservice`:

```
application/defaultservice/
  contratacao/   ContratacaoOrquestradorService, ContratacaoService, ContratacaoRule,
                 ContratacaoValidator, rules/{ValorLimiteContrato, DataFimVigenciaInvalida, MetadadoRule}
  cancelamento/  CancelamentoOrquestradorService, CancelamentoService, CancelamentoRule,
                 CancelamentoValidator, CancelamentoContext, rules/{TipoProdutoCancelamento}
```

A tabela de camadas em `docs/arquitetura/based-java-aplication.md` atribui "regras de negócio" também ao domínio. `ContratacaoService`/`CancelamentoService` são contratos de strategy (ports) implementados por `PixAutoService`/`DdaAutoService` em `application/enabledproduct`; validators, rules e `CancelamentoContext` são lógica de negócio. Já os `*OrquestradorService` são componentes Spring que iteram a `List<Strategy>` e selecionam o produto — isso é orquestração de caso de uso, papel da camada de aplicação.

São 13 classes de produção e 8 de teste (espelhadas), e consumidores externos que importam o pacote (`AutorizacaoController`, `Criar/CancelarAutorizacaoUseCase`, `PixAutoService`, `DdaAutoService`, `TestFixtures`).

## Goals / Non-Goals

**Goals:**
- Mover as regras de negócio (contratos de strategy, validators, rules, `CancelamentoContext`) para `domain/services/{contratacao,cancelamento}` (com `rules/`).
- Manter os orquestradores na aplicação, em `application/services/{contratacao,cancelamento}`.
- Eliminar `application/defaultservice`.
- Corrigir todas as declarações `package` e `import` afetadas, em `main` e `test`.
- Manter o build verde e todos os testes passando, sem mudança de comportamento; atualizar a documentação.

**Non-Goals:**
- Renomear classes, alterar assinaturas, dividir/mesclar serviços ou mudar regras de negócio.
- Alterar contratos REST, schema de banco, `pom.xml` ou dependências.
- Mover as implementações de strategy (`PixAutoService`, `DdaAutoService`) ou reorganizar outros pacotes.

## Decisions

**Decisão 1 — Split por responsabilidade.**
Regras de negócio → `domain.services`; orquestradores → `application.services`. Os contratos `ContratacaoService`/`CancelamentoService` vão para o domínio como ports; as implementações continuam em `application/enabledproduct` (aplicação → domínio, direção correta). Os orquestradores ficam em `application/services`, paralelos a `domain/services`, eliminando o nome genérico `defaultservice`. Alternativa considerada: mover tudo (inclusive orquestradores) para `domain/services` — rejeitada por colocar componentes Spring de orquestração no domínio, contrariando a separação de camadas.

**Decisão 2 — Direção de dependência preservada (sem ciclo).**
Os orquestradores passam a importar `domain.services.*` (contratos/contexto); nenhuma classe movida para o domínio referencia os orquestradores (verificado por busca). Assim `application → domain` se mantém e não há `domain → application`.

**Decisão 3 — Mover preservando o espelho `main`/`test`.**
A estrutura de testes acompanha a de produção: validators/rules tests → `domain/services/...`; orquestrador tests → `application/services/...`. Mantém a convenção de teste no mesmo pacote da classe sob teste.

**Decisão 4 — Regra de reescrita determinística para imports.**
Como apenas duas classes permanecem em aplicação, a substituição textual é não-ambígua: as FQNs `application.defaultservice.contratacao.ContratacaoOrquestradorService` e `application.defaultservice.cancelamento.CancelamentoOrquestradorService` viram `application.services...`; todo o restante de `application.defaultservice` vira `domain.services`. As linhas `package` dos dois orquestradores e os imports cruzados que eles passam a precisar (`ContratacaoService`; `CancelamentoService` + `CancelamentoContext`) são ajustados explicitamente, pois antes resolviam no mesmo pacote. O mesmo vale para os dois testes de orquestrador.

**Decisão 5 — Validação pelo compilador.**
A garantia de "nenhuma referência ao pacote antigo" e de ausência de ciclo vem de `mvn clean test`: qualquer import remanescente quebra a compilação. Busca textual final por `application\.defaultservice` em `src/` complementa.

## Risks / Trade-offs

- **Import cruzado esquecido nos orquestradores/tests** (antes resolviam no mesmo pacote) → o compilador falha; os 4 arquivos especiais são tratados explicitamente e validados pelo build.
- **Reescrita textual converter o pacote do orquestrador para `domain.services`** → corrigido logo após, com ajuste explícito das duas linhas `package` para `application.services`.
- **Artefatos gerados em `target/`** referenciam pacotes antigos → regenerados no próximo build; rodar `mvn clean`.
- **Diff grande, baixo risco semântico** → revisão foca em `package`/`import`; nenhuma linha de lógica muda.

## Migration Plan

Mudança puramente estrutural, sem migração de dados nem janela de deploy especial. Rollback = reverter o commit. Pacotes são internos à aplicação; nenhuma coordenação externa é necessária.
