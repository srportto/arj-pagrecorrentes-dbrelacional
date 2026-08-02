## MODIFIED Requirements

### Requirement: Enum StatusAutorizacao com grafo de transições nas 4 aplicações

As quatro aplicações do monorepo (`arj-contratocommand`, `arj-contratoquery`, `autorizacaostatus-producer`, `eventos-consumer`) SHALL conter um enum `StatusAutorizacao` com os 8 estados do ciclo de vida da autorização e seus códigos (`RECEBIDA=1`, `PENDENTE_ACEITE=2`, `EM_PROCESSO_ATIVACAO=3`, `ATIVA=4`, `CANCELADA=5`, `REJEITADA=6`, `EXPIRADA=7`, `FINALIZADA=8`), lookup por código (`obterStatusEnumPorIdStatus`) e o grafo de transições embutido, exposto pelo método `podeTransicionarPara(StatusAutorizacao destino)`. As transições permitidas SHALL ser exatamente:

- `RECEBIDA` → `PENDENTE_ACEITE`, `EM_PROCESSO_ATIVACAO`, `REJEITADA`
- `PENDENTE_ACEITE` → `EM_PROCESSO_ATIVACAO`, `REJEITADA`, `EXPIRADA`
- `EM_PROCESSO_ATIVACAO` → `ATIVA`, `REJEITADA`, `EXPIRADA`
- `ATIVA` → `CANCELADA`, `FINALIZADA`, `REJEITADA`
- `CANCELADA`, `REJEITADA`, `EXPIRADA`, `FINALIZADA` → nenhuma (estados terminais)

O enum SHALL ser um espelho manual idêntico entre as aplicações (pacotes próprios, sem módulo compartilhado). Em `arj-contratocommand`, `arj-contratoquery` e `autorizacaostatus-producer` o enum SHALL residir em `domain/enums/` (ou `application/eventos/` no caso do producer, que ainda não tem camada `domain/`); em `eventos-consumer` — que passa a ter camada `domain/` — ele SHALL residir em `domain/enums/`, junto de `TipoEventoAutorizacao`.

#### Scenario: Transição válida é aceita
- **WHEN** `StatusAutorizacao.ATIVA.podeTransicionarPara(CANCELADA)` é consultado
- **THEN** o resultado é `true`

#### Scenario: Transição inválida é negada
- **WHEN** `StatusAutorizacao.ATIVA.podeTransicionarPara(EXPIRADA)` é consultado
- **THEN** o resultado é `false`

#### Scenario: Estado terminal não transiciona
- **WHEN** `podeTransicionarPara` é consultado a partir de `CANCELADA`, `REJEITADA`, `EXPIRADA` ou `FINALIZADA`, para qualquer destino
- **THEN** o resultado é `false`

#### Scenario: Enum presente e idêntico nas 4 apps, em `domain/enums/` no eventos-consumer
- **WHEN** os fontes das 4 aplicações são inspecionados
- **THEN** cada uma contém seu `StatusAutorizacao` com os mesmos 8 valores, códigos e grafo de transições
- **AND** em `eventos-consumer` o enum reside em `br.com.srportto.eventosconsumer.domain.enums`, não mais em `application.eventos`
