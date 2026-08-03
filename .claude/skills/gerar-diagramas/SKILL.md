---
name: gerar-diagramas
description: Use quando o usuário pedir para criar, atualizar ou documentar visualmente um fluxo, arquitetura, sequência de chamadas, modelo de dados ou máquina de estados em algum arquivo .md do repositório. Gatilhos - "criar diagrama", "documentar o fluxo", "desenhar a arquitetura", "diagrama de sequência", "modelar o banco visualmente", "atualizar o CLAUDE.md com o fluxo". Todo diagrama gravado em arquivo (docs/arquitetura/*.md, CLAUDE.md, AGENTS.md, design.md do OpenSpec) SHALL usar bloco ```mermaid``` — nunca ASCII art nesses arquivos. ASCII continua sendo o normal para diagramas soltos direto na conversa (não é escopo desta skill). NÃO use para gráfico de dados/KPI/dashboard (skill global `dataviz`) nem para diagrama dentro de um Artifact/página web (skills globais `artifact-design`/`artifact-capabilities`) — esta skill cobre só arquivos .md versionados no repo. Uso: sessão principal, carregada proativamente sempre que o pedido casar com os gatilhos acima — desvio intencional do padrão deste catálogo, onde as demais skills exigem invocação manual via `/nome-da-skill`; também disponível via `/gerar-diagramas`.
---

# Gerar Diagramas

## Visão geral

Padroniza como diagramas técnicos são desenhados e onde são salvos neste monorepo:
sempre Mermaid quando o resultado é gravado em um arquivo `.md` versionado — nunca ASCII
art nesses arquivos, mesmo que existam diagramas ASCII antigos ao lado (não reescreva os
antigos por conta própria; a coexistência é aceitável, só o conteúdo novo segue esta
regra). ASCII continua normal para diagramas desenhados direto na resposta da conversa —
isso não passa por esta skill.

**Por que a regra existe:** Mermaid renderiza como diagrama de verdade no GitHub e em
qualquer visualizador de Markdown moderno; ASCII é só texto alinhado manualmente,
caro de editar e fácil de desalinhar num diff. O repo hoje tem ~2200 linhas de ASCII em
`docs/arquitetura/` (convenção antiga) e só 1 bloco Mermaid (`docs/floci-aws-local/`,
documentando uma ferramenta de terceiros) — esta skill não migra o que já existe, só
define o padrão para o que é escrito daqui pra frente.

## Quando NÃO usar

- Gráfico de dados, KPI ou dashboard → skill global `dataviz`.
- Diagrama dentro de um Artifact (página web publicada) → skills globais
  `artifact-design`/`artifact-capabilities`, que já tratam Mermaid nativamente em
  Artifacts.
- Diagrama que só precisa aparecer na resposta da conversa, sem ser salvo em arquivo →
  desenhe em ASCII direto na resposta, sem invocar esta skill.

## Procedimento

1. **Mapeie o fluxo real inspecionando o repositório** — código, configs, specs do
   OpenSpec relevantes. Nunca invente estrutura ou nomeie componentes que não existem no
   código.

2. **Decida o destino antes do formato.** Se não estiver óbvio pelo pedido do usuário,
   pergunte ou infira pelo escopo:
   - Fluxo/decisão específica de UMA aplicação → `apps/<app>/CLAUDE.md` (e espelhar em
     `AGENTS.md`, se o app mantiver os dois como cópias idênticas — confira se já é o
     caso antes de assumir).
   - Decisão de design de uma mudança em andamento → `design.md` da change ativa em
     `openspec/changes/<mudança>/` (confira com `openspec list --json` se houver mais de
     uma change ativa).
   - Visão de arquitetura do sistema como um todo → `docs/arquitetura/`. Esse diretório
     já separa por responsabilidade (contexto de negócio, modelo hexagonal, modelo de
     dados) — encaixe no arquivo existente certo em vez de criar um novo arquivo genérico
     tipo `arquitetura.md`. Só crie um arquivo novo se nenhum dos existentes cobrir o
     assunto, e nomeie pelo conteúdo, não genericamente.

3. **Escreva o diagrama em bloco ```mermaid``` nativo**, escolhendo o tipo de diagrama
   pelo que está sendo representado:
   - `flowchart TD`/`flowchart LR` — arquitetura, fluxo de dados, pipeline de
     processamento.
   - `sequenceDiagram` — troca de chamadas entre componentes/serviços ao longo do tempo.
   - `erDiagram` — modelo de dados/schema de banco.
   - `stateDiagram-v2` — máquina de estados (ex.: transições de status de uma entidade).

4. **Nomeie os nós como os componentes reais do código** (classe, tabela, tópico, fila —
   não abstrações genéricas como "Serviço A").

5. Se o destino for `CLAUDE.md`, verifique se `AGENTS.md` do mesmo diretório é mantido
   como espelho idêntico (vários apps deste monorepo seguem essa regra, declarada no
   topo do próprio `CLAUDE.md`) — se for, replique a mesma edição lá e confirme com
   `diff` que os dois arquivos continuam idênticos ao final.

## Skills e agents relacionados

| Situação | Use |
|---|---|
| Gráfico de dados, KPI, dashboard | skill global `dataviz` |
| Diagrama dentro de um Artifact (página web) | skills globais `artifact-design`/`artifact-capabilities` |
| Arquitetura interna de uma app Java nova/existente (decisão, não diagrama) | skill `java-architecture` |
| Camada correta para uma classe (hexagonal) | skill `arquitetura-limpa-java` |
| Criar uma proposta/change OpenSpec do zero (o design.md nasce junto) | skill `openspec-propose` |
