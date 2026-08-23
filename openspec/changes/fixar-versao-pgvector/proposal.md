## Why

O `pgvector` é compilado a partir do código-fonte em `infra/local/postgres/dockerfile`:

```
git clone --depth 1 https://github.com/pgvector/pgvector.git /tmp/pgvector
```

Sem `--branch` nem commit, `--depth 1` traz o **último commit do branch padrão no instante do
build**. Dois builds em datas diferentes produzem versões diferentes da extensão, e nada no
repositório registra qual foi usada — nem o `LABEL` da imagem, que só diz "PostgreSQL 18 com pg_cron,
pg_partman e pgvector".

Numa imagem qualquer isso seria descuido comum. Aqui é mais grave por uma razão específica: a
capability `local-postgres-environment` estabelece que este ambiente existe **também para demonstrar
como se monta um PostgreSQL com extensões auxiliares**. Uma receita de construção que produz
resultado diferente a cada execução não demonstra construção — demonstra sorte. Reprodutibilidade é
parte do que está sendo ensinado.

O problema é real e silencioso: a imagem é construída uma vez e cacheada, então a divergência só
aparece quando alguém constrói do zero meses depois — e aí o sintoma não se parece com "a versão
mudou".

## What Changes

- **Fixar o `pgvector` numa tag de release explícita** no `git clone` (`--branch <tag>`), de modo que
  o build seja determinístico.
- **Registrar a versão no `LABEL` da imagem**, para que a versão efetivamente instalada seja legível
  sem inspecionar o banco.
- **Documentar o procedimento de atualização** — trocar a tag é uma decisão consciente com
  reconstrução da imagem, não um efeito colateral de reconstruir.
- **Registrar explicitamente o que NÃO é fixado e por quê**: a imagem base `postgres:18` (flutua
  dentro da major, e essa flutuação é desejada para receber correções) e os pacotes PGDG
  `postgresql-18-partman` / `postgresql-18-cron` (versões curadas pelo repositório da distribuição,
  não commits arbitrários). Fingir que estão fixados seria pior que declarar a escolha.

Fora de escopo: atualizar a versão do `pgvector` para além do que já está instalado; fixar a imagem
base por digest; alterar `pg_partman`, `pg_cron` ou `shared_preload_libraries`.

## Capabilities

### New Capabilities

(nenhuma)

### Modified Capabilities

- `local-postgres-environment`: ganha o requisito de que a construção da imagem seja reprodutível —
  extensão compilada da fonte precisa de referência imutável, e as fontes que permanecem flutuantes
  precisam estar declaradas como escolha, não presumidas como fixas.

**Dependência:** a capability `local-postgres-environment` é criada pela change
`documentar-postgres-local-extensoes`. Esta change deve ser aplicada **depois** daquela.

## Impact

- **Arquivo alterado**: `infra/local/postgres/dockerfile` — a linha do `git clone` e o `LABEL`.
  Nenhum outro arquivo executável muda.
- **Documentação**: `infra/local/postgres/README.md` ganha a nota de atualização da extensão, dentro
  da seção de extensões criada pela change irmã.
- **Reconstrução necessária**: quem já tem a imagem cacheada continua com a versão que baixou. O
  efeito só aparece no próximo `docker compose build`. Vale confirmar que a versão instalada hoje e a
  tag fixada coincidem — ou registrar conscientemente que a fixação também atualiza.
- **Sem impacto em runtime, dado ou schema.** O `pgvector` não tem consumidor no monorepo (condição
  deliberada, ver `local-postgres-environment`), então mudança de versão não afeta aplicação alguma.
