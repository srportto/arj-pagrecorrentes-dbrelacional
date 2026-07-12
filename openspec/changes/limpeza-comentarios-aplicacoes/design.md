## Context

A exploração (`/opsx:explore`) inventariou todos os comentários em `aplicacoes/{arj-contratocommand,arj-contratoquery}/src/main` (24 arquivos) e `src/test` (6 arquivos). Os comentários de teste são, sem exceção, do tipo WHY (explicam ordem de rules, causas de `ObjectDeletedException`, ressalvas de cobertura) e ficam fora do escopo — nenhum candidato a remoção ali. Nos 24 arquivos de produção, os comentários se dividem em três grupos: (1) WHY genuíno — decodifica valor de negócio não óbvio ou explica uma decisão de design, deve permanecer; (2) WHAT redundante — repete em português o que a linha seguinte já diz em código, remover; (3) stale/incorreto — descreve um estado do código que não existe mais (as "strategies" removidas, o `void main()` nunca ativado), corrigir.

O usuário decidiu, na exploração, que o bloco `void main()` comentado deve ser mantido como referência de migração futura, mas não como um bloco de 3 linhas de código morto dentro da classe — vira um `// TODO` de uma linha, rastreável por busca de texto.

## Goals / Non-Goals

**Goals:**
- Remover comentários puramente decorativos ou que só reafirmam a linha seguinte (WHAT redundante).
- Corrigir os dois comentários que hoje descrevem algo que não é mais verdade: o javadoc de `AutorizacaoRepository` (menciona "strategies") e o `CLAUDE.md`/`AGENTS.md` dos dois módulos (afirmam "usa `void main()`").
- Substituir o bloco `void main()` comentado por um `// TODO` de uma linha nos dois `*Application.java`.
- Remover as duas linhas de cálculo morto em `ControleExpurgoAutorizacao.obterParticaoExpurgoDrop()` (não fazem parte do valor retornado).
- Preservar 100% dos comentários que decodificam valor de negócio ou explicam uma decisão de design não óbvia.

**Non-Goals:**
- Não migra o entrypoint para `void main()` de fato — isso depende do maven plugin suportar Java 25, fora do controle desta change.
- Não altera nenhuma lógica executável além da remoção das 2 linhas mortas (que já são inalcançáveis, não afetam o valor retornado).
- Não toca `src/test` — os comentários lá já são todos WHY genuíno.
- Não toca nada fora de `aplicacoes/` (docs de raiz do monorepo, scripts, etc.) — escopo definido explicitamente pelo usuário.
- Não cria uma capability `documentacao-contratocommand` simétrica à `documentacao-contratoquery` — fora do escopo desta change; a correção do `CLAUDE.md` do contratocommand é tratada como parte da nova capability `higiene-comentarios-codigo`, não como uma capability de documentação completa.

## Decisions

### 1. `void main()` morto vira `// TODO` de uma linha, não é apagado nem mantido como bloco comentado

O usuário optou explicitamente por manter a referência (não apagar), mas rejeitou a forma atual (3 linhas de código Java comentado, parecendo um bloco "pronto para descomentar" mas na verdade não compilável nesse projeto sem trocar a versão do maven plugin). O formato escolhido:

```java
@SpringBootApplication
public class ContratocommandApplication {

	// TODO: migrar para `void main()` (Java 25) quando o maven plugin suportar.
	public static void main(String[] args) {
		SpringApplication.run(ContratocommandApplication.class, args);
	}

}
```

- **Alternativa considerada — apagar tudo, sem TODO**: mais limpo, mas perde o rastro da intenção de migração; rejeitada porque o usuário quer manter a referência.
- **Alternativa considerada — manter o bloco comentado como está**: rejeitada — código morto dentro do corpo da classe é fonte de confusão (parece testável/ativável, mas não é) e é exatamente o tipo de comentário que a nova capability proíbe.

### 2. Correção do `CLAUDE.md`/`AGENTS.md`: descrever a realidade atual, não a aspiração

A linha "usa `void main()` em vez de `public static void main()`" nos pré-requisitos dos dois módulos passa a: "usa `public static void main()` — a forma `void main()` do Java 25 está pendente de suporte do maven plugin (ver TODO no entrypoint)". Isso mantém a informação de que a migração é uma intenção conhecida, sem afirmar algo falso sobre o código atual.

- **Alternativa considerada — remover a linha inteira**: perde contexto útil (por que ainda não usa a feature mais nova do Java 25); rejeitada.

### 3. Nova capability `higiene-comentarios-codigo`, não uma modificação de spec existente

`coesao-contratocommand` já tem um requirement sobre "domínio sem dead code", mas é específico de `domain/` e sobre inicialização/anotações, não sobre comentários em geral nem sobre `application/`/`entrypoint/`. `documentacao-contratoquery` é especificamente sobre os arquivos `CLAUDE.md`/`AGENTS.md`/`README.md`, não sobre comentários inline no código Java. Nenhum dos dois specs existentes cobre "comentários de código devem ser WHY, não stale, não decorativos" como invariante — por isso esta change introduz uma capability nova, aplicável aos dois módulos (`arj-contratocommand` e `arj-contratoquery`) de uma vez, em vez de duplicar a mesma regra em dois deltas separados.

- **Alternativa considerada — modificar `coesao-contratocommand` (só cobre contratocommand) e criar uma capability irmã para contratoquery**: rejeitada por duplicar a mesma regra em dois lugares quando ela é, na prática, uma única convenção cross-module.

## Risks / Trade-offs

- **[Julgamento subjetivo sobre o que é "WHY genuíno" vs "WHAT redundante"]** → Mitigado: a lista de arquivos e comentários a remover/manter já foi enumerada item a item na exploração e está detalhada em `tasks.md`; nenhuma decisão de "remover ou manter" fica em aberto para a implementação.
- **[Remoção das 2 linhas de cálculo morto poderia, em teoria, esconder uma lógica que alguém pretendia reativar]** → Mitigado: são linhas comentadas (`//int particaoExpurgoMaxima = 999;` etc.), já inertes há tempo, sem relação com o `// TODO` de migração do `main()`; git preserva o histórico se precisar recuperar.
- **[`CLAUDE.md`/`AGENTS.md` ficarem dessincronizados entre si]** → Mitigado: ambos os arquivos recebem a mesma edição em cada módulo, como de praxe.

## Migration Plan

Mudança somente de comentários/documentação, sem alteração de comportamento (exceto 2 linhas de código morto já inalcançáveis). Deploy padrão; rollback = reverter o commit.

## Open Questions

Nenhuma — decisões fechadas na exploração, incluindo a escolha explícita do usuário sobre o tratamento do `void main()`.
