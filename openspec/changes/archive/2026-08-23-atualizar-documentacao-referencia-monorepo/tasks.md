## 1. Levantamento

- [x] 1.1 Ler `apps/expurgo-particao/src/expurgo_particao/{handler,rotina,calculo,classificacao,estado,persistencia}.py`
      para confirmar variáveis de ambiente, formato do evento e comportamento atual antes de
      documentar
- [x] 1.2 Ler `openspec/specs/reclamacao-particao-expurgo/spec.md` para linkar os requisitos formais
      a partir da documentação nova
- [x] 1.3 Confirmar, com `git log --since=2026-08-22`, que não há mudança adicional além das já
      identificadas (Lambda de expurgo, extensões do Postgres local, versão do pgvector fixada)

## 2. Documentação de `apps/expurgo-particao`

- [x] 2.1 Criar `apps/expurgo-particao/README.md`: o que a app faz, como rodar os testes
      (`pytest`), variáveis de ambiente exigidas pelo `handler.py`, formato do evento de invocação
      (`data_referencia`, `modo_consulta`), link para a capability `reclamacao-particao-expurgo` —
      sem seção de endpoint HTTP ou profile Spring, que não existem aqui (D1 do design)
- [x] 2.2 Criar `apps/expurgo-particao/CLAUDE.md`: armadilhas e decisões resumidas (por que
      `TRUNCATE`, por que offset +2, por que sem dry-run, por que `host.docker.internal`, por que
      `pg_cron` não tem poder de escrita), referenciando o design da change arquivada
      `reclamar-particao-expurgo-ciclo` em vez de reabrir a justificativa por extenso (D2 do design)
- [x] 2.3 Criar `apps/expurgo-particao/AGENTS.md` como espelho byte a byte do `CLAUDE.md` recém
      criado

## 3. Documentação de raiz

- [x] 3.1 `README.md`: ajustar a frase de abertura ("cinco microserviços") para refletir as seis
      apps, distinguindo os cinco serviços Java (request/response ou consumo de fila) da Lambda
      agendada
- [x] 3.2 `README.md`: adicionar bloco/diagrama próprio do ciclo de expurgo (escrita por
      `contratocommand`, reclamação por `expurgo-particao`, auditoria por `pg_cron`) — sem inserir
      no fluxograma síncrono existente (D3 do design)
- [x] 3.3 `README.md`: adicionar `expurgo-particao` à estrutura de pastas (`apps/`) e a uma
      tabela/linha própria (não a tabela de "Microserviços" com porta HTTP, que não se aplica)
- [x] 3.4 `README.md`: adicionar `infra/local/postgres/README.md` e `apps/expurgo-particao/README.md`
      à tabela "Documentação"
- [x] 3.5 `AGENTS.md` (raiz): copiar o parágrafo faltante sobre o ring buffer de expurgo do
      `CLAUDE.md` (raiz), restaurando o espelho — usar `diff` para confirmar identidade total ao
      final

## 4. Documentação de infraestrutura

- [x] 4.1 `infra/README.md`: citar `infra/modules/lambda-scheduled/` no mesmo padrão em que os
      módulos das cinco apps Java já são descritos
- [x] 4.2 `infra/README.md`: citar o repositório ECR de `expurgo-particao` junto aos das demais apps

## 5. Specs

- [x] 5.1 Copiar o requisito integral "README de raiz linka para documentação de cada app" de
      `openspec/specs/readme-raiz/spec.md` para a delta spec desta change e editar conforme já
      redigido (cobertura de todas as apps, não subconjunto fixo)
- [x] 5.2 Copiar o requisito integral "Toda app tem os arquivos de documentação do seu papel" de
      `openspec/specs/higiene-documentacao-repo/spec.md` para a delta spec desta change e editar
      conforme já redigido (todas as apps, não "cinco")

## 6. Verificação final

- [x] 6.1 Confirmar com `git diff --stat` que nenhum arquivo executável (`.py`, `.java`, `.tf`,
      compose) foi alterado — só `.md` e specs do OpenSpec
- [x] 6.2 Verificar que todo link relativo novo ou alterado resolve para caminho existente (mesmo
      mecanismo exigido por `higiene-documentacao-repo`, sem depender de `grep -P`)
- [x] 6.3 Confirmar que nenhum arquivo novo ou alterado linka `openspec/changes/<nome>/` por caminho
      relativo — referenciar por nome de change ou pela spec da capability, conforme
      `higiene-documentacao-repo`
- [x] 6.4 Rodar `diff` entre `CLAUDE.md` e `AGENTS.md` de `apps/expurgo-particao/` e entre os da raiz,
      confirmando identidade total
- [x] 6.5 Rodar `openspec validate atualizar-documentacao-referencia-monorepo --strict`
