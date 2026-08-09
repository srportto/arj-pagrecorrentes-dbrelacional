-----------------------------------------------------------------------------
--- Faz o banco impor a obrigatoriedade que a entidade ja declara
---
--- Motivacao (change `corrigir-expurgo-merge-version`, D3-pre):
---
--- A entidade Autorizacao declara 22 colunas como `nullable = false`, mas o
--- banco so impunha 9 delas. Com `ddl-auto: none`, a anotacao nao cria nada --
--- e apenas documentacao, e estava documentando errado.
---
--- Isso nao e so cosmetico: em UNIQUE, o PostgreSQL trata cada NULL como
--- distinto de qualquer outro NULL. Enquanto `id_autorizacao_empresa` aceitar
--- NULL, a garantia de unicidade da chave de negocio (v1.0.4) tem um buraco --
--- N linhas com chave nula coexistem na mesma particao sem violar nada.
---
--- ATENCAO -- SET NOT NULL exige varredura completa da tabela sob lock
--- ACCESS EXCLUSIVE, propagada a cada particao. Em base pequena e instantaneo;
--- em volume de producao, planeje janela ou use a alternativa em duas fases
--- (ADD CONSTRAINT ... NOT VALID + VALIDATE CONSTRAINT, disponivel para CHECK).
-----------------------------------------------------------------------------

-----------------------------------------------------------------------------
--- 1. Normalizacao previa: metadados ausente vira objeto JSON vazio
---
--- `metadados` e mapeado como jsonb nao-nulo na entidade, e as proprias
--- fixtures da aplicacao usam '{}' para "sem metadados". Linhas antigas com
--- NULL sao anteriores a coluna virar obrigatoria. A conversao preserva o
--- significado: ausencia de metadados == objeto vazio.
-----------------------------------------------------------------------------

UPDATE autorizacoes SET metadados = '{}' WHERE metadados IS NULL;

-----------------------------------------------------------------------------
--- 2. Colunas que passam a ser NOT NULL
-----------------------------------------------------------------------------

ALTER TABLE autorizacoes ALTER COLUMN id_autorizacao_empresa     SET NOT NULL;
ALTER TABLE autorizacoes ALTER COLUMN id_unico_conta_contratante SET NOT NULL;
ALTER TABLE autorizacoes ALTER COLUMN motivo_status              SET NOT NULL;
ALTER TABLE autorizacoes ALTER COLUMN data_inicio_vigencia       SET NOT NULL;
ALTER TABLE autorizacoes ALTER COLUMN valor                      SET NOT NULL;
ALTER TABLE autorizacoes ALTER COLUMN frequencia                 SET NOT NULL;
ALTER TABLE autorizacoes ALTER COLUMN quantidade_dividas_ciclo   SET NOT NULL;
ALTER TABLE autorizacoes ALTER COLUMN indicador_uso_limite_conta SET NOT NULL;
ALTER TABLE autorizacoes ALTER COLUMN indicador_tipo_mensageria  SET NOT NULL;
ALTER TABLE autorizacoes ALTER COLUMN id_pessoa_pagadora         SET NOT NULL;
ALTER TABLE autorizacoes ALTER COLUMN id_pessoa_devedora         SET NOT NULL;
ALTER TABLE autorizacoes ALTER COLUMN id_pessoa_recebedora       SET NOT NULL;
ALTER TABLE autorizacoes ALTER COLUMN metadados                  SET NOT NULL;

-----------------------------------------------------------------------------
--- 3. NAO incluida de proposito: valor_limite
---
--- A entidade tambem a declara `nullable = false`, mas ha linha legada com
--- NULL e `valor_limite` e um valor monetario -- escolher um numero para ela
--- e decisao de negocio, nao algo que uma migration deva inventar. Depois de
--- definido o destino dessas linhas (corrigir o valor ou expurga-las), feche
--- a lacuna com:
---
---   ALTER TABLE autorizacoes ALTER COLUMN valor_limite SET NOT NULL;
---
--- Para localizar as pendentes:
---   SELECT id_autorizacao, id_particao_conta, id_autorizacao_empresa, status
---     FROM autorizacoes WHERE valor_limite IS NULL;
-----------------------------------------------------------------------------

-----------------------------------------------------------------------------
--- REVERSAO
-----------------------------------------------------------------------------
--- ALTER TABLE autorizacoes ALTER COLUMN id_autorizacao_empresa     DROP NOT NULL;
--- ALTER TABLE autorizacoes ALTER COLUMN id_unico_conta_contratante DROP NOT NULL;
--- ALTER TABLE autorizacoes ALTER COLUMN motivo_status              DROP NOT NULL;
--- ALTER TABLE autorizacoes ALTER COLUMN data_inicio_vigencia       DROP NOT NULL;
--- ALTER TABLE autorizacoes ALTER COLUMN valor                      DROP NOT NULL;
--- ALTER TABLE autorizacoes ALTER COLUMN frequencia                 DROP NOT NULL;
--- ALTER TABLE autorizacoes ALTER COLUMN quantidade_dividas_ciclo   DROP NOT NULL;
--- ALTER TABLE autorizacoes ALTER COLUMN indicador_uso_limite_conta DROP NOT NULL;
--- ALTER TABLE autorizacoes ALTER COLUMN indicador_tipo_mensageria  DROP NOT NULL;
--- ALTER TABLE autorizacoes ALTER COLUMN id_pessoa_pagadora         DROP NOT NULL;
--- ALTER TABLE autorizacoes ALTER COLUMN id_pessoa_devedora         DROP NOT NULL;
--- ALTER TABLE autorizacoes ALTER COLUMN id_pessoa_recebedora       DROP NOT NULL;
--- ALTER TABLE autorizacoes ALTER COLUMN metadados                  DROP NOT NULL;
---
--- A normalizacao de `metadados` NAO e revertida: nao ha como distinguir o
--- '{}' que veio de NULL do '{}' que sempre foi '{}'.
-----------------------------------------------------------------------------
