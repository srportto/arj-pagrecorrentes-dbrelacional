## 1. Inventário de vazamentos

- [ ] 1.1 Buscar nos quatro apps por interpolação de objeto em log (`log.info("...{}", objeto)` com objeto de domínio, record Avro, payload ou DTO)
- [ ] 1.2 Inspecionar manualmente os pontos de log das camadas `application` e `entrypoint` dos quatro apps — a busca textual não pega `toString()` implícito nem objeto passado a `MDC.put`
- [ ] 1.3 Verificar se alguma entidade ou DTO usa `@Data`/`@ToString` sem exclusão de campos sensíveis, o que faria qualquer interpolação vazar
- [ ] 1.4 Registrar o inventário completo das ocorrências encontradas — sem ele não é possível afirmar que o vazamento parou, apenas que um parou

## 2. Correção dos logs

- [ ] 2.1 Corrigir `ProcessarEventoAutorizacaoUseCase.java:17-18` do `eventos-consumer` para citar apenas `idAutorizacao` e `tipoEvento`
- [ ] 2.2 Corrigir as demais ocorrências identificadas em 1.4
- [ ] 2.3 Ajustar os testes que verificam conteúdo de log, se existirem
- [ ] 2.4 Adicionar teste que falhe caso o record Avro completo volte a ser interpolado no log do consumer

## 3. Exceções com causa preservada

- [ ] 3.1 Adicionar construtor `(String message, Throwable cause)` em `ApplicationException` do `arj-contratocommand`
- [ ] 3.2 Adicionar o mesmo construtor em `ApplicationException`, `BusinessException` e `ResourceNotFoundException` do `arj-contratoquery`
- [ ] 3.3 Corrigir o `catch (Exception e)` de `CancelarAutorizacaoUseCase` para propagar `e` como causa
- [ ] 3.4 Varrer os dois serviços por outros pontos que encapsulem exceção descartando a causa e corrigi-los
- [ ] 3.5 Teste: falha encapsulada preserva a exceção original como causa

## 4. Sanitização das respostas de erro

- [ ] 4.1 Alterar o handler de `ApplicationException` do `arj-contratocommand` para devolver mensagem genérica, mantendo o log completo no servidor
- [ ] 4.2 Fazer o mesmo no handler de `ApplicationException` do `arj-contratoquery`
- [ ] 4.3 Confirmar que os handlers logam a cadeia completa de causas com stack trace — sanitizar sem preservar o diagnóstico seria trocar um problema por outro
- [ ] 4.4 Confirmar que `BusinessException` continua devolvendo sua mensagem ao cliente, sem alteração
- [ ] 4.5 Testes dos handlers: resposta sem detalhe interno, log com a falha real
- [ ] 4.6 Verificar sobreposição com `blindar-superficie-leitura`, que trata o catch-all de exceção não mapeada no query — evitar trabalho duplicado nos dois changes

## 5. Normatização

- [ ] 5.1 Atualizar `CLAUDE.md`/`AGENTS.md` do `eventos-consumer` com a regra de log, alinhando ao texto já existente no `autorizacaostatus-producer`
- [ ] 5.2 Verificar se os quatro apps têm a regra documentada de forma consistente; alinhar os que não tiverem, mantendo `CLAUDE.md` e `AGENTS.md` idênticos em cada um
- [ ] 5.3 Registrar a lista de campos não logáveis em local único e referenciável, para que a regra não dependa de memória em revisões futuras

## 6. Validação e escalada

- [ ] 6.1 Rodar a suíte completa dos quatro apps
- [ ] 6.2 Subir o fluxo local ponta a ponta, produzir um evento e inspecionar os logs gerados confirmando ausência de campo sensível
- [ ] 6.3 Revisar os 4 requisitos do spec `protecao-dado-sensivel` e confirmar cobertura de cada cenário
- [ ] 6.4 Escalar a decisão sobre os logs históricos já gravados (política de retenção, necessidade de expurgo) — fora do alcance do código, mas parte do encerramento do vazamento
- [ ] 6.5 Confirmar com quem opera se algum fluxo de investigação dependia do dump completo do evento; havendo, acordar o substituto antes de mesclar
