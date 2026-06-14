## Context

O repositório é um monorepo com dois microserviços Java que formam um par (command/query): `arj-contratocommand` (escrita, porta 8080) e `arj-contratoquery` (leitura, porta 8081). Ambos compartilham o mesmo banco PostgreSQL com particionamento temporal via `pg_partman` + `pg_cron`.

Cada app já tem seu próprio README.md detalhado. O README de raiz deve ser leve — visão de 10 mil pés — e referenciar os READMEs individuais em vez de duplicar conteúdo técnico.

## Goals / Non-Goals

**Goals:**
- Descrever o propósito geral do sistema (autorizações de produtos financeiros: PIX Auto e DDA Auto)
- Mapear a estrutura de pastas do repositório (`aplicacoes/`, `docs/`, `openspec/`)
- Mostrar a relação entre os dois microserviços (command escreve, query lê)
- Listar pré-requisitos mínimos (Java 25, PostgreSQL 16+, Maven)
- Linkar para o README de cada app e para a documentação em `docs/`

**Non-Goals:**
- Repetir detalhes de arquitetura, endpoints ou stack que já estão nos READMEs das apps
- Adicionar CLAUDE.md ou AGENTS.md na raiz (não há código para orientar agentes diretamente na raiz)

## Decisions

**Decisão 1 — README curto e orientado a links**
O README de raiz deve ser breve. Detalhes técnicos ficam nos READMEs de cada app. Alternativa considerada: README longo com tudo duplicado — rejeitada por gerar manutenção dupla.

**Decisão 2 — Diagrama textual da relação entre serviços**
Um diagrama ASCII simples (cliente → command → DB ← query ← cliente) torna imediatamente clara a arquitetura command/query sem precisar abrir outro arquivo.

## Risks / Trade-offs

- [Risco de desatualização] Se um app mudar de porta ou nome, o README raiz precisa ser atualizado manualmente → Mitigação: manter o README raiz intencionalmente de alto nível para reduzir a frequência de atualização necessária.
