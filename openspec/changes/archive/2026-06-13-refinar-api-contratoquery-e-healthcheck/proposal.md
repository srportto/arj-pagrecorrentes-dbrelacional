## Why

A API do `contratoquery` ainda carrega resquícios do esqueleto inicial: a única forma de leitura é uma listagem cuja rota tem um sufixo `/listar` desnecessário, não há como consultar uma autorização individual, e a "disponibilidade" da aplicação é sinalizada por uma rota improvisada `/olamundo`. Falta também observabilidade padronizada — nenhuma das duas aplicações expõe um health-check real para orquestradores/load balancers.

## What Changes

- **Nova rota** `GET /api/autorizacoes/{autorizacaoId}` no `contratoquery`, que retorna os dados de uma única autorização. Quando a autorização não existe (ou o id é malformado), responde **404**.
- **BREAKING:** a rota de listagem do `contratoquery` deixa de ser `GET /api/autorizacoes/listar` e passa a ser `GET /api/autorizacoes` (sem o sufixo `/listar`). Parâmetros, filtros, paginação e formato de resposta permanecem idênticos.
- **Health-check via Spring Boot Actuator** em **ambas** as aplicações (`contratocommand` e `contratoquery`), expondo `GET /actuator/health` com verificação de conectividade ao PostgreSQL (readiness).
- **Remoção** da rota `/olamundo` e de todo o código associado (`OlamundoController`, `OlamundoService`, `SaudacaoOlamundo`) do `contratoquery` — o `/actuator/health` assume o papel de rota de disponibilidade.

## Capabilities

### New Capabilities
- `consultar-autorizacao-por-id`: consulta de uma autorização individual no `contratoquery` via `GET /api/autorizacoes/{autorizacaoId}`, incluindo o comportamento de 404 para não encontrado/id inválido.
- `health-check`: endpoint de saúde via Actuator (`GET /actuator/health`) em `contratocommand` e `contratoquery`, com indicador de banco de dados; substitui e remove a rota legada `/olamundo`.

### Modified Capabilities
- `listar-autorizacoes`: a rota da listagem muda de `GET /api/autorizacoes/listar` para `GET /api/autorizacoes`; todo o restante do contrato (parâmetros, DTO de resposta, códigos de erro) permanece inalterado.

## Impact

- **contratoquery** — `entrypoint/AutorizacaoController` (nova rota by-id + remoção do `/listar`), nova camada de consulta por id (service + repository query), novo DTO de detalhe, nova `ResourceNotFoundException` + handler 404 em `shared/interceptors/api/ApiExceptionHandler`; remoção de 3 arquivos do `/olamundo`; `pom.xml` (+actuator) e `application.yaml` (exposição de health).
- **contratocommand** — `pom.xml` (+actuator) e `application.yaml` (exposição de health); atualização de `CLAUDE.md`/`AGENTS.md` que ainda citam `/api/autorizacoes/listar`.
- **Dependências** — adiciona `spring-boot-starter-actuator` às duas aplicações.
- **Consumidores** — clientes da listagem do `contratoquery` precisam atualizar a URL (`/api/autorizacoes/listar` → `/api/autorizacoes`).
- **OpenSpec** — modifica a spec existente `listar-autorizacoes` (já arquivada na change `mover-listagem-autorizacoes-para-contratoquery`).
