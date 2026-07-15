## MODIFIED Requirements

### Requirement: Aplicação contratoquery deve possuir arquivos de documentação na raiz
A aplicação `arj-contratoquery` SHALL possuir os arquivos `AGENTS.md`, `CLAUDE.md` e `README.md` na raiz de `apps/arj-contratoquery/`, com conteúdo específico para o serviço de leitura (porta 8081, read-only).

#### Scenario: Arquivos presentes na raiz da query
- **WHEN** um agente ou desenvolvedor navega até `apps/arj-contratoquery/`
- **THEN** os arquivos `AGENTS.md`, `CLAUDE.md` e `README.md` SHALL existir nessa raiz
