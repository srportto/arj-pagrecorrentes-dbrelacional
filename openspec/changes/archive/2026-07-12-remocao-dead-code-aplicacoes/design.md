# Design: remocao-dead-code-aplicacoes

## Context

`contratoquery` nasceu como cópia de `contratocommand` e reteve classes do fluxo de escrita que nunca foram usadas no serviço de leitura. A análise de referências (grep por nome de classe/método, excluindo o próprio arquivo) mapeou exatamente quem usa o quê:

**Query — classes main sem nenhuma referência de produção:**

| Classe | Referências fora dela mesma |
|---|---|
| `domain/model/ContratoBase` | nenhuma |
| `domain/enums/TipoJornadaAutorizacao` | nenhuma no main |
| `domain/enums/CanaisConhecidosEnum` | só `CanaisConhecidosEnumTest` |
| `domain/enums/TipoConta` | só `TipoContaTest` |
| `domain/enums/MotivoStatusAutorizacao` | só `MotivoStatusAutorizacaoTest` |
| `domain/utilities/AchaQtdeSemanas` | só `AchaQtdeSemanasTest` |
| `domain/utilities/ControleExpurgoAutorizacao` | só `ControleExpurgoAutorizacaoTest` |

**Query — cadeia de dead code partindo da entidade:**

```
Autorizacao.inicializaCriacao()  ← nunca chamado no main (sem MapStruct na query)
    ├─ IdContaUUIDPartitionDistributor  ← único uso main; morre junto
    └─ ReversibleUUIDv7.generate()      ← fica sem uso main, mas extract()
                                          continua vivo (ConsultarAutorizacaoService)
```

**Command — menor:** `CanaisConhecidosEnum` e `TipoConta` só têm os próprios testes; `AchaQtdeSemanas` só é usado por `AchaQtdeSemanasTest` e pelo helper de teste `GeraDatasPorParticao` (o código de produção `ControleExpurgoAutorizacao` calcula semanas por conta própria); `StatusAutorizacao.isStatusFinalizador` não tem uso em nenhuma das duas apps.

Especificações existentes que tangenciam o escopo: `coesao-contratocommand` (exige remoção de classes não usadas na command — precedente), `motivo-status-por-jornada` (a query expõe `motivoStatus` como **string crua do banco**, sem enum) e `validacao-header-jornada` (`TipoJornadaAutorizacao` é requisito **da command**, não da query).

## Goals / Non-Goals

**Goals:**
- Eliminar todas as classes e métodos do `src/main` das duas apps sem referência de produção.
- Remover junto os testes que só exercitavam código morto (mantê-los exigiria manter o morto vivo).
- Manter `mvn test` 100% verde nos dois módulos após as remoções.
- Manter `CLAUDE.md`/`AGENTS.md` fiéis ao código (espelhos idênticos entre si).

**Non-Goals:**
- Migração de DTOs Lombok → records (excluída por decisão do usuário).
- Expansão de wildcard imports ou qualquer reorganização de imports (varredura não achou imports não usados).
- Migração para `void main()` do Java 25 (TODO existente, bloqueado pelo maven plugin).
- Qualquer mudança de contrato REST, entidade persistida (colunas) ou comportamento observável.

## Decisions

### D1 — Testes de código morto são removidos, não preservados
Um teste cujo único propósito é exercitar uma classe sem uso de produção não protege comportamento nenhum — ele apenas impede a remoção do morto. Alternativa considerada: manter os testes como "documentação" — rejeitada, pois contradiz o objetivo e o precedente da spec `coesao-contratocommand`.

### D2 — `ReversibleUUIDv7` da query permanece íntegro (com `generate()`)
Após remover `inicializaCriacao()`, `generate()` fica sem uso no main da query. Ainda assim permanece: os testes de `extract()` (e o `ConsultarAutorizacaoServiceTest`) precisam construir UUIDs v7 válidos com partição embutida, e remover `generate()` exigiria duplicar o bit-twiddling dentro dos testes. Par `generate`/`extract` é uma unidade coesa. Alternativa: mover `generate()` para `src/test` — rejeitada por fragmentar a classe.

### D3 — `AchaQtdeSemanas` da command é **movida** para `src/test`, não removida
Diferente da cópia da query (que morre), a da command é usada pelo helper `GeraDatasPorParticao` (tooling manual de geração de datas por partição, documentado no CLAUDE.md). Mover para `src/test/java/.../domain/utilities/` mantém o tooling funcionando e tira a classe do jar de produção. Alternativas: remover e inlinar o cálculo no helper (mais invasivo), ou deixar no main (perpetua o problema).

### D4 — Remoção em cascata na query é feita na ordem da cadeia
Primeiro remove-se `Autorizacao.inicializaCriacao()` (+ `AutorizacaoTest`), o que órfãna `IdContaUUIDPartitionDistributor`; então remove-se a utility (+ teste). Compilação intermediária confirma cada elo. Isso evita remover a utility antes e quebrar a compilação da entidade.

### D5 — `@Getter`/`@Setter` redundantes saem da entidade `Autorizacao` da query
`@Data` já gera getters/setters; as anotações extras são ruído sem efeito. É limpeza de anotação obsoleta, não migração de DTO (fora de escopo apenas a troca Lombok→record).

### D6 — Métodos de enum mortos saem junto com as classes mortas
`TipoProduto.obterTipoProdutoEnumPorNome` (query) e `StatusAutorizacao.isStatusFinalizador` (ambas as apps) são removidos, com os respectivos casos de teste (não as classes de teste inteiras — `TipoProdutoTest` e `StatusAutorizacaoTest` mantêm os casos dos métodos vivos).

## Risks / Trade-offs

- [Classe removida pode ser desejada no futuro (ex.: query exibir `MotivoStatusAutorizacao` por extenso)] → Mitigação: o git preserva o histórico; recuperar é um `git revert`/cópia da command. O custo de recriar é menor que o de manter morto indefinidamente.
- [Remoção de método usado por reflection/serialização não detectada pelo grep] → Mitigação: as classes removidas não são entidades JPA nem DTOs serializados (exceto o método `inicializaCriacao`, que não participa de serialização); `mvn test` + subida local cobrem regressões.
- [CLAUDE.md/AGENTS.md dessincronizados] → Mitigação: tarefa explícita de atualização com verificação de que os dois arquivos ficam idênticos (`diff CLAUDE.md AGENTS.md`).
- [Testes de enum parcialmente editados (D6) podem quebrar] → Mitigação: rodar `mvn test` por módulo após cada bloco de remoção, não só no final.

## Migration Plan

Sem migração de dados ou deploy especial — mudança é somente de código-fonte. Rollback = revert do commit. Ordem de implementação: query (blocos independentes → cadeia da entidade), depois command, depois docs.

## Open Questions

(nenhuma — as decisões D1–D6 cobrem os pontos ambíguos levantados na exploração)
