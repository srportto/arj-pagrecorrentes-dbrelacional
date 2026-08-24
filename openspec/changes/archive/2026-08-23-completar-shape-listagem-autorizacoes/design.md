## Context

`AutorizacaoResumidaResponseDto` (`contratoquery`, `GET /api/autorizacoes`) já carrega
`motivoStatus` no código, mas a spec `listar-autorizacoes` nunca o listou. `tipoProduto` está
disponível no domínio (`Autorizacao.getTipoProduto()`) desde sempre — o mesmo getter já é usado
por `AutorizacaoDetalheResponseDto.from()` — mas nunca foi lido pelo `.from()` do DTO resumido.
Não há entidade JPA, mapper ou coluna de banco para tocar: os dois campos já atravessam
`AutorizacaoJpaEntity` → `AutorizacaoPersistenceMapper` → `Autorizacao` (domínio) hoje. O gap é
só no último passo, a montagem do DTO de listagem.

Este documento existe mais para registrar a rastreabilidade da decisão (a lacuna vem da change
`2026-06-09-mover-listagem-autorizacoes-para-contratoquery`) do que por complexidade técnica —
não há ambiguidade de implementação aqui.

## Goals / Non-Goals

**Goals:**
- Fechar a divergência entre o DTO real de listagem e a spec `listar-autorizacoes`.
- Expor `tipoProduto` na listagem para o caso de uso concreto do frontend (badge de produto sem
  N chamadas de detalhe).
- Deixar `motivoStatus` formalmente especificado, já que o código já o expõe.

**Non-Goals:**
- Não reabre a dívida aceita de nomes de campo / formato de `status` entre `contratocommand` e
  `contratoquery` (D2 de `reconciliar-contrato-spec-doc`) — fora de escopo, sem gatilho novo.
- Não adiciona filtro por `tipoProduto` nos query params de `GET /api/autorizacoes` — só o campo
  de saída. Se surgir necessidade de filtrar, é uma capability nova, não parte desta change.
- Não expõe `tipoJornada` na listagem — essa é uma decisão de contrato em aberto e distinta (ver
  `CLAUDE.md` do `contratoquery`, seção "Coluna tipo_jornada"), não tratada aqui.
- Não versiona a API (`/v2`) nem introduz negociação de conteúdo — adição de campo é
  retrocompatível por natureza em JSON.

## Decisions

**D1 — Adicionar campos diretamente ao DTO existente, sem versionar o endpoint.**
Alternativa descartada: criar um `AutorizacaoResumidaResponseDtoV2` ou um novo endpoint. Rejeitada
porque a mudança é puramente aditiva (nenhum campo removido/renomeado) e o repositório não tem
convenção de versionamento de API neste momento (nenhum outro endpoint versiona). Adicionar campo
a um record Java não quebra clientes JSON existentes.

**D2 — `tipoProduto` no DTO usa o mesmo enum `TipoProduto` do DTO de detalhe, sem conversão.**
Alternativa descartada: expor como `String` (nome do enum) para simetria com o campo `status`
(que já é `String` por convenção). Rejeitada porque o DTO de detalhe já expõe `tipoProduto` como
o enum tipado diretamente (serializado pelo Jackson como string), e não há relato de problema com
esse formato — manter os dois DTOs consistentes entre si evita um terceiro formato no mesmo
serviço.

**D3 — Corrigir a spec e o doc de gateway na mesma change, não como follow-up.**
Alternativa descartada: só mudar o código e abrir uma change de documentação depois. Rejeitada
porque este repositório trata a divergência spec/código como o problema central que
`reconciliar-contrato-spec-doc` já existiu para resolver uma vez — deixar a spec desatualizada de
novo, mesmo que temporariamente, recria o mesmo tipo de dívida.

## Risks / Trade-offs

- **Payload da listagem cresce ligeiramente por item** → irrelevante: dois campos escalares
  (enum + string), sem impacto de paginação (máx. 100 itens por página).
- **Cliente atual da listagem pode ter parser estrito que rejeita campos desconhecidos** →
  mitigação: nenhuma ação necessária além de comunicar a mudança; é o risco padrão de qualquer
  adição de campo aditiva e não há indício de parser estrito nos consumidores conhecidos
  (`docs/contrato-api-para-gateway.md` é o único consumidor documentado, e ele é o insumo que
  esta mesma change atualiza).
- **`docs/contrato-api-para-gateway.md` tem prazo de validade** (nota no topo do arquivo) →
  mitigação: atualizamos mesmo assim, porque o arquivo ainda é a única fonte usada para montar o
  gateway hoje; quando o gateway assumir o contrato via OpenAPI, o arquivo é removido por inteiro
  (fora de escopo desta change).

## Migration Plan

Sem migração de dado ou de schema — mudança é só na camada de apresentação (DTO) do
`contratoquery`, serviço somente leitura. Deploy é o ciclo normal de release do `contratoquery`;
nenhuma coordenação com `contratocommand` ou com o schema do Postgres é necessária. Rollback é
reverter o deploy da imagem anterior, sem efeito colateral de dado.

## Open Questions

Nenhuma pendente para esta change.
