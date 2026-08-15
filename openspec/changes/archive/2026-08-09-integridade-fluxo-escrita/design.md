## Context

O `contratocommand` grava autorizações de pagamento recorrente numa tabela particionada
compartilhada com o `contratoquery`. Dois caminhos de escrita existem: criação (POST) e
cancelamento (PATCH). A auditoria de 2026-08-04 mapeou o estado atual de cada um:

```
CRIAÇÃO                                CANCELAMENTO
POST /api/autorizacoes                 PATCH /api/autorizacoes/{id}/cancelar
   │                                      │
   ├─ ContratacaoValidator                ├─ CancelamentoValidator
   │    ProdutoSuportado                  │    ProdutoSuportadoCancelamento
   │    TipoProduto                       │    TipoProdutoCancelamento
   │    ValorLimiteContrato               │      └── compara só tipoProduto
   │                                      │
   ├─ ✗ nenhuma checagem de duplicidade   ├─ findByIdAutorizacaoAndParticao (sem lock)
   │                                      ├─ ✗ nenhuma checagem de status atual
   ├─ save (idAutorizacao UUIDv7 novo)    ├─ setters + transferirParaNovaParticao
   │                                      │    (delete + flush + detach + save)
   └─ evento ATIVACAO no SNS              └─ evento CANCELAMENTO no SNS
```

Três ausências, um efeito comum: **nada impede que duas requisições produzam dois estados ou dois
eventos onde deveria haver um.**

A restrição mais relevante ao desenho: o cancelamento não é um `UPDATE` simples. Ele move a linha
entre partições (`delete` + `flush` + `detach` + `save`), porque a partição de expurgo depende do
status. Isso afeta como o lock otimista se comporta e é a razão pela qual `@Version` sozinho não
basta — ver D1.

## Goals / Non-Goals

**Goals:**

- Tornar impossível que dois cancelamentos concorrentes ambos sucedam.
- Tornar impossível que o mesmo `id_autorizacao_empresa` produza duas autorizações.
- Fazer o grafo de transições de `maquina-estados-autorizacao` valer em runtime, não só no papel.
- Que ambos os cenários rejeitados produzam erro de contrato legível, não 500.

**Non-Goals:**

- `Idempotency-Key` completo com replay da resposta original.
- Lock pessimista (`SELECT FOR UPDATE`).
- Validar transição na criação.
- Mover `TipoEventoAutorizacao` de pacote (fica em `reconciliar-contrato-spec-doc`).

## Decisions

### D1 — `@Version` **e** rule de transição, não um ou outro

Os dois mecanismos parecem redundantes, mas cobrem janelas diferentes:

| Cenário | Rule de transição | `@Version` |
|---|---|---|
| Cancelar autorização já `CANCELADA` (sequencial) | **pega** | não pega — versão atual bate |
| Dois PATCH concorrentes, ambos leem `ATIVA` | não pega — ambos veem estado válido | **pega** — segundo commit falha |

A rule resolve o caso sequencial com erro de negócio claro; o `@Version` resolve a corrida real.
Implementar só um deixa metade do problema.

Complicação específica deste código: o cancelamento faz `delete` + `save` para trocar de partição.
Um `delete` seguido de `save` de entidade destacada pode não disparar a checagem de versão como
num `UPDATE` convencional. **A implementação precisa verificar empiricamente** que o
`OptimisticLockException` de fato ocorre nesse caminho — está previsto como task de validação com
teste de concorrência real. Se não ocorrer, a alternativa é lock pessimista via
`@Lock(PESSIMISTIC_WRITE)` na busca do cancelamento, aceitando o custo de contenção.

**Resultado empírico (2026-08-09)**: confirmado. Duas transações concorrentes cancelando a mesma
autorização, ambas disparando o caminho `delete`+`flush`+`detach`+`save` (partições diferentes,
o caso real), resultaram em `ObjectOptimisticLockingFailureException` — nas duas transações, não
apenas na segunda. Interpretação: o `DELETE ... WHERE id = ? AND version = ?` gerado pelo
Hibernate para entidade versionada, quando a linha já foi removida pela transação concorrente,
afeta 0 linhas e Hibernate trata isso como falha de lock otimista (mesmo sinal que uma falha de
`UPDATE` por versão divergente). Resultado aceito: o objetivo da mudança ("nunca ambos sucedem
silenciosamente") está satisfeito mesmo quando as duas falham — o cliente recebe 409 dos dois
lados e pode repetir. Não foi necessário lock pessimista.

### D2 — `UNIQUE` no banco **e** checagem na aplicação

A checagem via `existsByIdAutorizacaoEmpresa` não é suficiente sozinha: entre o `exists` e o
`save` existe uma janela de corrida. A constraint no banco é a garantia real; a checagem na
aplicação existe para produzir erro de contrato legível no caso comum, em vez de deixar vazar uma
`DataIntegrityViolationException`.

O handler também precisa tratar a violação de constraint como caminho esperado, para o caso da
corrida — cinto e suspensório, com papéis distintos.

Alternativa considerada: só a constraint, traduzindo a exceção. Descartada porque acopla a
mensagem de erro ao nome da constraint e torna o código dependente de detalhe do banco.

### D3 — Códigos de status HTTP para os novos erros

- **Criação duplicada → `409 Conflict`.** O recurso já existe; é a semântica exata do 409.
- **Conflito de concorrência → `409 Conflict`.** Mesma família: o estado mudou sob os pés do
  cliente, e o retry é a ação apropriada.
- **Transição inválida (cancelar já cancelada) → `422`.** É violação de regra de negócio, e o
  serviço já usa 422 para `BusinessException`.

Ressalva registrada: a proposta `reconciliar-contrato-spec-doc` vai revisitar a convenção 422
vs 400 deste serviço, porque o código usa 422 para `@Valid` enquanto o `README.md` promete 400.
Esta mudança segue a convenção **vigente no código** para não criar um terceiro padrão; se a
reconciliação alterar a convenção, estes códigos acompanham.

### D4 — Duplicatas preexistentes bloqueiam a migration

`ALTER TABLE ... ADD CONSTRAINT UNIQUE` falha se a base já contiver duplicatas. A migration precisa
ser precedida de uma query de diagnóstico (`GROUP BY id_autorizacao_empresa HAVING COUNT(*) > 1`)
em cada ambiente.

Se houver duplicatas, o que fazer com elas **é decisão de negócio, não técnica** — pode envolver
autorizações ativas legítimas que precisam de tratamento manual. Está registrado em Open Questions
e como task explícita de levantamento antes da migration.

Nota: a tabela é particionada. Em Postgres, constraint única em tabela particionada exige que a
chave de particionamento faça parte da constraint, ou aplicação por partição. Como o
particionamento é por `id_particao_conta` e a chave de negócio é `id_autorizacao_empresa`, a
implementação precisa verificar qual forma é viável — possivelmente índice único por partição via
template, como já se faz para outros índices neste schema.

### D5 — Teste de concorrência precisa ser real

`CancelarAutorizacaoUseCaseTest` mocka `AutorizacaoRepository` inteiro. Um mock nunca vai produzir
`OptimisticLockException`, então o teste passaria sem provar nada. A validação exige duas
transações reais competindo contra Postgres (Testcontainers), disparadas de threads distintas.

Sem isso, não há evidência de que a correção funciona — só de que o código compila.

## Risks / Trade-offs

- **O `delete`+`save` do cancelamento pode não disparar checagem de versão** → Maior incerteza
  técnica da mudança. Mitigação: teste de concorrência real antes de considerar pronto; se falhar,
  cair para lock pessimista (D1). Não assumir que `@Version` funciona só porque foi anotado.

- **Duplicatas preexistentes podem inviabilizar a migration** → Levantamento obrigatório antes de
  escrever a migration; o tratamento é decisão de negócio.

- **Constraint única em tabela particionada tem restrições no Postgres** → Verificar viabilidade
  na fase de design da migration; alternativa é índice único por partição via template.

- **`@Version` adiciona coluna à tabela compartilhada com o `contratoquery`** → A entidade de
  leitura precisa mapear ou ignorar a coluna. Verificação incluída nas tasks; risco baixo, mas
  quebra o serviço de leitura se esquecida.

- **Clientes podem depender do comportamento atual de criar duplicata** → É comportamento
  incorreto, mas alguém pode ter construído em cima dele. A rejeição passa a ser 409 explícito;
  vale comunicar antes de subir.

- **Contenção sob carga se cair para lock pessimista** → Só se o plano A falhar. O lock seria
  restrito à busca do cancelamento, operação curta.

- **Achado em revisão (2026-08-09, Importante I3)**: `ExpurgoAutorizacaoService` move a linha
  cancelada para a partição `900 + (semanas desde epoch % 100)` — **todos os cancelamentos da
  mesma semana, de todas as contas, caem na mesma partição de expurgo**. Dentro dela,
  `UNIQUE (id_particao_conta, id_autorizacao_empresa)` vira, na prática, unicidade quase-global de
  `id_autorizacao_empresa` por semana. Cenário de risco: duas autorizações de contas diferentes
  com o mesmo `id_autorizacao_empresa` (a checagem de duplicidade na criação é por partição de
  *criação*, que é diferente da de *expurgo* — duas contas distintas passam pela checagem sem
  problema) sendo canceladas na mesma semana → a segunda tentativa de transferência para expurgo
  colide na constraint → 409 permanente, sem caminho de retry que resolva (a autorização fica
  presa, impossível de cancelar). **Risco aceito nesta entrega**, sem mitigação implementada —
  registrado explicitamente para não ser esquecido: mitigação futura possível é excluir
  `id_autorizacao_empresa` da constraint nas partições 900-999 (índice único parcial só nas
  partições de vigência, não nas de expurgo) ou aceitar formalmente com um passo operacional de
  liberação manual. Nenhum teste cobre este cenário nesta entrega.

## Migration Plan

1. Diagnóstico de duplicatas de `id_autorizacao_empresa` em cada ambiente.
2. Tratamento das duplicatas encontradas (decisão de negócio).
3. Migration: coluna de versão (com default para linhas existentes) + constraint/índice único.
4. Deploy do código: `@Version`, rule de transição, checagem de duplicidade, handlers de erro.
5. Validação com teste de concorrência real em ambiente com Postgres.

Rollback: a migration é aditiva — a constraint pode ser removida e a coluna de versão ignorada sem
perda de dados. Reverter o código restaura o comportamento anterior.

## Open Questions (resolvidas em 2026-08-09)

- **Existem duplicatas de `id_autorizacao_empresa` em produção?** Não há ambiente de produção
  real neste projeto (pessoal/de estudo) — verificado no Postgres local (20 linhas de teste, 0
  duplicatas). Não bloqueou a migration.
- **Postgres aceita constraint única em `id_autorizacao_empresa` nesta tabela particionada, ou
  será necessário índice único por partição?** A tabela é `PARTITION BY LIST (id_particao_conta)`,
  e o Postgres exige que a chave de particionamento integre qualquer `UNIQUE`. Resolvido com
  `UNIQUE (id_particao_conta, id_autorizacao_empresa)`. Como `id_particao_conta` é derivado
  deterministicamente de `idUnicoContaContratante` (mesma conta → mesma partição, sempre), essa
  constraint garante unicidade dentro da mesma conta — exatamente o cenário de retry de POST
  descrito na proposta. Gap residual aceito: duas contas diferentes escolhendo por coincidência o
  mesmo `id_autorizacao_empresa` não seriam pegas (partições diferentes); fora do cenário que a
  proposta descreve.
- **O `delete`+`save` do cancelamento dispara `OptimisticLockException`?** Sim, confirmado
  empiricamente (ver D1) — em ambas as transações concorrentes no teste real. Lock pessimista não
  foi necessário.
