## Context

O `contratocommand` tem borda REST madura — `@Valid` nos bodies, handler global único, convenção 422
documentada e verificada — mas essa borda tem **dois furos** por onde entrada inválida do cliente
atravessa sem ser tratada como tal:

```
                    BORDA REST                    APLICAÇÃO           BANCO
                        │                              │                │
 quantidadeDividasCiclo │  Integer (sem @Max)          │  short         │  INT
   = 32768 ─────────────┼──────────────────────────────┼───► -32768 ────┼──► -32768
                        │        (sem erro)            │                │
                        │                              │                │
 idAutorizacao          │  String ────────────────────►│ UUID.fromString│
   = "nao-e-uuid" ──────┼──────────────────────────────┼───► IAE ───────┼──► 500
                        │   (não validado aqui)        │                │
```

Nos dois casos o problema não é falta de padrão — é o padrão existente não ter sido aplicado. O
campo `frequencia` tem `@Min(1) @Max(4)`; seus dois vizinhos não têm `@Max`. A convenção "entrada
inválida do cliente → 422" está escrita na capability `contrato-api-consistente`; o path variable
malformado escapa dela porque o parsing acontece três camadas abaixo da borda.

O terceiro alvo é de manutenção: o bloco de carregamento + validação está triplicado nos três use
cases de escrita, e o `catch (Exception)` que o acompanha reembala `ConcurrencyFailureException` como
`ApplicationException`, anulando na aplicação o mapeamento 409 que o handler faz corretamente.

## Goals / Non-Goals

**Goals:**

- Nenhum valor numérico aceito pela borda muda de valor ao ser convertido para `short`.
- Identificador malformado responde 422, sem log de erro e sem contar como 5xx.
- Um único ponto de verdade para carregar a autorização nos três use cases de escrita.
- Conflito de concorrência no carregamento volta a responder 409.
- Zero mudança em resposta de sucesso e zero migration.

**Non-Goals:**

- **Migrar `AutorizacaoPersistenceMapper` para MapStruct.** É o maior risco de regressão silenciosa
  da app (24 campos × 3 métodos manuais), mas exige um teste de equivalência campo a campo escrito
  antes. Change própria.
- **Remover `@Data` do modelo de domínio.** Permite `setStatus()` direto, contornando
  `aprovar()`/`cancelar()`; mexer nisso é invasivo (o mapper depende dos setters).
- **Introduzir `Clock` injetável.** `LocalDateTime.now()` aparece em 9 pontos; é dívida real de
  testabilidade, mas ortogonal a esta change.
- **Reduzir o boilerplate do `ApiExceptionHandler`** (215 linhas, ~60% repetição de 4 setters) e
  **remover `getLogCode()`** (código morto em 10 classes, sem chamador em `src/main`). Varredura
  oportunista, não justifica change.
- **Atualizar a capability `coesao-contratocommand` como um todo** — ver Risco R3.

## Decisions

### D1: `AutorizacaoId` é um record no domínio que valida no construtor

O identificador passa a ser um value object (`domain/model/AutorizacaoId`), construído no controller
a partir do path variable e propagado tipado até o repositório.

```
ANTES                                  DEPOIS
─────                                  ──────
Controller  String idAutorizacao       Controller  AutorizacaoId.de(pathVariable)
    │                                      │            └─► valida formato AQUI
    ▼                                      ▼
Command     String idAutorizacao       Command     AutorizacaoId idAutorizacao
    │                                      │
    ▼                                      ▼
UseCase     UUID.fromString(...)       UseCase     (nenhuma conversão)
            └─► IllegalArgumentEx
                └─► 500
```

**Por que no domínio e não em `infrastructure/web`:** o identificador é conceito de domínio, não de
transporte — as portas de entrada (`*Command`) o carregam, e `domain/` não pode depender de
`infrastructure/`. Alinhado com "Wrap All Primitives" da skill `qualidade-codigo-java`: o formato
válido do id deixa de ser conhecimento espalhado em três use cases e passa a ser invariante do tipo.

**Por que lançar `BusinessException` e não uma exceção nova:** `BusinessException` já é mapeada para
422 com `LayoutErrosApiResponse`. Nenhuma mudança no `ApiExceptionHandler` é necessária — o handler
que já existe produz exatamente o contrato desejado.

**Por que `LayoutErrosApiResponse` (sem `occurrences`) e não o shape de validação:** o path variable
não passa por `@Valid`, logo não há `FieldError` para popular `occurrences`. Emitir o shape de
validação exigiria fabricar uma ocorrência sintética. A capability `contrato-api-consistente`
distingue os dois shapes por origem do erro, e esta origem não é Bean Validation.

**Alternativas consideradas:**

| Alternativa | Por que não |
|---|---|
| `@Pattern` regex no `@PathVariable` + `@Validated` | Gera `ConstraintViolationException`, que **não tem handler** hoje → viraria 500 do mesmo jeito, exigindo handler novo. Troca um furo por outro. |
| `try/catch` em torno do `UUID.fromString` no controller | Resolve o status, mas mantém o id como `String` cruzando as camadas e o parsing duplicado em 4 rotas. Não elimina a causa. |
| `Converter<String, AutorizacaoId>` registrado no Spring | Funciona, mas a falha vira `MethodArgumentTypeMismatchException` — outro caminho sem handler. Mais mágica, mesmo problema. |

### D2: A fonte única é um colaborador injetado, não uma superclasse

O trecho triplicado vira um `@Component` em `application/usecase` — provisoriamente
`CarregadorAutorizacao` — que recebe o `AutorizacaoId` e o produto/status a aplicar no comando, e
devolve a autorização carregada.

**Por que composição e não classe base abstrata:** herança acoplaria os três use cases a uma
hierarquia só para compartilhar 13 linhas, dificultaria o mock em teste unitário e violaria a
preferência do repositório por colaboradores explícitos (o mesmo motivo que levou à remoção dos
`*OrquestradorService`). Os três use cases já recebem `repository` e `eventPublisher` por construtor;
mais um colaborador segue o padrão vigente.

**Nome:** evita os genéricos proibidos pela skill (`Helper`, `Manager`, `Util`). Se na implementação
o nome soar melhor como `AutorizacaoParaEscrita` ou similar, é decisão livre — o requisito é ser
grepável e de domínio.

### D3: O `catch` estreita para o que é genuinamente inesperado

```java
// hoje — engole o que tem contrato próprio
} catch (Exception e) {
    throw new ApplicationException(...);   // ConcurrencyFailureException → 500 ❌
}
```

O `catch` passa a **não** capturar `ConcurrencyFailureException` (e subclasses), deixando-a propagar
até o handler que a mapeia para 409. `BusinessException` continua repassada como hoje.

**Por que não remover o `catch` inteiro:** o requirement "Erros inesperados usam exceções do próprio
projeto" da capability `coesao-contratocommand` exige que falha genuinamente inesperada seja
sinalizada como `ApplicationException`. O `catch` continua correto — só para de ser amplo demais.

### D4: `@Max(32767)` em vez de alargar `short` para `int`

Decisão do responsável pelo repositório: `indicadorUsoLimiteConta` é flag booleana (`@Min(0) @Max(1)`)
e `quantidadeDividasCiclo` recebe o limite físico do `short` (`@Min(1) @Max(32767)`).

O teto de 32767 é **deliberadamente técnico, não de negócio** — existe para impedir truncamento, não
para introduzir regra que ninguém definiu. Isso precisa estar comentado no código, senão a próxima
pessoa lê 32767 como limite de negócio.

**Alternativa considerada — trocar `short` por `int` no modelo:** elimina o truncamento na raiz e
alinha com a coluna `INT` do banco, mas toca modelo, entidade JPA, mapper (nos três métodos) e
testes. Maior alcance para o mesmo ganho imediato. **Gatilho para revisitar:** se negócio definir um
teto real acima de 32767, ou se a migração do mapper para MapStruct acontecer — aí o custo marginal
de alargar o tipo cai bastante.

## Risks / Trade-offs

**R1: mudança de assinatura dos três `*Command` quebra todos os call sites** → o compilador aponta
cada um; não há reflexão nem construção dinâmica desses records. Os testes afetados
(`AutorizacaoControllerTest`, três `*ServiceTest`) falham em compilação, não silenciosamente.

**R2: value object de domínio lançando exceção por erro de formato** pode ser lido como mistura de
responsabilidade (formato é assunto de borda). → Mitigado pela convenção D3 já vigente no repo:
formato e regra de negócio compartilham o 422, distinguidos pelo shape. O `AutorizacaoId` valida uma
invariante do próprio tipo — um id que não é UUID não é um identificador válido em nenhuma camada.

**R3: a capability `coesao-contratocommand` está defasada em relação ao código atual** — ela descreve
`ContratacaoContext`, pacotes `application/contratacao`, `entrypoint` e "domínio sem anotações
Spring", nada disso correspondendo ao código de hoje (comandos em vez de contexts,
`application/usecase`, `domain/service` com `@Component`/`@Service` por decisão D2 registrada no
`CLAUDE.md`). → Esta change toca **apenas** dois pontos dessa spec, ambos ainda fiéis ao código; o
delta não herda nem propaga a defasagem. Reconciliar o resto da capability é trabalho próprio, a ser
proposto separadamente — tentar fazê-lo aqui inflaria o escopo e misturaria correção de defeito com
atualização documental.

**R4: chamador que hoje trata 500 como transitório e faz retry** passará a receber 422 (definitivo)
para id malformado. → É a correção pretendida, não um efeito colateral: retry de id malformado nunca
teria sucesso. O único chamador automatizado conhecido é o `temporiza-autorizacao`, que aciona
`PATCH /decisao` com id vindo do próprio evento — sempre bem formado.

**R5: `@Max(32767)` lido como limite de negócio** por quem mexer depois. → Comentário de proveniência
obrigatório no ponto da anotação, explicando que o teto é a largura do `short` e apontando o gatilho
de D4.

## Migration Plan

Deploy direto, sem etapas: nenhuma migration, nenhum dado existente afetado, nenhum contrato de
sucesso alterado. Rollback é reverter o commit — não há estado persistido novo para desfazer.

Linhas já gravadas com valor truncado (se houver) **não** são corrigidas por esta change; a correção
impede novas ocorrências. Vale uma consulta de verificação em produção antes do deploy:
`SELECT count(*) FROM autorizacoes WHERE quantidade_dividas_ciclo < 0 OR indicador_uso_limite_conta NOT IN (0,1)`
— se retornar linhas, a limpeza é trabalho à parte.

## Open Questions

1. **Existe teto de negócio real para `quantidadeDividasCiclo`?** A change adota 32767 (limite do
   `short`) por não haver regra documentada. Se negócio definir um teto — algo como "máximo 12
   dívidas por ciclo" —, a constante muda e o gatilho de D4 (alargar o tipo) perde relevância.
   **Status: pendente, registrada explicitamente.** Não foi possível consultar o time de negócio
   dentro do escopo desta implementação (execução autônoma, sem canal síncrono disponível). Mantém-se
   a decisão original (32767, teto técnico) até alguém com autoridade de negócio definir um valor.
   Não bloqueia o fechamento/arquivamento desta change — é dívida documentada, com gatilho de
   revisão já registrado em D4.
2. **A verificação de dado já truncado em produção retorna linhas?** Determina se é preciso abrir
   trabalho de correção de dado histórico.
   **Status: não executada — registrada explicitamente, não respondida.** Ambiente desta
   implementação não tem acesso a `DB_PASSWORD` (variável obrigatória, sem default, conforme
   `PostgresLocalDisponivelCondition`); o container Postgres local (`postgres18-kiq`, ver `docker ps`)
   está no ar, mas sem credencial disponível nesta sessão a consulta não pôde ser executada nem
   contra ele, nem contra produção (que este agente não tem acesso de rede/credencial para alcançar).
   **Query pendente de execução, registrada aqui para quem tiver acesso:**
   ```sql
   SELECT count(*) FROM autorizacoes
   WHERE quantidade_dividas_ciclo < 0 OR indicador_uso_limite_conta NOT IN (0, 1);
   ```
   Se retornar linhas > 0, abrir trabalho de correção de dado histórico separado (esta change não
   corrige dado, só impede novas ocorrências — ver Migration Plan acima).
