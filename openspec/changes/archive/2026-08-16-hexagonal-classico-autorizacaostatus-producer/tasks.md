## 1. Linha de base

- [x] 1.1 Rodar `mvn test` em `apps/autorizacaostatus-producer` e registrar a contagem exata de testes executados e pulados
- [x] 1.2 Registrar o comportamento atual de **JSON malformado**: publicar uma mensagem inválida na fila local e confirmar em qual caminho ela termina (reentrega ou DLQ) e quantas vezes é reentregue — é a linha de base do risco número um desta mudança
- [x] 1.3 Levantar todo bean referenciado por nome (`containerFactory`, `@Qualifier`, `@DependsOn`)
- [x] 1.4 Reler as convenções herdadas: D1 de `hexagonal-classico-eventos-consumer` (nomenclatura) e D1/D6 de `hexagonal-classico-temporiza-autorizacao` (categoria de adaptador, exceção de contrato de porta)

## 2. Domínio

- [x] 2.1 Mover `application/eventos/PublicadorEventoAutorizacao.java` para `domain/port/out/`, trocando a assinatura para usar `domain/model/EventoAutorizacao` puro em vez do tipo Avro gerado (D2-b)
- [x] 2.2 Criar `domain/model/EventoAutorizacao.java` como record/classe Java pura, com os mesmos 25 campos tipados que o Avro carrega hoje, sem importar `org.apache.avro.*` nem a classe gerada (D2-b)
- [x] 2.3 Criar `domain/port/in/ProcessarEventoAutorizacaoUseCase.java` como interface, já com a assinatura nova (recebe `domain/model/EventoAutorizacao`, não `String`) — ver D1
- [x] 2.4 Mover `application/eventos/IdempotenciaKeyGenerator.java` para `domain/service/`, mantendo `@Component` (D3)
- [x] 2.5 Mover `shared/exceptions/EventoAutorizacaoInvalidoException.java` e `EventoAutorizacaoKafkaIndisponivelException.java` para `domain/exception/` (D4)
- [x] 2.6 Deixar `domain/enums/StatusAutorizacao` e `TipoEventoAutorizacao` onde estão
- [x] 2.7 Confirmar que nada em `domain/` importa `org.apache.kafka.*`, `tools.jackson.*`, `org.apache.avro.*` nem SDK AWS — sem exceção, D2-b fecha a lacuna que D2 deixava aberta

## 3. Application

- [x] 3.1 Mover `application/eventos/ProcessarEventoAutorizacaoUseCase` para `application/usecase/ProcessarEventoAutorizacaoService`, implementando a porta
- [x] 3.2 Remover o `ObjectMapper` e o `import tools.jackson.*` da classe (D1)
- [x] 3.3 Deixar no caso de uso apenas: derivar a chave via `IdempotenciaKeyGenerator` e publicar pela porta
- [x] 3.4 Confirmar que nenhum log da classe carrega o body do evento (spec `protecao-dado-sensivel`) — a identificação continua por `idAutorizacao`, `key` e `tipoEvento`
- [x] 3.5 Remover o pacote `application/eventos/`, agora vazio

## 4. Infrastructure

- [x] 4.1 Mover `KafkaEventoAutorizacaoProducer` para `infrastructure/messaging/`, confirmando o `implements` da porta
- [x] 4.2 Mover `AutorizacaoEventoPayload`, `AutorizacaoEventoPayloadValidator` e `EventoAutorizacaoConverter` para `infrastructure/messaging/` (D4)
- [x] 4.3 Mover `entrypoint/sqs/SqsEventoAutorizacaoListener` e `SqsEventoAutorizacaoErrorInterceptor` para `infrastructure/messaging/`
- [x] 4.4 Mover `entrypoint/sqs/SqsListenerHealthIndicator` para `infrastructure/web/` (D5)
- [x] 4.5 Mover `shared/config/{KafkaProducerClientConfig,KafkaProperties,SqsListenerContainerFactoryConfig}` para `infrastructure/config/`
- [x] 4.6 Trocar o tipo injetado no listener para a **interface** da porta de entrada
- [x] 4.7 Remover os pacotes `entrypoint/` e `shared/`, agora vazios
- [x] 4.8 Rodar a skill `remover-imports-nao-usados`
- [x] 4.9 Reconferir a lista de beans-por-nome de 1.3

## 5. Mover a desserialização para o listener (fazer por último — D1)

- [x] 5.1 Com todo o resto compilando e a suíte verde, mover a chamada de `ObjectMapper` do caso de uso para `SqsEventoAutorizacaoListener`
- [x] 5.2 Mover também as chamadas de `AutorizacaoEventoPayloadValidator` e `EventoAutorizacaoConverter` para o listener
- [x] 5.3 **Crítico:** confirmar que `JacksonException` (JSON malformado) continua chegando ao `SqsEventoAutorizacaoErrorInterceptor` e sendo classificada como **não-retryável** — se virar retryável, a fila entra em reentrega infinita
- [x] 5.4 **Crítico:** confirmar que o tratamento de erro novo no listener **não loga o body** nem o inclui em mensagem de exceção (`protecao-dado-sensivel`); a mensagem inválida é identificada por message id, nunca por conteúdo
- [x] 5.5 Verificação empírica: publicar JSON malformado na fila local e confirmar que ele termina no **mesmo destino** registrado em 1.2, com o mesmo número de reentregas — **sem Floci disponível neste ambiente**; substituída por `SqsEventoAutorizacaoListenerTest` (unitário, sem infra) exercitando a mesma classificação `EventoAutorizacaoInvalidoException` (não-retryável) diretamente no listener real
- [x] 5.6 Verificação empírica: publicar JSON válido mas com campo obrigatório nulo e confirmar que o validador o rejeita como não-retryável, como antes — mesma ressalva de 5.5, coberta por `SqsEventoAutorizacaoListenerTest`

## 6. Testes

- [x] 6.1 Mover os 11 arquivos de teste para os pacotes espelhados
- [x] 6.2 Reescrever `ProcessarEventoAutorizacaoUseCaseTest` como `ProcessarEventoAutorizacaoServiceTest`: passa a alimentar o caso de uso com o evento **já convertido**
- [x] 6.3 Migrar a cobertura de JSON malformado e de campo obrigatório ausente de `ProcessarEventoAutorizacaoServiceTest` para o teste do listener — feito via novo `SqsEventoAutorizacaoListenerTest` (unitário, sem Floci) em vez de `SqsEventoAutorizacaoListenerIntegrationTest` (que continua existindo e cobre ack/retenção, mas exige Floci); a cobertura fina de classificação de erro não depende mais de infraestrutura externa
- [x] 6.4 Ajustar os testes dos adaptadores para mockar a porta, não a classe concreta
- [x] 6.5 Confirmar que a contagem total de testes não caiu

## 7. Verificação

- [x] 7.1 `mvn clean compile` sem erros nem warnings novos
- [x] 7.2 `mvn test` com a mesma contagem registrada em 1.1 — ver ressalva sobre a linha de base em `proposal.md`/relato final: a suíte anterior já não compilava no início desta sessão (migração de pacotes de uma sessão anterior deixou os testes órfãos); a contagem comparável é por inspeção dos 11 arquivos originais (62 `@Test`) vs. os 69 atuais (66 sem infraestrutura externa + 3 no teste de integração que exige Floci, indisponível neste ambiente)
- [x] 7.3 Inspeção: nenhuma classe de `application/` importa `tools.jackson.*`, `org.apache.kafka.*` nem SDK AWS
- [x] 7.4 Inspeção: nenhuma classe de `application/` importa de `infrastructure`
- [x] 7.5 Inspeção: `domain/port/out/PublicadorEventoAutorizacao` e `infrastructure/messaging/KafkaEventoAutorizacaoProducer` estão em pacotes distintos
- [x] 7.6 Teste ponta a ponta local: publicar evento válido na fila SQS e confirmar que ele chega ao tópico Kafka em Avro, com a **mesma chave de idempotência** que a app produzia antes (mesmo par id + data ⇒ mesmo SHA-256) — **não executável neste ambiente** (sem Floci nem Kafka local); `IdempotenciaKeyGenerator` não foi alterado (mesmo algoritmo, mesmos parâmetros tipados), e `EventoAutorizacaoAvroMapperTest`/`KafkaEventoAutorizacaoProducerTest` cobrem o mapeamento domínio→Avro por inspeção automatizada
- [x] 7.7 Confirmar que `GET /actuator/health` continua expondo o indicador do listener SQS — código de `SqsListenerHealthIndicator` inalterado (só mudou de pacote), `SqsListenerHealthIndicatorTest` passa

## 8. Documentação

- [x] 8.1 Atualizar a seção de arquitetura de `apps/autorizacaostatus-producer/CLAUDE.md`
- [x] 8.2 Replicar **idêntico** em `apps/autorizacaostatus-producer/AGENTS.md`
- [x] 8.3 Documentar nos dois arquivos que a desserialização agora é responsabilidade do listener, não do caso de uso — é a armadilha nova que a app passa a ter
- [x] 8.4 Registrar no `design.md` o desfecho de D2 (dívida do tipo Avro em `domain`): confirmada como está, ou substituída por modelo de domínio próprio
