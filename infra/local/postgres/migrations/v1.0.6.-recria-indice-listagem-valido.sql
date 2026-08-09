-----------------------------------------------------------------------------
--- Recria idx_autorizacoes_conta_status_data, hoje INVALID
---
--- Motivacao:
---
--- A migration v1.0.3 criou o indice com:
---
---   CREATE INDEX CONCURRENTLY idx_autorizacoes_conta_status_data
---   ON autorizacoes (id_unico_conta_contratante, status, data_hora_inclusao DESC);
---
--- Em tabela particionada isso nao faz o que parece. O PostgreSQL NAO suporta
--- construcao concorrente de indice particionado: ele cria apenas o indice-pai,
--- nao recursa para as particoes, e o pai fica marcado INVALID -- inutil para o
--- planejador. O indice existe no catalogo e nunca foi usado por consulta
--- nenhuma; a listagem do arj-contratoquery vinha varrendo sequencialmente as
--- 989 particoes desde entao.
---
--- Conferir antes e depois:
---   SELECT indexrelid::regclass, indisvalid FROM pg_index
---    WHERE indrelid = 'autorizacoes'::regclass;
---
--- O procedimento correto tem tres passos: criar o indice-pai com ON ONLY (sem
--- recursar), criar o indice de cada particao CONCURRENTLY, e anexar cada um ao
--- pai. O pai vira VALID sozinho quando a ultima particao e anexada.
---
--- ATENCAO -- este arquivo usa `\gexec`, meta-comando do psql: CREATE INDEX
--- CONCURRENTLY nao pode rodar dentro de bloco transacional, o que descarta
--- DO/PL-pgSQL. Execute com psql, nao por ferramenta que envolva tudo numa
--- transacao. Requisito de "criacao sem bloquear escrita" vem da spec
--- `desempenho-consulta-autorizacoes`.
-----------------------------------------------------------------------------

-- Remove o indice-pai invalido deixado por v1.0.3 (sem filhos, operacao instantanea)
DROP INDEX IF EXISTS idx_autorizacoes_conta_status_data;

-----------------------------------------------------------------------------
--- 1. Indice-pai, SEM recursar para as particoes
---    Nasce INVALID por design; e o estado esperado ate o passo 3 terminar.
-----------------------------------------------------------------------------

CREATE INDEX idx_autorizacoes_conta_status_data
    ON ONLY autorizacoes (id_unico_conta_contratante, status, data_hora_inclusao DESC);

-----------------------------------------------------------------------------
--- 2. Um indice por particao, CONCURRENTLY (nao bloqueia escrita)
-----------------------------------------------------------------------------

SELECT format(
           'CREATE INDEX CONCURRENTLY IF NOT EXISTS %I ON %I '
           '(id_unico_conta_contratante, status, data_hora_inclusao DESC);',
           'idx_' || c.relname || '_conta_status_data',
           c.relname)
  FROM pg_class c
  JOIN pg_inherits i ON i.inhrelid = c.oid
 WHERE i.inhparent = 'autorizacoes'::regclass
 ORDER BY c.relname;
\gexec

-----------------------------------------------------------------------------
--- 3. Anexa cada indice de particao ao pai
---    Quando o ultimo e anexado, o PostgreSQL marca o pai como VALID.
-----------------------------------------------------------------------------

SELECT format(
           'ALTER INDEX idx_autorizacoes_conta_status_data ATTACH PARTITION %I;',
           'idx_' || c.relname || '_conta_status_data')
  FROM pg_class c
  JOIN pg_inherits i ON i.inhrelid = c.oid
 WHERE i.inhparent = 'autorizacoes'::regclass
 ORDER BY c.relname;
\gexec

-----------------------------------------------------------------------------
--- VERIFICACAO (deve devolver indisvalid = t)
-----------------------------------------------------------------------------
--- SELECT indexrelid::regclass, indisvalid
---   FROM pg_index
---  WHERE indrelid = 'autorizacoes'::regclass
---    AND indexrelid::regclass::text = 'idx_autorizacoes_conta_status_data';
---
--- NOTA OPERACIONAL: toda particao criada depois desta migration (pelo
--- pg_partman ou a mao) precisa do seu proprio indice anexado ao pai, ou o pai
--- volta a INVALID. Ao usar CREATE TABLE ... PARTITION OF, o PostgreSQL cria e
--- anexa o indice filho automaticamente -- o cuidado vale para particoes
--- criadas soltas e depois anexadas com ATTACH PARTITION.
-----------------------------------------------------------------------------

-----------------------------------------------------------------------------
--- REVERSAO
-----------------------------------------------------------------------------
--- DROP INDEX IF EXISTS idx_autorizacoes_conta_status_data;  -- leva os filhos junto
-----------------------------------------------------------------------------
