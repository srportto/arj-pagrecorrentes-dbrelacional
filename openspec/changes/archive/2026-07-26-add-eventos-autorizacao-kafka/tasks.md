# Tasks: add-eventos-autorizacao-kafka

## 1. Infra Kafka local (compose dedicado)

- [x] 1.1 Criar `infra/local/kafka/compose.yaml` com cp-kafka (KRaft, nó único,
      `auto.create.topics.enable=false`), cp-schema-registry e Kafbat UI, com portas
      fixas em localhost (broker em `19092` — `9092` colide com a faixa dinâmica do
      Hyper-V/WSL2 no Windows; registry em `8085`; dashboard em `8090`)
- [x] 1.2 Adicionar init-container que cria o tópico `eventos-autorizacao` com 3
      partições (`kafka-topics --create --if-not-exists`)
- [x] 1.3 Criar `infra/local/kafka/README.md` (subida, portas, URL do dashboard,
      `docker compose down -v`, ordem em relação aos demais ambientes locais)
- [x] 1.4 Validar: compose sobe, tópico listado com 3 partições (confirmado via
      `kafka-topics --describe`), `GET /subjects` responde `[]` no registry, Kafbat UI
      responde 200 em `http://localhost:8090`

## 2. Schema Avro (contrato)

- [x] 2.1 Escrever o `.avsc` `EventoAutorizacao` (namespace
      `br.com.srportto.eventos.autorizacao`) espelhando a linha de `autorizacoes`:
      snake_case, nulabilidade do DDL, `local-timestamp-micros`, `date`, `uuid`,
      `decimal(17,2)`, `long` para `tipo_produto`, `metadados` como string JSON
- [x] 2.2 Configurar `avro-maven-plugin` no `pom.xml` da `autorizacaostatus-producer`
      (com `enableDecimalLogicalType=true`) e validar a geração das classes
      (`mvn generate-sources`) — `BigDecimal`/`LocalDate`/`LocalDateTime`/`UUID`
      confirmados nos tipos gerados

## 3. Ponte SQS → Kafka (autorizacaostatus-producer)

- [x] 3.1 Adicionar dependências `kafka-clients` e `kafka-avro-serializer` (repositório
      Confluent) ao `pom.xml`
- [x] 3.2 Criar configuração do producer (`shared/config/`): bootstrap servers, Schema
      Registry URL, `enable.idempotence=true`, `acks=all`, `max.block.ms=5s`,
      `request.timeout.ms=5s`, `delivery.timeout.ms=15s`; properties com defaults
      locais no profile `local` e variáveis de ambiente em `prod`
- [x] 3.3 Implementar o conversor payload → `EventoAutorizacao` (setScale(2) nos
      decimais, metadados serializado como string) e a key SHA-256 de
      `id_autorizacao` + `data_hora_ultima_atlz` (formatter `ISO_LOCAL_DATE_TIME`)
- [x] 3.4 Implementar o adapter de produção Kafka (`infrastructure/kafka/`): produce
      síncrono com header `tipoEvento`, distinção retryable (exceção propaga → sem
      ack) vs não-retryable (log ERROR com body completo → ack)
- [x] 3.5 Ajustar `SqsEventoAutorizacaoListener` para solicitar
      `messageAttributeNames("tipoEvento")` e integrar o use case ao fluxo de produção
      (ack somente após confirmação do broker)
- [x] 3.6 Testes: conversor (mapeamentos, scale, nulos), geração da key (determinismo,
      transições distintas), classificação de falhas e semântica de ack do listener —
      22/22 testes passando (`mvn test`)
- [x] 3.7 Atualizar CLAUDE.md/AGENTS.md e README da app (papel de ponte, novo fluxo,
      armadilha do espelho do `.avsc`, semântica de descarte não-retryable)

## 4. Nova app eventos-consumer

- [x] 4.1 Criar o esqueleto de `apps/eventos-consumer` a partir do modelo da casa
      (Spring Boot 4.0.7, Java 25, pacote `br.com.srportto.eventosconsumer`, porta
      8083, sem banco, Actuator, profiles `local`/`prod`, Dockerfile multi-stage)
- [x] 4.2 Copiar o `.avsc` (espelho manual) e configurar `avro-maven-plugin` +
      dependências spring-kafka e `kafka-avro-serializer`
- [x] 4.3 Implementar o listener (`@KafkaListener`, group `eventos-consumer`,
      `AckMode.MANUAL`, `specific.avro.reader=true`): loga o corpo do evento e comita o
      offset após o log; `DefaultErrorHandler` com defaults (padrão do container factory)
- [x] 4.4 Testes: use case de processamento (log + ack), comportamento em erro (offset
      não comitado) — 4/4 testes passando (`mvn test`), incluindo `@SpringBootTest`
      (contexto sobe sem broker real — conexão Kafka é lazy)
- [x] 4.5 Criar CLAUDE.md/AGENTS.md e README da app no padrão do monorepo

## 5. Validação fim a fim e documentação

- [x] 5.1 Subir Postgres + apps + Floci + Kafka, criar e cancelar uma autorização via
      REST e verificar: 2 eventos no tópico com keys distintas (confirmado via log da
      ponte e via `kafka-topics --describe`, partições 0 e 2), header `tipoEvento`
      propagado (`CRIACAO`/`CANCELAMENTO`, confirmado no log da eventos-consumer),
      payload decodificado corretamente pelo consumer. **Bug encontrado e corrigido**:
      faltava `@EnableKafka` em `KafkaConsumerConfig` — sem ela o `@KafkaListener` nunca
      é registrado (nenhum log de consumer group aparecia)
- [x] 5.2 Verificar idempotência: keys SHA-256 distintas por transição confirmadas
      (criação e cancelamento da mesma autorização geraram keys diferentes, caindo em
      partições diferentes — trade-off de ordenação do design.md observado na prática);
      consumer group `eventos-consumer` visível via `kafka-consumer-groups --describe`
      com **lag 0** em todas as partições
- [x] 5.3 Verificado falha retryable: com Kafka derrubado, mensagem publicada ficou
      `NotVisible` na fila (sem ack) e, após religar o broker, foi produzida com sucesso
      no próximo ciclo de retry (fila voltou a 0/0). Verificado falha não-retryable:
      mensagem com JSON malformado gerou log ERROR com o corpo completo e recebeu ack
      (fila voltou a 0 mensagens) — comportamento exatamente como especificado em
      `consumo-eventos-autorizacao`
- [x] 5.4 Atualizado README raiz (diagrama, tabela de microserviços, estrutura de
      pastas, passo a passo de subida com Kafka, tabela de documentação). `mvn test`
      verde: 22/22 em `autorizacaostatus-producer`, 4/4 em `eventos-consumer`
