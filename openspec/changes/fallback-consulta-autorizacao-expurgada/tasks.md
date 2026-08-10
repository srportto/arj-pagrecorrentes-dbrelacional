## 1. Resolver as questões em aberto antes de codificar

- [x] 1.1 Spike de `plan_cache_mode=force_generic_plan`: medir se o replanejamento por chamada
      é eliminado para os prepared statements do Hibernate. Se for, **todos** os números do
      `design.md` mudam de ordem de grandeza e a discussão N2-vs-N3 perde peso. Barato e
      potencialmente decisivo — fazer primeiro.
- [x] 1.2 Decidir se o nível 3 nasce habilitado (proposta: sim, para que anomalia existente
      apareça em vez de virar 404 silencioso). Registrar no `design.md`.
- [x] 1.3 Decidir o nível de log do acerto em N3 — `WARN` ou `ERROR`. A resposta ao cliente é
      sucesso, mas o invariante foi violado.

## 2. Ancorar o comportamento em teste antes de implementar

- [x] 2.1 Teste de integração contra PostgreSQL real, com partições físicas distintas (quente
      + expurgo), seguindo o padrão de `ExpurgoParticaoIntegrationTest` no `contratocommand`:
      autorização transferida para o expurgo é encontrada pelo `GET /{id}`. **Deve falhar
      agora** com 404.
- [x] 2.2 Teste: autorização ativa continua sendo encontrada no nível 1, e os níveis seguintes
      não são acionados.
- [x] 2.3 Teste: linha em partição quente diferente da derivada do id é encontrada no nível 3
      e produz o log de alerta.
- [x] 2.4 Teste: id inexistente devolve 404 depois de esgotar os níveis habilitados.
- [x] 2.5 Teste: com o nível 3 desabilitado, id inexistente devolve 404 sem consultar as demais
      partições quentes.
- [x] 2.6 Teste: duas linhas com o mesmo `id_autorizacao` em partições distintas resultam em
      500, sem escolher uma delas.

> O `arj-contratoquery` ainda não tem teste de integração contra banco real. Reaproveitar o
> padrão `PostgresLocalDisponivelCondition` + schema isolado criado em
> `corrigir-expurgo-merge-version`, em vez de inventar um segundo mecanismo.

## 3. Consultas no repositório

- [x] 3.1 Adicionar em `AutorizacaoRepository` a consulta do nível 2 (`id = ? AND
      id_particao_conta >= 900`), devolvendo lista — não `Optional` — para que a duplicidade de
      D2 seja detectável.
- [x] 3.2 Adicionar a consulta do nível 3 (`id = ? AND id_particao_conta < 900 AND
      id_particao_conta <> :particaoDoId`), também devolvendo lista.
- [x] 3.3 Confirmar por `EXPLAIN` que ambas podam partição como medido: 100 e 888 subplanos.
      Se a poda não ocorrer no SQL gerado pelo Hibernate, o desenho inteiro perde a premissa.

## 4. Cascata no serviço

- [x] 4.1 Implementar a cascata de três níveis em `ConsultarAutorizacaoService`, parando no
      primeiro nível que encontrar.
- [x] 4.2 Implementar a guarda de D2: mais de uma linha em qualquer nível vira erro de
      aplicação, com log do id e das partições envolvidas — sem expor nada disso ao cliente.
- [x] 4.3 Log de alerta no acerto do nível 3, conforme decidido em 1.3.
- [x] 4.4 Propriedade de configuração para habilitar/desabilitar o nível 3, com o default
      decidido em 1.2.
- [x] 4.5 Confirmar que os testes de 2.1–2.6 passam.

## 5. Verificação

- [x] 5.1 `mvn test` no `arj-contratoquery`.
- [x] 5.2 Verificação manual contra o banco local: `GET` da autorização `019fe8ef-…0006`
      (expirada, partição 953) passa a devolver 200; hoje devolve 404.
- [x] 5.3 Medir o tempo real de resposta dos três caminhos (ativa, expurgada, inexistente) e
      registrar no `design.md` — os números atuais são de `EXPLAIN`, não de ponta a ponta pela API.

## 6. Documentação

- [x] 6.1 Atualizar `apps/arj-contratoquery/CLAUDE.md` e `AGENTS.md` (espelhos — manter
      idênticos): descrever a cascata e, sobretudo, **por que ela existe** — o expurgo move a
      linha para uma partição que não é derivável do id. Quem mexer no particionamento precisa
      saber o que quebra.
- [x] 6.2 Corrigir na armadilha 6 do `CLAUDE.md` o que for necessário: a faixa `0–889` continua
      valendo para a validação do id, mas não é mais a única faixa consultada.
- [x] 6.3 Atualizar o fluxo "GET (consulta por id)" na seção de arquitetura, que hoje descreve
      um único `findById`.

## 7. Registrar o que ficou de fora

- [x] 7.1 Abrir change própria para o custo de planejamento da listagem
      (`GET /api/autorizacoes`, ~148 ms por chamada varrendo 989 partições). É maior que o
      problema resolvido aqui e independe dele.
- [x] 7.2 Registrar como pergunta em aberto se 889 partições quentes são necessárias — o custo
      de planejamento de toda consulta do sistema é linear nesse número. Consolidado na mesma
      change de 7.1 (`reduzir-custo-planejamento-consultas`, hipótese H2), por ter a mesma raiz:
      abrir change separada só para o número de partições fragmentaria a investigação.
