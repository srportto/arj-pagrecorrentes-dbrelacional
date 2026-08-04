## 1. Decidir a forma da verificação

- [ ] 1.1 Inspecionar `.github/workflows/` e determinar se o CI constrói o monorepo inteiro ou apenas os módulos afetados pelo diff
- [ ] 1.2 Com base em 1.1, escolher entre (a) teste em cada app comparando com a cópia irmã ou (b) job de CI dedicado independente de módulo; registrar a decisão no `design.md` (D2)
- [ ] 1.3 Definir a estratégia de comparação semântica (conjunto de campos, tipos, nulabilidade, nomes de serialização), garantindo que reformatação não cause falha

## 2. Teste de contrato de schema

- [ ] 2.1 Implementar a comparação das duas cópias de `EventoAutorizacao.avsc` (`autorizacaostatus-producer` e `eventos-consumer`)
- [ ] 2.2 Implementar a comparação das duas cópias de `AutorizacaoEventoPayload` (`arj-contratocommand` e `autorizacaostatus-producer`), incluindo os nomes `@JsonProperty`
- [ ] 2.3 Garantir que a mensagem de falha identifique o campo divergente e os dois arquivos comparados — o valor do teste está no diagnóstico, não só no vermelho
- [ ] 2.4 Validar com divergência proposital: adicionar um campo em apenas um lado e confirmar que o build falha
- [ ] 2.5 Validar o falso positivo: reindentar um `.avsc` sem mudar conteúdo e confirmar que o build passa
- [ ] 2.6 Reverter as alterações propositais de 2.4 e 2.5

## 3. Verificação no CI

- [ ] 3.1 Integrar a verificação ao CI conforme a decisão de 1.2
- [ ] 3.2 Validar o cenário crítico: pull request que altera **apenas** `arj-contratocommand` com divergência de schema SHALL falhar o CI
- [ ] 3.3 Confirmar que a verificação roda em tempo aceitável e não vira gargalo da pipeline

## 4. Tolerância a campo desconhecido

- [ ] 4.1 Configurar o `ObjectMapper` de `ProcessarEventoAutorizacaoUseCase` no `autorizacaostatus-producer` para ignorar propriedades desconhecidas — **somente após** a verificação de contrato estar ativa no CI (ordem definida em D3)
- [ ] 4.2 Teste: mensagem SQS com propriedade extra desconhecida é desserializada, processada e produzida no Kafka com os campos conhecidos
- [ ] 4.3 Teste de regressão: mensagem sem campo obrigatório continua classificada como não-retryable, sem mudança de comportamento
- [ ] 4.4 Avaliar se o `ObjectMapper` deve ser um bean único reutilizado em vez de instanciado por chamada; aplicar se for mudança contida

## 5. Provisionamento da DLT

- [ ] 5.1 Adicionar `eventos-autorizacao.DLT` com 3 partições ao `kafka-topic-init` de `infra/local/kafka/compose.yaml`
- [ ] 5.2 Subir o ambiente do zero (`docker compose down -v` seguido de `up -d`) e confirmar que `kafka-topics --list` inclui os dois tópicos
- [ ] 5.3 Teste de integração ponta a ponta: forçar mensagem que falha todas as tentativas e confirmar que ela chega na DLT e que o offset avança
- [ ] 5.4 Verificar se existe provisionamento de tópicos Kafka para ambientes além do local (IaC, pipeline); se existir, adicionar a DLT lá também
- [ ] 5.5 Corrigir a armadilha 9 do `CLAUDE.md` do `eventos-consumer`, que afirma que a DLT é criada por auto-create; replicar no `AGENTS.md` (espelhos idênticos)

## 6. Política de registro de schema

- [ ] 6.1 Definir como o schema passará a ser registrado em produção com `auto.register.schemas=false` (passo de pipeline, registro manual revisado ou plugin Maven dedicado) — bloqueia 6.2
- [ ] 6.2 Parametrizar `auto.register.schemas` por profile em `KafkaProducerClientConfig`, removendo o literal fixo da linha 34
- [ ] 6.3 Configurar `true` no profile `local` e `false` no `prod` via `application-*.yaml`
- [ ] 6.4 Documentar o caminho de registro em produção no `CLAUDE.md`/`AGENTS.md` do `autorizacaostatus-producer`
- [ ] 6.5 Confirmar que o ambiente local continua registrando o schema automaticamente e o fluxo ponta a ponta segue funcionando

## 7. Validação final

- [ ] 7.1 Rodar a suíte completa dos três apps do fluxo de eventos
- [ ] 7.2 Revisar os cenários dos 3 specs desta mudança e confirmar teste correspondente para cada um
- [ ] 7.3 Confirmar que o comentário de proveniência em `KafkaProducerClientConfig` que cita "visibility timeout de 30s" foi corrigido para 60s, ou registrar que fica para `reconciliar-contrato-spec-doc`
