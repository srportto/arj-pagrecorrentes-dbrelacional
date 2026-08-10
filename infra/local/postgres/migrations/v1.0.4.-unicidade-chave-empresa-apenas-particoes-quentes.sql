-----------------------------------------------------------------------------
--- Restringe a unicidade de id_autorizacao_empresa as particoes quentes
---
--- Motivacao (change `corrigir-expurgo-merge-version`, decisao D3a):
---
--- O PostgreSQL exige que toda constraint UNIQUE de tabela particionada inclua
--- as colunas da chave de particionamento. Por isso `id_particao_conta` entrou
--- na constraint uk_autorizacao_empresa_particao -- por imposicao do banco, nao
--- por intencao de modelagem.
---
--- Acontece que essa coluna significa coisas diferentes conforme a faixa:
---
---   particao quente  (0..888):  id_particao_conta = hash(id_unico_conta_contratante)
---                               => "um id_autorizacao_empresa por conta"    <- o que se quer
---
---   particao expurgo (900..999): id_particao_conta = balde semanal
---                               => "um id_autorizacao_empresa por semana,
---                                   somando TODAS as contas"                <- acidente
---
--- Resultado: duas autorizacoes de CONTAS DIFERENTES que compartilhem o mesmo
--- id_autorizacao_empresa e cheguem a estado terminal na mesma semana colidem
--- ao serem transferidas para o expurgo -- DataIntegrityViolationException,
--- HTTP 409 deterministico, autorizacao presa no status de origem.
---
--- Decisao: a unicidade da chave de negocio e uma regra sobre autorizacoes
--- ATIVAS, nao um invariante da tabela. Linhas em particao de expurgo estao em
--- estado terminal e existem apenas ate o proximo DROP PARTITION.
---
--- Consequencia aceita conscientemente: nas particoes 900..999 a unicidade fica
--- DESLIGADA, nao apenas relaxada -- duplicata exata (mesma conta, mesma chave)
--- passa a ser possivel ali. O que continua barrando duplicata fisica e a chave
--- primaria (id_autorizacao, id_particao_conta).
---
--- ATENCAO -- NAO usar CREATE INDEX CONCURRENTLY aqui: em tabela particionada o
--- PostgreSQL nao recursa para as particoes e deixa o indice-pai INVALID (foi
--- exatamente o que aconteceu com idx_autorizacoes_conta_status_data, criado em
--- v1.0.3 e ate hoje invalido). Para criar concorrentemente e preciso indexar
--- cada particao individualmente e so entao criar o indice-pai, nao
--- concorrentemente, com ATTACH PARTITION dos indices filhos.
-----------------------------------------------------------------------------

-- Remove a constraint cuja semantica se degrada na faixa de expurgo
ALTER TABLE autorizacoes DROP CONSTRAINT IF EXISTS uk_autorizacao_empresa_particao;

-- Recria a garantia como indice unico PARCIAL, restrito as particoes quentes.
-- O predicado e propagado a cada particao; nas de expurgo ele nunca e satisfeito
-- e o indice fica inerte.
CREATE UNIQUE INDEX uk_autorizacao_empresa_ativa
    ON autorizacoes (id_particao_conta, id_autorizacao_empresa)
    WHERE id_particao_conta < 900;

-----------------------------------------------------------------------------
--- REVERSAO
-----------------------------------------------------------------------------
--- DROP INDEX IF EXISTS uk_autorizacao_empresa_ativa;
--- ALTER TABLE autorizacoes ADD CONSTRAINT uk_autorizacao_empresa_particao
---     UNIQUE (id_particao_conta, id_autorizacao_empresa);
---
--- A reversao falha se, no periodo em que o indice parcial esteve ativo, tiverem
--- entrado na mesma particao de expurgo duas linhas com o mesmo
--- id_autorizacao_empresa -- justamente o que esta migration passa a permitir.
--- Nesse caso e preciso decidir o destino dessas linhas antes de reverter.
-----------------------------------------------------------------------------
