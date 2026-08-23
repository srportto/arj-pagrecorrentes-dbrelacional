## 1. Spike de rede (BLOQUEANTE — nada abaixo começa antes disto)

- [x] 1.1 Subir o ambiente (`docker compose up -d` na raiz) e confirmar `postgres` e `floci` saudáveis
- [x] 1.2 Provar que um container **irmão** (fora do projeto Compose) alcança o banco, sem envolver
      Lambda nenhuma: `docker run --rm postgres:18-alpine psql
      "postgresql://<user>@host.docker.internal:5432/db-csp-postgres" -c "select 1"`
- [x] 1.3 Publicar uma função Lambda mínima no Floci (handler que só abre conexão, faz `SELECT 1` e
      devolve) e invocá-la via `aws --endpoint-url http://localhost:4566 lambda invoke` — confirma o
      canal de invocação e o acesso ao banco de dentro do runtime de Lambda
- [x] 1.4 Agendar essa função com EventBridge Scheduler em `rate(1 minute)` e observar 3 minutos
      (`docker ps` mostrando containers de Lambda nascendo) — confirma que o Scheduler dispara Lambda
      em Docker real
- [x] 1.5 Registrar o resultado em `design.md` (D4): confirmado, ou desenho de infraestrutura
      refeito antes de prosseguir

## 2. Massa sintética na faixa de expurgo

- [x] 2.1 Criar `infra/local/postgres/gerar-massa-sintetica-expurgo.sql`, irmão de
      `gerar-massa-sintetica-representativa.sql` (que só popula as partições quentes, `0..888`),
      populando a faixa `900..999` com `data_hora_ultima_atlz` **retroativa** e coerente com a gaveta
      de cada linha (gaveta = `900 + (semanas desde o Epoch da data % 100)`)
- [x] 2.2 Parametrizar a data de referência do script, para permitir semear o cenário "gaveta alvo com
      dado de 98 semanas" e o cenário "gaveta alvo com dado recente" (recusa)
- [x] 2.3 Semear também as gavetas vizinhas (alvo−1, alvo+1) com dado próprio, para que o teste possa
      afirmar que elas ficam intactas
- [x] 2.4 Documentar o uso no `README.md` de `infra/local/postgres/`, ao lado do gerador existente

## 3. Cálculo e classificação (lógica pura, sem I/O)

- [x] 3.1 Criar `apps/expurgo-particao/` com layout Python enxuto (`src/`, `tests/`) e dependência de
      driver PostgreSQL
- [x] 3.2 Implementar o cálculo da semana e da partição de escrita, espelhando
      `ControleExpurgoAutorizacao.obterParticaoExpurgoWrite` (origem 1970-01-01, `floor(dias / 7)`,
      `900 + semanas % 100`), em **UTC fixo e explícito**
- [x] 3.3 Implementar a partição alvo como `escrita + 2` com retorno cíclico ao início da faixa
- [x] 3.4 Testes de paridade com o Java: os mesmos valores de `ControleExpurgoAutorizacaoTest`
      (época→900, +1 semana→901, +99→999, +100→900, +50→950) e a fórmula sobre 300 semanas
- [x] 3.5 Teste afirmando que o alvo **nunca** coincide com a partição de escrita, para toda semana de
      um ciclo completo
- [x] 3.6 Teste travando o fuso: o mesmo instante avaliado em UTC e em `America/Sao_Paulo` produz a
      mesma semana

## 4. Rotina de reclamação (I/O e transação)

- [x] 4.1 Implementar a classificação em três estados: **vazia** (`SELECT EXISTS (SELECT 1 FROM ...)`),
      **dado do ciclo anterior**, **dado recente**
- [x] 4.2 Implementar a transação: `SET LOCAL lock_timeout` curto → verificação de estado → `TRUNCATE`
      apenas no estado "dado do ciclo anterior" → `COMMIT`; qualquer outro estado faz `ROLLBACK`
- [x] 4.3 Tratar esgotamento de `lock_timeout` como execução sem efeito, não como falha
- [x] 4.4 Abrir e fechar a conexão por invocação (não guardar em variável global — o pool de
      containers quentes do Floci deixa 30 min de ociosidade entre invocações)
- [x] 4.5 Implementar a data de referência opcional no evento e o modo consulta (relata sem aplicar),
      como capacidade permanente
- [x] 4.6 Implementar o interruptor por variável de ambiente que desarma o `TRUNCATE` (alavanca
      operacional, não fase de implantação)
- [x] 4.7 Emitir registro estruturado em **toda** execução com `semana`, `particao_escrita`,
      `particao_alvo`, `estado` e `acao` — inclusive quando a ação for nenhuma
- [x] 4.8 Gravar o alvo calculado e o resultado na tabela de registro (que a verificação do item 6
      confere sem recalcular a fórmula)

## 5. Testes contra PostgreSQL real

- [x] 5.1 Teste: gaveta alvo com dado de 98 semanas → fica vazia; **vizinhas intactas**; relação ainda
      anexada ao pai (`pg_inherits`); `relpartbound` preservado; índices ainda `indisvalid`
- [x] 5.2 Teste: gaveta alvo vazia → nenhuma escrita, execução bem-sucedida, sem alarme
- [x] 5.3 Teste: gaveta alvo com dado recente → recusa, `ROLLBACK`, linhas preservadas, anomalia
      registrada
- [x] 5.4 Teste: após o esvaziamento, uma inserção com `id_particao_conta` da gaveta esvaziada é
      roteada normalmente para ela, sem reanexação nem recriação de índice
- [x] 5.5 Teste do modo consulta com data de referência futura (2028-04-20): relata o alvo e a ação,
      não altera dado algum
- [x] 5.6 Workflow `.github/workflows/ci-testesunitarios-expurgo-particao.yml`, seguindo o padrão dos
      5 existentes (`paths: apps/expurgo-particao/**`, job `testes-unitarios`), separando os testes
      que exigem Postgres dos que não exigem

## 6. Registro forense no banco (pg_cron)

- [x] 6.1 Migration criando a tabela de registro (ciclo, semana, partição alvo afirmada pela rotina,
      estado, ação, instante) — **fora** da tabela `autorizacoes`
- [x] 6.2 Job `pg_cron` diário que confere o estado da partição que a rotina **afirmou** ter mirado e
      grava o resultado; NÃO recalcula a fórmula
- [x] 6.3 Job `pg_cron` semanal (quinta de manhã, após a virada da gaveta) com a asserção de
      invariante do anel: para toda gaveta da faixa de expurgo, as linhas nela pertencem à semana
      correspondente àquela gaveta
- [x] 6.4 Papel de banco dedicado aos jobs, **sem** permissão de escrita sobre `autorizacoes`
- [x] 6.5 Papel de banco da Lambda com privilégio granular: `SELECT` + `GRANT TRUNCATE` sobre as 100
      partições de expurgo, **sem** ownership da tabela
- [x] 6.6 Documentar explicitamente que isto é **registro forense, não alarme** — não notifica
      ninguém por conta própria; responde "desde quando" quando alguém perguntar

## 7. Empacotamento e infraestrutura

- [x] 7.1 `apps/expurgo-particao/Dockerfile` a partir de `public.ecr.aws/lambda/python:<versão>`
- [x] 7.2 `infra/envs/local/ecr.tf`: repositório ECR `expurgo-particao` e o `local` de URI reescrita
      para `127.0.0.1:<porta>`, no mesmo padrão dos dois existentes
- [x] 7.3 `infra/envs/local/scripts/build-and-push.sh`: somar a entrada ao map `APPS` (o laço já
      cobre o resto)
- [x] 7.4 Módulo `infra/modules/lambda-scheduled/`: função Lambda por imagem, EventBridge Scheduler
      em `rate(30 minutes)`, papel IAM, e `db_host`/`db_port` como variáveis opacas — mesmo contrato
      de `ecs-service`, para que local e AWS real divirjam só no valor
- [x] 7.5 `infra/envs/local/main.tf`: instanciar o módulo com `db_host = var.db_host`
      (`host.docker.internal` por padrão, conforme D4)
- [x] 7.6 `README.md` do módulo novo, seguindo o padrão dos módulos existentes

## 8. Correção dos desvios de documentação

- [x] 8.1 `apps/contratocommand/CLAUDE.md` e `AGENTS.md` (espelhos — manter idênticos): remover a
      linha que descreve `obterParticaoExpurgoDrop` como existente (removido em `585f584`) e apontar
      a reclamação para a nova capability
- [x] 8.2 Nos mesmos dois arquivos: corrigir `obterParticaoExpurgoWrite(dataFimVigencia)` para o
      instante da finalização, como o código faz (`CancelarAutorizacaoService:59`,
      `DecidirAutorizacaoService:57`) e como `expurgo-estados-terminais` exige
- [x] 8.3 `docs/arquitetura/modelo-dados-e-dados-poc-testada-para-essa-implementacao.md`: remover as 3
      menções a `obterParticaoExpurgoDrop`; corrigir "Retenção de Dados: Garantida (2 anos)" para 98
      semanas, com a explicação de que 104 semanas é inalcançável num anel de 100 gavetas semanais;
      corrigir o exemplo narrativo "Semana 2: partição 900 pode ser DROPPED", que descreve o primeiro
      ciclo e induz a ler o offset +2 como "2 semanas atrás" em vez de "98 semanas atrás"
- [x] 8.4 Substituir, no mesmo documento, a descrição de expurgo por `DROP TABLE` pela decisão
      adotada (`TRUNCATE`), com o comparativo `DETACH` × `TRUNCATE` × `DROP`+`CREATE` em Mermaid
      (conforme a skill `gerar-diagramas`: Mermaid em `.md` versionado, nunca ASCII)
- [x] 8.5 `CLAUDE.md` da raiz: incluir a reclamação do anel na seção de regras que atravessam os
      serviços, já que ela é a contraparte de `transferirParaExpurgo`

## 9. Verificação final

- [x] 9.1 Rodar a suíte completa da app nova contra Postgres local real e confirmar todos os
      cenários dos itens 5.1–5.5
- [x] 9.2 Confirmar, com a massa sintética, que após um esvaziamento a partição continua anexada:
      `pg_inherits` aponta para `autorizacoes`, `relpartbound` preservado, `oid` inalterado, e nenhuma
      tabela nova apareceu (`count` de relações `autorizacoes%` idêntico antes e depois)
- [x] 9.3 Confirmar que o índice-pai `idx_autorizacoes_conta_status_data` continua `indisvalid = t`
      após um ciclo completo de esvaziamento
- [x] 9.4 Invocar em modo consulta com `data_referencia = 2028-04-20` e conferir que o alvo relatado
      é a partição 944 — a primeira gaveta com conteúdo a ser mirada
- [x] 9.5 Confirmar que `shared_preload_libraries` continua `pg_partman_bgw,pg_cron` — esta change
      não altera o preload
- [x] 9.6 Rodar `openspec validate reclamar-particao-expurgo-ciclo --strict`
