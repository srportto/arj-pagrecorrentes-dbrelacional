# Breaking change — `GET /api/autorizacoes` mais estrito: 500/400 → 422 em borda

**Aplicação**: `contratoquery` (porta 8081)
**Mudança**: o endpoint de listagem paginada passa a **rejeitar requisições fora da borda com 422** em vez de devolver 500 (detalhe vazado) ou aceitar valores abusivos.
**Quando**: a partir do merge desta change (`blindar-superficie-leitura`).

## O que muda

| Cenário | Antes | Depois |
|---|---|---|
| `GET /api/autorizacoes` **sem** `idUnicoContaContratante` | 400 (Spring: parâmetro obrigatório no binding) | **422** (`BusinessException`: "idUnicoContaContratante é obrigatório") |
| `GET /api/autorizacoes?tamanho=999999` (acima do teto de 100) | 200 (varredura sem limite) | **422** (`BusinessException`: "tamanho deve estar entre 1 e 100") |
| `GET /api/autorizacoes?pagina=-1` | 500 (fall-through do Spring para `PageRequest.of`) | **422** (`BusinessException`: "pagina deve ser maior ou igual a 0") |
| `GET /api/autorizacoes?tamanho=0` ou `tamanho=-5` | 500 | **422** (`BusinessException`: "tamanho deve estar entre 1 e 100") |
| `GET /api/autorizacoes?ordenarPor=campoQueNaoExiste` | 500 (`PropertyReferenceException` do Hibernate, com nome de campo interno na resposta) | **422** (`BusinessException` listando os campos aceitos) |

> **Teto de `tamanho` = 100** é o ganho de **proteção DoS** — sem ele, um cliente com `?tamanho=999999` dispara varredura completa das ~989 partições da tabela `autorizacoes` particionada, derrubando a performance para todos os outros clientes.

A mudança é **unidirecional** para todos os 5 cenários — clientes que hoje confiam em 200 (no caso do `tamanho` grande) ou 500 (nos casos de borda inválida) passam a ver 422.

## Por que muda

1. **Proteção DoS / desempenho** — sem teto de `tamanho`, qualquer cliente pode solicitar um payload arbitrariamente grande e degradar a performance para todos os outros. O teto de 100 foi escolhido como um limite generoso para paginação de UI, mas restritivo o suficiente para impedir varredura abusiva.
2. **Sanitização de erros** — os 500 anteriores (`PropertyReferenceException`, `PageRequest.of` com valor inválido) vazavam **detalhe de implementação** (nome de campo JPA, tipo de Spring exception) na resposta. 422 com mensagem genérica é semanticamente correto (regra de negócio violada) e seguro.
3. **Coerência com o `contratocommand`** — a outra API do monorepo já usa 422 para validação de borda; este `contratoquery` agora segue o mesmo padrão.

## Como o cliente deve tratar a nova resposta

### Antes (código legado que tratava 500 como "erro genérico")

```java
if (response.status >= 500) {
    log.error("falha inesperada", response.body());
    return Collections.emptyList();
}
```

### Depois (código novo — trata 422 como "requisição mal formada")

```java
if (response.status == 422) {
    // "borda inválida" — a mensagem do corpo diz qual campo está errado
    var body = response.body();
    return body.mensagens().get(0); // ex.: "tamanho deve estar entre 1 e 100"
}
// 500 é agora genuinamente "erro inesperado do servidor" — alerta, não silencie
```

A resposta 422 segue o envelope `LayoutErrosApiResponse` padrão do monorepo. O campo `mensagens` traz a string específica do problema (ex.: "tamanho deve estar entre 1 e 100").

## Compatibilidade

Esta é uma **mudança de comportamento observável** (status codes diferentes) com **duas faces**:

1. **Ganho de segurança**: clientes que abusavam de `?tamanho=999999` agora recebem 422 e param.
2. **Quebra de contrato**: clientes que tratavam 500 como "pode ter sido borda inválida, ignora" agora precisam diferenciar 422 (borda) de 500 (erro real).

Recomendações:

1. **Antes do próximo deploy** desta versão, comunicar internamente aos integradores:
   - "A partir de {data}, `GET /api/autorizacoes` rejeita borda inválida com 422 em vez de 500."
   - "Teto de `tamanho` agora é 100 (era ilimitado). Clientes que paginam em blocos maiores precisam revisar a estratégia."
   - "Recomendação: integrar 422 como 'requisição mal formada' (corrigir params e tentar de novo), em vez de tratar como falha transitória."
2. **Janela de convivência**: não há — a mudança é integral. Para evitar surpresa, o time de integrações deve ser notificado com pelo menos **1 sprint** de antecedência.
3. **Rollback** (se necessário): os 4 validadores de borda vivem no controller e no `ListarAutorizacoesService` do `contratoquery`. Reverter significa reabilitar o binding obrigatório de `idUnicoContaContratante`, remover o teto de `tamanho` e o whitelist de `ordenarPor`. Não é trivial — avalie antes.

## Detalhes de implementação (referência)

- **Teto de `tamanho`**: 100. Definido em `apps/contratoquery/application/autorizacao/ListarAutorizacoesService.java` (constante ou `@Value`).
- **Whitelist de `ordenarPor`**: implementada em `ListarAutorizacoesService.mapearCampoDTO(...)`.
- **Handler genérico de exceção**: `@ExceptionHandler(Exception.class)` no `ApiExceptionHandler` do `contratoquery` (3.1 da change).
- **Mensagem genérica no 500**: aplicada também ao catch-all do `contratocommand` (3.5b — achado em revisão cruzada).

## Referências

- Specs da change: `desempenho-consulta-autorizacoes`, `limites-consulta-autorizacoes`, `listar-autorizacoes`, `tratamento-erro-nao-mapeado` em `openspec/changes/blindar-superficie-leitura/specs/`
- Documentação de apoio: seção "Validações e códigos de erro" em `apps/contratoquery/{README.md,CLAUDE.md,AGENTS.md}`
