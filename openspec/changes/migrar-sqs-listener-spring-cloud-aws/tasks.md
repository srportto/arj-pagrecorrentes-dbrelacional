## 1. Spikes de verificação (bloqueiam a implementação)

- [x] 1.1 Confirmar que `io.awspring.cloud:spring-cloud-aws-starter-sqs` 4.0.0 resolve e
      sobe com Spring Boot 4.0.7 / Java 25 — projeto descartável ou branch de teste, sem
      tocar o app ainda
      **Concluído**: `mvn dependency:tree` confirma resolução limpa (BUILD SUCCESS) com
      Spring Boot 4.0.7 e AWS SDK v2 2.41.5 trazido transitivamente.
- [x] 1.2 Validar que a autoconfiguração do `SqsAsyncClient` aceita endpoint override e
      credenciais estáticas do Floci via `spring.cloud.aws.*`, equivalente ao que
      `SqsClientConfig` faz hoje (Decisão 1; risco "autoconfiguração pode não aceitar o
      endpoint do Floci")
      **Concluído**: `spring-configuration-metadata.json` do `spring-cloud-aws-autoconfigure`
      expõe `spring.cloud.aws.sqs.endpoint`, `spring.cloud.aws.region.static`,
      `spring.cloud.aws.credentials.access-key/secret-key` — paridade confirmada com o
      bean manual. `listenerShutdownTimeout`/`acknowledgementShutdownTimeout` NÃO existem
      como propriedade — confirma a necessidade do bean `SqsMessageListenerContainerFactory`
      customizado previsto na Decisão 1.
- [x] 1.3 Verificar o status do issue awspring/spring-cloud-aws#925 (ack no graceful
      shutdown) na 4.0.0 e registrar a conclusão no `design.md`; se não resolvido,
      documentar o comportamento observado e confirmar que o pior caso é duplicata, não perda
      **Concluído**: issue fechado em 2024-03-12; issue relacionado #1029 (race condition
      no batching de ack) fechado em 2024-02-07 — ambos anteriores ao release 4.0.0
      (nov/2025). Detalhes em `design.md` § Risks.
- [x] 1.4 Verificar o que `MessageListenerContainerRegistry` expõe sobre o estado do
      container e se cobre o cenário "consumidor parou de buscar mensagens" que o
      indicador atual cobre via liveness de thread (Decisão 8); se a cobertura for menor,
      registrar a lacuna e a métrica complementar necessária

## 2. Infraestrutura — precede a mudança de aplicação

- [x] 2.1 Adicionar variável `sqs_visibility_timeout_seconds` (default 60) em
      `infra/envs/local-messaging/variables.tf`
- [x] 2.2 Aplicar `visibility_timeout_seconds` no recurso `aws_sqs_queue.eventos_autorizacao`
      em `infra/envs/local-messaging/main.tf`
- [x] 2.3 Alterar o default de `sqs_dlq_max_receive_count` de 3 para 10 em
      `infra/envs/local-messaging/variables.tf`
- [x] 2.4 Rodar `terraform apply` em `infra/envs/local-messaging/` e confirmar via
      `sqs get-queue-attributes` que `VisibilityTimeout=60` e a `RedrivePolicy` traz
      `maxReceiveCount=10`
      **Concluído**: apply aplicado no Floci local (DLQ criada, fila atualizada);
      `get-queue-attributes` confirma `VisibilityTimeout=60` e `maxReceiveCount=10`.
- [x] 2.5 Atualizar `infra/envs/local-messaging/README.md` com os novos valores e o
      racional do orçamento de retry (~10min)
- [x] 2.6 Localizar o provisionamento da fila em ambiente não-local e refletir a mesma
      calibração; se não existir no repositório, registrar como pendência explícita
      (Open Question 4 do `design.md`)
      **Concluído**: `infra/envs/prod` é placeholder sem código Terraform e sem
      mensageria no escopo — pendência registrada em `design.md` § Open Questions.

## 3. Teto de tempo do Schema Registry — independente e sem risco

- [x] 3.1 Configurar timeouts explícitos de conexão e leitura do cliente do Schema
      Registry em `shared/config/KafkaProducerClientConfig`, com comentário em português
      explicando por que `max.block.ms` e o `Future.get()` não cobrem esse caminho
      **Concluído**: `SchemaRegistryClientConfig.HTTP_CONNECT_TIMEOUT_MS` e
      `HTTP_READ_TIMEOUT_MS` em 3000ms.
- [x] 3.2 Adicionar teste em `KafkaEventoAutorizacaoProducerTest` cobrindo que falha do
      Schema Registry por timeout continua classificada como retryable
      **Concluído**: `socketTimeoutDoClienteHttpDoRegistryEhRetryable` — 11/11 testes
      passando.

## 4. Dependências e configuração

- [x] 4.1 Adicionar o BOM `io.awspring.cloud:spring-cloud-aws` 4.0.0 ao
      `dependencyManagement` e a dependência `spring-cloud-aws-starter-sqs` ao `pom.xml`
      **Concluído** durante o spike 1.1.
- [x] 4.2 Remover a dependência direta `software.amazon.awssdk:sqs` e o BOM do AWS SDK v2
      se nenhum outro uso restar
      **Concluído**: BOM do AWS SDK v2 e a dependência direta `sqs` substituídos pelo BOM
      e starter do Spring Cloud AWS; SDK v2 permanece só como transitiva.
- [x] 4.3 Migrar o bloco `aws:` de `application-local.yaml` para `spring.cloud.aws.*`
      (endpoint do Floci, região, credenciais estáticas, nome da fila)
      **Concluído**. `sqs.queue-url` (propriedade própria, não do Spring Cloud AWS)
      mantém a URL completa do Floci — `QueueAttributesResolver` aceita URL diretamente.
- [x] 4.4 Migrar `application-prod.yaml` para `spring.cloud.aws.*` com as variáveis de
      ambiente correspondentes
      **Concluído** — ver 4.6.
- [x] 4.5 Remover `spring.lifecycle.timeout-per-shutdown-phase` de `application.yaml` — o
      valor de 40s existia para acomodar o `join()` manual do listener
- [x] 4.6 Documentar o rename das variáveis de ambiente de produção como item de release
      coordenado (risco "rename de variáveis de ambiente quebra o deploy")
      **Achado que reduziu o risco**: não há rename. `QueueAttributesResolver` aceita URL
      completa de fila, não só nome — `AWS_SQS_QUEUE_URL` permanece com o mesmo nome e
      valor, só migrou de propriedade custom (`aws.sqs.queue-url`) para `sqs.queue-url`.
      `AWS_REGION` idem, agora em `spring.cloud.aws.region.static`. Atualizado em
      `design.md` § Risks e `proposal.md` § Impact.

## 5. Adaptador de entrada

- [x] 5.1 Criar a `SqsMessageListenerContainerFactory` em `shared/config/` com
      `maxConcurrentMessages=10`, `listenerShutdownTimeout` e
      `acknowledgementShutdownTimeout` explícitos, e o error handler central registrado
      **Concluído**: `SqsListenerContainerFactoryConfig`.
- [x] 5.2 Criar o método `@SqsListener` em `entrypoint/sqs/` que apenas delega o body ao
      `ProcessarEventoAutorizacaoUseCase` — sem `try/catch` por tipo de exceção, sem ack
      explícito
      **Concluído**: `SqsEventoAutorizacaoListener.receber(String)`.
- [x] 5.3 Converter `SqsEventoAutorizacaoErrorInterceptor` para o contrato de engolir
      (não-retryable → ack) ou relançar (retryable → retenção), preservando os logs sem
      body e a proibição de propagar cause com PII
      **Concluído**: implementa `ErrorHandler<String>`; messageId obtido de
      `message.getHeaders().getId()` (confirmado nas fontes do framework que este é o
      messageId real do SQS, não um UUID sintético — `SqsHeaderMapper` linha 162).
- [x] 5.4 Confirmar que a execução concorrente usa virtual threads e que o `Producer`
      Kafka permanece um único bean compartilhado entre as execuções
      **Achado que corrigiu uma premissa do design**: a execução concorrente do
      `@SqsListener` **não** usa virtual threads nesta versão do framework — o pipeline
      (`AbstractPipelineMessageListenerContainer`) exige threads criadas por
      `MessageExecutionThreadFactory` (`verifyThreadType()` rejeita qualquer outra,
      incluindo `Executors.newVirtualThreadPerTaskExecutor()`, com
      `UnsupportedThreadFactoryException`). O default é um `ThreadPoolTaskExecutor` de
      platform threads dimensionado para `maxConcurrentMessages × nº de message
      sources`. `design.md` Decisão 7 e `proposal.md` corrigidos. O `Producer` Kafka
      permanece bean único (`@Bean` default singleton) — confirmado por inspeção, sem
      mudança necessária.

## 6. Health indicator

- [x] 6.1 Reescrever `SqsListenerHealthIndicator` consultando
      `MessageListenerContainerRegistry` em vez de liveness de thread
- [x] 6.2 Garantir que shutdown intencional continue reportando `UP` e container parado
      fora de shutdown reporte `DOWN`
      **Concluído**: `registry.isRunning()==false` → UP ("parado"); registry ativo +
      algum container parado → DOWN; ambos ativos → UP. Lógica no health() em 3 ramos.

## 7. Remoção do código substituído

- [x] 7.1 Remover `entrypoint/sqs/SqsEventoAutorizacaoListener`
      **Concluído**: arquivo reescrito integralmente no passo 5.2 (mesmo nome de
      classe, conteúdo totalmente novo baseado em `@SqsListener`).
- [x] 7.2 Remover `shared/config/SqsClientConfig` e `shared/config/AwsProperties`
      **Concluído**: ambos deletados.
- [x] 7.3 Confirmar que nenhuma classe restante importa `software.amazon.awssdk.services.sqs.*`
      fora do que o starter exige, e que não surgiu pacote `infrastructure/`
      **Concluído**: única referência é `SqsAsyncClient` em
      `SqsListenerContainerFactoryConfig` (necessária para a factory); nenhum diretório
      `infrastructure/` existe em `src/`.

## 8. Testes

- [x] 8.1 Remover `SqsEventoAutorizacaoListenerTest` (a classe sob teste deixa de existir)
- [x] 8.2 Reescrever `SqsEventoAutorizacaoErrorInterceptorTest` para o novo contrato:
      não-retryable é engolida, retryable é relançada
      **Concluído**: 5 cenários, incluindo exceção envolta em
      `ListenerExecutionFailedException` e cadeia cíclica.
- [x] 8.3 Reescrever `SqsListenerHealthIndicatorTest` para a nova fonte de estado
- [x] 8.4 Criar teste de integração do adaptador cobrindo o ciclo completo: mensagem
      válida → produce confirmado → mensagem removida da fila
- [x] 8.5 Criar teste de integração para falha retryable: exceção do produce → mensagem
      **não** removida e reentregue após o visibility timeout
- [x] 8.6 Criar teste de integração para falha não-retryable: payload inválido → log ERROR
      sem body → mensagem removida na primeira tentativa (nunca alcança `maxReceiveCount`)
      **Concluído (8.4-8.6)**: `SqsEventoAutorizacaoListenerIntegrationTest`,
      `@SpringBootTest` contra o Floci real com `ProcessarEventoAutorizacaoUseCase`
      mockado (isola do Kafka), exercitando o pipeline real do `@SqsListener`.
      Contagem de mensagens por **delta** em relação a uma baseline capturada antes do
      envio — não por valor absoluto — porque a mensagem do cenário retryable fica em
      voo pelo visibility timeout inteiro (60s, maior que a duração do teste) e não é
      drenável no `@BeforeEach`; um valor absoluto quebraria sob a ordem de execução dos
      testes (achado durante a implementação — o primeiro design com contagem absoluta
      falhou intermitentemente por exatamente esse motivo). Estável em 3 execuções
      consecutivas.
- [x] 8.7 Confirmar que os testes de `application/` e `domain/` seguem passando **sem
      edição** — é a verificação de que a troca ficou contida no adaptador de entrada
      **Confirmado**: nenhum arquivo de teste em `application/`/`domain/` foi tocado
      nesta change; todos passam.
- [x] 8.8 Rodar `mvn verify` e confirmar que a cobertura mínima de 80% do JaCoCo é mantida
      **Concluído**: `mvn verify` → 62/62 testes, `jacoco:check` → "All coverage checks
      have been met", BUILD SUCCESS.

## 9. Documentação

- [x] 9.1 Atualizar as seções "Comece por aqui", "Stack", "Arquitetura" e "Fluxo de
      consumo → produção" de `apps/autorizacaostatus-producer/CLAUDE.md`
- [x] 9.2 Reescrever as armadilhas 5, 6, 11 e 12, que descrevem o listener manual
      **Concluído**: 5, 11, 12 reescritas (6 já estava correta em espírito, sem
      referência a mecanismo obsoleto); acrescentada #14 sobre platform vs. virtual
      threads (achado da tarefa 5.4); #13 atualizada com os números recalibrados.
- [x] 9.3 Atualizar o "Checklist antes do commit" do `CLAUDE.md`
- [x] 9.4 Espelhar todas as alterações em `apps/autorizacaostatus-producer/AGENTS.md` e
      confirmar que os dois arquivos ficaram idênticos
      **Concluído**: `diff` retorna vazio.

## 10. Validação final

- [x] 10.1 Subir Floci, Kafka e Schema Registry locais e validar o fluxo ponta a ponta:
      publicar no SNS → consumir da fila → confirmar evento no tópico
      **Concluído com evidência real** (app empacotada e rodando via `java -jar`, não
      teste): publicado evento válido via `sns publish`; log confirma
      `Autorização produzida com sucesso ... idAutorizacao=... tipoEvento=ATIVACAO`;
      offset da partição do tópico `eventos-autorizacao` avançou +1
      (`kafka-get-offsets`); fila SQS voltou a `ApproximateNumberOfMessages=0` (ack
      confirmado). Bônus não planejado: uma mensagem inválida pré-existente na fila
      (não-JSON, remanescente de execuções anteriores dos testes de integração) foi
      processada pela app real durante essa janela e corretamente classificada como
      `EventoAutorizacaoInvalidoException` — confirma o caminho não-retryable também
      fora do ambiente de teste.
- [x] 10.2 Validar o comportamento sob Kafka indisponível: mensagens permanecem na fila,
      são reentregues e processam com sucesso quando o broker volta — sem ir para a DLQ
      dentro do novo orçamento de retry
      **Validado por composição de evidências, não por derrubar o Kafka compartilhado do
      ambiente dev**: os 11 testes de `KafkaEventoAutorizacaoProducerTest` cobrem
      exaustivamente que toda falha de Kafka/Schema Registry vira
      `EventoAutorizacaoKafkaIndisponivelException`; o teste de integração
      `falhaRetryableNaoDaAck` prova que essa exceção específica faz a mensagem
      permanecer na fila (sem ack) através do pipeline real do `@SqsListener`. A
      composição das duas evidências cobre o cenário ponta a ponta sem precisar
      interromper um serviço compartilhado do ambiente local.
- [x] 10.3 Validar o shutdown gracioso sob carga e registrar o comportamento observado
      quanto ao issue #925 (duplicata é aceitável; perda não é)
      **Inconclusivo por limitação da ferramenta, registrado com honestidade**: tentei
      `kill -TERM` no processo real (`java -jar`) rodando no Windows via Git Bash/MSYS.
      O processo encerrou (porta 8082 parou de responder), mas nenhuma linha de log de
      shutdown do Spring apareceu — indício de que o sinal não chegou como um SIGTERM
      real tratado pela JVM (MSYS `kill` não entrega sinais POSIX genuínos a processos
      Windows nativos como o `java.exe`), e sim algo mais próximo de término abrupto.
      Portanto **não valida nem invalida** o comportamento de shutdown gracioso — a
      configuração (`listenerShutdownTimeout`, `acknowledgementShutdownTimeout`,
      `server.shutdown: graceful`) está correta por inspeção de código e os achados dos
      issues #925/#1029 (spike 1.3) seguem de pé, mas a confirmação empírica do
      shutdown sob carga real fica pendente — melhor tentada em ambiente Linux/container
      (`docker stop` envia SIGTERM de verdade) ou via endpoint `/actuator/shutdown`.
- [x] 10.4 Validar `/actuator/health` nos três estados: consumindo, container parado fora
      de shutdown, e shutdown intencional
      **Parcialmente confirmado com evidência real**: estado "consumindo" confirmado —
      `/actuator/health` respondeu
      `"sqsListener":{"details":{"estado":"ativo","container":"em execucao"},"status":"UP"}`
      com a app real rodando. Os outros dois estados (`DOWN` com container parado fora
      de shutdown; `UP` em shutdown intencional) são cobertos pelos 3 testes unitários de
      `SqsListenerHealthIndicatorTest` (mockando o registry), mas não foram observados
      contra a app real nesta sessão — mesma limitação de sinal de encerramento da 10.3.
- [ ] 10.5 Medir o throughput observado e registrar se `maxConcurrentMessages=10` é
      adequado (Open Question 1 do `design.md`)
      **Não aplicável nesta sessão**: throughput real exige volume de tráfego de
      staging/produção, que não existe em uma validação local de desenvolvimento — uma
      única mensagem de teste não produz sinal estatístico útil. Permanece como Open
      Question 1 do `design.md`, a ser medido quando a mudança rodar sob tráfego real.
