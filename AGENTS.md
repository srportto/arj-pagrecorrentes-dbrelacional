# CLAUDE.md

> Mapa enxuto para agentes de IA. Diagrama de fluxo, portas e estrutura completa do repo estão no [README.md](README.md).

> Para entender este projeto, comece pela análise do grafo de conhecimento gerado pelo `graphify`
> (`graphify-out/`, skill `graphify`) — só leia arquivos diretamente quando necessário ou ao
> desconfiar de alguma imprecisão no grafo. Atualize o `graphify` sempre que encontrar divergência
> entre o grafo e o código, e sempre ao final da conclusão de uma change.

Monorepo de 5 microserviços Java (hexagonal, Spring Boot 4) + 1 Lambda Python em torno de autorizações de pagamentos recorrentes (PIX Automático / DDA Automático).

| Serviço (porta) | O quê | Guia |
|---|---|---|
| contratocommand (8080) | Cria/cancela/decide autorizações (POST/PATCH), publica eventos no SNS | [CLAUDE.md](apps/contratocommand/CLAUDE.md) |
| contratoquery (8081) | Lê autorizações (GET, somente leitura) | [CLAUDE.md](apps/contratoquery/CLAUDE.md) |
| autorizacaostatus-producer (8082) | Ponte SQS → Kafka, converte payload para Avro | [CLAUDE.md](apps/autorizacaostatus-producer/CLAUDE.md) |
| eventos-consumer (8083) | Consome o tópico Kafka, loga e comita (ack) | [CLAUDE.md](apps/eventos-consumer/CLAUDE.md) |
| temporiza-autorizacao (8084) | Temporiza a jornada 1 do PIX_AUTO (agenda/expira via Valkey), sem banco | [CLAUDE.md](apps/temporiza-autorizacao/CLAUDE.md) |
| expurgo-particao (Lambda, sem porta HTTP) | Python, agendada a cada 30 min: fecha o ring buffer de expurgo escrito pelo `contratocommand` | [CLAUDE.md](apps/expurgo-particao/CLAUDE.md) |

Antes de editar código de um serviço, leia o `CLAUDE.md` dele (armadilhas, fluxos e checklist específicos).

## Regras que atravessam os serviços

- **Schemas são espelhados manualmente**: `AutorizacaoEventoPayload` (JSON) vive em `contratocommand` e `autorizacaostatus-producer` como cópias independentes; `EventoAutorizacao.avsc` (Avro) vive em `autorizacaostatus-producer` e `eventos-consumer` (o consumer **não** consome o JSON — recebe Avro direto do tópico Kafka, o `.avsc` é o seu espelho). Não há módulo compartilhado. Mudou um, replique nos outros. `temporiza-autorizacao` usa apenas um **subconjunto** do payload (id + data de inclusão), não um espelho completo.
- Em cada app, `CLAUDE.md` e `AGENTS.md` são espelhos — mantenha-os idênticos ao editar.
- Skills do monorepo (arquitetura hexagonal, JPA, mensageria SQS/Kafka, revisão de código etc.) ficam em `.claude/skills/` — consulte antes de decidir onde um componente novo deve viver.
- **Modelos dos agents** (`.claude/agents/`): cada agent declara `model:` como string simples de um tier Claude (`opus`, `sonnet` ou `haiku`) — sem lista de fallback nem correlato copilot.
- **Autorizações `PIX_AUTO` nascem `RECEBIDA`** e só viram `ATIVA` após aprovação do cliente
  pagador (`PATCH /decisao` no `contratocommand`) — ou `REJEITADA` se o cliente rejeitar
  ou se o prazo de 10 minutos da jornada 1 expirar (temporizado por `temporiza-autorizacao`).
  `DDA_AUTO` continua nascendo `ATIVA` diretamente.
- **Command e query têm representações distintas por design (dívida aceita).** O `contratocommand`
  expõe `status` como `Integer` (código do enum) e nomes longos de campo (`valorAutorizacao`,
  `dataHoraInclusao`, `dataHoraUltimaAtualizacao`); o `contratoquery` expõe `status` como
  `String` (nome do enum, em conformidade com a spec `listar-autorizacoes`) e nomes curtos
  (`valor`, `dataCriacao`, `dataAtualizacao`). A correção está condicionada a um dos gatilhos
  da D1 da change `reconciliar-contrato-spec-doc` (parceiro B2B, conflito semântico, regulação).
- **Convenção única de status HTTP para entrada inválida do cliente: 422.** Tanto falha de formato
  via `@Valid` (`MethodArgumentNotValidException`) quanto violação de regra de negócio via
  `BusinessException` retornam **422** — convenção assumida por `integridade-fluxo-escrita` e
  `blindar-superficie-leitura`. Decidido em 2026-08-09 (D3 da change
  `reconciliar-contrato-spec-doc`): a distinção entre "formato" e "regra" é carregada pelo
  **shape da resposta** (`LayoutErrosApiValidationsResponse` vs `LayoutErrosApiResponse`), não
  pelo primeiro byte do status.
- **O ring buffer de expurgo tem escritor e reclamador em apps diferentes.** O `contratocommand`
  só escreve: `transferirParaExpurgo` move autorizações em estado terminal para a gaveta semanal
  calculada por `ControleExpurgoAutorizacao.obterParticaoExpurgoWrite` (partições `900`–`999`) e
  nunca esvazia nada. Quem fecha o ciclo é `apps/expurgo-particao` (Python, Lambda agendada a cada
  30 minutos, change `reclamar-particao-expurgo-ciclo`): calcula a partição alvo (`escrita + 2`,
  retenção de 98 semanas), classifica seu estado e a esvazia via `TRUNCATE` só quando contém dado
  do ciclo anterior — nunca sobre dado recente. `pg_cron` audita o resultado (registro forense),
  mas não expurga. Mudou a fórmula de particionamento num lado, replique no outro — não há módulo
  compartilhado entre Java e Python aqui, mesma convenção de espelhamento manual do resto do
  monorepo.
