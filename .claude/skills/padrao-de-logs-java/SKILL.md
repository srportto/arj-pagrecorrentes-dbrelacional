---

name: padrao-de-logs-java
description: "Single logging standard for Java/Spring Boot hexagonal applications — SLF4J as the only API, JSON structured by default, MDC correlation with `traceId`, and an objective criterion of level and \"what to log\" per layer. Use when the user asks to add/improve logs, configure structured JSON, or correlate requests via MDC. Uso: agents `engenheiro-seguranca` / `especialista-monitoramento` / `java-revisor` or manual invocation via `/padrao-de-logs-java`; não deve ser carregada proativamente pela sessão principal."
license: MIT
metadata:
  author: https://github.com/srportto/srportto
  co-author: https://github.com/Jeffallan/claude-skills
  version: "1.1.0"
  domain: logging
  triggers: adicione logs, melhore os logs, log estruturado, traceId, correlação, MDC, JSON, SLF4J
  role: reference
  scope: logging
  output-format: document
  related-skills: monitoramento-java, seguranca-aplicacao-java, revisao-de-codigo-java
---
---

# Padrão de Logs Java

## Visão geral

Padrão único de logging para aplicações Java/Spring Boot hexagonais neste projeto: SLF4J como única
API de log, JSON estruturado por padrão (já ativo no app-base), correlação via MDC/`traceId`, e um
critério objetivo de nível e de "o que logar" por camada.

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

// CORRETO - SLF4J, placeholder, logger private static final, sem dado sensivel
private static final Logger log = LoggerFactory.getLogger(LoginService.class);
// ...
log.info("login realizado usuarioId={}", usuario.id());
```

## 2. JSON estruturado por padrão

Este projeto já sai com log estruturado em JSON habilitado, para todo ambiente (inclusive `local`) —
não é preciso adicionar nenhuma dependência, é suporte nativo do Spring Boot (3.4+; este projeto usa
Boot 4). Toda aplicação nova gerada por `criar-aplicacao-java` deve nascer com este `application.yaml`:

```yaml
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

**Importante — o que realmente vira campo JSON:** os placeholders `{}` viram parte do texto do campo
`message`, não campos JSON separados (isso exigiria `logstash-logback-encoder` com
`StructuredArguments.kv()`, que este projeto não usa). Quem vira campo JSON de verdade,
automaticamente, é **tudo que está no MDC** (seção 4) — por isso um id que você precisa
filtrar/agrupar entre linhas de log (`traceId`) deve ir para o MDC, não só para o texto da mensagem.

Para um campo estruturado pontual sem passar pelo MDC, o SLF4J 2.x (já usado pelo Spring Boot 4) tem
uma API fluente sem dependência extra — use só quando o campo separado importa de verdade
(dashboard/query); no caso comum, `log.info("... id={} valor={}", id, valor)` já é suficiente:

```java
// campo extra como chave/valor real no JSON, sem precisar de biblioteca adicional
log.atInfo().setMessage("pedido processado")
        .addKeyValue("pedidoId", pedido.id())
        .addKeyValue("valor", pedido.valor())
        .log();
```

### Como ler os logs como humano em dev

O dia a dia mais simples é filtrar o JSON com `jq`, sem mexer em nenhuma configuração:

```bash
mvn spring-boot:run | jq .
# ou, olhando so os erros de um traceId especifico
tail -f app.log | jq 'select(.traceId == "9f1c3e2a-6b7d-4e11-9a2f-1234567890ab")'
```

A propriedade `logging.structured.format.console` hoje **não tem** forma documentada de ser
desligada por profile (limitação conhecida e em aberto do Spring Boot — issue
[#45407](https://github.com/spring-projects/spring-boot/issues/45407)). Se precisar mesmo de texto
legível por profile, a forma suportada é um `logback-spring.xml` próprio com `<springProfile
name="log-humano">` trocando o appender `CONSOLE` para um `ConsoleAppender` com `${CONSOLE_LOG_PATTERN}`,
e `<springProfile name="!log-humano">` incluindo `structured-console-appender.xml` (o JSON padrão) —
ativado com `mvn spring-boot:run -Dspring-boot.run.profiles=local,log-humano`.

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
linhas de log emitidas naquela thread, sem repetir o valor em cada chamada de `log.info`. Combinado
com `logging.structured.format.console: logstash` (seção 2), não exige configuração extra.

O filtro abaixo popula um `traceId` no MDC na borda (`entrypoint`), reaproveita um `traceId` recebido
de outro serviço via header quando existe, e sempre limpa o MDC no `finally` — sem isso, como o
servlet container reaproveita threads de um pool, o `traceId` vazaria para a próxima requisição:

```java
package br.com.srportto.appbase.shared.filters;

import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import jakarta.servlet.*;
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

// CORRETO - so relanca com contexto; quem loga (uma vez, com stack trace) e o handler central (shared)
try {
    pedidoRepository.save(pedido);
} catch (Exception e) {
    throw new ApplicationException("Falha ao salvar pedido " + pedido.id(), e);
}
```

### Outros erros comuns

| Anti-padrão | Por que é errado | Correção |
|---|---|---|
| Log dentro de loop quente (`log.info` por item, 10 mil itens = 10 mil linhas) | Explode volume de log para a mesma operação, sem valor extra | Um `log.info` antes/depois do loop com o total; detalhe por item só em `debug`, sob `if (log.isDebugEnabled())` |
| `e.printStackTrace()` | Vai para stdout sem nível, sem timestamp, fora do JSON — invisível para qualquer ferramenta | `log.error("mensagem", e)` — vira evento ERROR estruturado, stack trace no campo de exceção do JSON |

### Concatenação ansiosa (eager)

Placeholder evita a concatenação de string, mas **não** evita a avaliação do argumento em si — o Java
avalia os argumentos antes de chamar `log.debug(...)`, então uma chamada cara (serialização, outro
método pesado) roda de qualquer forma, mesmo com `DEBUG` desligado, se estiver como argumento direto:

```java
// ERRADO - a concatenacao E a serializacao rodam sempre, mesmo com DEBUG desligado
log.debug("Payload recebido: " + objectMapper.writeValueAsString(pedido));

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
