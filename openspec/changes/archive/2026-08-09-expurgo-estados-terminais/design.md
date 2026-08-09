## Context

`CancelarAutorizacaoUseCase.transferirParaNovaParticao` é hoje o único lugar do
`arj-contratocommand` que move uma `Autorizacao` da partição de vigência (calculada a
partir de `dataFimVigencia`, default `9999-12-31`) para a partição de expurgo
(`ControleExpurgoAutorizacao.obterParticaoExpurgoWrite`, delete+insert na mesma
`@Transactional`). A lógica está acoplada ao use case de cancelamento: usa
`AutorizacaoRepository` e `EntityManager` diretamente, sem abstração compartilhada.

Levantamento do estado atual da máquina de estados (`StatusAutorizacao.TRANSICOES`):
- `CANCELADA`, `REJEITADA`, `EXPIRADA` e `FINALIZADA` são terminais (sem arestas de saída).
- Hoje só **dois** casos de uso escrevem em estados terminais: `CancelarAutorizacaoUseCase`
  (→ `CANCELADA`) e `DecidirAutorizacaoUseCase` (→ `ATIVA` ou `REJEITADA`, este último tanto
  para rejeição do pagador quanto para timeout da jornada 1 — `AcaoDecisao.EXPIRAR` também
  grava status `REJEITADA`, não `EXPIRADA`). Nenhum caso de uso hoje leva uma autorização a
  `EXPIRADA` ou `FINALIZADA` — são arestas do grafo sem implementação, reservadas para fluxos
  futuros (jornada 2, PENDENTE_ACEITE etc.).
- Logo, o expurgo que falta implementar agora é apenas o caminho `RECEBIDA → REJEITADA` via
  `DecidirAutorizacaoUseCase` (`REJEITAR` e `EXPIRAR`). `EXPIRADA` e `FINALIZADA` ficam fora
  do escopo até existir um caso de uso real que os produza.
- Ambos os use cases já setam `dataHoraUltimaAtualizacao = LocalDateTime.now()` no momento
  exato da transição — inclusive `CancelarAutorizacaoUseCase`, que atribui o mesmo instante
  (`dataHoraCancelamento`) a `dataHoraUltimaAtualizacao` (linha 60). Ou seja, já existe um
  campo genérico, sempre preenchido, com o instante de qualquer transição de status —
  incluindo as terminais.

## Goals / Non-Goals

**Goals:**
- Extrair `transferirParaNovaParticao` de `CancelarAutorizacaoUseCase` para um serviço
  compartilhado em `application/` (mesmo nível de `AutorizacaoRepository`/`AutorizacaoMapper`
  — não é feature específica).
- Fazer `DecidirAutorizacaoUseCase` chamar esse serviço quando a decisão resulta em
  `REJEITADA` (ações `REJEITAR` e `EXPIRAR`), usando `dataHoraUltimaAtualizacao` como data de
  referência do expurgo — eliminando o uso de `dataFimVigencia` (`9999-12-31`) como partição
  de uma autorização já resolvida.
- Preservar o comportamento observável de `CancelarAutorizacaoUseCase` (mesma partição de
  destino, mesmo padrão delete+insert, mesmo evento publicado) — é refactor, não mudança de
  regra de negócio para o fluxo de cancelamento.

**Non-Goals:**
- Implementar os fluxos que levam a `EXPIRADA` ou `FINALIZADA` — não existem hoje; o serviço
  compartilhado é desenhado para ser reutilizável por eles quando existirem, mas nenhum caso
  de uso novo é criado nesta mudança.
- Backfill de autorizações já `REJEITADA`/`EXPIRADA` antes desta mudança (ficam na partição de
  vigência `9999-12-31` até uma migração dedicada — ver Open Questions).
- Mudar o grafo de `StatusAutorizacao` ou os motivos de `MotivoStatusAutorizacao`.
- Criar nova capability formal antes da fase de specs — a decisão de nomenclatura
  (`maquina-estados-autorizacao` modificada vs. `expurgo-estados-terminais` nova) é resolvida
  no artefato `specs/`, não aqui.

## Decisions

**1. Onde vive o serviço compartilhado**: `application/ExpurgoAutorizacaoService`
(raiz de `application/`, `@Service`, ao lado de `AutorizacaoRepository`/`AutorizacaoMapper`),
não em `domain/utilities/`. Motivo: a operação depende de `AutorizacaoRepository` e
`EntityManager` (delete+flush+detach+save) — não é lógica pura de domínio como
`ControleExpurgoAutorizacao` (que permanece intocado e continua sendo chamado internamente
para calcular a partição de destino). Alternativa considerada: método estático em
`ControleExpurgoAutorizacao` — rejeitada porque essa classe é testada como lógica pura, sem
Spring, e não deve ganhar dependência de `EntityManager`/`Repository`.

**2. Assinatura do serviço**:
```java
Autorizacao transferirParaExpurgo(Autorizacao autorizacao, LocalDate dataReferenciaExpurgo)
```
Recebe a data de referência já resolvida pelo chamador (não decide internamente qual campo
usar) — mantém o serviço agnóstico ao use case de origem. Corpo é o mesmo algoritmo hoje em
`transferirParaNovaParticao` (delete → flush → detach → ajusta partição do `@EmbeddedId` →
save), movido sem alteração de lógica.

**3. Data de referência = `dataHoraUltimaAtualizacao`, não um campo novo**: para
`CancelarAutorizacaoUseCase`, `dataHoraUltimaAtualizacao` já recebe o mesmo instante de
`dataHoraCancelamento` (comportamento inalterado). Para `DecidirAutorizacaoUseCase`,
`dataHoraUltimaAtualizacao` já é setada antes do `save()` — só falta chamar o serviço de
expurgo com esse valor antes de persistir. Alternativa considerada: criar
`data_hora_transicao_terminal` dedicada — rejeitada por YAGNI: o campo genérico já existe, já
é preenchido em toda transição de status, e reaproveitá-lo evita migração de schema.

**4. Ordem das operações em `DecidirAutorizacaoUseCase`**: hoje o use case faz
`aplicarDecisao` → `setDataHoraUltimaAtualizacao` → `save`. Passa a ser: `aplicarDecisao` →
`setDataHoraUltimaAtualizacao` → **se o status resultante for terminal (`REJEITADA`)**,
chama `expurgoService.transferirParaExpurgo(...)` no lugar do `save()` direto (o serviço já
faz o save internamente); senão (caso `APROVAR` → `ATIVA`, não terminal), mantém `save()`
direto como hoje. A checagem de "é terminal" usa o mesmo status já calculado por
`aplicarDecisao`, sem nova consulta ao grafo.

## Risks / Trade-offs

- [Risco] Refactor em `CancelarAutorizacaoUseCase` pode introduzir regressão sutil no
  delete+insert (é a parte mais delicada do código, com comentário explícito sobre
  `ObjectDeletedException`) → Mitigação: mover o método literalmente, sem reescrevê-lo, e
  rodar `CancelarAutorizacaoUseCaseTest` + `ControleExpurgoAutorizacaoTest` antes/depois
  (regra de refatoração do time, ver `refatorador-java`).
- [Risco] `DecidirAutorizacaoUseCase` ganha uma segunda transferência de partição
  (delete+insert) na mesma transação onde antes só havia `save()` — aumenta o tempo de lock
  na tabela particionada → Mitigação: comportamento já validado em produção pelo fluxo de
  cancelamento; sem indício de problema de performance conhecido.
- [Trade-off] Autorizações `REJEITADA`/`EXPIRADA` anteriores a esta mudança continuam presas
  na partição `9999-12-31` — aceito como dívida remanescente, não bloqueante (ver proposal.md
  "Não bloqueante").

## Migration Plan

- Sem migração de schema (reaproveita `dataHoraUltimaAtualizacao`, já existente e `NOT NULL`
  seguindo o padrão do restante da entidade).
- Deploy é um refactor + uma chamada nova em `DecidirAutorizacaoUseCase` — sem coordenação
  com outras apps do monorepo (não muda `AutorizacaoEventoPayload`, não muda o `.avsc`, não
  muda contrato do endpoint `/decisao`).
- Rollback: reverter o deploy é suficiente — nenhuma migração de dado irreversível é
  executada por este change.
- Backfill de dados já rejeitados/expirados fica como **decisão adiada** (ver Open
  Questions) — se necessário, será um change separado (script/migração one-off), não faz
  parte da entrega deste change.

## Open Questions

- Backfill é necessário agora ou pode esperar o próximo ciclo de expurgo natural (novas
  ocorrências)? Proposal.md já sinaliza que não é bloqueante — decisão de negócio, não
  técnica, fica para quem prioriza o backlog de dívida técnica.
- Quando os fluxos de `EXPIRADA`/`FINALIZADA` forem implementados (fora de escopo aqui), eles
  devem reusar `ExpurgoAutorizacaoService` da mesma forma — vale registrar esse acoplamento
  esperado na doc do serviço para quem for implementá-los.
