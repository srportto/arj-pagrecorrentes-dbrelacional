## MODIFIED Requirements

### Requirement: Ambiente local sobe por um ponto de entrada único

O repositório SHALL prover um ponto de entrada único capaz de subir o ambiente local completo —
banco, infraestrutura de mensageria e as cinco aplicações — sem exigir que a pessoa conheça a
ordem de subida dos ambientes.

A ordem de subida MUST NOT ser conhecimento tácito: se um ambiente depende de outro, a dependência
SHALL estar declarada no arquivo de composição, não apenas descrita em README.

#### Scenario: Subida completa a partir de repositório limpo

- **WHEN** alguém com o repositório recém-clonado e o `.env` preenchido executa o comando de
  subida do ponto de entrada único
- **THEN** as cinco aplicações sobem, o banco sobe com schema aplicado, e a infraestrutura de
  mensageria fica disponível
- **AND** nenhum passo manual anterior de subida de outro compose é necessário

#### Scenario: Nenhuma rede externa não criada

- **WHEN** o arquivo de composição do ambiente local é analisado
- **THEN** nenhuma rede declarada como externa depende de um compose que o próprio caminho
  unificado não sobe

#### Scenario: Cada serviço de aplicação declara depends_on para sua infraestrutura no caminho unificado

- **WHEN** `docker compose config` é executado a partir do `compose.yaml` da raiz (ponto de entrada
  único)
- **THEN** cada um dos 5 serviços de aplicação resolve com `depends_on` e `condition:
  service_healthy` para todo serviço de infraestrutura do qual depende, seguindo o mapeamento:
  - `contratocommand` → `postgres`, `floci`
  - `contratoquery` → `postgres`
  - `autorizacaostatus-producer` → `floci`, `kafka`, `schema-registry`
  - `eventos-consumer` → `kafka`, `schema-registry`
  - `temporiza-autorizacao` → `floci`, `valkey`
- **AND** nenhum desses `depends_on` usa a forma curta (lista sem `condition`), que só espera o
  container iniciar, não ficar saudável
- **AND** `apps/docker-compose.yml`, inspecionado sozinho, não declara nenhum desses `depends_on` —
  eles vivem num arquivo overlay separado, incluído apenas pelo `compose.yaml` da raiz, porque
  `depends_on` só resolve contra serviços definidos no mesmo projeto Compose e `apps/docker-compose.yml`
  não conhece os serviços de infraestrutura quando sobe sozinho (Opção C do README)

#### Scenario: Subida unificada não depende de timing acidental

- **WHEN** o ambiente local sobe do zero com `docker compose up -d` (sem `--build`, para não haver
  o atraso incidental do build mascarando a corrida) e a infraestrutura demora a ficar saudável
- **THEN** cada serviço de aplicação aguarda sua infraestrutura ficar `healthy` antes de iniciar,
  em vez de iniciar, falhar e ser reiniciado por `restart: unless-stopped` até a infraestrutura
  ficar pronta
