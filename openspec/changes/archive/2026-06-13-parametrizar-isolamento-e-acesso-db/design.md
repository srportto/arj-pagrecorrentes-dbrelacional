## Context

`contratocommand` (escrita) e `contratoquery` (leitura) usam Spring Data JPA + HikariCP sobre o mesmo PostgreSQL 16. Os parâmetros do pool já são parametrizados por variáveis de ambiente com defaults no `application.yaml` (capability `db-connection-pool-config`). Hoje não há controle explícito do nível de isolamento transacional (usa-se o default do PostgreSQL, READ COMMITTED) nem do modo de acesso (ambas as apps abrem conexões read-write). Esta change adiciona ambos os controles seguindo o mesmo padrão de parametrização já estabelecido.

## Goals / Non-Goals

**Goals:**
- Tornar o nível de isolamento transacional configurável por ambiente, sem recompilar, em ambas as aplicações.
- Garantir que o `contratoquery` opere em modo somente-leitura e o `contratocommand` em leitura/escrita, de forma também parametrizável.
- Manter a mudança config-only (apenas `application.yaml`), com defaults seguros que preservam o comportamento atual quando nenhuma variável é definida.

**Non-Goals:**
- Não criar usuário de banco dedicado com `GRANT` somente-leitura (mudança de infraestrutura, fora de escopo — a base é compartilhada).
- Não aplicar isolamento por transação via `@Transactional(isolation=...)` em métodos específicos.
- Não implementar retry automático para falhas de serialização (relevante apenas se SERIALIZABLE for adotado).
- Não alterar pool sizing nem qualquer requisito de `db-connection-pool-config`.

## Decisions

### 1. Modo de acesso via flag `read-only` do HikariCP

**Decisão:** usar `spring.datasource.hikari.read-only`, alimentado por `DB_READ_ONLY` (default `true` no `contratoquery`, `false` no `contratocommand`). O HikariCP aplica `Connection.setReadOnly(...)` e o driver pgjdbc traduz para transação read-only; o PostgreSQL rejeita qualquer escrita com erro.

**Alternativas consideradas:**
- **Usuário de banco com `GRANT SELECT`** — enforcement mais forte (no servidor), mas exige gestão de usuários/infra; fora do escopo (base compartilhada, sem mudança de infra).
- **`@Transactional(readOnly = true)`** — apenas hint do Hibernate (FlushMode.MANUAL), não bloqueia escrita nativa, é code-level e não parametrizável.

**Rationale:** a flag do Hikari é config-level, parametrizável por ambiente e efetivamente bloqueia escritas no nível da conexão — o melhor equilíbrio dentro das restrições do projeto.

### 2. Nível de isolamento via `transaction-isolation` do HikariCP (pool-wide)

**Decisão:** usar `spring.datasource.hikari.transaction-isolation`, alimentado por `DB_TRANSACTION_ISOLATION`, definindo o isolamento default de todas as conexões do pool. Aceita os nomes de constante JDBC: `TRANSACTION_READ_UNCOMMITTED`, `TRANSACTION_READ_COMMITTED`, `TRANSACTION_REPEATABLE_READ`, `TRANSACTION_SERIALIZABLE`.

**Alternativa considerada:** `@Transactional(isolation = ...)` por método — granular, mas code-level e não ajustável "a qualquer momento" por ambiente, contrariando o pedido.

**Rationale:** parametrização global por env var atende diretamente ao requisito de poder mexer no isolamento sem recompilar.

### 3. Default = `TRANSACTION_READ_COMMITTED`

**Decisão:** default de `DB_TRANSACTION_ISOLATION` é `TRANSACTION_READ_COMMITTED`, igual ao default do PostgreSQL. Assim, na ausência da variável, o comportamento atual é preservado.

### 4. "A qualquer momento" = redefinir variável + reiniciar

**Decisão:** as variáveis são lidas na inicialização (binding do `application.yaml`). Ajustar o isolamento/modo significa redefinir a env var e reiniciar a aplicação — não há hot-reload em runtime. Isso é coerente com o padrão de `db-connection-pool-config`.

## Risks / Trade-offs

| Risco | Mitigação |
|---|---|
| `contratoquery` em read-only quebraria qualquer escrita futura nele | É o lado de leitura do CQRS (nunca escreve); se necessário, `DB_READ_ONLY=false` reverte sem deploy |
| `TRANSACTION_SERIALIZABLE` pode gerar falhas de serialização exigindo retry | Default permanece `READ_COMMITTED`; elevar isolamento é decisão explícita de operação, ciente do trade-off |
| Valor inválido em `DB_TRANSACTION_ISOLATION` impede o start do pool | Documentar os valores aceitos; default seguro quando a variável é omitida |
| Comportamento read-only do pgjdbc depende da versão/config do driver | Driver atual traduz `setReadOnly(true)` em transação read-only no PostgreSQL; validar no smoke test |

## Migration Plan

1. Adicionar `hikari.transaction-isolation` (`${DB_TRANSACTION_ISOLATION:TRANSACTION_READ_COMMITTED}`) e `hikari.read-only` aos dois `application.yaml` (`${DB_READ_ONLY:true}` no query, `${DB_READ_ONLY:false}` no command).
2. `mvn clean package` em ambos (config-only; não deve afetar testes).
3. (Opcional) Smoke test: subir as apps com PostgreSQL e confirmar que uma escrita no `contratoquery` falha e que o `contratocommand` escreve normalmente.

**Rollback:** remover as duas chaves dos `application.yaml` (`git revert`); sem mudança de schema.

## Open Questions

- Nenhuma pendência bloqueante. Defaults (`READ_COMMITTED`, query read-only, command read-write) já decididos.
