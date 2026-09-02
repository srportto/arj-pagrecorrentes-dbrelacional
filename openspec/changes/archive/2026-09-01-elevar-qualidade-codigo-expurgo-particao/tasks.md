## 1. Ferramental e tipos (não muda comportamento — estabelece o piso de verificação)

- [x] 1.1 Criar `apps/expurgo-particao/pyproject.toml` com metadados do pacote (`src` layout) e
      seções `[tool.mypy]` (strict, `python_version` compatível com a imagem 3.13), `[tool.ruff]` e
      `[tool.black]` (D7)
- [x] 1.2 Adicionar `mypy`, `ruff` e `black` a `requirements-dev.txt`, com versão pinada como já
      é feito com `pytest==8.0.0`
- [x] 1.3 Anotar `cur` nas quatro funções de `persistencia.py` (`existe_dado`,
      `max_data_hora_ultima_atlz`, `truncar_particao`, `gravar_registro`)
- [x] 1.4 Anotar `context` em `lambda_handler` e trocar `event: dict` / `-> dict` por tipos
      parametrizados
- [x] 1.5 Trocar `-> dict` de `ResultadoExecucao.como_registro` por tipo parametrizado
- [x] 1.6 Trocar `ambiente: dict | None` por `Mapping[str, str] | None` em `truncate_permitido` e
      `executar`
- [x] 1.7 Rodar `mypy --strict` e corrigir o que aparecer; onde a anotação correta for
      desproporcional, usar `# type: ignore` pontual **com comentário explicando**
- [x] 1.8 Rodar `ruff` e `black --check`, corrigindo as ocorrências
- [x] 1.9 Confirmar que os testes importam `expurgo_particao` sem `PYTHONPATH: src`, e ajustar o
      `from conftest import ...` de `test_rotina_integracao.py` se a instalação do pacote o tornar
      desnecessário
- [x] 1.10 Adicionar passos de `mypy --strict` e `ruff` ao
      `.github/workflows/ci-testesunitarios-expurgo-particao.yml`, mantendo o
      `--ignore=tests/test_rotina_integracao.py` do passo de pytest
- [x] 1.11 Confirmar suíte verde e workflow coerente antes de tocar em qualquer comportamento

## 2. Verificações de premissa (antes de mudar comportamento)

- [x] 2.1 Inspecionar `v1.0.7.-cria-tabela-registro-expurgo-particao.sql` e verificar se a coluna
      `acao` tem `CHECK`, tipo enum ou é `text` livre (D8)
- [x] 2.2 Se houver restrição, criar migration nova acrescentando `FALHA` e `RECUSA_DESARMADO` —
      **nunca** alterar a `v1.0.7` já aplicada
- [x] 2.3 Inspecionar o job `pg_cron` em `infra/local/postgres/` e verificar se ele assume lista
      fechada de valores de `acao`; ajustar se assumir
- [x] 2.4 Confirmar no DDL se `data_hora_ultima_atlz` é `NOT NULL`; registrar o resultado, pois ele
      decide se a task 5.1 (remoção de `existe_dado`) prossegue ou é abandonada (D4)

## 3. Testes novos contra o comportamento atual

- [x] 3.1 Criar `tests/test_handler.py` cobrindo `_montar_dsn` com credencial contendo `@`, `/`,
      `:`, `#` e `%` — deve falhar hoje, é o defeito que a task 4.6 corrige
- [x] 3.2 Cobrir em `test_handler.py` a ausência de variável de ambiente obrigatória e o parse de
      `data_referencia` inválida (ex.: `"20/04/2028"`)
- [x] 3.3 Cobrir em `test_handler.py` a coerção de `modo_consulta` e o evento vazio/`None`
- [x] 3.4 Criar `tests/test_estado.py` cobrindo `ResultadoExecucao.como_registro`, inclusive com
      `estado=None` (caso `RECUSA_LOCK_TIMEOUT`)
- [x] 3.5 Criar `tests/test_rotina_decisao.py` com duplo de teste de conexão/cursor, cobrindo os
      seis caminhos: vazia, ciclo anterior aplicado, dado recente recusado, modo consulta sobre
      dado esvaziável, desarme sobre dado esvaziável, e `LockNotAvailable` (D6)
- [x] 3.6 Adicionar a `test_rotina_decisao.py` os casos ainda não suportados — falha não prevista
      registrada, e desarme registrado como ação própria — marcados como esperando falha até o
      grupo 4
- [x] 3.7 Confirmar que os testes dos grupos 3.1–3.5 rodam no CI (sem Postgres) e que só os do 3.6
      estão vermelhos

## 4. Integridade forense (único grupo que muda comportamento observável)

- [x] 4.1 Acrescentar `FALHA` e `RECUSA_DESARMADO` ao enum `Acao` em `estado.py`
- [x] 4.2 Mover o `INSERT` do registro para dentro da transação do `TRUNCATE`, de modo que o commit
      do esvaziamento e o do registro sejam o mesmo (D1); manter os caminhos sem escrita gravando o
      registro após o `rollback`, como hoje
- [x] 4.3 Capturar `Exception` (nunca `BaseException`) em `_classificar_e_decidir`, gravar registro
      com `Acao.FALHA` e a natureza do erro em transação própria, e **re-lançar** (D2)
- [x] 4.4 Registrar `Acao.RECUSA_DESARMADO` quando `estado == DADO_CICLO_ANTERIOR` e o esvaziamento
      for impedido pelo interruptor `EXPURGO_PARTICAO_DESARMAR_TRUNCATE` (D3); `modo_consulta`
      continua produzindo `NENHUMA`
- [x] 4.5 Confirmar que os testes de 3.6 ficaram verdes e que nenhum de 3.1–3.5 regrediu
- [x] 4.6 Trocar o f-string de `_montar_dsn` por `psycopg.conninfo.make_conninfo` com argumentos
      nomeados (D5); confirmar que o teste 3.1 passou a verde
- [x] 4.7 Trocar `logging.basicConfig` por `logger.setLevel(os.environ.get("LOG_LEVEL", "INFO"))`
      em `handler.py`, com comentário explicando que o runtime da Lambda já instalou handler no
      root logger
- [x] 4.8 Acrescentar a `test_rotina_integracao.py` um caso que prove a atomicidade de 4.2 —
      esvaziamento e registro presentes juntos, ou nenhum dos dois

## 5. Higiene

- [x] 5.1 Se 2.4 confirmou `NOT NULL`: remover `existe_dado` de `persistencia.py` e a bifurcação
      correspondente em `rotina.py`, deixando `classificar_estado` decidir `VAZIA` a partir de
      `None` (D4). Caso contrário, anotar o motivo e pular
- [x] 5.2 Trocar `class EstadoParticao(str, enum.Enum)` e `class Acao(str, enum.Enum)` por
      `enum.StrEnum`; confirmar que todo uso continua explicitamente sobre `.value`
- [x] 5.3 Trocar `dt.timezone.utc` por `dt.UTC` em `calculo.py` e `rotina.py`
- [x] 5.4 Adicionar docstrings estilo Google às funções públicas de `persistencia.py`
- [x] 5.5 Corrigir o encoding misturado em `classificacao.py` (`"Retenção deliberada e' de 98
      semanas"` e os demais termos sem acento no mesmo comentário)

## 6. Fechamento

- [x] 6.1 Rodar `pytest`, `mypy --strict`, `ruff` e `black --check` — todos verdes
- [x] 6.2 Rodar a suíte de integração com o Postgres local no ar e `EXPURGO_PARTICAO_TEST_DSN`
      definida, confirmando que o `TRUNCATE` real continua esvaziando só a gaveta alvo
- [x] 6.3 Invocar a Lambda no Floci em modo consulta e confirmar que o registro é gravado, que o
      `LOG_LEVEL` agora é respeitado, e que a DSN monta corretamente com a senha do `.env` local
- [x] 6.4 Exercitar o interruptor `EXPURGO_PARTICAO_DESARMAR_TRUNCATE` e confirmar
      `acao=RECUSA_DESARMADO` no registro
- [x] 6.5 Atualizar `apps/expurgo-particao/CLAUDE.md` e `AGENTS.md` (espelhos — manter idênticos):
      armadilha 5 ganha os novos valores de `acao`, e a seção de testes ganha os arquivos novos
- [x] 6.6 Atualizar o grafo `graphify` do repositório — **não aplicável**: o grafo não é
      versionado (só `graphify-out/README.md` está no Git; o resto é gerado localmente e
      ignorado). Quem mantém grafo local deve regerá-lo após esta change
- [x] 6.7 Rodar `openspec validate elevar-qualidade-codigo-expurgo-particao --strict` e conferir
      que cada cenário dos dois delta specs tem teste correspondente

> **Nota de execução (2026-09-01):** a 6.3 foi validada invocando `lambda_handler`
> diretamente contra o Postgres local com as variáveis de ambiente reais do `.env` —
> nenhuma função está publicada no Floci (`lambda list-functions` devolve vazio), e
> publicá-la exigiria `terraform apply` + build da imagem, fora do escopo desta change.
> O caminho exercitado (handler → rotina → persistência → banco real) é o mesmo; falta
> apenas o runtime de container. Confirmados: `LOG_LEVEL=DEBUG` respeitado pelo logger,
> senha do `.env` preservada na DSN, registro gravado em modo consulta, e
> `acao=RECUSA_DESARMADO` com a gaveta intacta sob o interruptor de desarme.
>
> A migration `v1.1.0` foi aplicada à mão no banco local: o volume já existia, e
> migrations só rodam automaticamente na primeira subida com volume vazio.
