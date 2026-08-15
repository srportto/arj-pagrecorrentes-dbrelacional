## Contexto

Levantamento feito em 2026-08-10 sobre as cinco apps de `apps/` (217 arquivos `.java`).

| Frente | Medição | Consequência de projeto |
|---|---|---|
| Anotações de doc de API | 69, em 2 arquivos de produção | Alvo principal |
| Dependência springdoc | 2 `pom.xml` + 2 testes | Remoção em cascata |
| `import` sem uso | 2 em 217 arquivos (0,9%) | Correção pontual, não justifica ferramenta |
| Densidade de comentário | 4% (query) a 10% (producer) | Já dentro do padrão; nada a "resumir" |
| `TODO`/`FIXME` existentes | 13 | Base para o critério de marcação |
| Parâmetro sem uso | não medido | Exige o compilador, não `grep` |

A leitura importante do quadro: a limpeza de imports e comentários — que motivou parte do pedido
original — **já foi feita** pelas capacidades `higiene-codigo-morto` e `higiene-comentarios-codigo`.
O que resta de volume é a documentação de API.

## Decisões

### D1 — Remover sem gerar o `openapi.json` antes

**Decisão:** remoção direta, sem etapa de geração/versionamento do contrato.

**Alternativa descartada:** gerar o `openapi.json` das duas apps, versionar, e só então remover.

**Racional:** decisão explícita do responsável em 2026-08-10. O custo da alternativa não é
trivial — o `OpenApiGenerationTest` **hoje não gera nada**: ele aceita `200 || 404` e passa quando
o springdoc não é auto-configurado no `@WebMvcTest`, que é o caso no build atual. Fazê-lo gerar de
verdade exigiria trocar o slice por `@SpringBootTest` com contexto completo (e, no command, banco)
— para produzir um artefato que ainda seria descartado assim que o gateway assumisse o contrato.

**Confirmado em 2026-08-10:** o gateway **ainda não tem** o contrato; será montado depois. Então
não há premissa de segurança aqui — há uma janela em que o contrato não existe em lugar nenhum.

**Mitigação:** consolidar as três fontes de contrato em `docs/contrato-api-para-gateway.md` antes
de apagar (fase 1), organizado por endpoint. Rascunho legível, não `openapi.json` válido — o
objetivo é que montar o gateway depois não dependa de `git show` de arquivo apagado.

**Onde o rascunho vive (decidido em 2026-08-10):** `docs/`, não a pasta desta change. A pasta da
change vai para `openspec/changes/archive/` no arquivamento — e enterrar num histórico o único
registro de um contrato que ainda vai ser montado é o oposto do que a preservação quer. Em
`docs/` ele fica vivo e visível até o gateway absorvê-lo; aí sim se remove.

Há uma ironia aceita aqui: a change `enxugar-documentacao-repo` está podando `docs/`, e esta
acrescenta um arquivo. É deliberado — o critério de B é remover o que é falso ou duplicado, e
este arquivo não é nenhum dos dois. Ele é a única cópia de informação real, com prazo de validade
declarado.

**Por que rascunho em Markdown e não `openapi.json` gerado:** gerar o JSON de verdade exige o
`@SpringBootTest` com contexto completo (o custo que D1 acabou de descartar). O rascunho custa uma
leitura dos dois controllers e preserva a mesma informação — `description`, `example`, `oneOf` —
numa forma que uma pessoa consegue traduzir para a definição do gateway. Precisão de máquina não
ajuda aqui: o destino é um gateway cuja forma de configuração ainda não foi escolhida.

**O que continua sem espelho:** nada, se a fase 1 for feita. Se for pulada, perdem-se
`description`, `example` e o `oneOf` do 422 do query. As tabelas dos `CLAUDE.md` cobrem método,
caminho, parâmetros, status e exceção de origem — não cobrem exemplos nem schema de resposta.

### D1b — A preservação cobre três fontes, não uma

Descoberto em 2026-08-10, depois de escrita a proposal original: o contrato **não** vive só nas
anotações. Os READMEs das duas apps carregam exemplos de request/response que nenhuma outra fonte
tem — e quem os apaga é a change `enxugar-documentacao-repo`, não esta.

```
                       docs/contrato-api-para-gateway.md
                                     ▲
              ┌──────────────────────┼──────────────────────┐
              │                      │                      │
   69 anotações springdoc   README command 268-380   README query 181-264
   (esta change apaga)      (change B apaga)         (change B apaga)
```

**Decisão: esta change preserva as três, e `enxugar-documentacao-repo` espera pela fase 1.**

**Alternativa descartada** — cada change preservar a sua fonte: manteria as duas independentes,
mas o contrato ficaria fragmentado em dois arquivos e dois momentos, e "juntar depois" é
exatamente o tipo de tarefa que não acontece. O ganho de independência não paga o risco de o
gateway ser montado a partir de metade do contrato.

**Consequência operacional:** as duas changes deixam de ser independentes. A dependência é
estreita — só a fase 1 desta change precede B, não a change inteira —, e está declarada nos dois
`tasks.md`.

### D5 — `/swagger-ui/**` não recebe tratamento próprio

**Decisão (2026-08-10, era Q2):** o 404 natural basta.

**Racional:** sem springdoc, `/swagger-ui/**` e `/v3/api-docs` simplesmente não são mapeados por
controller nenhum, e o handler de `NoResourceFoundException` que D2 preserva já os transforma em
404 no formato `LayoutErrosApiResponse`. Tratamento explícito seria código a mais fazendo o que o
default já faz — e um caminho a mais para manter em sincronia com a lista de rotas removidas.
A tarefa 3.7 apenas confirma o comportamento.

### D2 — O handler de `NoResourceFoundException` fica

`ApiExceptionHandler` das duas apps tem um `@ExceptionHandler(NoResourceFoundException.class)`
cujo javadoc diz existir por causa do `/v3/api-docs`:

> "Importante para o endpoint `/v3/api-docs` do springdoc em testes de slice sem
> auto-configuração completa."

**Decisão:** manter o handler, reescrever o javadoc.

**Racional:** sem ele, qualquer caminho desconhecido cai no catch-all `Exception` e vira **500**.
Um `GET /caminho-que-nao-existe` respondendo 500 é defeito de contrato independente do springdoc.
O springdoc foi só o gatilho que expôs o buraco; o comportamento correto não depende dele.

Isto é uma armadilha real: quem remover springdoc "seguindo os imports" vai achar que o handler
é órfão. Ele não é.

### D3 — Critério de marcação com `// TODO`

**Decisão:** `// TODO` apenas onde existe **custo concreto já identificado e documentado**.

**Racional:** a capacidade `higiene-comentarios-codigo` já veda comentário que não explique um
porquê não óbvio, e veda banner decorativo. Um `// TODO: método longo` não explica porquê algum —
é ruído que envelhece. Já `// TODO: 148 ms de planejamento por chamada` carrega uma medição.

Critério objetivo — o trecho SHALL ter pelo menos um destes:
1. Uma medição registrada (latência, contagem, consumo).
2. Um bloqueio externo nomeado (limitação de ferramenta, versão de biblioteca).
3. Uma change OpenSpec aberta que o endereça, citada pelo nome.

Fora disso, a oportunidade vira item de backlog no `design.md`, não comentário no código.

**Candidatos conhecidos** (a confirmar na tarefa 4.1):
- Custo de planejamento de 148 ms na listagem do query — medido, e já tem change aberta
  (`reduzir-custo-planejamento-consultas`). Critérios 1 e 3.
- `void main()` do Java 25 pendente de suporte do maven plugin — critério 2, e o
  `// TODO` já existe.

### D4 — Parâmetros sem uso: a premissa do `-Xlint:all` estava errada

**Premissa original (2026-08-10):** habilitar `-Xlint:all` temporariamente, coletar avisos de
parâmetro sem uso, remover os confirmados.

**Refutada na execução, mesmo dia.** O javac padrão **não tem categoria de lint para parâmetro de
método sem uso** — as categorias de `-Xlint:all` são `cast`, `deprecation`, `unchecked`,
`serial`, `overloads`, `rawtypes` etc.; nenhuma cobre parâmetro não referenciado no corpo. Testado
em `contratocommand`: `-Xlint:all` produziu só avisos de MapStruct (`Unmapped target
property`), processamento de anotação e `serialVersionUID` ausente — nada sobre parâmetros. O
flag foi revertido do `pom.xml` assim que confirmado, para não deixar ruído de build sem
propósito.

**Alternativa usada:** varredura heurística (script Python, `src/main` das cinco apps) que
extrai assinatura + corpo de cada método e verifica se cada nome de parâmetro reaparece no corpo.
Achados brutos: 22 ocorrências.

**Triagem — quase tudo era falso positivo do próprio heurístico, não código morto real:**

| Categoria | Qtd | Exemplo | Ação |
|---|---|---|---|
| Records (`@ConfigurationProperties`, eventos) | 12 | `AwsProperties(endpoint, region, ...)`, `AutorizacaoPersistidaEvent(autorizacao)` | Nenhuma — componente de record vira acessor gerado; o heurístico não entende records |
| Override de interface (`Rule.aceita(context)`) | 9 | `ProdutoSuportado.aceita(ContratacaoContext contexto)` retorna `true` sem usar `contexto` | Nenhuma — contrato de `ContratacaoRule`/`CancelamentoRule`/`DecisaoRule`; remover quebra a interface. Confirma a ressalva já prevista abaixo |
| `@ExceptionHandler` com colisão de assinatura | 2 | `ApiExceptionHandler.conflitoLockOtimista` — dois overloads, ambos perderiam o parâmetro de exceção e colidiriam em `(HttpServletRequest req)` | Nenhuma nesta change — remover exige renomear um dos dois métodos, refactoring maior que "parâmetro sem uso". Registrado como oportunidade identificada, não removida — ver seção de backlog |
| `@ExceptionHandler` sem colisão, genuinamente morto | 1 | `ApiExceptionHandler.conflitoEstadoObsoleto(StaleStateException exception, HttpServletRequest req)` — `exception` nunca lido; `@ExceptionHandler(StaleStateException.class)` já declara a classe, Spring não exige o parâmetro para rotear | **Removido.** Assinatura virou `conflitoEstadoObsoleto(HttpServletRequest req)`; teste ajustado |

**Ressalva original, confirmada na prática:** parâmetro exigido por assinatura de framework
(implementação de interface, callback de listener) não é parâmetro morto mesmo sem uso no corpo —
9 dos 22 achados eram exatamente esse caso. A diferença que a triagem revelou: para
`@ExceptionHandler`, a "exigência de framework" é mais fraca do que eu supunha — Spring não exige
o parâmetro de exceção quando a anotação já declara a classe —, então cada ocorrência precisa de
julgamento sobre colisão de assinatura, não uma regra única.

**Limite conhecido do heurístico:** o regex casa assinatura em uma linha só; método com retorno e
parâmetros quebrados em linhas diferentes pode escapar da varredura. Não foi feita verificação
exaustiva de cobertura — aceito como risco residual proporcional ao tamanho da tarefa (um item
entre vários numa change de limpeza, não uma auditoria dedicada).

**Decidido em 2026-08-10 (era Q1): não há mais flag de lint a remover** — nunca chegou a ficar no
`pom.xml` além do teste pontual que o refutou.

## Backlog identificado

Oportunidades encontradas durante a execução que não atendem o critério de D3 (medição, bloqueio
externo nomeado, ou change aberta) para virar `// TODO`, e que não foram corrigidas nesta change
por exigirem escopo maior que "remover código sem uso". Registradas aqui, não no código.

- **`ApiExceptionHandler.conflitoLockOtimista` (contratocommand) tem dois overloads com
  parâmetro de exceção sem uso, mas não podem perder o parâmetro sem colidir.** Um trata
  `OptimisticLockException`, o outro `ObjectOptimisticLockingFailureException`; nenhum dos dois
  lê o parâmetro `exception` no corpo (só constroem uma mensagem genérica "Tente novamente"). Sem
  o parâmetro, os dois viram `conflitoLockOtimista(HttpServletRequest req)` — mesma assinatura,
  erro de compilação. Resolver exige renomear um dos dois métodos (ex.:
  `conflitoLockOtimistaJpa`/`conflitoLockOtimistaSpring`), uma decisão de nomenclatura que não é
  "limpeza residual". Candidato a um refactoring pontual (`Rename Method` + `Remove Parameter`),
  não a esta change.

## Riscos

| Risco | Probabilidade | Mitigação |
|---|---|---|
| Contrato de API se perde (D1) | **Alta** — o gateway ainda não o tem | Tarefa 1.1 extrai o texto das anotações antes de qualquer remoção; é bloqueante |
| Handler de 404 removido por engano | Média | D2 explícito; tarefa 2.3 é o teste que trava |
| Remoção de parâmetro quebra binding de framework | Média | D4: triagem por aviso, com teste verde entre cada remoção |
| `TODO` vira ruído | Baixa | D3 dá critério objetivo de 3 gatilhos |
| Alguém depende de `/swagger-ui` local | Baixa | Declarado em Impact; sem substituto por decisão |

## Questões em aberto

- ~~**Q1:** o `-Xlint:all` fica permanente?~~ **Respondida em 2026-08-10: temporário** — ver D4.
- ~~**Q2:** rejeitar `/swagger-ui/**` explicitamente?~~ **Respondida: 404 natural basta** — ver D5.

Nenhuma questão em aberto.
