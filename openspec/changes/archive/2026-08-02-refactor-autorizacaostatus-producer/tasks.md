## 1. Correção crítica: campo obrigatório nulo deixa de causar reentrega infinita

- [x] 1.1 Criar `application/eventos/AutorizacaoEventoPayloadValidator` que valide os seis campos obrigatórios do `.avsc` (`id_autorizacao`, `data_fim_vigencia`, `data_hora_inclusao`, `data_hora_ultima_atlz`, `status`, `codigo_canal_contratacao`) e lance `EventoAutorizacaoInvalidoException` nomeando o campo faltante — sem incluir o body na mensagem
- [x] 1.2 Injetar o validator em `ProcessarEventoAutorizacaoUseCase` e chamá-lo imediatamente após `desserializar()`, antes de derivar o tipo, converter ou gerar a key
- [x] 1.3 Mover a chamada a `keyGenerator.gerar()` para dentro do bloco de classificação de falha (defesa em profundidade contra `NullPointerException` não classificada)
- [x] 1.4 Criar `AutorizacaoEventoPayloadValidatorTest`: um caso por campo obrigatório nulo + caso de payload completo
- [x] 1.5 Adicionar a `ProcessarEventoAutorizacaoUseCaseTest` o cenário de fluxo completo com `id_autorizacao` nulo (caminho A: NPE na key) e com `codigo_canal_contratacao` nulo (caminho B: `SerializationException` síncrona no `send()`), verificando que ambos viram `EventoAutorizacaoInvalidoException` e que `produzir()` nunca é chamado
- [x] 1.6 Rodar `mvn clean package` e confirmar que os testes passam

## 2. Correção crítica: PII fora dos logs

- [x] 2.1 Trocar o log de sucesso de `ProcessarEventoAutorizacaoUseCase` por `idAutorizacao={} key={} tipoEvento={}`, removendo `mensagemJson`
- [x] 2.2 Remover `mensagemJson` das mensagens de `EventoAutorizacaoInvalidoException` lançadas em `desserializar`, `derivarTipoEvento` e `paraEventoAvro` — passar a citar apenas a causa precisa (JSON inválido, `status` desconhecido, falha de conversão)
- [x] 2.3 Trocar o log de descarte de `SqsEventoAutorizacaoListener` por `messageId={}` + causa, removendo `message.body()`
- [x] 2.4 Ajustar os testes que hoje assertam sobre o conteúdo dos logs ou das mensagens de exceção, se houver
- [x] 2.5 Revisar todo o `src/main/java` por outras ocorrências de log/exceção que carreguem o payload bruto

## 3. Correção crítica: shutdown gracioso e outage visível

- [x] 3.1 Adicionar `join(30s)` em `SqsEventoAutorizacaoListener.stop()` após o `interrupt()`, com log de aviso quando o tempo se esgotar
- [x] 3.2 Mover o tratamento de falha catastrófica para `loopDeConsumo()`, envolvendo a chamada a `pollOnce()` com `catch (Throwable)` + backoff, mantendo `pollOnce()` com `catch (Exception)` e package-private
- [x] 3.3 Expor no listener a liveness real da thread de polling (distinta da flag `running`)
- [x] 3.4 Criar `SqsListenerHealthIndicator` em `entrypoint/sqs/` reportando `UP` (ativo + thread viva), `DOWN` (ativo + thread morta) e `UP` (parado — shutdown intencional não é outage)
- [x] 3.5 Criar `SqsListenerHealthIndicatorTest` cobrindo os três estados
- [x] 3.6 Adicionar a `SqsEventoAutorizacaoListenerTest` os cenários de `stop()` aguardando a thread e de `Error` não encerrando o loop
- [x] 3.7 Rodar `mvn clean package` e confirmar que os testes passam

## 4. Movimentação: domain/enums e shared/exceptions

- [x] 4.1 Mover `StatusAutorizacao` e `TipoEventoAutorizacao` de `application/eventos/` para `domain/enums/`, atualizando o `package` e todos os imports
- [x] 4.2 Remover a anotação `@NoArgsConstructor` (morta) de `StatusAutorizacao` e tornar o campo `statusAutorizacao` `final` — `domain/` deve ser livre de framework
- [x] 4.3 Mover `StatusAutorizacaoTest` e `TipoEventoAutorizacaoTest` para o pacote de teste espelho `domain/enums/`
- [x] 4.4 Mover `EventoAutorizacaoInvalidoException` (de `application/eventos/`) e `EventoAutorizacaoKafkaIndisponivelException` (de `infrastructure/kafka/`) para `shared/exceptions/`, atualizando `package` e imports
- [x] 4.5 Rodar `mvn clean package` e confirmar compilação e testes

## 5. Movimentação: porta de saída e adaptador Kafka

- [x] 5.1 Criar a interface `PublicadorEventoAutorizacao` em `application/eventos/` declarando a operação de publicação (key, evento Avro, tipoEvento)
- [x] 5.2 Mover `KafkaEventoAutorizacaoProducer` de `infrastructure/kafka/` para `application/eventos/` e fazê-lo implementar a porta
- [x] 5.3 Trocar a dependência de `ProcessarEventoAutorizacaoUseCase` da classe concreta para a interface da porta
- [x] 5.4 Mover `KafkaEventoAutorizacaoProducerTest` para o pacote de teste espelho `application/eventos/`
- [x] 5.5 Confirmar que `ProcessarEventoAutorizacaoUseCase` não importa mais `org.apache.kafka.*` nem a classe concreta do adaptador
- [x] 5.6 Rodar `mvn clean package` e confirmar compilação e testes

## 6. Movimentação: listener para entrypoint

- [x] 6.1 Mover `SqsEventoAutorizacaoListener` e `SqsListenerHealthIndicator` de `infrastructure/sqs/` para `entrypoint/sqs/`, atualizando `package` e imports
- [x] 6.2 Mover `SqsEventoAutorizacaoListenerTest` e `SqsListenerHealthIndicatorTest` para o pacote de teste espelho `entrypoint/sqs/`
- [x] 6.3 Remover os diretórios `infrastructure/sqs/` e `infrastructure/kafka/` (main e test), confirmando que o pacote `infrastructure` deixou de existir
- [x] 6.4 Rodar `mvn clean package` e confirmar compilação e testes

## 7. Verificação

- [x] 7.1 `mvn clean package` completo: todos os testes verdes e gate JaCoCo de 80% cumprido
- [x] 7.2 Confirmar que a árvore de pacotes contém apenas `entrypoint/`, `application/`, `domain/` e `shared/`
- [x] 7.3 Confirmar por `diff` que `apps/autorizacaostatus-producer/src/main/resources/avro/EventoAutorizacao.avsc` continua idêntico ao de `apps/eventos-consumer`
- [x] 7.4 Subir a aplicação localmente (`mvn spring-boot:run`) e confirmar `/actuator/health` respondendo `200 (UP)` com o listener ativo
- [x] 7.5 Revalidar a mudança com o agent `java-especialista`, confirmando que os cinco achados bloqueantes foram corrigidos e que nenhum achado novo foi introduzido

## 8. Documentação

- [x] 8.1 Atualizar a seção "Arquitetura (hexagonal)" e o "Comece por aqui" de `apps/autorizacaostatus-producer/CLAUDE.md` com a nova estrutura de pacotes e os novos arquivos
- [x] 8.2 Atualizar a seção "Exceções e tratamento de erros" e o diagrama de fluxo do `CLAUDE.md` (validação de campos obrigatórios, `join()` no shutdown, health indicator, logs sem body)
- [x] 8.3 Atualizar a armadilha crítica #9 do `CLAUDE.md`, que cita o local antigo dos enums
- [x] 8.4 Replicar todas as alterações em `apps/autorizacaostatus-producer/AGENTS.md` (espelho — manter idêntico ao `CLAUDE.md`)

## 9. Correções da revalidação (java-especialista reprovou a 1ª rodada)

- [x] 9.1 Validar `precision` de `valor`/`valor_limite` em `AutorizacaoEventoPayloadValidator` (máx. 15 dígitos inteiros, conforme `decimal(17,2)` do `.avsc`) — decimal fora da faixa hoje estoura `SerializationException` síncrona dentro de `send()` e escapa da classificação
- [x] 9.2 Classificar `SerializationException` em `KafkaEventoAutorizacaoProducer.produzir()` como `EventoAutorizacaoInvalidoException` (problema de payload, não de broker); demais falhas continuam retryable
- [x] 9.3 Parar de propagar o `cause` de exceções de terceiros em `ProcessarEventoAutorizacaoUseCase` — a mensagem do Jackson (`MismatchedInputException`) embute o valor do campo, e `id_pessoa_*`/`valor` são PII; usar `getPathReference()` (caminho sem valor) e o nome da classe da exceção
- [x] 9.4 Reduzir `TIMEOUT_ENCERRAMENTO` para 25s e declarar `spring.lifecycle.timeout-per-shutdown-phase: 40s` no `application.yaml` — margem determinística contra o default de 30s do `DefaultLifecycleProcessor`
- [x] 9.5 Tornar `pollingThread` `volatile` e atribuí-lo antes de `running = true` em `start()` — publicação segura para o health indicator (janela de falso-negativo)
- [x] 9.6 Trocar o `Thread.sleep(300)` de `errorNoCicloNaoEncerraAThreadDePolling` por espera baseada em condição
- [x] 9.7 Testes para os dois gaps críticos: decimal fora de precision e ausência de PII no `cause` da exceção
- [x] 9.8 Atualizar `design.md` e `specs/publicacao-eventos-kafka/spec.md` para 8 campos obrigatórios + validação de precision
- [x] 9.9 `mvn clean verify` e reinvocar o agent `java-especialista`

## 10. Correções da 2ª e 3ª revalidação (classificação de falha do produce)

- [x] 10.1 Inspecionar a causa da `SerializationException` em vez de classificá-la sempre como payload inválido — Schema Registry indisponível estava virando ack e perda definitiva de mensagem válida
- [x] 10.2 Ampliar o `catch` de `SerializationException` para `KafkaException`: `TimeoutException`, `DisconnectException` e `ThrottlingQuotaExceededException` do despacho por status HTTP do cliente Confluent escapavam sem classificação
- [x] 10.3 Inverter a direção da classificação — retryable como default, não-retryable apenas para causa comprovada de dado (`AvroRuntimeException`/`ClassCastException`); enumerar indisponibilidade é frágil, enumerar dado inválido é verificável
- [x] 10.4 Proteger a percorrida da cadeia de causas contra ciclo (`IdentityHashMap`)
- [x] 10.5 Testes dos ramos: `RestClientException`(500), `TimeoutException`(503), `KafkaException` genérica, cadeia cíclica, `AvroRuntimeException`, `IOException` aninhada, ausência de cause na falha de schema
- [x] 10.6 Alinhar `specs/publicacao-eventos-kafka/spec.md` com a direção invertida e os novos cenários
- [x] 10.7 `mvn clean verify` (62/62, JaCoCo OK) e revalidação final pelo `java-especialista` — **APROVADO**
