## Why

A auditoria arquitetural do `java-revisor` (modo `auditoria`, uma rodada por app) nas cinco
aplicações do monorepo encontrou 3 achados críticos — já corrigidos e revalidados fora desta
change — e 23 achados Importantes/Menores que não bloqueiam, mas deixam as cinco apps em desacordo
parcial com as próprias regras que `layout-hexagonal-classico` e `higiene-codigo-morto` já
declaram (domínio anêmico em vez de comportamento, DTO de resposta expondo modelo de domínio bruto,
`@Data` em entidade JPA proibido pela skill `persistencia-jpa`, métodos e classes sem chamador de
produção). Sem correção, cada achado é uma pequena divergência entre o que o repositório documenta
como regra e o que o código realmente faz — o tipo de gap que a próxima pessoa (ou agente) só
descobre auditando de novo.

## What Changes

- **contratocommand**: `Autorizacao` (domain/model) ganha comportamento próprio (`aprovar()`,
  `rejeitarPeloPagador()`, `expirarJornada1()`, `cancelar(Cancelamento)`) em vez de os `*Service`
  mutarem campos diretamente; `CancelamentoResponseDto` próprio substitui a exposição direta de
  `domain/model/Cancelamento` na resposta HTTP; `CriarAutorizacaoCommand` passa a tipar
  `tipoProduto` como `TipoProduto` (hoje é `String`, assimétrico com os outros dois comandos);
  `ValorLimiteContrato` para de usar `switch` sobre literais de produto; `@Data` sai das três
  classes JPA (entidade + 2 embeddables); dois métodos de repositório sem chamador que furam a
  poda de partição são removidos; limpezas menores de comentário/javadoc/constante.
- **contratoquery**: converters JPA que traduzem dado corrompido do banco passam a lançar
  `ApplicationException` (500), não `BusinessException` (422); `status` ganha
  `StatusAutorizacaoConverter` (mesmo padrão dos outros dois `@Convert`), eliminando a tradução
  duplicada e sem tratamento de erro na borda web; `metadados` para de ser reparseado por
  `ObjectMapper` próprio em cada DTO, com falha engolida em silêncio; `@JoinColumn` incorreto vira
  `@Column` nos embeddables; a coluna `indicador_tipo_mensageria ` perde o espaço à direita
  (alinhando com o `contratocommand`, que já corrigiu); `@Data` sai das classes JPA; DTOs de
  resposta viram `record`.
- **autorizacaostatus-producer**: o `catch (RuntimeException)` amplo do listener SQS é restrito
  aos tipos realmente esperados, parando de classificar falha transitória como descarte
  permanente; o segundo caminho de classificação de erro (no producer Kafka) é documentado no
  `CLAUDE.md`/`AGENTS.md`; o grafo de transição de status sem uso em produção é removido; NPE
  latente no interceptor de erro (`getHeaders().getId()` pode ser `null`) é fechado; `ObjectMapper`
  vira bean.
- **eventos-consumer**: `DefaultErrorHandler` passa a classificar explicitamente exceção de
  negócio como não-retryable; `domain/exception/EventoAutorizacaoInvalidoException` substitui
  `IllegalArgumentException` genérica nos dois pontos de validação; `@NoArgsConstructor` do Lombok
  sai do enum de domínio, campo vira `final`; `group-id` para de ser configurado em dois lugares.
- **temporiza-autorizacao**: classificação de erro do consumo do stream passa a cobrir qualquer
  `RuntimeException`, não só a retryable conhecida; `PendenciasSchedulerReivindicador` muda de
  pacote (`infrastructure/messaging` → `infrastructure/scheduler`, conforme a própria convenção
  documentada); os dois `catch` mudos que mascaram degradação real do Valkey passam a logar o
  caso não esperado; cálculo de vencimento troca `ZoneId.systemDefault()` por `ZoneOffset.UTC`
  explícito; `domain/model` ganha um tipo para a regra "vencimento = inclusão + prazo", e a porta
  de processamento de expiração passa a receber `UUID` tipado em vez de `String` cru.
- **Fora de escopo, deliberadamente**: correlação via MDC/`traceId` nos entrypoints de mensageria
  e logging estruturado JSON — dívidas que atravessam as 5 apps e merecem uma change própria de
  padronização de frota, não uma correção pontual por app dentro desta change.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `layout-hexagonal-classico`: dois requisitos novos, generalizáveis às apps migradas — (1) o
  modelo de domínio expressa suas próprias transições de estado como comportamento (Tell, Don't
  Ask), em vez de `application` mutar campos do modelo diretamente; (2) um DTO de resposta
  (HTTP ou mensageria) NÃO SHALL embutir um tipo de `domain/model` diretamente — todo campo
  exposto é mapeado explicitamente para um tipo próprio da borda.
- `higiene-codigo-morto`: escopo de "métodos sem chamador de produção são removidos" passa a
  cobrir explicitamente os métodos identificados nesta auditoria em `contratocommand`
  (`SpringDataAutorizacaoRepository.findByStatus`/`findByIdAutorizacao`,
  `IdContaUUIDPartitionDistributor.getPartitionPrecision`,
  `ControleExpurgoAutorizacao.obterParticaoExpurgoDrop`) e em `autorizacaostatus-producer`
  (`StatusAutorizacao.TRANSICOES`/`podeTransicionarPara`, sem uso fora de teste nessa app-ponte).

## Impact

- Código afetado: `apps/contratocommand`, `apps/contratoquery`, `apps/autorizacaostatus-producer`,
  `apps/eventos-consumer`, `apps/temporiza-autorizacao` — camadas `domain`, `application` e
  `infrastructure` de cada uma, conforme o inventário acima. Nenhuma rota HTTP, tópico, fila ou
  chave de configuração externa muda de nome ou de shape observável.
- Testes: cada correção preserva ou estende a suíte existente (nenhum teste é removido sem ser
  substituído pelo equivalente do novo desenho); apps com teste de integração real
  (`contratocommand` exige PostgreSQL local, `autorizacaostatus-producer`/`temporiza-autorizacao`
  exigem Floci/Kafka/Valkey) precisam da infra correspondente no ar para fechar a tarefa.
- Documentação: `CLAUDE.md`/`AGENTS.md` de `contratocommand`, `autorizacaostatus-producer` e
  `contratoquery` recebem atualização pontual onde o achado aponta divergência entre o documento e
  o código (mantendo os dois arquivos de cada app idênticos, conforme `higiene-documentacao-repo`).
