## 1. Health-check via Actuator (T3 — ambas as aplicações)

- [x] 1.1 Adicionar `spring-boot-starter-actuator` ao `aplicacoes/arj-contratocommand/pom.xml`
- [x] 1.2 Adicionar `spring-boot-starter-actuator` ao `aplicacoes/arj-contratoquery/pom.xml`
- [x] 1.3 Em `arj-contratocommand/src/main/resources/application.yaml`, expor apenas `health` (`management.endpoints.web.exposure.include: health`) com `management.endpoint.health.show-details: always`
- [x] 1.4 Em `arj-contratoquery/src/main/resources/application.yaml`, expor apenas `health` com `show-details: always`
- [x] 1.5 Validar que o indicador `db` está ativo (sem desabilitar `management.health.db.enabled`), tornando `/actuator/health` um readiness real

## 2. Remoção da rota /olamundo (T2 — contratoquery)

- [x] 2.1 Remover `arj-contratoquery/.../entrypoint/OlamundoController.java`
- [x] 2.2 Remover `arj-contratoquery/.../application/olamundo/OlamundoService.java`
- [x] 2.3 Remover `arj-contratoquery/.../domain/model/SaudacaoOlamundo.java`
- [x] 2.4 Confirmar por grep que não restam referências a `olamundo`/`SaudacaoOlamundo` no `contratoquery`

## 3. Simplificação da rota de listagem (T4 — contratoquery)

- [x] 3.1 No `AutorizacaoController` do `contratoquery`, alterar `@GetMapping("/listar")` para `@GetMapping` (mantendo `@RequestMapping("/api/autorizacoes")` e todos os parâmetros/assinatura)

## 4. Consulta por id (T1 — contratoquery)

- [x] 4.1 Criar `ResourceNotFoundException` em `shared/exceptions/`
- [x] 4.2 Adicionar `@ExceptionHandler(ResourceNotFoundException.class)` em `shared/interceptors/api/ApiExceptionHandler` retornando HTTP 404 com `LayoutErrosApiResponse`
- [x] 4.3 Criar `AutorizacaoDetalheResponseDto` em `entrypoint/contratosrest/` com os campos completos definidos na spec (status como nome do enum, metadado como JSON)
- [x] 4.4 Reusar `findById` do `AutorizacaoQueryRepository` com a chave composta `IdAutorizacao` (nenhuma query nova necessária)
- [x] 4.5 Criar serviço de consulta por id que extrai a partição via `ReversibleUUIDv7.extract(autorizacaoId)`, valida a faixa `900–999` (fora dela → `ResourceNotFoundException`), monta `IdAutorizacao` e busca; lança `ResourceNotFoundException` se não encontrado
- [x] 4.6 Adicionar `@GetMapping("/{autorizacaoId}")` no `AutorizacaoController` recebendo `@PathVariable UUID autorizacaoId` e retornando 200 com o `AutorizacaoDetalheResponseDto`

## 5. Testes (contratoquery)

- [x] 5.1 Criar teste do serviço de consulta por id: caso de sucesso (retorna DTO), caso não encontrado (`ResourceNotFoundException`) e caso de partição inválida (`ResourceNotFoundException`)
- [x] 5.2 Garantir que `ContratoqueryApplicationTests` permanece `@Disabled` (não regredir)

## 6. Documentação e validação final

- [x] 6.1 Atualizar `aplicacoes/arj-contratocommand/CLAUDE.md` e `AGENTS.md` removendo/ajustando menções a `GET /api/autorizacoes/listar` (rota agora é `GET /api/autorizacoes`); registrar a nova rota by-id e o `/actuator/health`
- [x] 6.2 Rodar `mvn clean package` em `arj-contratoquery` e confirmar build verde
- [x] 6.3 Rodar `mvn clean package` em `arj-contratocommand` e confirmar build verde
