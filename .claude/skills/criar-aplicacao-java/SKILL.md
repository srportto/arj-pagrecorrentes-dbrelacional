---
name: criar-aplicacao-java
description: Use quando o usuário pedir para criar uma aplicação, microserviço ou esqueleto Java/Spring Boot em qualquer variante - REST puro, CRUD com banco, listener de fila SQS, fila para Kafka, fila para banco, consumidor Kafka ou REST que publica em Kafka. Gatilhos - "crie uma aplicação", "novo microserviço", "esqueleto de app java", "app que consome fila", "consumidor kafka". Uso: agents `java-construtor`/`java-especialista` ou invocação manual via `/criar-aplicacao-java`; não deve ser carregada proativamente pela sessão principal.
---

# Criar Aplicação Java (Spring Boot, hexagonal)

## Visão geral

Gera uma aplicação Spring Boot **buildável** seguindo o esqueleto hexagonal (camadas `entrypoint` /
`application` / `domain` / `shared`, ver `arquitetura-limpa-java`) com uma rota de disponibilidade
pronta, combinada, opcionalmente, com uma **variante** que adiciona a funcionalidade pedida (CRUD com
banco, consumo de fila, ponte de mensageria, etc.). Não há templates físicos para copiar neste
catálogo — cada aplicação é gerada do zero seguindo os requisitos desta skill e das skills
referenciadas (`arquitetura-limpa-java`, `mensageria-sqs-kafka`, `persistencia-jpa`, `java-moderno`).

**Princípio central:** a base (sem variante) roda **sem depender de nenhuma infraestrutura externa**
— nem banco, nem fila, nem broker. É o "esqueleto" seguro para começar qualquer aplicação. Cada
variante é quem introduz (e exige) a infraestrutura que passa a ser necessária.

A base entrega sempre:
- Pacote `br.com.srportto.<nome>`, classe principal `<Nome>Application`
- Rota `GET /disponibilidade` → `200 OK`, corpo `{"aplicacao":"<nome>","status":"DISPONIVEL"}`
- Tratamento de erros (`BusinessException` → 422, `ApplicationException` → 500, validação de bean)
- Teste de contexto (`@SpringBootTest`) que sobe sem infra externa

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
| **rest-crud-banco** | JPA/Hibernate + PostgreSQL, `@RestController` + `@Service` + `Repository`, mapeamento via MapStruct. | `persistencia-jpa` |
| **sqs-listener** | Listener em `entrypoint/sqs/` com idempotência em memória, **interceptor central de erro de consumo** (`entrypoint/sqs/*ErrorInterceptor`) e **fila provisionada com DLQ + `RedrivePolicy`** (nunca uma sem a outra). | `mensageria-sqs-kafka` (seções 2 e 3) |
| **sqs-para-banco** | Como acima + idempotência **persistente** (constraint única) + gravação JPA. | `mensageria-sqs-kafka`, `persistencia-jpa` |
| **sqs-para-kafka** | Ponte: consome SQS (interceptor + DLQ, como acima) e republica no Kafka via porta/adapter de saída em `application/`. | `mensageria-sqs-kafka` |
| **kafka-consumer** | `@KafkaListener` + `DefaultErrorHandler`/`DeadLetterPublishingRecoverer` central (o ponto único de erro é o próprio `DefaultErrorHandler`, configurado em `shared/config/`). | `mensageria-sqs-kafka` (seções 3 e 5) |
| **rest-para-kafka** | Endpoint REST (`POST /eventos`) que publica no Kafka via adapter de saída em `application/`. | `mensageria-sqs-kafka` (seção 4) |

**Toda variante que envolva SQS SHALL nascer com DLQ na fila e com o interceptor central de erro de
consumo** — não é opcional, é parte da definição da variante (ver regra de ouro em
`mensageria-sqs-kafka` seção 2 e o padrão da seção 3).

## Fluxo de geração

1. **Gerar a base**: estrutura `entrypoint`/`application`/`domain`/`shared`, classe principal, rota
   `/disponibilidade`, tratamento de erro genérico e teste de contexto — seguindo
   `arquitetura-limpa-java`. Aplique o container web escolhido no `pom.xml`.

2. **Aplicar a variante**, se houver: gere os componentes obrigatórios da tabela acima, seguindo a
   skill de referência indicada. Para variantes com SQS, provisione a fila com DLQ (IaC local, ex.
   Terraform contra o emulador) e implemente o interceptor central de erro **no mesmo passo** — não
   deixe para depois.

3. **Buildar**: `mvn clean package`. Use `-DskipTests` quando a variante exigir infraestrutura externa
   no ar para os testes de contexto passarem (ex.: variantes com `@SqsListener`/SDK SQS exigem o
   emulador rodando com a fila já criada).

4. **Smoke test**: suba a aplicação (`mvn spring-boot:run`) e confirme `GET /disponibilidade`
   respondendo `{"aplicacao":"<nome>","status":"DISPONIVEL"}`.

5. **Validação obrigatória**: invoque o agent `java-especialista`, passando a lista de arquivos
   gerados e a saída do build. Quando a aplicação tocar mensageria, o agent valida também DLQ e
   interceptor central (ver `mensageria-sqs-kafka` seção 8). Achados **críticos** bloqueiam a
   entrega — corrija e revalide antes de considerar a tarefa concluída.

## Delegação

Quando o pedido ocorrer dentro de um contexto de trabalho maior, a **geração** (passos 1–4) pode ser
delegada ao agent `java-construtor`. A **validação final** (passo 5) é sempre responsabilidade do
agent `java-especialista`, independentemente de quem gerou os arquivos.

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
- [ ] Estrutura hexagonal (`entrypoint`/`application`/`domain`/`shared`) presente e completa
- [ ] Se a variante envolve SQS: fila tem DLQ + `RedrivePolicy`, e existe interceptor central de erro
- [ ] Veredicto do agent `java-especialista` sem achados críticos
