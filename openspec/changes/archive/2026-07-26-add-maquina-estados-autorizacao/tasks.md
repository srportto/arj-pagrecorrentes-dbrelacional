# Tasks: add-maquina-estados-autorizacao

## 1. arj-contratocommand — máquina de estados e publicação derivada

- [x] 1.1 Evoluir `StatusAutorizacao` (`domain/enums/`) com o grafo de transições e `podeTransicionarPara(destino)`; adicionar/atualizar testes cobrindo transições válidas, inválidas e estados terminais
- [x] 1.2 Reescrever `TipoEventoAutorizacao` com os 8 valores (`RECEPCAO`, `PENDENCIA_ACEITE`, `INICIO_ATIVACAO`, `ATIVACAO`, `CANCELAMENTO`, `REJEICAO`, `EXPIRACAO`, `FINALIZACAO`) e a fábrica `porStatus(status)` (exceção para código desconhecido); testes da bijeção completa
- [x] 1.3 Remover o campo `tipo` de `AutorizacaoPersistidaEvent` e simplificar `CriarAutorizacaoUseCase`/`CancelarAutorizacaoUseCase` (publicam só a entidade); atualizar `CriarAutorizacaoUseCaseTest`/`CancelarAutorizacaoUseCaseTest`
- [x] 1.4 `AutorizacaoEventoPublisher`: derivar o attribute `tipoEvento` via `TipoEventoAutorizacao.porStatus(autorizacao.getStatus())`; atualizar `AutorizacaoEventoPublisherTest` (criação → `ATIVACAO`, cancelamento → `CANCELAMENTO`)
- [x] 1.5 Rodar `mvn test` em `apps/arj-contratocommand`

## 2. arj-contratoquery — espelho do enum

- [x] 2.1 Evoluir `StatusAutorizacao` (`domain/enums/`) com o mesmo grafo e método do item 1.1; criar `TipoEventoAutorizacao` espelho em `domain/enums/`; atualizar `StatusAutorizacaoTest`
- [x] 2.2 Rodar `mvn test` em `apps/arj-contratoquery`

## 3. autorizacaostatus-producer — ponte deriva do status + avro em resources

- [x] 3.1 Criar `StatusAutorizacao` e `TipoEventoAutorizacao` (espelhos) em `application/eventos/`, com testes
- [x] 3.2 `ProcessarEventoAutorizacaoUseCase`: remover o parâmetro `tipoEvento`, derivar o tipo de `payload.status()` após desserializar; `status` desconhecido → `EventoAutorizacaoInvalidoException`; atualizar testes
- [x] 3.3 `SqsEventoAutorizacaoListener`: remover `messageAttributeNames` do `ReceiveMessage` e o repasse do attribute; atualizar `SqsEventoAutorizacaoListenerTest`
- [x] 3.4 `KafkaEventoAutorizacaoProducer`: header `tipoEvento` sempre presente com o valor derivado; atualizar testes
- [x] 3.5 Mover `src/main/avro/EventoAutorizacao.avsc` para `src/main/resources/avro/` e ajustar `sourceDirectory` no `pom.xml`
- [x] 3.6 Rodar `mvn clean package` em `apps/autorizacaostatus-producer` (valida geração Avro + testes)

## 4. eventos-consumer — log derivado + avro em resources

- [x] 4.1 Criar `StatusAutorizacao` e `TipoEventoAutorizacao` (espelhos) em `application/eventos/`, com testes
- [x] 4.2 `ProcessarEventoAutorizacaoUseCase` e `EventoAutorizacaoKafkaListener`: remover o parâmetro/leitura do header `tipoEvento`; logar o tipo derivado de `evento.getStatus()`; atualizar testes
- [x] 4.3 Mover `src/main/avro/EventoAutorizacao.avsc` para `src/main/resources/avro/` e ajustar `sourceDirectory` no `pom.xml`
- [x] 4.4 Rodar `mvn clean package` em `apps/eventos-consumer`

## 5. Comentários enxutos (código Java, docs intocadas)

- [x] 5.1 Resumir comentários de `ReversibleUUIDv7` e `AutorizacaoRepository` (`arj-contratocommand`) para 1–3 linhas, preservando porquês sem outro lar
- [x] 5.2 Resumir javadocs densos das classes de eventos nas 4 apps (publisher, use cases, listeners, converter, producer Kafka)
- [x] 5.3 Rodar `mvn test` nas apps tocadas para garantir que nada quebrou

## 6. Docs espelho e verificação final

- [x] 6.1 Atualizar `CLAUDE.md`/`AGENTS.md`/`README.md` das apps afetadas: caminho `src/main/resources/avro`, novo contrato do `tipoEvento` (derivado do status, `ATIVACAO` na criação), remoção do campo `tipo` do evento interno — sem resumir conteúdo (manter espelhos CLAUDE/AGENTS idênticos)
- [x] 6.2 Build completo das 4 apps (`mvn clean package`) e revisão de que os espelhos manuais (enums, payload, `.avsc`) estão idênticos entre apps
