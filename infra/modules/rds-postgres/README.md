# modules/rds-postgres

**Status:** placeholder — sem código Terraform ainda.

## Propósito futuro

Módulo reutilizável para provisionar o PostgreSQL gerenciado (RDS) usado pelas
duas aplicações (`arj-contratocommand`, `arj-contratoquery`).

Pontos que este módulo precisará resolver (herdados do ambiente local em
[`../../local/postgres/`](../../local/postgres/)):

- Parameter group habilitando `shared_preload_libraries = "pg_cron,pg_partman_bgw"`
  e `cron.database_name` (equivalente ao `command` da imagem Docker local).
- Estratégia de criação das extensões `pg_cron`/`pg_partman` no banco (passo de
  migração — ainda não definido; hoje o schema é aplicado via SQL manual).
- Credenciais fora do Terraform state em texto plano (Secrets Manager — fora de
  escopo desta fase, mas o módulo deve prever o ponto de integração).
