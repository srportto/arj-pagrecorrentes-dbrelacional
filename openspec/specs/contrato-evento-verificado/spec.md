# contrato-evento-verificado Specification

## Purpose
TBD - created by archiving change rede-seguranca-contrato-evento. Update Purpose after archive.
## Requirements
### Requirement: Divergência entre cópias de schema falha o build

O monorepo SHALL possuir verificação automatizada que compara as cópias manuais espelhadas dos
contratos de evento e falha quando divergem:

- `AutorizacaoEventoPayload` em `contratocommand` e em `autorizacaostatus-producer`
- `EventoAutorizacao.avsc` em `autorizacaostatus-producer` e em `eventos-consumer`

A comparação SHALL considerar conjunto de campos, tipos, nulabilidade e nomes de serialização
(`@JsonProperty` no JSON, ordem e união de tipos no Avro). Diferença de formatação, indentação ou
comentário NÃO SHALL causar falha.

#### Scenario: Cópias sincronizadas passam

- **WHEN** as quatro cópias estão sincronizadas e a verificação é executada
- **THEN** a verificação SHALL passar

#### Scenario: Campo adicionado em apenas um lado falha

- **WHEN** um campo é adicionado ao `AutorizacaoEventoPayload` do `contratocommand` sem ser
  replicado no `autorizacaostatus-producer`
- **THEN** a verificação SHALL falhar, identificando o campo e os dois arquivos comparados

#### Scenario: Tipo divergente falha

- **WHEN** um campo existe nas duas cópias do `.avsc` mas com tipos ou nulabilidade diferentes
- **THEN** a verificação SHALL falhar, identificando o campo divergente

#### Scenario: Reformatação não falha

- **WHEN** um `.avsc` é reindentado sem alteração de conteúdo semântico
- **THEN** a verificação SHALL passar

### Requirement: Verificação alcança alteração unilateral no CI

A verificação SHALL ser executada pelo CI de forma que uma alteração que toque **apenas uma** das
aplicações espelhadas seja detectada. Um pipeline que construa somente os módulos afetados pelo
diff NÃO SHALL permitir que a divergência passe sem verificação.

#### Scenario: Alteração em um único app aciona a verificação

- **WHEN** um pull request altera exclusivamente arquivos de `contratocommand`, incluindo o
  `AutorizacaoEventoPayload`, sem tocar o `autorizacaostatus-producer`
- **THEN** o CI SHALL executar a verificação de contrato e falhar se as cópias divergirem

### Requirement: Campo desconhecido no payload não descarta a mensagem

A desserialização do payload JSON no `autorizacaostatus-producer` SHALL tolerar propriedades
desconhecidas, ignorando-as. Uma propriedade não mapeada NÃO SHALL causar exceção de
desserialização, classificação como payload inválido, nem descarte da mensagem.

#### Scenario: Mensagem com campo novo é processada

- **WHEN** uma mensagem SQS chega com uma propriedade não presente no `AutorizacaoEventoPayload`
  do producer
- **THEN** a mensagem SHALL ser desserializada com sucesso, ignorando a propriedade desconhecida
- **AND** o evento correspondente SHALL ser produzido no Kafka com os campos conhecidos

#### Scenario: Campo obrigatório ausente continua sendo rejeitado

- **WHEN** uma mensagem chega sem um dos campos obrigatórios exigidos por
  `AutorizacaoEventoPayloadValidator`
- **THEN** a mensagem SHALL continuar sendo classificada como não-retryable, sem alteração do
  comportamento atual

