## Context

A tabela `autorizacoes` é particionada por `LIST (id_particao_conta)`. Autorizações nascem
numa **partição quente** (`0..888`, derivada do hash da conta contratante e embutida no
UUIDv7) e, ao chegarem a um estado terminal, são movidas para a **partição de expurgo**
(`900..999`, derivada do balde semanal da data da transição) para que a retenção seja
resolvida por `DROP PARTITION`.

Como JPA não permite alterar o `@EmbeddedId` de uma entidade gerenciada, a v3.0.0 implementou
a movimentação como `delete → flush → detach → mutar o id → save`. O comentário no código
explica a razão do `flush`/`detach`, mas **não registra a premissa oculta que fazia o `save`
final virar um `INSERT`**: a ausência de `@Version`.

### O mecanismo exato da falha

`repository.save(instânciaDetached)` → `JpaMetamodelEntityInformation.isNew()` consulta o
campo de versão; sendo não-nulo, a entidade não é nova → `EntityManager.merge()`.

`DefaultMergeEventListener.entityIsDetached` (hibernate-core 7.2.19):

```java
final Object result = session.get( entityName, clonedIdentifier );   // linha já apagada → null

if ( result == null ) {
    final Boolean knownTransient = persister.isTransient( entity, session );
    if ( knownTransient == Boolean.FALSE ) {
        throw new StaleObjectStateException( entityName, id );
    }
    else {
        entityIsTransient( event, clonedIdentifier, copyCache );   // → INSERT
    }
}
```

`AbstractEntityPersister.isTransient` (linha 3891):

```java
if ( isVersioned() ) {
    final Object version = getVersion( entity );                       // 0L
    return versionMapping.getUnsavedStrategy().isUnsaved( version );   // → FALSE
}
// sem @Version e com id atribuído, o método segue adiante e devolve null ("não sei")
```

```
                merge(instância detached cuja linha sumiu)
                                │
                  persister.isTransient(entity) ?
                                │
     ┌──────────────────────────┴──────────────────────────┐
     │ null  "não sei"                                     │ FALSE  "é detached"
     │ = sem @Version (v3.0.0 → 2026-08-09 16:05)          │ = com @Version (16:06 →)
     ▼                                                     ▼
 entityIsTransient → INSERT na partição nova ✅   StaleObjectStateException ❌ → 409
```

O Hibernate não tem como distinguir *"a linha sumiu porque outra transação a apagou"* de
*"a linha sumiu porque eu mesmo a apaguei duas instruções atrás"*. O `@Version` é
precisamente o sinal que o faz assumir a primeira hipótese. O `409` resultante é um falso
positivo de lock otimista produzido pela transação contra si mesma — determinístico, imune a
retry.

### Restrições

- PostgreSQL 18, particionamento por LISTA, sem fallback H2.
- `@Version` é requisito de `concorrencia-otimista-autorizacao` e não pode ser removido.
- `ExpurgoAutorizacaoService` é compartilhado por cancelamento e decisão; qualquer correção
  vale para os dois.
- A app roda sob `@Transactional` no use case chamador; o serviço participa da transação
  existente e não abre a sua.

## Goals / Non-Goals

**Goals:**

- Transferência de partição concluída com sucesso em transação isolada, em `PATCH /cancelar`
  e em `PATCH /decisao` (`REJEITAR` e `EXPIRAR`).
- Preservar integralmente a proteção de lock otimista contra escritas concorrentes reais.
- Eliminar a classe de fragilidade — não apenas esta ocorrência: nenhuma correção que
  dependa de o Hibernate inferir corretamente o estado de uma instância detached.
- Fechar a colisão de unicidade que aflora na partição de expurgo assim que o defeito
  principal for corrigido.
- Deixar a garantia coberta por teste contra banco real.

**Non-Goals:**

- Rever a estratégia de particionamento ou o cálculo das partições.
- Mudar o contrato HTTP de `/decisao` ou `/cancelar`.
- Alterar `temporiza-autorizacao` (seu comportamento está correto).
- Corrigir o índice `idx_autorizacoes_conta_status_data` (`INVALID`) — change separada.
- Reprocessar as autorizações já presas em `RECEBIDA` como parte do código (é operação
  manual, documentada em `tasks.md`).

## Decisions

### D1 — Movimentar a linha com `UPDATE` do `id_particao_conta` (row movement nativo do PostgreSQL)

O PostgreSQL ≥ 11 move a linha entre partições automaticamente quando um `UPDATE` altera a
chave de particionamento, de forma atômica e dentro da mesma transação. A movimentação
passa a ser:

```
1. repository.saveAndFlush(autorizacao)
      → UPDATE ... SET status=?, motivo_status=?, data_hora_ultima_atlz=?, version=version+1
        WHERE id_autorizacao=? AND id_particao_conta=? AND version=?
      → dirty-check normal do Hibernate: colunas de negócio + @Version, ainda na partição atual

2. repository.moverParaParticao(idAutorizacao, particaoAntiga, novaParticao)
      → @Modifying nativo: UPDATE autorizacoes SET id_particao_conta=?
                           WHERE id_autorizacao=? AND id_particao_conta=?
      → o PostgreSQL faz o row movement; a operação SHALL afetar exatamente 1 linha

3. entityManager.detach(autorizacao)
   autorizacao.getIdAutorizacao().setIdParticaoConta(novaParticao)
      → sincroniza a instância em memória com a nova localização física, para o
        AutorizacaoPersistidaEvent e o response DTO
```

**Por que esta e não as alternativas:**

| Alternativa | Por que foi descartada |
|---|---|
| **A. `autorizacao.setVersion(null)` antes do `save`** — faz `isNew()` devolver `true`, o Spring Data chamar `persist()` e o Hibernate semear versão `0`. Correção de uma linha. | Funciona, mas o contador de versão **reinicia em 0** a cada movimentação, o que viola o cenário vigente "Escrita isolada incrementa a versão" de `concorrencia-otimista-autorizacao`. Adotá-la exigiria enfraquecer um requisito existente para acomodar uma limitação de implementação. Pior: mantém o `merge` de instância detached — a mesma armadilha, agora escorada por uma linha cuja necessidade não é óbvia e que a próxima refatoração remove. |
| **C. Não mover partição na transição de estado** (expurgo assíncrono por job) | Muda a decisão de design de `expurgo-estados-terminais`, que é recente e deliberada. Trocar arquitetura para contornar um mal-entendido de um campo é desproporcional. |
| **D. Remover `@Version`** | Reverteria `concorrencia-otimista-autorizacao` inteira. Fora de questão. |

A D1 é a única que **remove a causa** em vez de neutralizá-la: sem `merge` de instância
detached, a inferência frágil do Hibernate deixa de estar no caminho. Como bônus, a
movimentação vira uma única instrução atômica no banco, em vez de um `DELETE` seguido de um
`INSERT` que o ORM tem de reconstruir a partir do estado em memória.

**Custo aceito:** entra SQL nativo num repositório que hoje só tem JPQL. É um caso onde a
operação não tem expressão em JPA por definição (JPA não altera chave primária), então
descer ao SQL é honesto, não uma fuga.

### D2 — A verificação da contagem de linhas afetadas faz parte do contrato

O `UPDATE` nativo do passo 2 **SHALL** afetar exatamente uma linha. Zero linhas significa que
a linha sumiu entre o passo 1 e o passo 2 — cenário que o lock de linha tomado no passo 1
torna impossível, mas cuja verificação é barata e transforma uma corrupção silenciosa
(entidade respondida como movida, linha parada na partição antiga) em erro explícito.

Zero linhas SHALL resultar em exceção tratada como conflito (`409`), coerente com o
tratamento já dado a `StaleStateException` em `ApiExceptionHandler`.

### D3 — Escopo da unicidade de `id_autorizacao_empresa` nas partições de expurgo

A constraint `uk_autorizacao_empresa_particao (id_particao_conta, id_autorizacao_empresa)`
tem semânticas diferentes conforme a faixa da partição:

```
partição quente (0–888):    id_particao_conta = hash(conta contratante)
                            → "um id_autorizacao_empresa por conta"          ✅ intenção original

partição expurgo (900–999): id_particao_conta = balde da semana
                            → "um id_autorizacao_empresa por semana,
                               somando TODAS as contas"                       ⚠️ colisão entre contas
```

Duas autorizações de contas distintas com o mesmo `id_autorizacao_empresa`, chegando a estado
terminal na mesma semana, colidem — `DataIntegrityViolationException` → `409`, com sintoma
indistinguível do defeito principal. Não é hipotético: os próprios dados de teste locais usam
chaves repetíveis (`dda-teste`, `verificacao-pix-auto-001`).

A origem do problema é uma regra do PostgreSQL, verificada empiricamente contra a instância
local (PostgreSQL 18, schema descartável):

```
CREATE UNIQUE INDEX ... ON t (conta, empresa);       -- sem a coluna de particionamento
ERROR: unique constraint on partitioned table must include all partitioning columns
DETAIL: UNIQUE constraint on table "t" lacks column "part" which is part of the partition key.
```

`id_particao_conta` está na constraint por **imposição do Postgres**, não por intenção de
modelagem. Nas partições quentes, "único por partição" coincide com "único por conta" porque
a partição *é* o hash da conta (`IdContaUUIDPartitionDistributor.getPartitionFast`); nas de
expurgo essa coincidência desaparece.

Opções, ambas verificadas empiricamente:

| Opção | Resultado medido |
|---|---|
| **D3a — Índice único parcial restrito às partições quentes** (`WHERE id_particao_conta < 900`). O Postgres aceita predicado em índice único de tabela particionada e o propaga a cada partição; nas de expurgo o predicado nunca é satisfeito, e o índice fica inerte. | Contas distintas no expurgo: **passam** ✅. Duplicata exata da **mesma** conta no expurgo: **também passa** ⚠️ — a garantia não fica relaxada, fica *desligada* na faixa inteira. Partição quente: segue barrando ✅. |
| **D3b — Incluir `id_unico_conta_contratante` na chave** (`id_particao_conta, id_unico_conta_contratante, id_autorizacao_empresa`). | Contas distintas no expurgo: **passam** ✅. Mesma conta duplicada, tanto no expurgo quanto na partição quente: **barrada** ✅. A constraint significa "um por conta" nas duas faixas. |

**DECISÃO: D3a** (tomada em 2026-08-09 pelo dono do produto). A unicidade de
`id_autorizacao_empresa` é uma regra sobre **autorizações ativas**, não um invariante da
tabela. Linhas em partição de expurgo estão em estado terminal e existem apenas até o próximo
`DROP PARTITION` — impor unicidade sobre elas é aplicar uma regra de negócio a dados que já
saíram do negócio.

Consequência aceita conscientemente: nas partições `900..999` a unicidade fica **desligada**,
não apenas relaxada. Duplicata exata (mesma conta, mesma chave de empresa) passa a ser
possível ali, e o índice único deixa de servir de detector de duplicação acidental nessa
faixa. O que continua protegendo contra duplicata exata é a chave primária
`(id_autorizacao, id_particao_conta)`.

Alternativa descartada — **D3b**, incluir `id_unico_conta_contratante` na chave: faria a
constraint significar "um por conta" nas duas faixas, mas trataria a unicidade como
invariante da tabela, que é justamente a leitura rejeitada pela decisão acima.

**Custo de acoplamento a registrar:** o predicado `WHERE id_particao_conta < 900` codifica a
fronteira entre faixa quente e faixa de expurgo num terceiro lugar (hoje ela vive em
`ControleExpurgoAutorizacao.obterParticaoExpurgoWrite`, que soma 900, e na migration que cria
as partições). Mudar a fronteira passa a exigir tocar também no índice.

### D3-pré — Nulidade das colunas da chave

Verificação do schema real (`information_schema.columns`):

```
 id_autorizacao_empresa     | is_nullable = YES
 id_unico_conta_contratante | is_nullable = YES
```

Ambas são declaradas `nullable = false` na entidade `Autorizacao`, mas com `ddl-auto: none`
quem vale é o banco — a anotação apenas documenta. Como `UNIQUE` no Postgres trata `NULL`
como distinto de qualquer outro `NULL`, **a constraint vigente já tem um buraco**: N linhas
com `id_autorizacao_empresa` nulo coexistem hoje em qualquer partição. A D3b herdaria o
buraco e acrescentaria um segundo (conta nula).

Com a D3a escolhida, a única coluna relevante é `id_autorizacao_empresa` (a conta não entra na
chave). O buraco de `NULL` **não é fechado por esta mudança**: é pré-existente, ortogonal ao
defeito em correção, e fechá-lo junto misturaria escopos. Fica registrado como dívida
conhecida — `id_autorizacao_empresa` deveria ser `NOT NULL` no banco, coerente com o que a
entidade já declara. Verificado que hoje não há linha com valor nulo (0 de 23), então a
migration futura é aplicável sem limpeza prévia.

### D4 — Teste contra banco real é parte da correção, não um extra

`ExpurgoAutorizacaoServiceTest` é `@ExtendWith(MockitoExtension.class)` com
`@Mock AutorizacaoRepository` e `@Mock EntityManager`, e verifica a **ordem** das chamadas
(`inOrder(deleteById, flush, detach, save)`). Um teste dessa forma não poderia ter detectado
este defeito em nenhuma circunstância: o bug não está na sequência de chamadas, está na
decisão que o Hibernate toma dentro do `save` diante de um banco real.

A movimentação de partição **SHALL** ser coberta por teste de integração contra PostgreSQL
real (Testcontainers ou o Postgres local já exigido pelo build), afirmando que após a
operação a linha existe na partição de destino, não existe na de origem, e preserva os dados.

## Risks / Trade-offs

- **SQL nativo introduz acoplamento ao PostgreSQL** → A app já é declaradamente
  PostgreSQL-only (particionamento com `pg_partman`, sem fallback H2). O acoplamento é
  pré-existente e assumido; o `UPDATE` fica isolado numa única query anotada no repositório.

- **`@Modifying` opera fora do persistence context** → Após o passo 2, a instância em memória
  aponta para uma localização que não existe mais. Mitigação: `detach` imediato seguido do
  ajuste do `@EmbeddedId` (passo 3), na mesma ordem hoje usada. Se o `detach` for esquecido,
  o flush de commit não emite nada (a entidade está limpa após o `saveAndFlush`), mas a
  instância devolvida ao chamador ficaria mentindo sobre a própria partição.

- **A alteração da chave única (D3b) mexe numa constraint referenciada por outra spec** →
  Exige migration e revalidação de `existsByIdAutorizacao_IdParticaoContaAndIdAutorizacaoEmpresa`
  no `CriarAutorizacaoUseCase`. Mitigação: tratar D3 como decisão separável — o defeito
  principal (D1) pode ser entregue e verificado antes, sem depender dela.

- **Autorizações já presas não se recuperam sozinhas** → As entradas do stream de expirações
  já esgotaram as 5 tentativas e receberam `XACK`. Nenhum agendamento resta no Valkey.
  Mitigação: **nenhuma** — decidido em 2026-08-09 que não haverá reprocessamento, por serem
  autorizações de teste em ambiente local. Em produção, este mesmo defeito exigiria um
  procedimento de recuperação, já que o temporizador não reagenda por conta própria.

- **O bug pode ter irmãos não descobertos** → Qualquer outro ponto que faça `merge` de uma
  instância detached cuja linha foi apagada na mesma transação tem o mesmo defeito.
  Mitigação: a busca por outras ocorrências de `detach(` + `save(` é um item de verificação
  em `tasks.md`.

## Migration Plan

1. Corrigir D1 + D2 e verificar contra o Postgres local (a movimentação passa a concluir).
2. Rodar a suíte completa das duas apps que leem a tabela (`contratocommand`,
   `contratoquery`).
3. Aplicar D3a com migration própria, em passo separado e reversível.
4. Validar fim-a-fim com autorização nova (as presas não serão reprocessadas — ver Open
   Questions).

**Rollback:** D1/D2 são alterações de código sem mudança de schema — reverter o commit
restaura o comportamento anterior (quebrado, porém conhecido). D3 exige migration reversa da
constraint; deve ser entregue separada justamente para poder ser revertida sozinha.

## Open Questions

- ~~**D3a ou D3b?**~~ **Resolvido em 2026-08-09: D3a.** A unicidade vale apenas para
  autorizações ativas. Ver D3.
- ~~**As autorizações presas devem ser expiradas ou canceladas?**~~ **Resolvido em
  2026-08-09: nenhuma ação.** São autorizações de teste em ambiente local; não há dado real
  a recuperar. A validação fim-a-fim é feita com autorização nova.
- **`id_autorizacao_empresa` deveria ser `NOT NULL`?** Dívida pré-existente, deliberadamente
  fora do escopo desta mudança (ver D3-pré). Merece change própria.
