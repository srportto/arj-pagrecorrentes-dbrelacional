# maquina-estados-autorizacao — Delta

## ADDED Requirements

### Requirement: Enum StatusAutorizacao com grafo de transições nas 4 aplicações

As quatro aplicações do monorepo (`arj-contratocommand`, `arj-contratoquery`, `autorizacaostatus-producer`, `eventos-consumer`) SHALL conter um enum `StatusAutorizacao` com os 8 estados do ciclo de vida da autorização e seus códigos (`RECEBIDA=1`, `PENDENTE_ACEITE=2`, `EM_PROCESSO_ATIVACAO=3`, `ATIVA=4`, `CANCELADA=5`, `REJEITADA=6`, `EXPIRADA=7`, `FINALIZADA=8`), lookup por código (`obterStatusEnumPorIdStatus`) e o grafo de transições embutido, exposto pelo método `podeTransicionarPara(StatusAutorizacao destino)`. As transições permitidas SHALL ser exatamente:

- `RECEBIDA` → `PENDENTE_ACEITE`, `EM_PROCESSO_ATIVACAO`, `REJEITADA`
- `PENDENTE_ACEITE` → `EM_PROCESSO_ATIVACAO`, `REJEITADA`, `EXPIRADA`
- `EM_PROCESSO_ATIVACAO` → `ATIVA`, `REJEITADA`, `EXPIRADA`
- `ATIVA` → `CANCELADA`, `FINALIZADA`, `REJEITADA`
- `CANCELADA`, `REJEITADA`, `EXPIRADA`, `FINALIZADA` → nenhuma (estados terminais)

O enum SHALL ser um espelho manual idêntico entre as aplicações (pacotes próprios, sem módulo compartilhado). Em `arj-contratocommand` e `arj-contratoquery` o enum existente SHALL ser evoluído em `domain/enums/`; nas aplicações de eventos (sem camada `domain/`) ele SHALL residir em `application/eventos/`.

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
