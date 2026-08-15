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
| 1 | Status de `@Valid` | código: 422 | README/CLAUDE: 400 | **D3 = código (422)**, alinhar doc |
| 2 | Formato de `status` | command: `Integer` | query: `String` | **D1+C = dívida documentada** |
| 3 | Nomes de campo | command: `valorAutorizacao` | query: `valor` | **D1+C = dívida documentada** |
| 4 | `maximum-pool-size` | `db-connection-pool-config`: 5 | `virtual-threads-config` + código: 10 | **B (10)**, corrigido em 2.2 |
| 5 | Pacote de `TipoEventoAutorizacao` | código command: `application/eventos/` | spec + 3 apps: `domain/enums/` | **B (spec)**, corrigido em 3.x |
| 6 | Cópias de `AutorizacaoEventoPayload` | CLAUDE.md raiz: 3 apps | código: 2 apps | código, corrigido em 1.1 |
| 7 | Versões do producer | CLAUDE.md: Avro 1.11.3, kafka 3.7.1 | pom.xml: 1.11.4, 3.9.2 | pom, corrigido em 1.2 |
| 8 | Visibility timeout | comentário: 30s | Terraform: 60s | Terraform, corrigido em 1.3 |
| 9 | Rules de cancelamento | CLAUDE.md: só `TipoProdutoCancelamento` | código: mais `ProdutoSuportadoCancelamento` | código, corrigido em 1.4 |
| 10 | Dedup por key | spec: "responsabilidade do consumidor" | nenhum consumidor dedupica | **D5 = reescrever spec**, feito em 2.5 |

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

### D1 — Sem versionamento de API nesta change (decisão de 2026-08-09)

Após análise `openspec-explore`, **D1 é resolvida como "não versionar agora"** (Opção C). Motivos:

- **Zero cliente externo identificado.** Os `CLAUDE.md` mostram que as APIs REST (porta 8080 e 8081) são consumidas apenas por `temporiza-autorizacao` (interno) e operadores via `curl`/Postman (sem contrato de SLA). Não há parceiros B2B nem exigência regulatória que obrigue versionamento explícito.
- **Items 2 e 3 (incoerência command vs query) não são bugs ativos.** A fricção está documentada e é mitigável por adições compatíveis — não justifica o overhead de versionamento só para corrigir nomes.
- **Section 5 (OpenAPI) segue independente.** Gerar OpenAPI da situação vigente é insumo barato para versionar no futuro, **se** o gatilho abaixo ocorrer.

**Consequências:**

- Itens 2 e 3 (formato de `status`, nomes de campo) **não** entram nesta change como renomeação. Viram **dívida documentada**, atualizando o `CLAUDE.md` da raiz e a spec `listar-autorizacoes` para deixar explícito que command e query têm representações distintas por design.
- Section 6 (versionamento) e section 7 (mudanças de contrato) **saem** desta change. Total cai de 40 para ~32 tasks.
- **Gatilhos para reverter (registrar na raiz):** se (a) o primeiro parceiro B2B for integrado, (b) o command precisar expor um campo que conflita semanticamente com o nome atual, ou (c) entrar em vigor exigência regulatória que obrigue versionamento explícito — abrir nova change com versionamento por URI (próxima opção natural, com `spring.mvc.apiversion` nativo do Boot 4).

### D2 — Diferença command/query documentada, não corrigida (consistente com D1=C)

Os items 2 e 3 (formato de `status`, nomes de campo) ficam **documentados** como dívida aceita. O `CLAUDE.md` da raiz e a spec `listar-autorizacoes` ganham nota explícita: command e query têm representações distintas por design. Correção fica condicionada a um dos gatilhos da D1.

### D3 — Manter 422 para `@Valid` e para `BusinessException` (decisão de 2026-08-09)

A convenção que o código pratica é 422 para qualquer entrada inválida do cliente (seja de formato
via `@Valid`, seja de regra de negócio via `BusinessException`). Esta é a convenção assumida por
`integridade-fluxo-escrita` e `blindar-superficie-leitura`. A documentação que prometia 400 (no
`contratocommand/README.md`, `CLAUDE.md`, `AGENTS.md`) precisa ser alinhada ao código.
**Não há mudança de código** — apenas atualização de docs.

A opção por 422 como padrão único (em vez de 400 para formato + 422 para regra) é justificada
por três pontos:

1. **Coerência atual**: `ApiExceptionHandler` de ambos os apps (command e query) já mapeia
   `MethodArgumentNotValidException` para `HttpStatus.UNPROCESSABLE_CONTENT` (422). Voltar atrás
   exigiria **mexer em código e em testes** (`ApiExceptionHandlerTest` do command e do query já
   afirmam o 422 para `MethodArgumentNotValidException`).
2. **Princípio do menor esforço**: a dívida é puramente documental, e o conflito está confinado
   ao `contratocommand` (o `contratoquery` já documenta 422 corretamente na narrativa,
   embora ainda cite 400 na tabela de status — inconsistência residual, também resolvida aqui).
3. **Distinção entre "formato" e "regra" é marginal para o consumidor**: o cliente recebe um 422
   com `LayoutErrosApiValidationsResponse` (com `occurrences` por campo) ou `LayoutErrosApiResponse`
   (com `message` único) — o **shape da resposta** já carrega a distinção, sem precisar do
   primeiro byte do status.

Tabela vigente pós-D3:

| Origem | Status | Handler |
|---|---|---|
| `@Valid` / `MethodArgumentNotValidException` (formato) | **422** | `ApiExceptionHandler.validation` |
| Regra de negócio (`BusinessException`) | **422** | `ApiExceptionHandler.erroNegociosResponseEntity` / `erroNegocio` |
| Conflito (recurso duplicado, lock otimista, integridade) | 409 | handlers específicos no command; só `BusinessException` no query |
| Não encontrado | 404 | `ApiExceptionHandler.recursoNaoEncontrado` / `recursoNaoEncontrado` |
| Inesperado | 500 | catch-all genérico |

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
