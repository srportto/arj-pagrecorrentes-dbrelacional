## Why

Quarta das seis mudanças que migram as aplicações de `apps/` para a arquitetura hexagonal clássica —
e a **primeira que não é remanejamento**. As três anteriores (`eventos-consumer`,
`temporiza-autorizacao`, `autorizacaostatus-producer`) tinham as setas de dependência corretas e só
precisavam de endereço novo. O `contratoquery` tem as setas erradas.

Três violações estruturais, todas verificáveis por `import`:

```
1.  application/autorizacao/ConsultarAutorizacaoService
      import ...entrypoint.contratosrest.AutorizacaoDetalheResponseDto
      └─ a camada de dentro IMPORTA a de fora. É a seta invertida.

2.  application/autorizacao/AutorizacaoRepository
      extends JpaRepository<Autorizacao, IdAutorizacao>
      └─ anti-padrão #1 da skill: o use case fala Spring Data direto,
         o domínio não dita contrato nenhum.

3.  domain/entities/Autorizacao          domain/converters/TipoProdutoConverter
      @Entity @Table @Version              implements jakarta.persistence.AttributeConverter
      └─ anti-padrão #2 e #6: o domínio É o schema do banco.
```

O `contratoquery` vem **antes** do `contratocommand` de propósito. Os dois compartilham a mesma
tabela e entidades quase idênticas, mas o query é **somente leitura**: não tem `@Version` em uso,
não tem dirty checking, não tem transação de escrita, não publica evento. Separar modelo de domínio
de entidade JPA aqui exercita exatamente o mesmo mapper que o command vai precisar, sem nenhum dos
riscos de concorrência que fazem do command a app mais perigosa da frota.

Se o padrão de mapper não funcionar, é aqui que descobrimos — a 972 linhas em vez de 1942, e sem
poder corromper dado.

## What Changes

- Reorganizar as 25 classes de `main` para `domain` / `application` / `infrastructure`.

- **Separar modelo de domínio de entidade JPA** (nível N3, decidido na exploração de 2026-08-15):

  | Hoje | Depois |
  |---|---|
  | `domain/entities/Autorizacao` (`@Entity`, Lombok) | `domain/model/Autorizacao` (Java puro) **+** `infrastructure/persistence/AutorizacaoJpaEntity` **+** `AutorizacaoPersistenceMapper` |
  | `domain/entities/IdAutorizacao`, `Cancelamento` | idem — modelo puro + embeddable JPA no adapter |
  | `domain/converters/*Converter` (`jakarta.persistence`) | `infrastructure/persistence/` |

- **Introduzir portas:**
  - `domain/port/in/ConsultarAutorizacaoUseCase`, `ListarAutorizacoesUseCase`;
  - `domain/port/out/AutorizacaoRepository` — interface própria do domínio, falando em
    `domain/model`. O `JpaRepository` vira `SpringDataAutorizacaoRepository`, **package-private**
    dentro de `infrastructure/persistence/`.

- **Mover a cascata de partições para o adaptador de persistência.** Hoje `ConsultarAutorizacaoService`
  implementa a busca em até três níveis (partição derivada do id → faixa 0–889 → faixa 900–999) e
  conhece as constantes `PARTICAO_MIN`, `PARTICAO_MAX`, `PRIMEIRA_PARTICAO_EXPURGO`. Isso existe
  porque o expurgo move linhas entre partições — é estratégia de armazenamento, não regra de negócio.
  A porta passa a expor `buscarPorId(UUID)`; a cascata inteira, incluindo a flag
  `contratoquery.consulta.busca-em-particoes-inesperadas`, vira responsabilidade do adaptador.

- **Isolar o particionamento atrás de porta** (decisão de 2026-08-15): `ReversibleUUIDv7` sai do
  domínio. Como o query só **extrai** partição de um id existente, sua necessidade é atendida
  inteiramente dentro do adaptador — o domínio deixa de mencionar partição. A porta de geração de
  identidade, que só o command precisa, é introduzida na mudança
  `hexagonal-classico-contratocommand-dominio-puro`.

- **Corrigir a seta invertida:** os casos de uso passam a retornar `domain/model/Autorizacao`. Quem
  monta `AutorizacaoDetalheResponseDto` / `AutorizacaoResumidaResponseDto` / `PaginacaoResponseDto`
  é o controller, em `infrastructure/web/`.

- Mover `shared/exceptions/*` para `domain/exception/` e `shared/interceptors/api/*` para
  `infrastructure/web/`.
- Mover os 16 arquivos de teste para a árvore espelhada.
- Acrescentar à capacidade `layout-hexagonal-classico` os requisitos sobre separação modelo/entidade
  e sobre estratégia de armazenamento no adaptador, mais o requisito específico desta app.
- Atualizar `apps/contratoquery/CLAUDE.md` e `AGENTS.md` (espelhos idênticos).

- **Nenhuma mudança de contrato REST.** `GET /api/autorizacoes` e `GET /api/autorizacoes/{id}`
  mantêm parâmetros, formato de resposta, códigos de erro e — explicitamente — a representação
  divergente do `contratocommand` (`status` como `String`, `valor`/`dataCriacao`/`dataAtualizacao`).
  A dívida command × query registrada no `CLAUDE.md` da raiz **não** é resolvida aqui.

- **Fora de escopo:** o `contratocommand`, que tem duas mudanças próprias.
- **Fora de escopo:** unificar a representação de `Autorizacao` entre as duas apps. Continuam sendo
  cópias independentes, agora ambas em `domain/model/`.
- **Fora de escopo:** mudar o comportamento da cascata. Ela muda de lugar, não de algoritmo — os três
  níveis, a ordem e a flag de habilitação do nível 3 permanecem.

## Capabilities

### Modified Capabilities

- `layout-hexagonal-classico`: acrescenta (a) o requisito de que o modelo de domínio seja livre de
  mapeamento objeto-relacional, com entidade JPA e mapper confinados ao adaptador; (b) o requisito de
  que estratégia de armazenamento — particionamento, cascata de busca, índice — viva no adaptador e
  não vaze para `application`; e (c) o requisito específico do `contratoquery`.

## Impact

- **Código afetado (25 arquivos em `main`):** todos mudam de pacote. Classes novas:
  `AutorizacaoJpaEntity`, `AutorizacaoPersistenceMapper`, 2 interfaces de porta de entrada,
  1 interface de porta de saída. `AutorizacaoRepository` deixa de estender `JpaRepository`.
- **Testes (16 arquivos):** movidos. Três exigem trabalho real:
  `ConsultarAutorizacaoServiceTest` (a cascata que ele exercita mudou de camada),
  `ConsultaCascataIntegrationTest` (idem, agora contra o adaptador) e
  `AutorizacaoControllerTest` (o controller passa a montar o DTO).
- **Banco:** nenhuma migration. O mapeamento coluna a coluna é preservado integralmente — qualquer
  divergência entre `AutorizacaoJpaEntity` e o schema atual é defeito, não escopo.
- **Desempenho:** a cascata muda de camada mas não de algoritmo. O número e a forma das queries por
  requisição SHALL ser idênticos — a spec `desempenho-consulta-autorizacoes` e a configuração
  `plan_cache_mode = force_generic_plan` do Hikari continuam valendo sem ajuste.
- **`contratocommand`:** nenhuma alteração. As duas apps compartilham a tabela, não código.
- **Precedente para as duas mudanças seguintes:** o formato do mapper, o nome
  `SpringDataAutorizacaoRepository` e a decisão de confinar a cascata no adaptador são herdados pelo
  `contratocommand`.
