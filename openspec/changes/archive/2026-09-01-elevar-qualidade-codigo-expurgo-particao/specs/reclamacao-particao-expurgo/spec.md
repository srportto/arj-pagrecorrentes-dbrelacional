## MODIFIED Requirements

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

O esvaziamento e o registro que o relata SHALL ocorrer na mesma transação. NÃO SHALL existir estado
observável em que a partição alvo foi esvaziada e o registro correspondente não existe: como a
ausência de registro é o sinal de rotina parada, um esvaziamento sem registro faria a supervisão
concluir o oposto do que ocorreu, precisamente no caminho destrutivo.

Falha não prevista durante a execução SHALL ser registrada como ação própria, distinguível de
execução sem efeito, antes de ser propagada. Sem isso, uma rotina que falha a cada ciclo e uma
rotina que não está sendo invocada produzem o mesmo sinal — ausência de registro — e a supervisão
não tem como separá-las.

Recusa causada pelo interruptor operacional de desarme do esvaziamento SHALL ser registrada como
ação própria, e NÃO SHALL ser gravada como ausência de ação: o auditor precisa ler por que o
esvaziamento não ocorreu, não deduzi-lo do cruzamento entre estado classificado e ação.

#### Scenario: Execução sem efeito ainda registra o cálculo

- **WHEN** a rotina é executada e a partição alvo está vazia
- **THEN** o registro SHALL conter a semana corrente, a partição de escrita, a partição alvo e o
  estado classificado
- **AND** SHALL indicar explicitamente que nenhuma ação foi tomada

#### Scenario: Esvaziamento e registro são atômicos

- **WHEN** a rotina esvazia a partição alvo
- **THEN** o registro do esvaziamento SHALL ser gravado na mesma transação que o esvaziamento
- **AND** a falha ao gravar o registro SHALL desfazer também o esvaziamento

#### Scenario: Partição esvaziada nunca fica sem registro

- **WHEN** o registro de execuções é inspecionado após um esvaziamento bem-sucedido
- **THEN** SHALL existir exatamente um registro correspondente àquele esvaziamento
- **AND** NÃO SHALL existir partição esvaziada cujo ciclo não tenha registro

#### Scenario: Falha não prevista é registrada antes de propagar

- **WHEN** a execução encontra erro não previsto (por exemplo, tabela de partição inexistente ou
  permissão insuficiente)
- **THEN** o registro SHALL indicar falha como ação, distinguível de ausência de ação
- **AND** o registro SHALL identificar a natureza do erro encontrado
- **AND** a execução SHALL então propagar o erro, para que a invocação seja contabilizada como
  malsucedida

#### Scenario: Falha recorrente é distinguível de rotina não invocada

- **WHEN** a rotina falha em todas as execuções de um período
- **THEN** o registro SHALL conter uma entrada de falha por execução
- **AND** esse resultado SHALL ser distinguível da ausência total de registros, que continua
  significando que a rotina não foi invocada

#### Scenario: Desarme do esvaziamento é registrado explicitamente

- **WHEN** a partição alvo contém dado do ciclo anterior e o interruptor operacional de desarme do
  esvaziamento está ativo
- **THEN** a partição NÃO SHALL ser esvaziada
- **AND** o registro SHALL indicar recusa por desarme como ação, não ausência de ação
- **AND** o estado classificado SHALL continuar sendo registrado normalmente
