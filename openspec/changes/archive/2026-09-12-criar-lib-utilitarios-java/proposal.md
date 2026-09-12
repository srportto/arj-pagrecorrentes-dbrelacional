## Why

Três classes utilitárias — `ReversibleUUIDv7`, `BusinessException` e `ApplicationException` — existem
hoje como cópias byte-a-byte (lógica idêntica, só o pacote muda) em `contratocommand` e
`contratoquery`. Nenhuma das três carrega semântica de negócio nem trafega em contrato de rede
(evento SNS/SQS/Kafka): são utilitário técnico puro. Corrigir um bug em qualquer uma delas hoje
depende de lembrar de replicar manualmente no outro serviço — exatamente o risco que o espelhamento
deliberado de contratos (`AutorizacaoEventoPayload`, `.avsc`, `StatusAutorizacao`) aceita
conscientemente, mas que aqui não tem a mesma justificativa: essas três classes não precisam evoluir
em ritmos diferentes entre serviços.

## What Changes

- Criação de um novo módulo Maven **Java puro** (sem Spring, sem Jakarta/Servlet, sem qualquer
  dependência de framework) para hospedar código utilitário verdadeiramente idêntico entre apps.
- Migração de exatamente 3 classes para o novo módulo: `ReversibleUUIDv7`, `BusinessException`,
  `ApplicationException` — hoje duplicadas entre `contratocommand` e `contratoquery`.
- `contratocommand` e `contratoquery` passam a depender do novo módulo em vez de manter cópia
  própria dessas 3 classes.
- **Fora de escopo, deliberadamente**: `TraceIdFilter` (idêntica hoje, mas depende de
  `jakarta.servlet` — contradiz o requisito "Java puro" desta lib), `LayoutErrosApiResponse` e
  `ApiExceptionHandler` (já divergem por design entre command/query — mutável vs. imutável,
  handlers específicos de escrita vs. leitura), `ResourceNotFoundException` (existe só numa app,
  não é duplicação), qualquer contrato de evento (`AutorizacaoEventoPayload`, `.avsc`) e qualquer
  enum de máquina de estados (`StatusAutorizacao`, `TipoEventoAutorizacao`) — esses continuam
  espelhados à mão, por decisão já registrada em `docs/arquitetura/stack-e-padroes.md`.
- **Pergunta em aberto que trava o design (D-CI)**: como o build de CI de cada app consumirá o
  novo módulo, dado que hoje não existe reactor Maven nem registro de pacotes no monorepo. Não
  decidido nesta proposta — ver `design.md`, seção Open Questions.

## Capabilities

### New Capabilities

- `biblioteca-utilitarios-compartilhada`: descreve o módulo Java puro compartilhado, o que ele
  pode e não pode conter (critério de elegibilidade de classe), e como `contratocommand` e
  `contratoquery` consomem essas 3 classes a partir dele em vez de manter cópia local.

### Modified Capabilities

- `layout-hexagonal-classico`: os cenários que listam a árvore de pacotes de `contratocommand` e
  `contratoquery` referenciavam `ReversibleUUIDv7` como pertencente a `infrastructure/persistence/`
  e `BusinessException`/`ApplicationException` como pertencentes a `domain/exception/` de cada app.
  Após a migração, as 3 classes deixam de residir localmente e passam a vir de
  `libs/srportto-commons-java` como dependência Maven — os cenários são atualizados para refletir
  essa origem, sem mudar nenhum comportamento observável (mesma mensagem de exceção, mesmo
  mapeamento HTTP, mesmo algoritmo de codificação de partição).

## Impact

**Código de produção:**

| Local | Mudança |
|---|---|
| Novo módulo (nome e localização a definir em `design.md`) | `ReversibleUUIDv7.java`, `BusinessException.java`, `ApplicationException.java` — Java puro |
| `apps/contratocommand` | remove as 3 classes locais; adiciona dependência ao novo módulo |
| `apps/contratoquery` | remove as 3 classes locais; adiciona dependência ao novo módulo |

**Build/CI**: impacto real, mecanismo ainda não decidido — ver `design.md` (D-CI). Afeta
`ci-testesunitarios-contratocommand.yml` e `ci-testesunitarios-contratoquery.yml`, e potencialmente
a capacidade `ci-testes-unitarios` (cache por app hoje chaveado só pelo `pom.xml` da própria app).

**Documentação**: `CLAUDE.md`/`AGENTS.md` raiz ("Regras que atravessam os serviços") e os pares
`CLAUDE.md`/`AGENTS.md` de `contratocommand` e `contratoquery` precisam registrar a existência do
módulo compartilhado e o critério de elegibilidade (para não virar precedente para migrar
contratos/enums que devem continuar espelhados).

**Sem impacto**: `autorizacaostatus-producer`, `eventos-consumer`, `temporiza-autorizacao`,
`expurgo-particao` — nenhuma das 3 classes migradas existe nessas apps hoje.
