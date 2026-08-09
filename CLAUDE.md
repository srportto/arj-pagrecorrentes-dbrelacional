# CLAUDE.md

> Mapa enxuto para agentes de IA. Diagrama de fluxo, portas e estrutura completa do repo estão no [README.md](README.md).

Monorepo de 5 microserviços Java (hexagonal, Spring Boot 4) em torno de autorizações de pagamentos recorrentes (PIX Automático / DDA Automático).

| Serviço (porta) | O quê | Guia |
|---|---|---|
| arj-contratocommand (8080) | Cria/cancela/decide autorizações (POST/PATCH), publica eventos no SNS | [CLAUDE.md](apps/arj-contratocommand/CLAUDE.md) |
| arj-contratoquery (8081) | Lê autorizações (GET, somente leitura) | [CLAUDE.md](apps/arj-contratoquery/CLAUDE.md) |
| autorizacaostatus-producer (8082) | Ponte SQS → Kafka, converte payload para Avro | [CLAUDE.md](apps/autorizacaostatus-producer/CLAUDE.md) |
| eventos-consumer (8083) | Consome o tópico Kafka, loga e comita (ack) | [CLAUDE.md](apps/eventos-consumer/CLAUDE.md) |
| temporiza-autorizacao (8084) | Temporiza a jornada 1 do PIX_AUTO (agenda/expira via Valkey), sem banco | [CLAUDE.md](apps/temporiza-autorizacao/CLAUDE.md) |

Antes de editar código de um serviço, leia o `CLAUDE.md` dele (armadilhas, fluxos e checklist específicos).

## Regras que atravessam os serviços

- **Schemas são espelhados manualmente**: `AutorizacaoEventoPayload` (JSON) e `EventoAutorizacao.avsc` (Avro) existem como cópias independentes em `arj-contratocommand`, `autorizacaostatus-producer` e `eventos-consumer` — não há módulo compartilhado. Mudou um, replique nos outros. `temporiza-autorizacao` usa apenas um **subconjunto** do payload (id + data de inclusão), não um espelho completo.
- Em cada app, `CLAUDE.md` e `AGENTS.md` são espelhos — mantenha-os idênticos ao editar.
- Skills do monorepo (arquitetura hexagonal, JPA, mensageria SQS/Kafka, revisão de código etc.) ficam em `.claude/skills/` — consulte antes de decidir onde um componente novo deve viver.
- **Autorizações `PIX_AUTO` nascem `RECEBIDA`** e só viram `ATIVA` após aprovação do cliente
  pagador (`PATCH /decisao` no `arj-contratocommand`) — ou `REJEITADA` se o cliente rejeitar
  ou se o prazo de 10 minutos da jornada 1 expirar (temporizado por `temporiza-autorizacao`).
  `DDA_AUTO` continua nascendo `ATIVA` diretamente.
