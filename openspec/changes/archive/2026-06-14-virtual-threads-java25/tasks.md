## 1. Habilitar Virtual Threads no contratocommand

- [x] 1.1 Adicionar `spring.threads.virtual.enabled: true` em `aplicacoes/contratocommand/src/main/resources/application.yaml`
- [x] 1.2 Ajustar `maximum-pool-size` do Hikari de `5` para `10` (padrão `${DB_POOL_MAX_SIZE:10}`) no mesmo arquivo

## 2. Habilitar Virtual Threads no contratoquery

- [x] 2.1 Adicionar `spring.threads.virtual.enabled: true` em `aplicacoes/contratoquery/src/main/resources/application.yaml`
- [x] 2.2 Ajustar `maximum-pool-size` do Hikari de `5` para `10` (padrão `${DB_POOL_MAX_SIZE:10}`) no mesmo arquivo

## 3. Verificar build e testes

- [x] 3.1 Executar `mvn verify` em `contratocommand` e confirmar que todos os testes passam sem falhas
- [x] 3.2 Executar `mvn verify` em `contratoquery` e confirmar que todos os testes passam sem falhas

## 4. Benchmark de performance

- [x] 4.1 Documentar resultado de benchmark before/after com `ab` (Apache Benchmark) ou equivalente — registrar requests/sec e latência P99 sob carga concorrente (`-c 50`) para o endpoint `GET /actuator/health` ou outro endpoint de leitura da query app
