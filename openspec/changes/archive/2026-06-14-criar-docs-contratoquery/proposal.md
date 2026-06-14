## Why

`arj-contratoquery` (porta 8081, read-only) não possui os arquivos `AGENTS.md`, `CLAUDE.md` e `README.md` que `arj-contratocommand` já tem. Agentes de IA e desenvolvedores que abrem a aplicação query ficam sem orientação rápida sobre propósito, arquitetura, endpoints e convenções, precisando explorar o código do zero.

## What Changes

- Criar `aplicacoes/arj-contratoquery/AGENTS.md` — espelho de `CLAUDE.md`, guia para agentes de IA
- Criar `aplicacoes/arj-contratoquery/CLAUDE.md` — guia de orientação rápida para agentes (arquitetura, endpoints, build, armadilhas)
- Criar `aplicacoes/arj-contratoquery/README.md` — documentação completa para desenvolvedores (stack, estrutura, como executar, API, testes)

## Capabilities

### New Capabilities

- Nenhuma — esta mudança é puramente documental, sem alteração de comportamento da aplicação.

### Modified Capabilities

- Nenhuma — nenhuma spec de comportamento existente é alterada.

## Impact

- Apenas `aplicacoes/arj-contratoquery/` — 3 novos arquivos de documentação
- Sem impacto em código, testes, banco de dados ou configurações
- Sem breaking changes
