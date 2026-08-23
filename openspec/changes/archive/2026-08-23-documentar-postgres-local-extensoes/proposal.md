## Why

O PostgreSQL local deste repositório não é a imagem oficial pronta: é uma imagem **construída**
(`infra/local/postgres/dockerfile`) justamente para carregar extensões auxiliares — `pg_partman` e
`pg_cron` via pacote PGDG, `pgvector` compilado da fonte. Saber montar um PostgreSQL com as
extensões que se queira é um **objetivo declarado do projeto**, não um efeito colateral.

Só que essa capacidade hoje existe apenas de forma implícita, espalhada por três arquivos que
precisam ser lidos juntos e na ordem certa para a receita fazer sentido: o `dockerfile` (instalação),
o `postgres-db-v18.yml` (preload) e a migration `v1.0.0` (`CREATE EXTENSION`). O
`README.md` de `infra/local/postgres/` documenta subir, validar e parar — mas não documenta **como
somar uma extensão nova**, nem qual das três etapas cada extensão exige.

A consequência já é observável: `pg_partman` está carregado, com background worker rodando, e nunca
foi usado por nada (o ring buffer é gerido por fórmula na aplicação, não por partman); `pgvector` é
compilado da fonte e não tem consumidor algum. Lido sem contexto, isso parece dívida técnica
esperando limpeza — e uma futura change de higiene removeria exatamente a demonstração que o
ambiente existe para fazer. A intenção precisa estar escrita, não presumida.

Três das quatro specs de ambiente local existem (`local-aws-environment`, `local-kafka-environment`,
`local-valkey-environment`). A do PostgreSQL — o mais elaborado dos quatro — é a que falta.

## What Changes

- **Nova capability `local-postgres-environment`**, cobrindo o ambiente PostgreSQL local como
  **receita reaproveitável** de construção de banco com extensões auxiliares.
- **Receita de adição de extensão documentada de ponta a ponta** no `README.md` de
  `infra/local/postgres/`: as três etapas (instalar na imagem → declarar em
  `shared_preload_libraries` **quando a extensão exigir** → `CREATE EXTENSION` na migration), com os
  dois caminhos de instalação já exercitados no repositório — pacote PGDG via `apt`
  (`postgresql-18-partman`, `postgresql-18-cron`) e compilação da fonte (`pgvector`).
- **Critério explícito de quando o preload é necessário**, para que somar uma extensão nova não vire
  tentativa e erro: extensão com background worker ou hook de planner exige preload e reinício;
  extensão puramente de tipos/funções basta `CREATE EXTENSION`.
- **Registro de que extensão sem consumidor é deliberada**, não dívida — nenhuma change de higiene
  deve "limpar" o preload ou o `dockerfile` por ausência de uso.
- **Comandos de verificação** para cada etapa: confirmar que a biblioteca foi carregada no processo e
  que a extensão foi efetivamente criada no banco, distinguindo os dois estados (uma extensão pode
  estar no preload e não criada, ou criada e não pré-carregada).

Fora de escopo: adicionar ou remover qualquer extensão; alterar `shared_preload_libraries`; alterar
o `dockerfile`; migração para outra versão do PostgreSQL.

## Capabilities

### New Capabilities

- `local-postgres-environment`: descreve o ambiente PostgreSQL local como receita de construção de
  banco com extensões auxiliares — imagem própria em vez de imagem pronta, os dois caminhos de
  instalação, o critério de quando o preload é exigido, a verificação de cada etapa, e a natureza
  deliberada de extensões carregadas sem consumidor.

### Modified Capabilities

(nenhuma — `orquestracao-local-unificada` já especifica que o serviço PostgreSQL tem definição
única, que as migrations rodam em qualquer caminho de subida, e que `shared_preload_libraries` é
declarada numa **única** diretiva `-c` com lista; esta change **não redeclara** nada disso, e a
fronteira entre as duas capabilities está registrada no `Purpose` da nova spec)

## Impact

- **Documentação**: `infra/local/postgres/README.md` (seção nova de extensões; as seções de subir,
  validar e parar permanecem como estão).
- **Nenhum arquivo executável é alterado.** O `dockerfile`, o `postgres-db-v18.yml` e as sete
  migrations ficam byte a byte idênticos. Esta change descreve o que já existe e registra a intenção
  por trás — não muda comportamento algum.
- **Protege o preload de remoção futura.** Com `pg_partman` e `pgvector` sem consumidor, qualquer
  auditoria de código morto tenderia a propô-los para remoção. O requisito de intencionalidade dá a
  essa auditoria a resposta antecipada.
- **Fecha a lacuna do conjunto `local-*-environment`**, que hoje cobre Floci, Kafka e Valkey mas não
  o PostgreSQL.
