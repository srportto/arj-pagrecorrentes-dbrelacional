## Why

A auditoria multi-agente de 2026-08-04 comparou campo a campo as cópias manuais dos schemas de
evento e encontrou-as **perfeitamente sincronizadas** — as 2 cópias de `AutorizacaoEventoPayload`
(command e producer) e as 2 de `EventoAutorizacao.avsc` (producer e consumer) são idênticas em
nome, tipo, nulabilidade e ordem. O problema não é o estado atual; é que **nada além de disciplina
humana impede que ele mude**.

O que torna isso grave é a combinação com o tratamento de erro do producer, que por si só está
correto: `AutorizacaoEventoPayloadValidator` classifica payload inválido como não-retryable e dá
ack — comportamento deliberado e especificado, para que dado ruim não trave a fila. Só que
`ProcessarEventoAutorizacaoUseCase` usa `new ObjectMapper()` sem `ignoreUnknown`, e Jackson falha
por padrão em propriedade desconhecida. Encadeando:

```
command adiciona campo no payload  →  deploy independente
producer não replica (esquecimento)
   → Jackson falha em TODA mensagem
   → classificada como não-retryable
   → ack + descarte
   → 100% dos eventos perdidos, em silêncio, até alguém notar o padrão de log ERROR
```

Um esquecimento de sincronização não degrada o fluxo: apaga o fluxo inteiro, sem alarme.

A mesma classe de problema aparece duas vezes mais no pipeline. O `eventos-consumer` configura
`DeadLetterPublishingRecoverer` publicando em `eventos-autorizacao.DLT`, mas o
`kafka-topic-init` do compose só cria `eventos-autorizacao` e o broker tem
`auto.create.topics.enable: false` — a DLT **não existe**. Uma mensagem venenosa esgota as
tentativas, a publicação na DLT falha com `UnknownTopicOrPartitionException`, o offset não avança,
e a partição trava indefinidamente: exatamente o cenário que a DLT existe para evitar. Pior, o
`CLAUDE.md` do app afirma que o tópico "é criado sob demanda pelo auto-create padrão do broker
local" — documentação que contradiz o compose e que provavelmente é a razão de a lacuna ter
passado despercebida.

Por fim, `auto.register.schemas=true` está fixo no código (`KafkaProducerClientConfig.java:34`),
sem distinção de profile — embora o spec `publicacao-eventos-kafka` o descreva como configuração
do profile `local`. Combinado à ausência de teste de contrato, um schema incompatível é
auto-registrado em produção na primeira mensagem produzida, sem revisão.

## What Changes

- Adicionar teste automatizado que compara as cópias espelhadas e **falha o build** quando
  divergem: os 2 `AutorizacaoEventoPayload` (command, producer) e os 2 `EventoAutorizacao.avsc`
  (producer, consumer).
- Executar esse teste no CI de forma que a divergência seja detectada mesmo quando a mudança toca
  um único app — o risco real é justamente a alteração unilateral.
- Adicionar `@JsonIgnoreProperties(ignoreUnknown = true)` na desserialização do payload no
  producer, como rede de segurança: campo novo desconhecido passa a ser ignorado em vez de
  descartar a mensagem inteira.
- Provisionar `eventos-autorizacao.DLT` no `kafka-topic-init` do compose Kafka local, com o mesmo
  número de partições do tópico principal.
- Corrigir a armadilha do `CLAUDE.md`/`AGENTS.md` do `eventos-consumer` que afirma que a DLT é
  criada por auto-create.
- Parametrizar `auto.register.schemas` por profile — `true` em `local`, `false` em `prod` — e
  documentar como o schema passa a ser registrado em produção.
- **Fora de escopo (deliberado):** substituir os espelhos manuais por um módulo Maven
  compartilhado. É a solução estrutural, mas muda a topologia de build do monorepo e a autonomia
  de deploy dos serviços — decisão de arquitetura própria. Esta proposta assume a duplicação como
  dada e constrói a rede de segurança em volta dela.
- **Fora de escopo:** verificação de compatibilidade de schema contra o Schema Registry em CI
  (`mvn schema-registry:test-compatibility`). Depende de decidir a política de compatibilidade e
  de ter um Registry acessível na pipeline.
- **Fora de escopo:** o log de PII no `eventos-consumer`, tratado em `parar-vazamento-dado-sensivel`.

## Capabilities

### New Capabilities

- `contrato-evento-verificado`: como a sincronização entre as cópias manuais dos schemas de evento
  é garantida automaticamente — teste que falha o build na divergência, e tolerância a campo
  desconhecido como rede de segurança em runtime.

### Modified Capabilities

- `local-kafka-environment`: o requisito de criação explícita de tópicos hoje cobre apenas
  `eventos-autorizacao`. Passa a cobrir também `eventos-autorizacao.DLT`, sem o qual o mecanismo
  de dead letter do consumer não funciona. O caminho do compose também é corrigido no texto
  (`compose.yaml`, não `docker-compose.yml`).
- `publicacao-eventos-kafka`: o requisito hoje descreve `auto.register.schemas=true` no profile
  `local`, mas o código o aplica em todos os profiles. Passa a exigir explicitamente que seja
  desabilitado fora do `local`.

## Impact

- **Testes/build:** novo teste de contrato de schema; definição de onde ele roda (módulo de teste
  dedicado ou teste em cada app comparando com a cópia irmã) e ajuste do CI.
- **`autorizacaostatus-producer`:** `ProcessarEventoAutorizacaoUseCase` (configuração do
  `ObjectMapper`), `KafkaProducerClientConfig` (`auto.register.schemas` por profile),
  `application-prod.yaml`.
- **`infra/local/kafka/compose.yaml`:** init-container passa a criar dois tópicos.
- **`eventos-consumer`:** `CLAUDE.md` e `AGENTS.md` (armadilha 9 incorreta), mantidos idênticos.
- **Comportamento em runtime:** payload com campo desconhecido deixa de ser descartado e passa a
  ser processado ignorando o campo — mudança deliberada de semântica, documentada no `design.md`.
- **Produção:** registro de schema deixa de ser automático; exige caminho explícito de registro
  antes do primeiro produce de um schema novo.
