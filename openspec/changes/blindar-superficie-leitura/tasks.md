## 1. Decisões pendentes antes de codificar

- [ ] 1.1 Definir o teto de `tamanho` (sugestão: 100) verificando se existe consumidor conhecido que pagine em blocos maiores
- [ ] 1.2 Verificar contra o banco real se este particionamento aceita índice propagado da tabela-mãe ou exige criação por partição via template; registrar no `design.md` (D5)
- [ ] 1.3 Confirmar a convenção de status a seguir (422, conforme o código pratica hoje) e registrar que ela acompanha eventual mudança em `reconciliar-contrato-spec-doc`

## 2. Validações de borda na listagem

- [ ] 2.1 Trocar `@RequestParam UUID idUnicoContaContratante` para `required = false` em `entrypoint/AutorizacaoController.java`
- [ ] 2.2 Confirmar que a verificação de nulidade em `ListarAutorizacoesService` passou a ser alcançável e retorna 422 com `LayoutErrosApiResponse`
- [ ] 2.3 Impor teto de `tamanho` na borda, com mensagem informando o máximo aceito
- [ ] 2.4 Rejeitar `pagina` negativa e `tamanho` menor ou igual a zero antes da construção do `PageRequest`
- [ ] 2.5 Inverter o `default` de `mapearCampoDTO` para lançar erro de negócio, listando os campos ordenáveis aceitos
- [ ] 2.6 Testes de borda: `tamanho` acima do teto, `pagina=-1`, `tamanho=0`, `ordenarPor` desconhecido, `idUnicoContaContratante` omitido — todos devolvendo `LayoutErrosApiResponse` e nenhum devolvendo 500
- [ ] 2.7 Teste de regressão: os cenários já especificados em `listar-autorizacoes` (filtro por status, paginação customizada, ordenação válida, lista vazia) continuam funcionando

## 3. Tratamento de erro não mapeado

- [ ] 3.1 Adicionar `@ExceptionHandler(Exception.class)` ao `ApiExceptionHandler` do `arj-contratoquery`, devolvendo 500 com `LayoutErrosApiResponse`
- [ ] 3.2 Garantir que o handler logue a exceção completa com stack trace no servidor — o diagnóstico não pode ser perdido junto com o vazamento
- [ ] 3.3 Confirmar que a resposta ao cliente não contém nome de classe, stack trace, nome de tabela, coluna ou constraint
- [ ] 3.4 Verificar que `BusinessException` continua devolvendo sua mensagem de negócio ao cliente, sem alteração
- [ ] 3.5 Confirmar que o `ApiExceptionHandler` do `arj-contratocommand` já satisfaz o requisito de catch-all; ajustar apenas se não satisfizer
- [ ] 3.6 Testes do handler para exceção não mapeada nos dois serviços

## 4. Ajustes de leitura

- [ ] 4.1 Adicionar `@Transactional(readOnly = true)` em `ListarAutorizacoesService` e `ConsultarAutorizacaoService`
- [ ] 4.2 Extrair `ObjectMapper` estático em `AutorizacaoResumidaResponseDto`, alinhando com `AutorizacaoDetalheResponseDto`
- [ ] 4.3 Rodar os dois endpoints de leitura e confirmar que os resultados não mudaram

## 5. Índice

- [ ] 5.1 Popular volume representativo em ambiente de teste e capturar `EXPLAIN ANALYZE` da listagem **antes** do índice; registrar como baseline
- [ ] 5.2 Escrever a migration criando o índice composto `(id_unico_conta_contratante, status, data_hora_inclusao DESC)` na forma definida em 1.2, usando `CONCURRENTLY`
- [ ] 5.3 Aplicar a migration e capturar `EXPLAIN ANALYZE` **depois**, comparando com o baseline
- [ ] 5.4 Confirmar que o plano deixou de indicar varredura sequencial das partições; se não mudou, revisar a composição do índice antes de prosseguir
- [ ] 5.5 Confirmar que a listagem com filtro de `status` também usa o índice
- [ ] 5.6 Registrar os dois planos (antes e depois) como evidência da mudança

## 6. Validação e documentação

- [ ] 6.1 Rodar a suíte completa do `arj-contratoquery` e do `arj-contratocommand`
- [ ] 6.2 Revisar os cenários dos 4 specs desta mudança e confirmar teste correspondente para cada um
- [ ] 6.3 Documentar os novos caminhos de erro e o teto de `tamanho` no `README.md` e no `CLAUDE.md`/`AGENTS.md` do `arj-contratoquery`, mantendo os espelhos idênticos
- [ ] 6.4 Comunicar a quem integra a mudança de comportamento: `tamanho` acima do teto, `pagina` negativa e `ordenarPor` desconhecido passam a ser rejeitados; parâmetro de conta ausente passa de 400 para 422
