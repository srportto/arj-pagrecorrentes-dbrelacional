# Capability: documentacao-contratoquery

## Purpose

Documentação da aplicação `arj-contratoquery` (serviço de leitura, porta 8081), disponibilizando arquivos de orientação rápida (`CLAUDE.md`, `AGENTS.md`) e documentação completa para desenvolvedores (`README.md`) na raiz da aplicação.

## Requirements

### Requirement: Aplicação contratoquery deve possuir arquivos de documentação na raiz
A aplicação `arj-contratoquery` SHALL possuir os arquivos `AGENTS.md`, `CLAUDE.md` e `README.md` na raiz de `aplicacoes/arj-contratoquery/`, com conteúdo específico para o serviço de leitura (porta 8081, read-only).

#### Scenario: Arquivos presentes na raiz da query
- **WHEN** um agente ou desenvolvedor navega até `aplicacoes/arj-contratoquery/`
- **THEN** os arquivos `AGENTS.md`, `CLAUDE.md` e `README.md` SHALL existir nessa raiz

### Requirement: CLAUDE.md e AGENTS.md são espelhos com guia de orientação rápida
Os arquivos `CLAUDE.md` e `AGENTS.md` SHALL ter conteúdo idêntico e cobrir: ponto de entrada (classes-chave para leitura), comandos de build/teste, pré-requisitos, stack, endpoints GET reais, arquitetura hexagonal simplificada, e armadilhas críticas específicas da query.

#### Scenario: Conteúdo específico da query — porta e modo read-only
- **WHEN** um agente lê o `CLAUDE.md` ou `AGENTS.md` da query
- **THEN** o arquivo SHALL indicar que a aplicação roda na porta 8081 e que `DB_READ_ONLY=true` é o padrão

#### Scenario: Conteúdo reflete apenas o que existe na query
- **WHEN** o guia de arquitetura é lido
- **THEN** o arquivo SHALL descrever apenas `ListarAutorizacoesService`, `ConsultarAutorizacaoService` e `AutorizacaoQueryRepository`, sem mencionar orquestradores, use cases de contratação/cancelamento ou mappers que não existem na query

### Requirement: README.md contém documentação completa para desenvolvedores
O `README.md` SHALL conter documentação completa com as mesmas seções do command (stack, estrutura de pacotes, como executar, endpoints, testes, convenções, armadilhas), adaptadas para a query.

#### Scenario: Endpoints documentados refletem apenas GET
- **WHEN** a seção de endpoints do README é lida
- **THEN** SHALL listar apenas `GET /api/autorizacoes` (listagem paginada) e `GET /api/autorizacoes/{autorizacaoId}` (consulta por id), sem POST ou PATCH
