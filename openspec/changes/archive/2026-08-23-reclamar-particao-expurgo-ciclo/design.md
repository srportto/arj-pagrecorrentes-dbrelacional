## Context

A tabela `autorizacoes` é particionada por LISTA em `id_particao_conta`, com duas faixas de
significado diferente: **0–888** são partições quentes (hash da conta) e **900–999** formam um ring
buffer semanal de expurgo. `ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(data)` devolve
`900 + (semanas desde o Epoch % 100)`, e `AutorizacaoJpaAdapter.transferirParaExpurgo` move para lá
toda autorização que chega a estado terminal.

Investigação do estado atual, com os números apurados:

- **O anel nunca é reclamado.** Não há código, job, migration ou spec que esvazie qualquer gaveta de
  900 a 999.
- **A fórmula do lado de delete existiu e foi removida.** `obterParticaoExpurgoDrop` saiu em
  `585f584` como código sem chamador. Ela calculava `obterParticaoExpurgoWrite(data_ref) + 2` com
  wraparound, e recusava quando o resultado colidia com a partição de escrita corrente.
- **A data de referência é a finalização, não a vigência.** `CancelarAutorizacaoService:59` passa
  `dataHoraCancelamento.toLocalDate()` e `DecidirAutorizacaoService:57` passa
  `dataHoraUltimaAtualizacao.toLocalDate()`. A spec `expurgo-estados-terminais` exige isso
  explicitamente. A retenção portanto conta a partir da **morte** da autorização.
- **A gaveta vira na quinta-feira.** `ChronoUnit.WEEKS.between` ancora em 1970-01-01, que foi quinta.
  O ciclo não vira domingo nem segunda.
- **O anel está a 11% da primeira volta.** Primeiro commit em 2026-06-07 (semana 2944). Em
  2026-08-22 (semana 2955) as gavetas **944–955** têm dado e as outras **88 são virgens**.
- **A primeira reclamação com efeito é em ~2028-04-20.** O alvo percorre 957→999→900→943 antes de
  chegar em 944, a primeira gaveta com conteúdo: 87 semanas de execuções sem efeito algum.
- **`pg_cron` já está instalado, no preload e com `cron.database_name` configurado** desde a
  migration `v1.0.0`, e nunca foi usado.
- **O ambiente Docker do Floci já resolveu o acesso ao Postgres.** `infra/envs/local/variables.tf:64`
  define `db_host = "host.docker.internal"` para as tasks ECS, e o README de `envs/local` documenta
  isso como fato operacional. Containers criados pelo Floci via `docker.sock` são **irmãos** do
  projeto Compose, não membros dele.

## Goals / Non-Goals

**Goals:**

- Fechar o ring buffer: a gaveta permitida do ciclo é esvaziada antes de o ponteiro de escrita
  chegar nela.
- Nenhum expurgo sobre dado que a retenção ainda protege — a operação recusa e registra em vez de
  apagar quando o estado da gaveta não bate com o esperado.
- Tornar o caminho de expurgo **verificável hoje**, sem esperar 87 semanas.
- Não tomar lock na tabela pai `autorizacoes`.
- Reaproveitar a cadeia de container/IaC já existente (ECR + Floci + Terraform), sem inventar um
  caminho de deploy paralelo.

**Non-Goals:**

- Exportar dado para o MESH antes do expurgo. O MESH não existe; quando existir, ele consome antes
  do `TRUNCATE`. Esta change não cria ponte analítica nem área de quarentena.
- Alterar o tamanho do anel (100 gavetas) ou a granularidade (semanal). Ver D2.
- Dar ao `pg_cron` qualquer poder de expurgo. Ver D5.
- Mexer em `shared_preload_libraries`. Ver D7.
- Alarme ativo (e-mail, SNS, paging). O que esta change entrega é registro estruturado; ligá-lo a um
  canal de notificação é escopo de observabilidade, não desta change.

## Decisions

**D1. `TRUNCATE` na partição folha — não `DETACH`, não `DELETE`, não `DROP` + `CREATE`.**

`TRUNCATE autorizacoes_pe9XX` troca o `relfilenode` da relação: o arquivo antigo é desvinculado no
`COMMIT` e um vazio toma o lugar. O `oid`, o `relpartbound` (`FOR VALUES IN (9XX)`), a linha em
`pg_inherits` e todos os índices filhos permanecem intactos. A partição **continua anexada**; no
próximo ciclo o roteador de tuplas escreve nela sem nenhuma cerimônia de reativação.

- `DETACH` foi descartado por preservar o dado — é a única das quatro operações que não expurga.
- `DELETE` foi descartado por gerar dead tuples, exigir `VACUUM`, inchar o TOAST (a tabela tem
  `metadados JSON` e colunas `TEXT`) e só devolver disco depois. Contradiz a premissa do ring buffer.
- `DROP` + `CREATE` foi descartado por tomar **`ACCESS EXCLUSIVE` na tabela pai duas vezes** (alterar
  o descritor de partições exige isso). A listagem do `contratoquery` não filtra por chave de
  partição e varre as 989 partições, então esse lock congela leitura em todo o sistema. Há ainda um
  efeito colateral concreto: `CREATE TABLE ... PARTITION OF` recria os índices filhos com **nome
  auto-gerado**, e a migration `v1.0.6` nomeia os seus à mão com `IF NOT EXISTS` — o `IF NOT EXISTS`
  não reconheceria o índice de nome diferente e criaria um duplicado na próxima execução.

`TRUNCATE` toma `ACCESS EXCLUSIVE` apenas na folha, é instantâneo, devolve o disco no ato e exige
privilégio granular (`GRANT TRUNCATE`) em vez de ownership da tabela — o que permite à Lambda ter um
papel que só sabe esvaziar 100 tabelas específicas.

**D2. Alvo = partição de escrita + 2; retenção de 98 semanas, documentada como número deliberado.**

Reconstitui a fórmula do método removido. A gaveta em `escrita + k` guarda dado escrito há `100 − k`
semanas, então:

| offset | folga à frente do ponteiro | retenção |
|---|---|---|
| +1 | 1 semana | 99 semanas |
| **+2** | **2 semanas** | **98 semanas (~22,5 meses)** |
| +3 | 3 semanas | 97 semanas |

Mantido em **+2**. O offset +1 foi rejeitado: ganha 1% de retenção e gasta toda a folga, e o anel
**não tolera semana perdida** — se a reclamação falhar na semana W, a gaveta W+2 só se torna crítica
quando o ponteiro chega nela, e as 2 semanas são justamente o prazo de reação.

Crescer o anel para 106 gavetas (que entregaria 104 semanas = 2 anos exatos) foi considerado e
rejeitado. Trocar o módulo faz o ponteiro **saltar** (`900 + 2955 % 100 = 955` vira
`900 + 2955 % 106 = 993`), desalinhando de uma vez a relação "gaveta N = semana X" e fazendo a
reclamação mirar dado 38 semanas mais novo que a política. Seria barato hoje (88 gavetas virgens) e
caro para sempre depois — mas nenhum requisito externo exige 104 semanas, e "98 semanas porque o
anel tem 100 gavetas semanais e 2 são colchão" é uma afirmação mais precisa do que "2 anos". A
correção do documento faz parte desta change.

**D3. Sem fase de dry-run. Em vez disso: data de referência injetável, permanente.**

A formulação inicial era "fase 1 observa, fase 2 apaga". Ela não funciona aqui: por **87 semanas**
não há nada para observar, e todo relatório sairia "gaveta vazia" — indistinguível de cálculo
errado, conexão quebrada ou alvo errado. Pior, ligar o expurgo depois de 20 meses de silêncio faz da
primeira execução com consequência a primeira execução testada.

O risco real não é raio de explosão (não há o que explodir), é **apodrecimento**. As mitigações são
outras:

1. **Data de referência opcional no evento.** Como todo o cálculo é função pura da data, isso
   responde "o que você faria em 2028-04-20?" em milissegundos. É a única forma de exercitar hoje um
   caminho que a realidade só exercita em 2028. O modo consulta (sem aplicar) existe como
   **capacidade permanente**, não como etapa de rollout.
2. **Três estados distinguíveis** (vazia / dado do ciclo anterior / dado recente), com a gaveta vazia
   sendo resultado **normal**, não erro.
3. **Registro do que foi calculado, não só do que foi feito.** Uma execução que não faz nada precisa
   registrar `semana`, `partição de escrita`, `alvo` e `estado` — assim uma derivação de fórmula ou
   de fuso aparece no log mesmo sem nada a expurgar.
4. **Alarme por ausência, não por erro.** Uma Lambda que não faz nada 29.200 vezes é idêntica
   funcionando e quebrada; métrica de erro fica em zero nos dois casos. O sinal útil é a falta do
   registro periódico.
5. **Teste de CI que exercita o `TRUNCATE` de verdade**, com massa sintética datada retroativamente,
   afirmando também que as gavetas **vizinhas** (alvo−1, alvo+1) ficam intactas — é essa asserção que
   pega erro de offset, exatamente o defeito que ficaria dormindo 20 meses.

O expurgo é armado desde o dia 1. É seguro porque por 87 semanas ele encontra gaveta virgem por
construção, e porque a primeira execução com consequência apagará dado de teste de 2026. Um
interruptor por variável de ambiente permanece, como alavanca operacional para 2028 — não como fase.

**D4. Acesso ao Postgres por `host.docker.internal:5432`, seguindo o precedente do ECS.**

O Floci não roda a Lambda dentro de si: ele usa o `docker.sock` montado para pedir ao daemon do host
um container **irmão**, que não pertence ao projeto Compose e portanto não resolve o hostname
`postgres` da rede `postgres_default`. A task ECS enfrenta exatamente esse problema, e a resposta já
escolhida no repositório foi sair pelo host e voltar pela porta publicada
(`infra/envs/local/variables.tf:64`, documentado no README de `envs/local`).

Adotado o mesmo caminho, o que torna desnecessário mexer em `FLOCI_SERVICES_LAMBDA_DOCKER_NETWORK`.
Como o módulo `ecs-service` já recebe `db_host` como variável opaca, o módulo novo herda a mesma
propriedade: local e AWS real divergem no **valor** da variável, não no desenho (na AWS a Lambda vai
para as `private_subnets` com `vpc_config` e security group liberando 5432 para o RDS).

Esta é a única decisão que depende de verificação empírica, e por isso a task 1 é bloqueante.

**Spike executado em 2026-08-22, confirmado.** Com `postgres` e `floci` saudáveis (compose de raiz,
Docker Desktop/Windows):

1. Um container **irmão** avulso (`docker run --rm postgres:18-alpine psql ...`), fora do projeto
   Compose, conectou em `host.docker.internal:5432` e executou `SELECT 1` com sucesso.
2. Uma função Lambda mínima (handler Python puro, sem dependência externa, só checando
   conectividade TCP com `socket.create_connection`) foi publicada no Floci via
   `aws lambda create-function` e rodou como container Docker real
   (`public.ecr.aws/lambda/python:3.13`, nome `floci-spike-expurgo-particao-<sufixo>`). Invocada
   manualmente, resolveu `host.docker.internal:5432` e retornou `TCP_OK`.
3. Um EventBridge Scheduler (`rate(1 minute)`) foi criado apontando para essa função e observado por
   ~200 segundos. O Floci **reaproveitou o mesmo container quente** entre disparos (nenhum container
   novo nasceu — confirma o *warm container pool* citado no doc do Floci), mas os logs em
   CloudWatch Logs (`/aws/lambda/spike-expurgo-particao`) mostram **4 invocações distintas e
   sucessivas** ao longo da janela, cada uma retornando `TCP_OK` de forma independente — confirmando
   que o Scheduler efetivamente dispara a função no intervalo configurado, e não apenas que o
   container existe.

Todos os recursos do spike (função, agendamento, duas roles IAM) foram removidos ao final; nenhum
resíduo permanece no ambiente.

**Conclusão:** D4 confirmado sem ressalvas. `host.docker.internal:5432` é alcançável de dentro do
runtime de Lambda do Floci, o canal de invocação do EventBridge Scheduler funciona, e o
reaproveitamento do container quente não compromete a periodicidade das invocações — reforça, na
verdade, a decisão de D3 de abrir/fechar a conexão ao banco por invocação em vez de reutilizar uma
conexão de driver guardada em variável global, já que o mesmo processo Python sobrevive entre
disparos.

**D5. `pg_cron` como caixa-preta, não como segundo expurgador.**

A ideia original era um dead-man switch que agisse. Rejeitada: **dobrar o número de coisas que podem
apagar dado irreversivelmente não é redundância, é dobrar a superfície de risco.** Duas
implementações da mesma fórmula, mantidas em lugares diferentes (Python versionado vs. SQL dentro de
`cron.job`), divergem com o tempo, e o lado esquecido apaga a gaveta errada. E o offset +2 já dá
2 semanas de prazo de reação humana — não é preciso um segundo robô autônomo, basta **saber**.

O papel adotado é verificar o **resultado** afirmado pela Lambda, o que dá redundância **de
observação** com independência total de pilha: não passa por Floci, rede Docker, EventBridge, Python
nem credencial de aplicação.

Limite reconhecido e documentado: **`pg_cron` não tem saída.** Não publica em SNS, não chama webhook,
e `RAISE WARNING` só chega ao log do container. Se gravar numa tabela, alguém precisa lê-la — e o
candidato natural seria a própria Lambda, que é o que pode estar morto. Portanto o job **não é
alarme**; é registro forense. Para o risco desta change — falha silenciosa por 20 meses — isso é
exatamente o que falta: em 2028 a pergunta será "desde quando isto está quebrado?", e sem registro
ela é inauditável.

Para não criar segunda fonte da verdade, o job **não recalcula a fórmula**: a Lambda grava o alvo que
calculou, e o job confere o estado da gaveta que a Lambda **afirmou** ter mirado. A ausência de
linha é, por si só, o sinal.

**D6. A Lambda vive em `apps/` e sobe pela cadeia ECR já existente.**

`apps/` é onde moram os deployables do monorepo; `infra/` é Terraform e infraestrutura local. A
Lambda é um deployable, ainda que não seja Java nem Spring. O Floci roda Lambda como Docker real a
partir de `public.ecr.aws/lambda/<runtime>`, e `build-and-push.sh` já é um laço sobre um map de
`repositório ECR → diretório` — somar uma entrada custa uma linha e evita um segundo caminho de
publicação de imagem.

**D7. `shared_preload_libraries` permanece exatamente como está.**

`pg_partman_bgw` está carregado e sem uso (o anel é gerido por fórmula na aplicação, não por
partman), e `pgvector` é compilado da fonte no Dockerfile sem consumidor. **Isso é intencional**: o
ambiente Postgres local existe também como demonstração de como montar um PostgreSQL com extensões
auxiliares, e essa capacidade é um objetivo do projeto, não dívida técnica. Esta change não remove
nada do preload, e a task 9.5 verifica isso ao final. Registrar essa intenção por escrito — para que
a próxima pessoa (ou agente) não a confunda com sobra — é escopo da change irmã
`documentar-postgres-local-extensoes`, que cria a capability `local-postgres-environment`.

## Risks / Trade-offs

**O anel não tolera semana perdida, e nenhuma faxina de recuperação é possível.** Se a reclamação
falhar por 3 semanas, a gaveta contaminada não é recuperável mirando um offset maior: a gaveta em
`escrita + k` guarda dado de `100 − k` semanas, então qualquer `k > 2` apagaria dado mais novo que a
retenção. O offset +2 é o único alvo correto — não há folga a explorar. *Mitigação:* cadência de 30
minutos (336 tentativas por semana) é redundância por repetição; a trava de estado impede que a
contaminação vire perda de dado novo (a gaveta contaminada é **recusada**, não expurgada); e o
registro forense torna o início da falha auditável.

**A validação vive inteira no CI por 87 semanas.** Se o teste com massa sintética for frágil ou for
removido num refactor futuro, o caminho de expurgo fica sem cobertura nenhuma e ninguém percebe.
*Mitigação:* o teste afirma o resultado no banco (gaveta alvo vazia, vizinhas intactas, relação ainda
anexada ao pai), não sequência de chamadas — o mesmo critério que `expurgo-estados-terminais` já
impõe, e pelo mesmo motivo empírico.

**`TRUNCATE` não tem `WHERE`.** Apaga tudo que estiver na gaveta, sem perguntar. A trava de estado é
o único `WHERE` que existe. *Mitigação:* a operação inteira roda numa transação com `lock_timeout`;
se a trava reprovar, `ROLLBACK` e nada aconteceu.

**Fila de lock.** A listagem do `contratoquery` varre as 989 partições e pode segurar a gaveta alvo.
Sem `lock_timeout`, a Lambda entraria na fila e, ao ganhar o `ACCESS EXCLUSIVE`, bloquearia todos os
que chegaram depois. *Mitigação:* `SET LOCAL lock_timeout` curto; desistir é seguro porque as 336
execuções semanais são o mecanismo de retry.

**Divergência de relógio.** Se a JVM roda em `America/Sao_Paulo` e a Lambda em UTC, os dois discordam
sobre a semana corrente por 3 horas toda quinta-feira. Não há colisão (a Lambda miraria `escrita + 3`
em vez de `+2`, nunca a gaveta de escrita), mas o alvo fica errado na janela. *Mitigação:* fixar UTC
explicitamente nos dois lados e travar isso em teste.

**Conexão em container morno.** O Floci mantém pool de containers quentes e há 30 minutos de
ociosidade entre invocações; conexão guardada em variável global pode voltar morta. *Mitigação:*
abrir e fechar por invocação — 48 conexões por dia é custo irrelevante nessa cadência.

**Aceito conscientemente:** `TRUNCATE` é irreversível e o MESH não existe. Enquanto ele não existir,
dado expurgado só volta por backup/PITR. Como o primeiro expurgo real é em 2028 e apagará massa de
desenvolvimento de 2026, a janela de risco prático é nula — mas a propriedade é permanente e precisa
estar escrita.

## Migration Plan

Não há migração de dado. A ordem importa por dependência, não por risco:

1. **Spike de rede primeiro (bloqueante).** É a única incógnita que pode derrubar o desenho. Se um
   container irmão não alcançar `host.docker.internal:5432`, D4 cai e o desenho de infraestrutura
   precisa ser refeito antes de qualquer código.
2. Massa sintética na faixa de expurgo — é o que torna tudo abaixo testável.
3. Lambda + testes; publicação e agendamento; job `pg_cron`.
4. Correções de documentação, independentes do resto e sem risco.

Reversão: remover o agendamento (a função para de ser invocada) e o job `pg_cron`. Nada no schema
muda, nenhuma aplicação Java é tocada, e o `contratocommand` continua escrevendo no anel como hoje.

**Achado durante a verificação final (não introduzido por esta change):** `idx_autorizacoes_conta_status_data`
(migration `v1.0.6`) não existe no banco de desenvolvimento local usado para validar esta change —
`SELECT indexrelid::regclass FROM pg_index WHERE indrelid = 'autorizacoes'::regclass` só lista
`pk_autorizacoees` e `uk_autorizacao_empresa_particao`. A migration usa `\gexec`, que só executa via
`psql` direto (não em init script simples), e aparentemente nunca foi aplicada manualmente neste
ambiente. Não afeta o `TRUNCATE`: verificado que `pk_autorizacoees` e
`uk_autorizacao_empresa_particao` permanecem `indisvalid = false` (válidos) após um ciclo completo de
esvaziamento. Fica registrado para quem for rodar a suíte completa num ambiente onde a v1.0.6 tenha
sido aplicada.

## Open Questions

- ~~O ambiente Postgres local merece capability própria?~~ **Resolvido:** sim. A change irmã
  `documentar-postgres-local-extensoes` cria a capability `local-postgres-environment` e assume toda a
  documentação de extensões auxiliares referida em D7. As duas changes são independentes entre si —
  nenhuma bloqueia a outra.
- **Reset do anel.** As gavetas 944–955 guardarão massa de desenvolvimento por 98 semanas. Se em
  algum momento for desejável começar limpo — ou encurtar a espera para ver o mecanismo girar —, o
  caminho é um `TRUNCATE` manual das 100 gavetas. Vale decidir se isso vira script versionado ao
  lado das migrations ou permanece conhecimento tácito.
- **Onde o registro forense é lido.** Esta change entrega a tabela; quem a consulta, com que
  cadência, e se isso um dia vira alarme ativo, fica em aberto.
