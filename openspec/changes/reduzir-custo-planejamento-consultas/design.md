## Context

`autorizacoes` é particionada por `LIST (id_particao_conta)` em 989 partições. O PostgreSQL
planeja a consulta considerando cada partição que não conseguir podar; o custo desse
planejamento é pago **a cada chamada**, em CPU, e é linear no número de partições consideradas.

As medições que motivam esta mudança estão na seção "Why" do `proposal.md`. O ponto central: com
a tabela contendo 24 linhas, a listagem gasta 147,6 ms planejando e 17,8 ms executando. A
proporção 8:1 entre planejar e executar é o problema.

Esta é uma change de **investigação antes de decisão**. O spike já feito estabelece que o ganho
existe; o que falta é confirmar que ele não vem acompanhado de perdas em consultas específicas.

## Goals / Non-Goals

**Goals:**

- Determinar se `force_generic_plan` é seguro e vantajoso para cada consulta das duas apps de
  leitura, medindo uma a uma em vez de generalizar a partir do spike.
- Estabelecer medida de referência de latência por endpoint, para que regressão deixe de passar
  despercebida.
- Responder se 889 partições quentes se justificam.

**Non-Goals:**

- Reverter ou reabrir a cascata de `fallback-consulta-autorizacao-expurgada`. Ela resolve um
  problema de **correção** (404 indevido), não de desempenho.
- Mudar contrato de API.
- Reescrever consultas para incluir a chave de particionamento onde ela não faz sentido — a
  listagem filtra por conta porque é isso que o negócio pede.

## Decisions

Nenhuma decisão fechada ainda — esta change existe para produzir as medições que permitem
decidir. Registram-se as hipóteses e o que cada uma precisa provar.

### H1 — `force_generic_plan` elimina o replanejamento por chamada

Suportada pelo spike (0,17 ms contra 35,2 ms). Precisa provar, antes de virar decisão:

- Que vale para **todas** as conexões do pool HikariCP, e não só para a primeira. O
  `plan_cache_mode` é configuração de sessão; a forma de aplicá-la (parâmetro de conexão,
  `connection-init-sql`, `ALTER ROLE`) muda o alcance.
- Que a **listagem paginada com ordenação** ganha. É a consulta mais complexa e a de maior
  tráfego, e é a que o spike não cobriu — o spike usou busca por id.
- Que a poda em tempo de execução (`Subplans Removed`) não degrada consultas onde o plano custom
  hoje encontra um caminho melhor por conhecer o valor real do parâmetro.

**Risco conhecido:** plano genérico usa estimativas médias em vez do valor real do parâmetro. Em
consulta com distribuição de dados enviesada, isso escolhe plano pior. Com 24 linhas em ambiente
local não dá para observar; exige volume representativo.

### H2 — 889 partições quentes são mais do que o necessário

O número saiu de `Math.abs(uuid.hashCode()) % 889` em `IdContaUUIDPartitionDistributor`. Não há
registro do racional para 889 especificamente. Como o custo de planejamento é linear nesse
número, é a alavanca mais simples disponível — e a única que barateia **toda** consulta do
sistema de uma vez, inclusive as que o plano genérico não ajudar.

Precisa de: volume esperado de contas, volume por partição, e a política de retenção que o
expurgo pressupõe. Reduzir partição é migração de dados, não configuração.

### H3 — O ganho de índice é secundário enquanto o planejamento dominar

A capacidade `desempenho-consulta-autorizacoes` hoje exige que o plano use índice em vez de
varredura sequencial — e está correta. Mas ela endereça a *execução* (17,8 ms), não o
*planejamento* (147,6 ms). Corrigir a migration do índice, como foi feito na v1.0.6, era
necessário e insuficiente.

## Risks / Trade-offs

- **Medir em ambiente local não prova nada sobre produção** → 24 linhas não exercitam
  distribuição de dados nem estimativas de seletividade. Mitigação: as medições de planejamento
  são válidas (independem de volume); as de execução e de escolha de plano **não são** e
  precisam de ambiente com volume representativo antes de qualquer decisão.

- **`force_generic_plan` é global à sessão** → Não dá para ligar por consulta sem manipular a
  sessão em torno de cada uma. Ou vale para tudo, ou não vale. Mitigação: é exatamente por isso
  que a medição precisa ser consulta a consulta antes de adotar.

- **Reduzir partições é irreversível na prática** → Migração de dados em tabela particionada,
  com janela. Mitigação: separar em change própria depois que H2 tiver números.

## Open Questions

- Qual o volume real esperado por conta e por partição? Sem isso, H2 não sai do lugar.
- O `arj-contratocommand` também deve adotar plano genérico, ou o benefício lá é irrelevante por
  suas consultas já podarem para 1 partição?
- Existe ambiente com volume representativo onde medir, ou é preciso gerar massa sintética?
