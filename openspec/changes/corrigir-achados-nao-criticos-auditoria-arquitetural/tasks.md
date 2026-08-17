## 1. contratocommand

- [x] 1.1 Criar `CancelamentoResponseDto` (record) em `infrastructure/web/contratosrest/` e mapear
      explicitamente em `AutorizacaoCompletaResponseDto` (hoje expõe `domain/model/Cancelamento`
      direto)
- [x] 1.2 Implementar `Autorizacao.aprovar()`, `rejeitarPeloPagador()`, `expirarJornada1()` e
      `cancelar(Cancelamento)` em `domain/model/Autorizacao`, cada um fixando o par
      status+motivoStatus correspondente
- [x] 1.3 Atualizar `DecidirAutorizacaoService` e `CancelarAutorizacaoService` para chamar os novos
      métodos do modelo em vez de `setStatus`/`setMotivoStatus`/`setCancelamento` diretos
- [x] 1.4 Tipar `CriarAutorizacaoCommand.tipoProduto` como `TipoProduto` (hoje `String`); resolver o
      enum no `AutorizacaoController.insert()`, igual às rotas de cancelar/decidir já fazem
- [x] 1.5 Ajustar `AutorizacaoMapper`/`ProdutoSuportado`/consumidores do comando de criação para o
      tipo `TipoProduto` tipado
- [x] 1.6 Reescrever `ValorLimiteContrato` sem `switch` sobre literais de produto — limite como
      atributo de `TipoProduto` ou rule filtrada por `aceita(comando)`, com constante nomeada
- [x] 1.7 Remover `@Data` de `AutorizacaoJpaEntity`, `IdAutorizacaoJpaEmbeddable` e
      `CancelamentoJpaEmbeddable`; substituir por `@Getter @Setter @NoArgsConstructor
      @AllArgsConstructor`
- [x] 1.8 Remover `SpringDataAutorizacaoRepository.findByStatus`/`findByIdAutorizacao` (sem
      chamador de produção, varrem partições sem poda)
- [x] 1.9 Remover `IdContaUUIDPartitionDistributor.getPartitionPrecision` e
      `ControleExpurgoAutorizacao.obterParticaoExpurgoDrop` (só usados em teste) — ajustar os testes
      correspondentes na mesma tarefa
- [x] 1.10 Trocar `Math.abs(hash) % 889` por `Math.floorMod(hash, 889)` em
      `IdContaUUIDPartitionDistributor`; extrair `889` como constante nomeada compartilhada pelos
      dois métodos que a usam
- [x] 1.11 Trocar o `{@link AutorizacaoEventoPublisher}` do javadoc de
      `domain/event/AutorizacaoPersistidaEvent` por texto neutro, sem apontar para
      `infrastructure`
- [x] 1.12 Resolver o comentário contraditório em `AutorizacaoJpaEntity` (linhas ~20-22 vs ~58-59)
      sobre onde a unicidade parcial é declarada
- [x] 1.13 Avaliar `ExpurgoAutorizacaoService`: dar comportamento próprio (ex.: decidir data de
      referência) ou remover a indireção e chamar `AutorizacaoRepository` direto dos use cases
- [x] 1.14 Atualizar `CLAUDE.md`/`AGENTS.md` (idênticos) documentando as duas ampliações existentes
      da exceção "domínio sem Spring" (`@Service` nos validadores, Jackson em `MetadadoRule`) — hoje
      só o `design.md` arquivado registra
- [x] 1.15 `mvn clean compile` e `mvn test` (exige PostgreSQL local — `infra/local/postgres/`) —
      confirmar suíte verde com a mesma contagem de testes mais os que cobrem o comportamento novo
      do modelo

## 2. contratoquery

- [x] 2.1 `TipoProdutoConverter`/`TipoJornadaAutorizacaoConverter`: capturar
      `IllegalArgumentException` do enum e relançar como `ApplicationException` (500), preservando
      a causa — hoje propaga como `BusinessException` (422)
- [x] 2.2 Criar `StatusAutorizacaoConverter` (`@Convert`, mesmo padrão dos outros dois); tipar
      `Autorizacao.status` como `StatusAutorizacao`; remover `mapearStatus(Integer)` duplicado dos
      dois DTOs de resposta
- [x] 2.3 Mover o parse de `metadados` (hoje `ObjectMapper` próprio por DTO, falha engolida em
      `catch (Exception) { return null; }`) para `AutorizacaoPersistenceMapper` ou um `@Convert`
      dedicado; se permanecer na borda, usar `ObjectMapper` único (bean em
      `infrastructure/config/`) e `log.warn` na falha
- [x] 2.4 Trocar `@JoinColumn` por `@Column` (com nome explícito) em `IdAutorizacaoJpaEmbeddable` e
      `CancelamentoJpaEmbeddable`
- [x] 2.5 Remover o espaço à direita de `"indicador_tipo_mensageria "` em `AutorizacaoJpaEntity`
      (alinhar com o `contratocommand`, já corrigido)
- [x] 2.6 Remover `@Data` de `AutorizacaoJpaEntity` e dos embeddables; `@Getter @Setter
      @NoArgsConstructor` na entidade, `equals`/`hashCode` explícitos no `@EmbeddedId`
- [ ] 2.7 Converter `AutorizacaoDetalheResponseDto`, `AutorizacaoResumidaResponseDto`,
      `PaginacaoResponseDto` e `LayoutErrosApiResponse` de `@Data` para `record`
- [x] 2.8 Remover `@NoArgsConstructor` (Lombok) de `StatusAutorizacao`, `TipoProduto`,
      `TipoJornadaAutorizacao` (sem efeito nos enums)
- [x] 2.9 Simplificar o ramo inalcançável em `ListarAutorizacoesService` (`tamanhoFinal == 0` já
      rejeitado antes) para `(int) Math.ceil(...)` direto
- [x] 2.10 Atualizar `CLAUDE.md`/`AGENTS.md` (linha ~134): o fluxo de ordenação mapeia para
      `CampoOrdenacao` (domínio), não mais para "campo JPA" — desatualizado desde a correção do
      crítico já aplicada
- [x] 2.11 `mvn clean compile` e `mvn test` (exige PostgreSQL local) — confirmar suíte verde,
      rodando contra a massa sintética (`infra/local/postgres/gerar-massa-sintetica-representativa.sql`)
      para validar os converters contra dado real

## 3. autorizacaostatus-producer

- [x] 3.1 Restringir os três `catch (RuntimeException)` de `SqsEventoAutorizacaoListener`
      (desserialização, derivação de tipo de evento, conversão para domínio) aos tipos realmente
      esperados (`JacksonException`, `IllegalArgumentException`, tipo específico do converter) —
      qualquer outra exceção deve subir como retryable
- [x] 3.2 Documentar em `CLAUDE.md`/`AGENTS.md` que `KafkaEventoAutorizacaoProducer` é uma segunda
      origem de `EventoAutorizacaoInvalidoException` (cadeia de causa Avro/`ClassCastException`),
      hoje só descrita como responsabilidade exclusiva do listener
- [x] 3.3 Remover `StatusAutorizacao.TRANSICOES`/`podeTransicionarPara` (sem chamador de produção
      nesta app-ponte); manter `obterStatusEnumPorIdStatus`; ajustar `StatusAutorizacaoTest`
- [x] 3.4 Expor `ObjectMapper` como `@Bean` em `infrastructure/config/` em vez de instanciado com
      `new` no listener
- [x] 3.5 Corrigir NPE latente em `SqsEventoAutorizacaoErrorInterceptor`
      (`message.getHeaders().getId()` pode ser `null`) com `Optional.ofNullable(...).map(UUID::toString).orElse("desconhecido")`
- [x] 3.6 Ajustar a redação do `CLAUDE.md`/`AGENTS.md` sobre "produção idempotente" — `enable.idempotence`
      cobre só retries internos do producer; declarar explicitamente que a deduplicação por key
      SHA-256 é responsabilidade do consumidor
- [x] 3.7 Documentar `GET_TIMEOUT_SECONDS=20` do `Future.get()` junto aos demais timeouts já
      listados no `CLAUDE.md`/`AGENTS.md`
- [x] 3.8 `mvn clean compile` e `mvn test` (exige Floci + Kafka local) — confirmar suíte verde,
      incluindo o teste de integração do listener

## 4. eventos-consumer

- [x] 4.1 Criar `domain/exception/EventoAutorizacaoInvalidoException`; usar em
      `StatusAutorizacao.obterStatusEnumPorIdStatus` e `TipoEventoAutorizacao.porStatus` no lugar
      de `IllegalArgumentException` genérica
- [x] 4.2 `KafkaConsumerConfig`: registrar `errorHandler.addNotRetryableExceptions(EventoAutorizacaoInvalidoException.class)`
      no `DefaultErrorHandler`, para status desconhecido ir direto à DLT sem retry+backoff inútil
- [x] 4.3 Remover `@NoArgsConstructor` (Lombok) de `StatusAutorizacao`; tornar
      `statusAutorizacao` `final` (alinhar ao `autorizacaostatus-producer`, que já faz certo)
- [x] 4.4 Unificar a configuração de `group-id` num único caminho (record de config **ou** atributo
      da anotação `@KafkaListener`, não os dois)
- [x] 4.5 Documentar no `CLAUDE.md`/`AGENTS.md` que a ausência de vazamento de PII no log de erro do
      Kafka depende do formatter default (`KafkaUtils.format`), não de configuração explícita —
      registrar o risco de regressão silenciosa se o default mudar
- [x] 4.6 `mvn clean compile` e `mvn test` — confirmar suíte verde

## 5. temporiza-autorizacao

- [x] 5.1 Mover `PendenciasSchedulerReivindicador` de `infrastructure/messaging/` para
      `infrastructure/scheduler/` (mudança mecânica de pacote — a classe já é `@Scheduled`,
      conforme a própria convenção documentada no `CLAUDE.md`)
- [x] 5.2 `ExpiracaoStreamListener`: classificar explicitamente qualquer `RuntimeException` (não só
      `ExpiracaoRetryavelException`) no mesmo ponto central, com log estruturado
      (`streamId`/`idAutorizacao`) antes de decidir XACK/retenção
- [x] 5.3 Restringir os `catch` mudos de `PendenciasSchedulerReivindicador` e
      `ConsumidoresOrfaosLimpezaScheduler` ao caso real esperado (`NOGROUP`/stream inexistente);
      logar (`log.warn`) qualquer outra exceção em vez de engolir
- [x] 5.4 Trocar `ZoneId.systemDefault()` por `ZoneOffset.UTC` explícito em
      `AgendarExpiracaoService`
- [x] 5.5 Criar tipo em `domain/model` para a regra "vencimento = data_hora_inclusao + prazo" (ex.:
      `Agendamento` ou `CalculadoraVencimento`); mover o cálculo de `AgendarExpiracaoService` para
      lá
- [x] 5.6 Tipar `ProcessarExpiracaoUseCase.processar(UUID)` (hoje recebe `String` cru); mover o
      parse de `String` para `UUID` para o adaptador (`ExpiracaoStreamListener`/reivindicador)
- [x] 5.7 Expor `ObjectMapper` como bean em vez de `new ObjectMapper()` no listener
- [x] 5.8 Renomear `ConsumidorRemocaoService` (sufixo `*Service` fora de `application/usecase`,
      inconsistente com o resto do monorepo) para algo como `ConsumidorStreamRemovedor`
- [x] 5.9 Substituir o literal `"id_autorizacao"` duplicado em
      `PendenciasSchedulerReivindicador`/`AutorizacaoEventoPayload`/`varredura.lua` pela constante
      já existente em `ExpiracaoStreamListener`
- [x] 5.10 `mvn clean compile` e `mvn test` (exige Floci + Valkey local) — confirmar suíte verde,
      incluindo o teste de concorrência com múltiplas instâncias

## 6. Verificação final

- [x] 6.1 Subir o ambiente local completo (`docker compose up -d --build` na raiz) e confirmar as 5
      apps `healthy`
- [x] 6.2 Rodar `mvn clean verify` em cada um dos 5 módulos com a infra correspondente no ar,
      confirmando o gate de cobertura JaCoCo (mínimo 80%) onde configurado
- [ ] 6.3 Reinvocar o agent `java-revisor` em modo `auditoria` nas 5 aplicações (mesmo escopo da
      rodada original) e confirmar veredicto APROVADO sem achado crítico novo
