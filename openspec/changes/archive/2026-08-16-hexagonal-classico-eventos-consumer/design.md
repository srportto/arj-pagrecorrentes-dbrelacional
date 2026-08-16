## Context

O `eventos-consumer` é a app mais simples da frota: consome um tópico Kafka com `AckMode.RECORD`,
deriva o tipo de evento do status e loga. Não tem banco, não tem porta de saída, não tem regra de
negócio.

```
HOJE                                      DEPOIS
                                          
entrypoint/kafka/                         infrastructure/messaging/
  EventoAutorizacaoKafkaListener            EventoAutorizacaoKafkaListener
        │ injeta a CLASSE concreta                │ injeta a INTERFACE
        ▼                                         ▼
application/eventos/                      domain/port/in/
  ProcessarEventoAutorizacaoUseCase         ProcessarEventoAutorizacaoUseCase
  @Service, classe                          interface, Java puro
        │                                         ▲ implementa
        │                                         │
        │                                 application/usecase/
        │                                   ProcessarEventoAutorizacaoService
        │                                   @Service
        ▼                                         ▼
domain/enums/                             domain/enums/
  TipoEventoAutorizacao                     TipoEventoAutorizacao  (inalterado)
                                          
shared/config/                            infrastructure/config/
  KafkaConsumerConfig, KafkaProperties       KafkaConsumerConfig, KafkaProperties
```

Justamente por ser simples, é o lugar certo para fixar as decisões de convenção que as outras cinco
mudanças vão herdar sem rediscutir.

## Goals / Non-Goals

**Goals**

- Materializar em código o layout descrito na skill `arquitetura-limpa-java` v2.0.0.
- Fixar nomenclatura reutilizável pelas outras cinco mudanças.
- Zero mudança de comportamento observável.

**Non-Goals**

- Melhorar o que a app faz. `ProcessarEventoAutorizacaoService` continua só logando — se isso é
  pouco, é assunto de outra proposta.
- Introduzir porta de saída. Não há destino externo: o log não é uma porta.
- Tocar no contrato Avro ou no espelhamento de `.avsc` com o `autorizacaostatus-producer`.

## Decisions

### D1 — A implementação da porta de entrada recebe sufixo `Service`, e o nome `UseCase` fica com a interface

O nome `ProcessarEventoAutorizacaoUseCase` hoje pertence à classe `@Service`. No layout alvo ele
passa a nomear a **interface** em `domain/port/in/`, e a implementação vira
`ProcessarEventoAutorizacaoService` em `application/usecase/`.

É exatamente o par do exemplo mínimo da skill (`CriarPedidoUseCase` / `CriarPedidoService`), e tem a
propriedade de que o nome mais visível — o que aparece na assinatura do adaptador — é o do
contrato, não o da implementação.

**Alternativa descartada:** manter `...UseCase` na classe e chamar a interface de
`ProcessarEventoAutorizacaoPort`. Rejeitada porque colide com o vocabulário da skill em toda a
frota, e porque `Port` como sufixo já está reservado, na tabela da skill, para portas de saída
(`EstoquePort`).

**Consequência para as outras apps:** `autorizacaostatus-producer` e `temporiza-autorizacao` têm
classes homônimas (`ProcessarEventoAutorizacaoUseCase`, `ProcessarExpiracaoUseCase`,
`AgendarExpiracaoUseCase`, `VarrerAgendamentosVencidosUseCase`) que sofrerão o mesmo desdobramento.

### D2 — Toda porta de entrada vira interface, mesmo com um único adaptador e sem ganho de teste

O listener Kafka é o único chamador, e o teste atual já mocka a classe concreta com Mockito sem
precisar de interface. Extrair a interface aqui **não gera benefício técnico imediato** — é custo
puro nesta app.

Ainda assim: a regra de dependência do hexagonal é uma propriedade **estrutural verificável**
("nenhuma classe de `infrastructure` importa de `application`"). Se a porta for opcional quando
"não compensa", a regra deixa de ser verificável e vira julgamento caso a caso — que é exatamente o
estado que esta migração existe para encerrar. A uniformidade é o produto.

**Consequência:** a spec desta capacidade exige a interface como invariante estrutural, não como
recomendação.

### D3 — `domain/enums/` permanece; não vira `domain/model/`

A tabela de equivalência da skill lista `domain/model/`, `domain/enums/` → "inalterados".
`StatusAutorizacao` e `TipoEventoAutorizacao` são enums de negócio puros (sem framework), já estão
no lugar certo, e são **espelhados manualmente** nas quatro aplicações que os têm. Renomear o pacote
aqui criaria divergência entre espelhos que a spec `maquina-estados-autorizacao` trata como cópias
equivalentes.

### D4 — `KafkaProperties` vai para `infrastructure/config/`, não para `application`

`KafkaProperties` é `@ConfigurationProperties` — nome de tópico, group id, bootstrap servers. Tudo
detalhe de transporte. A skill mapeia `shared/config/` → `infrastructure/config/` sem exceção, e
não há aqui nenhuma propriedade de negócio que justificasse tratamento diferente.

### D5 — A árvore de testes espelha a de produção, arquivo por arquivo

`src/test/java` reproduz os pacotes novos. `ProcessarEventoAutorizacaoUseCaseTest` vira
`ProcessarEventoAutorizacaoServiceTest` (segue a classe que testa). Nenhum teste é adicionado ou
removido — a contagem antes e depois SHALL ser idêntica, e essa igualdade é o que dá confiança de
que a mudança foi mesmo mecânica.

## Risks / Trade-offs

- **Risco: `EventosConsumerApplicationTests` com `@SpringBootTest` deixa de achar componentes.**
  O `@SpringBootApplication` está em `br.com.srportto.eventosconsumer`, raiz de todos os pacotes
  novos — o component scan continua alcançando tudo. Mitigação: a suíte roda ao final e a contagem
  de testes é comparada com a de antes.
- **Risco: divergência com os espelhos de enum das outras apps.** Endereçado por D3 — os enums não
  se movem.
- **Trade-off aceito: uma interface a mais para um único implementador.** Justificado em D2. O custo
  é 1 arquivo de ~10 linhas; o ganho é a regra de dependência virar verificável em toda a frota.
- **Trade-off aceito: o diff é grande em número de arquivos e nulo em comportamento.** Revisar por
  `git log --follow` fica pior. Mitigação: mover e renomear em commits separados dos ajustes de
  `import`, para que o Git detecte os renames.

## Migration Plan

Uma etapa só, sem convivência entre layouts: a app tem 7 arquivos e reorganização parcial custaria
mais do que a completa. Se a suíte quebrar de forma não trivial, reverter é um `git revert` — não há
migration de banco, mudança de contrato nem estado persistido envolvido.

## Open Questions

- Nenhuma bloqueante. A decisão de ambição (N3 — domínio puro), o tratamento do particionamento
  (porta de saída) e a localização das rules (`domain/service/` com `@Component`) foram resolvidos
  na exploração de 2026-08-15 e só afetam `contratoquery` e `contratocommand`.

## Implementation Notes

Implementado em 2026-08-15. Nenhuma divergência entre D1–D5 e o resultado: `Service` como sufixo
da implementação (D1), interface extraída mesmo com único chamador (D2), `domain/enums/` inalterado
(D3), `KafkaProperties` em `infrastructure/config/` (D4), árvore de testes espelhada 1:1 sem teste
adicionado ou removido — 14/14 antes e depois (D5). Validado também em runtime: app subida contra
o Kafka local (`infra/local/kafka/`), mensagem Avro publicada via `kafka-avro-console-producer`,
consumida e logada por `ProcessarEventoAutorizacaoService` com `tipoEvento` derivado corretamente —
o `containerFactory` continuou resolvido por nome apesar da reorganização de pacotes.
