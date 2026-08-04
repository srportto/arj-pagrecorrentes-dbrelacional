## Context

Dois canais de saída, dois erros simétricos e opostos:

```
                    ┌──────────────────────────────┐
   CLIENTE   ◄──────┤  resposta 500                │  conta DEMAIS
                    │  exception.getMessage()      │  nome de tabela, coluna,
                    │                              │  constraint, infra
                    └──────────────────────────────┘

                    ┌──────────────────────────────┐
   LOG       ◄──────┤  log.info("...", evento)     │  conta DEMAIS
   (agregador)      │  record Avro inteiro         │  PII + valor financeiro
                    └──────────────────────────────┘

                    ┌──────────────────────────────┐
   LOG       ◄──────┤  ApplicationException(       │  conta DE MENOS
   (servidor)       │    e.getMessage() )          │  causa original descartada
                    │  sem Throwable cause         │  stack trace inútil
                    └──────────────────────────────┘
```

O sistema hoje entrega o detalhe técnico a quem não deveria vê-lo (o cliente da API), o dado
pessoal a onde ele não deveria ficar (o agregador de logs), e nega ao operador exatamente o que
ele precisa para diagnosticar (a exceção original).

O padrão correto já existe e está documentado no `CLAUDE.md` do `autorizacaostatus-producer`
("nenhum log nem mensagem de exceção carrega o body da mensagem"), e é praticado pelo
`AutorizacaoEventoPublisher` do `arj-contratocommand`. O `eventos-consumer` diverge. Como a regra
vivia apenas em documentação de um app, nada acusou a divergência — nem revisão, nem teste, nem
spec.

Campos sensíveis carregados pelo `EventoAutorizacao`: `id_pessoa_pagadora`, `id_pessoa_devedora`,
`id_pessoa_recebedora`, `valor`, `descricao`, `metadados`.

## Goals / Non-Goals

**Goals:**

- Parar os dois vazamentos conhecidos.
- Restaurar o diagnóstico do lado do servidor, preservando a causa original.
- Elevar a regra de `CLAUDE.md` de um app para spec normativa dos quatro.
- Descobrir se há outras ocorrências do mesmo padrão que a auditoria não alcançou.

**Non-Goals:**

- Mascaramento automático por appender/filtro de log.
- Catch-all de exceção não mapeada no query (fica em `blindar-superficie-leitura`).
- Correlação por MDC/`traceId`.
- Expurgo dos logs históricos já gravados — decisão operacional, não de código.

## Decisions

### D1 — Identificadores de negócio permanecem logáveis; dado de pessoa e valor, não

A linha divisória adotada:

| Logável | Não logável |
|---|---|
| `idAutorizacao`, `idAutorizacaoEmpresa` | `id_pessoa_pagadora/devedora/recebedora` |
| `tipoEvento`, `status`, `tipoProduto` | `valor`, `valor_limite` |
| `idParticaoConta` | `descricao`, `metadados` |
| contagens, durações | o objeto/record/payload inteiro |

O critério não é o nome do campo, e sim **a categoria**: identificador técnico de agregado é
rastreamento; identificador de pessoa e valor monetário são dado do titular. É a mesma linha que o
`arj-contratocommand` já pratica.

Regra operacional derivada, mais fácil de revisar do que a lista: **nunca interpolar um objeto de
domínio, record ou payload em log** — sempre citar campos nominalmente. Um objeto interpolado hoje
é seguro e amanhã ganha um campo novo que ninguém reavaliou.

### D2 — Spec normativa em vez de nota em `CLAUDE.md`

A regra já existia. Não impediu nada, porque documentação descritiva num app não governa outro.
Elevá-la a spec dá a ela um cenário verificável e a torna objeto de auditoria futura — que é a
diferença entre "está escrito" e "é exigido".

### D3 — Mensagem genérica ao cliente **e** causa preservada no servidor

As duas metades são inseparáveis. Sanitizar a resposta sem preservar a causa trocaria um problema
por outro: o cliente para de ver o detalhe e o operador continua sem ele — pior que hoje.

Concretamente:

- `ApplicationException` ganha `(String message, Throwable cause)`.
- `catch (Exception e) { throw new ApplicationException(e.getMessage()); }` no
  `CancelarAutorizacaoUseCase` passa a propagar `e` como causa.
- O handler loga a exceção completa (com a cadeia de causas) e devolve mensagem fixa ao cliente.

Nota deliberada: `BusinessException` continua devolvendo sua mensagem ao cliente. Ela é escrita
para o cliente — "frequência fora do intervalo aceito" é informação de negócio, não vazamento. A
sanitização vale para as exceções que carregam detalhe técnico não intencional.

### D4 — Varredura, não só correção pontual

A auditoria encontrou uma ocorrência clara no consumer. Duas razões para varrer os quatro apps
antes de fechar:

- O mesmo padrão pode existir em pontos que nenhum agente examinou a fundo.
- Sem inventário, não há como afirmar que o vazamento parou — só que aquele parou.

A varredura procura interpolação de objeto de domínio, record Avro, payload ou DTO em qualquer
chamada de log, nos quatro apps.

### D5 — Logs históricos ficam fora do código

Os dados já gravados continuam no agregador, sujeitos à política de retenção vigente. Expurgo ou
antecipação de retenção é decisão operacional, possivelmente com implicação regulatória, e não se
resolve por commit. Fica sinalizado nas tasks como item de escalada, não como correção.

## Risks / Trade-offs

- **A varredura pode não encontrar tudo** → Busca por padrão textual não pega todos os casos (ex.:
  `toString()` implícito via `@Data`, objeto passado a `MDC.put`). Mitigação: além da busca,
  inspecionar manualmente os pontos de log das camadas `application` e `entrypoint` dos quatro
  apps, que é onde objeto de domínio circula.

- **Log mais enxuto pode dificultar depuração que hoje se apoia no dump completo** → Se algum
  fluxo de investigação depende disso hoje, o substituto é logar identificadores e correlacionar
  pela base — não reintroduzir o payload. Vale confirmar com quem opera antes de mesclar.

- **Mensagem genérica no 500 pode dificultar suporte de primeiro nível** → Mitigado por logar a
  exceção completa no servidor. Se for necessário correlacionar resposta e log, um identificador de
  erro na resposta resolveria — mas isso é contrato novo, fica registrado como possibilidade, não
  entra neste escopo.

- **Dados já expostos permanecem no histórico do agregador** → Fora do alcance do código (D5);
  precisa de escalada operacional.

## Migration Plan

1. Varredura e inventário das ocorrências nos quatro apps.
2. Correção do log do `eventos-consumer` e das demais ocorrências encontradas.
3. Construtores com causa nas exceções dos dois serviços REST.
4. Preservação da causa nos pontos onde ela é descartada.
5. Sanitização da resposta de `ApplicationException` nos dois handlers.
6. Spec e documentação.
7. Escalada da decisão sobre logs históricos.

Rollback: todos os itens são reversíveis por commit e independentes entre si.

## Open Questions

- Algum fluxo de operação depende hoje do dump completo do evento em log? Se sim, qual o
  substituto aceitável?
- Qual a política de retenção do agregador de logs, e ela exige ação sobre os dados já gravados?
- Vale incluir um identificador de erro correlacionável na resposta 500 para apoiar o suporte?
  Contrato novo — decisão separada.
