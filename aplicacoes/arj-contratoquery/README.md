# Contrato Query

API REST Java 25 para **consulta e listagem de autorizações de contratos** (PIX Automático e DDA Automático), em **arquitetura hexagonal**. Serviço de leitura do par `arj-contratocommand` (escrita, porta 8080) + `arj-contratoquery` (leitura, porta 8081), compartilhando o mesmo banco PostgreSQL particionado.

## Sobre o Projeto

O **Contrato Query** é o microserviço de leitura responsável por expor consultas sobre as autorizações persistidas pelo `arj-contratocommand`. Construído com **Spring Boot 4.0.4** e **arquitetura hexagonal**, ele é estritamente **somente leitura** (`DB_READ_ONLY=true`): não possui endpoints de escrita, orquestradores, use cases de criação/cancelamento nem mappers.

### Funcionalidades

- **Listagem paginada**: `GET /api/autorizacoes` — filtra por conta, status, paginação e ordenação
- **Consulta por id**: `GET /api/autorizacoes/{autorizacaoId}` — extrai a partição diretamente do UUID reversível, sem query extra
- **Health-check**: `GET /actuator/health` — readiness de banco via Spring Actuator

## Stack Técnico

| Componente | Versão | Descrição |
|---|---|---|
| **Java** | 25 | `void main()`; records imutáveis |
| **Spring Boot** | 4.0.4 | Web MVC, Data JPA, Validation, Actuator |
| **Jetty** | embutido | Container web (Tomcat excluído no `pom.xml`) |
| **Spring Data JPA** | Latest | ORM via Hibernate; JPQL explícito no repositório |
| **Jakarta Validation** | 3.0 | Validação de entrada |
| **Lombok** | 1.18.40 | `@Data`, `@Getter`, `@Builder`, `@AllArgsConstructor` |
| **PostgreSQL** | 16+ | Particionamento com `pg_partman` + `pg_cron` |
| **Maven** | 3.9+ | Build e gerenciamento de dependências |

> Sem MapStruct — DTOs construídos via método estático `from()`.

## Estrutura do Projeto

```
src/main/java/br/com/srportto/contratoquery/
├── ContratoqueryApplication.java
├── application/
│   └── autorizacao/
│       ├── AutorizacaoQueryRepository.java    # JPQL explícito para particionamento
│       ├── ListarAutorizacoesService.java     # Listagem paginada com filtro de status
│       └── ConsultarAutorizacaoService.java  # Busca por id com extração de partição
├── domain/
│   ├── entities/      # Autorizacao, Cancelamento, IdAutorizacao (composite PK)
│   ├── enums/         # TipoProduto, StatusAutorizacao, MotivoStatusAutorizacao, TipoConta, CanaisConhecidosEnum
│   ├── converters/    # TipoProdutoConverter
│   ├── model/         # ContratoBase
│   └── utilities/     # ControleExpurgoAutorizacao, IdContaUUIDPartitionDistributor,
│                      # ReversibleUUIDv7, AchaQtdeSemanas
├── entrypoint/
│   ├── AutorizacaoController.java
│   └── contratosrest/
│       ├── AutorizacaoResumidaResponseDto.java   # from() estático
│       ├── AutorizacaoDetalheResponseDto.java    # from() estático
│       └── PaginacaoResponseDto.java
└── shared/
    ├── exceptions/         # BusinessException (422), ApplicationException (500),
    │                       # ResourceNotFoundException (404)
    └── interceptors/api/   # ApiExceptionHandler + DTOs de erro
```

## Arquitetura Hexagonal

O projeto segue **arquitetura hexagonal** com camadas isoladas:

| Camada | Pacote | Responsabilidade |
|--------|--------|-----------------|
| **Entrypoint** | `entrypoint/` | Controller REST + DTOs de resposta |
| **Application** | `application/autorizacao/` | Services de leitura + Repository |
| **Domain** | `domain/` | Entidades, Enums, Utilities (pura, sem frameworks) |
| **Shared** | `shared/` | Exceções e tratamento centralizado de erros |

### Fluxo de uma requisição GET (listagem)

```
AutorizacaoController.listar()
  └─ ListarAutorizacoesService.listar()
       ├─ valida idUnicoContaContratante (BusinessException se nulo)
       ├─ converte nomes de status (String → Integer via StatusAutorizacao enum)
       ├─ constrói Pageable com mapeamento de campos DTO→entidade
       └─ AutorizacaoQueryRepository.findByIdUnicoContaContratante()   ← JPQL
            └─ AutorizacaoResumidaResponseDto.from(autorizacao)         ← from() estático
```

### Fluxo de uma requisição GET (consulta por id)

```
AutorizacaoController.consultarPorId()
  └─ ConsultarAutorizacaoService.consultarPorId()
       ├─ ReversibleUUIDv7.extract(uuid)         ← extrai idParticaoConta sem query
       ├─ valida faixa 0–889 → 404 imediato se fora
       └─ AutorizacaoQueryRepository.findById(IdAutorizacao(uuid, particao))
            └─ AutorizacaoDetalheResponseDto.from(autorizacao)
```

### Exceções e Códigos HTTP

Tratadas centralmente em `ApiExceptionHandler`.

| Origem | HTTP | Quando |
|--------|------|--------|
| `BusinessException` | 422 | Parâmetro inválido (ex.: valor de status desconhecido) |
| `ResourceNotFoundException` | 404 | Autorização não encontrada pelo id |
| `ApplicationException` | 500 | Erro inesperado de sistema |

## Como Executar

### Pré-requisitos

- **Java 25** (JDK 25+)
- **Maven 3.9+** (use `mvn` se `./mvnw.cmd` falhar no Windows)
- **PostgreSQL 16+** com `pg_partman` e `pg_cron` — obrigatório, sem fallback H2

### Variáveis de Ambiente

```bash
# Obrigatórias
DB_NAME=contratoquery           # Nome do banco (compartilhado com o command)
DB_USER_NAME=postgres           # Usuário PostgreSQL
DB_PASSWORD=sua_senha_segura    # Senha PostgreSQL

# Opcionais (padrões seguros para leitura)
DB_READ_ONLY=true               # Padrão: true — não alterar em produção
DB_TRANSACTION_ISOLATION=TRANSACTION_READ_COMMITTED
DB_POOL_MAX_SIZE=10
DB_POOL_MIN_IDLE=2
```

### Build & Execução

```bash
# Compilar + testes + JAR
mvn clean package

# Build rápido (sem testes)
mvn clean package -DskipTests=true

# Rodar localmente (porta 8081)
mvn spring-boot:run

# Via JAR
java -jar target/contratoquery-0.0.1-SNAPSHOT.jar
```

Acesse: `http://localhost:8081`

### PostgreSQL via Docker Compose

Pasta: `run_postgres16_ja_com_cron_partman/` (na raiz do repositório)

```bash
# Build da imagem PostgreSQL com pg_partman + pg_cron
docker build -t contratocommand-db:16 \
  -f run_postgres16_ja_com_cron_partman/Dockerfile .

# Subir container
docker run -d \
  --name contratocommand-db \
  -e POSTGRES_DB=contratoquery \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=sua_senha_segura \
  -p 5432:5432 \
  contratocommand-db:16
```

## API REST Endpoints

### GET `/api/autorizacoes` — Listagem paginada

**Query params:**

| Parâmetro | Tipo | Obrigatório | Padrão | Descrição |
|-----------|------|-------------|--------|-----------|
| `idUnicoContaContratante` | UUID | Sim | — | Filtra autorizações da conta |
| `status` | String[] | Não | todos | Ex.: `ATIVO`, `CANCELADO` |
| `pagina` | Integer | Não | 0 | Página (base 0) |
| `tamanho` | Integer | Não | 20 | Itens por página |
| `ordenarPor` | String | Não | `dataHoraInclusao,desc` | Campo + direção (`asc`/`desc`) |

**Campos válidos para `ordenarPor`:** `dataHoraInclusao` (ou `dataCriacao`), `valor`, `idAutorizacao`, `dataInicioVigencia`, `dataFimVigencia`, `idPessoaRecebedora`.

**200 OK:**
```json
{
  "conteudo": [
    {
      "idAutorizacao": "550e8400-e29b-41d4-a716-446655440000",
      "tipoProduto": "PIX_AUTO",
      "statusAutorizacao": "ATIVO",
      "valor": 500.00,
      "dataFimVigencia": "2026-12-31"
    }
  ],
  "paginaAtual": 0,
  "totalPaginas": 3,
  "totalElementos": 58,
  "tamanho": 20
}
```

**422 Unprocessable Entity** (status inválido):
```json
{
  "status": 422,
  "mensagem": "Status inválido: INVALIDO. Use um dos valores: ATIVO, CANCELADO"
}
```

---

### GET `/api/autorizacoes/{autorizacaoId}` — Consulta por id

**Path param:** `autorizacaoId` (UUID gerado pelo `ReversibleUUIDv7`)

**200 OK:**
```json
{
  "idAutorizacao": "550e8400-e29b-41d4-a716-446655440000",
  "tipoProduto": "PIX_AUTO",
  "statusAutorizacao": "ATIVO",
  "valor": 500.00,
  "valorLimite": 10000.00,
  "dataFimVigencia": "2026-12-31",
  "dataHoraInclusao": "2026-06-10T14:32:00"
}
```

**404 Not Found** (id não encontrado ou partição inválida):
```json
{
  "status": 404,
  "mensagem": "Autorizacao nao encontrada para o id 550e8400-e29b-41d4-a716-446655440000"
}
```

---

### GET `/actuator/health`

**200 OK** (banco disponível):
```json
{ "status": "UP" }
```

**503 Service Unavailable** (banco indisponível):
```json
{ "status": "DOWN" }
```

## Testes

```bash
# Todos os testes
mvn test

# Classe específica
mvn test -Dtest=ConsultarAutorizacaoServiceTest

# Método específico
mvn test -Dtest=ListarAutorizacoesServiceTest#deveListarComFiltroDeStatus

# Com relatório de cobertura (JaCoCo)
mvn clean verify
# Abrir: target/site/jacoco/index.html
```

### Classes de Teste

| Classe | Pacote |
|--------|--------|
| `ContratoqueryApplicationTests` | raiz |
| `AutorizacaoControllerTest` | `entrypoint/` |
| `ListarAutorizacoesServiceTest` | `application/autorizacao/` |
| `ConsultarAutorizacaoServiceTest` | `application/autorizacao/` |
| `ApiExceptionHandlerTest` | `shared/interceptors/api/` |
| `AutorizacaoDetalheResponseDtoTest` | `entrypoint/contratosrest/` |
| `AutorizacaoResumidaResponseDtoTest` | `entrypoint/contratosrest/` |
| `TipoProdutoConverterTest` | `domain/converters/` |
| `AutorizacaoTest` | `domain/entities/` |
| `StatusAutorizacaoTest`, `TipoProdutoTest`, outros | `domain/enums/` |
| `ReversibleUUIDv7Test`, `ControleExpurgoAutorizacaoTest`, outros | `domain/utilities/` |

> Testes unitários rodam sem banco de dados. Testes `@SpringBootTest` que precisam de contexto completo exigem PostgreSQL.

## Convenções de Codificação

### Nomenclatura

| Elemento | Padrão | Exemplos |
|----------|--------|----------|
| **Entidades** | Substantivo singular | `Autorizacao`, `Cancelamento` |
| **Services** | `{Nome}Service` | `ListarAutorizacoesService`, `ConsultarAutorizacaoService` |
| **Repository** | `{Entidade}QueryRepository` | `AutorizacaoQueryRepository` |
| **Response DTOs** | `{Entidade}ResponseDto` | `AutorizacaoResumidaResponseDto`, `AutorizacaoDetalheResponseDto` |
| **Controllers** | `{Recurso}Controller` | `AutorizacaoController` |

### Conversão DTO

Não há MapStruct nesta app. Conversão feita via `from()` estático:

```java
// Correto
AutorizacaoResumidaResponseDto dto = AutorizacaoResumidaResponseDto.from(autorizacao);

// Incorreto — não existe mapper
// PixAutoMapper.toDto(autorizacao)  ← não existe aqui
```

### Records são Imutáveis

```java
// Errado: tentar modificar record
dto.valor = 5000;  // Compile error!

// Correto: recriar o record
AutorizacaoResumidaResponseDto novoDto = new AutorizacaoResumidaResponseDto(...);
```

## Armadilhas Críticas

1. **Esta app é somente leitura** — `DB_READ_ONLY=true` por padrão. Não adicione endpoints de escrita ou `@Transactional` com flush.
2. **Porta 8081**, não 8080 (que é do `arj-contratocommand`).
3. **Não há Strategy Pattern** — sem `ContratacaoOrquestradorService`, sem use cases `Criar*` ou `Cancelar*`.
4. **Sem MapStruct** — não instancie mappers; use `from()` nos DTOs.
5. **Container é Jetty**, não Tomcat — configurações de servidor Tomcat não se aplicam.
6. **Faixa de partição na leitura é 0–889** (não 900–999 como no command) — `ConsultarAutorizacaoService` lança 404 imediato para UUIDs fora dessa faixa.
7. **JPQL explícito no repository** — `AutorizacaoQueryRepository` não usa métodos derivados do Spring Data; renomear campos da entidade exige atualizar as queries manualmente.
8. **Base de URL é `/api/autorizacoes`** (plural) — não existe `/api/autorizacao` nem `/listar`.

## Documentação em `docs/` (raiz do repositório)

- `docs/info_build-my-image-and-execute.md` — Docker + PostgreSQL com partman/cron
- `docs/comandos-sql.txt` — scripts SQL de particionamento
- `docs/resultado-poc/POC_PARTICIONAMENTO_BUFFER_RING_UUIDV7.md` — racional do particionamento

## Informações do Projeto

**Grupo:** br.com.srportto
**Artifact:** contratoquery
**Versão:** 0.0.1-SNAPSHOT
**Java:** 25 | **Spring Boot:** 4.0.4 | **Porta:** 8081
