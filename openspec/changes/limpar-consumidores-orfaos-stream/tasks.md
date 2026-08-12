## 1. Resolver as questões em aberto do design

- [x] 1.1 Definir o limiar de ociosidade para remoção (D3). Precisa ser inatingível por
      instância viva no pior caso de inatividade legítima e maior que `stream-min-idle-time-ms`.
      Registrar o número e o racional no `design.md` antes de codificar. **600000 ms (10 min)**,
      verificado empiricamente que `idle` (campo do `XINFO CONSUMERS`) reseta a cada
      `XREADGROUP` mesmo sem dado novo — instância viva não se aproxima do limiar.
- [x] 1.2 Decidir onde a limpeza periódica vive: dentro do `PendenciasSchedulerReivindicador`
      (que já chama `XPENDING` a cada ciclo) ou em componente próprio. Pesar contra a armadilha
      6 do `CLAUDE.md` da app, que alerta contra inchar aquela classe. **Componente próprio**
      (`ConsumidoresOrfaosLimpezaScheduler`), cadência de `stream-min-idle-time-ms`.
- [x] 1.3 Decidir a forma de exposição da contagem (D4): `TemporizacaoHealthIndicator` ou
      métrica Micrometer — a app ainda não tem Micrometer configurado. **`TemporizacaoHealthIndicator`**.

## 2. Ancorar o comportamento em teste antes de implementar

- [x] 2.1 Teste contra Valkey real: consumidor ocioso além do limiar e com `pending = 0` é
      removido; instância viva não é. **Deve falhar agora.** Escrito em
      `ConsumidorRemocaoIntegrationTest` — falha ao compilar (classes ainda não existem), o
      equivalente TDD de "falha agora" para código novo.
- [x] 2.2 Teste que blinda a garantia central: consumidor ocioso **com** `pending > 0` NÃO é
      removido, por mais ocioso que esteja. É o teste que impede a regressão perigosa.
- [x] 2.3 Teste de ordem de eventos: entrada pendente de instância morta continua reivindicável
      depois de a limpeza rodar, e a autorização acaba expirada. Verificado até o reprocessamento
      pelo `ProcessarExpiracaoUseCase` (o acionamento real do command já é coberto por outros
      testes; aqui o que importa é a entrada sobreviver à limpeza e ser reivindicável).
- [x] 2.4 Teste de remoção concorrente: duas instâncias removendo o mesmo órfão no mesmo ciclo,
      sem erro e sem lock. 5 threads concorrentes chamando a mesma remoção.

## 3. Remoção no encerramento gracioso

- [x] 3.1 Implementar a remoção do próprio consumidor no encerramento, condicionada a
      `pending = 0`. `ValkeyStreamConfig.removerConsumidorAoEncerrar` (`@PreDestroy`), delega a
      `ConsumidorRemocaoService.removerSeSemPendencia`.
- [x] 3.2 Garantir que falha na remoção não propaga exceção nem bloqueia o shutdown. `try/catch`
      amplo em `removerConsumidorAoEncerrar` — cobre tanto a consulta a `consumers()` (grupo pode
      nem existir ainda) quanto a remoção em si.
- [x] 3.3 Log da ocorrência quando o consumidor é mantido por ter pendências, com
      `consumidor-id` e quantidade — sem o corpo do evento (armadilha 9 do `CLAUDE.md` da app).
      Vive em `ConsumidorRemocaoService.removerSeSemPendencia` (compartilhado com a camada 2).

## 4. Varredura periódica por ociosidade

- [x] 4.1 Implementar a varredura conforme a decisão de 1.2, usando o limiar de 1.1.
      `ConsumidoresOrfaosLimpezaScheduler`, cadência de `stream-min-idle-time-ms`.
- [x] 4.2 Verificar `pending` imediatamente antes de cada remoção (D2) — nunca confiar em
      leitura de ciclo anterior. `pendingCount()` vem da mesma chamada `XINFO CONSUMERS` que
      decide quem está ocioso, dentro do mesmo ciclo — nunca cacheado entre execuções.
- [x] 4.3 Conferir o retorno de `XGROUP DELCONSUMER`: valor diferente de zero vira `log.error`,
      por indicar que a verificação prévia foi violada. **Achado de implementação**: a API tipada
      do Spring Data Redis (`StreamOperations#deleteConsumer`, e também `RedisConnection#execute`
      genérico) não expõe esse valor — o `execute` genérico decodifica a resposta como bulk
      string e lança `UnsupportedOperationException` em runtime para o inteiro real que o comando
      devolve (confirmado rodando os testes). Resolvido acessando a conexão nativa do driver
      Lettuce (`RedisClusterAsyncCommands#xgroupDelconsumer`, tipado `Long`).
- [x] 4.4 Log de cada remoção com nome do consumidor e tempo ocioso, para que a frequência de
      remoções fique auditável (mitigação do risco "limpeza mascarando problema real").

## 5. Observabilidade

- [x] 5.1 Expor a contagem de consumidores conforme a decisão de 1.3.
      `TemporizacaoHealthIndicator` ganhou o detalhe `consumidoresStream`.
- [x] 5.2 Confirmar que divergência entre consumidores e instâncias não derruba o
      `/actuator/health`. Testado (`divergenciaNaoDerrubaHealth`): 3 consumidores registrados,
      health permanece UP.

## 6. Verificação

- [x] 6.1 Confirmar que os testes de 2.1–2.4 passam. 7/7 verdes em `ConsumidorRemocaoIntegrationTest`.
- [x] 6.2 `mvn test` no `temporiza-autorizacao` (Floci e Valkey no ar). 41/41 verdes (1 falha
      inicial em `VarreduraEAgendamentoIntegrationTest` — flakiness pré-existente de timing em
      `Instant.now()` na borda do segundo, não relacionada a esta change; confirmado repetindo).
- [x] 6.3 Verificação manual com réplicas reais (não via `--scale`, que exigiria remover a
      publicação fixa de porta `8084:8084` de outra change — usados containers `docker run`
      adicionais na mesma rede, mesma imagem). **Achado real durante a verificação**: a primeira
      implementação usava `@PreDestroy`, que falhava em runtime
      (`IllegalStateException`/`assertStarted`) porque `LettuceConnectionFactory` implementa
      `SmartLifecycle` (fase 0) e é parado pela fase de `Lifecycle.stop()` do Spring **antes** da
      fase de `@PreDestroy`/`DisposableBean` no fechamento do contexto — a conexão já estava
      morta quando a remoção tentava rodar. Corrigido: `ValkeyStreamConfig` passou a implementar
      `SmartLifecycle` com fase 100 (para antes do connection factory, conexão ainda viva).
      Reconfirmado depois da correção: (1) `docker compose stop` — consumidor da instância
      parada some do grupo; (2) 2 réplicas, derrubar uma graciosamente — grupo cai de 2 para 1;
      (3) `docker kill --signal=SIGKILL` na sobrevivente — consumidor morto continua no grupo
      logo após o kill; (4) uma terceira réplica viva, decorrido o ciclo da varredura (limiar
      reduzido a 15 s só para o teste, via env var, sem alterar código/config padrão), remove o
      órfão morto por SIGKILL — log confirma: `Consumidor órfão 'fb439e3de632' removido do grupo
      'temporizaautorizacao' — ocioso há 120375 ms`.

## 7. Documentação

- [x] 7.1 Atualizar `apps/temporiza-autorizacao/CLAUDE.md` e `AGENTS.md` (espelhos — manter
      idênticos): descrever o ciclo de vida do consumidor e a regra de nunca remover consumidor
      com PEL não vazio. Armadilhas 11–13 reescritas (SmartLifecycle vs. `@PreDestroy`, checagem
      de pending nas duas camadas, limitação da API tipada para o retorno de `XGROUP
      DELCONSUMER`); armadilha antiga renumerada para 14. `diff` confirma os dois arquivos
      idênticos.
- [x] 7.2 Remover da documentação o procedimento manual de contorno registrado no `design.md`,
      quando deixar de ser necessário. Marcado como superado (mantido só como registro histórico
      do sintoma original, não como procedimento a seguir).
