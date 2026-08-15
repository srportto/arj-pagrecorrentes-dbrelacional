## Why

O custo dominante das consultas ao `autorizacoes` é **planejamento**, não I/O — e ninguém está
olhando para ele.

A tabela tem 989 partições (889 quentes + 100 de expurgo). Toda consulta cujo filtro não seja a
chave de particionamento faz o PostgreSQL montar centenas de subplanos. Medições no ambiente
local, com a tabela contendo **24 linhas**:

| Consulta | Subplanos | Planning | Execution |
|---|---|---|---|
| `GET /api/autorizacoes` (listagem, endpoint principal) | 989 | **147,6 ms** | 17,8 ms |
| Nível 3 da cascata de `GET /{id}` | 888 | 125,6 ms | 11,2 ms |
| Nível 2 da cascata de `GET /{id}` | 100 | 15,2 ms | 1,0 ms |

Esse custo **não diminui com menos dados e não melhora com índice** — é linear no número de
partições consideradas, pago em CPU a cada chamada. A listagem, que é o endpoint principal de
leitura do sistema, gasta ~148 ms de planejamento por requisição para devolver no máximo 100
linhas.

Um spike executado em 2026-08-10 (registrado no `design.md` da change
`fallback-consulta-autorizacao-expurgada`) mostrou que o planejador **nunca** migra para plano
genérico por conta própria neste schema — com poda por partição, o plano custom sempre parece
mais barato, então o replanejamento se repete em toda chamada:

| Modo | 1ª execução | Execuções seguintes |
|---|---|---|
| Custom (padrão), após 6 execuções do mesmo `PREPARE` | 35,2 ms | **35,2 ms** — não cacheia |
| `force_generic_plan` | 39,4 ms | **0,17 ms** |
| `force_generic_plan`, partição parametrizada | 46,1 ms | **0,15 ms**, `Subplans Removed: 988` |

Uma configuração, sem mudança de código, com potencial de reduzir o custo por chamada em duas
ordens de grandeza — beneficiando a listagem, a consulta por id e tudo o mais.

## What Changes

- Avaliar e, se confirmado, adotar `plan_cache_mode = force_generic_plan` nas aplicações que
  leem a tabela particionada, verificando o efeito **consulta a consulta** — plano genérico
  troca poda em tempo de planejamento por poda em tempo de execução, e nem toda consulta ganha
  com isso.
- Medir o efeito sobre a listagem paginada com ordenação, que é o caso mais complexo e o de
  maior tráfego.
- Avaliar se **889 partições quentes** são necessárias. O custo de planejamento é linear nesse
  número: reduzi-lo barateia toda consulta do sistema, permanentemente. É a alavanca mais
  simples e a que ninguém questionou desde o desenho original.
- Estabelecer uma medida de referência de latência das consultas, para que regressão de
  desempenho deixe de ser invisível.

## Capabilities

### New Capabilities

Nenhuma prevista antes da investigação. Se a adoção for confirmada, o comportamento é de
configuração e desempenho, não de contrato de API.

### Modified Capabilities

- `desempenho-consulta-autorizacoes`: hoje a capacidade trata apenas de **cobertura de índice**
  e exige que o plano use índice em vez de varredura sequencial. Isso endereça a *execução*, que
  as medições mostram ser a fatia pequena. A capacidade precisa passar a tratar também o custo
  de **planejamento**, que é o dominante.

## Impact

**Aplicações**
- `contratoquery` — maior beneficiado; a listagem é o endpoint mais afetado
- `contratocommand` — as consultas por chave composta já são baratas (1 partição), mas o
  `existsBy...` da idempotência e a cascata de expurgo também pagam planejamento

**Configuração**
- `spring.jpa.properties.hibernate.jdbc...` / parâmetro de conexão ou `SET` de sessão para
  `plan_cache_mode`. A forma exata depende de como o HikariCP mantém as conexões — precisa
  valer para toda conexão do pool, não só para a primeira.

**Banco**
- Uma eventual redução do número de partições quentes é mudança estrutural com migração de
  dados. Investigação primeiro, decisão depois.

**Precedente relevante**
- A change `fallback-consulta-autorizacao-expurgada` foi desenhada com os números do plano
  custom. Se o plano genérico for adotado, o pior caso da cascata cai de ~75 ms para a casa dos
  poucos milissegundos, e a flag `contratoquery.consulta.busca-em-particoes-inesperadas` deixa
  de ter justificativa de custo (mantém a de diagnóstico).
