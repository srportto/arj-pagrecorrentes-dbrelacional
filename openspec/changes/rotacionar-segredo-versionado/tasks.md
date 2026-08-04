## 1. Levantamento de exposição

- [ ] 1.1 Localizar todas as ocorrências do literal no repositório (`git grep -n "JTMQ9YxDkHfRQbX2"`) e confirmar que são exatamente as 4 esperadas: `apps/docker-compose.yml` (linhas 15, 38, 54) e `infra/local/postgres/postgres-db-v18.yml` (linha 14)
- [ ] 1.2 Confirmar que o literal está no histórico (`git log -S "JTMQ9YxDkHfRQbX2" --oneline`) e registrar desde qual commit — define a janela de exposição
- [ ] 1.3 Listar os ambientes onde este Postgres roda e verificar, em cada um, se a senha atual coincide com o literal exposto
- [ ] 1.4 Registrar o resultado de 1.3 na seção "Open Questions" do `design.md` — se houve coincidência fora do local, a mudança deixa de ser higiene e vira tratamento de incidente

## 2. Rotação da credencial

- [ ] 2.1 Rotacionar a senha do Postgres em cada ambiente identificado em 1.3 como afetado
- [ ] 2.2 Atualizar o segredo no gerenciador de configuração de cada ambiente afetado (variável de ambiente da task ECS, secret do CI, etc.)
- [ ] 2.3 Validar que cada ambiente rotacionado continua conectando ao banco após a troca
- [ ] 2.4 Se nenhum ambiente além do local coincidiu, registrar explicitamente essa conclusão — a ausência de rotação passa a ser decisão documentada, não omissão

## 3. Contrato de configuração

- [ ] 3.1 Criar `.env.example` na raiz enumerando as variáveis obrigatórias do ambiente local, com placeholders sem valor utilizável (ex.: `DB_PASSWORD=<defina-sua-senha-local>`)
- [ ] 3.2 Confirmar que `.env` consta no `.gitignore`; adicionar se ausente
- [ ] 3.3 Atualizar o `README.md` com o passo de setup `cp .env.example .env` e a explicação de que a subida falha de propósito sem ele

## 4. Remoção dos valores padrão

- [ ] 4.1 Substituir as 3 ocorrências em `apps/docker-compose.yml` por `${DB_PASSWORD:?DB_PASSWORD nao definida - copie .env.example para .env}`
- [ ] 4.2 Substituir a ocorrência em `infra/local/postgres/postgres-db-v18.yml` pela mesma forma
- [ ] 4.3 Reexecutar `git grep -n "JTMQ9YxDkHfRQbX2"` e confirmar zero ocorrências na árvore de trabalho

## 5. Verificação de consumidores automatizados

- [ ] 5.1 Inspecionar `.github/workflows/` e qualquer script que suba o compose, verificando se algum dependia do valor padrão
- [ ] 5.2 Ajustar os que dependiam para injetar `DB_PASSWORD` explicitamente (secret do CI, variável de ambiente do runner)

## 6. Validação

- [ ] 6.1 Executar `docker compose up` sem `.env` e sem `DB_PASSWORD` exportada; confirmar que aborta antes de subir container e que a mensagem nomeia a variável ausente
- [ ] 6.2 Executar `docker compose up` com `.env` criado a partir do `.env.example`; confirmar que os containers sobem e as aplicações conectam ao Postgres
- [ ] 6.3 Confirmar que `git status` não lista `.env` como arquivo passível de commit
- [ ] 6.4 Revisar os 4 cenários do spec `gestao-de-segredos` e confirmar que cada um foi exercitado

## 7. Comunicação

- [ ] 7.1 Comunicar ao time o novo passo de setup local e, se houve rotação, a troca de credencial
- [ ] 7.2 Registrar como trabalho futuro a avaliação de varredura de segredos no CI (`gitleaks`/`trufflehog`) — impede a reincidência desta classe de problema
