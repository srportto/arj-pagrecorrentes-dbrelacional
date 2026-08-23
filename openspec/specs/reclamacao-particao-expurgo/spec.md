# reclamacao-particao-expurgo

## Purpose

Descreve a reclamação periódica da partição de expurgo permitida do ciclo, fechando o lado
consumidor do ring buffer de partições do `contratocommand` cujo lado de escrita é descrito por
`expurgo-estados-terminais`. Cobre o cálculo da partição alvo (escrita + 2 semanas de folga, com
retorno cíclico), a classificação de estado da partição (vazia, dado do ciclo anterior, dado
recente), a garantia de que nenhum expurgo ocorre sobre dado ainda protegido pela retenção, a
consulta sem efeito colateral por data de referência, e a verificação forense independente do
resultado afirmado pela rotina.

## Requirements

### Requirement: Reclamação periódica da partição de expurgo permitida do ciclo

O sistema SHALL executar, em intervalo não superior a 30 minutos, uma rotina que identifica a
partição de expurgo permitida do ciclo corrente, verifica se ela contém dados e, quando contiver
dados do ciclo anterior, a esvazia — fechando o ring buffer cujo lado de escrita é descrito por
`expurgo-estados-terminais`.

O esvaziamento SHALL ser feito por `TRUNCATE` da partição folha. A rotina NÃO SHALL desanexar a
partição (`DETACH`), NÃO SHALL removê-la e recriá-la (`DROP` + `CREATE`), e NÃO SHALL apagar linha a
linha (`DELETE`). Após a operação, a partição SHALL permanecer anexada à tabela `autorizacoes`, com o
mesmo limite de particionamento e com seus índices válidos e anexados ao índice-pai.

A rotina NÃO SHALL adquirir lock sobre a tabela particionada pai.

#### Scenario: Partição do ciclo contém dados do ciclo anterior

- **WHEN** a rotina é executada e a partição alvo do ciclo contém linhas cuja finalização ocorreu no
  ciclo anterior
- **THEN** a partição SHALL ser esvaziada
- **AND** a partição SHALL continuar anexada à tabela `autorizacoes` com o mesmo limite de
  particionamento
- **AND** os índices da partição SHALL permanecer válidos e anexados ao índice-pai
- **AND** nenhuma outra partição SHALL ser afetada

#### Scenario: Partição do ciclo já está vazia

- **WHEN** a rotina é executada e a partição alvo do ciclo não contém linha alguma
- **THEN** nenhuma operação de escrita SHALL ser executada sobre a partição
- **AND** a execução SHALL ser considerada bem-sucedida, NÃO SHALL ser tratada como erro e NÃO SHALL
  gerar alarme

#### Scenario: Partição esvaziada volta a receber escrita no ciclo seguinte

- **WHEN** uma partição foi esvaziada e, em ciclo posterior, o cálculo da partição de escrita aponta
  para ela
- **THEN** a transferência de autorizações para essa partição SHALL ocorrer normalmente, sem exigir
  qualquer operação de reanexação, recriação ou reconstrução de índice

### Requirement: Partição alvo é calculada a partir da partição de escrita com folga de duas semanas

A partição alvo SHALL ser calculada como a partição de escrita do ciclo corrente acrescida de 2,
respeitando o retorno cíclico ao início da faixa quando o resultado ultrapassa o fim dela. A partição
alvo NÃO SHALL, em nenhuma circunstância, coincidir com a partição de escrita do momento.

O cálculo SHALL usar a mesma origem temporal e a mesma definição de semana que
`ControleExpurgoAutorizacao.obterParticaoExpurgoWrite` do `contratocommand`, e SHALL ser feito num
fuso horário fixo e explícito, de modo que a rotina e a aplicação nunca discordem sobre qual é a
semana corrente.

A retenção resultante — 98 semanas — SHALL estar documentada como consequência deliberada do tamanho
do anel e da folga adotada.

#### Scenario: Alvo respeita o retorno cíclico ao início da faixa

- **WHEN** a partição de escrita do ciclo corrente está a menos de duas posições do fim da faixa de
  expurgo
- **THEN** a partição alvo SHALL ser calculada retornando ao início da faixa
- **AND** SHALL permanecer dentro da faixa de partições de expurgo

#### Scenario: Alvo nunca coincide com a partição de escrita

- **WHEN** a partição alvo é calculada para qualquer data
- **THEN** ela SHALL ser diferente da partição de escrita calculada para o mesmo momento

#### Scenario: Fuso horário não altera o alvo entre aplicação e rotina

- **WHEN** o cálculo da semana corrente é feito pela rotina e pela aplicação para o mesmo instante
- **THEN** ambos SHALL obter a mesma semana, independentemente do fuso horário local de cada processo

### Requirement: Nenhum expurgo ocorre sobre dado protegido pela retenção

Antes de esvaziar a partição alvo, a rotina SHALL classificar o estado dela em exatamente um de três
resultados: **vazia**, **contendo dado do ciclo anterior** ou **contendo dado recente**. O
esvaziamento SHALL ocorrer apenas no segundo caso.

Quando a partição alvo contiver dado cuja finalização é recente demais para pertencer ao ciclo
anterior, a rotina NÃO SHALL esvaziá-la, SHALL desfazer a transação sem efeito algum e SHALL
registrar a anomalia — a condição indica escrita fora do fluxo esperado, divergência de relógio ou
erro de cálculo, e nenhuma dessas hipóteses justifica destruir dado.

A verificação e o esvaziamento SHALL ocorrer na mesma transação, de modo que a reprovação da
verificação não deixe efeito residual. A rotina SHALL impor limite de espera por lock, e desistir por
esgotamento desse limite SHALL ser tratado como execução sem efeito, não como falha — a repetição
periódica é o mecanismo de nova tentativa.

#### Scenario: Dado recente na partição alvo impede o expurgo

- **WHEN** a rotina é executada e a partição alvo contém pelo menos uma linha cuja finalização é
  recente demais para pertencer ao ciclo anterior
- **THEN** a partição NÃO SHALL ser esvaziada
- **AND** a transação SHALL ser desfeita sem deixar efeito
- **AND** a anomalia SHALL ser registrada de forma distinguível de uma execução normal

#### Scenario: Espera por lock esgota o limite

- **WHEN** a partição alvo está retida por outra transação além do limite de espera configurado
- **THEN** a rotina SHALL desistir sem alterar dado algum
- **AND** a execução NÃO SHALL ser reportada como falha da rotina
- **AND** a próxima execução periódica SHALL tentar novamente

### Requirement: Consulta por data de referência sem efeito colateral

A rotina SHALL aceitar, como parâmetro opcional de entrada, uma data de referência que substitui a
data corrente em todo o cálculo, e SHALL aceitar um modo de consulta em que nenhuma escrita é
executada.

Em modo de consulta, a rotina SHALL relatar a partição de escrita, a partição alvo, o estado
classificado da partição alvo e a ação que teria sido tomada, sem tomar ação alguma. Essa capacidade
SHALL ser permanente — NÃO SHALL ser uma etapa de implantação a ser removida depois.

#### Scenario: Consulta para data futura não altera dado

- **WHEN** a rotina é invocada com uma data de referência futura e em modo de consulta
- **THEN** ela SHALL relatar a partição alvo e a ação que tomaria naquela data
- **AND** nenhuma partição SHALL ser esvaziada
- **AND** nenhum dado SHALL ser alterado

#### Scenario: Ausência de data de referência usa a data corrente

- **WHEN** a rotina é invocada sem data de referência
- **THEN** o cálculo SHALL usar a data corrente

### Requirement: Cada execução registra o que foi calculado, não apenas o que foi feito

Toda execução SHALL produzir registro estruturado contendo, no mínimo, a semana corrente, a partição
de escrita, a partição alvo, o estado classificado da partição alvo e a ação tomada — **inclusive
quando a ação for nenhuma**.

A justificativa é operacional: durante o primeiro ciclo do anel a rotina não terá efeito algum por
dezenas de semanas, e uma execução sem efeito é indistinguível de uma rotina quebrada se o registro
disser apenas o que foi feito. Registrar o que foi **calculado** torna visível qualquer derivação de
fórmula, de fuso ou de alvo antes de haver dado para expurgar.

A ausência do registro periódico SHALL ser o sinal de que a rotina parou — a supervisão NÃO SHALL
depender exclusivamente de contagem de erros, que permanece em zero tanto na operação normal quanto
na parada silenciosa.

#### Scenario: Execução sem efeito ainda registra o cálculo

- **WHEN** a rotina é executada e a partição alvo está vazia
- **THEN** o registro SHALL conter a semana corrente, a partição de escrita, a partição alvo e o
  estado classificado
- **AND** SHALL indicar explicitamente que nenhuma ação foi tomada

### Requirement: Verificação independente do resultado afirmado pela rotina

O banco de dados SHALL manter uma verificação periódica, independente da infraestrutura em que a
rotina de reclamação executa, que confere o estado da partição que a rotina afirmou ter mirado e
registra o resultado de forma persistente.

Essa verificação NÃO SHALL possuir permissão de escrita sobre `autorizacoes` e NÃO SHALL executar
expurgo em nenhuma circunstância. Ela também NÃO SHALL recalcular a fórmula da partição alvo por
conta própria — SHALL conferir a afirmação registrada pela rotina, de modo que não exista uma segunda
fonte da verdade sobre qual partição deveria ter sido esvaziada.

A ausência de registro da rotina para um ciclo SHALL, por si só, constituir o resultado observável de
que a reclamação não ocorreu.

Esta verificação SHALL ser documentada como registro forense, e NÃO SHALL ser descrita como alarme:
ela não notifica ninguém por conta própria.

#### Scenario: Rotina não executou no ciclo

- **WHEN** a verificação periódica é executada e não existe registro da rotina para o ciclo corrente
- **THEN** a ausência SHALL ser registrada de forma persistente e consultável
- **AND** nenhuma partição SHALL ser esvaziada pela verificação

#### Scenario: Verificação não possui poder de expurgo

- **WHEN** a verificação periódica encontra a partição que a rotina afirmou ter mirado ainda com
  dados
- **THEN** ela SHALL registrar a divergência
- **AND** NÃO SHALL esvaziar a partição

### Requirement: Caminho de expurgo verificável sem depender da passagem do tempo

A suíte automatizada SHALL exercitar o esvaziamento contra um PostgreSQL real, com dados semeados
retroativamente na faixa de partições de expurgo, sem depender de o ring buffer haver completado um
ciclo.

A justificativa é que, num anel de 100 semanas, o consumidor roda sem efeito até o produtor completar
a volta — o único ambiente em que o caminho de expurgo existe antes disso é o de teste. Sem essa
cobertura, a primeira execução com consequência seria também a primeira execução exercitada.

O teste SHALL afirmar o resultado observável no banco, e NÃO SHALL bastar verificar que determinados
comandos foram emitidos.

#### Scenario: Teste afirma o resultado no banco

- **WHEN** o teste de esvaziamento é executado
- **THEN** ele SHALL consultar o banco após a operação e afirmar que a partição alvo está vazia
- **AND** SHALL afirmar que a partição continua anexada à tabela pai, com seus índices válidos

#### Scenario: Partições vizinhas permanecem intactas

- **WHEN** o teste semeia dados na partição alvo e também nas partições imediatamente anterior e
  posterior a ela, e executa o esvaziamento
- **THEN** apenas a partição alvo SHALL ficar vazia
- **AND** as duas partições vizinhas SHALL manter todas as suas linhas

#### Scenario: Recusa de expurgo sobre dado recente é exercitada

- **WHEN** o teste semeia a partição alvo com dado de finalização recente e executa a rotina
- **THEN** a partição SHALL manter todas as suas linhas
- **AND** a anomalia SHALL ser registrada
