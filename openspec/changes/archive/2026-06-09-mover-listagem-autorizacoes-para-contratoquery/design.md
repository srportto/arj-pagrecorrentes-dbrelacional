## Context

O sistema segue CQRS: `contratocommand` gerencia mutações (criar/cancelar autorizações) e `contratoquery` serve as leituras. Atualmente o endpoint `GET /api/autorizacoes/listar` está no `contratocommand`, lendo a mesma tabela que ele escreve. Ambas as aplicações rodam em Spring Boot 4.0.4 / Java 25 e compartilham o mesmo PostgreSQL 16. O `contratoquery` já existe como esqueleto hexagonal funcional (Jetty, sem JPA).

## Goals / Non-Goals

**Goals:**
- Mover a capacidade de listagem de autorizações para o `contratoquery`, respeitando CQRS.
- Remover do `contratocommand` todo código exclusivo da listagem (service, endpoint, DTOs, queries de paginação, testes).
- Manter ambas as aplicações buildáveis e funcionais (`mvn clean package` sem infraestrutura).
- Preservar o contrato do endpoint `GET /api/autorizacoes/listar` (mesmos parâmetros e estrutura de resposta).

**Non-Goals:**
- Não mover `POST /api/autorizacoes` nem `PATCH /{id}/cancelar`.
- Não criar read replica ou CDC; `contratoquery` lê diretamente a mesma base PostgreSQL.
- Não extrair código compartilhado em módulo Maven comum (cada app permanece autônoma).
- Não alterar schema de banco de dados.

## Decisions

### 1. Banco de dados compartilhado, sem biblioteca compartilhada

**Decisão:** `contratoquery` aponta para o mesmo banco PostgreSQL do `contratocommand` e faz cópias locais das entidades/enums necessários, sem criar um módulo `arj-shared`.

**Alternativas consideradas:**
- **Read replica separada** — aumenta consistência eventual e isolamento, mas exige infraestrutura extra fora do escopo desta mudança.
- **Módulo Maven comum** — elimina duplicação de código, mas acopla ciclos de release e complexifica o build multi-módulo no estado atual do repositório.

**Rationale:** Minimiza risco. As entidades copiadas são estáveis (domínio central) e o custo de manutenção da duplicação é baixo enquanto o projeto está em maturação.

### 2. Dependências JPA adicionadas ao contratoquery

**Decisão:** Adicionar `spring-boot-starter-data-jpa` + driver `postgresql` ao `pom.xml` do `contratoquery`. O datasource será configurado via variáveis de ambiente (`DB_NAME`, `DB_USER_NAME`, `DB_PASSWORD`) — mesmo padrão do `contratocommand`.

**Alternativa considerada:** JDBC puro ou Spring JDBC Template (sem JPA). Descartado porque o repositório JPA existente (`PixAutoRepository`) com as queries JPQL já cobre todos os casos de uso da listagem; replicá-lo em JPA é a opção de menor atrito.

**Consequência:** o teste de contexto `ContratoqueryApplicationTests` precisará de `@Disabled` (igual ao `contratocommand`) porque o `@SpringBootTest` tentará conectar ao banco.

### 3. Remoção de StatusAutorizacao do contratocommand

**Decisão:** `StatusAutorizacao` é usado apenas em `ListarAutorizacoesService` e `AutorizacaoResumidaResponseDto` — ambos movidos para `contratoquery`. Após a migração, remover o enum do `contratocommand`.

**Verificação:** `grep -r StatusAutorizacao src/` no `contratocommand` confirmou que os únicos usos são nos dois arquivos mencionados; `AutorizacaoCompletaResponseDto` expõe `status` como `Integer` bruto.

### 4. Package layout no contratoquery espelha o contratocommand

**Decisão:** Manter a estrutura hexagonal em `br.com.srportto.contratoquery.*` com os mesmos subpacotes (`domain/entities/`, `domain/enums/`, `application/`, `entrypoint/`). Os arquivos copiados recebem apenas ajuste de `package`.

**Rationale:** Consistência com o modelo arquitetural do projeto documentado em `based-java-aplication.md`.

## Risks / Trade-offs

| Risco | Mitigação |
|---|---|
| Duplicação de entidade `Autorizacao` entre as duas apps diverge com o tempo | Aceitar por ora; revisitar quando o schema estabilizar ou surgir terceiro consumidor |
| `contratoquery` sem JPA não builda sem datasource (testes de contexto) | Anotar `@Disabled` no `ContratoqueryApplicationTests` |
| Queries de paginação removidas do `PixAutoRepository` quebram algo no contratocommand | Verificar que nenhum serviço de criação/cancelamento usa os métodos `findByIdUnicoContaContratante*` antes de remover |
| Mudança de endereço do endpoint (porta diferente do contratoquery) pode quebrar clientes | Comunicar que o GET muda de host/porta; fora do escopo desta task |

## Migration Plan

1. Adicionar dependências JPA + PostgreSQL ao `contratoquery`.
2. Copiar entidades (`Autorizacao`, `IdAutorizacao`, `Cancelamento`, `ContratoBase`), enums (`StatusAutorizacao`, `TipoProduto`, `TipoConta`, `CanaisConhecidosEnum`, `MotivoStatusAutorizacao`), converters (`TipoProdutoConverter`) e utilities (`ReversibleUUIDv7`, `IdContaUUIDPartitionDistributor`, `AchaQtdeSemanas`, `ControleExpurgoAutorizacao`) necessários para a entidade compilar.
3. Criar repositório `AutorizacaoQueryRepository` no `contratoquery` com as queries de paginação.
4. Criar `ListarAutorizacoesService` e os DTOs (`AutorizacaoResumidaResponseDto`, `PaginacaoResponseDto`) no `contratoquery`.
5. Adicionar endpoint `GET /api/autorizacoes/listar` no `AutorizacaoController` do `contratoquery` (criá-lo se ainda não existir além do `/olamundo`).
6. Copiar/adaptar `ListarAutorizacoesServiceTest` para o `contratoquery`.
7. Validar `mvn clean package` no `contratoquery`.
8. Remover do `contratocommand`: `ListarAutorizacoesService`, trecho do controller, DTOs exclusivos de listagem, queries de paginação do `PixAutoRepository`, `StatusAutorizacao`, `ListarAutorizacoesServiceTest`.
9. Validar `mvn clean package` no `contratocommand`.

**Rollback:** reversão é um `git revert`; não há mudança de schema.

## Open Questions

- Os métodos `findByIdUnicoContaContratante` e `findByIdUnicoContaContratanteAndStatusIn` do `PixAutoRepository` são usados em algum lugar além do `ListarAutorizacoesService`? (Verificar no passo 8 antes de remover.)
- O `contratoquery` terá `hibernate.ddl-auto=none` (apenas leitura) ou `validate`? Recomendar `none` para evitar DDL acidental num serviço read-side.
