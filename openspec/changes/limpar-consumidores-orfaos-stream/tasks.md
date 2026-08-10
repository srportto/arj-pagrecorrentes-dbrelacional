## 1. Resolver as questões em aberto do design

- [ ] 1.1 Definir o limiar de ociosidade para remoção (D3). Precisa ser inatingível por
      instância viva no pior caso de inatividade legítima e maior que `stream-min-idle-time-ms`.
      Registrar o número e o racional no `design.md` antes de codificar.
- [ ] 1.2 Decidir onde a limpeza periódica vive: dentro do `PendenciasSchedulerReivindicador`
      (que já chama `XPENDING` a cada ciclo) ou em componente próprio. Pesar contra a armadilha
      6 do `CLAUDE.md` da app, que alerta contra inchar aquela classe.
- [ ] 1.3 Decidir a forma de exposição da contagem (D4): `TemporizacaoHealthIndicator` ou
      métrica Micrometer — a app ainda não tem Micrometer configurado.

## 2. Ancorar o comportamento em teste antes de implementar

- [ ] 2.1 Teste contra Valkey real: consumidor ocioso além do limiar e com `pending = 0` é
      removido; instância viva não é. **Deve falhar agora.**
- [ ] 2.2 Teste que blinda a garantia central: consumidor ocioso **com** `pending > 0` NÃO é
      removido, por mais ocioso que esteja. É o teste que impede a regressão perigosa.
- [ ] 2.3 Teste de ordem de eventos: entrada pendente de instância morta continua reivindicável
      depois de a limpeza rodar, e a autorização acaba expirada.
- [ ] 2.4 Teste de remoção concorrente: duas instâncias removendo o mesmo órfão no mesmo ciclo,
      sem erro e sem lock.

## 3. Remoção no encerramento gracioso

- [ ] 3.1 Implementar a remoção do próprio consumidor no encerramento, condicionada a
      `pending = 0`.
- [ ] 3.2 Garantir que falha na remoção não propaga exceção nem bloqueia o shutdown.
- [ ] 3.3 Log da ocorrência quando o consumidor é mantido por ter pendências, com
      `consumidor-id` e quantidade — sem o corpo do evento (armadilha 9 do `CLAUDE.md` da app).

## 4. Varredura periódica por ociosidade

- [ ] 4.1 Implementar a varredura conforme a decisão de 1.2, usando o limiar de 1.1.
- [ ] 4.2 Verificar `pending` imediatamente antes de cada remoção (D2) — nunca confiar em
      leitura de ciclo anterior.
- [ ] 4.3 Conferir o retorno de `XGROUP DELCONSUMER`: valor diferente de zero vira `log.error`,
      por indicar que a verificação prévia foi violada.
- [ ] 4.4 Log de cada remoção com nome do consumidor e tempo ocioso, para que a frequência de
      remoções fique auditável (mitigação do risco "limpeza mascarando problema real").

## 5. Observabilidade

- [ ] 5.1 Expor a contagem de consumidores conforme a decisão de 1.3.
- [ ] 5.2 Confirmar que divergência entre consumidores e instâncias não derruba o
      `/actuator/health`.

## 6. Verificação

- [ ] 6.1 Confirmar que os testes de 2.1–2.4 passam.
- [ ] 6.2 `mvn test` no `temporiza-autorizacao` (Floci e Valkey no ar).
- [ ] 6.3 Verificação manual com 2 réplicas: `docker compose up -d --scale
      temporiza-autorizacao=2`, derrubar uma graciosamente e conferir que o grupo volta a 1
      consumidor; matar a outra com `docker kill` e conferir que ela some após o limiar.

## 7. Documentação

- [ ] 7.1 Atualizar `apps/temporiza-autorizacao/CLAUDE.md` e `AGENTS.md` (espelhos — manter
      idênticos): descrever o ciclo de vida do consumidor e a regra de nunca remover consumidor
      com PEL não vazio.
- [ ] 7.2 Remover da documentação o procedimento manual de contorno registrado no `design.md`,
      quando deixar de ser necessário.
