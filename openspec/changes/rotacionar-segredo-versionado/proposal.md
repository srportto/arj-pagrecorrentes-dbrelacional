## Why

A auditoria multi-agente de 2026-08-04 encontrou a senha do Postgres `JTMQ9YxDkHfRQbX2`
embutida como valor padrão de `${DB_PASSWORD:-...}` em dois arquivos versionados no git
(`apps/docker-compose.yml` e `infra/local/postgres/postgres-db-v18.yml`). Qualquer pessoa com
acesso de leitura ao repositório — hoje ou em qualquer ponto do histórico — tem a credencial.
O fallback também apaga o sinal de erro: um ambiente que esqueça de definir `DB_PASSWORD` sobe
silenciosamente com a senha do repositório em vez de falhar, então a exposição pode ter
alcançado ambientes além do local sem que ninguém percebesse.

Esta é a única correção da auditoria com urgência independente do restante: remover o arquivo
não remove o segredo, porque ele permanece no histórico de commits.

## What Changes

- Remover o valor padrão embutido das três ocorrências em `apps/docker-compose.yml`
  (linhas 15, 38, 54) e da ocorrência em `infra/local/postgres/postgres-db-v18.yml` (linha 14),
  trocando `${DB_PASSWORD:-JTMQ9YxDkHfRQbX2}` por `${DB_PASSWORD:?...}` — sintaxe do Compose que
  **falha a subida** com mensagem explícita quando a variável não está definida, em vez de
  silenciosamente adotar um padrão.
- Introduzir `.env.example` versionado (com placeholder, sem valor real) documentando as
  variáveis obrigatórias, e garantir que `.env` esteja no `.gitignore`.
- **BREAKING (ambiente local):** subir o ambiente local passa a exigir `DB_PASSWORD` definida.
  Quem hoje roda `docker compose up` sem `.env` vai receber erro na subida até criar o arquivo a
  partir do `.env.example`.
- Rotacionar a credencial em qualquer ambiente onde este valor tenha sido reutilizado, e
  registrar no `README.md` como o time obtém a senha de cada ambiente.
- **Fora de escopo (deliberado):** reescrita do histórico do git (`filter-repo`/BFG) para expurgar
  o segredo dos commits antigos. É operação destrutiva que invalida clones e PRs abertos; a
  decisão de fazê-la ou não fica registrada em `design.md` como risco aceito, e a mitigação
  efetiva é a rotação da credencial.
- **Fora de escopo:** migração para AWS Secrets Manager / Parameter Store nos ambientes de
  nuvem. Vale a pena, mas é mudança de plataforma com raio próprio — esta proposta se limita a
  parar o vazamento e tornar a ausência da variável um erro visível.

## Capabilities

### New Capabilities

- `gestao-de-segredos`: como credenciais e segredos são fornecidos às aplicações e ao ambiente
  local — proibição de valor padrão embutido em arquivo versionado, falha explícita quando a
  variável obrigatória está ausente, e contrato do `.env.example`.

### Modified Capabilities

(nenhuma)

Os specs de ambiente local existentes (`local-aws-environment`, `local-messaging-environment`,
`local-kafka-environment`) cobrem Terraform/Floci e a malha de mensageria — nenhum deles
especifica o `docker-compose` do Postgres nem a origem das credenciais, então não há requisito
existente a alterar. A regra passa a viver na capacidade nova `gestao-de-segredos`, que é
transversal e não pertence a nenhum ambiente específico.

## Impact

- **Arquivos afetados:** `apps/docker-compose.yml`, `infra/local/postgres/postgres-db-v18.yml`,
  `.gitignore`, novo `.env.example`, `README.md` (instruções de setup local).
- **Sem mudança de código de aplicação:** nenhum `.java`, `pom.xml` ou `application.yaml` é
  tocado — as aplicações já leem `DB_PASSWORD` do ambiente corretamente.
- **Operacional:** exige rotação da senha do Postgres nos ambientes onde o valor foi reutilizado,
  e comunicação ao time sobre o novo passo de setup local (`cp .env.example .env`).
- **Risco residual documentado:** o segredo permanece recuperável no histórico do git; a rotação
  é o que efetivamente o neutraliza.
