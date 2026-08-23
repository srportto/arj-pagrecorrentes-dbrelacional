## Why

Nenhuma das 5 aplicações do monorepo tem hoje uma esteira de CI que rode testes automaticamente a
cada push/PR — só existe a verificação de contrato de evento (`contrato-eventos.yml`), que não
executa suíte de testes. Uma regressão só é pega manualmente, se alguém lembrar de rodar `mvn test`
localmente.

## What Changes

- Adiciona 5 workflows do GitHub Actions, um por app (`ci-testesunitarios-<app>.yml`), cada um
  disparado por `push`/`pull_request` restrito a `paths: apps/<app>/**` — só a app alterada roda sua
  esteira.
- Cada workflow builda com o Maven pré-instalado no runner (Java 25 Temurin) e executa
  `mvn -B test -Dtest='!*IntegrationTest'`, excluindo testes de integração pelo padrão de nome já
  usado no repo (nenhum deles roda de forma confiável num runner sem Postgres/Floci/Valkey no ar).
- Cache do Maven (`~/.m2`) isolado por app via `actions/setup-java` (`cache-dependency-path:
  apps/<app>/pom.xml`), para que uma mudança de dependência numa app não invalide o cache das
  outras.
- Nome do job/check exposto no PR: `testes-unitarios`.
- Fora de escopo nesta change: testes de integração no CI, cobertura (JaCoCo já é outra capability,
  `cobertura-testes-unitarios`), lint, build de imagem Docker, deploy.

## Capabilities

### New Capabilities

- `ci-testes-unitarios`: pipeline de CI que executa os testes unitários de cada uma das 5
  aplicações do monorepo isoladamente, disparado só quando a app correspondente muda, com testes de
  integração excluídos por convenção de nome.

### Modified Capabilities

(nenhuma — não altera o comportamento de `contrato-evento-verificado` nem de
`cobertura-testes-unitarios`, apenas adiciona uma esteira nova ao lado)

## Impact

- Código afetado: apenas arquivos novos em `.github/workflows/` (nenhuma mudança em código Java ou
  em pom.xml das apps).
- Nenhuma dependência nova é introduzida — reaproveita `actions/checkout@v4` e
  `actions/setup-java@v4`, já usados em `contrato-eventos.yml`.
- Sem impacto em runtime de produção; efeito é só na superfície de CI/PR.
