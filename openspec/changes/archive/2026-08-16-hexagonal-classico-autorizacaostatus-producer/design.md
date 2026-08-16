## Context

O `autorizacaostatus-producer` é uma ponte: consome JSON da fila SQS (alimentada pelo tópico SNS do
`contratocommand`) e produz Avro no tópico Kafka lido pelo `eventos-consumer`. Não tem banco, não
tem API, não decide nada de negócio — traduz.

```
                    o "domínio" desta app é a própria tradução

  SQS (JSON)                                                    Kafka (Avro)
     │                                                              ▲
     │   AutorizacaoEventoPayload      EventoAutorizacao            │
     │   (espelho manual do            (gerado do .avsc,            │
     │    payload do command)           espelhado no consumer)      │
     ▼            │                            │                    │
  listener ──────▶│───── validator ────────────│──── producer ──────┘
                  └──── converter ─────────────┘
                                    │
                        IdempotenciaKeyGenerator
                        SHA-256(id + data_hora_ultima_atlz)
```

Duas restrições moldam o desenho e não são negociáveis nesta mudança:

1. **O monorepo já espelha schema manualmente em quatro lugares** (`AutorizacaoEventoPayload` em duas
   apps, `EventoAutorizacao.avsc` em duas apps). O `CLAUDE.md` da raiz trata isso como armadilha
   conhecida: *"Mudou um, replique nos outros"*.
2. **Nenhum log nem mensagem de exceção pode carregar o body** — ele contém `id_pessoa_pagadora`,
   `id_pessoa_devedora`, `id_pessoa_recebedora`, `valor`, `descricao` e `metadados`. É a spec
   `protecao-dado-sensivel`, e mover classe não pode afrouxá-la.

## Goals / Non-Goals

**Goals**

- Separar a porta de saída existente do seu adaptador Kafka.
- Tirar a desserialização de dentro do caso de uso.
- Decidir e **registrar** até onde vale levar o hexagonal numa aplicação-ponte.

**Non-Goals**

- Eliminar o espelhamento manual de schema. É problema real, mas é outra proposta — e a solução
  (módulo compartilhado) é decisão de topologia de build, não de layout de pacote.
- Introduzir modelo de domínio próprio para o evento. Ver D2.
- Alterar a classificação de erro do interceptor ou a política de DLQ.

## Decisions

### D1 — A desserialização sai do caso de uso e vai para o listener

Hoje `ProcessarEventoAutorizacaoUseCase` importa `tools.jackson.databind.ObjectMapper` e recebe a
mensagem como `String`. Isso é o anti-padrão #3 da skill em outra roupa: detalhe de infraestrutura
(o formato de serialização escolhido pelo produtor) dentro de `application`.

Depois: o listener SQS desserializa, valida (`AutorizacaoEventoPayloadValidator`) e converte
(`EventoAutorizacaoConverter`); o caso de uso recebe o evento já tipado e faz o que sobra — derivar
a chave de idempotência e publicar pela porta.

**Consequência sobre classificação de erro (crítica).** Hoje `JacksonException` é lançada de dentro
do caso de uso e sobe até o `SqsEventoAutorizacaoErrorInterceptor`, que a classifica. Passando a
desserialização para o listener, a exceção nasce **antes** — e precisa continuar chegando ao
interceptor classificada como **não-retryável**. JSON malformado nunca vai virar JSON válido: se
escorregar para retryável, a mensagem reentrega para sempre e a fila trava. É o risco número um
desta mudança, e tem task e cenário de spec dedicados.

### D2 — Modelo de domínio puro (`domain/model/EventoAutorizacao`); decisão revisada em 2026-08-16

**Decisão final: D2-b.** A alternativa inicialmente cogitada (aceitar `EventoAutorizacao` Avro
atravessando a porta de saída em `domain`) foi descartada explicitamente pelo usuário antes da
implementação — é a única violação consciente da regra de dependência em toda a migração da frota, e
o custo de pagá-la (abaixo) foi considerado aceitável frente a manter `domain` livre de framework em
100% das apps migradas.

Cria-se `domain/model/EventoAutorizacao` como record/classe Java pura, com os mesmos 25 campos
tipados (`UUID`, `LocalDateTime`, `BigDecimal`, etc.) que hoje o Avro carrega, sem qualquer import de
`org.apache.avro.*` nem da classe gerada pelo `avro-maven-plugin`.

```java
// domain/port/out/PublicadorEventoAutorizacao.java
public interface PublicadorEventoAutorizacao {
    void publicar(String key, EventoAutorizacao evento);   // ← domain/model, tipo puro
}
```

Fluxo de tradução passa a ter dois mapeamentos, não um:

```
SQS (JSON) ──▶ AutorizacaoEventoPayload ──▶ domain/model/EventoAutorizacao ──▶ Avro EventoAutorizacao ──▶ Kafka
                (infrastructure/messaging)   (application, via mapper novo)   (infrastructure/messaging)
```

- `EventoAutorizacaoConverter` (infrastructure) passa a mapear `AutorizacaoEventoPayload` →
  `domain/model/EventoAutorizacao` (não mais direto para Avro).
- Um mapper novo, `EventoAutorizacaoAvroMapper` (infrastructure/messaging, ao lado do
  `KafkaEventoAutorizacaoProducer`), mapeia `domain/model/EventoAutorizacao` → Avro `EventoAutorizacao`
  gerado. É o adaptador de saída que efetivamente conhece o tipo Avro; ele já implementava
  `PublicadorEventoAutorizacao`, então o mapeamento acontece dentro dele, imediatamente antes de
  publicar.
- O `setScale(2)` defensivo hoje presente no converter (porque "o serializer Avro exige scale exata")
  migra para o mapper Avro novo — é particularidade do formato de fio Avro, não do modelo de domínio.

Custo pago conscientemente: 3 representações do evento a manter em sincronia (payload JSON + modelo de
domínio + Avro, contra 2 antes), 2 mapeamentos a escrever e testar (JSON→domínio, domínio→Avro, contra
1 antes). Sem ganho de comportamento — o ganho é estrutural: `domain/` fica livre de framework em
100% das apps migradas, sem exceção.

**Gatilhos que já não se aplicam** (eram a favor de aceitar a dívida; ficam registrados para o caso de
uma futura reversão): a ponte permanecer estritamente 1:1, sem regra de negócio própria; existir um só
formato de destino; o espelhamento manual de schema permanecer sem módulo compartilhado.

**Implementado em 2026-08-16, conforme decidido:** `domain/model/EventoAutorizacao` nasceu como record
Java puro com 28 campos (a estimativa de "~25" da decisão original era aproximada — o `.avsc` tem 28
campos no total). `EventoAutorizacaoConverter` (`infrastructure/messaging/`) mapeia
`AutorizacaoEventoPayload` → `domain/model/EventoAutorizacao`, sem `setScale`. O `setScale(2)`
defensivo migrou para o novo `EventoAutorizacaoAvroMapper` (`infrastructure/messaging/`), que mapeia
`domain/model/EventoAutorizacao` → o record Avro gerado; é injetado em `KafkaEventoAutorizacaoProducer`
(implementação de `PublicadorEventoAutorizacao`) e usado só ali, imediatamente antes do `send()`.
Confirmado por inspeção (task 2.7 de `tasks.md`): nenhuma classe de `domain/` ou `application/` importa
`org.apache.avro.*`, a classe Avro gerada, `org.apache.kafka.*`, `tools.jackson.*` ou SDK AWS.

### D3 — `IdempotenciaKeyGenerator` vai para `domain/service/`

`SHA-256(idAutorizacao + dataHoraUltimaAtualizacao)` é a definição de **o que torna duas mensagens a
mesma transição de estado**. É regra de negócio: se mudar, muda a semântica de duplicata para todo o
pipeline a jusante. Ele já opera sobre campos tipados (`UUID`, `LocalDateTime`), nunca sobre o JSON
cru — o javadoc atual até enfatiza isso.

Fica em `domain/service/` mantendo `@Component`, seguindo a convenção decidida na exploração de
2026-08-15 para as rules do `contratocommand`: regra de negócio pertence ao domínio, e a anotação
Spring ali é exceção consciente para permitir injeção. A tensão com "domínio sem framework" é real e
está registrada como tal — ver o requisito correspondente na spec.

### D4 — Validator e converter ficam em `infrastructure/messaging/`

Os dois operam sobre formato de fio: `AutorizacaoEventoPayloadValidator` valida campos obrigatórios
do payload recebido (e o javadoc diz que espelha `decimal(17,2)` do `.avsc`);
`EventoAutorizacaoConverter` traduz payload → Avro com `setScale` defensivo porque *"o serializer
Avro exige scale exata"*.

Ambos existem por causa de particularidades de serialização. Nenhum expressa regra de negócio.
Contraste com D3: a chave de idempotência sobreviveria a uma troca de Avro por Protobuf; o
`setScale(2)` não.

A exceção que o validador lança (`EventoAutorizacaoInvalidoException`) vai para `domain/exception/`,
pela mesma regra de contrato de porta fixada em `hexagonal-classico-temporiza-autorizacao` (D6).

### D5 — `SqsListenerHealthIndicator` vai para `infrastructure/web/`

Precedente direto de `hexagonal-classico-temporiza-autorizacao` (D3). Sem novidade.

## Risks / Trade-offs

- **Risco alto: JSON malformado deixar de ser classificado como não-retryável.** Consequência é fila
  travada em reentrega infinita. Endereçado por D1, com task e cenário de spec dedicados; a
  verificação é empírica (publicar JSON quebrado na fila local e confirmar que vai para a DLQ), não
  só por leitura de código.
- **Risco: log passar a carregar o payload.** Ao mover a desserialização para o listener, o
  tratamento de erro ali é código novo — e o caminho mais natural para quem escreve seria logar a
  string que falhou. Isso violaria `protecao-dado-sensivel`. Task dedicada.
- **Trade-off aceito: 3 representações do evento e 2 mapeamentos a manter em sincronia (D2-b),** em vez
  do tipo Avro atravessando `domain`. Custo maior de manutenção, mas `domain` fica livre de framework
  sem exceção.
- **Trade-off aceito: `domain/service/IdempotenciaKeyGenerator` carrega `@Component` (D3).**
- **Risco menor: `ProcessarEventoAutorizacaoUseCaseTest` precisa ser reescrito, não só movido.** É o
  único ponto onde esta mudança não preserva a estrutura dos testes; a contagem total permanece.

## Migration Plan

Etapa única, mas com uma ordem que importa: mover a desserialização (D1) **por último**, depois que
todo o resto já compilar e a suíte estiver verde. É a única alteração comportamentalmente sensível, e
isolá-la no fim faz com que uma quebra na classificação de erro seja atribuível sem bisseção.

## Open Questions

Nenhuma pendente. D2 foi decidida em 2026-08-16 (D2-b, modelo de domínio puro) antes do início da
implementação — ver seção Decisions.
