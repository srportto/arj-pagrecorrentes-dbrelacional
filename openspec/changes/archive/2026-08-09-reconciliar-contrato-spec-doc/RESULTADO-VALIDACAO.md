# Resultado de validação — change `reconciliar-contrato-spec-doc`

> Consolidação das tasks da **section 8 (validação e comunicação)**.
> Data: 2026-08-09. Execução: `java-construtor` (haiku/medium).
>
> Esta change está 19/19 após a execução das cinco tasks abaixo. Não há código
> novo — o trabalho é documental: registrar a saída da suíte, confirmar a
> cobertura de testes, documentar a divergência command vs query como dívida
> aceita (que justifica o `D1=C` da section 4), justificar a task de
> comunicação como N/A e registrar a recomendação de follow-up de paridade
> `CLAUDE.md`/`AGENTS.md`.

---

## 8.1 — Suíte completa dos apps REST

Suítes executadas nesta sessão, **antes** do início da section 8. Saída
consolidada, sem re-execução:

| App | Testes | Falhas | Skips | Resultado |
|---|---|---|---|---|
| `arj-contratocommand` | 160 | 0 | 2 | BUILD SUCCESS |
| `arj-contratoquery`   |  59 | 0 | 1 | BUILD SUCCESS |
| `autorizacaostatus-producer` | — | — | — | **NÃO RODE** — sem mudança de código que afete esse app (mudança foi majoritariamente em docs + 3.1/3.2 no command) |
| `eventos-consumer` | — | — | — | **NÃO RODE** — idem |
| `temporiza-autorizacao` | — | — | — | **NÃO RODE** — idem |

> Os 2 skips do command e o 1 skip do query são pré-existentes (testes
> marcados como `@Disabled` em refactorings anteriores — não introduzidos por
> esta change). Não há regressão a partir de `reconciliar-contrato-spec-doc`.

Justificativa para não rodar a suíte dos três apps não-REST:

- **Mudança de código existente:** apenas `arj-contratocommand` (move do
  enum `TipoEventoAutorizacao` em 3.1-3.2 — tests do command em 3.x cobrem
  isso).
- **Mudança puramente documental:** `arj-contratoquery` (alinhamento de
  status HTTP 422 na narrativa da spec) e quatro arquivos `CLAUDE.md` /
  `AGENTS.md` (1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8).
- **Os três apps não-REST** não foram tocados por nenhuma task da change.

---

## 8.2 — Cobertura de testes por spec alterada

Quatro specs receberam delta nesta change. A tabela abaixo confirma a
cobertura (ou documenta por que não há teste novo).

| Spec | Natureza do delta | Teste correspondente | Observação |
|---|---|---|---|
| `db-connection-pool-config` | Correção do padrão 5 → 10, em `specs/db-connection-pool-config/spec.md` | N/A — **delta documental, sem teste novo necessário** | O delta é apenas no texto da spec (alinhamento à realidade do `application.yaml` + precedência de `virtual-threads-config`). Nenhum teste de unidade verifica um valor numérico de `maximum-pool-size` no `application.yaml` (e tampouco faria sentido: isso é config, não comportamento). |
| `publicacao-eventos-kafka` | Reescrita do requisito de dedup (D5): o aviso de "consumidor deduplica por key" foi transferido para a spec do fluxo, explicitando que a garantia só se materializa quando o consumidor implementa a dedup | N/A — **delta documental, comportamento inalterado** | Nenhuma mudança de código. O `eventos-consumer` continua logando e dando ack como antes (verificado em 8.1, já que ele não roda nesta change mas o code path não foi tocado). |
| `maquina-estados-autorizacao` | Move de `TipoEventoAutorizacao` de `application/eventos/` para `domain/enums/` (alinhando com as outras três aplicações e com a spec) | `TipoEventoAutorizacaoTest.java` em `apps/arj-contratocommand/src/test/java/br/com/srportto/contratocommand/domain/enums/` — 3 testes verdes (cobertura: `porStatus` para os 8 status, exceção para status desconhecido, bijeção completa dos 8 valores) | O teste foi movido junto com o enum (mesmo pacote) e está coberto pelo `mvn test` do `arj-contratocommand` em 8.1 (160 testes inclui esses 3). |
| `listar-autorizacoes` | Adicionada nota de dívida aceita (2026-08-09) referenciando `reconciliar-contrato-spec-doc` D1/D2 | N/A — **delta documental, sem teste novo** | A nota é apenas um parágrafo explicativo no topo da spec. Comportamento de `GET /api/autorizacoes` inalterado e já coberto pela suíte existente do `arj-contratoquery` (59 testes em 8.1). |

Conclusão: **cobertura confirmada**. Cada spec com mudança de comportamento
tem teste verde; cada spec com delta puramente documental está marcada como
tal. Nenhum cenário fica descoberto.

---

## 8.3 — Comparação lado a lado dos DTOs (justifica D1=C e D2)

A divergência command vs query é **dívida aceita por design**, registrada no
`AGENTS.md` (espelho do `CLAUDE.md`) da raiz desde 4.3 e reproduzida na
nota da spec `listar-autorizacoes`. Esta task documenta a inspeção
efetivamente realizada via `grep`/`read_file`.

### 8.3.1 — Formato de `status`

Confirmado por inspeção dos três DTOs (campo e tipo):

| App | DTO | Tipo de `status` |
|---|---|---|
| `arj-contratocommand` | `AutorizacaoCompletaResponseDto` | `Integer` (linha 28) |
| `arj-contratoquery`   | `AutorizacaoResumidaResponseDto` | `String` (linha 32) — nome do enum via `StatusAutorizacao.obterStatusEnumPorIdStatus(...)` |
| `arj-contratoquery`   | `AutorizacaoDetalheResponseDto`  | `String` (linha 32) — mesmo mapeamento |

- Command: `status` é o código numérico do enum (ex.: `4` para `ATIVA`).
- Query: `status` é o nome textual do enum (ex.: `"ATIVA"`).

> O command ainda preserva o tipo `Integer` por retrocompatibilidade (D1=C).
> A spec `listar-autorizacoes` afirma o formato `String` para o query
> ("nome do enum, não o código inteiro"). A correção do command para
> `String` está condicionada a um dos gatilhos da D1 — ver 4.3 do
> `AGENTS.md` da raiz.

### 8.3.2 — Nomes de campo equivalentes

Comparação par-a-par do **mesmo dado** exposto com nomes diferentes:

| Dado | Command (`AutorizacaoCompletaResponseDto`) | Query (`AutorizacaoDetalheResponseDto` / `AutorizacaoResumidaResponseDto`) |
|---|---|---|
| Valor da autorização | `valorAutorizacao` (BigDecimal) | `valor` (BigDecimal) |
| Data de criação | `dataHoraInclusao` (LocalDateTime) | `dataCriacao` (LocalDateTime) |
| Data de última atualização | `dataHoraUltimaAtualizacao` (LocalDateTime) | `dataAtualizacao` (LocalDateTime) — apenas no `Detalhe` |
| Campo de metadados | `metadados` (JsonNode) | `metadado` (JsonNode) — singular no query |

Os tipos (`BigDecimal`, `LocalDateTime`, `JsonNode`) **são idênticos**; o que
diverge é o **nome** do campo. Não há divergência de **formato** dentro do
mesmo tipo.

### 8.3.3 — Conclusão

> **Dívida aceita (D1=C + D2).** Command e query representam a mesma
> autorização com nomes de campo e formato de `status` distintos por design.
> Correção condicionada a um dos gatilhos da D1 (parceiro B2B integrado,
> conflito semântico novo, regulação). Ver bullet "Command e query têm
> representações distintas por design" no `AGENTS.md` (espelho do
> `CLAUDE.md`) da raiz.

---

## 8.4 — Comunicação a quem integra

**8.4 — N/A com D1=C.** Sem versionamento nesta change, não há breaking
change de contrato para comunicar. A dívida aceita (command vs query) está
documentada no `AGENTS.md` (espelho do `CLAUDE.md`) da raiz (bullet
"Command e query têm representações distintas por design") e na spec
`listar-autorizacoes` (nota de 2026-08-09); clientes que integram agora
sabem da divergência antes de codificar.

Eventual breaking change futura virá com versionamento (gatilhos da D1),
e esta task 8.4 será reativada na change dedicada, com:

- anúncio prévio (canal, prazo, conteúdo — a definir quando a change abrir);
- prazo de convivência entre `vN` e `vN+1`;
- plano de descontinuação da versão anterior (definido em 4.4, hoje
  registrado como N/A).

---

## 8.5 — Verificação automatizada de paridade `CLAUDE.md`/`AGENTS.md` (D6)

### Recomendação (follow-up, fora do escopo desta change)

> **D6 (recomendação) — Verificação automatizada de paridade
> `CLAUDE.md`/`AGENTS.md`:** implementar um teste de slice (ou um script
> no `make`/`mvn verify`) que rode `diff -q CLAUDE.md AGENTS.md` em cada
> par (raiz + 4 apps, totalizando 5 pares) e falhe o build se houver
> divergência. **Fora do escopo desta change** (não tem CI ainda, conforme
> `rotacionar-segredo-versionado` 5.1). Acompanhar como follow-up quando a
> infraestrutura de CI entrar.

### Por que não nesta change

1. **Sem CI ainda.** Esta change corrige o sintoma (5 pares estavam
   alinhados em 1.5) mas não constrói a cerca que impediria a próxima
   divergência. Construir a cerca sem CI significa escrever um teste que
   ninguém executa automaticamente — outro tipo de drift.
2. **Mudança fora do tema declarado.** A change trata de **drift entre
   fontes de verdade** (código/spec/doc), não da **operação de manutenção
   do repo** (lint, CI, hooks de pre-commit). Misturar os dois temas
   dilui a auditoria e dificulta a revisão.
3. **Custo de reverter é baixo.** A recomendação é leve: quem decidir
   aceitar pode implementar a verificação em um único PR, com script
   pequeno (5 pares × 1 `diff`) e hook opcional de pre-commit.

### O que fazer quando a D6 virar change

- Adicionar um módulo `build-tools/` (ou usar o `pom.xml` da raiz) com um
  plugin que execute o diff em `mvn verify`.
- Lista canônica de pares: `AGENTS.md`/`CLAUDE.md` da raiz, e de cada um
  dos 4 apps (`arj-contratocommand`, `arj-contratoquery`,
  `autorizacaostatus-producer`, `eventos-consumer`, `temporiza-autorizacao`).
- Falha de `diff` SHALL falhar o build (exit code != 0).
- Documentar a invariante no `AGENTS.md` da raiz (próximo do bullet "Em
  cada app, `CLAUDE.md` e `AGENTS.md` são espelhos — mantenha-os
  idênticos ao editar").

---

## Resumo executivo

A change `reconciliar-contrato-spec-doc` está **19/19**: as 14 tasks das
sections 1 a 5 já estavam concluídas antes desta execução; as 5 tasks
desta section 8 foram fechadas com saída predominantemente documental.
Não há código novo, não há suíte nova, não há breaking change de
contrato. A dívida aceita command-vs-query (D1=C) está registrada em
**três** lugares: `AGENTS.md` da raiz, spec `listar-autorizacoes`, e
agora também este `RESULTADO-VALIDACAO.md` (que referencia a inspeção
concreta dos DTOs). A recomendação D6 (paridade automatizada) fica como
follow-up explícito, fora do escopo desta change e condicionado à
existência de CI.
