> Ordem por dependência: cada grupo é entregável e verificável isoladamente. Os grupos 1 e 2 não se
> tocam — podem ser feitos em qualquer ordem. O grupo 3 depende do 2 (a assinatura do comando muda).
> Rodar `mvn test` ao fim de cada grupo, não só no final.

## 1. Faixa numérica validada na borda

- [ ] 1.1 Adicionar `@Min(0) @Max(1)` em `indicadorUsoLimiteConta` e `@Max(32767)` em
      `quantidadeDividasCiclo` no record `CriarAutorizacaoRequest`, com mensagens no mesmo padrão
      das existentes (`"O campo 'X' deve ..."`)
- [ ] 1.2 Adicionar as mesmas constraints em `AtualizarDadosRecorrenciaRequest`, preservando a
      semântica de PATCH parcial (campo ausente/`null` continua significando "não altera" — `@Min`
      e `@Max` não disparam em `null`)
- [ ] 1.3 Comentar o teto 32767 como limite físico do `short`, não regra de negócio, apontando o
      gatilho de revisão (design.md, D4) — sem esse comentário o número é lido como regra
- [ ] 1.4 Varrer os demais campos dos records de request e confirmar que nenhum outro campo com
      destino `short` no modelo `Autorizacao` ficou sem `@Max` (cenário "Nenhum campo numérico
      convertido para short fica sem teto")
- [ ] 1.5 Testes em `AutorizacaoControllerTest`: criação com `quantidadeDividasCiclo = 32768` → 422;
      criação com `indicadorUsoLimiteConta = 2` → 422; criação com `quantidadeDividasCiclo = 32767`
      → persiste exatamente 32767 (guarda contra truncamento, não só contra o status)
- [ ] 1.6 Testes equivalentes para a rota de atualização em `AtualizarDadosRecorrenciaServiceTest`
      e/ou no teste de controller, incluindo o caso "campo ausente não altera" ainda passando

## 2. Identificador validado na borda

- [ ] 2.1 Criar o record `AutorizacaoId` em `domain/model/`, com fábrica que valida o formato UUID e
      lança `BusinessException` para entrada malformada ou nula (design.md, D1) — Java puro, sem
      Spring, sem importar `infrastructure`
- [ ] 2.2 Testar `AutorizacaoId` isoladamente: UUID válido constrói; string malformada, vazia e
      `null` lançam `BusinessException`; o valor exposto é igual ao UUID de entrada
- [ ] 2.3 Trocar `String idAutorizacao` por `AutorizacaoId` em `CancelarAutorizacaoCommand`,
      `DecidirAutorizacaoCommand` e `AtualizarDadosRecorrenciaCommand` (nos `doRequest` e em
      `comAutorizacaoCarregada`)
- [ ] 2.4 Construir o `AutorizacaoId` no `AutorizacaoController`, nas três rotas PATCH
- [ ] 2.5 Remover as três chamadas a `UUID.fromString` dos use cases de escrita
- [ ] 2.6 Ajustar os call sites quebrados pela mudança de assinatura (o compilador aponta todos) —
      inclui as rules que leem `comando.idAutorizacao()`, se houver, e os `*ServiceTest`
- [ ] 2.7 Testes de contrato nas três rotas: `PATCH /api/autorizacoes/nao-e-uuid/{cancelar,decisao,
      atualizar}` → 422 com `LayoutErrosApiResponse`, **não** 500
- [ ] 2.8 Teste de regressão: UUID bem formado de autorização inexistente continua 422 por
      `BusinessException`, com a mesma mensagem de antes (não pode virar erro de formato)
- [ ] 2.9 Confirmar que id malformado não gera log em nível `ERROR` — o caminho não passa mais pelo
      `@ExceptionHandler(Exception.class)`

## 3. Fonte única de carregamento e `catch` estreitado

- [ ] 3.1 Criar o colaborador de carregamento em `application/usecase` (nome de domínio, grepável —
      não `Helper`/`Manager`/`Util`), encapsulando: buscar por `AutorizacaoId`, lançar
      `BusinessException` com a mensagem atual quando não encontrada, e devolver o status atual
      resolvido para enum
- [ ] 3.2 Estreitar o tratamento de erro: `ConcurrencyFailureException` e subclasses **não** são
      capturadas nem reembaladas em `ApplicationException` (design.md, D3); `BusinessException`
      segue repassada; demais exceções continuam virando `ApplicationException`
- [ ] 3.3 Substituir o método `obterAutorizacaoPorId` privado nos três use cases pela chamada ao
      colaborador, e remover as três cópias
- [ ] 3.4 Teste unitário do colaborador: encontrada devolve o modelo; ausente lança
      `BusinessException` com a mensagem preservada; `ConcurrencyFailureException` do repositório
      **propaga** em vez de virar `ApplicationException`
- [ ] 3.5 Teste por use case confirmando que conflito de concorrência no carregamento resulta em 409
      e não 500 (cenário novo de `coesao-contratocommand`)
- [ ] 3.6 Confirmar por inspeção que nenhum dos três use cases declara método próprio de
      carregamento (cenário "Lógica de carregamento não é duplicada")

## 4. Verificação e fechamento

- [ ] 4.1 `mvn test` verde em `apps/contratocommand`, com a contagem de testes maior que a inicial
      (nenhum teste foi apenas removido)
- [ ] 4.2 Subir a app localmente e exercitar manualmente os quatro casos corrigidos: id malformado
      nas três rotas PATCH e `quantidadeDividasCiclo = 32768` no POST — confirmando status **e**
      ausência de `ERROR` no log
- [ ] 4.3 Rodar a consulta de verificação de dado histórico truncado (design.md, Migration Plan) e
      registrar o resultado na change; se retornar linhas, abrir trabalho separado — não corrigir
      dado aqui
- [ ] 4.4 Atualizar `apps/contratocommand/CLAUDE.md` **e** `AGENTS.md` (são espelhos — devem ficar
      idênticos): tabela de códigos de erro, e a nota de que o id é validado na borda
- [ ] 4.5 Atualizar o grafo `graphify` (`graphify-out/`), conforme exigido pelo `CLAUDE.md` da raiz
      ao fim de cada change
- [ ] 4.6 Responder as duas Open Questions do design.md ou registrá-las explicitamente como
      pendentes antes de arquivar
