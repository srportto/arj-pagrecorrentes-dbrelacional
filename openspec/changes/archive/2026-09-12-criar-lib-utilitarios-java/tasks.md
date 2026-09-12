## 1. Scaffold do módulo `libs/srportto-commons-java`

- [x] 1.1 Criar `libs/srportto-commons-java/pom.xml` — Java puro, `groupId br.com.srportto`,
      `artifactId srportto-commons-java`, sem `<parent>` Spring Boot, sem dependência de escopo
      `compile` fora de `java.*` (JUnit só em `test`, D-Requirement "Módulo Java puro")
- [x] 1.2 Configurar `<distributionManagement>` apontando para o GitHub Packages do repositório
      (D5)
- [x] 1.3 Confirmar que `mvn clean compile` roda no módulo vazio antes de mover qualquer classe

## 2. Migração das 3 classes elegíveis (comportamento idêntico, D-Requirement "Nenhuma mudança de
   comportamento observável")

- [x] 2.1 Mover `ReversibleUUIDv7` para `br.com.srportto.commons.persistence.ReversibleUUIDv7`,
      preservando o algoritmo de `generate`/`extract` byte-a-byte
- [x] 2.2 Mover `BusinessException` para `br.com.srportto.commons.exception.BusinessException`,
      preservando construtores e mensagens
- [x] 2.3 Mover `ApplicationException` para
      `br.com.srportto.commons.exception.ApplicationException`, preservando construtores e
      mensagens
- [x] 2.4 Trazer/mesclar os testes existentes das 3 classes (de `contratocommand` e/ou
      `contratoquery`, o que estiver mais completo) para `libs/srportto-commons-java`, ajustando
      só o pacote — só existia `ReversibleUUIDv7Test` (idêntico nas duas apps); não havia teste
      dedicado para `BusinessException`/`ApplicationException`
- [x] 2.5 Rodar `mvn -f libs/srportto-commons-java test` com suíte verde

## 3. Publicação no GitHub Packages

- [x] 3.1 Criar `.github/workflows/ci-publish-srportto-commons-java.yml`, disparado em push que
      altere `libs/srportto-commons-java/**` (mesmo padrão de gatilho por path da capacidade
      `ci-testes-unitarios`)
- [x] 3.2 Workflow executa `mvn deploy` usando `GITHUB_TOKEN` do próprio Actions (sem secret novo)
- [ ] 3.3 Publicar a versão `1.0.0` e confirmar no GitHub Packages do repositório que o artefato
      `br.com.srportto:srportto-commons-java:1.0.0` está disponível — **pendente**: requer push
      real ao GitHub (workflow criado e testado localmente com `mvn deploy -DskipTests` apontado
      para o repositório correto; a publicação efetiva só ocorre quando o commit chegar ao GitHub
      Actions)

## 4. Consumo em `contratocommand`

- [x] 4.1 Adicionar `<repositories>` (GitHub Packages) e `<dependency>` com versão fixa `1.0.0`
      ao `pom.xml` de `contratocommand`
- [x] 4.2 Remover `ReversibleUUIDv7.java`, `BusinessException.java`, `ApplicationException.java`
      locais e seus testes locais
- [x] 4.3 Atualizar todo `import` que referenciava
      `br.com.srportto.contratocommand.domain.exception.{BusinessException,ApplicationException}`
      e `br.com.srportto.contratocommand.infrastructure.persistence.ReversibleUUIDv7` para o
      pacote `br.com.srportto.commons.*`
- [x] 4.4 Configurar `settings.xml` com credencial de leitura (`GITHUB_TOKEN`) no workflow de CI
      de `contratocommand` (`ci-testesunitarios-contratocommand.yml`), antes do passo `mvn test`
- [x] 4.5 Rodar `mvn clean test` local (com a lib instalada localmente via `mvn install`, ver nota
      abaixo) e confirmar suíte verde, sem nenhuma mudança de mensagem de exceção ou status HTTP
      observável — 219 testes, 0 falhas

## 5. Consumo em `contratoquery`

- [x] 5.1 Repetir 4.1–4.4 para `contratoquery` (`ci-testesunitarios-contratoquery.yml`)
- [x] 5.2 Rodar `mvn clean test` local e confirmar suíte verde, sem mudança de comportamento
      observável (`OrdenacaoTest` e demais suítes recém-tocadas continuam passando) — 103 testes,
      0 falhas

## 6. Documentação

- [x] 6.1 Atualizar `CLAUDE.md`/`AGENTS.md` raiz ("Regras que atravessam os serviços") registrando
      a existência de `libs/srportto-commons-java`, o critério de elegibilidade de classe (3
      condições da spec) e que ele NÃO se estende a contratos/enums de máquina de estados
- [x] 6.2 Atualizar `CLAUDE.md`/`AGENTS.md` de `contratocommand` e `contratoquery` (espelhos —
      manter idênticos) com o novo pré-requisito de PAT pessoal (`read:packages`) para build local
- [x] 6.3 Criar `CLAUDE.md`/`AGENTS.md` mínimo em `libs/srportto-commons-java` documentando o
      propósito do módulo, o critério de elegibilidade e como publicar uma nova versão

## 7. Fechamento

- [x] 7.1 Rodar `mvn clean test` em `libs/srportto-commons-java`, `contratocommand` e
      `contratoquery` com suíte inteira verde nos três
- [ ] 7.2 Confirmar que os workflows de CI das duas apps resolvem a dependência num runner limpo
      (cache de `~/.m2` frio) — **pendente**: só pode ser confirmado após 3.3 (publicação real) e
      execução do workflow no GitHub Actions
- [x] 7.3 Confirmar que nenhuma das outras 3 apps (`autorizacaostatus-producer`,
      `eventos-consumer`, `temporiza-autorizacao`) foi tocada
- [ ] 7.4 Atualizar o grafo `graphify` do repositório para refletir o novo módulo e a remoção das
      cópias locais (se houver grafo gerado localmente) — **pendente**, ver nota no relatório final
- [x] 7.5 Rodar `openspec validate criar-lib-utilitarios-java --strict` e conferir que todo
      cenário do delta spec tem cobertura de teste correspondente — `openspec validate` passou
      ("Change 'criar-lib-utilitarios-java' is valid"); cobertura de teste: cenários de
      "Módulo Java puro" e "Nenhuma mudança de comportamento observável" cobertos por
      `ReversibleUUIDv7Test` + suíte completa das duas apps (219+103 testes verdes); cenários de
      "Distribuição via GitHub Packages"/"CI resolve a dependência" ficam sem teste automatizado
      (são verificáveis só em runner real do GitHub Actions — ver pendências 3.3/7.2)

> **Nota sobre 4.5/5.2**: como este ambiente de execução não tem acesso ao GitHub Packages real,
> a lib foi instalada no repositório Maven local (`mvn -f libs/srportto-commons-java install`)
> para validar o consumo pelas duas apps. O mecanismo de produção (GitHub Packages, D5) está
> configurado nos três `pom.xml` e no workflow novo, mas só será exercitado de fato quando o
> commit chegar ao GitHub Actions (tarefa 3.3/7.2).

## 8. Correções da auditoria do `java-revisor` (achados críticos, importantes e menores)

- [x] 8.1 [Crítico] Corrigir o gatilho de `ci-publish-srportto-commons-java.yml` para
      `branches: [main]` + `paths` restrito a `pom.xml`/`src/**` (em vez de casar qualquer branch
      e qualquer arquivo do módulo, incluindo `CLAUDE.md`/`AGENTS.md`) e adicionar
      `workflow_dispatch`
- [x] 8.2 [Crítico] Adicionar passo-guarda antes do `mvn deploy` que consulta o endpoint Maven do
      GitHub Packages (`HEAD`/`GET` no `.pom` da versão corrente, via `curl` com `GITHUB_TOKEN`) e
      pula a publicação (job termina com sucesso) se a versão já existir — deploy idempotente, sem
      falhar com 409
- [x] 8.3 [Importante] Criar o delta `MODIFIED Requirements` em
      `openspec/changes/criar-lib-utilitarios-java/specs/layout-hexagonal-classico/spec.md`
      removendo `ReversibleUUIDv7`/`BusinessException`/`ApplicationException` das listas de
      `infrastructure/persistence/`/`domain/exception/` de `contratocommand` e `contratoquery` na
      spec viva, documentando que vêm de `libs/srportto-commons-java`
- [x] 8.4 [Importante] Corrigir `proposal.md` — "Modified Capabilities" agora declara
      `layout-hexagonal-classico` como capability modificada (antes dizia "Nenhuma", incorreto)
- [x] 8.5 [Importante] Adicionar entrada de `libs/srportto-commons-java/` na árvore de estrutura
      do `README.md` raiz (entre `apps/` e `testes-carga/`)
- [x] 8.6 [Importante] Confirmar estado de `.claude/settings.json`/`settingsX.json` — fora de
      escopo desta change; não havia divergência a corrigir neste worktree
- [x] 8.7 [Menor] Adicionar bloco `permissions: {contents: read, packages: read}` a nível de job
      em `ci-testesunitarios-contratocommand.yml` e `ci-testesunitarios-contratoquery.yml`
- [x] 8.8 [Menor] Deixado como está por instrução explícita: referência a
      `openspec/changes/archive/*-criar-lib-utilitarios-java/design.md` nos `CLAUDE.md`/`AGENTS.md`
      antes do arquivamento da change — resolve-se sozinho ao arquivar
- [x] 8.9 [Menor] Adicionar `<releases><enabled>true</enabled></releases>` e
      `<snapshots><enabled>false</enabled></snapshots>` ao `<repository>` do GitHub Packages em
      `apps/contratocommand/pom.xml` e `apps/contratoquery/pom.xml`
- [x] 8.10 [Menor] Tornar `ReversibleUUIDv7` `public final class` com construtor privado que
      lança `AssertionError` (padrão utility class); confirmado que só há chamadas estáticas nos
      call sites
- [x] 8.11 [Menor] Adicionar `BusinessExceptionTest`/`ApplicationExceptionTest` em
      `libs/srportto-commons-java/src/test/java/.../exception/` cobrindo mensagem preservada
      (construtor de 1 argumento) e `getCause()` preservado (construtor de 2 argumentos)
- [x] 8.12 Rodar `mvn clean test` nos três módulos após as correções e confirmar suíte verde —
      `libs/srportto-commons-java`: 7 testes, 0 falhas (3 originais + 4 novos de exception);
      `contratocommand`: 222 testes, 0 falhas, 1 skip; `contratoquery`: 106 testes, 0 falhas, 1 skip
