## ADDED Requirements

### Requirement: Controle de concorrência otimista atravessa o mapper íntegro

Quando uma app migrada usa lock otimista, o token de versão SHALL trafegar do adaptador para o modelo
de domínio e de volta, de modo que o `UPDATE` emitido continue portando a cláusula de versão.

A verificação SHALL ser **empírica**, não por leitura de código: um teste de concorrência real, com
duas transações competindo sobre a mesma linha, SHALL ser executado — não pulado — antes de a migração
ser considerada concluída. Um teste de concorrência pulado NÃO SHALL contar como verificação
cumprida.

A escrita de agregado já existente SHALL reaplicar o estado do modelo sobre a entidade **gerenciada**
dentro da transação. NÃO SHALL montar entidade nova a partir do modelo e submetê-la a `save` ou
`merge` quando o token de versão está preenchido: com versão presente, o provedor trata a instância
como detached e conclui que outra transação removeu a linha, produzindo falha determinística e imune a
retry numa operação que deveria ter sucesso.

#### Scenario: Cláusula de versão preservada no UPDATE

- **WHEN** o SQL emitido numa escrita de agregado existente é inspecionado antes e depois da migração
- **THEN** os dois contêm a cláusula de comparação de versão

#### Scenario: Concorrência real é exercitada, não pulada

- **WHEN** a suíte é executada para fechar a migração
- **THEN** o teste de concorrência aparece entre os testes executados
- **AND** duas escritas concorrentes sobre a mesma linha resultam em falha de lock em ao menos uma delas

#### Scenario: Escrita simples continua tendo sucesso

- **WHEN** uma escrita sobre agregado existente ocorre sem concorrência
- **THEN** ela é persistida com sucesso
- **AND** não resulta em falha de estado obsoleto — sintoma de entidade detached submetida a `merge`

#### Scenario: Conflito na movimentação física continua sendo tratado como conflito

- **WHEN** duas transações disputam um registro cuja escrita implica movê-lo entre partições
- **THEN** a transação perdedora produz erro classificado como conflito de concorrência
- **AND** a resposta HTTP é a mesma de antes da migração, não um erro interno genérico

### Requirement: Identidade do agregado não carrega layout físico no domínio

A geração de identificador que depende do layout físico do armazenamento SHALL ficar atrás de uma
porta de saída declarada em `domain/port/out/` e implementada em `infrastructure/persistence/`.

O modelo de domínio SHALL receber o identificador pronto e NÃO SHALL conhecer partição, faixa de
partição ou qualquer constante derivada do armazenamento. A regra de negócio que acompanha a criação
— estado inicial, datas, defaults — SHALL permanecer no modelo.

A porta SHALL preservar exatamente o algoritmo de geração vigente: os identificadores produzidos
depois da migração SHALL ser idênticos aos que o caminho anterior produziria para as mesmas entradas.

#### Scenario: Domínio pede identidade e não sabe o que há nela

- **WHEN** o modelo de domínio e o caso de uso de criação são inspecionados
- **THEN** nenhum referencia número de partição, faixa de partição ou utilitário de particionamento
- **AND** o identificador chega por uma porta declarada em `domain/port/out/`

#### Scenario: Identificadores gerados são idênticos aos anteriores

- **WHEN** a porta de identidade gera identificadores para um conjunto conhecido de entradas
- **THEN** cada um é idêntico ao que o caminho anterior à migração produzia para a mesma entrada

#### Scenario: Regra de criação permanece no modelo

- **WHEN** o modelo de domínio é inspecionado
- **THEN** o estado inicial por produto, as datas e os defaults continuam sendo responsabilidade dele
- **AND** produto sem estado inicial definido continua falhando explicitamente

### Requirement: contratocommand tem domínio puro

A aplicação `contratocommand` SHALL ter `domain/` livre de mapeamento objeto-relacional, com a
entidade JPA, o mapper, os `AttributeConverter` e os utilitários de particionamento confinados a
`infrastructure/persistence/`.

Este é o requisito que fecha a migração da frota: com ele cumprido, as cinco aplicações de `apps/`
seguem o layout hexagonal clássico.

#### Scenario: Domínio do contratocommand sem ORM

- **WHEN** `apps/contratocommand/src/main/java/br/com/srportto/contratocommand/domain` é inspecionado
- **THEN** nenhuma classe importa `jakarta.persistence` ou `org.hibernate`
- **AND** `org.springframework.*` aparece apenas em `domain/service/`, apenas como anotação de injeção
  e ordenação

#### Scenario: Persistência do contratocommand completa

- **WHEN** `infrastructure/persistence/` do `contratocommand` é inspecionado
- **THEN** contém `AutorizacaoJpaEntity`, os embeddables de chave composta e cancelamento,
  `AutorizacaoPersistenceMapper`, `AutorizacaoJpaAdapter`, `SpringDataAutorizacaoRepository`,
  `TipoProdutoConverter`, `TipoJornadaAutorizacaoConverter`, `ReversibleUUIDv7`,
  `IdContaUUIDPartitionDistributor`, `ControleExpurgoAutorizacao` e o adaptador da porta de identidade

#### Scenario: Unicidade parcial continua não declarada na entidade

- **WHEN** `AutorizacaoJpaEntity` é inspecionada
- **THEN** ela não declara constraint de unicidade para `id_autorizacao_empresa`
- **AND** o comentário que explica o índice único parcial restrito às partições quentes está preservado

#### Scenario: Mapeamento coluna a coluna preservado

- **WHEN** `AutorizacaoJpaEntity` é comparada com a entidade anterior e com as migrations
- **THEN** todos os nomes de coluna, tipos, nulabilidade, precisão, escala e conversores coincidem
- **AND** `metadados` continua mapeada como jsonb

#### Scenario: Fluxos de escrita preservados ponta a ponta

- **WHEN** os três fluxos de escrita são exercitados após a migração
- **THEN** criar `PIX_AUTO` produz status `RECEBIDA` e evento `RECEPCAO`
- **AND** aprovar via decisão produz `ATIVA` e evento `ATIVACAO`
- **AND** criar `DDA_AUTO` produz `ATIVA` e evento `ATIVACAO` diretamente
- **AND** cancelar produz `CANCELADA` e evento `CANCELAMENTO`
- **AND** os message attributes `tipoEvento`, `tipoProduto` e `tipoJornada` são os de antes da migração

#### Scenario: Expurgo continua movendo a linha e a leitura continua encontrando

- **WHEN** uma autorização em estado terminal é transferida para a faixa de partições de expurgo
- **THEN** a movimentação é executada pelo adaptador de persistência
- **AND** o `contratoquery` continua encontrando a autorização por id

#### Scenario: Frota inteira migrada

- **WHEN** as cinco aplicações de `apps/` são inspecionadas
- **THEN** todas estão organizadas em `domain` / `application` / `infrastructure`
- **AND** nenhuma tem pacote de topo `entrypoint` ou `shared`
