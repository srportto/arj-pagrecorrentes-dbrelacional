## MODIFIED Requirements

### Requirement: Enum StatusAutorizacao com grafo de transições nas 4 aplicações

As quatro aplicações do monorepo (`arj-contratocommand`, `arj-contratoquery`, `autorizacaostatus-producer`, `eventos-consumer`) SHALL conter um enum `StatusAutorizacao` com os 8 estados do ciclo de vida da autorização e seus códigos (`RECEBIDA=1`, `PENDENTE_ACEITE=2`, `EM_PROCESSO_ATIVACAO=3`, `ATIVA=4`, `CANCELADA=5`, `REJEITADA=6`, `EXPIRADA=7`, `FINALIZADA=8`), lookup por código (`obterStatusEnumPorIdStatus`) e o grafo de transições embutido, exposto pelo método `podeTransicionarPara(StatusAutorizacao destino)`. As transições permitidas SHALL ser exatamente:

- `RECEBIDA` → `PENDENTE_ACEITE`, `EM_PROCESSO_ATIVACAO`, `REJEITADA`
- `PENDENTE_ACEITE` → `EM_PROCESSO_ATIVACAO`, `REJEITADA`, `EXPIRADA`
- `EM_PROCESSO_ATIVACAO` → `ATIVA`, `REJEITADA`, `EXPIRADA`
- `ATIVA` → `CANCELADA`, `FINALIZADA`, `REJEITADA`
- `CANCELADA`, `REJEITADA`, `EXPIRADA`, `FINALIZADA` → nenhuma (estados terminais)

O enum SHALL ser um espelho manual idêntico entre as aplicações (pacotes próprios, sem módulo compartilhado). Em **todas as quatro aplicações** o enum SHALL residir em `domain/enums/` — é regra de negócio pura, sem dependência de framework. A exceção anterior, que permitia à `autorizacaostatus-producer` mantê-lo em `application/eventos/` por ela não possuir camada `domain/`, deixa de valer: a aplicação passa a ter `domain/enums/` como as demais.

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
