## MODIFIED Requirements

### Requirement: CLAUDE.md e AGENTS.md são espelhos com guia de orientação rápida
Os arquivos `CLAUDE.md` e `AGENTS.md` SHALL ter conteúdo idêntico e cobrir: ponto de entrada (classes-chave para leitura), comandos de build/teste, pré-requisitos, stack, endpoints GET reais, arquitetura hexagonal simplificada, e armadilhas críticas específicas da query.

#### Scenario: Conteúdo específico da query — porta e modo read-only
- **WHEN** um agente lê o `CLAUDE.md` ou `AGENTS.md` da query
- **THEN** o arquivo SHALL indicar que a aplicação roda na porta 8081 e que `DB_READ_ONLY=true` é o padrão

#### Scenario: Conteúdo reflete apenas o que existe na query
- **WHEN** o guia de arquitetura é lido
- **THEN** o arquivo SHALL descrever apenas `ListarAutorizacoesService`, `ConsultarAutorizacaoService` e `AutorizacaoRepository`, sem mencionar orquestradores, use cases de contratação/cancelamento ou mappers que não existem na query
