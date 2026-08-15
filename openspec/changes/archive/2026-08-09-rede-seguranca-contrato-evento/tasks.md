## 1. Decidir a forma da verificação

- [x] 1.1 Inspecionar `.github/workflows/` e determinar se o CI constrói o monorepo inteiro ou apenas os módulos afetados pelo diff — **não existe `.github/workflows/`, não há CI hoje**
- [x] 1.2 Com base em 1.1, escolher entre (a) teste em cada app comparando com a cópia irmã ou (b) job de CI dedicado independente de módulo; registrar a decisão no `design.md` (D2) — escolhido (b), registrado em D2
- [x] 1.3 Definir a estratégia de comparação semântica (conjunto de campos, tipos, nulabilidade, nomes de serialização), garantindo que reformatação não cause falha — comparação estrutural via parser JSON próprio (avsc) e extração de componentes de record via regex (payload), serialização canônica para diff

## 2. Teste de contrato de schema

- [x] 2.1 Implementar a comparação das duas cópias de `EventoAutorizacao.avsc` (`autorizacaostatus-producer` e `eventos-consumer`) — `ci/contrato-eventos/VerificarContratoEventos.java`
- [x] 2.2 Implementar a comparação das duas cópias de `AutorizacaoEventoPayload` (`contratocommand` e `autorizacaostatus-producer`), incluindo os nomes `@JsonProperty`
- [x] 2.3 Garantir que a mensagem de falha identifique o campo divergente e os dois arquivos comparados — o valor do teste está no diagnóstico, não só no vermelho
- [x] 2.4 Validar com divergência proposital: adicionar um campo em apenas um lado e confirmar que o build falha — confirmado, exit code 1, diagnóstico identificou `campo_novo_proposital` e os dois arquivos
- [x] 2.5 Validar o falso positivo: reindentar um `.avsc` sem mudar conteúdo e confirmar que o build passa — confirmado, `OK`
- [x] 2.6 Reverter as alterações propositais de 2.4 e 2.5 — `git status`/`git diff` confirmam árvore de trabalho limpa

## 3. Verificação no CI

- [x] 3.1 Integrar a verificação ao CI conforme a decisão de 1.2 — `.github/workflows/contrato-eventos.yml`, roda em push/PR sob `apps/**`
- [x] 3.2 Validar o cenário crítico: pull request que altera **apenas** `contratocommand` com divergência de schema SHALL falhar o CI — confirmado (exit 1) e revertido
- [x] 3.3 Confirmar que a verificação roda em tempo aceitável e não vira gargalo da pipeline — ~0,5s local, sem build Maven

## 4. Tolerância a campo desconhecido (declarativa, não corretiva — ver D3 revisado)

- [x] 4.1 Anotar `@JsonIgnoreProperties(ignoreUnknown = true)` no `ObjectMapper`/record de `ProcessarEventoAutorizacaoUseCase` no `autorizacaostatus-producer`, alinhando ao `temporiza-autorizacao` — **somente após** a verificação de contrato estar ativa no CI (ordem definida em D3). Validado em auditoria (2026-08-09): o Jackson 3 já ignora campo desconhecido por default; esta task declara a intenção, não corrige perda de mensagens. Aplicado no record, mesmo padrão do `temporiza-autorizacao`.
- [x] 4.2 Teste: mensagem SQS com propriedade extra desconhecida é desserializada, processada e produzida no Kafka com os campos conhecidos — `propriedadeDesconhecidaEIgnorada`
- [x] 4.3 Teste de regressão: mensagem sem campo obrigatório continua classificada como não-retryable, sem mudança de comportamento — `campoObrigatorioAusenteContinuaNaoRetryable`
- [x] 4.4 Avaliar se o `ObjectMapper` deve ser um bean único reutilizado em vez de instanciado por chamada; aplicar se for mudança contida — já é campo de instância de `@Service` singleton (`new ObjectMapper()` roda uma vez na construção, não por chamada); confirmado sem outra ocorrência de `new ObjectMapper()` no módulo. Nenhuma mudança necessária.

## 5. Provisionamento da DLT

- [x] 5.1 Adicionar `eventos-autorizacao.DLT` com 3 partições ao `kafka-topic-init` de `infra/local/kafka/compose.yaml`
- [x] 5.2 Subir o ambiente do zero (`docker compose down -v` seguido de `up -d`) e confirmar que `kafka-topics --list` inclui os dois tópicos — confirmado, ambos com 3 partições
- [x] 5.3 Teste de integração ponta a ponta: forçar mensagem que falha todas as tentativas e confirmar que ela chega na DLT e que o offset avança — **achado adicional (2026-08-09, além do escopo original desta proposta):** o destino default do `DeadLetterPublishingRecoverer` no spring-kafka 4.0.6 é `<topico>-dlt` (hífen, minúsculo), não `<topico>.DLT`. Com o tópico provisionado como `eventos-autorizacao.DLT` (conforme spec) e o resolver default do Spring, a publicação na DLT falhava (`UnknownTopicOrPartitionException`), reproduzindo exatamente o cenário que esta mudança existe para evitar — confirmado ao vivo antes da correção (partição travou, offset não avançou, mensagem ficou em retry indefinido). Corrigido com um `BiFunction` de destino explícito em `KafkaConsumerConfig.eventoAutorizacaoDeadLetterRecoverer`, forçando `<topico>.DLT`. Revalidado após a correção: 3 mensagens venenosas enviadas, todas as 3 chegaram em `eventos-autorizacao.DLT` e os offsets das 3 partições avançaram sem travar (`LAG=0`).
- [x] 5.4 Verificar se existe provisionamento de tópicos Kafka para ambientes além do local (IaC, pipeline); se existir, adicionar a DLT lá também — nenhum encontrado; a única referência a `eventos-autorizacao` fora do compose Kafka é o nome da fila SQS (`SQS-eventos-autorizacao`) em `infra/envs/local-messaging/variables.tf`, que não é um tópico Kafka. O repositório só evidencia provisionamento de tópicos no compose local.
- [x] 5.5 Corrigir a armadilha 9 do `CLAUDE.md` do `eventos-consumer`, que afirma que a DLT é criada por auto-create; replicar no `AGENTS.md` (espelhos idênticos)

## 6. Política de registro de schema

- [x] 6.1 Definir como o schema passará a ser registrado em produção com `auto.register.schemas=false` (passo de pipeline, registro manual revisado ou plugin Maven dedicado) — bloqueia 6.2 — decisão registrada em design.md: registro manual revisado (sem CI/CD/IaC de prod no repo hoje para um passo automatizado)
- [x] 6.2 Parametrizar `auto.register.schemas` por profile em `KafkaProducerClientConfig`, removendo o literal fixo da linha 34 — via `kafka.auto-register-schemas` (`KafkaProperties`)
- [x] 6.2b Parametrizar o mesmo literal fixo em `eventos-consumer/KafkaConsumerConfig.java:114` (achado adicional da auditoria de 2026-08-09, não coberto pela proposta original)
- [x] 6.3 Configurar `true` no profile `local` e `false` no `prod` via `application-*.yaml`, nos dois apps
- [x] 6.4 Documentar o caminho de registro em produção no `CLAUDE.md`/`AGENTS.md` do `autorizacaostatus-producer` e do `eventos-consumer`
- [x] 6.5 Confirmar que o ambiente local continua registrando o schema automaticamente e o fluxo ponta a ponta segue funcionando — validado com os 3 apps reais: subject `eventos-autorizacao-value` registrado no Schema Registry, mensagem SQS real → producer → Kafka → consumer, tudo com sucesso

## 7. Validação final

- [x] 7.1 Rodar a suíte completa dos três apps do fluxo de eventos — `contratocommand` 160/160, `autorizacaostatus-producer` 64/64, `eventos-consumer` 14/14, todos `BUILD SUCCESS`
- [x] 7.2 Revisar os cenários dos 3 specs desta mudança e confirmar teste correspondente para cada um — `contrato-evento-verificado`: coberto por `VerificarContratoEventos.java` + validação manual (2.4/2.5/3.2) e pelos testes de `ProcessarEventoAutorizacaoUseCaseTest`; `local-kafka-environment`: coberto pela subida real do compose (5.2) e pelo teste ponta a ponta da DLT (5.3); `publicacao-eventos-kafka`: coberto pela parametrização de `auto.register.schemas` (6.2/6.2b/6.3) e pela validação e2e (6.5)
- [x] 7.3 Confirmar que o comentário de proveniência em `KafkaProducerClientConfig` que cita "visibility timeout de 30s" foi corrigido para 60s, ou registrar que fica para `reconciliar-contrato-spec-doc` — já estava correto (60s), nenhuma ocorrência de "30s" encontrada
- [x] 7.4 Auditoria `java-revisor` (2026-08-09): **APROVADO**. Achado Importante corrigido: `KafkaConsumerConfigTest` não capturava o `ProducerRecord` para verificar o tópico real de destino — adicionado `ArgumentCaptor` nos dois testes de DLT, com `assertEquals("eventos-autorizacao.DLT", ...)`; validado por regressão manual (removendo temporariamente o `BiFunction` explícito, os dois testes falham com o diagnóstico correto; restaurado, voltam a passar). Achados menores corrigidos: import não usado em `VerificarContratoEventos.java`; comentário de escopo adicionado esclarecendo que a comparação do `.avsc` é por conjunto de campos (ordem da lista não importa) mas por ordem de união de tipos (importa) e que `namespace`/`name` de nível raiz não entram na comparação.
