## Why

Sexta e última das mudanças que migram as aplicações de `apps/` para a arquitetura hexagonal clássica,
e segunda etapa do `contratocommand`. Depende de `hexagonal-classico-contratocommand-portas` estar
entregue e com build verde.

Ao final da etapa anterior, cinco dos sete anti-padrões da skill `arquitetura-limpa-java` estão
resolvidos. Sobram os dois de persistência, os dois mais difíceis:

```
#2  Entidade JPA usada como modelo de domínio
      domain/model/Autorizacao  →  @Entity @Table @EmbeddedId @Version @Data
      25 colunas, Lombok gerando setter para tudo, domínio anêmico acoplado ao schema

#6  Domínio anotado com anotação de ORM
      domain/converters/TipoProdutoConverter  →  jakarta.persistence.AttributeConverter
      domain/utilities/ReversibleUUIDv7       →  partição do Postgres dentro do id do agregado
```

O segundo é mais fundo do que parece. `ReversibleUUIDv7` embute o número da partição **dentro do UUID
da autorização**, e `Autorizacao.inicializaCriacao()` gera essa identidade. Ou seja: a identidade do
agregado carrega uma decisão de layout físico do PostgreSQL. Na exploração de 2026-08-15 foi decidido
**isolar isso atrás de uma porta de saída** — o domínio pede uma identidade e deixa de saber que
partições existem.

Esta é a mudança de maior risco de toda a migração, e o motivo é concreto: **partir a entidade muda
como o JPA enxerga a escrita.** Hoje o cancelamento e a decisão mutam uma entidade *gerenciada* e o
commit gera o `UPDATE` por dirty checking. Com um modelo de domínio puro, o adaptador passa a
reconstruir a entidade — e o `CLAUDE.md` desta app já documenta, na armadilha nº 11, o que aconteceu
da última vez que uma instância detached encontrou `@Version`: `StaleObjectStateException` → 409
determinístico, imune a retry, que funcionou por meses até `@Version` existir.

Duas coisas não podem sair daqui quebradas em silêncio: o lock otimista e a movimentação de partição.

## What Changes

- **Separar modelo de domínio de entidade JPA:**

  | Depois | Onde | O quê |
  |---|---|---|
  | `Autorizacao` | `domain/model/` | Java puro, sem anotação, com comportamento de negócio |
  | `AutorizacaoJpaEntity` | `infrastructure/persistence/` | `@Entity`, `@Table`, `@EmbeddedId`, `@Column`, `@Convert`, `@Version`, `@JdbcTypeCode` |
  | `IdAutorizacaoJpaEmbeddable`, `CancelamentoJpaEmbeddable` | `infrastructure/persistence/` | `@Embeddable` |
  | `AutorizacaoPersistenceMapper` | `infrastructure/persistence/` | conversão **bidirecional**, com `version` |

- **Fazer o `version` trafegar nos dois sentidos.** O modelo de domínio passa a carregar a versão como
  dado opaco de controle de concorrência — sem ela, o `UPDATE` sai sem cláusula de versão, o lock
  otimista para de funcionar **sem erro nenhum**, e o cenário de cancelamento duplicado que a change
  `integridade-fluxo-escrita` fechou volta silenciosamente.

- **Introduzir a porta de identidade** `domain/port/out/GeradorIdentidadeAutorizacao`: o domínio pede
  um identificador para uma conta contratante e recebe um; quem sabe que esse identificador embute
  partição é `infrastructure/persistence/`. `Autorizacao.inicializaCriacao()` deixa de gerar o id — o
  caso de uso o obtém pela porta e o entrega ao modelo, que segue responsável por status inicial por
  produto, datas e defaults.

- **Mover as utilities de particionamento** `ReversibleUUIDv7`, `IdContaUUIDPartitionDistributor` e
  `ControleExpurgoAutorizacao` de `domain/utilities/` para `infrastructure/persistence/`, e os dois
  `AttributeConverter` de `domain/converters/` para o mesmo pacote.

- **Fazer `AutorizacaoEventoPayload` mapear do modelo de domínio**, não da entidade JPA. Ele já é um
  record dedicado com `@JsonProperty` por nome de coluna, e continua em `infrastructure/messaging/`.

- Ajustar os testes afetados, entre os 37 da app.
- Acrescentar à capacidade `layout-hexagonal-classico` os requisitos sobre travessia de versão no
  mapper, sobre identidade atrás de porta e o requisito final do `contratocommand`.
- Atualizar `apps/contratocommand/CLAUDE.md` e `AGENTS.md` (espelhos idênticos), incluindo as
  armadilhas nº 4 e nº 11, que descrevem o estado anterior.

- **Nenhuma mudança de contrato, de schema ou de comportamento observável.** As três rotas, os códigos
  de status, o lock otimista com seus 409, a movimentação de linha entre partições, o expurgo, os
  eventos e seus message attributes permanecem idênticos.

- **Fora de escopo:** a divergência de representação entre command e query. Segue como dívida aceita.
- **Fora de escopo:** trocar o esquema de particionamento ou a geração de id. A porta **esconde** a
  estratégia atual; não a substitui. `ReversibleUUIDv7` continua gerando exatamente os mesmos ids.
- **Fora de escopo:** unificar `Autorizacao` com a do `contratoquery`. Continuam cópias independentes.

## Capabilities

### Modified Capabilities

- `layout-hexagonal-classico`: acrescenta (a) o requisito de que o controle de concorrência otimista
  atravesse o mapper íntegro, com verificação empírica obrigatória; (b) o requisito de que a
  identidade do agregado não carregue conhecimento de layout físico no domínio, ficando atrás de porta
  quando a estratégia de geração depende do armazenamento; e (c) o requisito final do
  `contratocommand`, que fecha a migração da frota.

## Impact

- **Código afetado:** `domain/model/Autorizacao` reescrita; classes novas `AutorizacaoJpaEntity`,
  `IdAutorizacaoJpaEmbeddable`, `CancelamentoJpaEmbeddable`, `AutorizacaoPersistenceMapper`,
  `GeradorIdentidadeAutorizacao` (porta) e seu adaptador; 5 classes movidas de `domain/` para
  `infrastructure/persistence/`; `AutorizacaoJpaAdapter` passa a mapear em todos os métodos.
- **Semântica de escrita (o ponto crítico):** o dirty checking sobre entidade gerenciada deixa de ser
  o mecanismo de `UPDATE`. Cancelamento e decisão passam por carregar → mapear → mutar o modelo →
  mapear de volta → gravar. É onde mora o risco de reencontrar a armadilha nº 11.
- **Concorrência:** `ObjectOptimisticLockingFailureException` → 409 e `ConcurrencyFailureException`
  (SQLSTATE 40001, `tuple to be locked was already moved to another partition`) → 409 devem continuar
  disparando nos mesmos cenários. `ConcorrenciaOptimisticaIntegrationTest` é o teste que prova, e ele
  **não pode** ser pulado ao fechar esta mudança.
- **Banco:** nenhuma migration. O mapeamento coluna a coluna é preservado integralmente, incluindo
  `metadados` em jsonb e a decisão documentada de **não** declarar em `@Table` a unicidade de
  `id_autorizacao_empresa` — que no banco é índice único **parcial** (só partições quentes), forma que
  o JPA não expressa.
- **`contratoquery`:** compartilha a tabela, não o código. Nenhum impacto.
- **`temporiza-autorizacao`:** aciona `PATCH /decisao`. Nenhuma alteração.
- **Documentação:** além do `CLAUDE.md`/`AGENTS.md` da app, o `CLAUDE.md` da raiz e a skill
  `arquitetura-limpa-java` (seção "Equivalência com a estrutura legada") passam a descrever um estado
  que deixou de existir — a frota inteira estará migrada.
