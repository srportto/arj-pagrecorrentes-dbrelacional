# maquina-estados-autorizacao

## Purpose

TBD — capacidade criada a partir da mudança `add-maquina-estados-autorizacao`. Descreve
o enum `StatusAutorizacao` com o grafo de transições de estado da autorização e o enum
`TipoEventoAutorizacao` derivado 1:1 do status, espelhados manualmente nas 4 aplicações
do monorepo.
## Requirements
### Requirement: Enum StatusAutorizacao com grafo de transições nas 4 aplicações

As quatro aplicações do monorepo (`contratocommand`, `contratoquery`, `autorizacaostatus-producer`, `eventos-consumer`) SHALL conter um enum `StatusAutorizacao` com os 8 estados do ciclo de vida da autorização e seus códigos (`RECEBIDA=1`, `PENDENTE_ACEITE=2`, `EM_PROCESSO_ATIVACAO=3`, `ATIVA=4`, `CANCELADA=5`, `REJEITADA=6`, `EXPIRADA=7`, `FINALIZADA=8`), lookup por código (`obterStatusEnumPorIdStatus`) e o grafo de transições embutido, exposto pelo método `podeTransicionarPara(StatusAutorizacao destino)`. As transições permitidas SHALL ser exatamente:

- `RECEBIDA` → `PENDENTE_ACEITE`, `EM_PROCESSO_ATIVACAO`, `REJEITADA`
- `PENDENTE_ACEITE` → `EM_PROCESSO_ATIVACAO`, `REJEITADA`, `EXPIRADA`
- `EM_PROCESSO_ATIVACAO` → `ATIVA`, `REJEITADA`, `EXPIRADA`
- `ATIVA` → `CANCELADA`, `FINALIZADA`, `REJEITADA`
- `CANCELADA`, `REJEITADA`, `EXPIRADA`, `FINALIZADA` → nenhuma (estados terminais)

O enum SHALL ser um espelho manual idêntico entre as aplicações (pacotes próprios, sem módulo compartilhado). Em **todas as quatro aplicações** o enum SHALL residir em `domain/enums/` — é regra de negócio pura, sem dependência de framework.

#### Scenario: Transição válida é aceita
- **WHEN** `StatusAutorizacao.ATIVA.podeTransicionarPara(CANCELADA)` é consultado
- **THEN** o resultado é `true`

#### Scenario: Transição inválida é negada
- **WHEN** `StatusAutorizacao.ATIVA.podeTransicionarPara(EXPIRADA)` é consultado
- **THEN** o resultado é `false`

#### Scenario: Estado terminal não transiciona
- **WHEN** `podeTransicionarPara` é consultado a partir de `CANCELADA`, `REJEITADA`, `EXPIRADA` ou `FINALIZADA`, para qualquer destino
- **THEN** o resultado é `false`

#### Scenario: Enum presente e idêntico nas 4 apps
- **WHEN** os fontes das 4 aplicações são inspecionados
- **THEN** cada uma contém seu `StatusAutorizacao` com os mesmos 8 valores, códigos e grafo de transições

#### Scenario: Localização uniforme em domain/enums
- **WHEN** o pacote de `StatusAutorizacao` e `TipoEventoAutorizacao` é inspecionado em cada uma das 4 aplicações
- **THEN** em todas elas o enum reside em `domain/enums/`
- **AND** nenhuma delas mantém o enum em `application/eventos/`

#### Scenario: Enum permanece livre de framework
- **WHEN** os imports de `StatusAutorizacao` e `TipoEventoAutorizacao` são inspecionados
- **THEN** não há import de `org.springframework.*`, `jakarta.*` nem `lombok.*`

### Requirement: Enum TipoEventoAutorizacao mapeado 1:1 ao status

As quatro aplicações SHALL conter um enum `TipoEventoAutorizacao` com 8 valores em bijeção com `StatusAutorizacao` — `RECEPCAO`(RECEBIDA), `PENDENCIA_ACEITE`(PENDENTE_ACEITE), `INICIO_ATIVACAO`(EM_PROCESSO_ATIVACAO), `ATIVACAO`(ATIVA), `CANCELAMENTO`(CANCELADA), `REJEICAO`(REJEITADA), `EXPIRACAO`(EXPIRADA), `FINALIZACAO`(FINALIZADA) — e uma fábrica `porStatus` que deriva o tipo a partir do código de status. Código de status desconhecido SHALL resultar em exceção. O valor `CRIACAO` NÃO SHALL existir.

#### Scenario: Derivação a partir do status
- **WHEN** `TipoEventoAutorizacao.porStatus(4)` é invocado (status `ATIVA`)
- **THEN** o resultado é `ATIVACAO`

#### Scenario: Status desconhecido lança exceção
- **WHEN** `TipoEventoAutorizacao.porStatus(99)` é invocado
- **THEN** uma exceção é lançada identificando o código não reconhecido

#### Scenario: Bijeção completa
- **WHEN** cada um dos 8 códigos de status válidos é passado a `porStatus`
- **THEN** cada código resulta em um valor distinto do enum, cobrindo os 8 valores

### Requirement: Grafo de transições aplicado no fluxo de escrita

O grafo de transições exposto por `StatusAutorizacao.podeTransicionarPara` SHALL ser consultado
pelo `contratocommand` antes de persistir qualquer mudança de status de autorização. Uma
transição não permitida pelo grafo SHALL ser rejeitada, e a mudança de status NÃO SHALL ser
persistida nem gerar evento.

Esta exigência complementa o requisito existente "Enum StatusAutorizacao com grafo de transições
nas 4 aplicações", que hoje determina apenas que o grafo **exista** — sem exigir que seja
aplicado. O grafo passa a ser normativo em runtime.

#### Scenario: Cancelamento de autorização ativa é permitido

- **WHEN** um cancelamento é solicitado para autorização com status `ATIVA`
- **THEN** a transição `ATIVA` → `CANCELADA` SHALL ser reconhecida como válida pelo grafo
- **AND** o cancelamento SHALL prosseguir

#### Scenario: Cancelamento de autorização já cancelada é rejeitado

- **WHEN** um cancelamento é solicitado para autorização com status `CANCELADA`
- **THEN** a requisição SHALL ser rejeitada com erro de regra de negócio
- **AND** os dados de cancelamento existentes NÃO SHALL ser sobrescritos
- **AND** nenhum evento `CANCELAMENTO` SHALL ser publicado

#### Scenario: Cancelamento a partir de qualquer estado terminal é rejeitado

- **WHEN** um cancelamento é solicitado para autorização com status `REJEITADA`, `EXPIRADA` ou
  `FINALIZADA`
- **THEN** a requisição SHALL ser rejeitada com erro de regra de negócio, pois nenhum desses
  estados admite transição

#### Scenario: Validação de transição roda como rule do validador

- **WHEN** o `CancelamentoValidator` do `contratocommand` é inspecionado
- **THEN** ele SHALL incluir uma rule que consulta `podeTransicionarPara`, seguindo o mesmo padrão
  das demais rules de cancelamento

#### Scenario: Método deixa de ser código sem uso em produção

- **WHEN** as referências a `podeTransicionarPara` no `contratocommand` são inspecionadas
- **THEN** SHALL existir ao menos uma chamada em código de produção, além das chamadas em teste

