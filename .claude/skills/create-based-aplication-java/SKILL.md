---
name: create-based-aplication-java
description: Use ao criar uma nova aplicacao base REST em Java/Spring Boot dentro de "aplicacoes/" deste repositorio, seguindo o modelo arquitetural hexagonal de docs/arquitetura/based-java-aplication.md e usando arj-contratocommand como referencia. Aciona em pedidos como "crie uma aplicacao base", "novo microservico REST", "esqueleto de app java".
---

# Criar Aplicacao Base REST em Java (modelo arquitetural do projeto)

## Visao Geral

Gera uma aplicacao Spring Boot **buildavel e executavel** dentro de `aplicacoes/`, seguindo a
**arquitetura hexagonal** descrita em [docs/arquitetura/based-java-aplication.md](../../../docs/arquitetura/based-java-aplication.md)
e o contexto de [docs/arquitetura/contexto_arquitetural.md](../../../docs/arquitetura/contexto_arquitetural.md).
A referencia viva e a aplicacao [aplicacoes/arj-contratocommand](../../../aplicacoes/arj-contratocommand).

**Principio central:** o esqueleto base entrega as 4 camadas hexagonais (`entrypoint`, `application`,
`domain`, `shared`) + tratamento de erros, com uma rota de disponibilidade `GET /olamundo`, e **roda sem
depender de PostgreSQL**. Persistencia (JPA/Postgres) e plugada depois, quando houver entidade.

## SEMPRE pergunte antes de gerar

Colete os 4 parametros do usuario (use a ferramenta de perguntas). Sem eles, **nao gere**:

| Parametro | Uso | Exemplo |
|-----------|-----|---------|
| **Nome da pasta** | diretorio em `aplicacoes/<pasta>` | `arj-contratoquery` |
| **Nome da aplicacao** | `artifactId`, `spring.application.name`, pacote `br.com.srportto.<nome>`, classe `<Nome>Application` | `contratoquery` |
| **Porta** | `server.port` | `8081` (evite 8080 se contratocommand estiver no ar) |
| **Profile default** | `spring.profiles.active` | `dev` |
| **Container web** | servidor embutido no `pom.xml` (ver secao abaixo) | `Jetty` (Tomcat e o default; Undertow NAO existe no Spring Boot 4.0) |

> Derive os identificadores do "nome da aplicacao": pacote = `br.com.srportto.<nome>` (minusculo),
> classe principal = `<Nome>Application` (PascalCase).
>
> Ao perguntar o **container web**, apresente as 3 opcoes (Tomcat / Undertow / Jetty) com o resumo de
> beneficios da secao [Escolha do container web](#escolha-do-container-web) para auxiliar a decisao.

## Stack (alinhada ao modelo)

| Componente | Versao | Notas |
|---|---|---|
| Java | 25 | usa `public static void main` (o plugin Spring Boot ainda nao suporta `void main()` do JDK25) |
| Spring Boot | 4.0.4 | parent; starters `webmvc` + `validation` |
| Lombok | 1.18.40 | reduz boilerplate; via `annotationProcessorPaths` |
| Maven | 3.9+ | se `./mvnw.cmd` falhar no Windows, use `mvn` direto |

> Persistencia (opcional): `spring-boot-starter-data-jpa` + `postgresql` + MapStruct. **Nao** inclua no base
> puro — exige datasource ativo no startup e quebra o "executavel sem infra".

## Escolha do container web

O `spring-boot-starter-webmvc` traz o **Tomcat** por padrao. Para usar Jetty, **exclua** o
Tomcat do starter web e adicione o starter do Jetty. Apresente este resumo ao usuario:

| Container | Resumo de beneficios | Quando preferir |
|-----------|----------------------|-----------------|
| **Tomcat** | Padrao do Spring Boot, mais maduro e documentado; maior base de comunidade e troubleshooting; integracao "out-of-the-box" sem exclusoes. | Default seguro; time sem requisito especifico; maxima compatibilidade. |
| **Jetty** | Leve e flexivel/embarcavel; footprint enxuto; forte em WebSocket e long-lived connections; bastante configuravel. | Cloud-native/containers, alta concorrencia, muitos WebSockets/streaming, tuning fino. |
| **Undertow** | Leve e de alta performance (NIO/non-blocking). **Era** o padrao citado na arquitetura (apps ECS, ver contexto_arquitetural.md), porem **foi REMOVIDO no Spring Boot 4.0** — so disponivel via downgrade para 3.x. | Apenas se a stack for Spring Boot 3.x. |

> ⚠️ **Undertow nao existe no Spring Boot 4.0** (o BOM 4.0.x gerencia apenas Tomcat e Jetty para web MVC,
> mais reactor-netty para reativo). Tentar `spring-boot-starter-undertow` falha com "version is missing".
> Na stack 4.0.4 deste projeto, **recomende Jetty** como container leve (substituto mais proximo da
> intencao original de Undertow). So use Undertow se baixar o Spring Boot para 3.x.

### Como configurar cada container no pom.xml

**Tomcat (default)** — nenhuma alteracao; mantenha apenas `spring-boot-starter-webmvc`.

**Jetty** — exclua o Tomcat do starter web e adicione o Jetty:
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

**Undertow** — indisponivel no Spring Boot 4.0. So funciona apos downgrade para 3.x; nesse caso, mesma
mecanica do Jetty trocando o starter final por `spring-boot-starter-undertow`.

> Apos trocar o container, valide no log de startup a linha do servidor ativo (ex.: `Jetty started on
> port <porta>` em vez de `Tomcat started on port <porta>`) e mantenha o teste `GET /olamundo`.

## Estrutura gerada (hexagonal)

```
aplicacoes/<pasta>/
├── pom.xml
├── .gitignore
└── src
    ├── main
    │   ├── java/br/com/srportto/<nome>/
    │   │   ├── <Nome>Application.java          # entry point @SpringBootApplication
    │   │   ├── entrypoint/                      # adaptadores REST (controllers)
    │   │   │   └── OlamundoController.java
    │   │   ├── application/                     # orquestracao / casos de uso
    │   │   │   └── olamundo/OlamundoService.java
    │   │   ├── domain/                          # logica pura, sem frameworks
    │   │   │   └── model/SaudacaoOlamundo.java
    │   │   └── shared/                          # infra compartilhada
    │   │       ├── exceptions/{BusinessException,ApplicationException}.java
    │   │       └── interceptors/api/            # ApiExceptionHandler + DTOs de erro
    │   └── resources/application.yaml
    └── test/java/br/com/srportto/<nome>/<Nome>ApplicationTests.java
```

## Contrato da rota obrigatoria

`GET /olamundo` → `200 OK`, corpo: `salve quebrada, <nome-da-aplicacao> ON!!`
(ex.: `salve quebrada, contratoquery ON!!`). A mensagem deve usar `spring.application.name`,
montada no `domain` (record imutavel) e exposta pela camada `application`.

## Passo a passo

1. **Perguntar** os 5 parametros (incluindo o **container web** — mostre o resumo de beneficios).
2. **Ler a referencia**: `aplicacoes/arj-contratocommand` (pom, classe principal, controller, `shared/`)
   para copiar fielmente convencoes (pacote `br.com.srportto`, starter `webmvc`, layout de erros).
3. **Gerar os arquivos** substituindo `<nome>`/`<Nome>`/`<pasta>`/porta/profile e aplicando o
   container web escolhido no `pom.xml` (ver [Escolha do container web](#escolha-do-container-web)).
4. **Buildar**: `cd aplicacoes/<pasta> && mvn clean package` (deve passar com o teste de contexto).
5. **Executar e validar**:
   ```bash
   java -jar target/<nome>-0.0.1-SNAPSHOT.jar      # ou: mvn spring-boot:run
   curl http://localhost:<porta>/olamundo          # => salve quebrada, <nome> ON!!
   ```
   Confirme no log: profile ativo correto e `Tomcat started on port <porta>`.

## Templates essenciais

### pom.xml (base executavel sem DB)
```xml
<project ...>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.4</version>
  </parent>
  <groupId>br.com.srportto</groupId>
  <artifactId><nome></artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <properties>
    <java.version>25</java.version>
    <lombok.version>1.18.40</lombok.version>
  </properties>
  <dependencies>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
    <!-- container web: webmvc traz Tomcat por padrao. Para Undertow/Jetty, ver "Escolha do container web" -->
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webmvc</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
    <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><version>${lombok.version}</version><scope>provided</scope></dependency>
  </dependencies>
  <build><plugins>
    <plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin>
    <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId>
      <configuration><annotationProcessorPaths>
        <path><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><version>${lombok.version}</version></path>
      </annotationProcessorPaths></configuration>
    </plugin>
  </plugins></build>
</project>
```

### application.yaml
```yaml
spring:
    application:
        name: <nome>
    profiles:
        active: <profile>
server:
    port: <porta>
```

### domain / application / entrypoint (fluxo da rota)
```java
// domain/model/SaudacaoOlamundo.java  -> logica pura, record imutavel
public record SaudacaoOlamundo(String nomeAplicacao) {
    public String montarMensagem() { return "salve quebrada, " + nomeAplicacao + " ON!!"; }
}

// application/olamundo/OlamundoService.java
@Service
public class OlamundoService {
    private final String nomeAplicacao;
    public OlamundoService(@Value("${spring.application.name}") String nome) { this.nomeAplicacao = nome; }
    public String obterSaudacao() { return new SaudacaoOlamundo(nomeAplicacao).montarMensagem(); }
}

// entrypoint/OlamundoController.java
@RestController
@AllArgsConstructor
public class OlamundoController {
    private final OlamundoService olamundoService;
    @GetMapping("/olamundo")
    public ResponseEntity<String> olamundo() { return ResponseEntity.ok(olamundoService.obterSaudacao()); }
}
```

### shared (copiar de arj-contratocommand, trocando o pacote)
- `exceptions/BusinessException` (RuntimeException) → mapeada para **422**
- `exceptions/ApplicationException` (RuntimeException) → mapeada para **500**
- `interceptors/api/ApiExceptionHandler` (`@ControllerAdvice`): trata `BusinessException` (422),
  `ApplicationException` (500) e `MethodArgumentNotValidException` (validacao).
- DTOs de erro: `LayoutErrosApiResponse`, `LayoutErrosApiValidationsResponse`, `BodyOcorrenciasErrosValidations`.

### Teste de contexto
```java
@SpringBootTest
class <Nome>ApplicationTests {
    @Test void contextLoads() {}
}
```
> Sem JPA, `@SpringBootTest` sobe o contexto sem infra externa. **Com** JPA, anote `@Disabled`
> (como em `ContratocommandApplicationTests`) ou configure um datasource de teste.

## Convencoes (do modelo)

| Elemento | Padrao |
|----------|--------|
| Pacote base | `br.com.srportto.<nome>` |
| Controllers | `<Recurso>Controller` em `entrypoint/` |
| Services | `<Nome>Service` em `application/<contexto>/` |
| Domain | records imutaveis / logica pura, **sem** Spring |
| Exceptions | `BusinessException` (422), `ApplicationException` (500) em `shared/` |
| DTOs | records imutaveis |

## Erros comuns

| Sintoma | Causa / correcao |
|---------|------------------|
| App nao sobe: "Failed to configure a DataSource" | Incluiu `spring-boot-starter-data-jpa` sem DB. Remova do base ou configure datasource. |
| `./mvnw.cmd` falha no Windows | Use `mvn` diretamente (wrapper quebrado, ver CLAUDE.md). |
| Plugin nao compila `void main()` | Use `public static void main(String[] args)` no JDK 25 com Spring Boot plugin. |
| Porta ocupada | Outra app (ex.: contratocommand:8080) no ar. Escolha porta livre. |
| Mensagem `/olamundo` sem o nome | Garanta `spring.application.name` setado e injetado via `@Value`. |

## Checklist final (antes de concluir)

- [ ] 5 parametros confirmados com o usuario (inclui container web)
- [ ] `mvn clean package` passou
- [ ] App sobe com profile e porta corretos (conferir log)
- [ ] Container web escolhido ativo no log de startup (Tomcat/Undertow/Jetty)
- [ ] `GET /olamundo` retorna `salve quebrada, <nome> ON!!` com 200
- [ ] Estrutura hexagonal (entrypoint/application/domain/shared) presente
