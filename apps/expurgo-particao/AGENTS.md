# CLAUDE.md

> Guia para agentes de IA (Claude Code, Copilot, etc.) trabalharem neste repositório.
> **Este arquivo e `AGENTS.md` são espelhos — mantenha-os idênticos ao editar.**

Lambda Python agendada (EventBridge Scheduler, a cada 30 minutos) que fecha o lado consumidor do
ring buffer de partições `900`–`999` da tabela `autorizacoes`. O `contratocommand`
(`ControleExpurgoAutorizacao.obterParticaoExpurgoWrite`) só escreve nesse anel — esta app é quem o
esvazia. Contrato vigente: [reclamacao-particao-expurgo](../../openspec/specs/reclamacao-particao-expurgo/spec.md).

## Comece por aqui

Leia nesta ordem:
1. [calculo.py](src/expurgo_particao/calculo.py) — cálculo puro, sem I/O: `obter_particao_expurgo_write`
   (espelha `ControleExpurgoAutorizacao` do `contratocommand`) e `obter_particao_alvo`
   (escrita + offset 2, com wraparound)
2. [classificacao.py](src/expurgo_particao/classificacao.py) — decide `EstadoParticao` a partir da
   data mais recente encontrada na partição
3. [estado.py](src/expurgo_particao/estado.py) — os tipos: `EstadoParticao` (3 estados),
   `Acao` (o que foi feito), `ResultadoExecucao` (o que toda execução registra)
4. [persistencia.py](src/expurgo_particao/persistencia.py) — todo SQL que toca `autorizacoes` vive
   aqui; nomes de partição via `psycopg.sql.Identifier`, nunca interpolação de string crua
5. [rotina.py](src/expurgo_particao/rotina.py) — orquestra: classifica e decide numa única
   transação, grava o registro, sempre abre/fecha conexão nova (nunca reaproveita entre invocações)
6. [handler.py](src/expurgo_particao/handler.py) — ponto de entrada da Lambda: lê evento e
   variáveis de ambiente, monta a DSN, chama `rotina.executar`

## Testes

```bash
cd apps/expurgo-particao
pip install -r requirements-dev.txt
export EXPURGO_PARTICAO_TEST_DSN="postgresql://docker:<sua-senha>@localhost:5432/db-csp-postgres"
pytest
```

`test_calculo.py` e `test_classificacao.py` são puros (sem banco). `test_rotina_integracao.py`
exige o Postgres local no ar com a migration `v1.0.7` aplicada — exercita o `TRUNCATE` de verdade
com massa sintética e afirma que as gavetas **vizinhas** (alvo−1, alvo+1) ficam intactas.

> `ci-testesunitarios-expurgo-particao.yml` roda `pytest tests --ignore=tests/test_rotina_integracao.py`
> a cada push/PR que toque `apps/expurgo-particao/**`, mesmo padrão de path das cinco apps Java.
> Ignora `test_rotina_integracao.py` (exige Postgres real no ar) por nome de arquivo — não há
> convenção de sufixo `*IntegrationTest` em Python aqui, então a exclusão é por caminho explícito,
> não por padrão de nome.

## Decisões de desenho (resumo — detalhe completo no design.md arquivado)

O design completo, com alternativas descartadas e o racional de cada escolha, está em
`openspec/changes/archive/2026-08-23-reclamar-particao-expurgo-ciclo/design.md`. Resumo do que
importa para quem mexe neste código:

- **`TRUNCATE` na partição folha, não `DETACH`/`DELETE`/`DROP`+`CREATE`.** Só o `TRUNCATE` expurga
  sem tomar `ACCESS EXCLUSIVE` na tabela pai `autorizacoes` — as outras três opções ou preservam o
  dado, ou incham o TOAST, ou travam a listagem do `contratoquery` (que varre as 989 partições).
- **Alvo = partição de escrita + 2**, retenção de 98 semanas (não "2 anos" — 100 gavetas semanais
  jamais entregariam 104 semanas). Offset fixo, não configurável: qualquer valor diferente de 2
  arrisca apagar dado mais novo que a retenção ou desperdiçar folga de reação.
- **Sem fase de dry-run.** A gaveta alvo fica vazia por ~87 semanas desde o primeiro commit do
  projeto (2026-06-07) — um dry-run não teria nada para observar. A mitigação real é
  `data_referencia` injetável no evento (capacidade permanente, não etapa de rollout) mais os três
  estados distinguíveis e o registro do que foi **calculado**, não só do que foi feito.
- **`host.docker.internal:5432`, não o hostname `postgres` da rede Compose.** O Floci roda a Lambda
  como container **irmão** do projeto Compose (via `docker.sock`), que não resolve nomes da rede
  `postgres_default` — mesmo caminho já adotado pelas tasks ECS (`infra/envs/local/variables.tf`).
  Confirmado por spike em 2026-08-22 (função mínima + EventBridge Scheduler no Floci, 4 invocações
  sucessivas observadas).
- **`pg_cron` é caixa-preta de auditoria, sem poder de escrita sobre `autorizacoes`.** Confere o
  resultado que a Lambda **afirmou** ter produzido (não recalcula a fórmula) — duas implementações
  independentes da mesma fórmula divergiriam com o tempo. Ver o job em
  `infra/local/postgres/` (mesma migration que cria `expurgo_particao_registro`).
- **`shared_preload_libraries` do Postgres local permanece intocado.** `pg_partman` carregado sem
  uso é intencional — ver [local-postgres-environment](../../openspec/specs/local-postgres-environment/spec.md).

## Armadilhas críticas

1. **A fórmula de partição é espelhada manualmente do Java.** `calculo.py` duplica
   `ControleExpurgoAutorizacao.obterParticaoExpurgoWrite` (`apps/contratocommand`) em Python. Não há
   módulo compartilhado entre as duas linguagens — mudou a fórmula num lado, replique no outro
   (mesma convenção do resto do monorepo, ver `CLAUDE.md` da raiz).
2. **`EXPURGO_PARTICAO_DESARMAR_TRUNCATE` é interruptor operacional, não flag de teste.** Quando
   ativo, a rotina continua calculando e gravando o registro, só não executa o `TRUNCATE`. Não
   confunda com `modo_consulta` do evento (mesmo efeito, mas por invocação, não por ambiente).
3. **Nunca reaproveite conexão entre invocações.** `rotina.executar` abre e fecha a conexão a cada
   chamada de propósito — o Floci mantém pool de containers quentes com até 30 minutos de
   ociosidade entre disparos, e uma conexão guardada em variável global pode voltar morta.
4. **`gaveta vazia` é resultado normal, não erro.** Por ~87 semanas desde o primeiro commit do
   projeto, toda execução encontra a gaveta alvo vazia. Não trate `EstadoParticao.VAZIA` como
   anomalia nem adicione alarme sobre ausência de `TRUNCATE`.
5. **`RECUSA_LOCK_TIMEOUT` tem `estado=None`, não `VAZIA`.** Quando o `lock_timeout` (5s) esgota, a
   verificação nem chegou a rodar — não confunda ausência de leitura com partição vazia observada.
6. **Nomes de tabela de partição nunca são parametrizados como bind.** `nome_tabela_particao`
   valida a faixa (`900..999`) antes de montar `sql.Identifier` — é a defesa contra SQL injection
   neste módulo. Não troque por f-string nem remova a validação de faixa.
7. **Verificação e `TRUNCATE` vivem na mesma transação.** Se a classificação decidir por não
   truncar, `rotina.py` sempre chama `ROLLBACK` explícito — não há como a verificação deixar efeito
   residual mesmo sendo só leitura.

## Documentação relacionada

- [reclamacao-particao-expurgo](../../openspec/specs/reclamacao-particao-expurgo/spec.md) — contrato
  vigente
- [expurgo-estados-terminais](../../openspec/specs/expurgo-estados-terminais/spec.md) — lado de
  escrita do ring buffer (`contratocommand`)
- [local-postgres-environment](../../openspec/specs/local-postgres-environment/spec.md) — ambiente
  Postgres local, incluindo o registro de que `pg_partman` sem consumidor é intencional
- `openspec/changes/archive/2026-08-23-reclamar-particao-expurgo-ciclo/` — proposal, design e tasks
  completos desta app

## Checklist antes do commit

- [ ] `pytest` passa (Postgres local no ar, `EXPURGO_PARTICAO_TEST_DSN` definida)
- [ ] Se mudou a fórmula em `calculo.py`, replicou a mudança em
  `ControleExpurgoAutorizacao` (`apps/contratocommand`) e vice-versa
- [ ] Se mudou `persistencia.py`, confirmou que nenhum nome de tabela é montado por
  interpolação de string crua
- [ ] Nenhum log novo carrega dado de linha de `autorizacoes` — só `semana`, `particao_escrita`,
  `particao_alvo`, `estado`, `acao`
