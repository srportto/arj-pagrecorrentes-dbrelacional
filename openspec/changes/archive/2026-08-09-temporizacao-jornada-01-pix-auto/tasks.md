## 1. Schema: coluna tipo_jornada

- [x] 1.1 Adicionar a coluna `tipo_jornada` ao DDL de `autorizacoes` (aceitando valor de jornada desconhecida para linhas legadas) e registrar o script em `apps/contratocommand/docs/comandos-sql.txt`
- [x] 1.2 Declarar o campo `tipoJornada` na entidade `Autorizacao` do `contratocommand`, com o converter adequado ao tipo da coluna
- [x] 1.3 Espelhar o campo na entidade equivalente do `contratoquery`
- [x] 1.4 Passar `AutorizacaoMapper` a gravar a jornada na entidade, além de continuar derivando `motivo_status` dela
- [x] 1.5 Testes: criação por cada jornada persiste `tipo_jornada` coerente e mantém o `motivo_status` atual

## 2. Enum de motivo e domínio da decisão

- [x] 2.1 Adicionar `REJEITADA_SISTEMA_TIMEOUT_J1` a `MotivoStatusAutorizacao`, com código próprio não colidente
- [x] 2.2 Teste: o novo valor é resolvível por código e não colide com nenhum existente
- [x] 2.3 Confirmar por teste que o grafo de `StatusAutorizacao` permanece inalterado (`RECEBIDA → REJEITADA` e `RECEBIDA → EM_PROCESSO_ATIVACAO → ATIVA` já cobrem os caminhos da decisão)

## 3. Rota de decisão no contratocommand

- [x] 3.1 Criar `DecisaoAutorizacaoRequest` (record imutável) com `acao` validada contra `APROVAR`/`REJEITAR`/`EXPIRAR` e demais campos do canal
- [x] 3.2 Criar `DecisaoContext` (record imutável) com id do path, `tipoProduto` do header e dados do corpo
- [x] 3.3 Criar `DecisaoRule` + `DecisaoValidator` no padrão do framework de validação existente
- [x] 3.4 Criar rule que valida a transição pedida contra `podeTransicionarPara` a partir do status atual, lançando `BusinessException` com o status atual na mensagem
- [x] 3.5 Implementar `DecidirAutorizacaoUseCase` (`@Transactional`): carrega por UUID + partição extraída, valida, aplica status e motivo por ação, atualiza `data_hora_ultima_atlz` e publica um único `AutorizacaoPersistidaEvent`
- [x] 3.6 Expor `PATCH /api/autorizacoes/{idAutorizacao}/decisao` no `AutorizacaoController`, resolvendo o header `tipoProduto` no padrão do cancelamento
- [x] 3.7 Testes de use case: aprovação grava `ATIVA` + `AUTORIZACAO_ACEITA_POR_TODOS`; rejeição grava `REJEITADA` + `REJEITADA_PAGADOR`; expiração grava `REJEITADA` + `REJEITADA_SISTEMA_TIMEOUT_J1`
- [x] 3.8 Testes de idempotência: decisão sobre autorização já resolvida devolve 422, não altera a linha e não publica evento; decisão repetida publica exatamente um evento no total
- [x] 3.9 Teste de controller: ação inválida e corpo sem `acao` resultam em 422

## 4. Enriquecimento do evento publicado

- [x] 4.1 Adicionar `tipo_jornada` a `AutorizacaoEventoPayload` no `contratocommand`
- [x] 4.2 Publicar os message attributes `tipoProduto` e `tipoJornada` em `AutorizacaoEventoPublisher`, derivados da linha
- [x] 4.3 Testes do publisher: os três attributes acompanham cada evento e são coerentes com o body; aprovação publica `ATIVACAO`; rejeição e expiração publicam `REJEICAO`
- [x] 4.4 Espelhar `tipo_jornada` em `AutorizacaoEventoPayload` do `autorizacaostatus-producer`
- [x] 4.5 Espelhar o campo em `EventoAutorizacao.avsc` como `["null","long"]` com `default: null`, em `autorizacaostatus-producer` **e** em `eventos-consumer`
- [x] 4.6 Confirmar que o campo nullable não entra na validação de campos obrigatórios do producer e que o consumidor existente segue funcionando

## 5. Infraestrutura de mensageria e Valkey

- [x] 5.1 Adicionar fila `SQS-temporizacao-autorizacao` + DLQ + `redrive_policy` em `infra/envs/local-messaging/`
- [x] 5.2 Adicionar a subscription do tópico para a nova fila, com `raw_message_delivery`, política de publicação e filter policy dos três attributes
- [x] 5.3 Verificar o suporte do emulador à filter policy; se ausente, isolar a divergência no Terraform e documentar no README do root
- [x] 5.4 Criar `infra/local/redis/compose.yaml` com Valkey e append-only file sincronizado a cada segundo, mais o README de subir/validar/parar
- [x] 5.5 Criar o módulo `infra/modules/elasticache-valkey/` (variables, outputs, README), com `snapshot_retention_limit` > 0 e security group sem origem irrestrita
- [x] 5.6 Referenciar o módulo no root `infra/envs/prod/` sem instanciá-lo nos roots locais

## 6. Aplicação temporiza-autorizacao

- [x] 6.1 Criar `apps/temporiza-autorizacao` no esqueleto hexagonal do monorepo (porta 8084, Actuator, profiles `local`/`prod`, Dockerfile multi-stage)
- [x] 6.2 Adicionar as dependências: `spring-cloud-aws-starter-sqs`, cliente Valkey e cliente HTTP; **sem** JPA e sem driver PostgreSQL
- [x] 6.3 Implementar o adapter de entrada `@SqsListener` da fila de temporização, delegando ao use case sem `try/catch`
- [x] 6.4 Implementar o interceptor de erro do listener, classificando payload inválido (confirma) e falha de infraestrutura (retém), sem logar o corpo
- [x] 6.5 Implementar o cálculo do vencimento a partir de `data_hora_inclusao` + prazo configurável (padrão 10 min), com disparo imediato para vencimento já no passado
- [x] 6.6 Implementar a porta de saída de agendamento e o adapter Valkey que insere no sorted set usando o id da autorização como member
- [x] 6.7 Implementar a varredura agendada com script Lua atômico: seleciona vencidos em lote, remove do sorted set e só então cria a entrada no stream
- [x] 6.8 Criar o consumer group do stream na subida da aplicação, de forma idempotente
- [x] 6.9 Implementar o worker de leitura por consumer group, com confirmação apenas após desfecho conclusivo
- [x] 6.10 Implementar a reivindicação periódica de pendências ociosas do grupo
- [x] 6.11 Implementar o client HTTP que aciona `PATCH /{id}/decisao` com `acao: EXPIRAR`, classificando 2xx e 4xx como conclusivos e 5xx/timeout como retryable
- [x] 6.12 Implementar o health indicator refletindo o consumo da fila e a conexão com o Valkey
- [x] 6.13 Nomear as chaves do sorted set e do stream com escopo de hash comum, para operação em cluster
- [x] 6.14 Testes: agendamento idempotente para evento duplicado; vencimento não adiado por reentrega; varredura concorrente elege um único executor
- [x] 6.15 Testes: 2xx e 422 confirmam a entrada; 5xx/timeout mantêm a pendência; nenhum log carrega o corpo do evento

## 7. Verificação ponta a ponta

- [x] 7.1 Subir Postgres, Floci (com o Terraform de mensageria aplicado), Kafka e Valkey locais
- [x] 7.2 Criar autorização `PIX_AUTO` em `SPI_J1` e confirmar que a mensagem chega **apenas** às filas esperadas, com os três attributes presentes
- [x] 7.3 Confirmar que `DDA_AUTO` e `PIX_AUTO` em `QRC_J2` não chegam à fila de temporização
- [x] 7.4 Com prazo reduzido por configuração, confirmar a expiração automática: status vira `REJEITADA` com `REJEITADA_SISTEMA_TIMEOUT_J1` e o evento `REJEICAO` percorre SNS → SQS → Kafka
- [x] 7.5 Aprovar uma autorização antes do vencimento e confirmar que o disparo posterior recebe 422, confirma a entrada e não altera a linha
- [x] 7.6 Derrubar o `contratocommand`, forçar um vencimento e confirmar que a entrada permanece pendente e é processada quando o serviço volta
- [x] 7.7 Reiniciar o Valkey com agendamentos e pendências em aberto e confirmar que sobrevivem

## 8. Documentação

- [x] 8.1 Criar `CLAUDE.md` e `AGENTS.md` (espelhos idênticos) de `apps/temporiza-autorizacao`
- [x] 8.2 Atualizar `CLAUDE.md` e `AGENTS.md` do `contratocommand`: rota de decisão, coluna `tipo_jornada`, motivo novo e attributes novos
- [x] 8.3 Atualizar `CLAUDE.md`/`AGENTS.md` de `contratoquery`, `autorizacaostatus-producer` e `eventos-consumer` quanto aos espelhos de schema
- [x] 8.4 Atualizar o `CLAUDE.md` raiz e o `README.md` raiz: quinta aplicação, porta 8084, diagrama de fluxo e nova fila
- [x] 8.5 Atualizar `infra/README.md` e os READMEs dos roots/módulos tocados
- [x] 8.6 Registrar a dívida de expurgo de estados terminais como proposta própria (`expurgo-estados-terminais`)

## 9. Checklist final

- [x] 9.1 `mvn test` passa nas cinco aplicações
- [x] 9.2 `mvn verify` passa onde há gate de cobertura
- [x] 9.3 Nenhum log novo carrega corpo de mensagem ou dado pessoal
- [x] 9.4 `openspec validate` sem erros para esta change
