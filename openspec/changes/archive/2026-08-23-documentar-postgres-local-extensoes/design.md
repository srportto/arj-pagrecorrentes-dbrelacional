## Context

O PostgreSQL local é o mais elaborado dos quatro ambientes locais do repositório, e o único
construído a partir de `dockerfile` próprio em vez de imagem pronta. A receita que ele exercita está
hoje distribuída por três arquivos, sem que nenhum deles se refira aos outros:

| Etapa | Arquivo | O que faz |
|---|---|---|
| instalar | `infra/local/postgres/dockerfile` | `apt` para `pg_partman`/`pg_cron`; `git clone` + `make install` para `pgvector` |
| declarar | `infra/local/postgres/postgres-db-v18.yml` | `-c shared_preload_libraries=pg_partman_bgw,pg_cron` |
| criar | `infra/local/postgres/migrations/v1.0.0...sql` | `CREATE EXTENSION IF NOT EXISTS` para as três |

O `README.md` do diretório cobre subir, validar e parar — inclusive já mostrando
`SHOW shared_preload_libraries;` como validação — mas não descreve o procedimento de **somar** uma
extensão, nem por que `pgvector` aparece na etapa 1 e na 3 mas não na 2.

Estado das três extensões hoje:

- **`pg_cron`** — instalado, no preload, criado, `cron.database_name` configurado. Sem uso. A change
  `reclamar-particao-expurgo-ciclo` passa a usá-lo (D5).
- **`pg_partman`** — instalado com background worker (`pg_partman_bgw`), no preload, criado. **Sem
  uso e assim permanecerá**: o ring buffer é gerido por fórmula na aplicação
  (`ControleExpurgoAutorizacao`), não por partman. Existe um `docs/poc-pg-partman-docx.docx`,
  indicando que houve avaliação.
- **`pgvector`** — compilado da fonte, criado, **fora** do preload. Sem uso. É justamente o exemplar
  que demonstra os dois pontos mais sutis da receita: instalação por compilação e extensão que
  dispensa preload.

Existem specs `local-aws-environment`, `local-kafka-environment` e `local-valkey-environment`. A do
PostgreSQL nunca foi criada.

## Goals / Non-Goals

**Goals:**

- Tornar a receita de adição de extensão executável a partir de um único documento.
- Dar critério que funcione para extensão **ainda não usada** no repositório, não só para as três
  presentes.
- Registrar a intenção por trás das extensões sem consumidor, de modo que a informação sobreviva à
  próxima auditoria de higiene.
- Fechar a lacuna do conjunto `local-*-environment`.

**Non-Goals:**

- Adicionar, remover ou atualizar qualquer extensão.
- Alterar `dockerfile`, compose ou migrations. Esta change não toca arquivo executável.
- Reespecificar o que `orquestracao-local-unificada` já cobre. Ver D1.
- Documentar administração de PostgreSQL em geral (tuning, backup, replicação) — o escopo é
  estritamente a construção da imagem com extensões.

## Decisions

**D1. Capability nova, sem tocar em `orquestracao-local-unificada`.**

A investigação encontrou que `orquestracao-local-unificada` **já especifica** três coisas vizinhas:
que o serviço PostgreSQL tem definição única em exatamente um compose; que as migrations são
aplicadas por qualquer caminho de subida; e que `shared_preload_libraries` é declarada numa **única**
diretiva `-c`, com o racional da armadilha da GUC repetida.

Duas saídas foram consideradas:

- *estender aquela capability* — rejeitada porque o assunto dela é **orquestração** (como o ambiente
  local sobe como um todo, com quatro serviços e um ponto de entrada único). A construção da imagem
  do banco e a receita de extensões não são orquestração, e diluiriam o foco dela;
- *capability nova com fronteira explícita* — adotada. O `Purpose` da spec nova nomeia o que fica na
  vizinha, para que a próxima pessoa não procure no lugar errado nem duplique o requisito.

A regra prática que separa as duas: **`orquestracao-local-unificada` responde "como o ambiente
sobe"; `local-postgres-environment` responde "como o banco é construído".**

**D2. O critério de preload é enunciado por natureza da extensão, não por lista.**

Documentar apenas "`pg_partman` e `pg_cron` vão no preload, `pgvector` não" resolve o caso presente e
falha no caso futuro — que é justamente o caso de uso do documento, já que ele existe para quem quer
somar uma extensão **nova**. O critério adotado é a natureza: extensão que registra background worker
ou se engancha em ponto de extensão do servidor precisa ser carregada na inicialização; extensão que
só acrescenta tipos, funções, operadores ou índices não precisa.

As três extensões presentes entram como **ilustração** do critério, não como sua definição — e
`pgvector` é o exemplar valioso aqui, por ser o único que demonstra o lado negativo da regra.

**D3. A intencionalidade vira requisito, não comentário.**

A alternativa mais barata seria um comentário no `dockerfile` dizendo "não remover". Rejeitada: o
repositório tem histórico de remoção de código sem chamador — a change
`corrigir-achados-nao-criticos-auditoria-arquitetural` removeu `obterParticaoExpurgoDrop`,
`findByStatus`, `getPartitionPrecision` e `ExpurgoAutorizacaoService` exatamente por ausência de uso,
e estava certa em fazê-lo. Uma auditoria futura aplicaria o mesmo critério, correto em geral, a um
caso em que ele não vale.

Um comentário é conselho; um requisito é critério de aceitação. Como requisito, a ausência de
consumidor deixa de ser evidência de defeito, e a remoção passa a exigir a afirmação positiva de que
a demonstração não é mais desejada — que é a decisão real em jogo.

**D4. A verificação separa dois estados independentes.**

`SHOW shared_preload_libraries` e a consulta às extensões criadas respondem perguntas diferentes, e o
README hoje só mostra a primeira. Uma extensão pode estar pré-carregada e não criada (biblioteca no
processo, nada no catálogo do banco), ou criada e nunca pré-carregada (`pgvector` é exatamente esse
caso). Quem diagnostica "a extensão não funciona" sem separar os dois estados vai investigar o
arquivo errado — mexer no compose quando o problema está na migration, ou vice-versa.

## Risks / Trade-offs

**Documentação diverge do código com o tempo.** É o risco padrão de toda documentação, e este
repositório já o materializou três vezes só na área de particionamento (`obterParticaoExpurgoDrop`
descrito depois de removido, `dataFimVigencia` onde o código usa o instante da finalização, retenção
de "2 anos" aritmeticamente impossível). *Mitigação:* o documento descreve **procedimento e critério**
— que mudam raramente — em vez de reproduzir o conteúdo dos arquivos, que muda com frequência. Ele
aponta para os três arquivos, não os transcreve.

**Requisito de intencionalidade pode ser lido como imunidade permanente.** Congelar decisão contra
revisão futura é ruim. *Mitigação:* o requisito não proíbe remover — proíbe remover **pela razão
errada**. A saída existe e está nomeada: declarar que a demonstração daquela extensão deixou de ser
desejada.

**Aceito conscientemente:** três extensões carregadas sem consumidor têm custo real, ainda que
pequeno — memória do background worker do partman, tempo de build da compilação do pgvector, e
superfície de atualização. O custo é aceito em troca do propósito demonstrativo, e esta change
registra a troca em vez de deixá-la implícita.

## Migration Plan

Não há migração. Nenhum arquivo executável é alterado — `dockerfile`, `postgres-db-v18.yml` e as sete
migrations ficam byte a byte idênticos, e a task de verificação final confirma isso. A mudança é
inteiramente de documentação e de especificação.

Reversão: reverter o commit. Nenhum efeito em runtime, build ou dado.

## Open Questions

Nenhuma pendente — as três questões levantadas na exploração foram decididas:

- ~~`pgvector` é clonado sem versão fixada.~~ **Decidido: fixar**, em change própria
  (`fixar-versao-pgvector`), que acrescenta a esta capability os requisitos de reprodutibilidade da
  construção e de declaração honesta do que permanece flutuante. Ficou fora daqui para preservar a
  propriedade de que **esta** change não altera arquivo executável algum.
- ~~O `docs/poc-pg-partman-docx.docx` ainda descreve intenção viva?~~ **Decidido: manter como está.**
  O documento fica sem anotação de status. O requisito de intencionalidade desta change já cobre a
  pergunta que importa — por que `pg_partman` está carregado sem uso — de modo que o leitor não
  depende do `.docx` para obter essa resposta.
- ~~As outras specs `local-*-environment` têm `Purpose` como `TBD`?~~ **Resolvido fora de change.**
  Eram 21 specs no repositório inteiro, não só as `local-*`: 15 tinham descrição real precedida do
  boilerplate `TBD — capability criada a partir da mudança X` (removido) e 6 não tinham texto algum
  (`TBD - created by archiving change X. Update Purpose after archive.`), cujo `Purpose` foi escrito
  a partir dos requisitos de cada uma. Nenhum `TBD` resta em `openspec/specs/`.
