## Context

O monorepo tem 5 apps Maven independentes (sem pom pai). A única
esteira hoje (`contrato-eventos.yml`) verifica sincronismo de schema, não roda testes. Investigação
da suíte de testes mostrou que a convenção de nomes é `*Test.java` para tudo, incluindo integração
(`*IntegrationTest.java`) — ambos casam com o include default do Surefire (`**/*Test.java`), então
não há separação hoje a nível de build.

O comportamento sem infra local não é uniforme entre apps:

- `contratocommand`/`contratoquery`: os testes de integração com Postgres usam
  `PostgresLocalDisponivelCondition`, que se auto-desabilita (SKIPPED) sem `DB_PASSWORD` — não
  quebrariam num runner pelado.
- `autorizacaostatus-producer`: `SqsEventoAutorizacaoListenerIntegrationTest` não tem guarda — exige
  Floci (LocalStack) com fila real provisionada, e falha sem isso.
- `temporiza-autorizacao`: os testes de integração com Valkey (`ValkeyStreamConfigIntegrationTest`,
  `VarreduraEAgendamentoIntegrationTest` e outros) também não têm guarda — exigem Valkey local no ar.

O parent é `spring-boot-starter-parent:4.0.7`, que gerencia uma versão do `maven-surefire-plugin`
recente o bastante para suportar exclusão via `-Dtest='!*IntegrationTest'` sem precisar declarar o
plugin explicitamente em nenhum pom.xml.

**Descoberta durante a implementação (invalidou uma decisão inicial):** só `contratocommand` tem
`mvnw`/`mvnw.cmd` versionados, e mesmo esse não tinha `.mvn/wrapper/maven-wrapper.properties`
commitado (`.mvn` está no `.gitignore` raiz) — o wrapper nunca funcionou a partir de um clone limpo.
As outras 4 apps não têm wrapper nenhum. O `CLAUDE.md` de cada app já documenta o comando de teste
como `mvn test` (Maven global), não `./mvnw test`. A decisão original desta seção (usar `./mvnw`) foi
revertida em favor do Maven do runner — ver Decisions.

## Goals / Non-Goals

**Goals:**
- Rodar automaticamente, a cada push/PR, apenas os testes unitários da app que mudou.
- Não quebrar em runner sem infra local (Postgres/Floci/Valkey) — exclusão confiável de todo teste
  de integração, guardado ou não.
- Cache de dependências Maven isolado por app.
- Zero mudança em código Java ou em pom.xml.

**Non-Goals:**
- Rodar testes de integração no CI (fica para uma change futura, quando houver infra
  provisionada — Testcontainers, serviços do GitHub Actions, ou Floci/Valkey como container do job).
- Medir ou impor gate de cobertura (já é escopo de `cobertura-testes-unitarios`, via JaCoCo).
- Separar unit/integration por `@Tag` do JUnit5 — fica como evolução possível, não necessária agora
  porque a exclusão por nome já resolve o "apenas unitário" pedido.
- Lint, build de imagem Docker, deploy, ou qualquer outro estágio de pipeline.

## Decisions

**Um workflow por app, não um workflow único com matrix.** Segue o precedente já estabelecido por
`contrato-eventos.yml` (workflow enxuto e path-scoped). Alternativa considerada: um único workflow
com `strategy.matrix` + `paths` no nível do job. Rejeitada porque path filtering de job individual
dentro de uma matrix exige lógica condicional extra (ex. `dorny/paths-filter`) para decidir quais
entradas da matrix rodam — os triggers nativos do GitHub Actions (`on.push.paths`) já resolvem isso
de graça quando cada app tem seu próprio arquivo de workflow.

**Exclusão de integração por convenção de nome (`-Dtest='!*IntegrationTest'`), não por `@Tag`.**
Alternativa considerada: anotar as ~15 classes de integração espalhadas pelas 5 apps com
`@Tag("integration")` e configurar `<excludedGroups>` no Surefire. Rejeitada por ora porque exige
tocar código Java em todas as apps — fora do escopo desta change, que é só infraestrutura de CI. A
convenção de nome já é 100% consistente hoje (toda classe de integração termina em
`IntegrationTest.java`), então a exclusão por `-Dtest` é equivalente em cobertura sem exigir a
mudança de código.

**Maven pré-instalado no runner (`mvn`), não `./mvnw`.** Decisão inicial era usar o wrapper de cada
app; revertida ao constatar que só `contratocommand` tem `mvnw` versionado (e mesmo esse sem
`.mvn/wrapper/maven-wrapper.properties`, então nunca funcionou num clone limpo) e que o `CLAUDE.md`
de todas as 5 apps já documenta `mvn test` como o comando oficial. Alternativa considerada: gerar
wrapper nas 4 apps que não têm e atualizar os 5 `CLAUDE.md` para `./mvnw`. Rejeitada por aumentar o
escopo desta change (que é só infraestrutura de CI) e divergir da convenção já documentada e usada
pelos desenvolvedores.

**Cache via `actions/setup-java` com `cache-dependency-path: apps/<app>/pom.xml`**, não
`actions/cache` manual. É a forma suportada nativamente pela action já em uso em
`contrato-eventos.yml`, e isola a chave de cache por `pom.xml` — um bump de dependência numa app não
invalida o cache das outras 4.

**Nome do arquivo: `ci-testesunitarios-<app>.yml`; nome do job/check: `testes-unitarios`.** Decisão
do usuário, mantém o prefixo `ci-` livre para outros estágios (lint, build) entrarem depois como
`ci-<estagio>-<app>.yml`.

## Risks / Trade-offs

- **Exclusão por nome é frágil a longo prazo**: se alguém criar uma classe de teste de integração
  sem o sufixo `IntegrationTest`, ela entra silenciosamente no job de unitário e pode falhar por
  falta de infra (ou, pior, passar por acidente contra infra real de outro ambiente). → Mitigação:
  a convenção já é auditável (grep por `@SpringBootTest` sem sufixo `IntegrationTest` seria um sinal
  de alerta); documentar a convenção no CLAUDE.md de cada app como parte das tasks desta change.
- **`autorizacaostatus-producer` e `temporiza-autorizacao` ficam sem qualquer verificação automática
  de integração no CI** — hoje já é o caso (não havia CI de testes nenhum), então não é regressão,
  mas o gap fica mais visível ao lado de um pipeline que "parece completo". → Mitigação: Non-Goals
  deixa explícito que isso é intencional e nomeia o caminho futuro (infra containerizada no job).
- **5 arquivos de workflow quase idênticos** (duplicação) em vez de um único parametrizado. →
  Mitigação aceita conscientemente: é o padrão que já existe no repo e evita a complexidade de matrix
  + path-filter condicional só para 5 entradas que raramente mudam de forma coordenada.

## Migration Plan

Adição pura — nenhum workflow existente é alterado ou removido. Deploy é o merge do PR que adiciona
os 5 arquivos em `.github/workflows/`; efeito imediato no próximo push/PR que tocar `apps/**`.
Rollback trivial: remover os arquivos (ou o PR que os introduziu).

## Open Questions

Nenhuma pendente — nome de arquivo, runner de Maven e nome do check já foram decididos com o
usuário antes desta change ser proposta.
