## Context

`apps/expurgo-particao` existe desde 22/08/2026 (change arquivada
`reclamar-particao-expurgo-ciclo`) e não tem nenhum arquivo `.md` — nem `README.md`, nem
`CLAUDE.md`, nem `AGENTS.md`. As outras cinco apps do monorepo têm os três, e a spec
`higiene-documentacao-repo` já declara essa obrigação (hoje falando em "cinco apps", desatualizada
por essa mesma lacuna).

`expurgo-particao` difere estruturalmente das outras cinco: não é Java/Spring Boot, não expõe porta
HTTP, não tem controller nem endpoint de health check no sentido usual — é uma Lambda Python
(`lambda_handler`), invocada por EventBridge Scheduler a cada 30 minutos, empacotada como imagem
Docker (`public.ecr.aws/lambda/python`). O padrão de documentação das cinco apps Java (seção de
arquitetura hexagonal, portas REST, profiles Spring) não se aplica diretamente; a nova documentação
precisa refletir o que a app realmente é, não forçar o molde das outras cinco.

A `README.md` de raiz também ficou obsoleta em pontos que não são novos hoje: a tabela
"Documentação" nunca linkou `infra/local/postgres/README.md` (existia antes desta change), e a spec
`readme-raiz` nomeia só `contratocommand`/`contratoquery` desde que só havia essas duas apps — já
estava um passo atrás mesmo antes do `expurgo-particao` existir.

## Goals / Non-Goals

**Goals:**

- `apps/expurgo-particao` ganha os três arquivos de documentação, cada um respeitando o papel que
  `higiene-documentacao-repo` já define (README para quem chega agora; CLAUDE.md/AGENTS.md como
  espelhos, para agente de IA).
- `README.md` e `AGENTS.md` de raiz refletem o estado atual do repositório: seis apps, não cinco;
  ambiente PostgreSQL local documentado e linkado; `AGENTS.md` sincronizado com `CLAUDE.md`.
- `infra/README.md` cita o módulo `lambda-scheduled` e o ECR da Lambda, no mesmo padrão das cinco
  apps Java.
- As specs `higiene-documentacao-repo` e `readme-raiz` deixam de nomear um número ou subconjunto fixo
  de apps que vira obsoleto a cada app nova — passam a exigir cobertura de **todas** as apps
  existentes, verificável por enumeração de `apps/*/`.

**Non-Goals:**

- Qualquer mudança de comportamento em `apps/expurgo-particao` ou em qualquer outra app. O código
  Python, os módulos Terraform e os composes ficam byte a byte idênticos.
- Reescrever `docs/arquitetura/*` — já corrigido pela change `reclamar-particao-expurgo-ciclo`.
- Auditoria completa de todos os links do repositório — escopo é o que mudou de 22/08 em diante mais
  as duas lacunas pré-existentes identificadas (link do Postgres local, nomeação fixa de apps nas
  duas specs).

## Decisions

**D1. `apps/expurgo-particao/README.md` documenta a app como Lambda agendada, não como serviço REST.**

Estrutura proposta, diferente do molde das cinco apps Java: o que a Lambda faz (fecha o ciclo do
ring buffer, calcula alvo, classifica estado, `TRUNCATE` condicional), como rodar localmente (testes
`pytest`, não `mvn spring-boot:run` — não há servidor para subir), variáveis de ambiente que o
`handler.py` exige (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER_NAME`, `DB_PASSWORD`, `LOG_LEVEL`), o
formato do evento de invocação (`data_referencia`, `modo_consulta` — ambos opcionais, com o
significado de cada um), e link para a capability `reclamacao-particao-expurgo` para quem quiser o
requisito formal. Sem seção de "endpoints" ou "profiles Spring", que não existem aqui.

Alternativa considerada e rejeitada: copiar a estrutura do README de `temporiza-autorizacao` (a app
mais próxima, também sem HTTP público de negócio) linha a linha. Rejeitada porque
`temporiza-autorizacao` ainda é Spring Boot com `/actuator/health` e roda como processo de vida
longa; `expurgo-particao` é invocação sob demanda sem processo residente — a seção "Como rodar"
precisa ser genuinamente diferente, não uma cópia com nomes trocados.

**D2. `CLAUDE.md`/`AGENTS.md` de `expurgo-particao` registram as armadilhas já documentadas no
design da change de origem, sem reabrir a decisão.**

O design da change `reclamar-particao-expurgo-ciclo` (arquivada) já resolveu e justificou: por que
`TRUNCATE` e não `DETACH`/`DELETE`/`DROP+CREATE` (D1 daquele design), por que offset `+2` (D2), por
que sem fase de dry-run (D3), por que `host.docker.internal` (D4, com spike confirmado), por que
`pg_cron` é caixa-preta sem poder de escrita (D5). O `CLAUDE.md` novo desta app **referencia** essas
decisões de forma resumida — não as re-justifica por extenso; o histórico completo continua
recuperável pela capability `reclamacao-particao-expurgo` e pelo git.

**D3. `README.md` de raiz recebe `expurgo-particao` como bloco separado, não inserido no fluxograma
síncrono existente.**

O fluxograma principal do README descreve o caminho de uma requisição (cliente → command/query →
mensageria → consumidores). `expurgo-particao` não participa desse caminho: não é acionada por
evento de negócio, é acionada por agendamento e opera sobre a mesma tabela por fora do fluxo de
requisição. Inseri-la no mesmo diagrama misturaria dois modelos de disparo (evento vs. cron) num só
desenho e tornaria o fluxograma existente mais difícil de ler para seu propósito original.

Adotado: parágrafo/diagrama próprio para o ciclo de expurgo (ring buffer: escrita por
`contratocommand`, reclamação por `expurgo-particao`, auditoria por `pg_cron`), na mesma seção onde
o README já menciona partições 900–999, e entrada na tabela de apps/estrutura de pastas.

**D4. Specs `higiene-documentacao-repo` e `readme-raiz` trocam enumeração fixa por cobertura de
"todas as apps existentes".**

Ambas as specs hoje nomeiam apps explicitamente (uma diz "cinco", a outra nomeia duas). Toda vez que
uma app nova nasce, o requisito precisa ser editado de novo só para atualizar uma contagem — é o
padrão de obsolescência que motivou esta change. A alternativa adotada: o requisito passa a se
referir a "cada app em `apps/`" / "todas as apps de `apps/`", verificável por enumeração do
diretório, sem número ou lista fixa embutida no texto do requisito. A classificação e o
mapeamento pé a pé (é preciso saber quais apps têm o quê) fica nos scenarios e no `Impact` desta
change, não no texto do requisito — que é exatamente onde a obsolescência mora hoje.

## Risks / Trade-offs

**Documentação nova para uma app cujo comportamento observável em produção só começa em ~2028.**
`expurgo-particao` roda 336 vezes por semana sem efeito até a primeira gaveta com dado ser
alcançada. Risco: a documentação descrever um comportamento que ninguém vai observar tão cedo, e
divergir do código sem que ninguém note. *Mitigação:* o `README.md`/`CLAUDE.md` linkam a capability
`reclamacao-particao-expurgo` e o teste de integração (`test_rotina_integracao.py`, que já exercita
o `TRUNCATE` de verdade com massa sintética datada) como fonte viva — não descrevem número de
execução esperado nem data, que ficariam obsoletos a cada semana que passa.

**Trocar enumeração fixa por "todas as apps" nas specs reduz a precisão de "o que verificar
exatamente".** Um requisito que antes dizia "as cinco apps: X, Y, Z..." era mais fácil de checar
mecanicamente. *Mitigação:* os scenarios continuam concretos e nomeiam as seis apps atuais como
**ilustração do estado corrente** (não como definição do requisito), preservando a verificabilidade
sem reintroduzir o número no texto normativo.

## Migration Plan

1. Criar os três arquivos de `apps/expurgo-particao/` (D1, D2).
2. Atualizar `README.md` e `AGENTS.md` de raiz, e `infra/README.md` (D3).
3. Atualizar as duas specs (D4) — delta specs desta change.
4. Verificar links relativos dos arquivos novos e alterados (mesmo mecanismo que
   `higiene-documentacao-repo` já exige, evitando `grep -P`).
5. `openspec validate atualizar-documentacao-referencia-monorepo --strict`.

Reversão: reverter o commit. Nenhum arquivo executável é tocado, então não há efeito em runtime a
desfazer.

## Open Questions

(nenhuma — o escopo é fechado: documentação de referência para o que mudou de 22/08/2026 até hoje,
mais as duas lacunas pré-existentes identificadas durante o levantamento)
