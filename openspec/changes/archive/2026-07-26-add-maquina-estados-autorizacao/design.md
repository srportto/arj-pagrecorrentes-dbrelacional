# Design: add-maquina-estados-autorizacao

## Context

O ciclo de vida da autorização tem 8 estados (códigos 1–8) com transições definidas em diagrama, mas o código não conhece o grafo. `StatusAutorizacao` existe como cópia idêntica em `contratocommand` e `contratoquery` (só valores + lookup por id); nas apps de eventos o status trafega como `int` no JSON/Avro. O `tipoEvento` é fixado no ponto de chamada (`CRIACAO`/`CANCELAMENTO`) e repassado como etiqueta opaca (attribute SNS/SQS → header Kafka → log), duplicando informação que já existe no campo `status` do dado.

Jurisprudência do monorepo relevante: contratos entre apps são **espelhos manuais** (payload JSON e `.avsc` — sem módulo compartilhado); decisão registrada em `openspec/changes/archive/2026-07-25-add-eventos-autorizacao-sns-sqs/design.md`.

Máquina de estados (correção sobre o diagrama original: `ATIVA` **não** transiciona para `EXPIRADA`):

```
RECEBIDA(1) ──▶ PENDENTE_ACEITE(2) ──▶ EM_PROCESSO_ATIVACAO(3) ──▶ ATIVA(4) ──▶ FINALIZADA(8)
    │  │              │    │                  │      │               │  └─────▶ CANCELADA(5)
    │  └──────────────┼────┼──────────────────┘      │               │
    │                 │    └──▶ EXPIRADA(7) ◀────────┘               │
    └─────────────────┴───────▶ REJEITADA(6) ◀───────────────────────┘
```

| Origem | Destinos permitidos |
|---|---|
| RECEBIDA(1) | PENDENTE_ACEITE, EM_PROCESSO_ATIVACAO, REJEITADA |
| PENDENTE_ACEITE(2) | EM_PROCESSO_ATIVACAO, REJEITADA, EXPIRADA |
| EM_PROCESSO_ATIVACAO(3) | ATIVA, REJEITADA, EXPIRADA |
| ATIVA(4) | CANCELADA, FINALIZADA, REJEITADA |
| CANCELADA(5), REJEITADA(6), EXPIRADA(7), FINALIZADA(8) | — (terminais) |

## Goals / Non-Goals

**Goals:**

- Formalizar a máquina de estados no enum `StatusAutorizacao` das 4 apps, com `podeTransicionarPara(destino)`.
- Tornar o `status` a única fonte de verdade do `tipoEvento` em toda a cadeia SNS → SQS → Kafka (`TipoEventoAutorizacao.porStatus`).
- Mover os `.avsc` para `src/main/resources/avro/` nas duas apps Kafka.
- Resumir javadocs/comentários de linha extensos no código Java.

**Non-Goals:**

- Não usar `podeTransicionarPara` para validar operações de negócio nesta fase (criação/cancelamento seguem gravando `ATIVA`/`CANCELADA` como hoje) — o método nasce pronto para uso futuro.
- Não criar módulo Maven compartilhado de enums/schemas — os espelhos manuais continuam.
- Não amarrar `MotivoStatusAutorizacao` → `StatusAutorizacao` (fio registrado, fora de escopo).
- Não resumir docs (`CLAUDE.md`/`AGENTS.md`/`README.md`) — apenas correções pontuais de caminho/contrato.
- Não adicionar filter policy SNS nem filtro por header no Kafka.

## Decisions

### D1 — Grafo de transições dentro do próprio enum, via `EnumSet` estático

`StatusAutorizacao` ganha `podeTransicionarPara(StatusAutorizacao destino)` e o grafo vive em um `Map<StatusAutorizacao, Set<StatusAutorizacao>>` estático (ou `EnumSet` por constante, resolvido lazy para evitar forward reference). Estados terminais retornam conjunto vazio.

*Alternativa rejeitada*: classe `MaquinaEstadosAutorizacao` separada — mais um tipo para espelhar em 4 apps sem ganho; o enum é o lugar natural do conhecimento.

*Replicação*: as 4 apps recebem o **mesmo** enum (pacotes próprios), seguindo a jurisprudência de espelhos manuais. Nas apps de eventos ele entra em `application/eventos/` (elas deliberadamente não têm camada `domain/`).

### D2 — `TipoEventoAutorizacao` 1:1 com o status, derivado por `porStatus`

Novo enum de 8 valores, cada um amarrado ao `StatusAutorizacao` correspondente:

| Status | TipoEventoAutorizacao |
|---|---|
| RECEBIDA | RECEPCAO |
| PENDENTE_ACEITE | PENDENCIA_ACEITE |
| EM_PROCESSO_ATIVACAO | INICIO_ATIVACAO |
| ATIVA | ATIVACAO |
| CANCELADA | CANCELAMENTO |
| REJEITADA | REJEICAO |
| EXPIRADA | EXPIRACAO |
| FINALIZADA | FINALIZACAO |

Fábrica `porStatus(long statusId)` (delega a `StatusAutorizacao.obterStatusEnumPorIdStatus` + mapeamento) lança exceção para status desconhecido. `CRIACAO` deixa de existir; o evento de criação (que persiste `ATIVA`) sai como `ATIVACAO`.

*Alternativa rejeitada*: manter `CRIACAO` como alias — dois nomes para o mesmo fato quebram a bijeção status↔tipo que motiva a mudança.

### D3 — Status manda; etiqueta vira derivada (opção "b" da exploração)

Cada salto **calcula** o tipo a partir do `status` presente no dado, e a etiqueta continua viajando apenas como metadado de filtragem:

```
contratocommand  → attribute SNS  = porStatus(autorizacao.getStatus())
producer (ponte) → header Kafka   = porStatus(payload.status())      [ignora o attribute SQS]
consumer         → log            = porStatus(evento.getStatus())    [ignora o header]
```

Consequências:
- `AutorizacaoPersistidaEvent` perde o campo `tipo`; `CriarAutorizacaoUseCase`/`CancelarAutorizacaoUseCase` publicam só a entidade.
- `SqsEventoAutorizacaoListener` para de solicitar `messageAttributeNames=tipoEvento` e de repassar o valor ao use case; `ProcessarEventoAutorizacaoUseCase.processar(body)` deriva o tipo após desserializar. Status inválido no payload → `EventoAutorizacaoInvalidoException` (não-retryable, mesma classificação já existente).
- No consumer, o header recebido deixa de ser argumento do use case; o log registra o tipo derivado do Avro.

*Alternativas rejeitadas*: (a) confiar na etiqueta — mantém a dupla fonte de verdade e deixa divergência passar; (c) derivar **e** validar contra a etiqueta — proteção extra que não paga o código adicional numa cadeia onde todos os publicadores estão neste monorepo.

### D4 — `.avsc` em `src/main/resources/avro/`, plugin reapontado

Mover `EventoAutorizacao.avsc` para `src/main/resources/avro/` em `autorizacaostatus-producer` e `eventos-consumer`, trocando o `sourceDirectory` do `avro-maven-plugin` para `${project.basedir}/src/main/resources/avro`. Efeito colateral aceito: o `.avsc` passa a ser empacotado no JAR (inofensivo; o contrato de runtime segue sendo o Schema Registry). Desvio consciente da convenção `src/main/avro` do plugin, a pedido.

### D5 — Concisão de comentários: 1–3 linhas, detalhe fica nas docs

Javadocs de classe/método ficam com o essencial (o quê + o porquê não óbvio, 1–3 linhas); procedimento operacional, classificação de falhas e trade-offs detalhados já vivem em `CLAUDE.md`/`design.md` das apps. Alvos: `ReversibleUUIDv7` (23/62 linhas), `AutorizacaoRepository` (19/44) e os javadocs densos das classes de eventos das 4 apps. Comentários que explicam um porquê não óbvio e não têm outro lar **permanecem** (requirement existente de `higiene-comentarios-codigo`).

## Risks / Trade-offs

- **[Breaking no contrato do attribute/header]** `tipoEvento=CRIACAO` deixa de existir e 6 valores novos passam a ser possíveis → consumidores atuais só logam (risco baixo); a mudança é documentada nos espelhos de doc e o valor antigo não é emitido por nenhuma app após o deploy conjunto da cadeia.
- **[Deploy fora de ordem]** producer antigo repassando attribute e command novo publicando `ATIVACAO` (ou vice-versa) → como a ponte nova ignora o attribute e deriva do `status`, qualquer ordem de deploy produz header coerente; a janela de inconsistência se limita ao valor do attribute SNS, que nada consome.
- **[Espelhos manuais × 4]** o enum replicado pode divergir entre apps com o tempo → mesma mitigação já usada para payload/`.avsc`: nota de espelho no javadoc curto + checklist de commit nas docs das apps.
- **[Grafo embutido em app que só loga]** `podeTransicionarPara` sem uso em producer/consumer/query é peso morto até segunda ordem → aceito por decisão explícita do usuário ("mesmo que não tenha uso nesse momento"); custo é pequeno e o enum é o mesmo espelho.
- **[.avsc no JAR]** schema empacotado pode sugerir que o runtime lê dele → não lê; registrar no README das apps que o arquivo é insumo de build/documentação.

## Migration Plan

1. Implementar por app na ordem `contratocommand` → `contratoquery` → `producer` → `consumer` (a ponte nova já deriva do status independentemente do que o command emite, então qualquer ordem de deploy é segura).
2. Rollback: reverter o commit da app afetada; não há migração de dados nem de schema Avro (o `.avsc` não muda de conteúdo, só de pasta — mesmo schema no Registry).

## Open Questions

- Nenhuma — decisões fechadas na exploração de 26/07/2026 (transições a partir de `ATIVA` = {CANCELADA, FINALIZADA, REJEITADA}; `CRIACAO` → `ATIVACAO`; opção "b" de propagação; escopo de comentários restrito a código Java).
