## Why

A operação `GET /api/autorizacoes/listar` está implementada no `contratocommand` (peça de escrita), violando o princípio CQRS que rege a arquitetura: leituras pertencem ao `contratoquery`. Mover o endpoint consolida a separação de responsabilidades, simplifica o `contratocommand` e posiciona o `contratoquery` para receber futuras consultas sem precisar de mudanças estruturais.

## What Changes

- **REMOVE** do `contratocommand`: `ListarAutorizacoesService`, endpoint `GET /api/autorizacoes/listar` em `AutorizacaoController`, DTOs `AutorizacaoResumidaResponseDto` e `PaginacaoResponseDto`, queries de listagem/paginação em `PixAutoRepository`, enum `StatusAutorizacao` (se não usado por mais nada), e o teste `ListarAutorizacoesServiceTest`.
- **ADD** no `contratoquery`: toda a stack de listagem — JPA + PostgreSQL, entidade `Autorizacao` (read-only), enum `StatusAutorizacao`, DTOs equivalentes, repositório de consulta, `ListarAutorizacoesService`, `AutorizacaoController` com `GET /api/autorizacoes/listar`, e testes unitários correspondentes.
- `contratocommand` mantém apenas as operações de mutação: `POST` (criar) e `PATCH` (cancelar), suas dependências diretas, e o restante do `PixAutoRepository` (reads pontuais por ID usados internamente na contratação/cancelamento).
- Ambas as aplicações devem continuar buildando e executando com `mvn clean package` sem infraestrutura adicional além do PostgreSQL já exigido pelo `contratocommand`.

## Capabilities

### New Capabilities

- `listar-autorizacoes`: Endpoint `GET /api/autorizacoes/listar` no `contratoquery`, com paginação, filtro por status e ordenação configurável, devolvendo `PaginacaoResponseDto<AutorizacaoResumidaResponseDto>`.

### Modified Capabilities

*(sem alterações de requisito em specs existentes)*

## Impact

- **contratoquery** (`aplicacoes/arj-contratoquery`): passa a depender de `spring-boot-starter-data-jpa` + driver `postgresql`; ganha entidade `Autorizacao`, `IdAutorizacao` (chave composta), enum `StatusAutorizacao`, DTOs de listagem, repositório JPA, service e controller. `application.yaml` precisa da configuração de datasource (variáveis `DB_NAME`, `DB_USER_NAME`, `DB_PASSWORD` — mesma base que o `contratocommand`).
- **contratocommand** (`aplicacoes/arj-contratocommand`): remoção de `ListarAutorizacoesService`, do endpoint `GET /api/autorizacoes/listar`, dos DTOs exclusivos de listagem (`AutorizacaoResumidaResponseDto`, `PaginacaoResponseDto`) e do teste `ListarAutorizacoesServiceTest`. Os queries de paginação em `PixAutoRepository` também podem ser removidos (não são usados por criar/cancelar). Verificar antes se `StatusAutorizacao` é referenciado além do serviço de listagem.
- **Sem breaking change externo** nos endpoints de escrita (POST, PATCH); apenas o GET muda de porta/serviço.
- **Banco de dados**: nenhuma mudança de schema — `contratoquery` lê a mesma tabela `autorizacoes` já existente (read-only na prática; nenhum `save` novo).
