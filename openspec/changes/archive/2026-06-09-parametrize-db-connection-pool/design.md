## Context

As aplicações `contratocommand` e `contratoquery` usam Spring Boot com JPA/Hibernate e PostgreSQL. O pool de conexões é gerenciado pelo **HikariCP**, que é o padrão do Spring Boot e já está presente no classpath sem dependência adicional.

Ambas as aplicações têm `application.yaml` simples com configuração de datasource (`url`, `username`, `password`), mas sem nenhuma configuração explícita do Hikari. Isso deixa o HikariCP operando com os valores padrão:

| Parâmetro | Default HikariCP |
|---|---|
| `maximumPoolSize` | 10 |
| `minimumIdle` | igual ao maximumPoolSize |
| `connectionTimeout` | 30000 ms |
| `idleTimeout` | 600000 ms |
| `maxLifetime` | 1800000 ms |

Com 10 conexões por instância e múltiplos containers, o banco rapidamente atinge o limite de conexões simultâneas.

## Goals / Non-Goals

**Goals:**
- Expor os parâmetros-chave do HikariCP como variáveis de ambiente em ambas as aplicações.
- Definir valores padrão conservadores no `application.yaml` (ex: `maximum-pool-size: 5`) que funcionem bem para containers com carga moderada.
- Permitir ajuste fino por ambiente (dev, staging, prod) sem rebuild de imagem.

**Non-Goals:**
- Não migrar para outro pool de conexões (c3p0, DBCP2, etc.).
- Não criar configuração programática via `@Bean DataSource` — manter tudo declarativo no yaml.
- Não alterar qualquer lógica de negócio ou API.
- Não criar profiles separados de Spring por agora — os defaults no yaml já cobrem o caso.

## Decisions

### D1 — Configuração via `application.yaml` com `${VAR:default}`

**Decisão**: Usar a sintaxe nativa do Spring `${ENV_VAR:valor_default}` diretamente no `application.yaml`, sob `spring.datasource.hikari.*`.

**Alternativas consideradas**:
- *Variáveis sem default*: Obrigaria sempre definir a variável no deploy, aumentando fricção.
- *Bean `@ConfigurationProperties`*: Mais verboso e desnecessário para configuração pura de infraestrutura.

**Rationale**: A abordagem yaml é a mais idiomática no Spring Boot, não requer código Java, e é imediatamente legível nos arquivos de configuração.

### D2 — Valores padrão conservadores (pool menor)

**Decisão**: Definir `maximum-pool-size: 5` como padrão (metade do default do Hikari).

**Rationale**: O objetivo é justamente reduzir o footprint de cada container. Com 5 conexões por instância, é possível rodar o dobro de containers com a mesma pressão sobre o banco. Ambientes que precisam de mais throughput sobrescrevem via `DB_POOL_MAX_SIZE`.

### D3 — Mesmo conjunto de variáveis em ambas as aplicações

**Decisão**: Usar os mesmos nomes de variável de ambiente em `contratocommand` e `contratoquery`.

**Rationale**: Simplifica a gestão de configuração no orquestrador (Docker Compose, Kubernetes). Os dois apps compartilham o mesmo banco, então faz sentido ter a mesma convenção de nomenclatura.

## Risks / Trade-offs

- **Pool subdimensionado por padrão** → Mitigação: documentar as variáveis no README/deployment guide e expor métricas do HikariCP via Actuator para observar o pool em produção.
- **Timeout de conexão com pool pequeno sob pico de carga** → Mitigação: o `connection-timeout` padrão de 30s evita que requisições esperem indefinidamente; deve ser observado via logs de warning do Hikari.
- **Esquecimento de definir as vars no deploy de prod** → Mitigação: os defaults no yaml garantem que a aplicação sobe mesmo sem as vars definidas, apenas com comportamento mais conservador.

## Migration Plan

1. Alterar `application.yaml` em `contratocommand`.
2. Alterar `application.yaml` em `contratoquery`.
3. Validar que a aplicação sobe localmente sem definir as novas variáveis (deve usar os defaults).
4. Validar que ao definir `DB_POOL_MAX_SIZE=2` a propriedade é refletida nos logs do Hikari (`HikariPool-1 - configuration: maximumPoolSize..2`).
5. Deploy em ambiente de homologação com as variáveis configuradas no orquestrador.

**Rollback**: Basta remover o bloco `hikari:` do yaml — o comportamento volta ao default do HikariCP sem nenhuma alteração de código Java.
