## Why

No `contratoquery`, o repository chama-se `AutorizacaoQueryRepository`, seguindo a convenção documentada `{Entidade}QueryRepository`. No `contratocommand`, o repository equivalente chama-se `AutorizacaoRepository`, seguindo a convenção `{Entidade}Repository`. É a única classe do módulo `contratoquery` que carrega "Query" no nome — o próprio artefato Maven e o pacote (`br.com.srportto.contratoquery`) já sinalizam que é o lado de leitura, tornando o sufixo redundante. O objetivo é uniformizar a convenção de nomenclatura de repository entre os dois módulos irmãos.

## What Changes

- Renomear a interface `AutorizacaoQueryRepository` para `AutorizacaoRepository` em `contratoquery` (arquivo + tipo).
- Atualizar o campo que injeta o repository em `ConsultarAutorizacaoService` e `ListarAutorizacoesService`: tipo `AutorizacaoQueryRepository` → `AutorizacaoRepository`, e nome do campo `autorizacaoQueryRepository` → `repository`, alinhando com a convenção enxuta já usada em `contratocommand`.
- Atualizar os mocks e usos correspondentes em `ConsultarAutorizacaoServiceTest` e `ListarAutorizacoesServiceTest`.
- Atualizar `CLAUDE.md`/`AGENTS.md` (espelhos) do `contratoquery` onde citam `AutorizacaoQueryRepository`.
- Atualizar `README.md` do `contratoquery`: todas as menções ao nome da classe e a linha da tabela de convenções de nomenclatura (`Repository | {Entidade}QueryRepository | AutorizacaoQueryRepository` → `Repository | {Entidade}Repository | AutorizacaoRepository`).
- Atualizar a spec `documentacao-contratoquery`, cujo requirement "CLAUDE.md e AGENTS.md são espelhos com guia de orientação rápida" cita `AutorizacaoQueryRepository` nominalmente em um cenário.

**Não é BREAKING**: é um rename de interface Spring Data interna a um único módulo; não há endpoint, contrato REST, schema de banco ou bean exposto externamente afetado. Cada aplicação roda em seu próprio processo/contexto Spring, então não há colisão de nome com o `AutorizacaoRepository` do `contratocommand`.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `documentacao-contratoquery`: o cenário "Conteúdo reflete apenas o que existe na query" cita `AutorizacaoQueryRepository` nominalmente; passa a citar `AutorizacaoRepository`.

## Impact

- **Código (contratoquery, 5 arquivos)**: `application/autorizacao/AutorizacaoQueryRepository.java` (rename), `ConsultarAutorizacaoService.java`, `ListarAutorizacoesService.java`.
- **Testes (2 arquivos)**: `ConsultarAutorizacaoServiceTest.java`, `ListarAutorizacoesServiceTest.java`.
- **Docs**: `CLAUDE.md`, `AGENTS.md` (manter espelhados), `README.md` (inclusive a tabela de convenções).
- **Specs**: delta em `documentacao-contratoquery`.
- **Não tocados**: `contratocommand` (nenhum arquivo), schema de banco, contratos REST, `domain/`, `entrypoint/`.
