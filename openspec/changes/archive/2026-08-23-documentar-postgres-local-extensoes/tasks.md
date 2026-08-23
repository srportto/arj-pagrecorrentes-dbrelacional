## 1. Levantamento da receita já exercitada

- [x] 1.1 Mapear, a partir de `infra/local/postgres/dockerfile`, os dois caminhos de instalação e a
      extensão que exemplifica cada um: pacote PGDG (`postgresql-18-partman`, `postgresql-18-cron`) e
      compilação da fonte (`pgvector`)
- [x] 1.2 Confirmar, no banco local no ar, o estado de cada uma das três extensões nos **dois** eixos:
      presente em `shared_preload_libraries` e presente em `pg_extension`
- [x] 1.3 Registrar a assimetria que o documento precisa explicar: `pgvector` está criado mas **não**
      no preload — é o exemplar do critério negativo

## 2. Seção de extensões no README

- [x] 2.1 `infra/local/postgres/README.md`: seção nova "Extensões", posicionada após as seções
      existentes de subir/validar/parar (que permanecem inalteradas)
- [x] 2.2 Documentar as três etapas na ordem de execução, nomeando **em qual arquivo** cada uma é
      feita: instalar (`dockerfile`) → declarar no preload quando exigido (`postgres-db-v18.yml`) →
      `CREATE EXTENSION` (migration)
- [x] 2.3 Deixar explícito que as três etapas vivem em três arquivos diferentes e que ler qualquer um
      isolado não revela o procedimento
- [x] 2.4 Documentar o caminho de instalação por pacote PGDG, apontando `pg_partman`/`pg_cron` como
      exemplo real do repositório
- [x] 2.5 Documentar o caminho de instalação por compilação da fonte, apontando `pgvector` como
      exemplo, incluindo a remoção das dependências de build ao fim do estágio
- [x] 2.6 Registrar a armadilha da GUC repetida (`shared_preload_libraries` em dois `-c` não acumula;
      o último vence sem erro, aviso ou log), referenciando o requisito já existente em
      `orquestracao-local-unificada` em vez de reespecificá-lo

## 3. Critério de preload

- [x] 3.1 Enunciar o critério por **natureza da extensão**: background worker ou hook de servidor →
      exige preload e reinício; apenas tipos/funções/operadores/índices → basta `CREATE EXTENSION`
- [x] 3.2 Classificar as três extensões presentes segundo o critério, como ilustração (não como
      definição): `pg_partman_bgw` e `pg_cron` exigem preload; `pgvector` não
- [x] 3.3 Escrever o passo prático de "somar uma extensão nova": como decidir, antes de tentar, se ela
      cairá de um lado ou do outro

## 4. Verificação de cada etapa

- [x] 4.1 Documentar o comando que exibe as bibliotecas efetivamente carregadas pelo servidor, com o
      resultado esperado (a seção "Validar que está no ar" já traz `SHOW shared_preload_libraries;` —
      referenciar em vez de duplicar)
- [x] 4.2 Documentar o comando que lista as extensões criadas no banco com suas versões
      (`\dx` ou consulta a `pg_extension`), com o resultado esperado
- [x] 4.3 Explicar que os dois estados são **independentes**, com o par de exemplos concretos:
      extensão no preload mas não criada × extensão criada sem estar no preload (`pgvector`)
- [x] 4.4 Incluir o sintoma de diagnóstico: "a extensão não funciona" investiga arquivo errado quando
      os dois estados não são separados antes

## 5. Registro da intencionalidade

- [x] 5.1 Documentar, no README, que o ambiente local tem dois propósitos simultâneos — servir as
      aplicações **e** demonstrar a construção de um PostgreSQL com extensões auxiliares
- [x] 5.2 Registrar que `pg_partman` está carregado sem uso **por intenção** (o ring buffer é gerido
      por fórmula em `ControleExpurgoAutorizacao`, não por partman) e que `pgvector` não tem
      consumidor — nenhuma das duas condições é dívida técnica
- [x] 5.3 Registrar que nenhuma change de higiene, auditoria de dependências ou limpeza de
      configuração deve remover extensão por ausência de uso, e qual é a justificativa válida para
      remover (a demonstração deixou de ser desejada)
- [x] 5.4 Garantir que essa informação seja encontrável a partir do próprio ambiente local — uma
      pessoa que abra o `dockerfile` ou o compose e pergunte "por que isto está aqui?" chega à
      resposta

## 6. Verificação final

- [x] 6.1 Confirmar com `git diff --stat` que **nenhum** arquivo executável foi alterado: `dockerfile`,
      `postgres-db-v18.yml` e as sete migrations byte a byte idênticos
      (ver nota no relatório final: `git diff --stat -- infra/local/postgres/postgres-db-v18.yml
      infra/local/postgres/migrations/` está vazio; esta change em si só editou `README.md`.
      Há uma modificação **pré-existente e não relacionada** em `dockerfile`, de uma sessão
      concorrente trabalhando na change `fixar-versao-pgvector` — não foi tocada por este trabalho)
- [x] 6.2 Confirmar que `shared_preload_libraries` continua `pg_partman_bgw,pg_cron`
- [x] 6.3 Seguir o procedimento documentado do zero com uma extensão de teste, num banco descartável,
      e confirmar que ele leva a uma extensão funcionando sem consulta a nenhuma outra fonte —
      reverter em seguida, sem commit
- [x] 6.4 Confirmar que a seção nova não reespecifica o que `orquestracao-local-unificada` já cobre
      (definição única do serviço, migrations em qualquer caminho, healthcheck, GUC única)
- [x] 6.5 Rodar `openspec validate documentar-postgres-local-extensoes --strict`
