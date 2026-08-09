## Why

`CancelarAutorizacaoUseCase` transfere a autorização para a partição de expurgo
(`ControleExpurgoAutorizacao.obterParticaoExpurgoWrite`, delete+insert na mesma transação)
quando ela vira `CANCELADA`. Os demais estados terminais — `REJEITADA`, `EXPIRADA` e
`FINALIZADA` — **não** passam por essa transferência: a lógica está implementada apenas
dentro do use case de cancelamento, não em um serviço de domínio compartilhado.

Como `dataFimVigencia` tem default `9999-12-31` quando não informado (`Autorizacao.
inicializaCriacao()`), uma autorização que termina rejeitada ou expirada permanece
indefinidamente na partição derivada dessa data — nunca migra para expurgo, nunca é
elegível a drop de partição. Isso foi identificado durante a mudança
`temporizacao-jornada-01-pix-auto` (que introduziu a rota de decisão, capaz de levar uma
autorização de `RECEBIDA` a `REJEITADA`) e deliberadamente deixado fora daquele escopo —
mexer no fluxo de expurgo, hoje estável, não deveria entrar de carona numa mudança sobre
temporização.

## What Changes

- Extrair a lógica de transferência para partição de expurgo de
  `CancelarAutorizacaoUseCase.transferirParaNovaParticao` para um serviço de domínio
  compartilhado, usado por **todo** use case que leva uma autorização a um estado
  terminal (`CancelarAutorizacaoUseCase`, `DecidirAutorizacaoUseCase` nas transições para
  `REJEITADA`/`ATIVA`→...→`FINALIZADA` quando existir).
- Decidir a data de referência do expurgo para os estados que não têm uma "data de
  cancelamento" natural (rejeição e expiração não têm campo equivalente a
  `data_hora_cancelamento` hoje).
- Modified capability: `maquina-estados-autorizacao` ou nova capability
  `expurgo-estados-terminais` — a definir na fase de design.

## Impact

**Código**: `arj-contratocommand` (`application/cancelamento/CancelarAutorizacaoUseCase`,
`application/decisao/DecidirAutorizacaoUseCase`, possível novo serviço de domínio
compartilhado em `domain/utilities/` ou `application/`).

**Dados**: autorizações já rejeitadas/expiradas antes desta mudança permanecem na partição
de vigência — decidir se há necessidade de uma migração de backfill ou se apenas novas
ocorrências passam a expurgar corretamente.

**Não bloqueante**: nenhuma operação de negócio depende disso hoje; é uma dívida de
manutenção de longo prazo (crescimento não controlado da partição de vigência).
