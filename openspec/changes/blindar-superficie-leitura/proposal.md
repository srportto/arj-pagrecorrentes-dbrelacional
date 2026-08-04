## Why

A auditoria multi-agente de 2026-08-04 encontrou quatro lacunas no `arj-contratoquery` que,
somadas, tornam a superfície de leitura derrubável por uma única requisição — e que foram
apontadas de forma independente pelos agentes de contrato REST, de persistência e de revisão do
próprio app:

- **Paginação sem teto.** Não há `@Max` no parâmetro `tamanho`, nem
  `spring.data.web.pageable.max-page-size`, nem clamp defensivo. `PageRequest.of(pagina, tamanho, sort)`
  aceita qualquer valor.
- **Consulta sem índice.** A tabela é particionada por `id_particao_conta`, mas toda listagem
  filtra por `id_unico_conta_contratante` — coluna diferente, sem índice, assim como `status` e as
  colunas de ordenação. Cada `GET /api/autorizacoes` varre as partições.
- **Ordenação sem whitelist.** O `default` de `mapearCampoDTO` repassa qualquer string de
  `ordenarPor` direto ao `Sort.by`, produzindo `PropertyReferenceException` em runtime para campo
  inexistente.
- **Sem catch-all no handler.** O `ApiExceptionHandler` do query não tem
  `@ExceptionHandler(Exception.class)` — ao contrário do command, que tem. Qualquer exceção não
  prevista escapa do contrato `LayoutErrosApiResponse` e vira 500 do container, com detalhe
  interno do Spring na resposta.

Combinadas: `?tamanho=999999` dispara uma varredura sem limite; `?pagina=-1` retorna 500 com
detalhe de implementação. Como nenhum dos serviços tem camada de autenticação, a composição é
explorável por qualquer cliente com acesso de rede à porta 8081.

Há ainda um drift direto entre spec e código: `listar-autorizacoes` exige HTTP 422 quando
`idUnicoContaContratante` é omitido, mas o parâmetro é declarado obrigatório no controller, então
o Spring rejeita o bind antes da aplicação e devolve 400 genérico do framework. A verificação
`if (idUnicoContaContratante == null)` no service é código inalcançável — o cenário especificado
nunca acontece.

## What Changes

- Tornar `idUnicoContaContratante` opcional no binding (`required = false`), de modo que a
  validação do service seja alcançada e produza o 422 com `LayoutErrosApiResponse` que a spec já
  determina — corrigindo simultaneamente o drift e o código morto.
- Impor limite máximo ao parâmetro `tamanho`, rejeitando valores acima do teto com erro de
  contrato em vez de executar a consulta.
- Rejeitar `pagina` negativa e `tamanho` não positivo com erro de contrato, em vez de deixar
  `IllegalArgumentException` do Spring Data escapar como 500.
- Trocar o `default` permissivo de `mapearCampoDTO` por rejeição explícita: campo de ordenação
  fora da lista conhecida passa a gerar erro de negócio, nunca alcançando o `Sort.by`.
- Adicionar `@ExceptionHandler(Exception.class)` ao `ApiExceptionHandler` do `arj-contratoquery`,
  mapeando exceção não prevista para 500 com `LayoutErrosApiResponse` e sem expor detalhe interno.
- Criar índice composto cobrindo o filtro e a ordenação padrão da listagem
  (`id_unico_conta_contratante`, `status`, `data_hora_inclusao`), aplicado de forma compatível com
  a tabela particionada e sem bloquear escrita (`CONCURRENTLY`).
- Adicionar `@Transactional(readOnly = true)` nos services de leitura — hoje inexistente, o que
  deixa cada consulta abrir transação de leitura/escrita implícita, sem o hint que desliga
  dirty-checking.
- Reutilizar `ObjectMapper` estático em `AutorizacaoResumidaResponseDto`, que hoje instancia um por
  item da página, ao contrário do `AutorizacaoDetalheResponseDto` que já usa `static final`.
- **BREAKING (contrato):** requisições com `tamanho` acima do teto, `pagina` negativa ou
  `ordenarPor` de campo desconhecido passam a ser rejeitadas com erro de contrato. Clientes que
  hoje recebem resultado (ou 500) receberão 4xx estruturado.
- **Fora de escopo (deliberado):** autenticação/autorização dos serviços. É a lacuna que
  transforma esta composição em risco externo, mas é decisão de arquitetura própria — registrada
  na auditoria e adiada por decisão explícita.
- **Fora de escopo:** migração da paginação offset para cursor. O teto resolve o risco imediato; a
  troca de estratégia é decisão de contrato para um dataset que só cresce, e pertence à evolução
  da API.
- **Fora de escopo:** teste de integração do repositório com Postgres real (Testcontainers) para
  as queries JPQL. Gap real apontado pela auditoria, mas com escopo próprio.

## Capabilities

### New Capabilities

- `limites-consulta-autorizacoes`: os limites defensivos da superfície de leitura — teto de
  tamanho de página, validação de índice de página, e whitelist de campos de ordenação.
- `desempenho-consulta-autorizacoes`: a cobertura de índice exigida pelas consultas da listagem
  numa tabela particionada por coluna distinta da coluna de filtro.
- `tratamento-erro-nao-mapeado`: garantia de que nenhuma exceção escapa do contrato de erro da
  API, em nenhum dos serviços que expõem REST.

### Modified Capabilities

- `listar-autorizacoes`: o cenário "idUnicoContaContratante ausente resulta em erro de negócio
  (422)" é hoje inalcançável pela implementação. O requisito passa a exigir explicitamente que a
  validação ocorra na camada de aplicação, e não no binding do framework, além de incorporar os
  limites de paginação e ordenação.

## Impact

- **Código afetado (`arj-contratoquery`):** `entrypoint/AutorizacaoController.java`,
  `application/autorizacao/ListarAutorizacoesService.java`,
  `application/autorizacao/ConsultarAutorizacaoService.java`,
  `entrypoint/contratosrest/AutorizacaoResumidaResponseDto.java`,
  `shared/interceptors/api/ApiExceptionHandler.java`, `application.yaml`.
- **Banco:** nova migration de índice, aplicada com `CONCURRENTLY` e compatível com o
  particionamento — precisa considerar aplicação por partição via template.
- **Contrato de API:** novos caminhos de erro precisam de código de status, corpo e documentação
  no `README.md`.
- **Desempenho:** ganho esperado na listagem; a validação exige comparação de plano de execução
  antes e depois, já que não há baseline registrada no repositório.
- **`arj-contratocommand`:** apenas verificação de que seu `ApiExceptionHandler` já satisfaz o
  requisito de catch-all — não há mudança prevista.
