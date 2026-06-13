# health-check

## Purpose

Definir o endpoint de health-check via Spring Boot Actuator nas aplicações `contratocommand` e `contratoquery`, com verificação de conectividade ao PostgreSQL (readiness), e a remoção da rota legada de disponibilidade `/olamundo`.

## Requirements

### Requirement: Health-check via Actuator nas duas aplicações
As aplicações `contratocommand` e `contratoquery` SHALL expor o endpoint `GET /actuator/health` provido pelo Spring Boot Actuator, refletindo a saúde da aplicação e da sua conexão com o PostgreSQL (readiness). Apenas o endpoint `health` SHALL ser exposto via web; demais endpoints do Actuator NÃO SHALL ser expostos.

#### Scenario: Aplicação saudável com banco acessível
- **WHEN** o cliente envia `GET /actuator/health` e o PostgreSQL está acessível
- **THEN** o sistema retorna HTTP 200 com `status` igual a `UP`

#### Scenario: Banco inacessível derruba o health
- **WHEN** o PostgreSQL está inacessível
- **THEN** `GET /actuator/health` retorna HTTP 503 com `status` igual a `DOWN`, refletindo o indicador `db`

#### Scenario: Apenas o endpoint health é exposto
- **WHEN** o cliente envia `GET /actuator/metrics` (ou outro endpoint do Actuator não exposto)
- **THEN** o sistema retorna HTTP 404, pois somente `health` está na lista de exposição

### Requirement: Rota de disponibilidade unificada no Actuator
A verificação de disponibilidade do `contratoquery` SHALL ser feita exclusivamente por `GET /actuator/health`. A rota legada `/olamundo` e seu código associado (`OlamundoController`, `OlamundoService`, `SaudacaoOlamundo`) SHALL ser removidos.

#### Scenario: Rota legada /olamundo não existe mais
- **WHEN** o cliente envia `GET /olamundo` para o `contratoquery`
- **THEN** o sistema retorna HTTP 404 (rota inexistente)
