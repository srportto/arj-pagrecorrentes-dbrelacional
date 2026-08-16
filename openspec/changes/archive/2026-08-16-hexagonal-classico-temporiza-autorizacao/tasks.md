## 1. Linha de base antes de mover qualquer arquivo

- [x] 1.1 Rodar `mvn test` em `apps/temporiza-autorizacao` e registrar a contagem exata de testes **executados e pulados** — os três testes de integração dependem de Valkey e podem pular; um pulo novo tem de ser detectável
- [x] 1.2 Subir o Valkey local e rodar a suíte de novo, registrando a contagem com os testes de integração realmente executando
- [x] 1.3 Confirmar que `TemporizaAutorizacaoApplication` está na raiz `br.com.srportto.temporizaautorizacao`
- [x] 1.4 Levantar todo bean referenciado **por nome** (`containerFactory = "...",`, `@Qualifier`, `@DependsOn`) — são os que uma mudança de pacote pode quebrar sem erro de compilação
- [x] 1.5 Reler as decisões D1–D5 de `hexagonal-classico-eventos-consumer` (convenções herdadas de nomenclatura)

## 2. Domínio

- [x] 2.1 Mover `application/agendamento/AgendamentoRepository.java` para `domain/port/out/` (D5)
- [x] 2.2 Mover `application/expiracao/DecisaoAutorizacaoClient.java` para `domain/port/out/` (D5)
- [x] 2.3 Confirmar que nenhuma das duas importa `org.springframework.*` nem tipo de cliente HTTP/Valkey
- [x] 2.4 Mover `shared/exceptions/ExpiracaoRetryavelException.java` e `AgendamentoInvalidoException.java` para `domain/exception/` (D6)
- [x] 2.5 Corrigir o javadoc de `DecisaoAutorizacaoClient`, que referencia `ExpiracaoRetryavelException` pelo FQN antigo

## 3. Portas de entrada

- [x] 3.1 Criar `domain/port/in/AgendarExpiracaoUseCase.java` (interface) a partir da assinatura pública da classe atual
- [x] 3.2 Criar `domain/port/in/ProcessarExpiracaoUseCase.java` (interface)
- [x] 3.3 Criar `domain/port/in/VarrerAgendamentosVencidosUseCase.java` (interface)
- [x] 3.4 Ajustar a assinatura de `ProcessarExpiracaoUseCase` para receber tipos simples (`UUID`, `Instant`) em vez do record de transporte, se hoje ela receber `AutorizacaoEventoPayload` (D4)
- [x] 3.5 Confirmar que nenhuma das três interfaces importa framework

## 4. Application

- [x] 4.1 Mover `application/agendamento/AgendarExpiracaoUseCase` para `application/usecase/AgendarExpiracaoService`, implementando a porta
- [x] 4.2 Mover `application/expiracao/ProcessarExpiracaoUseCase` para `application/usecase/ProcessarExpiracaoService`, implementando a porta
- [x] 4.3 Mover `application/varredura/VarrerAgendamentosVencidosUseCase` para `application/usecase/VarrerAgendamentosVencidosService`, implementando a porta
- [x] 4.4 Confirmar que nenhuma das três importa de `infrastructure` nem tipo de Valkey/HTTP/SQS
- [x] 4.5 Remover os pacotes `application/{agendamento,expiracao,varredura,eventos}/`, agora vazios

## 5. Infraestrutura — portas de saída

- [x] 5.1 Mover `ValkeyAgendamentoRepository` para `infrastructure/persistence/` (D1)
- [x] 5.2 Mover `CommandDecisaoAutorizacaoClient` para `infrastructure/external/` (D1)
- [x] 5.3 Confirmar que as duas classes declaram `implements` da porta correspondente e que nada fora de `infrastructure` as referencia pelo tipo concreto

## 6. Infraestrutura — driving adapters e config

- [x] 6.1 Mover `entrypoint/sqs/TemporizacaoEventoListener` e `TemporizacaoEventoErrorInterceptor` para `infrastructure/messaging/`
- [x] 6.2 Mover `AutorizacaoEventoPayload` para `infrastructure/messaging/` (D4)
- [x] 6.3 Fazer o listener SQS traduzir o payload em argumentos simples antes de chamar a porta de entrada (D4)
- [x] 6.4 Mover `entrypoint/stream/ExpiracaoStreamListener`, `PendenciasSchedulerReivindicador` e `ConsumidorRemocaoService` para `infrastructure/messaging/`
- [x] 6.5 Mover `entrypoint/scheduler/VarreduraAgendamentoScheduler` e `entrypoint/stream/ConsumidoresOrfaosLimpezaScheduler` para `infrastructure/scheduler/` (D2)
- [x] 6.6 Mover `entrypoint/health/TemporizacaoHealthIndicator` para `infrastructure/web/` (D3)
- [x] 6.7 Trocar os tipos injetados em todos os driving adapters para as **interfaces** de porta de entrada
- [x] 6.8 Mover `entrypoint/stream/ValkeyStreamConfig` e `shared/config/{CommandClientConfig,SqsListenerContainerFactoryConfig,TemporizacaoProperties}` para `infrastructure/config/`
- [x] 6.9 Remover os pacotes `entrypoint/` e `shared/`, agora vazios
- [x] 6.10 Reconferir a lista de beans-por-nome de 1.4 e confirmar que todos ainda resolvem
- [x] 6.11 Rodar a skill `remover-imports-nao-usados`

## 7. Testes

- [x] 7.1 Mover os 14 arquivos de teste para os pacotes espelhados, renomeando os três `*UseCaseTest` para `*ServiceTest`
- [x] 7.2 Ajustar os testes dos driving adapters para mockar a **porta**, não a classe concreta
- [x] 7.3 Confirmar que nenhum teste foi adicionado nem removido

## 8. Verificação

- [x] 8.1 `mvn clean compile` sem erros nem warnings novos
- [x] 8.2 `mvn test` com a mesma contagem de **executados e pulados** registrada em 1.1
- [x] 8.3 Com Valkey local no ar, `mvn test` com a mesma contagem registrada em 1.2 — nenhum teste de integração pode ter passado a pular
- [x] 8.4 Inspeção: nenhuma classe de `domain/` importa `org.springframework.*`, `io.lettuce.*`, `redis.*` nem SDK AWS
- [x] 8.5 Inspeção: nenhuma classe de `application/` importa de `infrastructure`
- [x] 8.6 Inspeção: `domain/port/out/` contém só interfaces, e nenhuma implementação de porta vive no mesmo pacote da interface
- [x] 8.7 Teste ponta a ponta local: publicar o evento de recepção `PIX_AUTO`/`SPI_J1` na fila, confirmar agendamento no Valkey, esperar o vencimento e confirmar que o `PATCH /decisao` chega ao `contratocommand` com `acao: EXPIRAR`
- [x] 8.8 Confirmar que `GET /actuator/health` continua respondendo com o indicador de temporização (D3 moveu a classe)

## 9. Documentação

- [x] 9.1 Atualizar a seção de arquitetura de `apps/temporiza-autorizacao/CLAUDE.md` com a árvore nova
- [x] 9.2 Replicar **idêntico** em `apps/temporiza-autorizacao/AGENTS.md`
- [x] 9.3 Conferir os links de "Comece por aqui" e os caminhos citados no corpo dos dois arquivos
- [x] 9.4 Registrar no `design.md` desta mudança se D2 (`infrastructure/scheduler/`) ou D3 (health em `web/`) precisaram ser revistos na prática — as mudanças seguintes herdam a decisão
