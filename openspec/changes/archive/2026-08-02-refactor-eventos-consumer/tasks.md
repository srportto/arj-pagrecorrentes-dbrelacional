## 1. Dependência Maven

- [x] 1.1 Trocar `org.springframework.kafka:spring-kafka` por
      `org.springframework.boot:spring-boot-starter-kafka` em
      `apps/eventos-consumer/pom.xml`
- [x] 1.2 Rodar `mvn clean compile` e confirmar que resolve sem conflito de versão

## 2. Mover enums de negócio para `domain/enums/`

- [x] 2.1 Criar pacote `br.com.srportto.eventosconsumer.domain.enums`
- [x] 2.2 Mover `StatusAutorizacao.java` de `application/eventos/` para `domain/enums/`
      (sem alterar valores, códigos ou grafo de transições)
- [x] 2.3 Mover `TipoEventoAutorizacao.java` de `application/eventos/` para
      `domain/enums/`
- [x] 2.4 Mover `StatusAutorizacaoTest.java` e `TipoEventoAutorizacaoTest.java` para
      `src/test/java/.../domain/enums/`, ajustando o pacote
- [x] 2.5 Atualizar imports em `ProcessarEventoAutorizacaoUseCase` e no respectivo teste

## 3. Mover listener para `entrypoint/kafka/`

- [x] 3.1 Criar pacote `br.com.srportto.eventosconsumer.entrypoint.kafka`
- [x] 3.2 Mover `EventoAutorizacaoKafkaListener.java` de `infrastructure/kafka/` para
      `entrypoint/kafka/`
- [x] 3.3 Mover `EventoAutorizacaoKafkaListenerTest.java` para
      `src/test/java/.../entrypoint/kafka/`, ajustando o pacote
- [x] 3.4 Remover o pacote `infrastructure/` vazio, se não sobrar mais nada nele

## 4. `AckMode.RECORD` no lugar de `AckMode.MANUAL`

- [x] 4.1 Trocar `factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL)`
      por `AckMode.RECORD` em `KafkaConsumerConfig` (não via propriedade — ver design.md D1,
      generics do `ConcurrentKafkaListenerContainerFactoryConfigurer` não são compatíveis
      com o factory fortemente tipado)
- [x] 4.2 (mesclada com 4.1)
- [x] 4.3 Remover o parâmetro `Acknowledgment acknowledgment` e a chamada
      `acknowledgment.acknowledge()` de `EventoAutorizacaoKafkaListener.escutar`
- [x] 4.4 Atualizar `EventoAutorizacaoKafkaListenerTest` para não mockar/injetar
      `Acknowledgment`; cobrir "processa com sucesso" (não lança) e "erro no
      processamento propaga a exceção" (offset não avança por não haver commit)

## 5. DLT para mensagens não-processáveis

- [x] 5.1 Envolver `KafkaAvroDeserializer` com `ErrorHandlingDeserializer` na
      configuração do `value.deserializer` em `KafkaConsumerConfig`
- [x] 5.2 Criar bean `KafkaTemplate<String, byte[]>` dedicado à DLT (falha de
      desserialização) e `KafkaTemplate<String, EventoAutorizacao>` (falha de negócio,
      record já desserializado) — `DeadLetterPublishingRecoverer` roteia pelo tipo do
      valor do record (ver design.md D2)
- [x] 5.3 Criar bean `DefaultErrorHandler` com `DeadLetterPublishingRecoverer` (usando
      os dois templates do item 5.2) e `FixedBackOff(1_000L, 3)`
- [x] 5.4 Registrar o `DefaultErrorHandler` na
      `ConcurrentKafkaListenerContainerFactory` (`setCommonErrorHandler`)
- [x] 5.5 Teste: evento com `status` desconhecido (já desserializado) é roteado ao
      template Avro do `DeadLetterPublishingRecoverer` (`KafkaConsumerConfigTest`)
- [x] 5.6 Teste: mensagem com falha de desserialização Avro (via `ErrorHandlingDeserializer`
      real + `MockSchemaRegistryClient`) é roteada ao template de bytes sem derrubar o
      deserializer (`KafkaConsumerConfigTest`)

## 6. Documentação

- [x] 6.1 Atualizar `apps/eventos-consumer/CLAUDE.md` e `AGENTS.md`: mapa de pacotes
      (`entrypoint/kafka/`, `domain/enums/`), fluxo de consumo (`AckMode.RECORD`, DLT),
      armadilhas críticas (tópico `.DLT`, `ErrorHandlingDeserializer`, dois `KafkaTemplate`
      de DLT, `AckMode.RECORD` em código e não em propriedade)
- [x] 6.2 Conferir que os dois arquivos continuam espelhos idênticos (`diff` = vazio)

## 7. Validação final

- [x] 7.1 `mvn clean test` em `apps/eventos-consumer` — 13/13 testes passam
- [x] 7.2 `mvn clean package` — build completo sem erros; `mvn clean verify` confirma o
      gate de cobertura JaCoCo (≥80%) também passa
- [x] 7.3 Revisado com o agent `java-especialista`: aprovado, sem achados críticos. Dois
      achados "Importantes" corrigidos (README.md desatualizado; comentário de
      `KafkaConsumerConfig` contradizendo D1) e `mvn clean verify` reconfirmado depois
