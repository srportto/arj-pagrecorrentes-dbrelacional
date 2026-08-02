---
name: padrao-de-logs-java
description: Use quando o usuário pedir para adicionar ou melhorar logs, padronizar logging, configurar logs JSON estruturados, correlacionar requisições com MDC/traceId, ou depurar o fluxo da aplicação pelos logs. Gatilhos - "adicione logs", "melhore os logs", "log estruturado", "traceId", "correlação".
---

# Padrão de Logs Java

## Visão geral

Padrão único de logging para aplicações Java/Spring Boot hexagonais neste projeto: SLF4J como única
API de log, JSON estruturado por padrão (já ativo no app-base), correlação via MDC/`traceId`, e um
critério objetivo de nível e de "o que logar" por camada. Use esta skill sempre que for adicionar
logs a um código novo, revisar logs existentes, configurar formato/nível, ou montar a correlação de
uma requisição/mensagem ponta a ponta.

**Quando NÃO usar:** para o checklist completo de revisão de código (do qual logs é só um item), use
`revisao-de-codigo-java` — ela referencia esta skill na seção "Logs". Para dúvida sobre em qual
camada uma classe deve viver, use `arquitetura-limpa-java`; esta skill assume o mesmo modelo de
camadas (`entrypoint` / `application` / `domain` / `shared`) só para decidir o que logar em cada uma.
Para uma auditoria de logging de uma aplicação inteira (não só um trecho), invoque o agent
`java-revisor`.

## 1. Regras de ouro

- **SLF4J sempre, nunca `System.out`/`System.err`** — `System.out.println` não tem nível, não vai para
  o pipeline de log estruturado e não aparece em nenhuma ferramenta de observabilidade.
- **Placeholders `{}`, nunca concatenação** — `log.info("pedido {}", id)`, nunca
  `log.info("pedido " + id)`. Concatenação roda sempre, mesmo com o nível desligado; placeholder só
  monta a string se o nível estiver habilitado.
- **Logger `private static final`** — uma instância por classe, nomeada `log`, criada com
  `LoggerFactory.getLogger(NomeDaClasse.class)`.
- **NUNCA logar (em nenhum nível, nem `debug`):**
  - senha, hash de senha, PIN;
  - token de autenticação, JWT, API key, secret, chave de assinatura;
  - CPF, CNPJ, número de cartão de crédito, CVV;
  - dado pessoal completo que identifique alguém sem necessidade (nome completo + endereço + telefone
    juntos, por exemplo) — prefira um id de negócio (`pedidoId`, `usuarioId`);
  - corpo bruto de payload de terceiro sem mascarar, quando esse payload pode conter qualquer um dos
    itens acima.

```java
// ERRADO - System.out, concatenacao, sem placeholder, senha no log
System.out.println("Login do usuario " + usuario.email() + " com senha " + usuario.senha());
```

```java
// CORRETO - SLF4J, placeholder, logger private static final, sem dado sensivel
private static final Logger log = LoggerFactory.getLogger(LoginService.class);
// ...
log.info("login realizado usuarioId={}", usuario.id());
```

## 2. JSON estruturado por padrão

Este projeto já sai com log estruturado em JSON habilitado, para todo ambiente (inclusive `local`) —
não é preciso adicionar nenhuma dependência, é suporte nativo do Spring Boot (3.4+; este projeto usa
Boot 4):

```yaml
# .claude/skills/criar-aplicacao-java/assets/app-base/src/main/resources/application.yaml
logging:
    structured:
        format:
            console: logstash   # logs JSON estruturados (padrao da skill padrao-de-logs-java)
```

### Por que JSON em vez de texto

```
# Texto - uma ferramenta (ou uma IA) precisa "interpretar" a string
2026-08-01 10:15:30 INFO ProcessarPedidoService - pedido processado id=abc-123 valor=99.90

# JSON - campos acessiveis diretamente, sem parsing ad-hoc
{"@timestamp":"2026-08-01T10:15:30.123-03:00","level":"INFO","logger_name":"br.com.srportto.appbase.application.pedido.ProcessarPedidoService","message":"pedido processado id=abc-123 valor=99.90","traceId":"9f1c3e2a-6b7d-4e11-9a2f-1234567890ab"}
```

| Aspecto | Texto | JSON |
|---|---|---|
| Parsing | Regex / interpretação da string | Acesso direto ao campo (`jq`, query de log) |
| Uso de tokens (análise por IA) | Maior — padrão repetido em texto livre precisa ser reinterpretado a cada linha | Menor — estrutura já separa o que é campo do que é texto |
| Extração de erro | Recortar a stack trace do meio do texto | Campo `stack_trace`/`exception` isolado |
| Filtragem / correlação | `grep` por substring, frágil a mudança de formato | `jq 'select(.traceId == "...")'`, robusto |
| Agregação (contagem de erros, latência) | Precisa de parser customizado | Direto por ferramenta de log (campo `level`, `duration_ms`) |

**Importante — o que realmente vira campo JSON:** os placeholders `{}` de `log.info("pedido
processado id={} valor={}", id, valor)` viram parte do texto do campo `message`, não campos JSON
separados (isso exigiria a biblioteca `logstash-logback-encoder` com `StructuredArguments.kv()`, que
este projeto não usa). Quem vira campo JSON de verdade, automaticamente, é **tudo que está no MDC**
(seção 4) — por isso um id que você precisa filtrar/agrupar entre várias linhas de log (`traceId`)
deve ir para o MDC, e não só para dentro do texto da mensagem.

Se precisar de um campo estruturado pontual sem passar pelo MDC, o SLF4J 2.x (já usado pelo Spring
Boot 4) tem uma API fluente sem dependência extra:

```java
// campo extra como chave/valor real no JSON, sem precisar de biblioteca adicional
log.atInfo()
        .setMessage("pedido processado")
        .addKeyValue("pedidoId", pedido.id())
        .addKeyValue("valor", pedido.valor())
        .log();
```

Use isso só quando o campo separado importa de verdade (dashboard, query); para o caso comum, o
padrão já usado no projeto — `log.info("pedido processado id={} valor={}", pedido.id(),
pedido.valor())`, como em `ProcessarPedidoService` — é suficiente e mais simples.

### Como ler os logs como humano em dev

O dia a dia mais simples é filtrar o JSON com `jq`, sem mexer em nenhuma configuração:

```bash
mvn spring-boot:run | jq .
# ou, olhando so os erros de um traceId especifico
tail -f app.log | jq 'select(.traceId == "9f1c3e2a-6b7d-4e11-9a2f-1234567890ab")'
```

Se quiser mesmo trocar o formato do console para texto legível por profile: a propriedade
`logging.structured.format.console` hoje **não tem** uma forma documentada de ser desligada por
profile (é uma limitação conhecida e em aberto do Spring Boot — issue
[#45407](https://github.com/spring-projects/spring-boot/issues/45407)). A forma suportada é um
`logback-spring.xml` próprio, trocando o appender por `<springProfile>`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <!-- perfil log-humano: texto legivel no console -->
    <springProfile name="log-humano">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>${CONSOLE_LOG_PATTERN}</pattern>
            </encoder>
        </appender>
    </springProfile>

    <!-- demais perfis: mantem o JSON estruturado do Spring Boot -->
    <springProfile name="!log-humano">
        <include resource="org/springframework/boot/logging/logback/structured-console-appender.xml"/>
    </springProfile>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

```bash
# ativa junto com o profile local, so quando precisar ler no terminal sem jq
mvn spring-boot:run -Dspring-boot.run.profiles=local,log-humano
```

## 3. Níveis

| Nível | Critério objetivo | Exemplo | Config por ambiente |
|---|---|---|---|
| `ERROR` | Algo quebrou e exige ação/investigação humana; a operação não foi concluída | `log.error("Falha ao salvar pedido {}", pedido.id(), e)` no handler central | Sempre habilitado, em todo ambiente |
| `WARN` | Situação anormal, mas recuperável — não interrompe o fluxo | `log.warn("Retry {} de {} ao chamar integracao", tentativa, maxTentativas)` | Sempre habilitado, em todo ambiente |
| `INFO` | Evento de negócio relevante, marco do fluxo — o que aconteceu, não como | `log.info("pedido processado id={} valor={}", pedido.id(), pedido.valor())` (`ProcessarPedidoService`) | `root: INFO` em todo ambiente |
| `DEBUG` | Detalhe técnico útil só durante investigação — verboso, granular | `log.debug("payload bruto recebido: {}", mensagemJson)` | Pacote da aplicação (`br.com.srportto.appbase`) em `DEBUG` **só** no profile `local`; nunca em produção |

Config de exemplo (documento YAML separado por `---`, seguindo a convenção de profile do
`application.yaml` do app-base):

```yaml
logging:
    level:
        root: INFO
---
spring:
    config:
        activate:
            on-profile: local
logging:
    level:
        br.com.srportto.appbase: DEBUG   # verboso so em local; nunca habilitar em hml/prod
```

## 4. MDC e correlação

MDC (`Mapped Diagnostic Context`, do SLF4J) é um contexto por thread: tudo que você coloca nele com
`MDC.put(chave, valor)` aparece automaticamente como campo de nível superior no JSON de todas as
linhas de log emitidas naquela thread, sem precisar repetir o valor em cada chamada de `log.info`.
Combinado com `logging.structured.format.console: logstash` (já ativo, seção 2), isso não exige
nenhuma configuração extra — é o próprio Spring Boot que inclui o MDC no JSON de saída.

O filtro abaixo popula um `traceId` no MDC assim que a requisição HTTP chega (na borda, camada
`entrypoint`), reaproveita um `traceId` recebido de outro serviço via header quando existe (para
correlacionar chamadas entre serviços), e sempre limpa o MDC no `finally` — sem isso, como o servlet
container reaproveita threads de um pool, um `traceId` pode vazar para a próxima requisição
processada pela mesma thread:

```java
package br.com.srportto.appbase.shared.filters;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

// popula traceId no MDC para toda a requisicao; o campo aparece sozinho no log JSON (logstash)
@Component
public class TraceIdFilter implements Filter {

    private static final String CABECALHO_TRACE_ID = "X-Trace-Id";
    private static final String CHAVE_MDC = "traceId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String recebido = ((HttpServletRequest) request).getHeader(CABECALHO_TRACE_ID);
        String traceId = (recebido != null && !recebido.isBlank()) ? recebido : UUID.randomUUID().toString();
        MDC.put(CHAVE_MDC, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            // MDC e por thread; sem clear() o valor vaza para a proxima requisicao no mesmo pool
            MDC.clear();
        }
    }
}
```

`@Component` é suficiente — o Spring Boot registra automaticamente qualquer bean `Filter` na cadeia
de filtros da aplicação, sem precisar de `FilterRegistrationBean`.

**Entrypoints sem servlet (SQS/Kafka):** listeners como `PedidoSqsListener` não passam por essa
cadeia de filtro HTTP. O mesmo padrão se aplica manualmente no início do método do listener —
`MDC.put("traceId", ...)` (gerado ou lido de um atributo da mensagem) antes de chamar o service, e
`MDC.clear()` no `finally` — pelo mesmo motivo: threads de consumer também são reaproveitadas de um
pool.

## 5. O que logar em cada camada hexagonal

| Camada | O que logar | O que NÃO logar aqui |
|---|---|---|
| `entrypoint` | Chegada e saída da requisição/mensagem; `traceId` gerado ou recebido; status HTTP retornado ou confirmação de consumo da mensagem | Corpo completo de payload sensível; stack trace de exceção (isso é do handler, ver `shared`) |
| `application` | Decisões de negócio relevantes e seus ids (`"pedido duplicado ignorado id={}"`, `"pedido processado id={} valor={}"` — como em `ProcessarPedidoService`) | Log em todo método só por rotina; dado pessoal/sensível (regra de ouro, seção 1) |
| `domain` | **Nada.** Domínio é puro — não importa SLF4J, não conhece logging, é testável sem subir nenhum contexto de log | Qualquer log — se uma regra de domínio "precisa" logar, o log pertence a quem chama, na `application` |
| `shared` (handler central, ex. `ApiExceptionHandler`) | A exceção completa, com stack trace, **uma única vez**, no ponto central de tratamento, antes de montar a resposta de erro | Logar a mesma exceção de novo em outro lugar do fluxo — ver "log duplicado" na seção 6 |

## 6. Erros comuns

### Log duplicado (log e relança — "log-and-rethrow")

```java
// ERRADO - loga no service E de novo no handler central: o mesmo erro aparece duas vezes no log
try {
    pedidoRepository.save(pedido);
} catch (Exception e) {
    log.error("Erro ao salvar pedido {}", pedido.id(), e);
    throw new ApplicationException("Falha ao salvar pedido", e);
}
```

```java
// CORRETO - so relanca com contexto; quem loga (uma vez, com stack trace) e o handler central (shared)
try {
    pedidoRepository.save(pedido);
} catch (Exception e) {
    throw new ApplicationException("Falha ao salvar pedido " + pedido.id(), e);
}
```

### Log dentro de loop quente

```java
// ERRADO - um log.info por item: para 10 mil itens, 10 mil linhas de log na mesma operacao
for (Produto produto : produtos) {
    log.info("Processando produto {}", produto.id());
    processar(produto);
}
```

```java
// CORRETO - um log antes e um depois do loop, com o total; detalhe por item so em debug, sob guarda
log.info("Iniciando processamento de {} produtos", produtos.size());
for (Produto produto : produtos) {
    if (log.isDebugEnabled()) {
        log.debug("Processando produto {}", produto.id());
    }
    processar(produto);
}
log.info("Processamento concluido: {} produtos", produtos.size());
```

### `e.printStackTrace()`

```java
// ERRADO - vai para stdout sem nivel, sem timestamp, fora do JSON; invisivel para qualquer ferramenta
try {
    carregarConfiguracao();
} catch (IOException e) {
    e.printStackTrace();
}
```

```java
// CORRETO - vira um evento ERROR estruturado, com a stack trace dentro do campo de excecao do JSON
try {
    carregarConfiguracao();
} catch (IOException e) {
    log.error("Falha ao ler arquivo de configuracao", e);
}
```

### Concatenação ansiosa (eager)

Placeholder evita a concatenação de string, mas **não** evita a avaliação do argumento em si — o Java
avalia os argumentos antes de chamar `log.debug(...)`, então uma chamada cara (serialização, outro
método pesado) roda de qualquer forma, mesmo com `DEBUG` desligado, se estiver como argumento direto:

```java
// ERRADO - a concatenacao E a serializacao rodam sempre, mesmo com DEBUG desligado
log.debug("Payload recebido: " + objectMapper.writeValueAsString(pedido));
```

```java
// CORRETO - argumento simples (toString barato): placeholder sozinho ja resolve
log.debug("Processando pedido {} valor {}", pedido.id(), pedido.valor());

// CORRETO - argumento caro (serializacao): guarda com isDebugEnabled() alem do placeholder
if (log.isDebugEnabled()) {
    log.debug("Payload recebido: {}", objectMapper.writeValueAsString(pedido));
}
```

## Skills e agents relacionados

| Situação | Use |
|---|---|
| Checklist completo de revisão de código (logs é um item entre vários) | skill `revisao-de-codigo-java` |
| Dúvida sobre em qual camada uma classe/log deve viver | skill `arquitetura-limpa-java` |
| Auditoria de logging de uma aplicação inteira, não só um trecho pontual | agent `java-revisor` |
