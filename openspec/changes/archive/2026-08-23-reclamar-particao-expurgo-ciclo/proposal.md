## Why

O particionamento em ring buffer do `contratocommand` tem **produtor, mas não tem consumidor**.
`transferirParaExpurgo` move autorizações em estado terminal para a gaveta semanal
`900 + (semanas desde o Epoch % 100)`, e **nada jamais esvazia essas 100 gavetas**. Quando o anel
completar a primeira volta, o ponteiro de escrita vai encontrar dado do ciclo anterior ainda dentro
da gaveta que ele está prestes a usar — dado velho e novo misturados na mesma partição, sem nenhum
critério que os separe depois.

A peça que faria a reclamação chegou a existir: `ControleExpurgoAutorizacao.obterParticaoExpurgoDrop`
foi removida no commit `585f584` por não ter chamador de produção. A remoção do código morto foi
correta; o que ela expôs é que o anel nunca teve quem o reclamasse. A spec `expurgo-estados-terminais`
descreve exclusivamente a escrita — não há requisito algum sobre o outro lado do ciclo.

Esta change fecha o ciclo com uma Lambda Python agendada, que a cada 30 minutos verifica a partição
de expurgo permitida da semana e a esvazia quando ela contém dado do ciclo anterior.

## What Changes

- **Nova aplicação `apps/expurgo-particao/`** (Python, empacotada como imagem de Lambda), que a cada
  30 minutos calcula a gaveta alvo do ciclo, classifica seu estado e a esvazia com `TRUNCATE`
  quando ela contém dado do ciclo anterior.
- **`TRUNCATE` na partição folha**, não `DETACH`, não `DELETE`, não `DROP`+`CREATE`. A partição
  permanece anexada ao pai, com o mesmo OID, bound e índices; só o armazenamento é trocado. Nenhum
  lock é tomado na tabela pai.
- **Alvo = partição de escrita + 2** (com wraparound), reconstituindo a fórmula do método removido.
  Duas semanas de folga à frente do ponteiro, 98 semanas de retenção atrás dele.
- **Três estados, não dois**: gaveta vazia → nada a fazer (normal, não é erro); gaveta com dado do
  ciclo anterior → `TRUNCATE`; gaveta com dado recente → `ROLLBACK` e registro de anomalia. Nenhum
  expurgo ocorre sobre dado que a política de retenção ainda protege.
- **Data de referência injetável** no evento, como capacidade permanente — permite perguntar "o que
  você faria em 2028-04-20?" sem esperar dois anos, e é o que torna o caminho de expurgo testável.
- **Infraestrutura**: repositório ECR, imagem publicada pelo `build-and-push.sh` já existente, módulo
  Terraform `lambda-scheduled` (função + EventBridge Scheduler + IAM), tudo provisionado no Floci
  como as duas aplicações Java já são.
- **Job `pg_cron` como registro forense** (caixa-preta), que confere o resultado afirmado pela Lambda
  sem recalcular a fórmula e **sem poder de escrita sobre `autorizacoes`**.
- **Massa sintética na faixa 900–999**, irmã da existente (que só popula as partições quentes), para
  exercitar o caminho de expurgo no CI — o único lugar onde ele existe antes de 2028.
- **Correção de três desvios de documentação** encontrados durante a investigação (ver Impact).

Fora de escopo: exportação do dado para o MESH antes do expurgo; alteração do tamanho do anel;
qualquer mudança em `shared_preload_libraries` (ver Impact); `pg_cron` com poder de expurgo.

## Capabilities

### New Capabilities

- `reclamacao-particao-expurgo`: reclamação periódica da partição de expurgo permitida do ciclo,
  fechando o ring buffer cujo lado de escrita é descrito por `expurgo-estados-terminais`. Cobre o
  cálculo do alvo, a classificação de estado da gaveta, a garantia de que nenhum expurgo ocorre
  sobre dado protegido pela retenção, a consulta sem efeito colateral por data de referência, e o
  registro independente do resultado.

### Modified Capabilities

(nenhuma — `expurgo-estados-terminais` continua descrevendo a escrita exatamente como hoje; esta
change adiciona o lado oposto do ciclo, sem alterar o comportamento existente)

## Impact

- **Código novo**: `apps/expurgo-particao/` (Python), `infra/modules/lambda-scheduled/`, um irmão de
  `gerar-massa-sintetica-representativa.sql` para a faixa de expurgo, e um script de job `pg_cron`.
- **Código tocado**: `infra/envs/local/ecr.tf` (+1 repositório), `infra/envs/local/main.tf`
  (+1 módulo), `infra/envs/local/scripts/build-and-push.sh` (+1 entrada no map `APPS`, que já é um
  laço). Nenhuma alteração em código Java.
- **Documentação corrigida** (três desvios verificados contra o código):
  1. `obterParticaoExpurgoDrop` é descrito como existente em `apps/contratocommand/CLAUDE.md`,
     `AGENTS.md` e em três pontos de `docs/arquitetura/modelo-dados-e-dados-poc-testada-para-essa-implementacao.md`,
     mas foi removido em `585f584`.
  2. O mesmo `CLAUDE.md`/`AGENTS.md` diz que a partição de escrita é calculada a partir de
     `dataFimVigencia`; o código passa o **instante da finalização**
     (`CancelarAutorizacaoService:59`, `DecidirAutorizacaoService:57`), como a spec
     `expurgo-estados-terminais` exige explicitamente.
  3. `docs/arquitetura/modelo-dados-e-dados-poc-testada-para-essa-implementacao.md` promete retenção
     de "2 anos". Com 100 gavetas semanais, 104 semanas é aritmeticamente inalcançável — o teto do
     anel é 99 semanas, e o desenho adotado entrega 98.
- **`shared_preload_libraries` fica intocado.** `pg_partman` está carregado e sem uso, e isso é
  **intencional**: o ambiente local existe também como demonstração de como montar um PostgreSQL com
  extensões auxiliares. Esta change **não** remove nada do preload, e a task 9.5 verifica isso ao
  final. A receita e o registro dessa intenção são escopo da change irmã
  `documentar-postgres-local-extensoes`.
- **Sem impacto observável em runtime por ~87 semanas.** Com base na primeira semana com dado
  (2944, início do projeto), a primeira gaveta com conteúdo só é mirada na semana que começa em
  **2028-04-20**. Até lá toda execução encontra gaveta virgem e não faz nada — motivo pelo qual a
  validação desta change vive inteira no CI, não na produção.
