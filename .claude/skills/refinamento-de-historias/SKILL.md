---

name: refinamento-de-historias
description: "Refina demanda, requisito ou história bruta em especificação pronta para desenvolvimento (Definition of Ready) no contexto deste monorepo de autorizações de pagamento recorrente — INVEST, critérios de aceite observáveis em Dado/Quando/Então, roteamento por serviço impactado, interrogatório de idempotência/concorrência/espelhamento manual, e lacunas classificadas por prontidão (Bloqueia / Ajusta / Registra). Use ao receber demanda vaga, escrever ou criticar história de usuário, montar critério de aceite, preparar refinamento/planning, ou antes de abrir uma change OpenSpec. Uso: sessão principal ou `/refinamento-de-historias`; não carregar proativamente."
license: MIT
metadata:
  author: https://github.com/srportto/srportto
  version: "1.0.0"
  domain: requirements
  triggers: refinar, refinamento, história de usuário, user story, critério de aceite, definition of ready, INVEST, BDD, demanda, requisito, backlog, planning, fatiar história, isso está pronto pra desenvolver?
  role: refiner
  scope: requirements-refinement
  output-format: document
  related-skills: openspec-propose, openspec-explore, design-system-architecture, api-rest-design, arquitetura-limpa-java, mensageria-sqs-kafka, revisao-de-codigo-java
---
---

# Refinamento de Histórias

## Visão geral

Transforma demanda bruta — parágrafo do PO, print de chamado, bug report, requisito regulatório —
em especificação que um dev deste monorepo consegue implementar sem adivinhar nada. É a imagem
espelhada de `revisao-de-codigo-java` no outro extremo do ciclo: lá se classifica **defeito de
código** por severidade depois que ele existe; aqui se classifica **lacuna de história** por
prontidão antes que ele exista. O critério de gravidade é o mesmo nas duas pontas — o que quebra em
produção —, só muda o momento em que a pergunta é feita.

**Por que uma skill específica e não um template genérico de história:** refinamento genérico produz
história bonita e vazia. Neste domínio os riscos não são hipotéticos, são conhecidos e recorrentes:
a convenção de 422, o grafo de transições de status, chamadores que repetem (SNS/SQS são
at-least-once), schemas espelhados manualmente entre apps, particionamento temporal. Uma história
que não responde a esses pontos chega ao dev com um buraco — e o buraco vira decisão de última hora
tomada por quem estava com o teclado na mão.

**Quando NÃO usar:**

- Você já sabe o que construir e quer os artefatos formais (proposal/design/tasks) → `openspec-propose`.
- Você ainda não tem recorte e precisa investigar o problema → `openspec-explore`.
- O detalhe que falta é o desenho do contrato REST (paginação, versionamento, shape de erro) →
  `api-rest-design`.
- O código já existe e o que você quer é criticá-lo → `revisao-de-codigo-java`.

## Entrada

Qualquer formato. Se a entrada não disser em qual serviço a mudança cai, **descubra na etapa 2 antes
de escrever qualquer critério de aceite** — um critério escrito sem saber o serviço tende a assumir
o comportamento de um app que não é o app da história (é assim que aparece "retorna 404" numa rota
de escrita do `contratocommand`, que não tem 404).

## Fluxo de refinamento

1. **Enquadrar valor e ator** — quem pede, por quê, o que muda e para quem.
2. **Rotear pelos serviços impactados** — onde a mudança cai e o que cada serviço exige de resposta.
3. **Interrogar os eixos de risco** — contrato, estado, idempotência, dado, evento, observabilidade.
4. **Escrever critérios observáveis** — cada `Então` amarrado a um efeito verificável numa borda.
5. **Classificar as lacunas** — o que bloqueia, o que ajusta, o que fica registrado como débito.

> **Regra que atravessa as cinco etapas: nunca invente regra de negócio para fechar uma lacuna.**
> Uma resposta inventada desaparece dentro de uma história bem formatada e é lida como requisito
> aprovado; uma pergunta em aberto fica visível e alguém a responde. Quando faltar a informação,
> escreva a pergunta — é mais barato do que construir a coisa errada com confiança.

## Níveis de prontidão

| Nível | Definição | Efeito |
|---|---|---|
| **Bloqueia** | Lacuna que faz o time construir a coisa errada, corromper dado ou quebrar contrato existente — regra de negócio indefinida, transição de status não especificada, comportamento sob chamada repetida em aberto | História **não entra** em sprint até ser respondida |
| **Ajusta** | Lacuna que não impede começar, mas gera retrabalho previsível — nome de campo, texto de mensagem de erro, valor default, limite de paginação | Pode entrar; resolva antes do primeiro commit da história |
| **Registra** | Decisão consciente de adiar algo que a história tocou de raspão | Vira débito com **gatilho de revisão explícito** ("revisitar quando X acontecer"), não "depois" |

O nível é sobre a **lacuna**, não sobre o tamanho da tarefa. "Falta decidir o `motivo_status` da nova
transição" é uma linha de código e mesmo assim **Bloqueia**: o valor errado fica gravado no banco e
depois não há como distinguir de qual caminho a linha veio.

## Etapa 1 — Valor, ator e recorte

**O ator raramente é uma pessoa.** Neste domínio os chamadores são a empresa recebedora via API, o
`temporiza-autorizacao` disparando `PATCH /decisao` com `acao: EXPIRAR`, o
`autorizacaostatus-producer` consumindo da fila, a Lambda de expurgo. Escrever "Como usuário" quando
o chamador é uma máquina custa caro no critério de aceite: **máquina repete, pessoa não**. Nomeie o
chamador real e a história passa a exigir sozinha o cenário de chamada repetida.

Das seis letras de INVEST, duas mordem de verdade aqui — as outras quatro (Independent, Negotiable,
Valuable, Estimable) se resolvem quase sozinhas quando estas duas estão certas:

**Small — fatie por contrato, não por camada.** Um recorte por camada ("história 1: criar a entidade;
história 2: criar o endpoint") produz duas histórias das quais nenhuma é demonstrável e cuja soma é
o único entregável real. Fatie pelo contrato que muda:

- ❌ 1) entidade + migration, 2) use case, 3) controller
- ✅ 1) aceitar e persistir o campo novo (`contratocommand`), 2) devolvê-lo na consulta
  (`contratoquery`), 3) propagá-lo no evento (payload JSON + `.avsc` + consumer)

Cada fatia vale sozinha, é testável numa borda e pode ir para produção sem a próxima.

**Testable — o critério tem que poder falhar.** Se nenhum teste pode ficar vermelho por causa de um
`Então`, ele não é critério, é intenção. A etapa 4 trata disso.

> **Sinal de história grande demais:** se ela toca **três ou mais** dos seis serviços, ela não é
> Small. Fatie antes de refinar o resto — refinar em detalhe uma história que vai ser fatiada é
> trabalho jogado fora, porque o fatiamento reescreve os critérios.

## Etapa 2 — Roteamento por serviço

Descubra onde a mudança cai e responda a pergunta obrigatória do serviço. Enquanto ela estiver sem
resposta, a lacuna é **Bloqueia**.

| Serviço | O que ele impõe à história | Pergunta obrigatória |
|---|---|---|
| **contratocommand** (8080) — escrita | Convenção 422, grafo de transições, evento SNS pós-commit, partição de expurgo, lock otimista | Qual transição de status (de → para)? Qual `motivo_status`? Publica evento — com qual `tipoEvento`? O que acontece se a chamada chegar duas vezes? |
| **contratoquery** (8081) — leitura | Somente leitura (`DB_READ_ONLY=true`), cascata de partições, `status` exposto como `String`, custo linear em consulta sem poda de partição | A leitura precisa encontrar autorização já expurgada (exige a cascata)? O campo novo foi refletido nos **três** pontos (entidade JPA + `domain/model` + mapper)? Qual o volume esperado? |
| **autorizacaostatus-producer** (8082) — ponte SQS→Kafka | Sem banco. Converte JSON → Avro | O payload JSON mudou? O `.avsc` mudou? Os espelhos foram replicados? Precisa de DLQ/retry novo? |
| **eventos-consumer** (8083) — consome Avro | Sem banco. Retry é do spring-kafka (`DefaultErrorHandler`), não há visibility timeout. Deriva o tipo do evento do **corpo**, não de header | O consumer precisa do campo novo? O `.avsc` local foi replicado? O campo é nullable (compatibilidade de schema)? |
| **temporiza-autorizacao** (8084) — jornada 1 do PIX_AUTO | Sem banco; agenda/expira via Valkey; usa só um **subconjunto** do payload (id + data de inclusão); é um chamador at-least-once do `/decisao` | O prazo muda? A história depende de campo do payload além de id/data? O efeito é seguro se o disparo repetir? |
| **expurgo-particao** (Lambda Python) | Fórmula de partição espelhada do Java; `TRUNCATE` só sobre dado do ciclo anterior; gaveta vazia é resultado normal | A fórmula de particionamento ou a retenção mudam? O espelho Java↔Python foi replicado? |

## Etapa 3 — Eixos de interrogatório

Sete eixos. Não é para escrever uma seção por eixo na saída — é para **não deixar a pergunta sem
resposta**. O que sobrar sem resposta vira Questão em Aberto classificada.

### 1. Contrato de entrada e status HTTP

A convenção do monorepo é única e já decidida: **entrada inválida do cliente → 422**, tanto falha de
formato (`@Valid`) quanto violação de regra de negócio (`BusinessException`). A distinção entre as
duas é carregada pelo **shape do corpo** (`LayoutErrosApiValidationsResponse` vs
`LayoutErrosApiResponse`), não pelo status. Concorrência → **409**. Erro técnico → **500**.

Consequências práticas para a história:

- Nas rotas de escrita do `contratocommand` **não existe 404**: autorização inexistente é 422. Uma
  história que pede 404 numa rota PATCH está pedindo mudança de convenção — isso é uma decisão, não
  um detalhe, e precisa aparecer como tal.
- `GET /api/autorizacoes/{id}` no `contratoquery` **tem** 404. Os dois comportamentos convivem por
  design; não "corrija" um pelo outro dentro de uma história de feature.
- Todo campo numérico novo precisa de faixa declarada na borda. Sem `@Max`, um valor grande sofre
  narrowing silencioso na conversão para `short` e é gravado errado, sem erro nenhum.

### 2. Máquina de estados

Toda história que muda status responde: **de qual estado, para qual estado, com qual `motivo_status`**.

O grafo vive em `StatusAutorizacao.podeTransicionarPara`: `RECEBIDA` → {`PENDENTE_ACEITE`,
`EM_PROCESSO_ATIVACAO`, `REJEITADA`}; `ATIVA` → {`CANCELADA`, `FINALIZADA`, `REJEITADA`}; estados
terminais não saem de lugar nenhum. Se a história exige uma aresta que não existe, isso é mudança do
grafo — alto impacto, **Bloqueia** até alguém decidir explicitamente.

E há uma sutileza que já custou um bug: **alcançabilidade no grafo não é idempotência**. `ATIVA →
REJEITADA` é aresta válida para outro fluxo de negócio, então validar só pelo grafo faria uma
expiração atrasada rejeitar uma autorização já aprovada. Por isso a decisão exige
`statusAtual == RECEBIDA` explicitamente. Se a história introduz uma transição nova, pergunte qual é
a **checagem explícita de origem** dela, não só se a aresta existe.

### 3. Idempotência e concorrência

| Pergunta | Por que importa aqui |
|---|---|
| Quem chama, e o chamador repete? | SNS/SQS entregam at-least-once; o `temporiza-autorizacao` chama `/decisao` sem conhecer o estado atual |
| A segunda chamada devolve o quê? | O padrão daqui é **422 identificando o status atual**, não 200 silencioso — o chamador automatizado precisa distinguir "já resolvida" (não repetir) de falha de sistema (repetir) |
| Dois chamadores simultâneos? | `@Version` na entidade JPA → `ObjectOptimisticLockingFailureException` → **409**. Movimentação entre partições muda a forma do conflito para `CannotAcquireLockException`, também 409 |
| Quantos efeitos colaterais no total? | O critério tem que dizer **exatamente um** evento publicado, não "um evento é publicado" |

### 4. Persistência, particionamento e migration

- Coluna nova no `contratoquery` exige edição em **três** lugares (entidade JPA,
  `domain/model/Autorizacao`, `AutorizacaoPersistenceMapper`) e **nenhum dos três quebra a
  compilação** se for esquecido — o sintoma é dado incompleto em runtime. Um critério de aceite que
  só verifique o status HTTP não pega isso.
- A tabela `autorizacoes` é particionada (faixa 900–999 reservada ao expurgo).
  `CREATE INDEX CONCURRENTLY` **não funciona** em tabela particionada — o índice-pai nasce inválido e
  some do planejador. Índice novo é item de tarefa com procedimento próprio, não uma linha de migration.
- `@Column(nullable = false)` não impõe nada com `ddl-auto: none`: é documentação. Obrigatoriedade
  real vem da migration.
- Consulta que não poda por chave de partição custa linearmente no número de partições. Uma história
  de listagem ou filtro novo precisa do volume esperado — sem ele, o critério de performance é chute.

### 5. Eventos e espelhos manuais

Não há módulo compartilhado neste monorepo: schemas são espelhados **manualmente**. Se a história
tocar qualquer linha da tabela abaixo, **a lista de espelhos entra na própria história** — não como
detalhe de implementação, porque esquecer um espelho é falha silenciosa em runtime, não erro de
compilação.

| Se a história muda... | Replique em |
|---|---|
| `AutorizacaoEventoPayload` (JSON) | `contratocommand` **e** `autorizacaostatus-producer` (cópias independentes). Confira se o `temporiza-autorizacao` precisa do campo — lá o payload é um **subconjunto** deliberado (id + data de inclusão) |
| `EventoAutorizacao.avsc` (Avro) | `autorizacaostatus-producer` **e** `eventos-consumer`, ambos em `src/main/resources/avro/` |
| enums `StatusAutorizacao` / `TipoEventoAutorizacao` | `contratocommand` **e** `eventos-consumer` (espelhos manuais) |
| Fórmula de partição de expurgo | `ControleExpurgoAutorizacao` (Java) **e** `calculo.py` (Python) |
| Coluna de `autorizacoes` | `contratocommand` **e** `contratoquery` (neste, os três pontos do eixo 4) |
| `CLAUDE.md` de um app | `AGENTS.md` do mesmo app (mantidos idênticos) |

Campo novo em evento é sempre pergunta de compatibilidade: **nullable com default**, ou o consumer
que ainda roda com o schema antigo quebra ao ler mensagem nova.

### 6. Observabilidade e dado sensível

- Correlação: a história precisa de `traceId` propagado? Em fluxo assíncrono (SNS → SQS → Kafka) a
  correlação não acontece sozinha.
- O que precisa ficar rastreável: quem decidiu, quando, por qual canal. As rotas de atualização já
  exigem `codigoCanalAtualizacao` + `idPessoaAtualizacao` por auditoria — feature nova que altera
  dado de cliente provavelmente precisa do equivalente.
- Nenhuma resposta de erro pode expor nome de classe, stack trace, tabela, coluna ou constraint.
- Dado pessoal ou financeiro não vai para log. Se a história introduz campo sensível, o critério de
  aceite diz o que **não** aparece no log.

### 7. Verificabilidade

Para cada `Então` que você pretende escrever: **existe um teste que fica vermelho se isso não
acontecer?** Se a resposta for não, ou o critério é vago (a etapa 4 resolve) ou falta um mecanismo
que ainda não existe — e aí falta uma tarefa.

## Etapa 4 — Critérios de aceite observáveis

Um critério de aceite serve quando nomeia um **efeito observável numa borda**: status HTTP e shape do
corpo, linha persistida (coluna e valor), mensagem publicada (tópico e attribute), chave no Valkey,
entrada de log. "Funciona corretamente" não é borda nenhuma.

| Vago (não serve) | Observável (serve) |
|---|---|
| "a autorização é cancelada" | "a linha persistida tem `status` correspondente a `CANCELADA` (código 5) **e** `motivo_status` = `<valor>`" |
| "retorna erro" | "responde 422 com `LayoutErrosApiResponse` identificando o status atual, **e** nenhuma alteração é persistida" |
| "o evento é publicado" | "**exatamente um** evento é publicado em `sns-estados-autorizacao` com o attribute `tipoEvento` = `ATIVACAO`" |
| "a consulta é rápida" | "p99 abaixo de `<N>` ms com massa representativa carregada" — e se ninguém souber o `<N>`, isso é Questão em Aberto **Bloqueia**, não um número inventado |
| "o campo é obrigatório" | "requisição sem o campo responde 422 no shape `LayoutErrosApiValidationsResponse`, com `occurrences` apontando o campo" |

**Cobertura mínima de cenários.** Caminho feliz sozinho é o padrão da história bruta — e é
exatamente onde nada quebra. Cubra sempre:

1. Caminho feliz.
2. Regra de negócio violada (o 422 **e** o que não aconteceu).
3. **Chamada repetida** — o mesmo comando chega duas vezes.
4. **Concorrência** — dois chamadores na mesma linha (409).
5. Recurso ausente, ou estado que não permite a operação.

O item mais esquecido não é um cenário, é uma cláusula: **o efeito que não deve acontecer**. "E
nenhum evento é publicado", "e a linha permanece em `ATIVA`", "e exatamente um evento no total". É
onde mora o bug caro, porque o caminho feliz passa nos dois casos.

## Anti-padrões de história

Mesmo formato de achado usado por `revisao-de-codigo-java` e `qualidade-codigo-java` — o smell, por
que ele custa, e a versão corrigida.

### A história já é a solução

**[❌ História Não Refinada]:**
```
Criar uma tabela de log de decisões com as colunas id, autorizacao_id, acao e data.
```

**[🚨 Violação e Explicação]:** entrega o desenho e esconde a necessidade. Ninguém consegue avaliar
se uma tabela é a resposta certa, se índice é preciso, ou se a informação já é recuperável de
`motivo_status` + `tipo_jornada` — e o time perde a chance de resolver mais barato.

**[✅ História Refinada]:**
```
Como analista de operações, quero saber por qual caminho uma autorização chegou ao estado atual
(aprovação do cliente, rejeição explícita ou expiração de prazo), para investigar reclamação de
cliente sem depender do time de engenharia.
```
A solução volta a ser decisão técnica — e a primeira pergunta passa a ser "isso já não é recuperável
do que está persistido hoje?".

### O critério não pode falhar

**[❌ História Não Refinada]:**
```
Então o sistema processa a autorização corretamente e mantém a consistência dos dados.
```

**[🚨 Violação e Explicação]:** nenhum teste consegue ficar vermelho por causa disso. Vira critério
decorativo: passa na demo, passa na revisão e não protege nada. Pior, dá sensação de cobertura.

**[✅ História Refinada]:**
```
Então a linha persistida tem status correspondente a ATIVA (código 4)
E motivo_status é AUTORIZACAO_ACEITA_POR_TODOS
E exatamente um evento é publicado com tipoEvento = ATIVACAO
```

### Silêncio sobre a chamada repetida

**[❌ História Não Refinada]:**
```
Quando o prazo da jornada 1 expira, a autorização é rejeitada.
```

**[🚨 Violação e Explicação]:** quem dispara é o `temporiza-autorizacao` via `PATCH /decisao`, um
chamador at-least-once que não conhece o estado atual. A história não diz o que acontece se a
expiração chegar **depois** de o cliente aprovar — e sem essa resposta o dev escolhe na hora, com
50% de chance de rejeitar uma autorização já ativa.

**[✅ História Refinada]:**
```
Cenário: expiração chega depois da aprovação
  Dado que a autorização já está em ATIVA
  Quando a expiração é processada
  Então a resposta é 422 identificando o status atual
  E a linha permanece em ATIVA
  E nenhum evento é publicado

Cenário: expiração repetida
  Quando a mesma expiração é submetida duas vezes
  Então a primeira aplica a transição e a segunda resulta em 422
  E exatamente um evento é publicado no total
```

### Contrato inventado por analogia com outra API

**[❌ História Não Refinada]:**
```
Se a autorização não existir, retornar 404 Not Found. Se os dados estiverem inválidos, retornar 400.
```

**[🚨 Violação e Explicação]:** é o padrão REST genérico, não o deste serviço. As rotas de escrita do
`contratocommand` não têm 404 (inexistente é 422) e não usam 400 (entrada inválida do cliente é 422,
distinguida pelo shape do corpo). Implementado como escrito, quebra a capability
`contrato-api-consistente` e diverge do resto da API sem ninguém ter decidido isso.

**[✅ História Refinada]:**
```
Autorização inexistente e dados inválidos respondem 422, seguindo a convenção da API:
- falha de formato (@Valid) -> LayoutErrosApiValidationsResponse, com occurrences por campo
- violação de regra de negócio -> LayoutErrosApiResponse
```

### Campo novo sem a lista de espelhos

**[❌ História Não Refinada]:**
```
Adicionar o campo canalOrigem na autorização e disponibilizá-lo para os consumidores.
```

**[🚨 Violação e Explicação]:** "os consumidores" esconde quatro cópias mantidas à mão — payload JSON
no `contratocommand` e no `autorizacaostatus-producer`, `.avsc` no producer e no `eventos-consumer` —
mais os três pontos do `contratoquery`. Esquecer qualquer uma não quebra a compilação: o campo
simplesmente chega nulo em produção.

**[✅ História Refinada]:** a história declara os espelhos e a compatibilidade:
```
Escopo de propagação de canalOrigem:
- contratocommand: request, domain/model, entidade JPA, migration, AutorizacaoEventoPayload
- autorizacaostatus-producer: AutorizacaoEventoPayload (espelho) e EventoAutorizacao.avsc
- eventos-consumer: EventoAutorizacao.avsc (espelho)
- contratoquery: entidade JPA + domain/model + AutorizacaoPersistenceMapper (três pontos)
Campo nullable com default, para não quebrar consumidor com schema antigo.
```

## Formato de saída

````markdown
## 🎯 História de Usuário (INVEST)

**Como** [ator real — nomeie o sistema, se for sistema],
**Eu quero** [ação/funcionalidade],
**Para que** [benefício verificável].

## 🗺️ Escopo e serviços impactados

| Serviço | O que muda | Espelho a replicar |
|---|---|---|
| [app] | [mudança] | [espelho, ou "—"] |

## ✅ Critérios de Aceite

### Cenário 1: [Caminho feliz]
- **Dado que** [pré-condição verificável]
- **Quando** [ação disparada]
- **Então** [efeito observável numa borda]
- **E** [efeito colateral esperado — evento, linha, chave]

### Cenário 2: [Regra de negócio violada]
- **Dado que** ...
- **Quando** ...
- **Então** [status + shape do corpo]
- **E** [o que **não** acontece]

### Cenário 3: [Chamada repetida]
### Cenário 4: [Concorrência]

## 🛠️ Detalhamento Técnico

- **Contrato**: [rotas, headers, status por caso, tópicos/filas]
- **Estado**: [transição de → para, `motivo_status`, checagem explícita de origem]
- **Persistência**: [entidades, colunas, migration, impacto de particionamento]
- **Eventos**: [`tipoEvento`, payload, compatibilidade de schema]
- **Resiliência**: [idempotência, lock otimista/409, retry, DLQ]
- **Observabilidade**: [`traceId`, o que logar, o que nunca logar]

## ⚠️ Bordas e Riscos
- [ ] [risco concreto e sua consequência, não categoria genérica]

## 🚦 Prontidão

| Nível | Lacuna | Ação |
|---|---|---|
| Bloqueia | ... | ... |
| Ajusta | ... | ... |
| Registra | ... | gatilho de revisão: ... |

## ❓ Questões em Aberto
- **[Bloqueia]** [pergunta objetiva, endereçada a quem pode respondê-la]
````

Omita uma seção que não tenha conteúdo real, em vez de preenchê-la com "não se aplica" — exceto
**Prontidão** e **Questões em Aberto**, que sempre aparecem: "nenhuma lacuna Bloqueia" é uma
informação, e é justamente o que autoriza a história a entrar em sprint.

## Ponte para o OpenSpec

Este monorepo é spec-driven: a história refinada não é o destino, é o insumo de uma change em
`openspec/changes/`. Escreva os critérios já no formato que sobrevive à tradução — cada regra de
negócio vira um `### Requirement:` com `SHALL`, cada cenário vira um `#### Scenario:` com
`WHEN`/`THEN`/`AND` (o `Dado que` normalmente é absorvido pelo `WHEN`).

**Refinamento:**
```
Cenário: Expiração chega depois da aprovação
  Dado que a autorização já está em ATIVA
  Quando a expiração é processada
  Então a resposta é 422 identificando o status atual
  E a linha permanece em ATIVA
```

**Spec (`openspec/changes/<change>/specs/<capability>/spec.md`):**
```
### Requirement: Decisão sobre autorização já resolvida é erro de negócio, não sucesso

Quando o status atual não permitir a transição pedida, o use case SHALL lançar
`BusinessException`, resultando em HTTP 422, e NÃO SHALL alterar a linha nem publicar evento.

#### Scenario: Expiração chega depois da aprovação
- **WHEN** um `PATCH /{id}/decisao` com `acao: EXPIRAR` é processado para uma autorização já em `ATIVA`
- **THEN** a resposta é 422 identificando o status atual
- **AND** a linha permanece em `ATIVA`
```

Um critério vago não sobrevive a essa tradução — não há como escrever um `SHALL` a partir de
"processa corretamente". Se a tradução travar, a lacuna é da história, não da spec.

## Skills e agents relacionados

| Situação | Use |
|---|---|
| História refinada, hora de abrir a change com proposal/design/tasks | skill `openspec-propose` |
| Ainda investigando o problema, sem recorte definido | skill `openspec-explore` |
| O detalhe que falta é desenho de contrato REST (paginação, erro, versionamento) | skill `api-rest-design` / agent `projetista-api` |
| A história exige decisão arquitetural de alto nível (serviço novo, fluxo novo entre apps) | agent `arquiteto-sistemas` |
| Dúvida sobre em qual camada a implementação cai | skill `arquitetura-limpa-java` |
| Dúvida sobre DLQ, retry ou idempotência de listener | skill `mensageria-sqs-kafka` |
| Diagrama para explicar o fluxo da história | skill `gerar-diagramas` |
| A história virou código e você quer criticá-lo | skill `revisao-de-codigo-java` / agent `java-revisor` |

> **Coesão com `revisao-de-codigo-java`:** as duas skills usam a mesma mecânica — classificar por
> gravidade, exigir evidência concreta, mostrar o antes/depois no mesmo formato — em pontas opostas
> do ciclo. Um achado **Crítico** na revisão que só existe porque a história não respondeu a uma
> pergunta é sinal de que faltou uma pergunta **Bloqueia** aqui; use isso para calibrar os eixos da
> etapa 3.
