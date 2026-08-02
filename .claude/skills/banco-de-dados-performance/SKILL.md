---
name: banco-de-dados-performance
description: Use ao otimizar queries SQL, desenhar schema, analisar planos de execução, tunar PostgreSQL/MySQL, criar índices, configurar replicação, ou diagnosticar problemas de performance de banco. Gatilhos - "query lenta", "EXPLAIN", "índice", "tuning de banco", "N+1", "plano de execução", "pg_stat_statements", "slow query".
---

# Banco de Dados — Performance e Tuning

## Visão geral

Guia de otimização de banco de dados relacional (PostgreSQL e MySQL) focado em performance de query,
design de índice e tuning de configuração. Use esta skill ao investigar uma query lenta, criar
índices, interpretar `EXPLAIN ANALYZE`, ou planejar a configuração do SGBD.

**Quando NÃO usar:** para problemas de JPA/Hibernate (N+1, `LazyInitializationException`, dirty
checking) em código Java, use a skill `persistencia-jpa`. Para a query JPA específica (JPQL,
`@EntityGraph`), use `persistencia-jpa` — esta skill é para o lado SQL/SGBD. Para o design
arquitetural da camada de persistência (em qual camada mora o Repository), use
`arquitetura-limpa-java`.

## Workflow

1. **Capture a baseline** — rode `EXPLAIN ANALYZE` **antes** de qualquer mudança; meça o tempo real
   e o custo estimado, salve para comparar depois.
2. **Identifique gargalos** — query ineficiente, índice faltando, configuração errada, conexão
   saturada.
3. **Projete a solução** — estratégia de índice, rewrite de query, ajuste de schema.
4. **Aplique incrementalmente** — uma mudança por vez, com monitoramento; valide cada uma antes de
   seguir.
5. **Valide o resultado** — re-rodar `EXPLAIN ANALYZE`, comparar custo, medir wall-clock
   improvement, documentar a mudança.

> ⚠️ **Sempre teste em não-produção primeiro.** Reverta imediatamente se a performance de escrita
> regredir ou se a replicação aumentar lag.

---

# SQL Query Patterns

## CTE — Common Table Expressions

```sql
-- Isola lógica cara de subquery para reuso e legibilidade
WITH ranked_orders AS (
    SELECT
        customer_id,
        order_id,
        total_amount,
        ROW_NUMBER() OVER (PARTITION BY customer_id ORDER BY order_date DESC) AS rn
    FROM orders
    WHERE status = 'completed'          -- filtra cedo, antes do join
)
SELECT customer_id, order_id, total_amount
FROM ranked_orders
WHERE rn = 1;                           -- último pedido completed por customer
```

## Window Functions

```sql
-- Running total e rank dentro de uma partição — sem self-join
SELECT
    department_id,
    employee_id,
    salary,
    SUM(salary)  OVER (PARTITION BY department_id ORDER BY hire_date) AS running_payroll,
    RANK()       OVER (PARTITION BY department_id ORDER BY salary DESC) AS salary_rank
FROM employees;
```

## EXISTS vs COUNT

```sql
-- RUIM: conta todas as linhas (lento)
SELECT COUNT(*) FROM orders WHERE customer_id = 42;

-- BOM: para no primeiro match (rápido)
SELECT EXISTS(SELECT 1 FROM orders WHERE customer_id = 42);
```

## Correlated Subquery → JOIN Rewrite

```sql
-- ANTES: subquery correlata, uma execução por linha (lento)
SELECT order_id,
       (SELECT SUM(quantity) FROM order_items oi WHERE oi.order_id = o.id) AS item_count
FROM orders o;

-- DEPOIS: agregação em um único join (rápido)
SELECT o.order_id, COALESCE(agg.item_count, 0) AS item_count
FROM orders o
LEFT JOIN (
    SELECT order_id, SUM(quantity) AS item_count
    FROM order_items
    GROUP BY order_id
) agg ON agg.order_id = o.id;

-- Covering index de apoio (inclui todas as colunas tocadas pela query)
CREATE INDEX idx_order_items_order_qty
    ON order_items (order_id)
    INCLUDE (quantity);
```

---

# EXPLAIN ANALYZE

## PostgreSQL — capturando o plano

```sql
-- Sempre use ANALYZE para ver row count real vs estimado
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT *
FROM orders o
JOIN customers c ON c.id = o.customer_id
WHERE o.created_at > NOW() - INTERVAL '30 days';
```

### Padrões a procurar

| Padrão | Sintoma | Remédio típico |
|--------|---------|----------------|
| `Seq Scan` em tabela grande | Row estimate alto, sem seletividade de filtro | Adicionar B-tree index na coluna do filtro |
| `Nested Loop` com outer set grande | Crescimento exponencial de rows no inner loop | Considerar Hash Join; index na chave do inner |
| `cost=... rows=1` mas `actual rows=50000` | Estatísticas desatualizadas | Rodar `ANALYZE <tabela>;` |
| `Buffers: hit=10 read=90000` | Baixo hit rate do buffer cache | Aumentar `shared_buffers`; adicionar covering index |
| `Sort Method: external merge` | Sort derramando para disco | Aumentar `work_mem` para a sessão |

## PostgreSQL — top slow queries

```sql
-- Requer extensão pg_stat_statements
SELECT query,
       calls,
       round(total_exec_time::numeric, 2)  AS total_ms,
       round(mean_exec_time::numeric, 2)   AS mean_ms,
       round(stddev_exec_time::numeric, 2) AS stddev_ms,
       rows
FROM   pg_stat_statements
ORDER  BY mean_exec_time DESC
LIMIT  20;
```

## MySQL — slow queries

```sql
-- Candidatos do slow query log
SELECT * FROM performance_schema.events_statements_summary_by_digest
ORDER  BY SUM_TIMER_WAIT DESC
LIMIT  20;

-- Plano de execução
EXPLAIN FORMAT=JSON
SELECT * FROM orders WHERE status = 'pending' AND created_at > NOW() - INTERVAL 7 DAY;
```

---

# Estratégias de Índice

## Covering Index

```sql
-- Cobre o filtro E as colunas projetadas, eliminando o heap fetch
CREATE INDEX CONCURRENTLY idx_orders_status_created_covering
    ON orders (status, created_at)
    INCLUDE (customer_id, total_amount);
```

## Partial Index

```sql
-- Partial index para filtro seletivo — menor, mais rápido
CREATE INDEX CONCURRENTLY idx_orders_pending
    ON orders (customer_id, created_at)
    WHERE status = 'pending';
```

## Validar uso do índice

```sql
-- Confirmar que o índice é realmente usado
SELECT indexname, idx_scan, idx_tup_read, idx_tup_fetch
FROM   pg_stat_user_indexes
WHERE  relname = 'orders';
```

> **`CREATE INDEX CONCURRENTLY` (PostgreSQL):** não bloqueia escrita na tabela durante a criação
> (ao custo de levar mais tempo). Use em produção; `CREATE INDEX` simples trava a tabela e causa
> downtime.

---

# Features específicas do PostgreSQL

## JSONB — GIN index e query

```sql
-- Cria GIN index para queries de contenção
CREATE INDEX idx_events_payload ON events USING GIN (payload);

-- Query eficiente de contenção JSONB (usa o GIN)
SELECT * FROM events WHERE payload @> '{"type": "login", "success": true}';

-- Extrair valor aninhado
SELECT payload->>'user_id', payload->'meta'->>'ip'
FROM events WHERE payload @> '{"type": "login"}';
```

## VACUUM e bloat monitoring

```sql
-- Tabelas com alta contagem de dead tuples
SELECT relname, n_dead_tup, n_live_tup,
       round(n_dead_tup::numeric / NULLIF(n_live_tup + n_dead_tup, 0) * 100, 2) AS dead_pct,
       last_autovacuum
FROM pg_stat_user_tables
ORDER BY n_dead_tup DESC
LIMIT 20;

-- VACUUM manual em tabela com churn alto
VACUUM (ANALYZE, VERBOSE) orders;
```

## Monitoring de replication lag

```sql
-- Na primary: verifica lag dos standbys
SELECT client_addr, state, sent_lsn, write_lsn, flush_lsn, replay_lsn,
       (sent_lsn - replay_lsn) AS replication_lag_bytes
FROM pg_stat_replication;
```

---

# Tuning de configuração

## PostgreSQL — parâmetros chave

| Parâmetro | Default | Target de tuning | Razão |
|-----------|---------|------------------|-------|
| `shared_buffers` | 128MB | 25% da RAM | Aumentar para workloads read-heavy |
| `work_mem` | 4MB | 64-256MB | Aumentar para sorts/hashes complexos |
| `maintenance_work_mem` | 64MB | 256-512MB | Aumentar para VACUUM/ANALYZE |
| `effective_cache_size` | 4MB | 75% da RAM | Hint para o planner da RAM disponível |
| `random_page_cost` | 4.0 | 1.1 (SSD) | Menor para storage rápido |

## MySQL — parâmetros chave

| Parâmetro | Default | Target de tuning | Razão |
|-----------|---------|------------------|-------|
| `innodb_buffer_pool_size` | 128M | 70-80% da RAM | Aumentar para read-heavy |
| `innodb_log_file_size` | 48M | 256-512M | Reduzir escritas no log |
| `max_connections` | 151 | Monitorar & tunar | Evitar exaustão de conexões |
| `slow_query_log` | OFF | ON | Habilitar para análise |

---

# Padrões de uso na aplicação

## Connection pooling (obrigatório em produção)

- **PostgreSQL:** pgBouncer em modo transaction (mais leve) ou session; **MySQL:** ProxySQL ou
  HikariCP do lado da aplicação.
- Tamanho do pool: `nucleos * 2` para connection por core é o ponto de partida; meça e ajuste.
- **Nunca** abra conexão por operação em loop sem pool — esgota o banco.

## Prepared statements (proteção e performance)

```java
// SEMPRE parametrizado — protege de SQL injection E permite o planner cachear o plano
PreparedStatement ps = connection.prepareStatement(
    "SELECT id, email FROM users WHERE email = ?"
);
ps.setString(1, email);
ResultSet rs = ps.executeQuery();
```

## `SELECT *` em produção

```sql
-- RUIM: traz colunas desnecessárias, quebra se a tabela ganhar uma coluna nova com JOIN
SELECT * FROM orders WHERE customer_id = 42;

-- BOM: explicito, estável, e o índice pode ser covering
SELECT id, status, total_amount FROM orders WHERE customer_id = 42;
```

---

# Constraints

## MUST DO
- Capture `EXPLAIN (ANALYZE, BUFFERS)` **antes** de otimizar — essa é a baseline.
- Meça performance antes e depois de cada mudança.
- Crie índices com `CONCURRENTLY` (PostgreSQL) para evitar table locks.
- Teste em não-produção; reverta se write performance ou replication lag piorar.
- Documente toda decisão de otimização com métricas antes/depois.
- Rode `ANALYZE` após mudanças em massa para atualizar estatísticas.
- Use connection pooling (pgBouncer, pgPool) em produção.
- Use prepared statements para prevenir SQL injection.

## MUST NOT DO
- Aplique otimizações sem baseline medida.
- Crie índices redundantes ou não usados.
- Faça múltiplas mudanças simultâneas (impossível atribuir impacto).
- Ignore write amplification causado por índices novos.
- Negligence VACUUM / manutenção de estatísticas.
- Use `SELECT *` em produção.
- Use `cursor` quando set-based operations funcionam.
- Desabilite autovacuum globalmente.

## Quem aplica o quê

| Situação | Quem | Skill |
|---|---|---|
| Investigar query lenta, criar índice | sessão principal | esta skill |
| Resolver N+1, `LazyInitializationException` em JPA | agent `java-revisor` | `persistencia-jpa` |
| Revisão de query gerada por SQL nativo (Hibernate `nativeQuery`) | agent `java-especialista` | esta skill + `persistencia-jpa` |
| Configurar banco novo (PostgreSQL tuning) | session principal | esta skill |
