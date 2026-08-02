---
name: refactoring-remove-parameter
description: 'Refactoring usando Remove Parameter em Java Language. Aplica o refactoring do catálogo do Fowler focado em remover parâmetros não usados/redundantes. Use quando um método tem parâmetro que pode ser inferido, ou ao revisar métodos longos com assinaturas inchadas. Uso: agent `refatorador-java` ou invocação manual via `/refactoring-remove-parameter`; não deve ser carregada proativamente pela sessão principal.'
---

# Refactoring Java: Remove Parameter

Aplique o refactoring **Remove Parameter** do catálogo do Fowler quando um parâmetro de método
está **não usado** ou é **redundante** (valor obtível de campo da classe, constante ou outra
chamada de método).

## Code Before / After

**Antes:**

```java
public Backend selectBackendForGroupCommit(long tableId, ConnectContext context, boolean isCloud)
        throws LoadException, DdlException {
    if (!Env.getCurrentEnv().isMaster()) {
        try {
            long backendId = new MasterOpExecutor(context)
                    .getGroupCommitLoadBeId(tableId, context.getCloudCluster(), isCloud);
            return Env.getCurrentSystemInfo().getBackend(backendId);
        } catch (Exception e) {
            throw new LoadException(e.getMessage());
        }
    } else {
        return Env.getCurrentSystemInfo()
                .getBackend(selectBackendForGroupCommitInternal(tableId, context.getCloudCluster(), isCloud));
    }
}
```

**Depois:**

```java
public Backend selectBackendForGroupCommit(long tableId, ConnectContext context)
        throws LoadException, DdlException {
    if (!Env.getCurrentEnv().isMaster()) {
        try {
            long backendId = new MasterOpExecutor(context)
                    .getGroupCommitLoadBeId(tableId, context.getCloudCluster());
            return Env.getCurrentSystemInfo().getBackend(backendId);
        } catch (Exception e) {
            throw new LoadException(e.getMessage());
        }
    } else {
        return Env.getCurrentSystemInfo()
                .getBackend(selectBackendForGroupCommitInternal(tableId, context.getCloudCluster()));
    }
}
```

`isCloud` foi removido: não era usado dentro do método (era apenas repassado às chamadas internas
que também não o utilizavam de fato).

## Quando aplicar

- **Parâmetro nunca referenciado** no corpo (verifique com Grep pelo nome, **incluindo** Javadoc,
  anotações, generics e tipos em assinatura).
- **Valor redundante com campo da classe** — o método pode usar o campo diretamente.
- **Valor constante** — sempre passado com o mesmo valor; vira constante.

## Quando NÃO aplicar

- **Parâmetro de interface/override** — exige remover também da interface e de **todos** os
  callers, custo alto; pondere.
- **Parâmetro reservado para extensão futura** — abstração especulativa, YAGNI; só remova com
  certeza de que não será usado (ver `qualidade-codigo-java`, seção YAGNI).
- **Parâmetro de callback/API pública** — parte de um contrato; remover quebra o cliente.

## Task — passos internos

Realize internamente, sem expor esses passos no output:

1. Identifique, em cada método, parâmetros não usados ou redundantes (valor obtível de campo da
   classe, constante ou outra chamada de método).
2. Remova o parâmetro da assinatura **e de todas as chamadas internas** que o recebem.
3. Garanta que o método continua funcionando após a remoção.
4. Output **apenas o código refatorado** em um único bloco `java`.
5. Não remova nenhuma funcionalidade do método original.
6. Inclua um comentário de uma linha acima de cada método modificado, indicando o parâmetro
   removido e por quê.

## Validação

Depois de aplicar o refactoring:

- Rode o build (`mvn clean compile`) — confirmação mecânica de que nada quebrou.
- Rode a suite de testes daquele módulo — se existirem.
- Peça revisão ao agent `java-revisor` com o diff, referenciando esta skill.

## Quem aplica o quê

| Situação | Quem | Skill |
|---|---|---|
| Aplicar Remove Parameter em um método específico | session principal | esta skill |
| Revisar diff pós-Remove Parameter | agent `java-revisor` | `revisao-de-codigo-java` |
| Decidir entre Remove Parameter, Extract Method, ou Extract Class | session principal | `qualidade-codigo-java` |
