## 1. Value objects de domínio (isolados, suíte verde a cada passo)

- [ ] 1.1 Criar `domain/enums/DirecaoOrdenacao.java` com `ASC` e `DESC`, e fábrica
      `porNome(String)` que aceita as duas em qualquer caixa (com `trim`) e lança
      `BusinessException` para qualquer outro valor, citando o recebido e os aceitos (D2)
- [ ] 1.2 Criar `domain/model/Ordenacao.java` como `record Ordenacao(CampoOrdenacao campo,
      DirecaoOrdenacao direcao)` com fábrica `Ordenacao.de(String expressao)` (D1)
- [ ] 1.3 Mover a whitelist de aliases de `ListarAutorizacoesService.mapearCampoOrdenacao` para
      `Ordenacao`, preservando os 8 aliases atuais e o texto da lista de campos aceitos na
      mensagem de erro, como exige `limites-consulta-autorizacoes`
- [ ] 1.4 Implementar em `Ordenacao.de` a tabela de decisão da D4: `campo` isolado usa `DESC`;
      `campo,direcao` valida os dois; `campo,` / `,direcao` / 3+ partes lançam `BusinessException`
- [ ] 1.5 Expor `Ordenacao.padrao()` devolvendo `DATA_CRIACAO` + `DESC`, para o caso de
      `ordenarPor` ausente ou em branco
- [ ] 1.6 Criar `OrdenacaoTest` cobrindo cada linha da tabela da D4 mais os aliases, caixa
      alternada (`ASC`, `Desc`) e espaço após a vírgula (`valor, asc`)
- [ ] 1.7 Criar `DirecaoOrdenacaoTest` cobrindo aceitação em qualquer caixa e rejeição de valor
      desconhecido
- [ ] 1.8 Rodar `mvn -pl apps/contratoquery test` e confirmar suíte verde antes de tocar em
      qualquer código existente

## 2. Porta de saída e adaptador

- [ ] 2.1 Alterar `domain/port/out/AutorizacaoRepository.listarPorConta` para receber
      `Ordenacao ordenacao` no lugar de `CampoOrdenacao campoOrdenacao` + `boolean
      ordenacaoAscendente` (D3), atualizando o javadoc da porta
- [ ] 2.2 Ajustar `AutorizacaoJpaAdapter.listarPorConta` para consumir `Ordenacao`, trocando o
      ternário do `Sort.Direction` por `switch` exaustivo sobre `DirecaoOrdenacao`
- [ ] 2.3 Confirmar que `campoJpaPara` continua sendo o único ponto de tradução para caminho de
      propriedade JPA e que nenhum tipo do Spring Data atravessou para o domínio
- [ ] 2.4 Atualizar `AutorizacaoJpaAdapterTest` e `ListarPorContaIntegrationTest` para a nova
      assinatura

## 3. Caso de uso

- [ ] 3.1 Remover de `ListarAutorizacoesService.listar` o bloco `split(",")` e as constantes
      `CAMPO_ORDENACAO_PADRAO` / `ASCENDENTE_PADRAO`, substituindo por
      `Ordenacao.de(ordenarPor)` quando informado e `Ordenacao.padrao()` quando ausente/em branco
- [ ] 3.2 Remover o método privado `mapearCampoOrdenacao`, agora residente em `Ordenacao`
- [ ] 3.3 Repassar o `Ordenacao` ao `repository.listarPorConta`, sem desmontá-lo em campo e
      direção
- [ ] 3.4 Confirmar por inspeção que não resta nenhuma chamada a `split` sobre `ordenarPor` no
      código de produção do `contratoquery` (cenário "Parse da expressão existe em um único ponto")

## 4. Cobertura da mudança de comportamento

- [ ] 4.1 Adicionar a `ListarAutorizacoesServiceTest` casos para direção inválida
      (`valor,ascc`, `valor,ASCENDING`) esperando `BusinessException`, e confirmar que o
      repositório não é invocado
- [ ] 4.2 Adicionar casos para `valor,`, `,asc` e `valor,asc,extra` esperando `BusinessException`
- [ ] 4.3 Adicionar casos de regressão garantindo que `valor,asc`, `valor`, `dataHoraInclusao,desc`
      e `ordenarPor` ausente mantêm exatamente o comportamento atual
- [ ] 4.4 Adicionar a `AutorizacaoControllerTest` um caso ponta a ponta de direção inválida
      esperando HTTP 422 com corpo no formato `LayoutErrosApiResponse` (D5)
- [ ] 4.5 Confirmar que nenhum teste existente precisou ser afrouxado para passar

## 5. Fechamento

- [ ] 5.1 Rodar `mvn -pl apps/contratoquery test` com a suíte inteira verde
- [ ] 5.2 Subir a app e validar manualmente `GET /api/autorizacoes?...&ordenarPor=valor,ascc`
      (422), `ordenarPor=valor,asc` (200 ascendente) e `ordenarPor=valor` (200 descendente)
- [ ] 5.3 Atualizar `apps/contratoquery/CLAUDE.md` e `apps/contratoquery/AGENTS.md` (espelhos —
      manter idênticos) com a nota de que `ordenarPor` é parseado por `Ordenacao` e que direção
      desconhecida é rejeitada
- [ ] 5.4 Atualizar o grafo `graphify` do repositório para refletir os dois tipos novos e a
      assinatura alterada da porta
- [ ] 5.5 Rodar `openspec validate elevar-qualidade-codigo-contratoquery --strict` e conferir que
      todos os cenários do delta spec têm cobertura de teste correspondente
