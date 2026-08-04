## Context

Esta proposta trata a categoria mais numerosa da auditoria de 2026-08-04, e a mais difícil de
enxergar numa revisão de PR: nenhum destes itens é um bug: cada um é uma **fonte de verdade
divergente**.

```
                        ┌──────────┐
                        │  CÓDIGO  │
                        └────┬─────┘
              ┌──────────────┼──────────────┐
              │              │              │
        ┌─────▼─────┐  ┌─────▼─────┐  ┌─────▼─────┐
        │  CLAUDE   │  │   SPEC    │  │  README   │
        │    .md    │  │           │  │           │
        └───────────┘  └───────────┘  └───────────┘

   Nenhuma seta é verificada. Cada uma pode divergir em silêncio,
   e algumas já divergiram — inclusive spec contra spec.
```

Inventário do que diverge:

| # | Item | Fonte A | Fonte B | Quem está certo |
|---|---|---|---|---|
| 1 | Status de `@Valid` | código: 422 | README/CLAUDE: 400 | a decidir |
| 2 | Formato de `status` | command: `Integer` | query: `String` | a decidir |
| 3 | Nomes de campo | command: `valorAutorizacao` | query: `valor` | a decidir |
| 4 | `maximum-pool-size` | `db-connection-pool-config`: 5 | `virtual-threads-config` + código: 10 | B |
| 5 | Pacote de `TipoEventoAutorizacao` | código command: `application/eventos/` | spec + 3 apps: `domain/enums/` | B (spec) |
| 6 | Cópias de `AutorizacaoEventoPayload` | CLAUDE.md raiz: 3 apps | código: 2 apps | código |
| 7 | Versões do producer | CLAUDE.md: Avro 1.11.3, kafka 3.7.1 | pom.xml: 1.11.4, 3.9.2 | pom |
| 8 | Visibility timeout | comentário: 30s | Terraform: 60s | Terraform |
| 9 | Rules de cancelamento | CLAUDE.md: só `TipoProdutoCancelamento` | código: mais `ProdutoSuportadoCancelamento` | código |
| 10 | Dedup por key | spec: "responsabilidade do consumidor" | nenhum consumidor dedupica | ninguém |

Itens 4 a 9 têm resposta objetiva: alinhar ao lado correto. Itens 1, 2, 3 e 10 exigem **decisão**,
porque nenhum lado é evidentemente certo — e é precisamente por isso que continuam divergindo.

Restrição estrutural: não há versionamento de API nem OpenAPI. Sem eles, os itens 2 e 3 não têm
caminho de migração — qualquer correção quebra todos os clientes simultaneamente. Isso dita a
ordem do plano.

## Goals / Non-Goals

**Goals:**

- Uma única resposta por questão, escrita onde ela é verificável.
- Contratos coerentes entre command e query para a mesma entidade.
- Caminho de migração antes das mudanças de contrato.
- Contrato machine-readable, verificável contra o código.
- Eliminar a contradição spec contra spec.

**Non-Goals:**

- Paginação por cursor, HATEOAS, RFC 9457.
- Itens já cobertos por `rede-seguranca-contrato-evento` e `blindar-superficie-leitura`.
- Reescrever a arquitetura de contratos — apenas torná-la coerente e verificável.

## Decisions

### D1 — Versionamento **antes** das renomeações

Ordem inegociável. Renomear `valorAutorizacao` para `valor` sem versionamento quebra todos os
clientes no deploy. Com versionamento, `v1` preserva os nomes atuais e `v2` traz os corrigidos, com
janela de convivência.

Se o versionamento for adiado ou recusado, os itens 2 e 3 **não devem** ser implementados — é
preferível conviver com a incoerência documentada a quebrar integrações sem caminho de saída. Esta
condicionalidade é parte da decisão, não uma ressalva.

### D2 — Nome canônico por campo: o mais descritivo, não o mais curto

Proposta a validar na implementação:

| Conceito | Canônico | Motivo |
|---|---|---|
| valor da autorização | `valor` | o recurso já é a autorização; `valorAutorizacao` é redundante no contexto |
| data de criação | `dataHoraInclusao` | é timestamp, não data; `dataCriacao` induz ao tipo errado |
| data de atualização | `dataHoraUltimaAtualizacao` | mesma razão |
| status | nome do enum (`"ATIVA"`) | legível, estável, já é o formato da spec `listar-autorizacoes` |

Cada escolha vence de um lado diferente de propósito — o objetivo é o nome certo, não empatar o
placar entre os serviços.

Sobre `status`: além de mais legível, o código numérico acopla o contrato à ordem interna do enum,
e o query já expõe o nome por exigência de spec. Padronizar no formato que já é normativo em um dos
lados é o caminho de menor conflito.

### D3 — Convenção de status HTTP: 400 para formato, 422 para negócio

Três agentes apontaram a divergência por ângulos diferentes. A convenção proposta:

| Origem | Status |
|---|---|
| Bean Validation (`@Valid`), tipo/formato inválido | **400** |
| Regra de negócio (`BusinessException`) | **422** |
| Conflito de estado ou recurso duplicado | 409 |
| Não encontrado | 404 |

Adota-se 400 para `@Valid` porque é o que a documentação já promete, é a convenção usual, e permite
ao cliente distinguir "corrija o payload" de "a regra de negócio recusou" — distinção que hoje se
perde ao colapsar tudo em 422.

Dependência a observar: `integridade-fluxo-escrita` e `blindar-superficie-leitura` foram escritas
seguindo a convenção **vigente** (422), deliberadamente, para não criar um terceiro padrão. Se esta
decisão for adotada, os pontos introduzidos por aquelas propostas precisam acompanhar — está nas
tasks.

### D4 — OpenAPI gerado do código, não escrito à mão

Contrato escrito à parte é apenas mais uma fonte de verdade para divergir — o problema que esta
proposta existe para resolver. Gerar a partir das anotações (`springdoc-openapi`) mantém contrato e
implementação acoplados por construção.

O ganho real vem de publicar o artefato gerado e, idealmente, verificá-lo em CI contra o contrato
aprovado — caso contrário, gerar documentação que ninguém compara é só documentação mais nova.

### D5 — Item 10: decidir entre implementar ou desprometer

O spec `publicacao-eventos-kafka` declara um **contrato explícito** de que o consumidor a jusante
deduplica por key. Nenhum consumidor o faz. Duas saídas:

- **(a) Implementar a deduplicação no `eventos-consumer`.** Hoje o app só loga, então dedup não
  protege nada de concreto — seria construir para um requisito que ainda não existe.
- **(b) Reescrever o requisito** para deixar explícito que a garantia só se materializa quando o
  consumidor implementa a dedup, e que consumidor sem ela NÃO deve aplicar efeito colateral
  persistente.

Recomendamos **(b)**. Ela é honesta sobre o estado atual e, mais importante, transfere o aviso para
onde ele será lido: quem for dar estado ao `eventos-consumer` precisa encontrar essa exigência na
spec do fluxo, não num `design.md` arquivado. O mesmo vale para a ordenação — a auditoria observou
que a mitigação prometida ("consumidores reordenam por `data_hora_ultima_atlz`") nunca foi
implementada, e hoje só está registrada num arquivo de mudança já arquivado.

### D6 — Espelhos `CLAUDE.md`/`AGENTS.md` são o ponto frágil

A convenção do monorepo exige que os dois arquivos sejam idênticos em cada app. Nada verifica isso.
Todo drift documental desta proposta é um par de arquivos que pode divergir silenciosamente — e a
correção manual de hoje não impede a reincidência de amanhã.

Uma verificação automatizada de igualdade entre os pares é barata e fecha a categoria. Fica
registrada como recomendação nas tasks; a decisão de automatizar ou manter manual é do time.

## Risks / Trade-offs

- **Mudança de contrato quebra clientes** → Versionamento primeiro (D1). Sem ele, os itens 2 e 3
  não entram. Esta é a condicionalidade central da proposta.

- **Convivência de duas versões dobra a superfície de manutenção** → Custo real e temporário.
  Definir prazo de descontinuação da `v1` junto com a introdução da `v2`, para que a convivência
  não se torne permanente.

- **Mudar a convenção de status afeta propostas já escritas** → `integridade-fluxo-escrita` e
  `blindar-superficie-leitura` seguem 422 por decisão explícita. Se D3 for adotada, ambas precisam
  de ajuste — previsto nas tasks, e a razão de esta proposta ser a última.

- **Corrigir a documentação hoje não impede o drift de amanhã** → É a limitação de fundo. Mitigação
  parcial: verificação automatizada dos espelhos (D6) e OpenAPI gerado do código (D4). O resto
  continua dependendo de disciplina.

- **Muitos itens numa proposta só** → O agrupamento é deliberado: são a mesma classe de problema, e
  fatiá-los produziria seis propostas de baixo valor individual. As tasks estão ordenadas para
  permitir entrega incremental, com os itens objetivos (4 a 9) independentes dos que exigem decisão.

## Migration Plan

1. Drifts objetivos (itens 4 a 9) — sem impacto em contrato, entregáveis de imediato.
2. Decisões de contrato (itens 1, 2, 3) — registrar antes de codificar.
3. OpenAPI nos dois serviços, refletindo o contrato atual.
4. Versionamento de API.
5. Mudanças de contrato sob a nova versão.
6. Ajuste das propostas anteriores à convenção de status, se D3 for adotada.
7. Comunicação e prazo de descontinuação da versão anterior.

Rollback: as etapas 1 a 4 são aditivas e reversíveis. A etapa 5 é protegida pelo versionamento —
clientes na versão anterior não são afetados.

## Open Questions

- O versionamento de API será adotado? A resposta determina se os itens 2 e 3 acontecem (D1).
- A convenção de status muda para 400/422 (D3), ou o código permanece como está e a documentação é
  que se alinha a ele?
- Item 10: implementar dedup ou reescrever a spec? Recomendação (b), decisão do time.
- Qual o prazo de convivência entre versões da API?
- Vale automatizar a verificação de igualdade entre `CLAUDE.md` e `AGENTS.md` (D6)?
