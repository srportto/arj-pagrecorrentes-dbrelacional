## Context

`arj-contratocommand` é uma API REST de autorizações de produtos financeiros (PIX_AUTO, DDA_AUTO) em arquitetura hexagonal, com Strategy para multi-produto, particionamento temporal e framework próprio de regras (`Rule`/`Validator`). A análise de coesão revelou que o eixo **operação** (contratacao / cancelamento) está saudável, mas o eixo **produto** está duplicado: `Mapper`, `Repository`, `UseCases` e `Service` de PIX e DDA são cópia-carbono, apesar de ambos operarem sobre a mesma entidade `Autorizacao` e tabela `autorizacoes`. A única variação de negócio entre produtos (valor-limite) já vive nas `Rules`. Há ainda defeitos latentes (transação ineficaz no cancelamento, `ApplicationContextException` vazada, status mágico que conflita com `StatusAutorizacao`) e incoerências de contrato (DTO mutável usado como carteiro de estado, record vs classe).

Esta change é preparatória para a futura operação de **alteração de contrato**, que entrará como terceira operação simétrica a contratacao/cancelamento. Restrições: manter `mvn clean compile` e `mvn test` verdes ao fim de cada fase; preservar os contratos REST; PostgreSQL obrigatório (sem fallback H2); Java 25 + Spring Boot 4.

## Goals / Non-Goals

**Goals:**
- Eliminar a duplicação do eixo produto mantendo o seam Strategy como ponto de extensão explícito.
- Corrigir os defeitos latentes de correção (transação, exceção, status) sem alterar contratos REST.
- Tornar os DTOs imutáveis e remover o uso de DTO como transporte de estado interno.
- Deixar a base enxuta, coesa e simétrica para receber a operação de alteração de contrato.
- Preservar (e consolidar) a cobertura de testes existente.

**Non-Goals:**
- Implementar a funcionalidade de alteração de contrato (é trabalho posterior).
- Redesenhar o particionamento temporal, os UUIDs reversíveis ou o schema do banco.
- Alterar os contratos REST públicos (corpo, headers, status), exceto o valor de `status` passar a refletir o enum.
- Migrar o `arj-contratoquery` (app irmã de leitura).

## Decisions

### Decisão 1: Como reconciliar a duplicação Pix/DDA — **B1 (Strategy fino)**

Mantemos as interfaces `ContratacaoService`/`CancelamentoService` e as classes `PixAutoService`/`DdaAutoService`, mas elas ficam **finas**: declaram o `TipoProduto` suportado e delegam a um fluxo compartilhado. Unificamos:
- `PixAutoMapper` + `DdaAutoMapper` → **um** `AutorizacaoMapper` (eram idênticos).
- `PixAutoRepository` + `DdaAutoRepository` → **um** `AutorizacaoRepository` (mesma entidade/tabela).
- `Criar{Pix,Dda}AutoUseCase` → **um** `CriarAutorizacaoUseCase`; `Cancelar{Pix,Dda}AutoUseCase` → **um** `CancelarAutorizacaoUseCase`.

**Alternativas consideradas:**
- **B2 (colapsar o eixo produto):** remover as classes por produto; o orquestrador valida o `TipoProduto` via enum e a variação vive só nas Rules. Mais enxuto (~70% de remoção), porém apaga o seam Strategy que é a identidade arquitetural documentada e que dá valor quando um produto realmente divergir (ex.: alteração de contrato com regras próprias). Rejeitada por reduzir a aptidão da base como template e exigir recriar o seam depois.
- **Status quo (abstrair via herança de UseCase abstrato):** manter N use cases herdando de base abstrata. Reduz parte da duplicação mas mantém proliferação de classes por produto sem ganho real. Rejeitada.

B1 é o equilíbrio: remove o copy-paste (o defeito), mantém o ponto de extensão ("novo produto = classe fina + eventuais Rules") e prepara o terreno para alteração de contrato.

### Decisão 2: Onde fica o valor-limite por produto

Curto prazo: manter `ValorLimiteContrato` como `Rule`. Médio prazo (sub-decisão aberta): mover o limite para o enum `TipoProduto` (cada constante carrega seu limite), restaurando Open/Closed e eliminando o `switch` de String. Esta change **mantém na Rule** para limitar o escopo; a migração para o enum fica registrada como Open Question.

### Decisão 3: `tipoProduto` no request de criação — String vs enum

Padronizar para uma única representação. Decisão: manter `String` no DTO de borda (tolerante a desserialização), mas converter cedo para `TipoProduto` via `TipoProduto.obterTipoProdutoEnumPorNome(...)` (que já lança `BusinessException`), eliminando `TipoProduto.valueOf(...)` cru no mapper (que lança `IllegalArgumentException`). Assim o erro de produto inválido vira 422 consistente.

### Decisão 4: Estado de cancelamento sem DTO-carteiro

`CancelarAutorizacaoRequestDto` vira record imutável contendo só os campos de corpo (`codigoCanalCancelamento`, `idPessoaCancelamento`, `motivoCancelamento`). O `idAutorizacao` (path), o `tipoProduto` (header) e o `tipoProdutoDaAutorizacao` (lido do banco) passam como **parâmetros explícitos** para o validador — via um objeto de contexto/comando interno (ex.: `CancelamentoContext`) ou assinatura de método ampliada. A `Rule` de divergência de produto recebe os dois produtos a comparar, sem ler campos previamente injetados no request.

### Decisão 5: Ordem das fases para manter o build verde

Sequência: **(0)** bugs de correção isolados → **(1)** normalização de DTOs/contrato → **(2)** fusão da duplicação Pix/DDA → **(3)** limpeza de domínio/dead code. Bugs primeiro porque são baratos e cobertos por testes existentes; a fusão (mais invasiva, apaga/funde classes e testes) só depois que os contratos estabilizam.

## Risks / Trade-offs

- **[Acoplamento entre apps]** `arj-contratoquery` pode importar classes de `enabledproduct` do contratocommand → Mitigação: verificar imports cross-app antes da Fase 2; se houver, ajustar ou preservar nomes públicos.
- **[Mudança observável de `status`]** corrigir o status para refletir o enum pode quebrar consumidores que dependiam do valor antigo → Mitigação: documentar explicitamente; decidir com o time se "ativa" deve ser `4` (enum) ou se o enum deve ser ajustado para `1`. Registrado em Open Questions.
- **[Consolidação de testes]** fundir `{Pix,Dda}MapperTest` e `Criar/Cancelar{Pix,Dda}UseCaseTest` pode reduzir a contagem de testes → Mitigação: garantir que cada cenário coberto antes continue coberto nos testes compartilhados + testes finos de strategy; rodar `mvn test` e comparar.
- **[Transação em método único]** mover `@Transactional` para `execute()` muda o limite transacional do cancelamento → Mitigação: é exatamente o comportamento desejado (atomicidade do delete+insert); coberto por teste de rollback.
- **[Migração de DTO mutável→record]** chamadas que usavam setters (controller, testes) precisam migrar para construção imutável → Mitigação: contido na Fase 1, com `AutorizacaoControllerTest` como rede.

## Migration Plan

Sem migração de dados nem de schema (exceto a correção do nome de coluna `indicador_tipo_mensageria ` → `indicador_tipo_mensageria`, que alinha o mapeamento ao schema pretendido — validar contra o DDL real antes de aplicar). Deploy é substituição normal do artefato. Rollback: reverter o commit da fase correspondente; como cada fase mantém o build verde e os contratos REST, fases são reversíveis isoladamente.

## Open Questions

- O valor de "ativa" deve ser `4` (conforme enum `StatusAutorizacao.ATIVA`) ou o enum deve ser corrigido para alinhar com o `1` historicamente gravado? Decisão de negócio/compatibilidade.
- Mover o valor-limite por produto do `ValorLimiteContrato` (Rule com switch) para o enum `TipoProduto` nesta change ou deixar para a operação de alteração de contrato?
- O objeto de contexto de cancelamento deve ser um record interno (`CancelamentoContext`) ou basta ampliar as assinaturas de `Validator`/`Rule`? Definir na Fase 1.
