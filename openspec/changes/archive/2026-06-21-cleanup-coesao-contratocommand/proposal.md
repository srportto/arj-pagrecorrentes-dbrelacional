## Why

A aplicação `arj-contratocommand` carrega duplicação estrutural e incoerências que dificultam manutenção, testes e seu uso como base para outras aplicações. O eixo "produto" (PIX_AUTO / DDA_AUTO) está duplicado em vez de abstraído: `Mapper`, `Repository`, `UseCases` e `Service` de cada produto são cópia-carbono, embora ambos os produtos escrevam na mesma entidade/tabela e a única variação real (valor-limite) já viva nas `Rules`. Soma-se a isso um conjunto de defeitos latentes (transação ineficaz no cancelamento, exceção de framework vazada, status semanticamente ambíguo) e inconsistências de contrato (DTO mutável usado como "carteiro" de estado, record vs classe, sufixos de nome). Esta change é uma **limpeza e organização** que precede a futura funcionalidade de **alteração de contrato**, deixando a base enxuta, coesa e simétrica para receber essa nova operação.

## What Changes

- **Corrige defeitos latentes (sem mudar o contrato REST):**
  - `@Transactional` passa para o método público `execute()` dos use cases de cancelamento (hoje está em método privado/auto-invocado, onde o Spring o ignora — o `delete+insert` entre partições roda sem transação).
  - Substitui `org.springframework.context.ApplicationContextException` por `ApplicationException` (500) do próprio projeto.
  - Reconcilia o status numérico mágico (`1` gravado como "ATIVO") com o enum `StatusAutorizacao` (`1 = RECEBIDA`, `4 = ATIVA`), passando a usar o enum como fonte da verdade.
  - Corrige `String.format("...%i...")` em `MotivoStatusAutorizacao` e o nome de coluna com espaço (`indicador_tipo_mensageria `).
- **Normaliza contratos/DTOs:**
  - `CancelarAutorizacaoRequestDto` vira **record imutável**; campos que não são dados de request (`tipoProdutoDoIdAutorizacao`, `produtoHeaderRequest`, `idAutorizacao`) deixam de ser mutados no DTO e passam a ser **parâmetros/contexto explícitos** no fluxo de validação.
  - `AutorizacaoCompletaResponseDto` deixa de fazer mapeamento manual com `ObjectMapper` interno e para de expor a entidade de domínio `Cancelamento`; padroniza sufixos e o tipo de `tipoProduto`.
- **Elimina a duplicação Pix/DDA (decisão B1 — manter o seam Strategy fino):** unifica `Mapper`, `Repository` e `UseCases` compartilhados; `PixAutoService`/`DdaAutoService` ficam finos (declaram o `TipoProduto` suportado e delegam). Adicionar um produto novo passa a custar ~10 linhas em vez de copiar 5 arquivos.
- **Limpa o domínio:** `Autorizacao.inicializaCriacao()` deixa de receber `this` como parâmetro; `@JoinColumn` em embeddables vira `@Column`; remove/utiliza `ContratoBase` (dead code); corrige comentários enganosos.
- **NÃO-BREAKING:** os três contratos REST públicos (`POST /api/autorizacoes`, `PATCH /api/autorizacoes/{id}/cancelar`, health-check) permanecem com o mesmo corpo e cabeçalhos. A única mudança observável intencional é o valor de `status` na resposta passar a refletir o enum.

## Capabilities

### New Capabilities
- `coesao-contratocommand`: invariantes de coesão e organização da aplicação contratocommand que o refactor deve satisfazer e que trabalhos futuros (incl. alteração de contrato) devem manter — produto sem duplicação via Strategy fino, DTOs imutáveis sem estado-carteiro, transação nos use cases, status via enum, e ausência de dead code/anotações incorretas.

### Modified Capabilities
<!-- Nenhuma. Os contratos REST e comportamentos descritos em specs existentes (validacao-header-jornada, motivo-status-por-jornada, etc.) são preservados; o refactor é validado por teste de regressão (mvn test verde a cada fase). -->

## Impact

- **Módulo:** `aplicacoes/arj-contratocommand` (somente). Sem mudança no `arj-contratoquery` — a verificar que ele não importa classes de `enabledproduct` antes da fase de fusão.
- **Camadas afetadas:** `entrypoint/contratosrest` (DTOs), `application/enabledproduct/{pixauto,ddaauto}` (fusão), `application/defaultservice/cancelamento` (validação por parâmetro), `domain/entities` e `domain/enums` (status/embeddables/inicialização).
- **Testes:** consolidação dos testes duplicados de Pix/DDA em testes compartilhados + testes finos de strategy; cobertura preservada. Gate: `mvn test` verde ao fim de cada fase.
- **Dependências externas:** nenhuma nova. Mantém Spring Boot, MapStruct, Lombok, PostgreSQL.
