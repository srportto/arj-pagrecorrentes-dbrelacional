# Contrato Command

🏗️ **API REST Java 25** para gerenciamento de autorizações de contratos e processamento de operações PIX automáticas com **arquitetura hexagonal**. Implementa **Strategy Pattern** para múltiplos produtos (PIX Automático e DDA Automático) e suporta **particionamento temporal** de dados em PostgreSQL.

## 📋 Sobre o Projetox

O **Contrato Command** é um microserviço backend construído com **Spring Boot 4.0.4** seguindo princípios de **Domain-Driven Design (DDD)** e **arquitetura hexagonal**. É um exemplo de arquitetura corporativa escalável para gerenciar contratos de produtos financeiros com orquestração de operações e particionamento inteligente de dados.

### Funcionalidades Principais
- ✅ **Autorizações de Contratos**: Criação, listagem, cancelamento e orquestração
- ✅ **Transações PIX Automáticas**: Processamento completo via `PixAutoService` + `CriarPixAutoUseCase`
- ✅ **DDA Automático**: Use cases de criação e cancelamento com validações robustas
- ✅ **Orquestração Multi-Produto**: Strategy Pattern com interface `ContratacaoService`
- ✅ **Suporte a Múltiplos Produtos**: PIX_AUTO, DDA_AUTO (extensível via interface ContratacaoService)
- ✅ **Particionamento Temporal**: Dados particionados por conta com PostgreSQL + pg_partman + pg_cron
- ✅ **Validações em Cascata**: Validadores customizados + regras de negócio
- ✅ **Tratamento Centralizado de Exceções**: `BusinessException` (422), `ApplicationException` (500)
- ✅ **Persistência Robusta**: PostgreSQL 16+ com UUIDs reversíveis e composite primary keys
- ✅ **Logging Estruturado**: Interceptadores HTTP e tratamento de erros consistente

## 🛠️ Stack Técnico

| Componente | Versão | Descrição |
|---|---|---|
| **Java** | 25 | Novo `void main()` em lugar de `public static void main()`, records imutáveis |
| **Spring Boot** | 4.0.4 | Framework web, IoC, autowiring, data persistence |
| **Spring Data JPA** | Latest | ORM via Hibernate com suporte a queries customizadas |
| **Jakarta Validation** | 3.0 | Validação de entrada com anotações e validadores customizados |
| **Lombok** | 1.18.40 | Reduz boilerplate: `@Data`, `@Getter`, `@Setter`, `@Builder` |
| **MapStruct** | 1.5.5.Final | Mapeamento automático DTO ↔ Entity com `@AfterMapping` |
| **Yasson** | 3.0.3 | Serialização JSON (Jakarta JSON Binding) para metadados JSONB |
| **PostgreSQL** | 16+ | Persistência relacional com particionamento temporal |
| **Maven** | 3.9+ | Build, compilação e gerenciamento de dependências |

## 📁 Estrutura do Projeto

```
src/main/java/br/com/srportto/contratocommand/
├── ContratocommandApplication.java
├── application/
│   ├── defaultservice/
│   │   ├── contratacao/      # ContratacaoOrquestradorService, ContratacaoService (Strategy),
│   │   │   │                 # ContratacaoRule, ContratacaoValidator
│   │   │   └── rules/        # DataFimVigenciaInvalida, ValorLimiteContrato, MetadadoRule
│   │   └── cancelamento/     # CancelamentoOrquestradorService, CancelamentoService,
│   │       │                 # CancelamentoRule, CancelamentoValidator
│   │       └── rules/        # TipoProdutoCancelamento
│   └── enabledproduct/
│       ├── pixauto/          # PixAutoService, PixAutoMapper, PixAutoRepository, ListarAutorizacoesService
│       │   └── usecases/     # CriarPixAutoUseCase, CancelarPixAutoUseCase
│       └── ddaauto/          # DdaAutoService, DdaAutoMapper, DdaAutoRepository
│           └── usecases/     # CriarDdaAutoUseCase, CancelarDdaAutoUseCase
├── domain/
│   ├── entities/             # Autorizacao, Cancelamento, IdAutorizacao
│   ├── enums/                # TipoProduto, StatusAutorizacao, MotivoStatusAutorizacao,
│   │                         # CanaisConhecidosEnum, TipoConta
│   ├── converters/           # TipoProdutoConverter
│   ├── model/                # ContratoBase
│   └── utilities/            # ControleExpurgoAutorizacao, IdContaUUIDPartitionDistributor,
│                             # ReversibleUUIDv7, AchaQtdeSemanas
├── entrypoint/
│   ├── AutorizacaoController.java
│   └── contratosrest/        # CriarAutorizacaoRequest, CancelarAutorizacaoRequestDto,
│                             # AutorizacaoCompletaResponseDto, AutorizacaoResumidaResponseDto,
│                             # PaginacaoResponseDto
└── shared/
    ├── exceptions/           # BusinessException (422), ApplicationException (500)
    ├── interceptors/api/     # ApiExceptionHandler + DTOs de erro
    └── validationsetup/      # Rule, Validator
```

## 🏗️ Arquitetura Hexagonal (Ports & Adapters)

O projeto segue **arquitetura hexagonal** com camadas bem isoladas e responsabilidades claras:

### Camadas de Arquitetura

| Camada | Pacote | Responsabilidade | Exemplos |
|--------|--------|------------------|----------|
| **Entrypoint** | `entrypoint/` | Adaptadores de entrada (REST Controllers) | `AutorizacaoController`, DTOs `Request/Response` |
| **Application** | `application/` | Orquestração de casos de uso e regras de negócio | `PixAutoService`, `ContratacaoOrquestradorService`, `Mappers` |
| **Domain** | `domain/` | Lógica pura independente de frameworks | `Entidades`, `Enums`, `Utilities`, regras de negócio |
| **Shared** | `shared/` | Infraestrutura compartilhada | `Exceções`, `Configurações`, `Interceptadores` |

### Diagrama de Fluxo

```
┌─────────────────────────────────────────────────────────────────┐
│                    ENTRYPOINT (REST)                            │
│   ┌────────────────────────────────────────────────────────┐   │
│   │ AutorizacaoController: GET/POST/PATCH endpoints         │   │
│   │ DTOs: CriarAutorizacaoRequest, AutorizacaoResponseDto  │   │
│   └────────────────────────────────────────────────────────┘   │
└────────────────────────┬────────────────────────────────────────┘
                         │ @Valid (400 Bad Request)
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                    APPLICATION (Services)                       │
│   ┌────────────────────────────────────────────────────────┐   │
│   │ ContratacaoOrquestradorService: orquestra fluxos       │   │
│   │ PixAutoService: lógica PIX Auto                        │   │
│   │ MapStruct Mapper: DTO ↔ Entity conversion              │   │
│   └────────────────────────────────────────────────────────┘   │
│   ┌────────────────────────────────────────────────────────┐   │
│   │ Strategy Pattern via ContratacaoService (multiproduct) │   │
│   │ - PIX_AUTO → PixAutoService                            │   │
│   │ - DDA_AUTO → CriarDdaAutoUseCase / CancelarDdaAuto     │   │
│   └────────────────────────────────────────────────────────┘   │
└────────────────────────┬────────────────────────────────────────┘
                         │ BusinessException (422)
                         │ ApplicationException (500)
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                    DOMAIN (Business Logic)                      │
│   ┌────────────────────────────────────────────────────────┐   │
│   │ Entidades: Autorizacao, Cancelamento                  │   │
│   │ Enums: TipoProduto, StatusAutorizacao                 │   │
│   │ Utilities: ControleExpurgoAutorizacao,                │   │
│   │            IdContaUUIDPartitionDistributor,           │   │
│   │            ReversibleUUIDv7                           │   │
│   └────────────────────────────────────────────────────────┘   │
└────────────────────────┬────────────────────────────────────────┘
                         │ Save/Query
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                    PERSISTENCE (JPA/PostgreSQL)                 │
│   ┌────────────────────────────────────────────────────────┐   │
│   │ PixAutoRepository extends JpaRepository               │   │
│   │ Partitionable: (id_autorizacao, id_particao_conta)     │   │
│   │ Database: PostgreSQL 16+ com pg_partman + pg_cron      │   │
│   └────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### Padrões de Design Utilizados

| Padrão | Implementação | Benefício |
|--------|---------------|-----------|
| **Strategy Pattern** | `List<ContratacaoService>` injetada em `ContratacaoOrquestradorService` | Adicionar novo produto sem modificar código (Open/Closed) |
| **Repository Pattern** | `PixAutoRepository extends JpaRepository` | Abstração de persistência independente de DB |
| **Mapper Pattern** | MapStruct com `@Mapper` + `@AfterMapping` | Conversão automática e typesafe DTO ↔ Entity |
| **Composite Primary Key** | `(UUID idAutos, Integer idParticaoConta)` em Autorizacao | Particionamento temporal sem queries adicionais |
| **Value Objects** | `IdAutorizacao` record | Encapsulação de chave composta |
| **Domain Model** | Lógica em `domain/utilities/` sem frameworks | Testes unitários puros sem Spring |

## 🚀 Como Executar

### Pré-requisitos

- **Java** `25` (JDK 25+) com preview features habilitados
- **Maven** `3.9+` (use `mvn` diretamente se `./mvnw.cmd` falhar)
- **PostgreSQL** `16+` com extensões obrigatórias:
  - **pg_partman** - Particionamento automático e gerenciamento de partições
  - **pg_cron** - Agendamento de tarefas de limpeza (expurgo) em background
  
⚠️ **CRÍTICO**: O projeto NÃO possui fallback para H2 ou outras databases. PostgreSQL é obrigatório.

### Variáveis de Ambiente Obrigatórias

Configure antes de executar (arquivo `.env` ou variáveis de sistema):

```bash
# Banco de Dados
DB_NAME=contratocommand                 # Nome do banco
DB_USER_NAME=postgres                   # Usuário PostgreSQL
DB_PASSWORD=sua_senha_segura            # Senha PostgreSQL

# Spring Profiles (opcional, padrão: dev)
SPRING_PROFILES_ACTIVE=dev              # dev, test, prod
```

### Build & Compilação

```bash
# ✅ Compilar + executar testes + gerar JAR
mvn clean package

# ✅ Compilar + instalar dependências (com testes)
mvn clean install -DskipTests=false

# ⚡ Build rápido (sem testes)
mvn clean package -DskipTests=true

# 🔍 Build com verificações de qualidade
mvn clean verify

# ❌ Erro com Maven Wrapper? Use `mvn` diretamente
mvn clean package  # Em vez de ./mvnw.cmd
```

### Executar a Aplicação

**Opção 1: Via Maven (recomendado para desenvolvimento)**
```bash
mvn spring-boot:run
```

**Opção 2: Via JAR (produção)**
```bash
java -jar target/contratocommand-0.0.1-SNAPSHOT.jar
```

**Opção 3: JAR com módulos JDK 25 (se necessário)**
```bash
java --add-modules=jdk.incubator.vector \
     --add-opens=java.base/java.util=ALL-UNNAMED \
     --add-opens=java.base/java.lang=ALL-UNNAMED \
     -jar target/contratocommand-0.0.1-SNAPSHOT.jar
```

✅ Acesse: `http://localhost:8080`

### PostgreSQL via Docker Compose

Folder: `run_postgres16_ja_com_cron_partman/`

```bash
# Build imagem PostgreSQL com pg_partman + pg_cron
docker build -t contratocommand-db:16 \
  -f run_postgres16_ja_com_cron_partman/Dockerfile .

# Executar container
docker run -d \
  --name contratocommand-db \
  -e POSTGRES_DB=contratocommand \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=sua_senha_segura \
  -p 5432:5432 \
  contratocommand-db:16

# Verificar status
docker logs -f contratocommand-db

# Acessar console PostgreSQL
docker exec -it contratocommand-db psql -U postgres -d contratocommand

# Parar container
docker stop contratocommand-db
docker rm contratocommand-db
```

### Testes

```bash
# 🧪 Executar todos os testes
mvn test

# 🎯 Testar classe específica
mvn test -Dtest=PixAutoAutorizacaoServiceTest

# 🔍 Testar método específico
mvn test -Dtest=PixAutoAutorizacaoServiceTest#testCriarAutorizacao

# 📊 Gerar relatório de cobertura
mvn clean test jacoco:report
# Abrir: target/site/jacoco/index.html
```

⚠️ **Nota**: Testes de integração exigem PostgreSQL rodando localmente.

## 📚 API REST Endpoints

### AutorizacaoController

| Método | Endpoint | Descrição | Status |
|--------|----------|-----------|--------|
| **POST** | `/api/autorizacoes` | Criar autorização (multi-produto) | 201 |
| **PATCH** | `/api/autorizacoes/{idAutorizacao}/cancelar` | Cancelar (header `tipoProduto` obrigatório) | 200 |
| **GET** | `/api/autorizacoes/listar` | Listar paginado por conta | 200 |

### Criar Autorização - Request (POST `/api/autorizacoes`)

```json
{
  "dataFimVigencia": "2026-12-31",
  "tipoProduto": "PIX_AUTO",
  "valor": 500.00,
  "idAutorizacaoEmpresa": "EMP001",
  "valorLimite": 10000.00,
  "frequencia": 2,
  "quantidadeDividasCiclo": 5,
  "indicadorUsoLimiteConta": 1,
  "codigoCanalContratacao": "01",
  "descricao": "Autorização PIX automática para transferências",
  "idUnicoContaContratante": "550e8400-e29b-41d4-a716-446655440000",
  "idPessoaPagadora": "550e8400-e29b-41d4-a716-446655440001",
  "idPessoaDevedora": "550e8400-e29b-41d4-a716-446655440002",
  "idPessoaRecebedora": "550e8400-e29b-41d4-a716-446655440003",
  "metadados": {
    "origem": "MOBILE",
    "versao_contrato": "1.0"
  }
}
```

#### Validações de Campo

| Campo | Tipo | Validação | HTTP se inválido |
|-------|------|-----------|------------------|
| `dataFimVigencia` | LocalDate | Não pode estar no passado | 422 |
| `tipoProduto` | Enum | PIX_AUTO, DDA_AUTO | 400 |
| `valor` | BigDecimal | @NotNull obrigatório | 400 |
| `frequencia` | Integer | @Min(1) @Max(4) (semanal a trimestral) | 400 |
| `quantidadeDividasCiclo` | Integer | @Min(1) obrigatório | 400 |
| `idUnicoContaContratante` | UUID | @NotNull obrigatório | 400 |

#### Respostas

**201 Created:**
```json
{
  "idAutorizacao": {
    "idAutos": "550e8400-e29b-41d4-a716-446655440000",
    "idParticaoConta": 45
  },
  "statusAutorizacao": "ATIVO",
  "dataFimVigencia": "2026-12-31",
  "tipoProduto": "PIX_AUTO",
  "valor": 500.00
}
```

**400 Bad Request** (validação de input):
```json
{
  "timestamp": "2026-05-17T23:32:31.000Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed: frequencia must be between 1 and 4",
  "path": "/api/autorizacoes"
}
```

**422 Unprocessable Entity** (regra de negócio violada):
```json
{
  "timestamp": "2026-05-17T23:32:31.000Z",
  "status": 422,
  "error": "Unprocessable Content",
  "message": "Data de fim de vigência não pode estar no passado",
  "path": "/api/autorizacoes"
}
```

**500 Internal Server Error** (erro inesperado):
```json
{
  "timestamp": "2026-05-17T23:32:31.000Z",
  "status": 500,
  "error": "Internal Server Error",
  "message": "Erro ao processar autorização",
  "path": "/api/autorizacoes"
}
```

### Cancelar Autorização - Request (PATCH `/api/autorizacoes/{idAutorizacao}/cancelar`)

```json
{
  "dataFimVigencia": "2026-06-30",
  "motivoCancelamento": "SOLICITACAO_CLIENTE"
}
```

**200 OK:** Retorna `AutorizacaoCompletaResponseDto` atualizada com `statusAutorizacao: "CANCELADO"`

## 📝 Alterações Recentes - v0.0.1 (maio 2026)

### ✅ API REST Completa Multi-Produto

**Endpoints implementados com Strategy Pattern:**
- `POST /api/autorizacoes` - Criação com orquestrador multi-produto
- `GET /api/autorizacoes/listar` - Listagem paginada por conta
- `PATCH /api/autorizacoes/{idAutorizacao}/cancelar` - Cancelamento programado

### ✅ Strategy Pattern para Múltiplos Produtos

**Produtos suportados:**
- **PIX_AUTO** → `PixAutoService` + `CriarPixAutoUseCase`
- **DDA_AUTO** → `DdaAutoService` + `CriarDdaAutoUseCase` / `CancelarDdaAutoUseCase`

**Seleção de produto (sem factory):**

`ContratacaoOrquestradorService` recebe `List<ContratacaoService>` via injeção Spring e itera chamando `validaContratacaoSuportada(request)` — o primeiro que retornar `true` é escolhido. Nenhuma `ProdutoStrategyFactory` existe em `src/` (os arquivos em `docs/strategyProduto/` são apenas exemplos didáticos).

### ✅ DTOs com Records Imutáveis (Java 25)

**Request DTO com validações declarativas:**
```java
public record CriarAutorizacaoRequest(
    LocalDate dataFimVigencia,
    @NotNull TipoProduto tipoProduto,
    @NotNull BigDecimal valor,
    @Min(1) @Max(4) Integer frequencia,
    @NotNull UUID idUnicoContaContratante,
    // ... demais campos
) {}
```

**Response DTO com Builder:**
```java
@Builder @Getter
public record AutorizacaoCompletaResponseDto(
    IdAutorizacao idAutorizacao,  // Composite PK
    TipoProduto tipoProduto,
    LocalDate dataFimVigencia,
    BigDecimal valor,
    StatusAutorizacao statusAutorizacao,
    JsonNode metadados
) {}
```

### ✅ Composite Primary Key com UUIDs Reversíveis

**Entidade com chave composta:**
```java
@Entity
@Table(name = "autorizacoes")
public class Autorizacao {
    @EmbeddedId
    private IdAutorizacao id;  // (UUID, Integer)
    
    // ... demais campos
}
```

**Record para composite PK:**
```java
public record IdAutorizacao(
    @Column(name = "id_autorizacao") UUID idAutos,
    @Column(name = "id_particao_conta") Integer idParticaoConta
) {}
```

### ✅ Mapeamento Automático com MapStruct

**Mapper com customização via `@AfterMapping`:**
```java
@Mapper(componentModel = "spring")
public interface PixAutoMapper {
    @Mapping(target = "id", ignore = true)
    Autorizacao toEntity(CriarAutorizacaoRequest dto);
    
    @AfterMapping
    default void afterToEntity(CriarAutorizacaoRequest dto, 
                                @MappingTarget Autorizacao auto) {
        // Gera UUID com partição embutida
        Integer particao = IdContaUUIDPartitionDistributor.getPartitionFast();
        UUID uuid = ReversibleUUIDv7.generate(particao);
        auto.setId(new IdAutorizacao(uuid, particao));
        auto.setStatusAutorizacao(StatusAutorizacao.ATIVO);
    }
}
```

### ✅ Validações em Cascata

**Pipeline de validação:**

| Nível | Componente | Validação | Exceção | HTTP |
|-------|-----------|-----------|---------|------|
| 1 | DTOs com `@Valid` | @NotNull, @Min, @Max | `BindException` | 400 |
| 2 | Validadores customizados | Regras específicas | `BusinessException` | 422 |
| 3 | Service layer | Regras de negócio | `BusinessException` | 422 |

### ✅ Particionamento Temporal com Composite Key

**Tabela particionada:**
```sql
CREATE TABLE autorizacoes (
    id_autorizacao UUID,
    id_particao_conta INTEGER,
    valor DECIMAL(19,2),
    data_criacao TIMESTAMP,
    PRIMARY KEY (id_autorizacao, id_particao_conta)
) PARTITION BY RANGE (id_particao_conta);
```

**Utilidade de domínio:**
```java
// domain/utilities/ControleExpurgoAutorizacao.java
Integer particaoEscrita = ControleExpurgoAutorizacao.obterParticaoExpurgoWrite();
Integer particaoDrop = ControleExpurgoAutorizacao.obterParticaoExpurgoDrop();
```

### ✅ Transações Explícitas e Isolamento

**Service com `@Transactional`:**
```java
@Service
@Transactional
public class PixAutoService {
    public void criar(CriarAutorizacaoRequest request) {
        // Dentro de transação ACID
    }
}
```

## ⚙️ Configurações Spring Boot

O arquivo `application.yaml` define configuração mínima para ambiente:

```yaml
spring:
  application:
    name: contratocommand
  
  datasource:
    url: jdbc:postgresql://localhost:5432/${DB_NAME:contratocommand}
    username: ${DB_USER_NAME:postgres}
    password: ${DB_PASSWORD:postgres}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
  
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: update          # validate, update, create, create-drop
    properties:
      hibernate:
        generate_statistics: false
        format_sql: false
        jdbc:
          batch_size: 10
          fetch_size: 50
  
  jackson:
    serialization:
      write-dates-as-timestamps: false
    deserialization:
      fail-on-unknown-properties: false

server:
  port: 8080
  compression:
    enabled: true
    min-response-size: 1024

logging:
  level:
    root: INFO
    br.com.srportto: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
```

### DDL Auto - Comportamento

| Valor | Ação | Uso |
|-------|------|-----|
| **validate** | Valida schema sem alterações | ✅ Produção (schema precriado) |
| **update** | Altera schema conforme entidades | ✅ Desenvolvimento |
| **create** | Cria schema do zero, apaga dados | ⚠️ Apenas testes locais |
| **create-drop** | Cria e destrói ao final | ⚠️ Testes apenas |

### Particionamento Temporal com `pg_partman`

**Tabela `autorizacoes` particionada por `id_particao_conta`:**

```sql
-- Criar partição (feito automaticamente por pg_partman)
CREATE TABLE autorizacoes (
    id_autorizacao UUID,
    id_particao_conta INTEGER CHECK (id_particao_conta >= 900 AND id_particao_conta <= 999),
    data_criacao TIMESTAMP NOT NULL DEFAULT NOW(),
    valor DECIMAL(19,2) NOT NULL,
    tipoProduto VARCHAR(50),
    statusAutorizacao VARCHAR(20),
    PRIMARY KEY (id_autorizacao, id_particao_conta)
) PARTITION BY RANGE (id_particao_conta);

-- Criar partições iniciais (900-924, 925-949, 950-974, 975-999)
SELECT pg_partman.partition_table('public.autorizacoes', 'id_particao_conta');
```

**Utilities de domínio:**

```java
// Obter partição ATUAL (para escrita)
Integer particaoAtual = ControleExpurgoAutorizacao.obterParticaoExpurgoWrite();

// Obter partição ANTIGA (para drop/expurgo)
Integer particaoAntiga = ControleExpurgoAutorizacao.obterParticaoExpurgoDrop();

// Gerar UUID com partição embutida
Integer particao = IdContaUUIDPartitionDistributor.getPartitionFast();
UUID uuid = ReversibleUUIDv7.generate(particao);

// Extrair partição do UUID
Integer particaoExtraida = ReversibleUUIDv7.extract(uuid);
```

### UUIDs Reversíveis (ReversibleUUIDv7)

**Por que não usar UUID aleatório puro?**
- UUIDs aleatórios não carregam informação de partição
- Exigem queries adicionais para extrair `id_particao_conta`
- ReversibleUUIDv7 combina timestamp + partição em um único campo

**Características:**
- ✅ Ordenável por timestamp (sequencial)
- ✅ Partição embutida (sem query adicional)
- ✅ Reversível via `ReversibleUUIDv7.extract(uuid)`
- ✅ Compatível com UPSERT e idempotência

## 🧪 Testes

### Executar Testes

```bash
# 🧪 Todos os testes
mvn test

# 🎯 Classe específica
mvn test -Dtest=PixAutoAutorizacaoServiceTest

# 🔍 Método específico
mvn test -Dtest=PixAutoAutorizacaoServiceTest#testCriarAutorizacao

# 📊 Com cobertura (jacoco)
mvn clean test jacoco:report
# Abrir: target/site/jacoco/index.html
```

### Estrutura de Testes

```
src/test/java/br/com/srportto/contratocommand/
├── ContratocommandApplicationTests.java
├── application/
│   ├── pixauto/PixAutoAutorizacaoServiceTest.java
│   └── enabledproduct/pixauto/ListarAutorizacoesServiceTest.java
└── domain/utilities/
    ├── ControleExpurgoAutorizacaoTest.java
    └── GeraDatasPorParticao.java
```

### Testes de Integração

**Pré-requisito**: PostgreSQL rodando em `localhost:5432`

```bash
# Testes que requerem banco
mvn test -P integration

# Sem testes de integração
mvn test -P '!integration'
```

### Exemplo de Teste Unitário

```java
@ExtendWith(MockitoExtension.class)
class PixAutoAutorizacaoServiceTest {
    
    @Mock
    private PixAutoRepository repository;
    
    @InjectMocks
    private PixAutoService service;
    
    @Test
    void testCriarAutorizacao() {
        // Arrange
        CriarAutorizacaoRequest request = new CriarAutorizacaoRequest(...);
        Autorizacao autorizacaoEsperada = new Autorizacao(...);
        
        when(repository.save(any())).thenReturn(autorizacaoEsperada);
        
        // Act
        Autorizacao resultado = service.criar(request);
        
        // Assert
        assertEquals(autorizacaoEsperada.getId(), resultado.getId());
        verify(repository, times(1)).save(any());
    }
}
```

## 📦 Dependências Principais

```xml
<!-- Spring Framework -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Persistência -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- Lombok -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.40</version>
</dependency>

<!-- MapStruct -->
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>1.5.5.Final</version>
</dependency>

<!-- Testes -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

## 🔧 Padrões de Design Utilizados

| Padrão | Implementação | Benefício |
|--------|---------------|-----------|
| **Strategy Pattern** | `List<ContratacaoService>` injetada em `ContratacaoOrquestradorService` | Multiplicidade de produtos sem modificar código existente |
| **Repository Pattern** | `PixAutoRepository extends JpaRepository` | Abstração de persistência |
| **Mapper Pattern** | MapStruct com `@Mapper` + `@AfterMapping` | Conversão typesafe automática DTO ↔ Entity |
| **Composite Primary Key** | `(UUID, Integer)` em `IdAutorizacao` | Particionamento eficiente sem queries adicionais |
| **Value Objects** | Records imutáveis para DTOs e IDs | Imutabilidade + segurança de tipo |
| **Dependency Injection** | `@Autowired` / `@Component` / `@Service` | Loose coupling, testabilidade |

## 📝 Convenções de Codificação

### Nomenclatura

| Elemento | Padrão | Exemplos |
|----------|--------|----------|
| **Entidades** | Substantivo singular | `Autorizacao`, `Cancelamento` |
| **Services** | `{Nome}Service` | `PixAutoService`, `ContratacaoOrquestradorService` |
| **UseCases** | `{Operacao}UseCase` | `CriarPixAutoUseCase`, `CriarDdaAutoUseCase`, `CancelarDdaAutoUseCase` |
| **Repositories** | `{Entidade}Repository` | `PixAutoRepository` |
| **Utilities** | `{Nome}Distributor` ou `{Nome}Controle` | `IdContaUUIDPartitionDistributor`, `ControleExpurgoAutorizacao` |
| **Mappers** | `{Entidade}Mapper` | `PixAutoMapper` |
| **Controllers** | `{Recurso}Controller` | `AutorizacaoController` |
| **Request DTOs** | `{Operacao}{Entidade}Request` | `CriarAutorizacaoRequest`, `CancelarAutorizacaoRequest` |
| **Response DTOs** | `{Entidade}ResponseDto` ou `{Entidade}CompletaResponseDto` | `AutorizacaoCompletaResponseDto` |
| **Enums** | PascalCase, descritivo | `TipoProduto`, `StatusAutorizacao`, `MotivoStatusAutorizacao` |

### Estrutura de Pacotes (Hexagonal)

```
br.com.srportto.contratocommand
├── application/          # Orquestração, serviços, mappers
├── domain/              # Lógica pura, entidades, enums, utilities
├── entrypoint/          # REST controllers, DTOs externos
├── shared/              # Infraestrutura, exceções, configs
└── ContratocommandApplication.java  # Entry point Spring Boot
```

### Validações & Exceções

**Validação de Input:**
```java
// DTOs com @Valid + anotações JSR-380
public record CriarAutorizacaoRequest(
    @NotNull(message = "campo obrigatório") BigDecimal valor,
    @Min(1) @Max(4) Integer frequencia,
    // ...
) {}
```

**Regras de Negócio:**
```java
if (dataFim.isBefore(LocalDate.now())) {
    throw new BusinessException("Data de fim não pode estar no passado");
}
```

**Erros Inesperados:**
```java
try {
    // operação
} catch (Exception e) {
    throw new ApplicationException("Erro ao processar", e);
}
```

### Records são Imutáveis

```java
// ❌ ERRADO: Tentar modificar record
CriarAutorizacaoRequest request = ...;
request.valor = 5000;  // Compile error!

// ✅ CERTO: Recriar com novo valor
CriarAutorizacaoRequest novoRequest = new CriarAutorizacaoRequest(..., 5000, ...);
```

## 📖 Documentação External

- [Spring Boot 4.0.4 Documentation](https://spring.io/projects/spring-boot)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)  
- [MapStruct Documentation](https://mapstruct.org/)
- [Jakarta Bean Validation 3.0](https://jakarta.ee/specifications/validate/)
- [PostgreSQL Partitioned Tables](https://www.postgresql.org/docs/current/ddl-partitioning.html)
- [pg_partman Extension](https://github.com/pgpartman/pg_partman)

## 🤝 Contribuindo

### Workflow Padrão

1. **Fork** do repositório principal
2. **Clone local**: `git clone https://github.com/your-username/contratocommand.git`
3. **Crie branch feature**: `git checkout -b feature/nova-funcionalidade`  
4. **Desenvolva + commit**: `git commit -m 'Implementa {nova_feature}'`  
5. **Push para remota**: `git push origin feature/{nome-feature}`  
6. Abra um Pull Request no repositório original

### Diretrizes de Código

- Segue arquitetura hexagonal: Domain layer independente de frameworks
- DTOs imutáveis com records quando possível
- Validadores customizados em regras específicas, não espalhados
- Composite PK `(UUID, Integer)` apenas no contexto particionamento temporal
- Exceptions mapeadas para status HTTP apropriado (400/422/500)

## 📄 Licença

Este projeto está licenciado sob a licença MIT - veja o arquivo [LICENSE](./LICENSE) para detalhes.

## 👨‍💻 Informações do Projeto

**Organização**: SR Porto  
**Grupo**: br.com.srportto  
**Versão**: 0.0.1-SNAPSHOT  
**Java Version**: 25 (JDK 25+ com preview features)  
**Spring Boot**: 4.0.4  
**Última atualização**: 17 de maio de 2026

## 📞 Suporte

Para dúvidas ou problemas, abra uma issue no repositório ou entre em contato com a equipe de desenvolvimento.

---

## ⚠️ Notas Importantes Sobre Java 25 Preview Features

Este projeto utiliza preview features do JDK 25 que podem exigir flags JVM específicas:

```bash
# Para compilar e executar com Java 25
java --version
javac --verbose --source 25 --release 25 \
   -Xlint:none --add-modules=jdk.incubator.vector --add-opens=java.base/java.util=ALL-UNNAMED \
   -jar target/contratocommand.jar
```

**Se encontrar erros de compilação**, verifique:
1. JDK 25 instalado e configurado (`JAVA_HOME` apontando para JDK 25)
2. Maven com plugin compatible Java 25+  
3. Preview features habilitados no build (se necessário)

---

📌 **Documentação Completa**: Arquivos em `docs/` contêm detalhes técnicos sobre particionamento, payloads de testes e scripts SQL para setup de banco de dados.
