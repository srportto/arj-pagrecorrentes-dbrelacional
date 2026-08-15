# Breaking change — POST `/api/autorizacoes` duplicado: duplicação silenciosa → 409

**Aplicação**: `contratocommand` (porta 8080)
**Mudança**: o segundo POST com o mesmo `id_autorizacao_empresa` deixa de **criar silenciosamente uma segunda autorização ativa** e passa a retornar **409 Conflict** com a `idAutorizacao` da linha já persistida.
**Quando**: a partir do merge desta change (`integridade-fluxo-escrita`).

## O que muda

| Cenário | Antes | Depois |
|---|---|---|
| POST com `id_autorizacao_empresa` **novo** | 201 Created | 201 Created (sem mudança) |
| POST com `id_autorizacao_empresa` **já existente** | **201 Created** — uma **segunda autorização ativa** era criada silenciosamente para o mesmo contrato, com risco de débito duplicado | **409 Conflict** (`RecursoJaExisteException` — mensagem: "Autorização com id_autorizacao_empresa já existe: …") — nenhuma linha adicional é criada; a resposta carrega o `idAutorizacao` da linha já persistida |

> **Por que isso importa mais do que parece:** a versão anterior **não rejeitava** o POST — criava uma segunda linha. Para integração com retry após timeout (rotina em integração bancária), isso significava que um único contrato podia ter duas autorizações ativas simultâneas, expondo o sistema a débito duplicado. Esta mudança **fecha essa porta** ao preço de um status HTTP novo (409) que clientes precisam aprender a tratar.

A mudança é **unidirecional e não negociável**: clientes que hoje confiam no "sempre 201" precisam migrar para o caminho "201 OU 409".

## Por que muda

1. **Risco financeiro real (autorização duplicada por retry).** `id_autorizacao_empresa` é a chave de negócio vinda do sistema da empresa; a versão anterior não tinha constraint UNIQUE nem checagem na aplicação, então um retry de POST após timeout de rede criava uma **segunda autorização ativa** para o mesmo contrato. Esta change adiciona constraint `UNIQUE (id_particao_conta, id_autorizacao_empresa)` no banco, mais checagem de existência no `CriarAutorizacaoUseCase`.
2. **Idempotência explícita e observável.** O cliente agora recebe o id já criado na resposta 409 e pode devolvê-lo ao chamador final sem precisar de uma consulta adicional.
3. **Coerência com a constraint do banco.** O `DataIntegrityViolationException` que escapa da checagem da aplicação em condições de corrida (constraint do Postgres) também é mapeado para 409, com o mesmo formato de resposta.

## Como o cliente deve tratar a nova resposta

### Antes (código legado)

```java
// Código que confiava no 201 sempre — não tratava duplicação,
// porque o servidor criava silenciosamente uma segunda autorização
if (response.status == 201) {
    var idCriado = response.body().idAutorizacao();
    return idCriado; // pode ser uma das duas autorizações criadas
}
```

### Depois (código novo)

```java
if (response.status == 409) {
    // "duplicado" — o id já vem na resposta; não há linha adicional
    var body = response.body();
    return body.idAutorizacao();
}
// 201 normal segue como antes
```

A resposta 409 segue o mesmo envelope `LayoutErrosApiResponse` dos demais erros. Como o "antes" não rejeitava, clientes que **nem olham o status** (só consomem `idAutorizacao` do 201) podem **não perceber a mudança** — o que é um problema em si, porque significa que têm duas autorizações ativas no banco e não sabem.

## Compatibilidade

Esta é uma **mudança de comportamento observável** com **duas faces**:

1. **Mudança de status code**: clientes que tratam 422 para "duplicado" precisam migrar para 409.
2. **Mudança funcional**: clientes que confiavam no "sempre 201" e nunca trataram 422 passam a ver 409 no caminho de duplicação — e **passam a ter uma única autorização ativa** em vez de duas. Esta é a face que mais importa.

Recomendações:

1. **Antes do próximo deploy** desta versão, comunicar internamente aos integradores:
   - "A partir de {data}, POST com `id_autorizacao_empresa` duplicado em `/api/autorizacoes` retorna 409 em vez de criar uma segunda autorização silenciosamente."
   - "Ação recomendada: tratar 409 como 'id já existe, devolver o que veio na resposta'."
   - "Auditoria recomendada: verificar se há autorizações duplicadas no banco de produção para o mesmo `id_autorizacao_empresa` antes do cutover (consulta na seção 1.1 do `design.md`/`tasks.md` da change)."
2. **Janela de convivência**: não há — a mudança é integral. Para evitar surpresa, o time de integrações deve ser notificado com pelo menos **1 sprint** de antecedência.
3. **Rollback** (se necessário): o `RecursoJaExisteException` é uma classe dedicada criada nesta change; rollback exige reverter para `BusinessException` (ou simplesmente remover a checagem no `CriarAutorizacaoUseCase` e dropar a constraint), ajustando o `ApiExceptionHandler` em consequência. Não é trivial — **avalie muito bem** antes, porque reverter significa voltar à duplicação silenciosa.

## Referências

- Spec: `openspec/changes/integridade-fluxo-escrita/specs/idempotencia-criacao-autorizacao/spec.md`
- Decisão D3 do `design.md`: 409 vs 422 para chave duplicada
- Implementação: `RecursoJaExisteException` em `shared/exceptions/` (mapeada no `ApiExceptionHandler`)
- Documentação de apoio: seção **"Códigos de erro (handler global)"** no `apps/contratocommand/README.md`, `CLAUDE.md` e `AGENTS.md`
