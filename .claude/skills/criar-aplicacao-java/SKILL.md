---
name: criar-aplicacao-java
description: Use quando o usuário pedir para criar uma aplicação, microserviço ou esqueleto Java/Spring Boot em qualquer variante - REST puro, CRUD com banco, listener de fila SQS, fila para Kafka, fila para banco, consumidor Kafka ou REST que publica em Kafka. Gatilhos - "crie uma aplicação", "novo microserviço", "esqueleto de app java", "app que consome fila", "consumidor kafka".
---

# Criar Aplicação Java (Spring Boot, hexagonal)

## Visão geral

Gera uma aplicação Spring Boot **buildável** a partir de `assets/app-base/` — um esqueleto hexagonal
(camadas `entrypoint` / `application` / `domain` / `shared`) com uma rota de disponibilidade pronta —
combinada, opcionalmente, com um **overlay** de `assets/overlays/<variante>/` que adiciona a
funcionalidade pedida (CRUD com banco, consumo de fila, ponte de mensageria, etc.).

**Princípio central:** a base (`app-base`, sem overlay) roda **sem depender de nenhuma infraestrutura
externa** — nem banco, nem fila, nem broker. É o "esqueleto" seguro para começar qualquer aplicação. Cada
overlay é quem introduz (e documenta) a infraestrutura que passa a ser necessária.

A base entrega:
- Pacote `br.com.srportto.appbase`, classe principal `AppbaseApplication`
- Rota `GET /disponibilidade` → `200 OK`, corpo `{"aplicacao":"<nome>","status":"DISPONIVEL"}`
- Tratamento de erros (`BusinessException` → 422, `ApplicationException` → 500, validação de bean)
- Teste de contexto (`@SpringBootTest`) que sobe sem infra externa

## Quando usar / Quando NÃO usar

**Use esta skill quando** o pedido for para **criar uma aplicação/microserviço nova do zero** — REST
puro, CRUD com banco, listener de fila SQS, ponte SQS→banco, ponte SQS→Kafka, consumidor Kafka, ou REST
que publica em Kafka.

**NÃO use esta skill para:**
- Adicionar uma feature/endpoint/entidade em uma aplicação **já existente** — use a skill
  `arquitetura-limpa-java`.
- Tirar dúvidas sobre mensageria (SQS/Kafka) sem a intenção de criar uma aplicação nova — use a skill
  `mensageria-sqs-kafka`.

## SEMPRE pergunte antes de gerar

Colete os 6 parâmetros abaixo do usuário antes de gerar qualquer arquivo:

| Parâmetro | Uso | Exemplo |
|-----------|-----|---------|
| **Nome da pasta destino** | diretório onde o projeto será gerado | `pedidos-service` |
| **Nome da aplicação** | deriva `artifactId`, pacote `br.com.srportto.<nome>`, classe `<Nome>Application`, `spring.application.name` | `pedidos` |
| **Porta** | `server.port` | `8081` |
| **Profile default** | `spring.profiles.default` | `local` |
| **Container web** | servidor embutido no `pom.xml` (Tomcat default, ou Jetty) — ver tabela abaixo | `Jetty` |
| **Variante** | base pura ou um dos 6 overlays — ver tabela abaixo | `sqs-listener` |

> Derive os identificadores do "nome da aplicação": pacote = `br.com.srportto.<nome>` (minúsculo),
> classe principal = `<Nome>Application` (PascalCase).

### Container web

O `spring-boot-starter-webmvc` traz o **Tomcat** por padrão. Apresente este resumo ao usuário:

| Container | Resumo de benefícios | Quando preferir |
|-----------|----------------------|------------------|
| **Tomcat** | Padrão do Spring Boot, mais maduro e documentado; maior base de comunidade e troubleshooting; integração "out-of-the-box" sem exclusões. | Default seguro; time sem requisito específico; máxima compatibilidade. |
| **Jetty** | Leve e flexível/embarcável; footprint enxuto; forte em WebSocket e long-lived connections; bastante configurável. | Cloud-native/containers, alta concorrência, muitos WebSockets/streaming, tuning fino. |

> ⚠️ **Undertow NÃO existe no Spring Boot 4.x** (o BOM gerencia apenas Tomcat e Jetty para web MVC, mais
> reactor-netty para reativo). Tentar `spring-boot-starter-undertow` falha com "version is missing". Só
> use Undertow se o usuário exigir explicitamente um downgrade para Spring Boot 3.x — fora do escopo
> desta skill.

**Tomcat (default)** — nenhuma alteração; mantenha apenas `spring-boot-starter-webmvc`.

**Jetty** — exclua o Tomcat do starter web e adicione o starter do Jetty:
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

> Após trocar o container, valide no log de startup a linha do servidor ativo (ex.: `Jetty started on
> port <porta>` em vez de `Tomcat started on port <porta>`).

### Variante

| Variante | Descrição | Reference |
|----------|-----------|-----------|
| **base pura** | Só a base hexagonal, sem overlay — rota `/disponibilidade`, sem infraestrutura externa. | (nenhum — só `assets/app-base/`) |
| **rest-crud-banco** | CRUD REST completo com JPA/Hibernate + PostgreSQL, mapeamento via MapStruct. | [references/variante-rest-crud-banco.md](references/variante-rest-crud-banco.md) |
| **sqs-listener** | Consome uma fila AWS SQS com idempotência em memória, via `@SqsListener` (LocalStack local). | [references/variante-sqs-listener.md](references/variante-sqs-listener.md) |
| **sqs-para-banco** | Consome uma fila SQS e grava em PostgreSQL, com idempotência **persistente** (constraint única). | [references/variante-sqs-para-banco.md](references/variante-sqs-para-banco.md) |
| **sqs-para-kafka** | Ponte de eventos: consome uma fila SQS e republica no Kafka. | [references/variante-sqs-para-kafka.md](references/variante-sqs-para-kafka.md) |
| **kafka-consumer** | Consome um tópico Kafka com consumer group, retry e Dead Letter Topic (DLT) automático. | [references/variante-kafka-consumer.md](references/variante-kafka-consumer.md) |
| **rest-para-kafka** | Endpoint REST (`POST /eventos`) que publica eventos no Kafka. | [references/variante-rest-para-kafka.md](references/variante-rest-para-kafka.md) |

## Fluxo de geração

1. **Copiar a base**: copie `assets/app-base/` inteiro para a pasta destino (exceto `target/`, que é
   artefato de build e não deve ser copiado).

2. **Renomear pacote/classe/artifactId/name** conforme os parâmetros coletados. Especificamente:
   - Renomeie a pasta de pacote `br/com/srportto/appbase` → `br/com/srportto/<nome>` (em `src/main/java`
     e `src/test/java`).
   - Atualize as declarações `package br.com.srportto.appbase;` → `package br.com.srportto.<nome>;` e
     todos os `import br.com.srportto.appbase....;` correspondentes em todos os arquivos `.java`.
   - Renomeie a classe `AppbaseApplication` (arquivo e declaração `class`) → `<Nome>Application`
     (PascalCase), e o teste `AppbaseApplicationTests` → `<Nome>ApplicationTests`.
   - No `pom.xml`: `artifactId` e `name` de `appbase` → `<nome>`.
   - Em `application.yaml`: `spring.application.name` de `appbase` → `<nome>`; `server.port` para a
     porta escolhida; `spring.profiles.default` para o profile escolhido.
   - Aplique o container web escolhido (ver seção acima) no `pom.xml`.

3. **Se houver variante**, abra o `references/variante-<nome>.md` correspondente e siga o "Como aplicar"
   — que aponta para o `LEIAME.md` real do overlay em `assets/overlays/<nome>/LEIAME.md` como fonte
   canônica dos passos (copiar `src/`, mesclar `pom-fragmento.xml`, mesclar `application-fragmento.yaml`,
   subir infra). **Não invente conteúdo divergente do LEIAME** — em caso de dúvida, o LEIAME do overlay
   é a fonte da verdade, o reference é só o resumo.

   > **Rename obrigatório também nos arquivos copiados do overlay.** Todo `.java` de todos os overlays
   > usa `package br.com.srportto.appbase` (e `import br.com.srportto.appbase....;`) como placeholder —
   > isso é esperado, é o texto-fonte a ser substituído. Depois de copiar o `src/` do overlay por cima
   > do projeto (Passo 1 do LEIAME), **aplique nos arquivos recém-copiados o MESMO rename de pacote do
   > Passo 2 acima**: a substituição textual `br.com.srportto.appbase` → `br.com.srportto.<nome>` (pasta
   > de pacote, declaração `package` e todos os `import`) precisa cobrir **todos** os arquivos `.java` do
   > projeto final — tanto os do app-base quanto os copiados do overlay. Pular esse rename nos arquivos
   > do overlay deixa dois pacotes coexistindo (`appbase` e `<nome>`) e o projeto não compila.

4. **Buildar**: `mvn clean package`. Use `-DskipTests` quando a variante exigir infraestrutura externa no
   ar para os testes de contexto passarem (ex.: overlays com `@SqsListener` exigem LocalStack rodando com
   a fila já criada; overlays somente-Kafka producer/consumer sobem sem broker — ver o reference/LEIAME
   de cada variante para a exigência exata).

5. **Smoke test**: suba a aplicação (`java -jar target/<nome>-0.0.1-SNAPSHOT.jar` ou
   `mvn spring-boot:run`) e confirme `GET /disponibilidade` respondendo
   `{"aplicacao":"<nome>","status":"DISPONIVEL"}`.

6. **Validação obrigatória**: invoque o agent `java-especialista`, passando a lista de arquivos gerados
   e a saída do build (`mvn clean package`/`mvn test`). Achados **críticos** bloqueiam a entrega — corrija
   e revalide com o `java-especialista` antes de considerar a tarefa concluída.

## Delegação

Quando o pedido de criação da aplicação ocorrer dentro de um contexto de trabalho maior (ex.: parte de
uma tarefa maior que já está sendo executada por outro agent), a **geração** dos arquivos (passos 1–5)
pode ser delegada ao agent `java-construtor`. A **validação final** (passo 6) é sempre responsabilidade
do agent `java-especialista`, independentemente de quem gerou os arquivos.

## Erros comuns

| Sintoma | Causa / correção |
|---------|-------------------|
| App não sobe: "Failed to configure a DataSource" | Incluiu `spring-boot-starter-data-jpa` (ex.: overlay `rest-crud-banco`/`sqs-para-banco`) sem o banco no ar. Suba o `docker-compose.yml` do overlay ou remova a dependência se não for usá-la. |
| `mvnw.cmd` quebrado/falha no Windows | Use `mvn` diretamente em vez do wrapper. |
| Plugin do Spring Boot não compila `void main()` | Use `public static void main(String[] args)` — o plugin ainda não suporta o `void main()` "sem argumentos" do JDK 25. |
| Porta ocupada | Outra aplicação já está usando a porta escolhida. Escolha uma porta livre ou pare o processo conflitante. |
| Container web errado no log de startup | Log mostra `Tomcat started on port <porta>` quando o esperado era Jetty (ou vice-versa) — confira se a exclusão do Tomcat + starter do Jetty foi aplicada corretamente no `pom.xml` (ver seção "Container web"). |
| `NoSuchBeanDefinitionException` para `ObjectMapper` (overlays SQS/Kafka com Jackson) | Faltou o bean `JacksonJson2Config` — Spring Boot 4 usa Jackson 3 por padrão e não cria um `ObjectMapper` clássico automaticamente. Ver o LEIAME do overlay. |
| `mvn test` completo falha com `SdkClientException: Connection refused: localhost:4566` | Overlay com `@SqsListener` (sqs-listener, sqs-para-banco, sqs-para-kafka) exige LocalStack rodando e a fila já criada antes de rodar a suíte completa — não é bug. |

## Checklist final

- [ ] Os 6 parâmetros foram confirmados com o usuário (pasta, nome, porta, profile, container web, variante)
- [ ] `mvn clean package` (ou `mvn test` completo, quando a infra da variante está no ar) passou
- [ ] Rota `GET /disponibilidade` responde com o nome correto da aplicação
- [ ] Estrutura hexagonal (`entrypoint`/`application`/`domain`/`shared`) presente e completa
- [ ] Se houver variante: overlay aplicado por completo — `pom.xml` mesclado, `application.yaml`
      mesclado, `src/` copiado, `docker-compose.yml` do overlay considerado
- [ ] Veredicto do agent `java-especialista` sem achados críticos
