## Context

O `contratoquery` (porta 8081, Jetty, `ddl-auto: none`) e o `contratocommand` (porta 8080, Tomcat, `ddl-auto: update`) compõem um par CQRS sobre o mesmo PostgreSQL 16 particionado. O `contratoquery` hoje expõe `GET /api/autorizacoes/listar` e uma rota improvisada `/olamundo` (cujo próprio javadoc se descreve como "rota de disponibilidade"). A entidade `Autorizacao` usa chave composta `IdAutorizacao(UUID idAutorizacao, Integer idParticaoConta)`, e o utilitário `ReversibleUUIDv7.extract(uuid)` consegue derivar a partição a partir do próprio UUID. O `ApiExceptionHandler` atual trata apenas 422 (`BusinessException`), 500 (`ApplicationException`) e 422 (validação) — **não há 404**.

Esta change refina a superfície de API do `contratoquery` e adiciona observabilidade padronizada às duas aplicações. As decisões abaixo foram acordadas na fase de exploração.

## Goals / Non-Goals

**Goals:**
- Adicionar `GET /api/autorizacoes/{autorizacaoId}` no `contratoquery` com semântica REST (200 com corpo, 404 quando não encontrado).
- Simplificar a rota de listagem de `GET /api/autorizacoes/listar` para `GET /api/autorizacoes`, preservando todo o resto do contrato.
- Expor `GET /actuator/health` (com check de banco) em `contratocommand` e `contratoquery`.
- Remover o `/olamundo` e seu código, uma vez que o Actuator assume o papel de rota de disponibilidade.
- Manter ambas as aplicações buildáveis sem infraestrutura (`mvn clean package`).

**Non-Goals:**
- Não introduzir autenticação/autorização nos endpoints do Actuator.
- Não expor outros endpoints do Actuator além de `health` (sem `metrics`, `env`, `loggers` etc.).
- Não alterar o schema do banco nem o contrato da listagem (parâmetros/DTO).
- Não versionar a API (`/v2`) nem manter alias de compatibilidade para `/listar`.

## Decisions

### 1. Busca by-id via extração de partição do UUID

**Decisão:** o serviço de consulta deriva a partição com `ReversibleUUIDv7.extract(autorizacaoId)`, monta `IdAutorizacao(autorizacaoId, particao)` e usa `AutorizacaoQueryRepository.findById(...)`.

**Alternativa considerada:** JPQL `WHERE a.idAutorizacao.idAutorizacao = :id`. Descartada por varrer todas as partições (sem partition pruning); a extração ataca uma única partição e é coerente com o racional do `ReversibleUUIDv7`.

**Consequência:** um UUID que não tenha sido gerado pelo `ReversibleUUIDv7` (id externo/malformado) produzirá uma partição fora da faixa válida `900–999`. Esse caso é validado e tratado como **404** (não 500).

### 2. Não encontrado e id inválido → 404 via nova exceção

**Decisão:** criar `ResourceNotFoundException` em `shared/exceptions` e um `@ExceptionHandler` em `ApiExceptionHandler` que retorna **HTTP 404** com o mesmo `LayoutErrosApiResponse` usado pelos demais erros. O serviço lança essa exceção quando: (a) o `findById` não encontra a autorização, ou (b) a partição extraída do UUID está fora de `900–999`.

**Alternativa considerada:** reusar `BusinessException` (422). Descartada por ser semanticamente incorreta para "recurso não encontrado".

### 3. DTO de detalhe próprio para o by-id

**Decisão:** criar `AutorizacaoDetalheResponseDto` (representação completa da autorização) para a resposta do by-id, em vez de reusar o `AutorizacaoResumidaResponseDto` da listagem.

**Rationale:** GET-by-id retorna a representação completa do recurso; a listagem retorna uma projeção resumida. Acoplar os dois forçaria a listagem a expor campos detalhados ou o detalhe a ficar incompleto. Segue o precedente do `AutorizacaoCompletaResponseDto` do `contratocommand`.

### 4. Health-check via Actuator com indicador de banco (readiness)

**Decisão:** adicionar `spring-boot-starter-actuator` aos dois `pom.xml` e expor apenas `health` na web (`management.endpoints.web.exposure.include: health`). O indicador `db` (padrão do Actuator quando há `DataSource`) permanece ativo, tornando o `/actuator/health` um readiness real — `DOWN` se o PostgreSQL estiver inacessível. `management.endpoint.health.show-details: always` em dev para facilitar diagnóstico.

**Alternativa considerada:** liveness puro (sem `db`). Descartada porque ambos os serviços dependem do banco para cumprir seu papel; um health que ignora o banco daria falso positivo. Caso liveness puro venha a ser necessário, usar grupos `liveness`/`readiness` do Actuator (fora do escopo agora).

**Nota:** o Actuator funciona normalmente sobre o Jetty do `contratoquery`; nenhuma configuração específica de container é necessária.

### 5. Remoção do `/olamundo` em vez de mantê-lo

**Decisão:** remover `OlamundoController`, `OlamundoService` e `domain/model/SaudacaoOlamundo` do `contratoquery`. Não há testes nem outras referências (verificado por grep).

**Ordem recomendada:** introduzir o `/actuator/health` (Decisão 4) antes de remover o `/olamundo`, para a aplicação nunca ficar sem rota de disponibilidade.

## Risks / Trade-offs

| Risco | Mitigação |
|---|---|
| `ReversibleUUIDv7.extract` em UUID arbitrário pode gerar partição inesperada | Validar faixa `900–999` e tratar como 404; cobrir com teste de id malformado |
| Mudança da rota `/listar` → `/autorizacoes` quebra clientes existentes | Documentar como BREAKING no proposal; atualizar `CLAUDE.md`/`AGENTS.md`; comunicar consumidores |
| `/actuator/health` com `db` fica `DOWN` em ambiente sem banco, confundindo "app subiu" | Decisão consciente (readiness); diagnóstico facilitado por `show-details: always` |
| Conflito entre `GET /api/autorizacoes` (lista) e `GET /api/autorizacoes/{id}` | Sem conflito de roteamento — uma rota tem path variable, a outra não |
| Teste de contexto `ContratoqueryApplicationTests` sobe Spring e tentaria conectar | Já está `@Disabled`; manter assim |

## Migration Plan

1. Adicionar `spring-boot-starter-actuator` + exposição de `health` nos dois apps; validar `/actuator/health`.
2. Remover os 3 arquivos do `/olamundo` do `contratoquery`.
3. Alterar `@GetMapping("/listar")` → `@GetMapping` no `AutorizacaoController` do `contratoquery`.
4. Implementar by-id: `ResourceNotFoundException` + handler 404, `AutorizacaoDetalheResponseDto`, método de consulta no service/repository, rota `GET /{autorizacaoId}`.
5. Atualizar `contratocommand/CLAUDE.md` e `AGENTS.md` (remover/ajustar menções a `/api/autorizacoes/listar`).
6. `mvn clean package` em ambos os apps.

**Rollback:** `git revert`; não há mudança de schema.

## Open Questions

- Nenhuma pendência bloqueante. (DTO de detalhe, estratégia de busca, comportamento 404 e escopo do health já decididos acima.)
