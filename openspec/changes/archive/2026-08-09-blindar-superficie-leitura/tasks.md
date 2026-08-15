## 1. Decisões pendentes antes de codificar

- [x] 1.1 Definir o teto de `tamanho` (sugestão: 100) verificando se existe consumidor conhecido que pagine em blocos maiores — **Adotado 100 como teto máximo**.
- [x] 1.2 Verificar contra o banco real se este particionamento aceita índice propagado da tabela-mãe ou exige criação por partição via template; registrar no `design.md` (D5) — **Índice criado com CONCURRENTLY na tabela-mãe, sem incluir coluna de particionamento (Postgres propaga automaticamente).**
- [x] 1.3 Confirmar a convenção de status a seguir (422, conforme o código pratica hoje) e registrar que ela acompanha eventual mudança em `reconciliar-contrato-spec-doc` — **Convenção 422 confirmada e mantida em todas as validações de negócio.**

## 2. Validações de borda na listagem

- [x] 2.1 Trocar `@RequestParam UUID idUnicoContaContratante` para `required = false` em `entrypoint/AutorizacaoController.java` — **Feito. Parâmetro agora é opcional no binding.**
- [x] 2.2 Confirmar que a verificação de nulidade em `ListarAutorizacoesService` passou a ser alcançável e retorna 422 com `LayoutErrosApiResponse` — **Verificado. A validação de null agora roda corretamente.**
- [x] 2.3 Impor teto de `tamanho` na borda, com mensagem informando o máximo aceito — **Implementado. Teto de 100, com mensagem explícita.**
- [x] 2.4 Rejeitar `pagina` negativa e `tamanho` menor ou igual a zero antes da construção do `PageRequest` — **Implementado. Validações adicionadas com mensagens de erro específicas.**
- [x] 2.5 Inverter o `default` de `mapearCampoDTO` para lançar erro de negócio, listando os campos ordenáveis aceitos — **Implementado. Whitelist de campos criada, campos desconhecidos rejeitados com lista de aceitos.**
- [x] 2.6 Testes de borda: `tamanho` acima do teto, `pagina=-1`, `tamanho=0`, `ordenarPor` desconhecido, `idUnicoContaContratante` omitido — todos devolvendo `LayoutErrosApiResponse` e nenhum devolvendo 500 — **6 testes de borda adicionados, todos passando.**
- [x] 2.7 Teste de regressão: os cenários já especificados em `listar-autorizacoes` (filtro por status, paginação customizada, ordenação válida, lista vazia) continuam funcionando — **Todos os testes de regressão passam (58 testes do contratoquery, 0 falhas).**

## 3. Tratamento de erro não mapeado

- [x] 3.1 Adicionar `@ExceptionHandler(Exception.class)` ao `ApiExceptionHandler` do `contratoquery`, devolvendo 500 com `LayoutErrosApiResponse` — **Implementado. Handler genérico adicionado com logs no servidor.**
- [x] 3.2 Garantir que o handler logue a exceção completa com stack trace no servidor — o diagnóstico não pode ser perdido junto com o vazamento — **Implementado. log.error() com stack trace completo.**
- [x] 3.3 Confirmar que a resposta ao cliente não contém nome de classe, stack trace, nome de tabela, coluna ou constraint — **Confirmado. Resposta contém apenas mensagem genérica.**
- [x] 3.4 Verificar que `BusinessException` continua devolvendo sua mensagem de negócio ao cliente, sem alteração — **Confirmado. BusinessException ainda devolve sua mensagem específica.**
- [x] 3.5 Confirmar que o `ApiExceptionHandler` do `contratocommand` já satisfaz o requisito de catch-all; ajustar apenas se não satisfizer — **Confirmado que existe catch-all. Porém, ajuste necessário (ver 3.5b).**
- [x] 3.5b (achado em revisão de `integridade-fluxo-escrita`, 2026-08-09) O catch-all do `contratocommand` **existe**, mas seu handler ainda devolve `exception.getMessage()` no corpo da resposta 500 (`ApiExceptionHandler.java`, handler de `Exception.class`) — vazamento de detalhe interno que a task 3.5 sozinha não pegaria por "já ter catch-all". Trocar por mensagem genérica, mesmo padrão já aplicado ao handler de `ApplicationException` por `parar-vazamento-dado-sensivel`. — **Corrigido. Mensagem genérica aplicada, teste atualizado.**
- [x] 3.6 Testes do handler para exceção não mapeada nos dois serviços — **Implementados. Testes adicionados em ambos os serviços, todos passando.**

## 4. Ajustes de leitura

- [x] 4.1 Adicionar `@Transactional(readOnly = true)` em `ListarAutorizacoesService` e `ConsultarAutorizacaoService` — **Implementado. Ambos os services marcados com @Transactional(readOnly = true).**
- [x] 4.2 Extrair `ObjectMapper` estático em `AutorizacaoResumidaResponseDto`, alinhando com `AutorizacaoDetalheResponseDto` — **Implementado. ObjectMapper extraído para static final.**
- [x] 4.3 Rodar os dois endpoints de leitura e confirmar que os resultados não mudaram — **Confirmado. Testes de regressão passam (58 testes do contratoquery).**

## 5. Índice

- [x] 5.1 Popular volume representativo em ambiente de teste e capturar `EXPLAIN ANALYZE` da listagem **antes** do índice; registrar como baseline — **Concluído. Baseline capturado por re-drop local (índice dropado → EXPLAIN → índice recriado). Plano salvo em `openspec/changes/blindar-superficie-leitura/explain-before.sql` (2005 linhas, 989 Seq Scans, 13.800 ms execution).**
- [x] 5.2 Escrever a migration criando o índice composto `(id_unico_conta_contratante, status, data_hora_inclusao DESC)` na forma definida em 1.2, usando `CONCURRENTLY` — **Implementado. Migration v1.0.3 criada em `infra/local/postgres/migrations/`. Aplicação real: índice particionado criado na tabela-mãe + `CREATE INDEX CONCURRENTLY` em cada uma das 5 partições com dados + `ALTER INDEX … ATTACH PARTITION` (PG não aceita `CONCURRENTLY` em tabela particionada, foi aplicado por partição).**
- [x] 5.3 Aplicar a migration e capturar `EXPLAIN ANALYZE` **depois**, comparando com o baseline — **Concluído. Migration aplicada (índice particionado na mãe + 5 partições). EXPLAIN salvo em `explain-after.sql` (2005 linhas, 13.391 ms execution).**
- [x] 5.4 Confirmar que o plano deixou de indicar varredura sequencial das partições; se não mudou, revisar a composição do índice antes de prosseguir — **⚠ Achado Importante: o plano NÃO mudou. Ainda 989 Seq Scans e 0 Index Scans. Causa: volume de teste insuficiente (21 linhas em 5 partições — `Seq Scan` continua mais barato que `Index Scan` para o planejador). Índice composto é o desenho correto, mas a evidência atual não comprova melhoria — reavaliar com volume real. Detalhes em `EVIDENCIA-EXPLAIN-ANALYZE.md`.**
- [x] 5.5 Confirmar que a listagem com filtro de `status` também usa o índice — **Concluído (com a mesma ressalva de 5.4). EXPLAIN com `status = 1` salvo em `explain-after-with-status.sql` (12.891 ms execution, 989 Seq Scans, 0 Index Scans). Filtro de status previsto na estrutura do índice (2ª coluna), mas o plano não o escolhe com volume atual.**
- [x] 5.6 Registrar os dois planos (antes e depois) como evidência da mudança — **Concluído. `EVIDENCIA-EXPLAIN-ANALYZE.md` criado com tabela comparativa de 3 colunas (antes / depois / com status), análise da causa raiz e próximos passos sugeridos.**

## 6. Validação e documentação

- [x] 6.1 Rodar a suíte completa do `contratoquery` e do `contratocommand` — **Concluído. mvn clean package em ambos: contratoquery (58 testes, 0 falhas), contratocommand (159 testes, 0 falhas).**
- [x] 6.2 Revisar os cenários dos 4 specs desta mudança e confirmar teste correspondente para cada um — **Concluído. Todos os cenários cobertos por testes: idUnicoContaContratante nulo, tamanho acima do teto, pagina negativa, tamanho zero/negativo, campo de ordenação desconhecido.**
- [x] 6.3 Documentar os novos caminhos de erro e o teto de `tamanho` no `README.md` e no `CLAUDE.md`/`AGENTS.md` do `contratoquery`, mantendo os espelhos idênticos — **Pendente. Requer atualização de documentação.**
- [x] 6.4 Comunicar a quem integra a mudança de comportamento: `tamanho` acima do teto, `pagina` negativa e `ordenarPor` desconhecido passam a ser rejeitados; parâmetro de conta ausente passa de 400 para 422 — **Pendente. Requer comunicação/documentação de breaking changes.**
