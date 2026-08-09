## Context

`PIX_AUTO` nasce com status `RECEBIDA` desde a mudança `pix-auto-status-recebida-na-criacao`.
Não há saída desse estado: nenhuma rota permite ao cliente pagador decidir, e nada encerra a
espera. O fluxo atual termina no evento de recepção:

```
POST /api/autorizacoes (tipoJornada: SPI_J1, tipoProduto: PIX_AUTO)
  └─ Autorizacao.inicializaCriacao() → status RECEBIDA, motivo RECEPCAO_SPI_J1
  └─ commit → AutorizacaoPersistidaEvent → AutorizacaoEventoPublisher (AFTER_COMMIT)
        body  = representação da linha (chaves = colunas)
        attrs = { tipoEvento: "RECEPCAO" }        ← único attribute existente
                 ↓ SNS sns-estados-autorizacao (subscription sem filter policy)
        SQS-eventos-autorizacao → autorizacaostatus-producer → Kafka
```

Três restrições do código existente moldam o desenho:

1. **O grafo de `StatusAutorizacao` é contrato espelhado nas 4 apps** e a spec
   `maquina-estados-autorizacao` diz que as transições "SHALL ser exatamente" as
   declaradas. Mudá-lo é caro e propaga.
2. **A jornada não é persistida.** Ela existe só como parâmetro do `AutorizacaoMapper`, que
   a usa para derivar `motivo_status` e a descarta. Como `motivo_status` é sobrescrito a
   cada transição, a jornada de origem é **destruída** na primeira mudança de status.
3. **`MotivoStatusAutorizacao` existe apenas no `arj-contratocommand`** — não é espelhado.
   Acrescentar valores ali é local e barato, ao contrário de `StatusAutorizacao`.

## Goals / Non-Goals

**Goals:**

- Encerrar deterministicamente a espera de 10 minutos da jornada 1 do `PIX_AUTO`,
  sem perder eventos e sem depender de um único nó vivo.
- Dar ao cliente pagador um caminho de aprovação e de rejeição explícita.
- Tornar a jornada de origem um dado durável e filtrável, em vez de um efeito colateral
  recuperável por parsing.
- Não alterar o grafo de transições nem o enum `TipoEventoAutorizacao`.

**Non-Goals:**

- Etapa de confirmação do PSP recebedor (`PENDENTE_CONFIRMACAO_RECEBEDOR`,
  `PENDENTE_ACEITE`). O par aprovação-do-pagador → confirmação-do-recebedor do PIX
  Automático real fica para fase futura; aqui a aprovação leva direto a `ATIVA`.
- Temporização de `QRC_J2`/`J3`/`J4` e de `DDA_AUTO`.
- Transferência de estados terminais para a partição de expurgo (dívida registrada na
  proposta).
- Autenticação/autorização diferenciada entre canal do cliente e canal de sistema.

## Decisions

### 1. A espera fica em `RECEBIDA`; o timeout grava `REJEITADA`

**Escolhido:** a autorização permanece em `RECEBIDA` durante os 10 minutos e o timeout a
leva a `REJEITADA(6)` com motivo novo `REJEITADA_SISTEMA_TIMEOUT_J1`.

**Por quê:** `RECEBIDA → REJEITADA` já existe no grafo. É a única aresta terminal saindo de
`RECEBIDA`, e "rejeitada sistemicamente" é exatamente a semântica pedida.

**Alternativa descartada — usar `PENDENTE_ACEITE` como estado de espera:** seria mais fiel
aos enums (`PENDENTE_APROVACAO_PAGADOR` diz "válido apenas para jornada 1", e
`PENDENTE_ACEITE → EXPIRADA` já existe). Mas exigiria ou reabrir a spec
`status-inicial-por-produto`, decidida recentemente, ou inventar um ator para promover
`RECEBIDA → PENDENTE_ACEITE` — mais uma transição e mais um evento SNS sem ganho de
negócio. `PENDENTE_ACEITE` e `PENDENTE_APROVACAO_PAGADOR` ficam reservados para a etapa de
confirmação do recebedor, quando ela existir.

**Alternativa descartada — timeout gravar `EXPIRADA(7)`:** semanticamente melhor
(`EXPIRADA_01` descreve o fato com precisão), mas `RECEBIDA → EXPIRADA` não existe no grafo
e adicioná-la propagaria para as 4 apps e para a spec da máquina de estados.

**Alternativa descartada — reusar o motivo `EXPIRADA_01`:** gravar `status=REJEITADA` com
`motivo_status="EXPIRADA_01"` é dissonância que custa caro em suporte. Um valor novo no
enum, que não é espelhado, é mais barato que a confusão permanente.

### 2. Aprovação percorre os dois saltos na mesma transação

`RECEBIDA → ATIVA` não existe. `DecidirAutorizacaoUseCase`, com `APROVAR`, valida
`RECEBIDA → EM_PROCESSO_ATIVACAO` e `EM_PROCESSO_ATIVACAO → ATIVA` via
`podeTransicionarPara()` e grava o estado final `ATIVA` com motivo
`AUTORIZACAO_ACEITA_POR_TODOS`. Publica **um** evento (`ATIVACAO`), coerente com o
requisito "Um evento lógico por operação" já existente em `publicacao-eventos-autorizacao`.
`EM_PROCESSO_ATIVACAO` não é observável de fora; quando a confirmação do recebedor entrar,
o segundo salto se separa naturalmente.

### 3. Uma rota, ação no corpo, erro de negócio como sinal de idempotência

```
PATCH /api/autorizacoes/{idAutorizacao}/decisao
Header: tipoProduto                       ← simetria com /cancelar
Body:   { "acao": "APROVAR" | "REJEITAR" | "EXPIRAR", ... }
```

Segue o padrão já estabelecido: record de contexto imutável → use case `@Transactional` →
validator + rules plugáveis.

O ponto crítico é que **o chamador da expiração é automatizado e at-least-once**: ele
precisa distinguir "já resolvida, não faça nada" de "falhei, tente de novo". O contrato usa
o `ApiExceptionHandler` existente, sem inventar códigos:

| Situação | HTTP | Ação do temporizador |
|---|---|---|
| expiração aplicada | 200 | confirma (`XACK`) |
| status já não é `RECEBIDA` | 422 (`BusinessException`) | confirma — é o caso do cliente ter decidido antes |
| autorização não encontrada | 422 | confirma |
| command indisponível / 5xx / timeout | — | **não** confirma; fica no PEL |

**Alternativa descartada — três rotas separadas** (`/aprovar`, `/rejeitar`, `/expirar`):
triplicaria controller, contexto e validator para uma diferença que é um campo. Quando
entrar autenticação, `EXPIRAR` provavelmente se separa por ser canal de sistema — registrado
como dívida, não antecipado agora.

**Alternativa descartada — 200 com o estado atual em vez de 422:** transformaria "não
aplicável" em sucesso, escondendo do log de suporte o caso em que a expiração chegou tarde.

### 4. Jornada vira coluna, não attribute derivado

Para o SNS filtrar por jornada, o publisher precisa da jornada. Persistir é o caminho certo
por dois motivos independentes:

- A spec `publicacao-eventos-autorizacao` exige **"attribute sempre coerente com o body"**.
  Um attribute `tipoJornada` sem contrapartida no body contraria o invariante de frente.
- A jornada é um fato durável do negócio que hoje o sistema destrói na primeira transição
  de status. O filtro só revelou o buraco.

**Alternativa descartada — carregar a jornada no `AutorizacaoPersistidaEvent`:** custaria 3
arquivos e zero migração, mas o attribute existiria apenas em eventos de criação
(cancelamento e decisão não têm jornada em mãos), criando um canal de dado fora da linha —
exatamente o que o desenho do payload evitou ao proibir campo de tipo de evento no evento
interno.

**Alternativa descartada — filtro grosso no SNS + refino por `motivo_status` na app:**
funciona hoje, mas amarra o consumidor a um campo mutável e mantém o dado perdido.

Compatibilidade: a coluna é nullable (ou `NOT NULL DEFAULT 0` = desconhecida) para linhas
legadas, e o campo Avro entra como `["null","long"]` com `default: null` — compatível para
trás no Schema Registry, sem quebrar `eventos-consumer`.

### 5. Valkey: sorted set é o relógio, stream é a fila de trabalho

**O ponto que dita o desenho:** Redis/Valkey **não tem entrega com atraso em streams**.
Entrada de stream não tem TTL. O mecanismo que tem TTL — keyspace notification `expired` —
é pub/sub fire-and-forget: sem consumer group, sem ack, sem redelivery; ninguém escutando no
instante do vencimento significa evento perdido para sempre, e em cluster mode a notificação
sai apenas do nó dono da chave. Os dois não se combinam num só objeto.

```
Recepção (listener SQS)
  vencimento = data_hora_inclusao + 10min          ← do payload, NÃO now()+10min
  ZADD agenda:pixauto:j1 <vencimento> <idAutorizacao>
     → reprocesso do SQS não empurra o prazo para frente
     → ZADD com o mesmo member sobrescreve: dedup natural, sem chave de idempotência

Varredura (todos os pods, sem lock distribuído)
  script Lua atômico:
    ZRANGEBYSCORE agenda -inf <now> LIMIT 0 N
    para cada id: se ZREM retornar 1 → XADD stream:expiracoes * id <id>
     → só um pod obtém ZREM==1 por id. O ZREM É o lock.

Trabalho (worker)
  XREADGROUP GROUP temporizaautorizacao <consumer> COUNT n BLOCK 5000 STREAMS ... >
    → PATCH /decisao {EXPIRAR} → 2xx/422: XACK | 5xx/timeout: sem XACK
  XAUTOCLAIM periódico (min-idle-time ~2min) recupera PEL de pod morto

Persistência: appendonly yes / appendfsync everysec
```

O vocabulário pedido (`XADD`, `XREADGROUP`, `XACK`, PEL, reivindicação por outro pod, AOF)
fica todo preservado — a diferença é **quando** o `XADD` acontece: no vencimento, não na
recepção.

**Alternativa descartada — keyspace notification `expired` → `XADD`:** mais "event-driven"
na aparência, mas a notificação não é durável, o Redis expira de forma preguiçosa/ativa (o
disparo pode atrasar), e em cluster a notificação é por nó. Na prática exige um varredor de
segurança de qualquer forma — que é justamente a solução do sorted set, com um mecanismo
frágil a mais no caminho.

**Alternativa descartada — scheduler batendo no Postgres** (`WHERE status=RECEBIDA AND
data_hora_inclusao < now()-10min`): dispensaria infra nova, mas varre uma tabela
particionada **por conta** (900–999), não por tempo — todo tick tocaria todas as partições.

### 6. A aplicação temporizadora não acessa o banco

O caso de uso original previa ler a base antes de acionar o command. Essa leitura **não
elimina a corrida**: o cliente pode aprovar entre o `SELECT` e o `PATCH`, então o command
revalida sob transação de qualquer modo. A leitura seria otimização, não correção — e o
contrato de erro da decisão nº 3 já cobre o caso "já resolvida" de forma barata.

Sem ela, `temporiza-autorizacao` fica sem JPA, sem pool, sem conhecer o schema particionado
e sem espelhar `StatusAutorizacao` — o mesmo perfil de `autorizacaostatus-producer`.

Se a leitura prévia for reintroduzida, o caminho é `GET /api/autorizacoes/{id}` no
`arj-contratoquery` (8081), que já existe, e **não** uma segunda conexão JPA à tabela
particionada.

### 7. Filtro inteiramente declarativo no SNS

```java
// AutorizacaoEventoPublisher
"tipoEvento",  TipoEventoAutorizacao.porStatus(status).name()   // já existe
"tipoProduto", autorizacao.getTipoProduto().name()              // novo
"tipoJornada", jornadaDaLinha.name()                            // novo
```

```json
{ "tipoEvento": ["RECEPCAO"], "tipoProduto": ["PIX_AUTO"], "tipoJornada": ["SPI_J1"] }
```

A aplicação recebe apenas o que interessa; nenhum descarte em memória, nenhuma leitura de
`motivo_status` para desempatar jornada. A subscription existente
(`SQS-eventos-autorizacao`) continua sem filter policy e sem mudança de comportamento.

### 8. A checagem de idempotência exige status == RECEBIDA explícito, não só alcançabilidade no grafo

Descoberto durante a implementação: o grafo pré-existente de `StatusAutorizacao` já permite
`ATIVA → REJEITADA` (para outro fluxo de negócio, fora do escopo desta mudança). Se
`TransicaoValidaDecisao` validasse apenas "o status atual alcança `REJEITADA` no grafo", uma
expiração que chegasse atrasada — depois do cliente já ter aprovado — passaria pela validação
e rejeitaria uma autorização `ATIVA`, quebrando a idempotência que a decisão nº 3 promete. A
rule exige o status atual seja **exatamente** `RECEBIDA` antes de checar a transição alvo;
`podeTransicionarPara` funciona como defesa em profundidade, não como o único portão.

## Risks / Trade-offs

- **Precisão do prazo não é exata** → a varredura roda em intervalo fixo (~5s), então a
  expiração ocorre entre 10min e 10min+intervalo. Aceitável para o negócio; documentado no
  spec como janela, não como instante.
- **Command indisponível atrasa expirações** → as mensagens ficam no PEL do stream e são
  reivindicadas por `XAUTOCLAIM`; nada se perde, mas autorizações passam do prazo. Mitigado
  por alarme sobre o tamanho do PEL.
- **Aprovação antes do vencimento deixa lixo no agendamento** → o `ZADD` continua lá e
  dispara; o command responde 422 e o worker confirma. Optamos por isso em vez de fazer o
  `arj-contratocommand` remover a entrada (`ZREM`), o que o acoplaria ao Valkey.
- **Perda total do Valkey perde agendamentos em voo** → AOF `everysec` limita a janela a ~1s
  de escritas; um replay a partir do Kafka reconstrói a agenda se necessário. Não há
  reconciliação automática nesta fase.
- **Cinco espelhos manuais do schema** → `tipo_jornada` precisa entrar em 2 entidades JPA, 2
  payloads JSON e 2 `.avsc`. É o custo já conhecido de não haver módulo compartilhado; o
  checklist de tarefas cobre cada arquivo explicitamente.
- **Um valor novo em `MotivoStatusAutorizacao`** → o enum não é espelhado, mas
  `motivo_status` viaja como string no evento; consumidores que fizerem `valueOf` do lado de
  fora precisam tolerar valor desconhecido.
- **`EM_PROCESSO_ATIVACAO` nunca observável** → a aprovação escreve o estado final `ATIVA`
  numa transação só. Se auditoria exigir o estado intermediário persistido, o desenho
  precisa de duas transações e dois eventos.
- **Filter policy do SNS no emulador** → o Floci precisa suportar filter policy por message
  attribute. Se não suportar, o fallback local é subscription sem filtro + descarte na app,
  mantendo o filtro real apenas em AWS — a diferença fica isolada no Terraform.

## Migration Plan

1. **Schema primeiro**: adicionar `tipo_jornada` como nullable (ou `NOT NULL DEFAULT 0`),
   compatível com o código atual — nenhuma app quebra por ignorar a coluna.
2. **Command**: passar a gravar a coluna e a publicar os dois attributes novos. Consumidores
   existentes ignoram attributes desconhecidos; a subscription atual não muda.
3. **Espelhos**: payload JSON e `.avsc` com o campo nullable; registrar o schema novo antes
   de subir o producer.
4. **Rota de decisão**: entra sem consumidor, exercitável manualmente.
5. **Infra**: Valkey local e fila/subscription filtrada.
6. **Aplicação temporizadora** por último, quando todo o resto já está em pé.

**Rollback**: derrubar `temporiza-autorizacao` interrompe apenas as expirações automáticas —
nenhuma autorização é corrompida, elas voltam a ficar paradas em `RECEBIDA` como hoje. A
coluna, os attributes e a rota são aditivos e podem permanecer.

## Open Questions

- **Intervalo da varredura e tamanho do lote** (`~5s` / `N`) — a definir com o volume real
  esperado de J1; o spec fixa o comportamento, não os números.
- **`arj-contratoquery` expõe `tipoJornada` nos DTOs de resposta?** A coluna passa a existir;
  expor ou não é decisão de contrato de API que não bloqueia esta mudança.
- **Cluster mode no ElastiCache** — se habilitado, `agenda:*` e `stream:*` precisam de hash
  tag comum para caírem no mesmo slot. Definir junto com o dimensionamento do cluster.
