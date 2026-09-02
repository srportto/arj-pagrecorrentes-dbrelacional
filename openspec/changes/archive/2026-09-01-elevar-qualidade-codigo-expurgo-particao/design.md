## Context

`apps/expurgo-particao` é a única app do monorepo cuja operação normal destrói dado. A qualidade
do seu núcleo puro é alta — `calculo.py` e `classificacao.py` não tocam I/O, são testados sem
banco, e carregam comentários de proveniência que explicam por que 98 semanas e não 104, por que
offset 2, por que UTC. O problema está na borda: transação, tratamento de erro, ponto de entrada e
ferramental.

O estado atual da cobertura efetiva no CI:

```
calculo.py        ████████████  199 linhas de teste, roda no CI
classificacao.py  ████████      idem
──────────────────────────────────────────────────────────────
rotina.py         ██████        só integração — IGNORADO no CI
persistencia.py   ██████        só integração — IGNORADO no CI
estado.py         ░░░░░░        nenhum teste
handler.py        ░░░░░░        nenhum teste
```

A exclusão de `test_rotina_integracao.py` na esteira é decisão correta e especificada
(`ci-testes-unitarios`), mas o efeito colateral é que **a lógica que executa o `TRUNCATE` nunca é
verificada automaticamente**. O módulo mais perigoso é o menos protegido.

Restrições herdadas que esta change **não** questiona:

- `TRUNCATE` na partição folha, nunca `DETACH`/`DELETE`/`DROP` — é a única opção que não toma
  `ACCESS EXCLUSIVE` na tabela pai e não trava a listagem do `contratoquery`.
- Offset fixo de 2 e retenção de 98 semanas.
- Conexão aberta e fechada a cada invocação, nunca guardada em variável global.
- `calculo.py` espelha `ControleExpurgoAutorizacao` do `contratocommand` — esta change **não toca**
  esse módulo, logo não gera obrigação de replicação para o lado Java.

## Goals / Non-Goals

**Goals:**

- Nenhum estado observável em que a partição foi esvaziada e não existe registro do esvaziamento.
- Falha e desarme deixam de ser silêncio: viram ação registrada e legível pelo auditor `pg_cron`.
- A árvore de decisão do expurgo passa a rodar no CI, sem Postgres.
- `mypy --strict` verde, como a skill `python-pro` exige como critério de conclusão.

**Non-Goals:**

- Alterar a fórmula de partição, o offset, a retenção ou a estratégia de `TRUNCATE`.
- Alterar o esquema da tabela `expurgo_particao_registro` além dos dois valores novos de `acao`.
- Migrar o log para JSON estruturado nos moldes de `padrao-de-logs-java` (ver Open Questions).
- Recalcular a fórmula no `pg_cron` — ele continua auditando o que a rotina **afirmou**, não
  recalculando.
- Qualquer mudança nas cinco apps Java.

## Decisions

### D1 — Registro entra na mesma transação do `TRUNCATE`

Hoje o commit do esvaziamento acontece dentro de `_classificar_e_decidir` e o registro é gravado
depois, num segundo commit. A change move o `INSERT` do registro para dentro da transação que
executa o `TRUNCATE`, de modo que os dois efeitos sejam indivisíveis.

**Trade-off aceito — o lock fica retido por mais tempo:** hoje a gaveta é liberada assim que o
`TRUNCATE` commita; unificando, o `ACCESS EXCLUSIVE` sobre a partição folha permanece até o
`INSERT` do registro concluir. São milissegundos sobre uma tabela de registro pequena e sem
contenção, contra a garantia de que o sinal de supervisão nunca mente no caminho destrutivo. O
lock é sobre a **partição folha**, não sobre a tabela pai `autorizacoes` — a listagem do
`contratoquery` não espera por ele.

**Alternativas consideradas:**

| Opção | Por que não |
|---|---|
| Manter separado e adicionar retry no registro | Retry não fecha a janela: o processo pode morrer antes de qualquer tentativa. Trata sintoma, não causa. |
| Gravar o registro **antes** do `TRUNCATE`, na mesma transação | Funciona igualmente bem para atomicidade, mas registraria a ação como fato consumado antes de sê-lo; se o `TRUNCATE` falhasse, o rollback desfaria os dois — mesmo resultado, ordem menos natural de ler no código. |
| Escrever o registro em outro destino (log, S3) | Introduz segundo sistema onde o `pg_cron` não alcança; a spec fixa o registro em tabela auditável. |

Nos caminhos sem escrita (vazia, recusa, lock timeout) nada muda: `rollback` e depois grava o
registro em transação própria, como já ocorre hoje.

### D2 — `Acao.FALHA` registra o erro antes de propagar

`_classificar_e_decidir` passa a capturar exceção ampla, gravar registro com `Acao.FALHA` e a
natureza do erro, e **re-lançar**. Re-lançar é essencial: a invocação precisa continuar sendo
contabilizada como malsucedida pelo runtime da Lambda; a change adiciona rastro, não engole erro.

A gravação do registro de falha usa conexão/transação própria, já que a transação original está
abortada quando a exceção surge.

**Alternativa:** engolir a exceção e retornar `ResultadoExecucao` com `Acao.FALHA`. Rejeitada — a
métrica de erro da Lambda ficaria em zero durante uma falha permanente, e a spec já alerta que a
supervisão não pode depender só de contagem de erros; degradá-la ainda mais é o caminho errado.

**Caso de borda aceito:** se a própria gravação do registro de falha falhar (banco inacessível), não
há onde registrar. Nesse caso o comportamento volta a ser o atual — erro propagado sem rastro — que
é o melhor possível quando o destino do rastro é o recurso indisponível.

### D3 — `Acao.RECUSA_DESARMADO` em vez de `NENHUMA`

Hoje, desarme sobre dado do ciclo anterior grava `acao=NENHUMA, modo_consulta=False`. É inferível
(`estado=DADO_CICLO_ANTERIOR` mais `acao=NENHUMA` só pode ser desarme), mas obriga o auditor a
deduzir. Num serviço cujo produto é registro forense, o registro deve ser lido, não deduzido.

`modo_consulta` continua produzindo `NENHUMA`, porque a coluna `modo_consulta` já o torna
explícito no próprio registro — não há dedução envolvida.

### D4 — `existe_dado` é removida, não corrigida

`data_hora_ultima_atlz` é `NOT NULL` na tabela, logo `max(...) IS NULL` já significa partição
vazia. E `classificar_estado` **já** trata `None` como `VAZIA`. A verificação prévia duplica uma
decisão que a função pura toma, ao custo de uma segunda ida ao banco:

```python
# antes — duas queries, duas decisões sobre "vazio"
if not existe_dado(cur, alvo):
    estado = EstadoParticao.VAZIA
else:
    estado = classificar_estado(max_data_hora_ultima_atlz(cur, alvo), data_referencia)

# depois — uma query, uma decisão
estado = classificar_estado(max_data_hora_ultima_atlz(cur, alvo), data_referencia)
```

**Verificar antes de remover:** a nulabilidade de `data_hora_ultima_atlz` deve ser confirmada no
DDL da migration, não apenas na entidade JPA. Se a coluna aceitar `NULL`, a equivalência cai e
`existe_dado` volta a ter função — nesse caso, a task correspondente é abandonada e o motivo
anotado.

### D5 — `make_conninfo` no lugar do f-string

```python
# hoje — senha com '@', '/', ':', '#', '?' ou '%' quebra o parse da URI
return f"postgresql://{usuario}:{senha}@{host}:{port}/{nome}"
```

Senha gerada por Secrets Manager contém símbolos por padrão. Uma rotação de senha pode derrubar a
Lambda, e o erro resultante (`could not translate host name`, tipicamente) aponta para rede em vez
de credencial — diagnóstico caro. `psycopg.conninfo.make_conninfo(host=..., password=..., ...)`
escapa corretamente, é da própria biblioteca já em uso, e evita construir uma string que carrega a
senha em claro e pode aparecer em mensagem de exceção.

### D6 — Testes de decisão com cursor falso, sem substituir o teste de integração

`_classificar_e_decidir` já recebe a conexão como parâmetro, então dá para exercitar os seis
caminhos com um duplo de teste, dentro do CI, sem Postgres. Isso **não** substitui
`test_rotina_integracao.py`, que continua sendo a única prova de que o `TRUNCATE` real esvazia a
gaveta certa e deixa as vizinhas intactas — verificando inclusive `pg_inherits` para provar que
nada foi desanexado.

A divisão fica: o CI protege a **decisão**; o teste de integração protege o **efeito**.

**Alternativa:** subir Postgres como service container no workflow. Rejeitada por ora — mudaria a
convenção de separação unitário/integração que `ci-testes-unitarios` fixa para as seis apps, por
um ganho que o duplo de teste já entrega.

### D7 — Ferramental em `pyproject.toml`, não em flags soltas no workflow

A skill `python-pro` fixa `mypy --strict` verde como critério de conclusão, e hoje o módulo não
tem nem `pyproject.toml`. Colocar a configuração em arquivo versionado (e não em flags do
workflow) garante que a verificação do desenvolvedor e a do runner sejam a mesma. Como efeito
secundário, o pacote passa a ser instalável e o `PYTHONPATH: src` do workflow — junto com o
`from conftest import ...` do teste de integração, que hoje funciona por acidente do `sys.path` do
pytest — deixam de ser necessários.

### D8 — Migration só se o schema restringir `acao`

Dois valores novos entram na coluna `acao` de `expurgo_particao_registro`. Se a coluna for `text`
livre, nada a fazer. Se houver `CHECK` ou tipo enum, é migration nova — nunca alteração da
`v1.0.7` já aplicada. A verificação é a primeira task do grupo correspondente.

## Risks / Trade-offs

- **Lock retido por mais tempo no caminho de esvaziamento (D1)** → Milissegundos sobre a partição
  folha, não sobre a tabela pai. Mitigação: o `lock_timeout` de 5s continua valendo para a
  transação inteira; se o `INSERT` do registro travasse, a transação seria abortada e o `TRUNCATE`
  desfeito — que é exatamente o comportamento desejado.

- **Captura ampla de exceção pode mascarar erro de programação (D2)** → O `raise` no fim é o que
  impede isso: nada é engolido, só registrado a caminho. Mitigação: capturar `Exception`, nunca
  `BaseException` — `KeyboardInterrupt` e `SystemExit` continuam passando direto.

- **Dois valores novos de `acao` podem quebrar o job `pg_cron` de auditoria** → O job confere o
  resultado afirmado pela rotina; se ele tiver lista fechada de ações esperadas, precisa
  acompanhar. Mitigação: inspecionar o job em `infra/local/postgres/` como task explícita antes de
  considerar a change concluída.

- **`mypy --strict` pode exigir mudanças além das previstas** → `psycopg` é tipado, mas a
  inferência de `Cursor[Any]` e `Connection[Any]` costuma exigir anotação explícita em mais lugares
  do que o esperado. Mitigação: `# type: ignore` pontual e comentado é aceitável onde o custo da
  anotação correta for desproporcional, conforme a própria skill permite.

- **Remoção de `existe_dado` depende de premissa sobre o DDL (D4)** → Ver a verificação embutida na
  decisão; a task é abandonável sem prejuízo do resto da change.

## Migration Plan

Sem migração de dado. Deploy é substituição da imagem da Lambda; rollback é a imagem anterior.

Ordem que mantém a suíte verde a cada passo:

1. Ferramental (`pyproject.toml`, type hints) — não muda comportamento, estabelece o piso de
   verificação para tudo que vem depois.
2. Testes novos contra o comportamento **atual** — vermelho só onde a change pretende mudar.
3. Integridade forense (D1, D2, D3) — os testes do passo 2 ficam verdes.
4. Higiene (D4 e modernizações).

O passo 3 é o único que muda comportamento observável. Se for preciso interromper a change no
meio, qualquer prefixo desta ordem é um estado coerente para commitar.

## Open Questions

- O log sai como repr de dict Python, enquanto as cinco apps Java emitem JSON logstash por
  `padrao-de-logs-java`. É decisão consciente de tratar esta app como exceção — já que o registro
  forense é a tabela, não o log — ou omissão? Fora do escopo desta change; vale responder antes que
  alguém "corrija" nos dois sentidos.

- `conftest.py` monta nome de tabela com f-string (`f"TRUNCATE autorizacoes_pe{p}"`), exatamente o
  que `persistencia.py` proíbe. Não é explorável (entrada é `int` interno de teste), mas contradiz
  a regra que o módulo declara. Vale alinhar por consistência, ou registrar a exceção
  deliberadamente?
