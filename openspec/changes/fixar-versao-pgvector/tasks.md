## 1. Descobrir a versão instalada hoje

- [ ] 1.1 Com o banco local no ar, obter a versão em uso:
      `SELECT extname, extversion FROM pg_extension WHERE extname = 'pgvector';`
- [ ] 1.2 Identificar a tag de release do `pgvector` correspondente a essa versão
- [ ] 1.3 Se não houver correspondência exata (a versão veio de um commit entre releases), **registrar
      no `design.md`** qual release foi adotada e por quê — a fixação passa a também atualizar, e isso
      precisa ser anunciado, não presumido

## 2. Fixar a referência

- [ ] 2.1 `infra/local/postgres/dockerfile`: acrescentar `--branch <tag>` ao `git clone --depth 1` do
      `pgvector`
- [ ] 2.2 Atualizar o `LABEL` da imagem para declarar a versão fixada (hoje o `description` cita as
      três extensões sem versão nenhuma)
- [ ] 2.3 Manter intacto o resto do estágio `RUN`: instalação por `apt`, `make`/`make install`, e a
      remoção das dependências de build ao final

## 3. Documentação

- [ ] 3.1 Na seção "Extensões" do `README.md` (criada por `documentar-postgres-local-extensoes`):
      procedimento de atualização — trocar a tag no `dockerfile` e reconstruir; reconstruir **sem**
      trocar a tag não muda a versão
- [ ] 3.2 Declarar explicitamente o que permanece flutuante e por quê: imagem base `postgres:18`
      (flutua dentro da major, e essa flutuação é desejada para receber correções) e pacotes PGDG
      `postgresql-18-partman` / `postgresql-18-cron` (versões curadas pela distribuição, não commits
      arbitrários)
- [ ] 3.3 Garantir que a documentação NÃO apresente a imagem como integralmente reprodutível — a
      change entrega reprodutibilidade da **versão da extensão**, não da imagem inteira

## 4. Verificação

- [ ] 4.1 Reconstruir **sem cache** (`docker compose build --no-cache`) e confirmar que a build conclui
- [ ] 4.2 Subir e confirmar que `extversion` do `pgvector` é a esperada, comparando com o valor
      colhido em 1.1
- [ ] 4.3 Confirmar que `pg_partman` e `pg_cron` continuam instalados e criados, e que
      `SHOW shared_preload_libraries;` continua `pg_partman_bgw,pg_cron`
- [ ] 4.4 Confirmar que a tabela `autorizacoes` particionada continua sendo criada normalmente pelas
      migrations num volume limpo
- [ ] 4.5 Confirmar que os metadados da imagem (`docker inspect`) expõem a versão fixada
- [ ] 4.6 Rodar `openspec validate fixar-versao-pgvector --strict`
