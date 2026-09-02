## Why

A rotina de expurgo destrói dado — é a única app do monorepo cuja operação normal é um `TRUNCATE`.
Hoje ela executa esse `TRUNCATE` e grava seu registro forense em **transações separadas**:

```python
truncar_particao(cur, particao_alvo)
conexao.commit()          # TX 1 — dado apagado, ponto de não-retorno
...                       # janela: timeout da Lambda, conexão caída, tabela indisponível
gravar_registro(cur, resultado)
conexao.commit()          # TX 2
```

Uma falha na janela deixa a partição vazia sem nenhum registro. A spec
`reclamacao-particao-expurgo` define que *"a ausência do registro periódico SHALL ser o sinal de que
a rotina parou"* — então a supervisão concluiria **"rotina parada"** exatamente no cenário em que
ela acabou de apagar uma gaveta inteira. O sinal aponta para o oposto do que aconteceu, e só no
caminho destrutivo.

O mesmo vale para falhas: `_classificar_e_decidir` só captura `LockNotAvailable`. Qualquer outra
exceção (`UndefinedTable`, `InsufficientPrivilege`, `OperationalError`) propaga e a execução
termina sem registro — tornando indistinguíveis "Lambda desagendada", "grant revogado com erro a
cada 30 minutos" e "TRUNCATE feito, crash antes do registro".

Agrava o quadro que a lógica destrutiva é justamente a menos coberta: o CI roda
`--ignore=tests/test_rotina_integracao.py`, de modo que `rotina.py` e `persistencia.py` nunca são
exercitados automaticamente. O CI verifica 103 das 414 linhas de produção — e nenhuma delas
executa `TRUNCATE`.

## What Changes

**Integridade forense (muda comportamento observável):**

- O registro da execução passa a ser gravado na **mesma transação** do `TRUNCATE`. Ou os dois
  efeitos acontecem, ou nenhum acontece.
- Nova ação `FALHA`: exceção não prevista passa a ser registrada com a classe do erro antes de
  propagar, em vez de encerrar a execução sem rastro.
- Nova ação `RECUSA_DESARMADO`: quando `EXPURGO_PARTICAO_DESARMAR_TRUNCATE` impede o esvaziamento,
  o registro passa a dizer isso explicitamente em vez de gravar `NENHUMA` e obrigar o auditor a
  deduzir pelo cruzamento de `estado` com `acao`.

**Rede de segurança:**

- Testes unitários de `_classificar_e_decidir` com cursor falso, cobrindo os seis caminhos de
  decisão (vazia / ciclo anterior / recente × modo consulta × desarmado) **dentro do CI**, sem
  Postgres. O teste de integração continua existindo como prova do `TRUNCATE` real.
- Testes de `handler.py`, hoje sem nenhum: montagem de DSN, parse de `data_referencia`, coerção de
  `modo_consulta`, variável de ambiente ausente.
- Testes de `estado.ResultadoExecucao.como_registro`, hoje sem nenhum.

**Correção de defeito latente:**

- A DSN deixa de ser montada por f-string e passa a usar `psycopg.conninfo.make_conninfo`. Senha
  contendo `@`, `/`, `:`, `#`, `?` ou `%` — comum em senha gerada por Secrets Manager — hoje quebra
  o parse da URI, e uma rotação de senha derrubaria a Lambda com erro que aponta para rede.
- `logging.basicConfig` no import do handler é no-op no runtime da Lambda (o runtime já instalou um
  handler no root logger), de modo que `LOG_LEVEL` é silenciosamente ignorado em produção. Passa a
  usar `logger.setLevel`.

**Ferramental (exigido pela skill `python-pro`, hoje inexistente):**

- `pyproject.toml` com configuração de mypy strict, ruff e black; o pacote passa a ser instalável
  em vez de depender de `PYTHONPATH: src`.
- Type hints faltantes: `cur` nas 4 funções de `persistencia.py`, `context` em `lambda_handler`,
  e os `dict` sem parâmetros em `estado.py`, `handler.py` e `rotina.py`.
- O workflow de CI passa a rodar `mypy --strict` e `ruff` além do `pytest`.

**Higiene:**

- Remoção de `existe_dado`: `data_hora_ultima_atlz` é `NOT NULL`, logo `max(...) IS NULL` já
  significa partição vazia, e `classificar_estado` já trata `None` como `VAZIA`. São duas idas ao
  banco e dois lugares decidindo a mesma coisa.
- `enum.StrEnum` no lugar de `class X(str, enum.Enum)`; `dt.UTC` no lugar de `dt.timezone.utc`.
- Docstrings nas funções públicas de `persistencia.py`; correção do encoding misturado em
  `classificacao.py` (`"Retenção deliberada e' de 98 semanas"`).

## Capabilities

### New Capabilities

Nenhuma. A change endurece garantias de capacidades já existentes.

### Modified Capabilities

- `reclamacao-particao-expurgo`: o requisito de registro por execução passa a exigir atomicidade
  entre o esvaziamento e seu registro, e a registrar falha e desarme como ações distinguíveis.
- `ci-testes-unitarios`: a esteira de `expurgo-particao` passa a exigir verificação estática
  (tipos e lint) além da execução de testes, e cobertura da árvore de decisão do expurgo sem
  depender de Postgres.

## Impact

**Código de produção** (`apps/expurgo-particao/src/expurgo_particao/`):

| Arquivo | Mudança |
|---|---|
| `rotina.py` | registro dentro da transação do `TRUNCATE`; captura ampla com `Acao.FALHA`; `RECUSA_DESARMADO`; remoção da chamada a `existe_dado` |
| `estado.py` | duas ações novas no enum; `StrEnum`; `dict[str, object]` em `como_registro` |
| `persistencia.py` | type hints de `cur`; docstrings; remoção de `existe_dado` |
| `handler.py` | `make_conninfo`; `logger.setLevel`; type hints |
| `classificacao.py` | encoding do comentário |

**Novos arquivos**: `pyproject.toml`, `tests/test_rotina_decisao.py`, `tests/test_handler.py`,
`tests/test_estado.py`.

**Infra/CI**: `.github/workflows/ci-testesunitarios-expurgo-particao.yml` ganha passos de `mypy` e
`ruff`; `requirements-dev.txt` ganha as três ferramentas.

**Banco de dados**: a coluna `acao` de `expurgo_particao_registro` passa a receber dois valores
novos (`FALHA`, `RECUSA_DESARMADO`). Se houver `CHECK` ou enum no schema, exige migration —
verificar `v1.0.7.-cria-tabela-registro-expurgo-particao.sql`.

**Espelhamento manual**: nenhum. `calculo.py` — o único módulo espelhado do
`ControleExpurgoAutorizacao` do `contratocommand` — **não é tocado**, logo nenhuma replicação para
o lado Java é necessária.

**Sem impacto**: as cinco apps Java, o `pg_cron` de auditoria (que lê a tabela de registro, cujas
colunas não mudam de nome nem de tipo).
