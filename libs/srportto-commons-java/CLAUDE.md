# CLAUDE.md

> Guia para agentes de IA (Claude Code, Copilot, etc.) trabalharem neste módulo.
> **Este arquivo e `AGENTS.md` são espelhos — mantenha-os idênticos ao editar.**

Módulo Maven **Java puro** (sem Spring, sem Jakarta/Servlet, sem qualquer dependência de
framework) com utilitários técnicos genuinamente idênticos entre apps do monorepo. Não é uma app
deployável — não tem Dockerfile, porta HTTP nem `apps/**` como pai; vive em `libs/` por decisão
explícita (ver `openspec/changes/archive/*-criar-lib-utilitarios-java/design.md`, D1).

## O que existe aqui

- `br.com.srportto.commons.persistence.ReversibleUUIDv7` — codifica/extrai uma partição (0–9999)
  embutida num UUIDv7.
- `br.com.srportto.commons.exception.BusinessException` — erro de regra de negócio (422 no
  `ApiExceptionHandler` de cada app consumidora).
- `br.com.srportto.commons.exception.ApplicationException` — erro inesperado de aplicação (500 no
  `ApiExceptionHandler` de cada app consumidora).

Consumidores atuais: `apps/contratocommand`, `apps/contratoquery`.

## Critério de elegibilidade de classe (as 3 condições SHALL ser todas verdadeiras)

1. Não carrega regra de negócio específica do domínio de autorização.
2. Não participa de contrato de rede entre serviços (payload de evento SNS/SQS/Kafka, schema
   Avro, enum de máquina de estados).
3. Hoje existe como cópia idêntica (ou quase idêntica, diferindo só em comentário/formatação) em
   duas ou mais apps do monorepo.

Classe que viole qualquer uma das três condições **não** deve ser movida para cá — nem
`TraceIdFilter` (depende de `jakarta.servlet`), nem `LayoutErrosApiResponse`/`ApiExceptionHandler`
(já divergem por design entre command/query), nem `AutorizacaoEventoPayload`/`.avsc`/
`StatusAutorizacao`/`TipoEventoAutorizacao` (contratos e enums de máquina de estados continuam
espelhados à mão, ver `CLAUDE.md` raiz).

## Build & Testes

```bash
mvn clean test      # Compilar + testes
mvn clean install   # Instalar no repositório Maven local (útil para testar consumo antes do publish)
```

## Publicar uma versão nova

1. Suba a `<version>` no `pom.xml` (versionamento fixo — nunca `RELEASE`/`LATEST`/range).
2. Faça o commit tocando `libs/srportto-commons-java/**` — o workflow
   `.github/workflows/ci-publish-srportto-commons-java.yml` roda `mvn deploy` automaticamente para
   o GitHub Packages do repositório, usando `GITHUB_TOKEN` do próprio Actions.
3. Atualize a `<version>` da dependência em `apps/contratocommand/pom.xml` e
   `apps/contratoquery/pom.xml` **manualmente** — o publish da lib nunca dispara o CI das apps
   consumidoras automaticamente; a atualização de versão é sempre um passo explícito.

## Pré-requisito de ambiente local

GitHub Packages exige autenticação até para leitura, mesmo em repositório público. Para rodar
`mvn test`/`mvn spring-boot:run` em `contratocommand` ou `contratoquery`, configure um Personal
Access Token pessoal com escopo `read:packages` no `~/.m2/settings.xml`.

## Armadilhas críticas

1. **Zero dependência de framework em escopo `compile`** — nada de `org.springframework*` ou
   `jakarta.*`. JUnit é permitido só em escopo `test`.
2. **Versão fixa nas apps consumidoras** — nunca `RELEASE`/`LATEST`/range (D4 do design.md). Duas
   apps podem consumir versões diferentes por um tempo, mas isso deve ser resultado de uma decisão
   explícita em cada `pom.xml`, nunca de resolução automática.
3. **Mudar o algoritmo de `ReversibleUUIDv7`/mensagem das exceções é breaking change silencioso**
   para quem já persiste dado codificado com a versão antiga — trate como contrato estável.
