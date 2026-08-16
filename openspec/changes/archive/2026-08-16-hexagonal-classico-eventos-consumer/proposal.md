## Why

O commit `b3f33ea` adotou a **arquitetura hexagonal clássica (ports & adapters)** como referência
nas skills do catálogo — `arquitetura-limpa-java` v2.0.0 passou a descrever `domain` /
`application` / `infrastructure` como layout alvo, e `criar-aplicacao-java` já gera app nova nesse
formato. As cinco aplicações de `apps/`, porém, continuam no layout anterior
(`entrypoint` / `application` / `domain` / `shared`). A própria skill reconhece a lacuna:

> As cinco aplicações de `apps/` ainda usam o layout anterior. A migração é trabalho à parte:
> **código existente nesse layout não é defeito** até ser migrado.

Enquanto a migração não acontece, o repositório sustenta **duas convenções simultâneas**: toda app
nova nasce no layout de referência e toda app existente permanece no legado. Isso força cada agente
e cada pessoa a decidir caso a caso qual convenção seguir, e a tabela de equivalência da skill
(legado → alvo) tem de ser consultada em toda revisão.

Esta é a **primeira de seis mudanças** que migram as cinco aplicações. O `eventos-consumer` foi
escolhido como piloto por ser o menor da frota — 7 arquivos em `main`, 232 linhas, 6 arquivos de
teste — e por não ter persistência, portas de saída nem regra de negócio. Ele é o lugar mais barato
para **materializar a convenção em código** antes de aplicá-la em apps onde errar custa caro.

O que esta mudança decide, as outras cinco herdam: nomes de pacote, sufixo de classe para porta vs.
adaptador, onde vive `config/`, e como a suíte de testes espelha a nova árvore.

## What Changes

- Reorganizar `eventos-consumer` de `entrypoint`/`application`/`domain`/`shared` para
  `domain`/`application`/`infrastructure`:

  | Hoje | Depois |
  |---|---|
  | `application/eventos/ProcessarEventoAutorizacaoUseCase` (classe `@Service`) | `domain/port/in/ProcessarEventoAutorizacaoUseCase` (interface) + `application/usecase/ProcessarEventoAutorizacaoService` (`@Service`) |
  | `entrypoint/kafka/EventoAutorizacaoKafkaListener` | `infrastructure/messaging/EventoAutorizacaoKafkaListener` |
  | `shared/config/KafkaConsumerConfig`, `KafkaProperties` | `infrastructure/config/` |
  | `domain/enums/StatusAutorizacao`, `TipoEventoAutorizacao` | inalterados |

- Extrair a **porta de entrada** `ProcessarEventoAutorizacaoUseCase` como interface em
  `domain/port/in/`. O listener Kafka (driving adapter) passa a injetar a interface, não a classe
  concreta — hoje ele injeta `ProcessarEventoAutorizacaoUseCase` como `@Service`.
- Mover os 6 arquivos de teste para espelhar a nova árvore de pacotes.
- Criar a capacidade `layout-hexagonal-classico`, que passa a ser a fonte da verdade verificável do
  layout alvo — as outras cinco mudanças acrescentam o requisito da sua app a ela.
- Atualizar `apps/eventos-consumer/CLAUDE.md` e `AGENTS.md` (espelhos idênticos) e a seção de
  arquitetura correspondente.

- **Nenhuma mudança de comportamento.** Nenhum tópico, group id, `AckMode`, contrato Avro, porta
  HTTP ou rota muda. O critério de aceite é `mvn test` verde com a mesma contagem de testes.

- **Fora de escopo (deliberado):** as outras quatro aplicações — cada uma tem sua própria mudança.
- **Fora de escopo:** o `.avsc` de `EventoAutorizacao` e o espelhamento manual do schema com o
  `autorizacaostatus-producer`. O contrato Avro não é afetado por reorganização de pacote Java.
- **Fora de escopo:** renomear `domain/enums/` para `domain/model/`. `StatusAutorizacao` e
  `TipoEventoAutorizacao` são enums de negócio, e a skill mantém `domain/enums/` como destino
  próprio na tabela de equivalência ("`domain/enums/` — inalterado").

## Capabilities

### New Capabilities

- `layout-hexagonal-classico`: define o layout de pacotes em três camadas, a regra de dependência
  (setas sempre para dentro) e a distinção porta × adaptador que as aplicações do monorepo SHALL
  seguir depois de migradas — mais o requisito específico do `eventos-consumer`, primeira app a
  cumprir a convenção.

## Impact

- **Código afetado (`eventos-consumer`, 7 arquivos em `main`):** todos os pacotes mudam de nome;
  1 arquivo novo (a interface da porta de entrada); 1 classe renomeada
  (`ProcessarEventoAutorizacaoUseCase` → `...Service`).
- **Testes (6 arquivos):** movidos para os pacotes espelhados. `ProcessarEventoAutorizacaoUseCaseTest`
  passa a se chamar `ProcessarEventoAutorizacaoServiceTest`;
  `EventoAutorizacaoKafkaListenerTest` continua exercitando o listener, agora mockando a porta.
- **Build:** nenhuma alteração em `pom.xml`. O plugin do Avro gera `br.com.srportto.eventos.autorizacao.*`
  fora da árvore da app — não é afetado.
- **Configuração:** nenhuma. `application.yaml` não referencia classe por FQN.
- **Documentação:** `apps/eventos-consumer/CLAUDE.md` + `AGENTS.md` (manter idênticos). O
  `CLAUDE.md` da raiz não precisa mudar nesta etapa — a linha sobre skills continua válida.
- **Convenção herdada pelas outras cinco mudanças:** o que for decidido aqui sobre nomenclatura
  (`*Service` para implementação de porta de entrada, `port/in` × `port/out`, `infrastructure/messaging`)
  vira precedente. Divergir depois exige justificar no `design.md` da mudança que diverge.
