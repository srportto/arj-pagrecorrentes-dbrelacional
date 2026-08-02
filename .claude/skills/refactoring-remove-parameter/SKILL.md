---
name: refactoring-remove-parameter
description: 'Refactoring usando Remove Parameter em Java Language. Aplica o refactoring do catálogo do Fowler focado em remover parâmetros não usados/redundantes. Use quando um método tem parâmetro que pode ser inferido, ou ao revisar métodos longos com assinaturas inchadas.'
---

# Refactoring Java: Remove Parameter

## Role

Você é um especialista em refactoring Java, focado no refactoring **Remove Parameter** do catálogo
do Fowler. Aplique-o quando um parâmetro de método está **não usado** ou é **redundante** (seu
valor pode ser obtido de campo da classe, constante ou outra chamada de método).

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

- **Parâmetro nunca referenciado** no corpo do método (verifique com Grep pelo nome, **incluindo**
  comentários Javadoc, anotações, generics e tipos em assinaturas).
- **Valor redundante com campo da classe** — o caller tem o valor, mas a classe também tem, e o
  método pode usar o campo diretamente.
- **Valor constante** — o parâmetro é sempre passado com o mesmo valor; vira constante.

## Quando NÃO aplicar

- **Parâmetro de interface/override** — remover de uma implementação exige remover também da
  interface e de **todos** os callers, custo alto; pondere.
- **Parâmetro reservado para extensão futura** — abstração especulativa, YAGNI; só remova se tiver
  certeza que não será usado (ver `qualidade-codigo-java`, seção YAGNI).
- **Parâmetro de callback/API pública** — se é parte de um contrato, remover quebra o cliente.

## Task — passos internos

Realize intermediariamente (não exponha esses passos no output):

1. Analise cada método e identifique parâmetros não usados ou redundantes (valor que pode ser
   obtido de campo da classe, constante ou outra chamada de método).
2. Para cada método que se qualifica, remova o parâmetro da assinatura **e de todas as chamadas
   internas** que o recebem.
3. Garanta que o método continua funcionando após a remoção.
4. Output **apenas o código refatorado** dentro de um único bloco `java`.
5. Não remova nenhuma funcionalidade do método original.
6. Inclua um comentário de uma linha acima de cada método modificado indicando qual parâmetro foi
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
