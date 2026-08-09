## Context

A auditoria multi-agente de 2026-08-04 localizou a senha do Postgres `JTMQ9YxDkHfRQbX2` como
valor padrão de expansão de variável em dois arquivos rastreados pelo git:

| Arquivo | Linhas | Forma |
|---|---|---|
| `apps/docker-compose.yml` | 15, 38, 54 | `${DB_PASSWORD:-JTMQ9YxDkHfRQbX2}` |
| `infra/local/postgres/postgres-db-v18.yml` | 14 | `POSTGRES_PASSWORD: JTMQ9YxDkHfRQbX2` (literal puro, sem variável — corrigido em auditoria de 2026-08-09; a versão original deste documento descrevia como fallback `${...:-...}`) |
| `infra/envs/local/terraform.tfvars` | 3 | `db_password = "JTMQ9YxDkHfRQbX2"` (ocorrência não catalogada na auditoria original, encontrada em 2026-08-09) |

O agravante não é só a presença do literal, e sim a **sintaxe `:-`** nos dois primeiros casos:
ela transforma a ausência da variável num caminho de sucesso silencioso. Um ambiente mal
configurado não falha — ele sobe usando a senha do repositório. O `.tfvars` é ainda mais direto:
não há nem interpolação, o Terraform lê o literal sempre que o arquivo é carregado. Por isso não
é possível afirmar, apenas lendo o repo, que a exposição ficou restrita ao ambiente local; a
única evidência disso viria de inspecionar cada ambiente onde o compose ou o Terraform já
rodaram — e `git log -S "JTMQ9YxDkHfRQbX2"` mostra 6 commits desde a origem do repositório
(`800a784`), então a janela de exposição é a vida inteira do projeto.

As aplicações Java não estão envolvidas: elas leem `DB_PASSWORD` do ambiente corretamente, e os
profiles `prod` dos quatro `application.yaml` já usam variáveis sem default (verificado na
auditoria de segurança). O problema é exclusivo dos arquivos de composição.

## Goals / Non-Goals

**Goals:**

- Eliminar o literal da árvore de trabalho atual.
- Fazer a ausência de `DB_PASSWORD` virar **falha ruidosa** em vez de fallback silencioso.
- Dar ao time um caminho de setup local óbvio (`.env.example` → `.env`), para que a remoção do
  default não vire atrito diário.
- Registrar explicitamente que a rotação da credencial — não a edição do arquivo — é o que
  neutraliza o vazamento.

**Non-Goals:**

- Reescrever o histórico do git para expurgar o segredo dos commits antigos.
- Migrar para AWS Secrets Manager / SSM Parameter Store nos ambientes de nuvem.
- Alterar qualquer código de aplicação, `pom.xml` ou `application.yaml`.
- Introduzir varredura automatizada de segredos em CI (`gitleaks`, `trufflehog`) — é o próximo
  passo natural desta capacidade, mas amplia o raio da mudança e depende de decisão sobre a
  pipeline.

## Decisions

### D1 — `${DB_PASSWORD:?mensagem}` em vez de `${DB_PASSWORD}` puro

O Compose oferece três formas:

| Forma | Comportamento quando a variável falta |
|---|---|
| `${DB_PASSWORD:-literal}` | Usa o literal — **é o problema atual** |
| `${DB_PASSWORD}` | Expande para string vazia; o Postgres sobe com senha vazia ou falha com erro obscuro |
| `${DB_PASSWORD:?erro}` | Aborta a subida com a mensagem informada |

Escolhemos a terceira. A segunda ainda deixa um caminho de falha silenciosa (senha vazia), que é
a mesma classe de problema que estamos corrigindo — só que menos visível. A terceira é a única
que converte configuração ausente em erro imediato e legível.

### D2 — `.env.example` versionado com placeholder, `.env` ignorado

Remover o default sem oferecer substituto empurra o time a inventar workarounds (exportar a
variável no shell, recriar o default localmente). O `.env.example` documenta o contrato — quais
variáveis existem e o que significam — sem carregar nenhum valor real. O `.env` real fica fora do
git.

Alternativa considerada: manter um `docker-compose.override.yml` local não versionado. Descartada
porque o override é um mecanismo mais obscuro e não se autodocumenta como uma lista de variáveis.

### D3 — Não reescrever o histórico do git nesta mudança

`git filter-repo` ou BFG removeriam o literal dos commits antigos, mas reescrevem todos os SHAs:
invalidam clones existentes, quebram PRs abertos e exigem force-push coordenado com o time
inteiro. O ganho é limitado, porque qualquer clone já feito antes da reescrita continua com o
segredo.

A mitigação que realmente funciona é a rotação: uma vez trocada a senha, o valor no histórico
vira lixo. Registramos a decisão aqui para que ela seja consciente, e não uma omissão.

Alternativa considerada: reescrever o histórico **além** de rotacionar. Continua sendo uma opção
válida se houver exigência de compliance — fica como decisão separada, fora deste escopo.

## Risks / Trade-offs

- **A senha pode ter sido reutilizada em ambiente real, e ninguém sabe** → A verificação é manual
  e precisa acontecer antes de considerar a mudança concluída: conferir cada ambiente onde o
  Postgres roda e confirmar se a credencial coincide. A rotação deve cobrir todos os que
  coincidirem. Esta é a tarefa de maior valor e a única que não é editável em código.

- **Atrito no setup local: `docker compose up` passa a falhar sem `.env`** → Mitigado pelo
  `.env.example` mais uma linha no `README.md`. A falha é intencional e traz mensagem explícita
  (`DB_PASSWORD não definida — copie .env.example para .env`), então o caminho de correção é
  auto-evidente. Trade-off aceito: um passo a mais no onboarding em troca de eliminar uma classe
  inteira de falha silenciosa.

- **CI ou scripts automatizados podem depender do default** → Antes de mesclar, verificar
  `.github/workflows/` e qualquer script que suba o compose. Se algum depender, precisa passar a
  injetar a variável — está previsto nas tasks.

- **O segredo continua recuperável no histórico** → Risco aceito conscientemente (D3), neutralizado
  pela rotação. Se surgir exigência de compliance que torne isso inaceitável, a reescrita de
  histórico vira uma mudança própria.

## Migration Plan

1. Auditar ambientes: onde este Postgres roda e onde a senha coincide.
2. Rotacionar a credencial nos ambientes afetados.
3. Publicar `.env.example` e atualizar o `README.md` **antes** de remover os defaults, para que o
   caminho de correção já exista quando a falha começar a aparecer.
4. Remover os defaults dos dois arquivos de compose.
5. Comunicar o time: novo passo `cp .env.example .env`.

Rollback: reverter o commit restaura os defaults. Não desfaz a rotação — nem deveria.

## Open Questions

- A senha foi reutilizada fora do ambiente local? Só uma inspeção manual dos ambientes responde,
  e a resposta define se esta mudança é higiene ou incidente.
- Vale adicionar varredura de segredos (`gitleaks`) ao CI como continuação natural desta
  capacidade? Fora de escopo aqui, mas é o que impediria a reincidência.
