## Context

Em 2026-08-16/17, o `java-revisor` (modo `auditoria`) rodou uma vez por app, focado em coerência
arquitetural — não checklist completo de qualidade genérica. Encontrou 3 achados críticos (porta
falando vocabulário de camada externa: JPA em `contratoquery`, Avro em `eventos-consumer`, driver
Redis em `temporiza-autorizacao`) e 23 achados Importantes/Menores. Os críticos já foram corrigidos
e revalidados (cada app voltou a `APROVADO`) fora desta change — esta change cobre só o restante.

As cinco apps já passaram por migração hexagonal clássica completa (`openspec/specs/layout-hexagonal-classico/spec.md`)
e têm um requisito vivo de higiene de código morto
(`openspec/specs/higiene-codigo-morto/spec.md`). A maioria dos achados desta rodada é
**enforcement**, não requisito novo: código que já deveria estar em conformidade e não está. Dois
achados (domínio mutado por fora, DTO expondo modelo de domínio) apontam uma lacuna real na spec —
a regra existia em espírito, mas nunca foi escrita como requisito verificável.

## Goals / Non-Goals

**Goals:**
- Fechar os 23 achados Importantes/Menores, um por um, sem alterar comportamento observável (rota,
  contrato de mensagem, shape de resposta, configuração externa).
- Formalizar em `layout-hexagonal-classico` os dois requisitos que a auditoria expôs como reais,
  mas não escritos.
- Deixar rastro no `CLAUDE.md`/`AGENTS.md` de cada app onde o achado era, na prática, doc
  desatualizada.

**Non-Goals:**
- MDC/`traceId` e logging estruturado JSON — dívida transversal às 5 apps, fora desta change (ver
  proposal.md, "Fora de escopo"). Não introduzir aqui uma correção parcial (ex.: só no
  `autorizacaostatus-producer`) que crie inconsistência nova entre apps.
- Renomear o tipo Avro gerado para resolver a colisão de nome com `domain/model/EventoAutorizacao`
  no `autorizacaostatus-producer` — mexe em `subject` do Schema Registry, risco desproporcional ao
  ganho estético; registrado como Open Question, não como tarefa.
- Qualquer achado que a auditoria já classificou como dívida consciente e documentada (ex.:
  `application/usecase` de `temporiza-autorizacao` importando `TemporizacaoProperties`) permanece
  intocado.

## Decisions

### D1 — Comportamento de domínio via métodos nomeados pela ação de negócio, não setters genéricos

`Autorizacao.aprovar()`/`rejeitarPeloPagador()`/`expirarJornada1()`/`cancelar(Cancelamento)` cada
um encapsula o par (status, motivo) que hoje `DecidirAutorizacaoService`/`CancelarAutorizacaoService`
montam à mão. Alternativa descartada: um único `Autorizacao.transicionar(StatusAutorizacao,
MotivoStatusAutorizacao)` genérico — rejeitado porque devolveria ao `application` a responsabilidade
de saber qual motivo acompanha qual status, que é exatamente o conhecimento que deveria ficar
encapsulado no domínio (e é o par que os quatro métodos nomeados já tornam impossível de errar).

### D2 — DTO de resposta nunca embute tipo de `domain/model`

Regra nova em `layout-hexagonal-classico`: um campo de DTO de resposta (`infrastructure/web` ou
`infrastructure/messaging`) que hoje é `private Cancelamento cancelamento` (tipo de domínio) precisa
virar um DTO próprio (`CancelamentoResponseDto`) com mapeamento explícito. A alternativa de anotar o
tipo de domínio com `@JsonIgnoreProperties`/Jackson para moldar a serialização foi descartada: ela
mancharia `domain/model` com anotação de biblioteca de serialização, violando a regra de dependência
já existente ("nenhuma classe de domain importa Jackson").

### D3 — `TipoProduto` tipado desde o controller nas 3 rotas de escrita do contratocommand

`CriarAutorizacaoCommand.tipoProduto` passa de `String` para `TipoProduto`, resolvido no controller
(`TipoProduto.obterTipoProdutoEnumPorNome`), igual às rotas de cancelar/decidir já fazem. Produto
desconhecido continua rejeitado — só muda o ponto em que a resolução falha (controller, antes de
chegar ao comando) e o formato do erro correspondente permanece o mesmo (`BusinessException` → 422),
porque `obterTipoProdutoEnumPorNome` já lança essa exceção hoje.

### D4 — Conversores JPA de dado corrompido lançam `ApplicationException`, não `BusinessException`

No `contratoquery`, um código de produto/jornada não mapeável vindo do banco é defeito de dado, não
de request do cliente — `BusinessException` (422) hoje devolve ao cliente um erro que sugere
"seu pedido está errado", quando na verdade é o servidor que tem uma linha inconsistente.
`ApplicationException` (500, resposta genérica, log completo no servidor) é o mapeamento correto e
já existe no catálogo de exceções da app.

### D5 — `StatusAutorizacaoConverter` fecha a assimetria dos 3 `@Convert` do contratoquery

`tipoProduto` e `tipoJornada` já são convertidos via `@Convert` (Integer↔enum na fronteira JPA);
`status` era a exceção, resolvida tarde demais (na borda web, duplicada em dois DTOs, sem catch).
Unificar sob o mesmo padrão elimina a duplicação e move a falha de "corpo de resposta explode" para
"conversão na leitura falha com stacktrace completo no log" — mesmo raciocínio do D4.

### D6 — Classificação de erro mais estrita nos listeners não muda o destino final de uma mensagem já classificada corretamente hoje

Em `autorizacaostatus-producer` (SQS) e `eventos-consumer`/`temporiza-autorizacao` (Kafka/stream), a
correção estreita o `catch`/adiciona classificação explícita — mas o desfecho de uma mensagem que já
era corretamente retryable ou não-retryable **não muda**. O que muda é o caso hoje mal-classificado
por header ausente/falha inesperada, que passa a cair no caminho retryable em vez de ser descartado
ou preso silenciosamente. Não requer alteração de `visibility_timeout`/`maxReceiveCount`/DLQ.

### D7 — `PendenciasSchedulerReivindicador` muda de pacote, não de comportamento

Mover `infrastructure/messaging/PendenciasSchedulerReivindicador.java` para
`infrastructure/scheduler/` é puramente mecânico (a classe já é `@Scheduled`, a convenção já está
escrita no `CLAUDE.md` do app). Sem mudança de import externo — nada fora do próprio pacote a
referencia por caminho completo.

### D8 — `ZoneOffset.UTC` explícito no cálculo de vencimento do temporiza-autorizacao

Troca `ZoneId.systemDefault()` por `ZoneOffset.UTC` em `AgendarExpiracaoService`. Isso é
tecnicamente uma mudança de comportamento **se** o host já rodava num fuso diferente de UTC — mas
os `Dockerfile`s dos 5 apps não fixam `TZ`, e a imagem base (`eclipse-temurin:25-jre-alpine`)
assume UTC por padrão; tratado como correção de bug latente, não como migração de dado, porque não
há prazo em voo cujo cálculo dependa de fuso não-UTC hoje.

## Risks / Trade-offs

- **[Risco] `Autorizacao.aprovar()`/`cancelar()`/etc. redistribuem lógica hoje só em
  `application`, podendo mudar comportamento por descuido de tradução (motivo errado associado a um
  status).** → Mitigação: a task correspondente exige rodar a suíte de testes existente do
  `contratocommand` (que já cobre os três fluxos) antes/depois da refatoração, comparando
  `status`+`motivoStatus` persistidos, não só ausência de exceção.
- **[Risco] Mover `status` do contratoquery para `@Convert` pode expor um valor de banco hoje
  silenciosamente tolerado (`IllegalArgumentException` engolida em algum ponto não mapeado).** →
  Mitigação: rodar contra o Postgres local com a massa sintética existente
  (`infra/local/postgres/gerar-massa-sintetica-representativa.sql`) antes de considerar a task
  concluída.
- **[Trade-off] Não corrigir MDC/logging estruturado nesta change deixa a auditoria parcialmente
  aberta.** → Aceito deliberadamente (Non-Goals) — abrir como change própria evita que uma decisão
  de padronização de frota (formato de log, biblioteca de correlação) seja tomada apressada dentro
  de uma change de correção pontual.

## Migration Plan

Sem migração de dado ou infraestrutura. Ordem sugerida em `tasks.md`: por app, do achado que menos
depende de infra externa para o que mais depende (compilação → teste unitário → teste de
integração), para permitir revisão incremental sem exigir Postgres/Floci/Kafka/Valkey no ar o tempo
inteiro. Nenhum rollback dedicado: cada task é uma mudança de código isolada, reversível por
`git revert` normal se algo quebrar em revisão.

## Open Questions

- Renomear o record Avro gerado (`EventoAutorizacao`) no `autorizacaostatus-producer`/
  `eventos-consumer` para eliminar a colisão de nome com `domain/model/EventoAutorizacao` — decisão
  adiada; requer confirmar que o `name` do `.avsc` não é parte do `subject` no Schema Registry antes
  de considerar.
- Se/quando a change de MDC + logging estruturado for aberta, ela deve decidir se `traceId` vem do
  `messageId` do SQS, de um header propagado desde o `contratocommand`, ou de um ID gerado no
  primeiro ponto de entrada — está fora do escopo decidir isso aqui.
