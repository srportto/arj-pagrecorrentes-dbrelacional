## 1. Domínio: status inicial por produto

- [x] 1.1 Em `apps/arj-contratocommand/src/main/java/br/com/srportto/contratocommand/domain/entities/Autorizacao.java`, alterar `inicializaCriacao()` para determinar o status inicial a partir de `this.tipoProduto` em vez do hardcode `StatusAutorizacao.ATIVA`, usando um mapeamento declarativo (ex.: `EnumMap<TipoProduto, StatusAutorizacao>`) com `PIX_AUTO → RECEBIDA` e `DDA_AUTO → ATIVA`.
- [x] 1.2 Garantir que produto sem entrada explícita no mapeamento falhe de forma clara (não herde `ATIVA` silenciosamente via `else`/`default`).
- [x] 1.3 Atualizar o Javadoc de `inicializaCriacao()` (hoje afirma "marca o status como ativa (fonte da verdade: `StatusAutorizacao.ATIVA`)") para refletir que o status inicial depende do produto.

## 2. Testes

- [x] 2.1 `AutorizacaoTest`: adicionar/ajustar casos cobrindo `inicializaCriacao()` para `PIX_AUTO` (espera `status == RECEBIDA`) e `DDA_AUTO` (espera `status == ATIVA`).
- [x] 2.2 `AutorizacaoMapperTest`: adicionar/ajustar casos de `toDomain()` verificando o `status` persistido por produto (mantendo os casos existentes de `motivoStatus` por jornada inalterados).
- [x] 2.3 `TipoEventoAutorizacaoTest`: confirmar (sem alterar a bijeção) que `porStatus` continua mapeando `RECEBIDA → RECEPCAO` — cobre o novo caminho de criação de PIX_AUTO. (Já coberto pelo teste `derivaTipoParaCadaStatus` existente; nenhuma alteração necessária.)
- [x] 2.4 `AutorizacaoEventoPublisherTest`: adicionar caso simulando criação de PIX_AUTO (entidade com status `RECEBIDA`) e verificar que o SNS é publicado com message attribute `tipoEvento=RECEPCAO`; manter o caso existente de DDA_AUTO/`ATIVACAO`.
- [x] 2.5 Rodar `mvn test` em `apps/arj-contratocommand` e confirmar suíte completa verde.

## 3. Verificação manual do fluxo ponta a ponta (PIX_AUTO)

- [x] 3.1 Subir o ambiente local (`apps/docker-compose.yml` + Floci/mensageria conforme `infra/envs/local-messaging/`) e criar uma autorização `PIX_AUTO` via `POST /api/autorizacoes`. (Postgres, Floci e Kafka já estavam no ar; os 3 serviços Java foram subidos via `mvn spring-boot:run`.)
- [x] 3.2 Confirmar na resposta 201 que `status` retornado é `"RECEBIDA"`. (Response teve `"status":1`, motivoStatus `RECEPCAO_SPI_J1`.)
- [x] 3.3 Confirmar que o evento publicado no tópico `sns-estados-autorizacao` carrega `tipoEvento=RECEPCAO` e que ele é processado sem erro por `autorizacaostatus-producer` e `eventos-consumer` (sem exigir mudança de código nesses serviços). (Confirmado via SQS `receive-message` e logs: producer publicou no Kafka com `tipoEvento=RECEPCAO`, consumer consumiu com sucesso.)
- [x] 3.4 Repetir o teste para `DDA_AUTO` e confirmar que nada mudou (`status=ATIVA`, `tipoEvento=ATIVACAO`). (Confirmado: response `"status":4`; producer/consumer processaram com `tipoEvento=ATIVACAO`.)

## 4. Documentação

- [x] 4.1 Atualizar `apps/arj-contratocommand/CLAUDE.md`, seção "Mapeamento de status", removendo a afirmação de que "criação grava `ATIVA` (= 4)" para todo produto — descrever a dependência do `tipoProduto`.
- [x] 4.2 Replicar a mesma atualização em `apps/arj-contratocommand/AGENTS.md` (espelho obrigatório do `CLAUDE.md`).

## 5. Checklist final

- [x] 5.1 `mvn clean compile` e `mvn test` sem erros em `apps/arj-contratocommand`.
- [x] 5.2 Revisar diff confirmando que nenhum arquivo fora de `arj-contratocommand` foi alterado (mudança é local a este serviço).
- [x] 5.3 Rodar `/opsx:sync` (ou equivalente) para consolidar as specs `status-inicial-por-produto` (nova) e `publicacao-eventos-autorizacao` (delta) em `openspec/specs/` após a implementação ser validada.
