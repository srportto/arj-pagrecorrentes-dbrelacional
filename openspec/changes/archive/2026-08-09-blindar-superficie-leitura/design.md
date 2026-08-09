## Context

O `arj-contratoquery` expõe dois endpoints de leitura sobre a tabela `autorizacoes`, particionada
por `id_particao_conta`. A listagem é o endpoint mais exercitado, e o desalinhamento entre como a
tabela é particionada e como ela é consultada é a raiz do problema de desempenho:

```
  TABELA                          CONSULTA
  particionada por                filtra por
  id_particao_conta        ✗      id_unico_conta_contratante
                                  + status
                                  ordena por data_hora_inclusao / valor / ...

  → o particionamento não ajuda a consulta
  → nenhuma das colunas de filtro ou ordenação tem índice
  → varredura sequencial multi-partição a cada requisição
```

Sobre isso, quatro ausências de validação de borda:

| Parâmetro | Hoje | Efeito |
|---|---|---|
| `tamanho` | sem teto | `?tamanho=999999` executa |
| `pagina` | sem validação | `?pagina=-1` → `IllegalArgumentException` → 500 do container |
| `ordenarPor` | `default` repassa string crua | campo inexistente → `PropertyReferenceException` → 500 |
| exceção não prevista | sem catch-all | escapa do `LayoutErrosApiResponse` |

O `arj-contratocommand` **tem** o catch-all. O query não. Mesma equipe, mesmo padrão, um lado só.

Há também um bug de contrato de origem oposta: a spec `listar-autorizacoes` especifica 422 para
`idUnicoContaContratante` ausente, o service implementa essa validação, e ela nunca roda — porque
o `@RequestParam` é obrigatório e o Spring rejeita antes. Código escrito para cumprir a spec,
tornado inalcançável por uma anotação.

## Goals / Non-Goals

**Goals:**

- Que nenhuma requisição de leitura consiga disparar trabalho ilimitado no banco.
- Que toda resposta de erro do query siga o contrato, incluindo o inesperado.
- Que a listagem use índice em vez de varredura.
- Que o 422 especificado para `idUnicoContaContratante` ausente de fato aconteça.

**Non-Goals:**

- Autenticação/autorização (decisão de arquitetura adiada por escolha explícita).
- Paginação por cursor.
- Teste de integração do repositório com Postgres real.
- Alterar a convenção 422 vs 400 do serviço — ver D2.

## Decisions

### D1 — `required = false` em vez de reescrever a spec

Duas saídas para o drift do `idUnicoContaContratante`:

- **(a)** Tornar o parâmetro opcional no binding, deixando a validação do service rodar → 422 com
  corpo estruturado, como a spec determina.
- **(b)** Atualizar a spec para documentar o 400 nativo do Spring que acontece hoje.

Escolhemos (a). A spec descreve o comportamento desejável — erro de contrato com corpo consistente
—, e (b) legitimaria uma resposta de framework que não segue o `LayoutErrosApiResponse` dos demais
erros. Como bônus, (a) elimina o código morto no service em vez de deixá-lo lá para confundir a
próxima pessoa.

Ressalva: com `required = false`, um cliente que omita o parâmetro passa a receber 422 em vez de
400. É mudança de contrato, mas na direção do que já estava especificado.

### D2 — Seguir a convenção vigente de status, não corrigi-la aqui

O serviço usa 422 para violação de `@Valid` e para `BusinessException`, enquanto o `README.md`
promete 400 — divergência confirmada por três agentes da auditoria e endereçada na proposta
`reconciliar-contrato-spec-doc`.

Esta mudança adota **a convenção que o código já pratica**, para não criar um terceiro padrão no
meio do caminho:

| Situação | Status |
|---|---|
| `idUnicoContaContratante` ausente | 422 (conforme spec) |
| `tamanho` acima do teto | 422 |
| `pagina` negativa / `tamanho` não positivo | 422 |
| `ordenarPor` de campo desconhecido | 422 |
| exceção não prevista | 500 estruturado |

Se a reconciliação mudar a convenção global, estes acompanham. O importante aqui é que **nada mais
retorne 500 do container com detalhe interno**.

### D3 — Teto de tamanho validado na borda, não só configurado

`spring.data.web.pageable.max-page-size` faz o Spring truncar silenciosamente o valor. Truncar é
melhor que executar, mas o cliente pede 5000, recebe 100 e não sabe por quê — paginação
aparentemente quebrada.

Preferimos validação explícita na borda (`@Max` no parâmetro + verificação no service),
devolvendo erro que diz qual é o teto. A configuração do Spring pode ficar como segunda camada,
mas não é o mecanismo principal.

### D4 — Whitelist por rejeição, invertendo o default

Hoje `mapearCampoDTO` tem `default -> campoDtoOuEntidade`: desconhecido passa adiante. Invertemos
para `default -> lança erro de negócio`. A lista de campos ordenáveis passa a ser fechada e
explícita — que é o que a whitelist significa.

Efeito colateral desejável: a mensagem de erro pode listar os campos aceitos, como já é feito para
`status` inválido.

### D5 — Índice composto, com forma a definir pelo particionamento

A ordem `(id_unico_conta_contratante, status, data_hora_inclusao DESC)` segue o uso: igualdade
primeiro, depois o filtro opcional, depois a ordenação padrão.

Duas questões que a implementação precisa resolver contra o banco real, não no papel:

- Em tabela particionada no Postgres, o índice é criado na tabela-mãe e propagado, ou por partição
  via template? Depende de como o particionamento foi declarado.
- `CREATE INDEX CONCURRENTLY` não roda dentro de transação, o que afeta como a migration é escrita
  conforme a ferramenta usada no projeto.

E, principalmente: **medir**. Não há baseline de `EXPLAIN ANALYZE` registrada em lugar nenhum do
repositório. Sem plano antes e depois, a criação do índice é ato de fé — e índice errado custa
escrita sem devolver leitura.

### D6 — `readOnly = true` é correção de intenção, não de desempenho

O ganho de desligar dirty-checking em consulta é real mas modesto. O valor maior é declarativo: o
`arj-contratoquery` é contratualmente somente-leitura, e hoje essa intenção vive apenas no
`read-only=true` do HikariCP, uma camada abaixo e sobrescrevível por variável de ambiente.

Não resolve a ausência de barreira estrutural — o repositório continua estendendo `JpaRepository`
com `save`/`delete` expostos, apontado pela auditoria. Restringir a interface fica fora deste
escopo por ser refactor de tipo com raio próprio.

## Risks / Trade-offs

- **Índice pode não ajudar como esperado** → Medir com `EXPLAIN ANALYZE` antes e depois em volume
  representativo. Se o plano não mudar, a composição de colunas precisa ser revista antes de
  mesclar. Índice inútil é custo de escrita puro.

- **`CREATE INDEX CONCURRENTLY` em tabela particionada tem restrições** → Verificar a forma viável
  contra o banco real antes de escrever a migration; possivelmente aplicação por partição.

- **Clientes podem depender de `tamanho` grande hoje aceito** → A rejeição é 4xx explícito com o
  teto na mensagem. Vale verificar se algum consumidor conhecido usa página grande antes de fixar
  o valor do teto.

- **Mudança de 400 para 422 no parâmetro ausente** → Alinha com a spec, mas altera o que o cliente
  recebe. Documentar no `README.md` e comunicar.

- **Catch-all pode mascarar erro que hoje é visível no console** → O handler deve logar a exceção
  completa com stack trace no servidor e devolver só a mensagem genérica ao cliente. Perder o
  diagnóstico seria trocar um problema por outro.

## Migration Plan

1. Validações de borda e catch-all (sem dependência de banco), com testes.
2. `readOnly = true` e `ObjectMapper` estático.
3. Baseline de `EXPLAIN ANALYZE` da listagem antes do índice.
4. Migration do índice com `CONCURRENTLY`.
5. `EXPLAIN ANALYZE` depois, comparando com o baseline de 3.

Rollback: o índice pode ser removido sem perda; as validações são reversíveis por commit.

## Open Questions

- Qual o teto adequado para `tamanho`? 100 é o default comum, mas depende de haver consumidor
  legítimo que pagine em blocos maiores.
- Em Postgres, este particionamento aceita índice propagado da tabela-mãe ou exige criação por
  partição?
- Existe consumidor conhecido usando `tamanho` grande hoje? Define se o teto entra direto ou com
  período de aviso.
