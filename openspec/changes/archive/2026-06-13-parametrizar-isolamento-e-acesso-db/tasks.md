## 1. Configuração do contratoquery (somente-leitura)

- [x] 1.1 Em `arj-contratoquery/src/main/resources/application.yaml`, adicionar `spring.datasource.hikari.transaction-isolation: ${DB_TRANSACTION_ISOLATION:TRANSACTION_READ_COMMITTED}`
- [x] 1.2 Em `arj-contratoquery/src/main/resources/application.yaml`, adicionar `spring.datasource.hikari.read-only: ${DB_READ_ONLY:true}`

## 2. Configuração do contratocommand (leitura/escrita)

- [x] 2.1 Em `arj-contratocommand/src/main/resources/application.yaml`, adicionar `spring.datasource.hikari.transaction-isolation: ${DB_TRANSACTION_ISOLATION:TRANSACTION_READ_COMMITTED}`
- [x] 2.2 Em `arj-contratocommand/src/main/resources/application.yaml`, adicionar `spring.datasource.hikari.read-only: ${DB_READ_ONLY:false}`

## 3. Documentação

- [x] 3.1 Registrar as novas variáveis de ambiente opcionais `DB_TRANSACTION_ISOLATION` e `DB_READ_ONLY` (valores aceitos e defaults) na documentação do projeto (`CLAUDE.md`/`AGENTS.md` do contratocommand e/ou docs de variáveis de ambiente)

## 4. Validação

- [x] 4.1 Rodar `mvn clean package` em `arj-contratoquery` e confirmar build verde (config-only não deve afetar testes)
- [x] 4.2 Rodar `mvn clean package` em `arj-contratocommand` e confirmar build verde
- [ ] 4.3 (Opcional) Smoke test com PostgreSQL: confirmar que escrita no `contratoquery` é rejeitada (read-only) e que o `contratocommand` escreve normalmente; confirmar `transactionIsolation` aplicado nos logs de inicialização do Hikari
