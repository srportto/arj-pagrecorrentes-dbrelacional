---
name: remove-unused-imports
description: Remove imports/usings não utilizados em Java, TypeScript, JavaScript, C#, Python, Go, Kotlin e outras linguagens. Use ao limpar imports, organizar imports, remover imports não usados, ou antes de um commit.
---

# Remover Imports Não Utilizados

Remove com segurança imports (`import` / `using`) que não são referenciados no
código. **Abordagem híbrida:** sempre tente a ferramenta nativa da linguagem
primeiro; só caia para análise manual se nenhuma estiver disponível. Sempre
verifique (build/teste) ao final.

## Regra de ouro

**Nunca remova um import por suposição.** Só remova se o símbolo importado não
aparecer em nenhum lugar do arquivo — incluindo código, anotações/decorators,
generics, comentários de documentação (Javadoc `@see`/`@link`, XML doc `<see>`)
e tipos usados apenas em assinaturas. Na dúvida, **não remova**.

## Fluxo (4 passos)

### 1. Detectar linguagem e escopo
Identifique a(s) linguagem(ns) pelos arquivos alvo (`.java`, `.ts`/`.tsx`,
`.js`/`.jsx`, `.cs`, `.py`, `.go`, `.kt`). Trabalhe apenas nos arquivos ou
diretório que o usuário indicou. Se ele não especificar, pergunte o escopo.

### 2. Tentar a ferramenta nativa primeiro

| Linguagem | Ferramenta preferida | Comando |
|---|---|---|
| **Java** | google-java-format | `google-java-format --replace <arquivos>` |
| Java | Spotless (Maven) | `mvn spotless:apply` |
| Java | Spotless (Gradle) | `./gradlew spotlessApply` |
| **TypeScript/JavaScript** | ESLint + `eslint-plugin-unused-imports` | `npx eslint --fix <arquivos>` |
| TS/JS | Biome | `npx biome check --write <arquivos>` |
| TS/JS | detecção apenas | `npx tsc --noUnusedLocals --noEmit` |
| **C#** | dotnet format | `dotnet format style --diagnostics IDE0005` |
| C# | detecção apenas | habilite `<EnforceCodeStyleInBuild>true` + `dotnet build` (IDE0005) |
| **Python** | Ruff | `ruff check --select F401 --fix <arquivos>` |
| Python | autoflake | `autoflake --remove-all-unused-imports --in-place <arquivos>` |
| **Go** | goimports | `goimports -w <arquivos>` (imports não usados são erro de compilação em Go) |
| **Kotlin** | ktlint | `ktlint --format <arquivos>` |

Verifique se a ferramenta existe **antes** de usar (`which`/`npx --no-install`,
ou presença em `package.json`/`pom.xml`/`build.gradle`/`.csproj`). Se a
ferramenta já estiver configurada no projeto, prefira-a — ela respeita o estilo
do repositório.

### 3. Fallback: análise manual (se nenhuma ferramenta existir)

Para cada arquivo:
1. Liste os imports do topo.
2. Para cada símbolo importado, busque seu uso no restante do arquivo (use Grep
   pelo nome do símbolo, não pela linha de import).
3. Remova apenas os que não têm nenhuma referência.

Cuidados por linguagem (veja **Armadilhas** abaixo). Nunca toque em imports com
efeito colateral, wildcards, ou usados só em tipos/anotações sem que você
confirme o não uso.

### 4. Verificar (obrigatório)

Após remover, rode a build/teste apropriada e confirme que nada quebrou:

| Linguagem | Verificação |
|---|---|
| Java (Maven) | `mvn -q compile` (ou `mvn test` se mexeu em testes) |
| Java (Gradle) | `./gradlew compileJava` |
| TS/JS | `npx tsc --noEmit` e/ou o build do projeto |
| C# | `dotnet build` |
| Python | `python -m py_compile <arquivos>` ou a suíte de testes |
| Go | `go build ./...` |
| Kotlin | `./gradlew compileKotlin` |

Se a verificação falhar por causa de um import removido, **reverta esse import**
e relate. Ao final, liste objetivamente quais imports foram removidos de quais
arquivos.

## Armadilhas (não remova nestes casos sem confirmar)

- **Imports com efeito colateral** — JS/TS `import './styles.css'`,
  `import 'reflect-metadata'`; Python `import logging.config`. Não têm símbolo
  referenciado mas são necessários.
- **Wildcards** — Java `import x.*`, Kotlin `import x.*`, Python `from x import *`.
  Não dá pra saber o uso por nome; só remova se tiver certeza absoluta.
- **C# extension methods** — um `using` pode existir só para um método de
  extensão (`.Where(...)`, `.ToList()`); o namespace não aparece explicitamente.
- **C# `global using`** — afeta o projeto inteiro; verifique uso em todos os
  arquivos, não só no atual.
- **TS types-only** — símbolo usado apenas em anotação de tipo ou generic
  (`const x: Foo`); ainda é uso. Cuidado também com decorators (`@Component`).
- **Java anotações e Javadoc** — símbolo usado só em `@Anotacao` ou em
  `{@link Classe}`/`@see` no Javadoc conta como uso.
- **Reflexão / strings** — símbolos referenciados por nome em string
  (reflection, DI) não aparecem como uso direto; não remova.

## Checklist antes de concluir

- [ ] Ferramenta nativa tentada primeiro (ou confirmado que não existe)
- [ ] Nenhum import com efeito colateral / wildcard removido por engano
- [ ] Build/teste da linguagem rodou e passou
- [ ] Relato final do que foi removido em cada arquivo
