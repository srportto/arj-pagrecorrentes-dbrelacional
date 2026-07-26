# CLAUDE.md

> Guia para agentes de IA (Claude Code, Copilot, etc.) trabalharem neste repositório.
> **Este arquivo e `AGENTS.md` são espelhos — mantenha-os idênticos ao editar.**

Consumidora do tópico Kafka `eventos-autorizacao`, em **arquitetura hexagonal**. Recebe
os eventos Avro produzidos pela `autorizacaostatus-producer` (ponte SQS → Kafka), loga o
consumo com sucesso incluindo a representação do evento e comita o offset (ack) somente
após o log. Nesta fase não há processamento de negócio — apenas log + ack.

## Comece por aqui

Leia nesta ordem:
1. [EventoAutorizacaoKafkaListener.java](src/main/java/br/com/srportto/eventosconsumer/infrastructure/kafka/EventoAutorizacaoKafkaListener.java) — `@KafkaListener` (ack manual)
2. [ProcessarEventoAutorizacaoUseCase.java](src/main/java/br/com/srportto/eventosconsumer/application/eventos/ProcessarEventoAutorizacaoUseCase.java) — loga o evento consumido
3. [KafkaConsumerConfig.java](src/main/java/br/com/srportto/eventosconsumer/shared/config/KafkaConsumerConfig.java) — `ConsumerFactory` + `ConcurrentKafkaListenerContainerFactory` (AckMode.MANUAL)
4. [EventoAutorizacao.avsc](src/main/avro/EventoAutorizacao.avsc) — schema Avro consumido (espelho manual do da `autorizacaostatus-producer`)

## Build & Testes

```bash
mvn clean package                            # Compilar + testes + JAR (gera classes Avro em generate-sources)
mvn spring-boot:run                          # Rodar localmente (porta 8083)
mvn test                                     # Todos os testes
```

> **Maven Wrapper**: este app não possui `mvnw`/`mvnw.cmd` — use `mvn` diretamente.

## Pré-requisitos

- **Java 25** (JDK 25+) — usa `public static void main()`; a forma `void main()` do Java 25 está pendente de suporte do maven plugin
- **Sem banco de dados** — esta app não usa JPA/PostgreSQL
- **Kafka local no ar** (broker + Schema Registry) via
  [`infra/local/kafka/`](../../infra/local/kafka/README.md) — a conexão do consumer
  Kafka é lazy: a app sobe normalmente sem o broker no ar, mas não consome nada até ele
  existir
- Variáveis de ambiente obrigatórias em `prod`: `KAFKA_BOOTSTRAP_SERVERS`,
  `KAFKA_SCHEMA_REGISTRY_URL` (no profile `local` há defaults apontando para
  `infra/local/kafka/`)
- Profiles Spring: `local` (padrão de desenvolvimento) e `prod` (deve ser setado
  explicitamente via `SPRING_PROFILES_ACTIVE=prod`)

## Stack

| Componente | Versão | Notas |
|---|---|---|
| Java | 25 | `void main()` pendente do maven plugin |
| Spring Boot | 4.0.7 | Web MVC (só para o Actuator), Actuator |
| spring-kafka | gerenciado pelo Spring Boot BOM | `@KafkaListener` + `Acknowledgment` manual |
| Avro | 1.11.3 | `avro-maven-plugin` gera `EventoAutorizacao` a partir de `src/main/avro/EventoAutorizacao.avsc` |
| kafka-avro-serializer | 7.7.1 (Confluent) | `KafkaAvroDeserializer` + integração com o Schema Registry |
| Lombok | 1.18.40 | uso mínimo (sem entidades JPA) |

## Endpoints reais

| Método | Caminho | Descrição |
|--------|---------|-----------|
| GET | `/actuator/health` | Health-check (Actuator). → 200 (UP) |

> **Não há endpoints REST de negócio** — esta app não expõe API própria, apenas consome
> o tópico Kafka em background.

## Arquitetura (hexagonal)

```
application/eventos/    → ProcessarEventoAutorizacaoUseCase (loga o evento)
infrastructure/kafka/   → EventoAutorizacaoKafkaListener (adapter de consumo, @KafkaListener)
shared/config/          → KafkaProperties, KafkaConsumerConfig (ConsumerFactory + container factory)
```

Sem camada `entrypoint/` nem `domain/`: não há API REST de negócio nem persistência.

### Fluxo de consumo

```
EventoAutorizacaoKafkaListener.escutar()  (@KafkaListener, containerFactory com AckMode.MANUAL)
  ├─ recebe o EventoAutorizacao desserializado (specific.avro.reader=true) + header tipoEvento
  ├─ ProcessarEventoAutorizacaoUseCase.processar(evento, tipoEvento)
  │    └─ loga sucesso com a representação do evento
  └─ Acknowledgment.acknowledge()  — só se processar() não lançar exceção
```

Erro em `processar()` não comita o offset. A reentrega **não** segue a semântica de
visibility timeout do SQS: quem trata a repetição é o `DefaultErrorHandler` do
spring-kafka (seek de volta ao offset não comitado, novas tentativas, e ao final log do
descarte) — comportamento padrão do container factory, sem customização nesta fase.

### Diferença deliberada de padrão em relação ao SQS

Ao contrário do listener SQS da `autorizacaostatus-producer` (cliente AWS SDK v2 puro,
sem framework), esta app usa **spring-kafka** (`@KafkaListener`) — quebra consciente da
jurisprudência "cliente puro" do monorepo: menos código, error handling e retry prontos,
e é o idioma dominante do ecossistema Kafka. Ver `design.md` da mudança
`add-eventos-autorizacao-kafka` para a decisão completa.

## Armadilhas críticas

1. **Porta 8083** — diferente de `arj-contratocommand` (8080), `arj-contratoquery`
   (8081) e `autorizacaostatus-producer` (8082).
2. **Sem banco de dados** — não adicione JPA/Postgres aqui; se precisar persistir algo,
   isso é uma mudança de escopo desta app.
3. **`EventoAutorizacao.avsc` é um espelho manual** do schema equivalente em
   `apps/autorizacaostatus-producer` — os dois não compartilham código; se o schema
   mudar lá, replique aqui.
4. **Conexão Kafka é lazy** — `@SpringBootTest` sobe normalmente sem broker real (o
   `KafkaConsumer` só toca a rede ao fazer poll, em thread de background do container);
   não confunda "contexto sobe" com "está consumindo".
5. **Semântica de retry é do spring-kafka, não do SQS** — não há visibility timeout;
   quem decide reentrega é o `DefaultErrorHandler` do container.

## Documentação relacionada

- [design.md da mudança](../../openspec/changes/add-eventos-autorizacao-kafka/design.md) — decisões técnicas do fluxo Kafka (schema, idempotência, spring-kafka vs. cliente puro)
- [infra/local/kafka/README.md](../../infra/local/kafka/README.md) — como subir o Kafka local (broker, Schema Registry, dashboard)

## Checklist antes do commit

- [ ] `mvn test` passa
- [ ] `mvn clean compile` sem erros (gera as classes Avro em `generate-sources`)
- [ ] Se mudou `EventoAutorizacao.avsc`, replicar em `apps/autorizacaostatus-producer`
- [ ] Erros de processamento continuam sem comitar o offset
