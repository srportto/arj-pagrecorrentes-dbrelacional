# Arquitetura de Solução: Autorizações de Pagamentos Recorrentes

> Recriado em 2026-08-30 a partir do grafo de conhecimento gerado pelo `graphify` sobre
> `apps/` + `infra/` (404 arquivos, 1985 nós, 6099 arestas, 105 comunidades). A versão
> anterior deste documento descrevia uma topologia de sete apps (`limpezadb`,
> `contratoeventos-producer`, `comunicacao-producer`, `comunicacoes-consumer`) que não
> corresponde ao código atual — foi substituída pela topologia real, validada
> estruturalmente pelo grafo e cruzada com [README.md](../../README.md) e
> [stack-e-padroes.md](stack-e-padroes.md).

O sistema concentra-se no núcleo de gestão de **autorizações de pagamentos recorrentes**
(PIX Automático e DDA Automático), implementando a POC validada de **Particionamento com
Buffer Ring e UUID-v7 Reversível** (detalhe completo em
[modelo-dados-e-dados-poc-testada-para-essa-implementacao.md](modelo-dados-e-dados-poc-testada-para-essa-implementacao.md)).

A solução é um monorepo de **cinco microserviços Java 25 / Spring Boot 4** (arquitetura
hexagonal — `domain` / `application` / `infrastructure`) mais **uma Lambda Python**
agendada, cobrindo cinco preocupações:

1. **Modelagem de dados**: tabela `autorizacoes` particionada por `LIST(id_particao_conta)`,
   resolvendo o *hot partition problem* do particionamento anterior por data.
2. **CQRS sem event sourcing**: `contratocommand` (escrita) e `contratoquery` (leitura)
   sobre a mesma base e a mesma tabela — separação de responsabilidade e de escala, não
   de storage.
3. **Máquina de estados da jornada 1 do PIX_AUTO**: `temporiza-autorizacao` agenda e
   expira a decisão do cliente pagador via Valkey, sem tocar o banco.
4. **Gestão de expurgo (Ring Buffer)**: `expurgo-particao` fecha o ciclo aberto pelo
   `contratocommand`, via `TRUNCATE` na partição folha.
5. **Mensageria e eventos**: `contratocommand` publica no SNS; `autorizacaostatus-producer`
   faz a ponte SQS → Kafka; `eventos-consumer` consome o tópico Kafka resultante.

O grafo confirma essa divisão: os **God Nodes** (nós mais conectados do código) são
`Autorizacao`, `TipoProduto`, `StatusAutorizacao`, `TemporizacaoProperties` e
`BusinessException` — exatamente o modelo de domínio e a máquina de estados descritos
acima, não um detalhe de infraestrutura incidental.

---

## 🛠️ Aplicações e Serviços

Todas as aplicações Java rodam em **Java 25 / Spring Boot 4.0.7**, containers Fargate-ready
(Dockerfile multi-stage), com **Tomcat** embutido — exceto `contratoquery`, que usa
**Jetty** (Tomcat excluído no `pom.xml`). Undertow, citado em versões antigas deste
documento, **não existe mais no Spring Boot 4.0** e não é usado por nenhuma app.

### 1. `contratocommand` (porta 8080)

* **Tipo**: microserviço Java, único ponto de escrita do domínio.
* **Contexto**: cria, cancela e decide autorizações (`POST /autorizacoes`,
  `PATCH /cancelar`, `PATCH /decisao`). Autorizações `PIX_AUTO` nascem `RECEBIDA` e só
  viram `ATIVA` após aprovação do cliente pagador ou `REJEITADA` por rejeição/timeout da
  jornada 1; `DDA_AUTO` nasce `ATIVA` direto.
* **Enriquecimento arquitetural (POC)**: ao criar uma autorização, calcula a partição
  ativa (hash do UUID da conta, módulo 889) via `ReversibleUUIDv7`, que embute a partição
  no próprio identificador. Ao transicionar para um estado terminal, `ControleExpurgoAutorizacao`
  atualiza a chave primária para a gaveta de expurgo (`900`–`999`) — o PostgreSQL move a
  linha automaticamente entre partições.
* **Mensageria**: publica um evento por transição de estado no SNS
  (`sns-estados-autorizacao`), via `@TransactionalEventListener(AFTER_COMMIT)` — rollback
  nunca publica.

### 2. `contratoquery` (porta 8081)

* **Tipo**: microserviço Java, somente leitura (`DB_READ_ONLY=true`).
* **Contexto**: lista e consulta autorizações (`GET`). Não escreve na base; não publica
  eventos.
* **Particularidade de contrato (dívida aceita)**: expõe `status` como `String` (nome do
  enum) e nomes curtos de campo (`valor`, `dataCriacao`), diferente do `contratocommand`
  — ver [stack-e-padroes.md](stack-e-padroes.md) para o racional completo.

### 3. `autorizacaostatus-producer` (porta 8082)

* **Tipo**: microserviço Java, ponte SQS → Kafka (*bridge / anti-corruption layer*).
* **Contexto**: consome `SQS-eventos-autorizacao` (alimentada pela subscription do SNS),
  converte o payload JSON (`AutorizacaoEventoPayload`) para Avro
  (`EventoAutorizacao.avsc`) e produz no tópico Kafka `eventos-autorizacao` com key
  determinística, via `kafka-clients` puro + Schema Registry (Confluent). O ack no SQS só
  ocorre após a confirmação do broker Kafka.

### 4. `eventos-consumer` (porta 8083)

* **Tipo**: microserviço Java, consumidor terminal do fluxo de eventos.
* **Contexto**: consome o tópico Kafka `eventos-autorizacao` via `spring-kafka`
  (`@KafkaListener`, `AckMode.RECORD`), loga e confirma. Não acessa o banco; não produz
  eventos a jusante.

### 5. `temporiza-autorizacao` (porta 8084)

* **Tipo**: microserviço Java, temporizador da jornada 1 do PIX_AUTO — sem banco.
* **Contexto**: consome uma subscription **filtrada** do mesmo SNS (`RECEPCAO` +
  `PIX_AUTO` + `SPI_J1`), agenda a expiração de 10 minutos num **sorted set** no Valkey
  (score = vencimento). Um script Lua move os vencidos para um **stream**, e o próprio
  `ZREM` dentro do script funciona como lock distribuído (sem Redlock). Ao vencer, chama
  de volta `PATCH /decisao` (`acao=EXPIRAR`) no `contratocommand`.

### 6. `expurgo-particao` (Lambda Python, sem porta HTTP)

* **Tipo**: função Lambda Python 3.13, agendada via EventBridge Scheduler a cada 30 min.
* **Contexto**: fecha o ciclo do Ring Buffer aberto pelo `contratocommand`. Calcula a
  partição alvo (`escrita + 2`, retenção de 98 semanas), classifica seu estado e a esvazia
  via `TRUNCATE` **só** quando contém dado do ciclo anterior — nunca sobre dado recente.
  `contratocommand` e `expurgo-particao` não se conectam entre si: cada um só conhece o
  Postgres, e nenhum evento de negócio atravessa esse ciclo. `pg_cron` audita o resultado
  (registro forense), mas não expurga.

O grafo aponta essa relação diretamente numa das suas *hyperedges* de maior confiança:
**"Fluxo de Expiração da Jornada 1 PIX_AUTO (Command → Temporiza → Command)"**, ligando
`CommandDecisaoAutorizacaoClient` (em `temporiza-autorizacao`) e
`DecidirAutorizacaoService` (em `contratocommand`) — o mesmo laço fechado descrito acima,
só que para o timeout da jornada 1, não para o expurgo.

---

## 📨 Estrutura de Mensageria

* **SNS** — `sns-estados-autorizacao`: publicado pelo `contratocommand` a cada transição
  de estado, via AWS SDK v2 puro (sem Spring Cloud AWS).
* **SQS**:
  * `SQS-eventos-autorizacao` — subscription *raw delivery*, sem filtro; alimenta
    `autorizacaostatus-producer`. DLQ com `maxReceiveCount=10`.
  * `SQS-temporizacao-autorizacao` — subscription com **filter policy** por message
    attributes (`tipoEvento=RECEPCAO` + `tipoProduto=PIX_AUTO` + `tipoJornada=SPI_J1`);
    alimenta `temporiza-autorizacao`. O filtro no broker evita lógica de filtro no
    consumidor.
* **Kafka** — tópico `eventos-autorizacao` (Avro + Schema Registry), produzido por
  `autorizacaostatus-producer` e consumido por `eventos-consumer`. DLT via
  `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`.
* **Valkey** — usado só por `temporiza-autorizacao`: sorted set (agenda) + stream
  (vencidos), sem papel de mensageria entre serviços.

Contratos são **espelhados manualmente** entre apps — `AutorizacaoEventoPayload` (JSON)
em `contratocommand` e `autorizacaostatus-producer`, `EventoAutorizacao.avsc` (Avro) em
`autorizacaostatus-producer` e `eventos-consumer`. Não há módulo compartilhado; mudou um
lado, replique no outro.

---

## 🗄️ Base de Dados

* **Tecnologia**: PostgreSQL 18 (`pg_partman`, `pg_cron`, `pgvector`), sem fallback H2.
* **Estratégia de organização (particionamento LIST)**: a tabela `autorizacoes` é
  particionada por `id_particao_conta`, em dois blocos:
  1. **889 partições ativas** (`0`–`888`): recebem as novas autorizações e toda a carga
     de vigência/consulta. Distribuição uniforme via módulo do UUID da conta — sem hot
     partition.
  2. **100 partições de expurgo / Ring Buffer** (`900`–`999`): reservadas para estados
     terminais (dados "frios"). Retenção real de 98 semanas — 2 gavetas de folga à frente
     do ponteiro de escrita. `expurgo-particao` é quem limpa esse segmento, via
     `TRUNCATE` da partição folha (não `DELETE` nem `DROP`/recriação) — sem lock na
     tabela pai, sem dead tuples, sem VACUUM.
* Só `contratocommand` e `contratoquery` conhecem o schema; as outras quatro apps não
  tocam o banco.

---

## Referências

| Assunto | Onde |
|---|---|
| Topologia, portas, diagramas de conexão entre serviços | [README.md](../../README.md) |
| Stack técnica, system design e design patterns | [stack-e-padroes.md](stack-e-padroes.md) |
| Modelo de dados (Buffer Ring + UUIDv7 reversível) | [modelo-dados-e-dados-poc-testada-para-essa-implementacao.md](modelo-dados-e-dados-poc-testada-para-essa-implementacao.md) |
| Armadilhas e fluxos por serviço | `apps/<serviço>/CLAUDE.md` |
| Mapa de agentes de IA / convenções transversais do monorepo | [CLAUDE.md](../../CLAUDE.md) |
