## 1. Workflow de contratocommand

- [x] 1.1 Criar `.github/workflows/ci-testesunitarios-contratocommand.yml`: trigger `push`/
      `pull_request` com `paths: ["apps/contratocommand/**"]`, job `testes-unitarios`
- [x] 1.2 Passos: `actions/checkout@v4`, `actions/setup-java@v4` (Temurin 25, `cache: maven`,
      `cache-dependency-path: apps/contratocommand/pom.xml`), `working-directory:
      apps/contratocommand` rodando `mvn -B test -Dtest='!*IntegrationTest'`
- [x] 1.3 Validar localmente que `mvn -B test -Dtest='!*IntegrationTest'` roda em
      `apps/contratocommand` sem exigir Postgres e sem executar nenhuma classe `*IntegrationTest`

## 2. Workflow de contratoquery

- [x] 2.1 Criar `.github/workflows/ci-testesunitarios-contratoquery.yml` espelhando a estrutura do
      item 1.1/1.2 com `paths: ["apps/contratoquery/**"]` e `working-directory: apps/contratoquery`
- [x] 2.2 Validar localmente que `mvn -B test -Dtest='!*IntegrationTest'` roda em
      `apps/contratoquery` sem exigir Postgres e sem executar nenhuma classe `*IntegrationTest`

## 3. Workflow de autorizacaostatus-producer

- [x] 3.1 Criar `.github/workflows/ci-testesunitarios-autorizacaostatus-producer.yml` espelhando a
      estrutura do item 1.1/1.2 com `paths: ["apps/autorizacaostatus-producer/**"]` e
      `working-directory: apps/autorizacaostatus-producer`
- [x] 3.2 Validar localmente que `mvn -B test -Dtest='!*IntegrationTest'` roda em
      `apps/autorizacaostatus-producer` sem exigir Floci e sem executar
      `SqsEventoAutorizacaoListenerIntegrationTest`

## 4. Workflow de eventos-consumer

- [x] 4.1 Criar `.github/workflows/ci-testesunitarios-eventos-consumer.yml` espelhando a estrutura
      do item 1.1/1.2 com `paths: ["apps/eventos-consumer/**"]` e `working-directory:
      apps/eventos-consumer`
- [x] 4.2 Validar localmente que `mvn -B test -Dtest='!*IntegrationTest'` roda em
      `apps/eventos-consumer`

## 5. Workflow de temporiza-autorizacao

- [x] 5.1 Criar `.github/workflows/ci-testesunitarios-temporiza-autorizacao.yml` espelhando a
      estrutura do item 1.1/1.2 com `paths: ["apps/temporiza-autorizacao/**"]` e
      `working-directory: apps/temporiza-autorizacao`
- [x] 5.2 Validar localmente que `mvn -B test -Dtest='!*IntegrationTest'` roda em
      `apps/temporiza-autorizacao` sem exigir Valkey e sem executar nenhuma classe
      `*IntegrationTest` (ex.: `ValkeyStreamConfigIntegrationTest`,
      `VarreduraEAgendamentoIntegrationTest`)

## 6. Documentação

- [x] 6.1 Atualizar `CLAUDE.md`/`AGENTS.md` (idênticos) de cada uma das 5 apps com uma nota curta:
      existe esteira de CI (`ci-testesunitarios-<app>.yml`) rodando só testes unitários por
      convenção de nome (`*IntegrationTest` excluído); testes de integração continuam manuais
- [x] 6.2 Atualizar `README.md` raiz (seção de CI/workflows, se existir, ou a tabela de apps) citando
      a nova esteira ao lado de `contrato-eventos.yml`

## 7. Verificação final

- [ ] 7.1 Abrir um PR de teste alterando só um arquivo dentro de `apps/contratocommand/**` e
      confirmar que só o check `testes-unitarios` de `contratocommand` dispara, os outros 4 não
- [ ] 7.2 Confirmar nos logs do Actions que a segunda execução de cada workflow reaproveita o cache
      do Maven (cache hit) quando o `pom.xml` correspondente não mudou
- [ ] 7.3 Confirmar que os 5 checks aparecem como `testes-unitarios` na lista de status do PR,
      distinguíveis pelo nome do workflow
