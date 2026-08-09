-----------------------------------------------------------------------------
--- Criacao de indice composto para otimizar a listagem de autorizacoes
--- por id_unico_conta_contratante, status e data_hora_inclusao
---
--- Este indice melhora o desempenho da consulta:
---   SELECT * FROM autorizacoes
---   WHERE id_unico_conta_contratante = ?
---   AND status = ?
---   ORDER BY data_hora_inclusao DESC
---
--- Nota: CONCURRENTLY nao pode ser usado dentro de transacao,
--- portanto esta migration pode precisar ser executada fora do contexto
--- transacional da ferramenta de migracao (Flyway/Liquibase).
-----------------------------------------------------------------------------

CREATE INDEX CONCURRENTLY idx_autorizacoes_conta_status_data
ON autorizacoes (id_unico_conta_contratante, status, data_hora_inclusao DESC);
