## 1. Levantamento da receita já exercitada

- [ ] 1.1 Mapear, a partir de `infra/local/postgres/dockerfile`, os dois caminhos de instalação e a
      extensão que exemplifica cada um: pacote PGDG (`postgresql-18-partman`, `postgresql-18-cron`) e
      compilação da fonte (`pgvector`)
- [ ] 1.2 Confirmar, no banco local no ar, o estado de cada uma das três extensões nos **dois** eixos:
      presente em `shared_preload_libraries` e presente em `pg_extension`
- [ ] 1.3 Registrar a assimetria que o documento precisa explicar: `pgvector` está criado mas **não**
      no preload — é o exemplar do critério negativo

## 2. Seção de extensões no README

- [ ] 2.1 `infra/local/postgres/README.md`: seção nova "Extensões", posicionada após as seções
      existentes de subir/validar/parar (que permanecem inalteradas)
- [ ] 2.2 Documentar as três etapas na ordem de execução, nomeando **em qual arquivo** cada uma é
      feita: instalar (`dockerfile`) → declarar no preload quando exigido (`postgres-db-v18.yml`) →
      `CREATE EXTENSION` (migration)
- [ ] 2.3 Deixar explícito que as três etapas vivem em três arquivos diferentes e que ler qualquer um
      isolado não revela o procedimento
- [ ] 2.4 Documentar o caminho de instalação por pacote PGDG, apontando `pg_partman`/`pg_cron` como
      exemplo real do repositório
- [ ] 2.5 Documentar o caminho de instalação por compilação da fonte, apontando `pgvector` como
      exemplo, incluindo a remoção das dependências de build ao fim do estágio
- [ ] 2.6 Registrar a armadilha da GUC repetida (`shared_preload_libraries` em dois `-c` não acumula;
      o último vence sem erro, aviso ou log), referenciando o requisito já existente em
      `orquestracao-local-unificada` em vez de reespecificá-lo

## 3. Critério de preload

- [ ] 3.1 Enunciar o critério por **natureza da extensão**: background worker ou hook de servidor →
      exige preload e reinício; apenas tipos/funções/operadores/índices → basta `CREATE EXTENSION`
- [ ] 3.2 Classificar as três extensões presentes segundo o critério, como ilustração (não como
      definição): `pg_partman_bgw` e `pg_cron` exigem preload; `pgvector` não
- [ ] 3.3 Escrever o passo prático de "somar uma extensão nova": como decidir, antes de tentar, se ela
      cairá de um lado ou do outro

## 4. Verificação de cada etapa

- [ ] 4.1 Documentar o comando que exibe as bibliotecas efetivamente carregadas pelo servidor, com o
      resultado esperado (a seção "Validar que está no ar" já traz `SHOW shared_preload_libraries;` —
      referenciar em vez de duplicar)
- [ ] 4.2 Documentar o comando que lista as extensões criadas no banco com suas versões
      (`\dx` ou consulta a `pg_extension`), com o resultado esperado
- [ ] 4.3 Explicar que os dois estados são **independentes**, com o par de exemplos concretos:
      extensão no preload mas não criada × extensão criada sem estar no preload (`pgvector`)
- [ ] 4.4 Incluir o sintoma de diagnóstico: "a extensão não funciona" investiga arquivo errado quando
      os dois estados não são separados antes

## 5. Registro da intencionalidade

- [ ] 5.1 Documentar, no README, que o ambiente local tem dois propósitos simultâneos — servir as
      aplicações **e** demonstrar a construção de um PostgreSQL com extensões auxiliares
- [ ] 5.2 Registrar que `pg_partman` está carregado sem uso **por intenção** (o ring buffer é gerido
      por fórmula em `ControleExpurgoAutorizacao`, não por partman) e que `pgvector` não tem
      consumidor — nenhuma das duas condições é dívida técnica
- [ ] 5.3 Registrar que nenhuma change de higiene, auditoria de dependências ou limpeza de
      configuração deve remover extensão por ausência de uso, e qual é a justificativa válida para
      remover (a demonstração deixou de ser desejada)
- [ ] 5.4 Garantir que essa informação seja encontrável a partir do próprio ambiente local — uma
      pessoa que abra o `dockerfile` ou o compose e pergunte "por que isto está aqui?" chega à
      resposta

## 6. Verificação final

- [ ] 6.1 Confirmar com `git diff --stat` que **nenhum** arquivo executável foi alterado: `dockerfile`,
      `postgres-db-v18.yml` e as sete migrations byte a byte idênticos
- [ ] 6.2 Confirmar que `shared_preload_libraries` continua `pg_partman_bgw,pg_cron`
- [ ] 6.3 Seguir o procedimento documentado do zero com uma extensão de teste, num banco descartável,
      e confirmar que ele leva a uma extensão funcionando sem consulta a nenhuma outra fonte —
      reverter em seguida, sem commit
- [ ] 6.4 Confirmar que a seção nova não reespecifica o que `orquestracao-local-unificada` já cobre
      (definição única do serviço, migrations em qualquer caminho, healthcheck, GUC única)
- [ ] 6.5 Rodar `openspec validate documentar-postgres-local-extensoes --strict`
