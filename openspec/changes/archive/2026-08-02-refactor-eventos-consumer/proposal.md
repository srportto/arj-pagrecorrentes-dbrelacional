## Why

O consumer Kafka de `eventos-consumer` acumulou complexidade e desvios de convenção que a
exploração (`/opsx:explore`) e as skills `arquitetura-limpa-java` e `mensageria-sqs-kafka`
deste próprio monorepo apontam como evitáveis: `AckMode.MANUAL` sem necessidade de negócio
(item listado como erro comum na skill de mensageria), ausência de DLT para mensagens
venenosas (outro erro comum da mesma skill), e um listener/enum de negócio posicionados fora
da camada que a skill `arquitetura-limpa-java` prescreve — divergindo do próprio
`contratocommand`, que segue a convenção corretamente. Corrigir agora, enquanto o app
ainda é pequeno (log + ack, sem processamento de negócio), evita que o padrão errado vire
referência para os próximos consumers Kafka do monorepo.

## What Changes

- Trocar `AckMode.MANUAL` (com `Acknowledgment` injetado no listener) por `AckMode.RECORD`
  via propriedade — mesma semântica observável (offset só avança após o log de sucesso, por
  registro), sem o parâmetro `Acknowledgment` nem o `ContainerProperties` customizado.
- Adicionar tratamento de mensagem venenosa: `DefaultErrorHandler` com
  `DeadLetterPublishingRecoverer` (backoff fixo + N tentativas, depois publica em
  `eventos-autorizacao.DLT`) — hoje uma mensagem que sempre falha fica em retry indefinido.
- Mover `EventoAutorizacaoKafkaListener` de `infrastructure/kafka/` para `entrypoint/kafka/`
  — alinha com a tabela "Que classe vai em qual camada" da skill `arquitetura-limpa-java`
  (`Listener SQS, consumer Kafka → entrypoint/`).
- Mover `StatusAutorizacao` e `TipoEventoAutorizacao` de `application/eventos/` para
  `domain/enums/`, introduzindo a camada `domain/` neste app — alinha com a mesma tabela
  (`Enum de negócio → domain/enums/`). Valores, códigos e grafo de transições continuam
  idênticos (nenhuma regra de negócio muda, só a camada).
- Trocar a dependência Maven `org.springframework.kafka:spring-kafka` pelo starter
  `org.springframework.boot:spring-boot-starter-kafka`, conforme convenção documentada na
  skill `mensageria-sqs-kafka`.
- **Fora de escopo (deliberado):** a derivação de `tipoEvento` a partir do campo `status` do
  record Avro (em vez de ler o header Kafka) permanece como está — é decisão já tomada e
  especificada em `consumo-eventos-kafka` (o body Avro like fonte única da verdade, não um
  header que pode divergir dele). Não há mudança de comportamento aqui, só a limpeza de
  camada/config acima.
- **Fora de escopo:** `autorizacaostatus-producer` não é tocado nesta mudança — hoje também
  usa `infrastructure/` em vez de `entrypoint/`, mas fica como trabalho futuro para não
  ampliar o raio da mudança.

## Capabilities

### New Capabilities

(nenhuma)

### Modified Capabilities

- `consumo-eventos-kafka`: requisito de `AckMode.MANUAL` muda para `AckMode.RECORD` (mesma
  garantia observável, sem `Acknowledgment` manual); novo requisito de tratamento de mensagem
  não-processável via DLT; localização do listener passa de `infrastructure/kafka/` para
  `entrypoint/kafka/`.
- `maquina-estados-autorizacao`: para `eventos-consumer` especificamente, a localização do
  espelho de `StatusAutorizacao`/`TipoEventoAutorizacao` muda de `application/eventos/` para
  `domain/enums/` (introduz camada `domain/` nesta app). Valores, códigos e grafo de
  transições permanecem inalterados; as outras 3 aplicações do monorepo não são afetadas.

## Impact

- **Código afetado:** `apps/eventos-consumer/src/main/java/...` (todas as classes principais
  e seus pacotes), `apps/eventos-consumer/src/test/java/...` (pacotes espelhados), `pom.xml`
  (dependência Kafka), `apps/eventos-consumer/CLAUDE.md`/`AGENTS.md` (mapa de pacotes e fluxo
  documentados).
- **Sem mudança de contrato externo:** tópico, group id, schema Avro e semântica de
  at-least-once continuam os mesmos; nenhum outro app do monorepo consome `eventos-consumer`
  diretamente.
- **Dependências:** troca de artefato Maven (`spring-kafka` → `spring-boot-starter-kafka`);
  nenhuma dependência nova além da já transitiva do starter.
