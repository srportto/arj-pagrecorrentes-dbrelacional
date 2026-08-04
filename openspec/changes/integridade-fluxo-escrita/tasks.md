## 1. Levantamento antes de tocar no banco

- [ ] 1.1 Rodar `SELECT id_autorizacao_empresa, COUNT(*) FROM autorizacoes GROUP BY 1 HAVING COUNT(*) > 1` em cada ambiente e registrar o resultado
- [ ] 1.2 Se houver duplicatas, escalar para decisão de negócio sobre o tratamento (quais linhas manter) — bloqueia a task 2.2 até resolver
- [ ] 1.3 Verificar se o Postgres aceita constraint `UNIQUE` em `id_autorizacao_empresa` nesta tabela particionada, ou se será necessário índice único por partição via template; registrar a conclusão no `design.md`
- [ ] 1.4 Confirmar como as migrations são aplicadas neste projeto (arquivo em `infra/local/postgres/migrations/`, ferramenta, ordem de nomenclatura) para seguir a convenção vigente

## 2. Migration

- [ ] 2.1 Criar migration adicionando a coluna de versão em `autorizacoes`, com default que popule as linhas existentes
- [ ] 2.2 Adicionar na mesma migration a constraint/índice único em `id_autorizacao_empresa`, na forma definida em 1.3
- [ ] 2.3 Aplicar a migration no ambiente local e confirmar que a tabela e as partições ficaram consistentes

## 3. Lock otimista

- [ ] 3.1 Adicionar campo `@Version` em `domain/entities/Autorizacao.java` do `arj-contratocommand`
- [ ] 3.2 Verificar a entidade `Autorizacao` do `arj-contratoquery`: mapear ou ignorar explicitamente a coluna de versão
- [ ] 3.3 Executar os dois endpoints de leitura do `arj-contratoquery` e confirmar que nada quebrou com a coluna nova
- [ ] 3.4 Escrever teste de concorrência real (Testcontainers + duas threads em transações distintas) que dispara dois cancelamentos simultâneos na mesma autorização
- [ ] 3.5 Rodar o teste de 3.4 e **confirmar empiricamente** que o caminho `delete` + `flush` + `detach` + `save` do `transferirParaNovaParticao` dispara `OptimisticLockException` na segunda transação
- [ ] 3.6 Se 3.5 falhar, adotar lock pessimista (`@Lock(PESSIMISTIC_WRITE)`) na busca do cancelamento e registrar a mudança de decisão no `design.md` (D1)

## 4. Rule de transição de status

- [ ] 4.1 Criar `application/cancelamento/rules/TransicaoStatusValida.java` consultando `StatusAutorizacao.podeTransicionarPara`, seguindo o padrão das rules existentes (`@Order`, interface `CancelamentoRule`)
- [ ] 4.2 Registrar a rule no `CancelamentoValidator`, definindo a ordem em relação a `ProdutoSuportadoCancelamento` e `TipoProdutoCancelamento`
- [ ] 4.3 Testes unitários da rule: transição `ATIVA` → `CANCELADA` aceita; a partir de `CANCELADA`, `REJEITADA`, `EXPIRADA` e `FINALIZADA` rejeitada
- [ ] 4.4 Teste de integração confirmando que cancelar autorização já cancelada retorna erro de negócio e não publica evento

## 5. Idempotência da criação

- [ ] 5.1 Trocar `unique = false` para `unique = true` no mapeamento de `id_autorizacao_empresa` em `domain/entities/Autorizacao.java`
- [ ] 5.2 Adicionar `existsByIdAutorizacaoEmpresa` em `application/AutorizacaoRepository.java`
- [ ] 5.3 Checar duplicidade no `CriarAutorizacaoUseCase` antes do `save`, lançando exceção de negócio quando já existe
- [ ] 5.4 Teste: dois POST com o mesmo `id_autorizacao_empresa` — o primeiro retorna 201, o segundo 409, e existe apenas uma linha no banco
- [ ] 5.5 Teste: confirmar que a segunda tentativa não publica evento `ATIVACAO` adicional

## 6. Contrato de erro

- [ ] 6.1 Mapear `OptimisticLockException` (ou `ObjectOptimisticLockingFailureException`) para 409 com `LayoutErrosApiResponse` no `ApiExceptionHandler`
- [ ] 6.2 Mapear violação de unicidade (`DataIntegrityViolationException` na constraint de `id_autorizacao_empresa`) para 409 com o mesmo formato — cobre a corrida que escapa da verificação da aplicação
- [ ] 6.3 Confirmar que nenhuma das duas respostas expõe nome de classe de exceção, stack trace ou nome de constraint
- [ ] 6.4 Testes do handler para os dois novos mapeamentos

## 7. Validação e documentação

- [ ] 7.1 Rodar a suíte completa do `arj-contratocommand` e do `arj-contratoquery`
- [ ] 7.2 Revisar os cenários dos 3 specs desta mudança (`concorrencia-otimista-autorizacao`, `idempotencia-criacao-autorizacao`, `maquina-estados-autorizacao`) e confirmar que cada um tem teste correspondente
- [ ] 7.3 Confirmar que `podeTransicionarPara` agora tem ao menos uma chamada em código de produção
- [ ] 7.4 Documentar os dois novos caminhos de erro (409 por concorrência, 409 por chave duplicada) no `README.md` e no `CLAUDE.md`/`AGENTS.md` do `arj-contratocommand`, mantendo os dois espelhos idênticos
- [ ] 7.5 Comunicar a mudança de comportamento do POST duplicado a quem integra com a API antes do deploy
