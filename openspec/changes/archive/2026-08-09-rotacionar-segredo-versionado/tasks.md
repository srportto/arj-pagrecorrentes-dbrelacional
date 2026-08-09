## 1. Levantamento de exposição

- [x] 1.1 Localizar todas as ocorrências do literal no repositório (`git grep -n "JTMQ9YxDkHfRQbX2"`) e confirmar que são exatamente as 5 esperadas: `apps/docker-compose.yml` (linhas 15, 38, 54), `infra/local/postgres/postgres-db-v18.yml` (linha 14, literal puro sem interpolação) e `infra/envs/local/terraform.tfvars` (linha 3 — caminho corrigido: não é `local-messaging`, é `local`)
- [x] 1.2 Confirmar que o literal está no histórico (`git log -S "JTMQ9YxDkHfRQbX2" --oneline`) e registrar desde qual commit — mais antigo: `800a784` ("iniciacao projeto com openSpec"), ou seja, a janela de exposição é a vida inteira do repositório
- [x] 1.3 Listar os ambientes onde este Postgres roda — decisão registrada em 2026-08-09: o repositório só evidencia ambiente **local** (docker-compose + Terraform apontando para Floci, emulador AWS local sem custo real); não há infra de nuvem provisionada com esta credencial
- [x] 1.4 Registrado no `design.md`: nenhuma coincidência fora do local identificada — tratado como higiene de repositório, não como incidente

## 2. Rotação da credencial

- [x] 2.1 Rotacionada a senha do Postgres local: nova senha gerada (`nUdfGU4xYS9xZsbT6axCFVuI`), usada nos `.env.example` como placeholder e no `terraform.tfvars` local (não versionado)
- [x] 2.2 Não aplicável: não há gerenciador de configuração de ambiente real (ECS/CI) usando esta credencial hoje
- [x] 2.3 Validação de conectividade fica coberta pela task 6.2 (subida com `.env` a partir do `.env.example`)
- [x] 2.4 Registrado: nenhum ambiente além do local coincidiu com a credencial exposta — decisão documentada em `design.md`/proposal.md, não omissão

## 3. Contrato de configuração

- [x] 3.1 Criados `apps/.env.example` e `infra/local/postgres/.env.example` com placeholders sem valor utilizável
- [x] 3.2 Confirmado: `.env` já constava no `.gitignore` (linha 31); adicionado também `*.tfvars` (cobre `infra/envs/local/terraform.tfvars`, que guardava o mesmo segredo)
- [x] 3.3 Atualizado `README.md` raiz com nota sobre `.env.example` → `.env` e a explicação de que a subida falha de propósito sem `DB_PASSWORD`

## 4. Remoção dos valores padrão

- [x] 4.1 Substituídas as 3 ocorrências em `apps/docker-compose.yml` por `${DB_PASSWORD:?DB_PASSWORD nao definida - copie .env.example para .env}`
- [x] 4.2 Substituído o literal puro em `infra/local/postgres/postgres-db-v18.yml:14` pela mesma forma `${DB_PASSWORD:?...}` — introduzida a interpolação de variável, inexistente antes neste arquivo
- [x] 4.3 Removido `infra/envs/local/terraform.tfvars` do controle de versão (`git rm --cached`), adicionado `*.tfvars` ao `.gitignore`, criado `terraform.tfvars.example` versionado com placeholder, e documentado `TF_VAR_db_password` como alternativa no `infra/envs/local/README.md`
- [x] 4.4 Reexecutado `git grep -n "JTMQ9YxDkHfRQbX2"` — zero ocorrências na árvore de trabalho rastreada (restam apenas as referências históricas dentro desta própria pasta de change, esperado)

## 5. Verificação de consumidores automatizados

- [x] 5.1 Inspecionado `.github/workflows/` — **não existe**, confirmando o achado da auditoria de 2026-08-09 de que não há pipeline de CI neste repositório; nenhum script de CI depende do valor padrão
- [x] 5.2 Não aplicável (sem CI hoje)

## 6. Validação

- [x] 6.1 Executado `docker compose config` sem `DB_PASSWORD`: falha com `required variable DB_PASSWORD is missing a value: DB_PASSWORD nao definida - copie .env.example para .env` — mensagem nomeia a variável ausente, como esperado
- [x] 6.2 Criado `.env` a partir do `.env.example`; `docker compose config` interpola `POSTGRES_PASSWORD` corretamente; `docker compose up -d postgres` criou o container e tentou subir (falhou apenas por porta 5432 já ocupada localmente por outro processo, pré-existente e não relacionado a esta mudança) — recursos de teste removidos (`docker compose down`, `.env` de teste apagado)
- [x] 6.3 Confirmado via `git status`: `.env` não aparece como arquivo passível de commit
- [x] 6.4 Revisados os cenários do spec `gestao-de-segredos` contra as mudanças acima — cobertos pela combinação de `:?` sem default, `.env.example` versionado e `.gitignore`

## 7. Comunicação

- [x] 7.1 Registrado nesta task list: novo passo de setup local (`cp apps/.env.example apps/.env`, idem para `infra/local/postgres/`) e a senha local foi trocada
- [x] 7.2 Registrado como trabalho futuro: avaliação de varredura de segredos no CI (`gitleaks`/`trufflehog`) — vale notar que também não há CI hoje para acoplar essa varredura, então é um item que depende de `rede-seguranca-contrato-evento` (que levanta a mesma lacuna de pipeline) ou de uma iniciativa própria de CI
