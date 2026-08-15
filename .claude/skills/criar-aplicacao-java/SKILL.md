---

name: criar-aplicacao-java
description: "Generates a buildable Spring Boot 4 + Java 25 application skeleton following the classic hexagonal (ports & adapters) layout — `domain` (model, port/in, port/out) / `application` (use cases) / `infrastructure` (adapters) —, with a `/disponibilidade` health route and a chosen variant (REST, CRUD with DB, SQS listener, Kafka consumer, SQS-to-Kafka bridge, REST publishing to Kafka, etc.). Use when the user asks to create a new application, microservice, or skeleton. Uso: agent `java-construtor` (and `java-revisor` modo `auditoria` for final validation) or manual invocation via `/criar-aplicacao-java`; não deve ser carregada proativamente pela sessão principal."
license: MIT
metadata:
  author: https://github.com/srportto/srportto
  co-author: https://github.com/Jeffallan/claude-skills
  version: "2.0.0"
  domain: application-scaffolding
  triggers: crie uma aplicação, novo microserviço, esqueleto de app java, app que consome fila, consumidor kafka, hexagonal Spring Boot, ports and adapters
  role: builder
  scope: application-generation
  output-format: code
  related-skills: arquitetura-limpa-java, mensageria-sqs-kafka, persistencia-jpa, java-moderno, java-architecture
---
---

# Criar Aplicação Java (Spring Boot, hexagonal clássica)

## Visão geral

Gera uma aplicação Spring Boot **buildável** seguindo a **arquitetura hexagonal clássica (ports &
adapters)** — camadas `domain` / `application` / `infrastructure`, ver `arquitetura-limpa-java` — com
uma rota de disponibilidade pronta, combinada, opcionalmente, com uma **variante** que adiciona a
funcionalidade pedida (CRUD com banco, consumo de fila, ponte de mensageria, etc.). Não há templates
físicos para copiar neste catálogo — cada aplicação é gerada do zero seguindo os requisitos desta
skill e das skills referenciadas (`arquitetura-limpa-java`, `mensageria-sqs-kafka`,
`persistencia-jpa`, `java-moderno`).

**Princípio central:** a base (sem variante) roda **sem depender de nenhuma infraestrutura externa**
— nem banco, nem fila, nem broker. É o "esqueleto" seguro para começar qualquer aplicação. Cada
variante é quem introduz (e exige) a infraestrutura que passa a ser necessária.

A base entrega sempre:
- Pacote `br.com.srportto.<nome>`, classe principal `<Nome>Application`
- As três camadas já materializadas (ver "Layout gerado" abaixo), com o health check passando por
  uma porta — é o exemplo vivo do padrão dentro do próprio esqueleto
- Rota `GET /disponibilidade` → `200 OK`, corpo `{"aplicacao":"<nome>","status":"DISPONIVEL"}`
- Tratamento de erros (`BusinessException` → 422, `ApplicationException` → 500, validação de bean)
- Teste de contexto (`@SpringBootTest`) que sobe sem infra externa

### Layout gerado

```
br.com.srportto.<nome>/
├── domain/
│   ├── model/                 ← modelo puro (ex.: Disponibilidade)
│   ├── port/in/               ← ConsultarDisponibilidadeUseCase
│   ├── port/out/              ← portas de saída (vazio na base pura)
│   └── exception/             ← BusinessException, ApplicationException
├── application/
│   └── usecase/               ← ConsultarDisponibilidadeService (@Service, implementa a port/in)
└── infrastructure/
    ├── web/                   ← DisponibilidadeController, DTOs, ApiExceptionHandler
    └── config/                ← @Configuration
```

> **Nunca** gere aplicação nova no layout legado `entrypoint`/`application`/`domain`/`shared` — as
> apps de `apps/` ainda o usam, mas ele é transitório; a tabela de equivalência está em
> `arquitetura-limpa-java`.

## Quando usar / Quando NÃO usar

**Use esta skill quando** o pedido for para **criar uma aplicação/microserviço nova do zero** — REST
puro, CRUD com banco, listener de fila SQS, ponte SQS→banco, ponte SQS→Kafka, consumidor Kafka, ou
REST que publica em Kafka.

**NÃO use esta skill para:**
- Adicionar uma feature/endpoint/entidade em uma aplicação **já existente** — use
  `arquitetura-limpa-java`.
- Tirar dúvidas sobre mensageria sem a intenção de criar uma aplicação nova — use
  `mensageria-sqs-kafka`.

## SEMPRE pergunte antes de gerar

| Parâmetro | Uso | Exemplo |
|-----------|-----|---------|
| **Nome da pasta destino** | diretório onde o projeto será gerado | `pedidos-service` |
| **Nome da aplicação** | deriva `artifactId`, pacote `br.com.srportto.<nome>`, classe `<Nome>Application`, `spring.application.name` | `pedidos` |
| **Porta** | `server.port` | `8081` |
| **Profile default** | `spring.profiles.default` | `local` |
| **Container web** | Tomcat (default) ou Jetty — ver abaixo | `Jetty` |
| **Variante** | base pura ou uma das 6 variantes — ver tabela abaixo | `sqs-listener` |

> Derive os identificadores do "nome da aplicação": pacote = `br.com.srportto.<nome>` (minúsculo),
> classe principal = `<Nome>Application` (PascalCase).

### Container web

`spring-boot-starter-webmvc` traz **Tomcat** por padrão.

| Container | Quando preferir |
|-----------|------------------|
| **Tomcat** (default) | Sem alteração; máxima compatibilidade, maior base de troubleshooting. |
| **Jetty** | Cloud-native/containers, alta concorrência, muitos WebSockets/streaming. |

> ⚠️ **Undertow NÃO existe no Spring Boot 4.x** (o BOM só gerencia Tomcat e Jetty para web MVC, mais
> reactor-netty para reativo). `spring-boot-starter-undertow` falha com "version is missing".

Para Jetty, exclua o Tomcat do starter web e adicione o starter Jetty:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-tomcat</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jetty</artifactId>
</dependency>
```
> Após trocar o container, valide no log de startup a linha do servidor ativo (`Jetty started on port
> <porta>` em vez de `Tomcat started on port <porta>`).

### Variante — componentes obrigatórios

| Variante | O que gerar | Skill de referência |
|----------|-------------|----------------------|
| **base pura** | Só a base hexagonal, sem infra externa. | — |
| **rest-crud-banco** | Modelo puro em `domain/model/`, `port/out` de repositório, use case em `application/usecase/`, e em `infrastructure/persistence/` a entidade JPA + Spring Data repo + adapter que implementa a porta (mapeamento via MapStruct). | `persistencia-jpa` |
| **sqs-listener** | Listener (driving adapter) em `infrastructure/messaging/` com idempotência em memória, **interceptor central de erro de consumo** (`infrastructure/messaging/*ErrorInterceptor`) e **fila provisionada com DLQ + `RedrivePolicy`** (nunca uma sem a outra). | `mensageria-sqs-kafka` (seções 2 e 3) |
| **sqs-para-banco** | Como acima + idempotência **persistente** (constraint única) + gravação via `port/out` e adapter JPA. | `mensageria-sqs-kafka`, `persistencia-jpa` |
| **sqs-para-kafka** | Ponte: consome SQS (interceptor + DLQ, como acima) e republica no Kafka através de uma `port/out` implementada por um producer em `infrastructure/messaging/`. | `mensageria-sqs-kafka` |
| **kafka-consumer** | `@KafkaListener` em `infrastructure/messaging/` + `DefaultErrorHandler`/`DeadLetterPublishingRecoverer` central (o ponto único de erro é o próprio `DefaultErrorHandler`, configurado em `infrastructure/config/`). | `mensageria-sqs-kafka` (seções 3 e 5) |
| **rest-para-kafka** | Endpoint REST (`POST /eventos`) que chama um use case, o qual publica pela `port/out` implementada em `infrastructure/messaging/`. | `mensageria-sqs-kafka` (seção 4) |

> Em toda variante, o adaptador **nunca** conversa com outro adaptador: a entrada chama uma `port/in`
> e a saída é sempre uma `port/out` declarada no `domain`.

**Toda variante que envolva SQS SHALL nascer com DLQ na fila e com o interceptor central de erro de
consumo** — não é opcional, é parte da definição da variante (ver regra de ouro em
`mensageria-sqs-kafka` seção 2 e o padrão da seção 3).

## Fluxo de geração

1. **Gerar a base**: estrutura `domain`/`application`/`infrastructure` (ver "Layout gerado"), classe
   principal, rota `/disponibilidade` atendida via `port/in`, tratamento de erro genérico e teste de
   contexto — seguindo `arquitetura-limpa-java`. Aplique o container web escolhido no `pom.xml`.

2. **Aplicar a variante**, se houver: gere os componentes obrigatórios da tabela acima, seguindo a
   skill de referência indicada. Para variantes com SQS, provisione a fila com DLQ (IaC local, ex.
   Terraform contra o emulador) e implemente o interceptor central de erro **no mesmo passo** — não
   deixe para depois.

3. **Buildar**: `mvn clean package`. Use `-DskipTests` quando a variante exigir infraestrutura externa
   no ar para os testes de contexto passarem (ex.: variantes com `@SqsListener`/SDK SQS exigem o
   emulador rodando com a fila já criada).

4. **Smoke test**: suba a aplicação (`mvn spring-boot:run`) e confirme `GET /disponibilidade`
   respondendo `{"aplicacao":"<nome>","status":"DISPONIVEL"}`.

5. **Validação obrigatória**: invoque o agent `java-revisor` (modo `auditoria`), passando a lista de arquivos
   gerados e a saída do build. Quando a aplicação tocar mensageria, o agent valida também DLQ e
   interceptor central (ver `mensageria-sqs-kafka` seção 8). Achados **críticos** bloqueiam a
   entrega — corrija e revalide antes de considerar a tarefa concluída.

## Delegação

Quando o pedido ocorrer dentro de um contexto de trabalho maior, a **geração** (passos 1–4) pode ser
delegada ao agent `java-construtor`. A **validação final** (passo 5) é sempre responsabilidade do
agent `java-revisor` (modo `auditoria`), independentemente de quem gerou os arquivos.

## Erros comuns

| Sintoma | Causa / correção |
|---------|-------------------|
| App não sobe: "Failed to configure a DataSource" | Incluiu `spring-boot-starter-data-jpa` sem o banco no ar. Suba o banco ou remova a dependência se não for usá-la. |
| `mvnw.cmd` quebrado/falha no Windows | Use `mvn` diretamente em vez do wrapper. |
| Plugin do Spring Boot não compila `void main()` | Use `public static void main(String[] args)` — o plugin ainda não suporta o `void main()` do JDK 25. |
| Porta ocupada | Escolha uma porta livre ou pare o processo conflitante. |
| Container web errado no log de startup | Confira se a exclusão do Tomcat + starter Jetty foi aplicada corretamente no `pom.xml`. |
| `NoSuchBeanDefinitionException` para `ObjectMapper` (variantes SQS/Kafka com Jackson) | Spring Boot 4 usa Jackson 3 por padrão e não cria um `ObjectMapper` clássico automaticamente — declare o bean explicitamente. |
| `mvn test` completo falha com `SdkClientException: Connection refused` | Variante com SQS exige o emulador rodando e a fila já criada antes de rodar a suíte completa — não é bug. |

## Checklist final

- [ ] Os 6 parâmetros foram confirmados com o usuário (pasta, nome, porta, profile, container web, variante)
- [ ] `mvn clean package` (ou `mvn test` completo, com a infra da variante no ar) passou
- [ ] Rota `GET /disponibilidade` responde com o nome correto da aplicação
- [ ] Estrutura hexagonal clássica (`domain` com `model`/`port/in`/`port/out`, `application/usecase`,
      `infrastructure` com os adapters) presente e completa
- [ ] `domain` sem nenhum import de `org.springframework.*`, `jakarta.persistence.*` ou Jackson
- [ ] Todo adapter de saída implementa uma `port/out`; nenhum use case injeta `JpaRepository`,
      `RestClient` ou SDK de broker diretamente
- [ ] Se a variante envolve SQS: fila tem DLQ + `RedrivePolicy`, e existe interceptor central de erro
- [ ] Veredicto do agent `java-revisor` (modo `auditoria`) sem achados críticos
