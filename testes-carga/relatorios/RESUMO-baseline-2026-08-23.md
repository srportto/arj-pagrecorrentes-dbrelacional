# Resumo — baseline de teste de carga (2026-08-23/24)

Change OpenSpec: `testes-de-carga-tps`. Três execuções reais contra o ambiente local
(`docker-compose` + Floci), com `deploy.resources.limits` aplicado em todos os containers
(design.md D5) e sem recalibrar nenhum teto conhecido (pool HikariCP, `MAX_CONCURRENT_MESSAGES`,
concorrência do consumer Kafka).

**Ambiente: SÓ LOCAL. Estes números não representam capacidade de produção** — refletem a
máquina onde os testes rodaram, sem isolamento de rede/IO entre containers (só CPU/mem).

## Configuração vigente durante os três testes

| Parâmetro | Valor |
|---|---|
| Pool HikariCP (`contratocommand`/`contratoquery`) | 10 (default, `DB_POOL_MAX_SIZE` não sobrescrita) |
| `MAX_CONCURRENT_MESSAGES` (listeners SQS) | 10 (hardcoded) |
| Concorrência do consumer Kafka (`eventos-consumer`) | 1 (default, sem `concurrency` configurado) |
| Limites de recursos por container | aplicados — ver `testes-carga/README.md`, tabela de limites |
| `spring.threads.virtual.enabled` | `true` nas duas apps Java com Hikari |

## Resultados

| Cenário | Requisições | Falhas | p50 | p99 | Máx | Throughput médio |
|---|---|---|---|---|---|---|
| Escrita isolada (`contratocommand`) — ramp 1→50/s, 5 min | 22.950 | 0 | 6ms | 364ms | 1.691ms | 76,5 req/s (~25,5 ciclos/s) |
| Leitura isolada (`contratoquery`) — ramp 1→50/s, 5 min | 7.650 | 0 | 13ms | 18ms | 177ms | 25,5 req/s |
| Jornada composta — patamar fixo 5/s, 10 min | 6.000 | 0 | 7ms | 18ms | 101ms | 10 req/s (bate com o patamar) |

**Fila SQS e lag do consumer group Kafka permaneceram em 0 durante toda a jornada composta** —
o pipeline assíncrono acompanhou a carga em tempo real nesse patamar.

## O que isto NÃO prova (limitações genuínas desta rodada)

1. **Não encontramos o ponto de colapso.** Nas três execuções, `0 falhas` e nenhum kill switch
   foi acionado — inclusive no ramp de escrita, que foi até 50 usuários/s sem degradação
   sustentada. Isto é "TPS mínimo confirmado sem colapso", não "TPS máximo suportado". Para
   achar o teto real é preciso repetir com ramp/patamar mais alto (ex.: escrita até 200-500/s;
   jornada composta em 20/s, 50/s...).
2. **A listagem (`contratoquery`) rodou contra banco praticamente vazio.** O gargalo já
   documentado (`apps/contratoquery/CLAUDE.md`, armadilha 8 — scan de ~889 partições sem poda,
   ~180-200ms com volume representativo) não foi exercitado, porque a massa sintética
   representativa (~276 mil linhas) não foi carregada antes deste baseline. O p99=18ms medido
   aqui não é comparável ao cenário de produção com volume real.
3. **Nenhum dos três buckets de erro (D3) foi exercitado de verdade.** Como não houve nenhuma
   falha, `ErroClassificador` não teve a chance de discriminar 409/idempotência de colapso
   real em execução real — o mecanismo está implementado e testado via smoke test manual, mas
   não validado sob taxa de erro real diferente de zero.
4. **Limiares dos kill switches não foram calibrados** (`LOADTEST_LIMITE_*`, valores de
   partida em `Config.java`/scripts) — como nenhum limite foi ultrapassado, não sabemos se os
   valores de partida (`hikaricp_connections_pending > 0`, CPU host > 90%, fila/lag > 1000)
   são sensatos ou não. Ver `tasks.md` 8.3.

## Rodada 2 (agressiva) — mesma sessão, 2026-08-23/24

Executada a pedido, para tentar achar o colapso real que a rodada 1 não encontrou. Mudanças:
ramp de escrita e leitura elevado para 10→400 usuários/s (4 min); jornada composta elevada de
patamar fixo 5/s para 30/s (8 min); massa sintética representativa
(`gerar-massa-sintetica-representativa.sql`, ~281 mil linhas em 889 partições) carregada antes
do cenário de leitura.

| Cenário | Requisições | OK | KO | p50 | p99 | Máx | Throughput médio |
|---|---|---|---|---|---|---|---|
| Escrita isolada — ramp 10→400/s, 4 min | 108.996 | 89.698 | 19.298 | 6ms | 479ms | 1.229ms | 450,4 req/s |
| Leitura isolada — ramp 10→400/s, 4 min, **com massa representativa** | 49.200 | 31.063 | 18.137 | 1.285ms | 52.078ms | 57.525ms | 176,98 req/s |
| Jornada composta — patamar fixo 30/s, 8 min | 28.800 | 28.800 | 0 | 7ms | 23ms | 2.282ms | 60 req/s (bate com o patamar) |

### Escrita: achado é do gerador de carga, não do servidor

19.298 KO foram **100% `java.net.BindException: Address already in use`** — esgotamento de
portas efêmeras do próprio Gatling/JVM no Windows sob altíssima concorrência de conexões
curtas, não erro do `contratocommand`. Isolando esse ruído, o classificador de erro (D3)
mostrou: `sucesso=89.646`, **`esperado-por-design=8`** (primeira vez que apareceu 409 real de
idempotência sob concorrência genuína — exatamente o comportamento correto, não contado como
colapso), `colapso-real=44` (falhas com status HTTP real, não conexão recusada). **O teto real
do `contratocommand` continua não encontrado** — o sistema sustentou ~450 req/s totais
(~150 ciclos/s) antes do limite do gerador de carga aparecer primeiro. Achar o teto real
exigiria rodar de múltiplas máquinas/processos geradores, ou configurar reuso agressivo de
conexão HTTP no Gatling (`shareConnections`) — fora do escopo desta rodada.

### Leitura: colapso real encontrado, confirma o gargalo já documentado

Com a massa sintética representativa carregada, a listagem deixou de ser trivial: **p50 subiu
de 13ms (rodada 1, banco vazio) para 1.285ms, e p99 de 18ms para 52 segundos**. 1.106
requisições tiveram falha real (`colapso-real`, D3); 7.013 KO adicionais foram
`IOException: Premature close` — conexão fechada pelo servidor em requisições que ficaram
enfileiradas tempo demais, sinal genuíno de saturação, distinto do `BindException` do lado do
cliente. **Este é o colapso real que a rodada 1 não conseguiu mostrar** — confirma
empiricamente o gargalo já documentado em `apps/contratoquery/CLAUDE.md` (armadilha 8: scan
físico das ~889 partições sem poda por conta) sob carga concorrente real, não só em medição
de query isolada.

### Jornada composta: ainda sem colapso, mesmo em 6x o patamar da rodada 1

60 req/s sustentado por 8 minutos, **0 falhas**, fila SQS e lag do consumer group Kafka em 0
durante toda a execução. O pipeline assíncrono segue com headroom além de 60 req/s.

## Conclusão consolidada (rodadas 1+2)

- **`contratoquery` (listagem) é o componente que primeiro colapsa de verdade** sob volume de
  dado representativo — não é hipótese, foi medido (p99 de 18ms para 52s).
- **`contratocommand` (escrita) não teve o teto real encontrado** — aguentou bem mais do que a
  rodada 1 sugeria (~450 req/s vs. ~76,5 req/s), mas o teste bateu num limite do gerador de
  carga antes do servidor. O 409 de idempotência sob concorrência real funcionou exatamente
  como desenhado (D3).
- **O pipeline assíncrono (SNS/SQS/Kafka/temporizador) não mostrou sinal de estresse** em
  nenhuma das rodadas, até 60 req/s sustentado — maior suspeito de gargalo aqui seria só sob
  taxa de escrita muito mais alta do que conseguimos aplicar de um único gerador local.

## Próximos passos recomendados (fora do escopo desta rodada)

- Para achar o teto real do `contratocommand`: distribuir a geração de carga (múltiplos
  processos/máquinas) ou configurar reuso de conexão HTTP mais agressivo no Gatling, para não
  esbarrar em limite de porta efêmera do cliente antes do servidor.
- Para `contratoquery`: já confirmado colapso real — próximo passo é decidir se vale investir
  em poda por partição na query de listagem (mudança de produto/arquitetura, fora do escopo
  desta capability de diagnóstico) ou se o comportamento atual é aceito como limitação
  conhecida.
- Calibrar os limiares dos kill switches agora tem dado real disponível: a leitura já mostrou
  que p99 > alguns segundos e `Premature close` são sinais de colapso genuíno — os limiares de
  `LOADTEST_LIMITE_*` podem ser ajustados com base nisso na próxima rodada.
