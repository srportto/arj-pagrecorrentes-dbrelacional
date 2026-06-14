## 1. CLAUDE.md e AGENTS.md

- [x] 1.1 Criar `aplicacoes/arj-contratoquery/CLAUDE.md` com seção "Comece por aqui" apontando para `AutorizacaoController.java`, `ListarAutorizacoesService.java`, `ConsultarAutorizacaoService.java` e `Autorizacao.java` da query
- [x] 1.2 Adicionar seções "Build & Testes", "Pré-requisitos", "Stack" adaptadas para a query (porta 8081, DB_READ_ONLY=true, sem DDA/PIX use cases)
- [x] 1.3 Adicionar seção "Endpoints reais" com apenas `GET /api/autorizacoes` e `GET /api/autorizacoes/{autorizacaoId}` e `GET /actuator/health`
- [x] 1.4 Adicionar seção "Arquitetura" descrevendo apenas as 4 camadas da query (`entrypoint`, `application`, `domain`, `shared`) sem orquestradores nem Strategy Pattern
- [x] 1.5 Adicionar seção "Armadilhas críticas" com particularidades da query (porta 8081, read-only, sem POST/PATCH, particionamento na leitura via JPQL)
- [x] 1.6 Adicionar seção "Checklist antes do commit" equivalente ao command
- [x] 1.7 Criar `aplicacoes/arj-contratoquery/AGENTS.md` com conteúdo idêntico ao `CLAUDE.md` recém-criado

## 2. README.md

- [x] 2.1 Criar `aplicacoes/arj-contratoquery/README.md` com seção "Sobre o Projeto" descrevendo a query como serviço de leitura (GET) do par de microserviços
- [x] 2.2 Adicionar seção "Stack Técnico" (mesma tabela do command, sem MapStruct)
- [x] 2.3 Adicionar seção "Estrutura do Projeto" com árvore de pacotes real da query (`application/autorizacao/`, `domain/`, `entrypoint/`, `shared/`)
- [x] 2.4 Adicionar seção "Arquitetura Hexagonal" adaptada: fluxo de uma requisição GET (Controller → ListarAutorizacoesService → AutorizacaoQueryRepository)
- [x] 2.5 Adicionar seção "Como Executar" com pré-requisitos, variáveis de ambiente (`DB_NAME`, `DB_USER_NAME`, `DB_PASSWORD`, `DB_READ_ONLY=true`), comandos Maven e Docker
- [x] 2.6 Adicionar seção "API REST Endpoints" documentando `GET /api/autorizacoes` (params: `idUnicoContaContratante`, `status`, `pagina`, `tamanho`, `ordenarPor`) e `GET /api/autorizacoes/{autorizacaoId}` com exemplos de resposta e erros 404/422/500
- [x] 2.7 Adicionar seção "Testes" com comandos Maven e lista das classes de teste existentes na query
- [x] 2.8 Adicionar seção "Convenções de Codificação" e "Armadilhas críticas" específicas da query
