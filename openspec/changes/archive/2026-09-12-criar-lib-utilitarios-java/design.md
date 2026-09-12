## Context

`contratocommand` e `contratoquery` são projetos Maven independentes (sem reactor pai comum,
cada um parented direto em `spring-boot-starter-parent`). Comparação byte-a-byte confirmou que
três classes são cópias idênticas na lógica entre as duas apps:

- `ReversibleUUIDv7` (`infrastructure/persistence/`) — codifica/extrai partição de um UUIDv7.
- `BusinessException` (`domain/exception/`) — mapeada para HTTP 422 no `ApiExceptionHandler`.
- `ApplicationException` (`domain/exception/`) — mapeada para HTTP 500 no `ApiExceptionHandler`.

Nenhuma das três tem semântica de negócio nem participa de contrato de rede (evento SNS/SQS/Kafka).
O monorepo já tem uma postura documentada e deliberada de **não** compartilhar módulo para
contratos e enums de máquina de estados (`docs/arquitetura/stack-e-padroes.md`, linha do "Contratos
espelhados à mão"), justamente para não acoplar o deploy de serviços que evoluem em ritmos
diferentes. Essa justificativa não se aplica às 3 classes acima — elas não atravessam fronteira de
serviço, só duplicam esforço de manutenção dentro do mesmo processo de build.

## Goals / Non-Goals

**Goals:**

- Eliminar a duplicação comprovada das 3 classes, com uma única fonte testada.
- Módulo **Java puro**: zero dependência de Spring, Jakarta/Servlet ou qualquer framework — só
  `java.*` e, se necessário, JUnit em escopo de teste.
- Preservar hexagonal clássica: as classes continuam elegíveis a viver em pacote `domain.exception`
  do ponto de vista de cada app consumidora — a lib não introduz acoplamento a infraestrutura, só
  move o local físico do arquivo-fonte.
- Nenhuma mudança de comportamento observável em `contratocommand`/`contratoquery` — mesma
  mensagem de exceção, mesmo mapeamento HTTP, mesmo algoritmo de codificação de partição.

**Non-Goals:**

- Não migrar `TraceIdFilter` (depende de `jakarta.servlet.Filter` — contradiz "Java puro").
- Não migrar `LayoutErrosApiResponse`/`ApiExceptionHandler` — já divergem por design entre
  command (mutável, `Integer` status) e query (imutável, `String` status); forçar convergência
  aqui reabriria uma decisão de arquitetura já fechada (`reconciliar-contrato-spec-doc`, D1).
- Não tocar em `AutorizacaoEventoPayload`, `.avsc`, `StatusAutorizacao`, `TipoEventoAutorizacao`
  ou qualquer outro espelhamento manual de contrato — permanece por decisão já registrada.
- Não estender a lib para `autorizacaostatus-producer`, `eventos-consumer`, `temporiza-autorizacao`
  ou `expurgo-particao` nesta change — nenhuma das 3 classes existe hoje nessas apps.
- Não publicar a lib em registro público (Maven Central) — só GitHub Packages, escopo interno.

## Decisions

### D1 — Módulo vive fora de `apps/`, em `libs/`

A capacidade `monorepo-organization` já fixa `apps/` como pasta exclusiva de aplicação deployável
(cada subpasta tem Dockerfile, porta HTTP, é uma unidade de deploy ECS/Fargate). O novo módulo não
é deployável — é uma dependência de biblioteca. Colocá-lo em `apps/` confundiria essa fronteira
(alguém poderia esperar Dockerfile/porta nele).

**Decisão:** novo módulo em `libs/srportto-commons-java/` (novo diretório de topo, ao lado de
`apps/` e `infra/`).

**Alternativa rejeitada:** `apps/srportto-commons` — quebraria o invariante "toda pasta em
`apps/` é deployável" da capacidade `monorepo-organization`.

### D2 — Coordenadas Maven e pacote

`groupId` `br.com.srportto`, `artifactId` `srportto-commons-java`, pacote-raiz
`br.com.srportto.commons`. As 3 classes migram para:

- `br.com.srportto.commons.exception.BusinessException`
- `br.com.srportto.commons.exception.ApplicationException`
- `br.com.srportto.commons.persistence.ReversibleUUIDv7`

Isso muda o pacote em relação às cópias atuais (`br.com.srportto.<app>.domain.exception.*` /
`...infrastructure.persistence.*`), então toda referência (throw/catch/import) em ambas as apps
precisa de atualização de import — refactoring mecânico, sem mudança de assinatura nem de
comportamento.

**Alternativa considerada:** manter o pacote `domain.exception`/`infrastructure.persistence`
dentro do jar compartilhado, só trocando o segmento do nome da app por algo neutro. Rejeitada:
criaria pacote com nome de camada (`domain`/`infrastructure`) fora do contexto de uma app
específica, confuso para quem inspecionar o jar isoladamente.

### D3 — Sem POM pai comum entre `libs/` e `apps/`

O módulo novo declara seu próprio `pom.xml`, sem `<parent>` do Spring Boot (ele não é Spring) e
sem depender de nenhuma app. `contratocommand` e `contratoquery` continuam parented no
`spring-boot-starter-parent` como hoje, só adicionando uma `<dependency>` nova.

**Trade-off aceito:** não migrar para monorepo de reactor único — decisão que ficaria maior que o
escopo desta change (afetaria as 5 apps Java, não só as 2 que consomem a lib). Ver D-CI abaixo
para o mecanismo de build que essa escolha exige.

### D4 — Versionamento fixo, sem range

Ambas as apps consumidoras fixam a mesma versão exata da lib (`<version>1.0.0</version>`, sem
`RELEASE`/`LATEST`/range). Elas SHALL usar a mesma versão simultaneamente — não há período de
convivência com duas versões da lib em produção.

**Racional:** a lib não tem contrato de rede para versionar com tolerância; é código executado
dentro do próprio processo de cada app. Range de versão introduziria risco de uma app pegar uma
versão nova sem o outro lado saber, reintroduzindo silenciosamente a divergência que a lib existe
para eliminar.

### D5 — Distribuição via GitHub Packages (decidido)

A lib é publicada como artefato Maven versionado no GitHub Packages do repositório
(`https://maven.pkg.github.com/srportto/arj-pagrecorrentes-dbrelacional`), e `contratocommand`/
`contratoquery` a consomem como dependência remota normal — sem reactor Maven na raiz, sem passo
manual de `mvn install` local.

**Mecânica:**

- `libs/srportto-commons-java/pom.xml` ganha `<distributionManagement>` apontando para o
  GitHub Packages do repositório.
- Novo workflow de CI, `ci-publish-srportto-commons-java.yml`, disparado por push em
  `libs/srportto-commons-java/**` (mesmo padrão de gatilho por path da capacidade
  `ci-testes-unitarios`), rodando `mvn deploy` com `GITHUB_TOKEN` (secret já disponível
  automaticamente em toda Action do repositório — não requer PAT novo em CI).
- `contratocommand/pom.xml` e `contratoquery/pom.xml` ganham `<repositories>` apontando para o
  mesmo endpoint, e a `<dependency>` normal na versão fixa (D4).
- Os workflows de CI dessas duas apps (`ci-testesunitarios-contratocommand.yml`,
  `ci-testesunitarios-contratoquery.yml`) ganham um passo de configurar `~/.m2/settings.xml` com
  credencial de leitura (`GITHUB_TOKEN`) antes do `mvn test` — necessário porque GitHub Packages
  exige autenticação até para leitura, mesmo em repositório público.
- **Ambiente local de cada desenvolvedor**: para rodar `mvn test`/`mvn spring-boot:run` em
  `contratocommand` ou `contratoquery`, é necessário um Personal Access Token pessoal com escopo
  `read:packages`, configurado no `~/.m2/settings.xml` da máquina — passo de onboarding novo,
  documentado nos `CLAUDE.md`/`AGENTS.md` das duas apps.

**Alternativas rejeitadas:** Opção A (reactor Maven na raiz) — mudaria o comando de build das
apps existentes (`-pl`/`-am` a partir da raiz) e tocaria a capacidade `monorepo-organization`.
Opção C (install local antes do build, sem registro) — deixava o build local quebrável por
esquecimento, sem nenhum mecanismo automático de lembrete. Ambas descartadas em favor do
isolamento de build que um registro de pacotes real proporciona, aceitando o custo de
autenticação como trade-off (ver Risks).

## Risks / Trade-offs

- **Acoplamento de deploy entre 2 serviços independentes** → É o trade-off central desta change,
  e o motivo de ela ter sido escopada para só 3 classes sem semântica de negócio. Mitigação:
  critério de elegibilidade explícito (zero regra de negócio, zero contrato de rede) documentado
  na capacidade `biblioteca-utilitarios-compartilhada`, para não virar precedente para migrar
  contratos ou enums de máquina de estados.

- **Renomeação de pacote toca todo `throw`/`catch`/`import` de `BusinessException` e
  `ApplicationException` nas duas apps** → Diff mecânico mas espalhado (ambas as classes são
  usadas amplamente em `application/usecase` e `infrastructure/web`). Mitigação: o compilador
  falha em toda referência não atualizada — não há risco de referência quebrada passar
  despercebida — e a suíte de testes de cada app roda inteira antes do merge.

- **Autenticação necessária até para leitura, em CI e em cada máquina de desenvolvedor** →
  Trade-off aceito conscientemente na D5, em troca do isolamento de build. Mitigação: usar
  `GITHUB_TOKEN` (já provisionado automaticamente em toda Action) no CI, evitando segredo novo
  para gerenciar; documentar o passo de PAT pessoal com clareza no `CLAUDE.md` das duas apps,
  incluindo o escopo mínimo necessário (`read:packages`) e como configurar `settings.xml`.

- **Ciclo de feedback mais lento que reactor local**: mudar a lib exige publish (merge → CI de
  publish → nova versão disponível) antes que `contratocommand`/`contratoquery` possam consumi-la
  — não há atalho para testar uma mudança da lib ainda não publicada dentro do build de uma app
  consumidora → Aceito: a lib muda com baixa frequência (3 classes já estáveis), o custo desse
  ciclo extra é menor que o custo operacional das outras opções.

## Migration Plan

1. Criar `libs/srportto-commons-java/` com as 3 classes (código idêntico ao de
   `contratoquery`, que já não tem os comentários redundantes removidos por outra change) e seus
   testes correspondentes (`ReversibleUUIDv7Test`, mais testes que já existirem para as duas
   exceptions em qualquer uma das apps).
2. Criar o workflow `ci-publish-srportto-commons-java.yml` e publicar a versão `1.0.0` no GitHub
   Packages (D5).
3. Em `contratocommand`: remover as 3 classes locais e seus testes locais, adicionar
   `<repositories>` + `<dependency>` no `pom.xml`, atualizar imports, configurar `settings.xml` no
   workflow de CI da app, rodar `mvn clean test`.
4. Repetir o passo 3 em `contratoquery`.
5. Rollback: reverter o(s) commit(s) — não há dado persistido nem migração de schema; o
   comportamento runtime é idêntico ao anterior. A versão já publicada no GitHub Packages pode
   permanecer órfã (sem consumidor) sem efeito colateral.

## Open Questions

Nenhuma pendente — D-CI resolvida em D5 (GitHub Packages).
