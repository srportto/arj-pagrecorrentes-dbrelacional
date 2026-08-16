## 1. Linha de base

- [x] 1.1 Rodar `mvn test` em `apps/contratocommand` e registrar a contagem exata de executados, falhos e **pulados** (`DockerDisponivelCondition` pula `ConcorrenciaOptimisticaIntegrationTest` sem Docker)
- [x] 1.2 Subir o Postgres local e rodar de novo; registrar a contagem com os testes de integração realmente executando
- [x] 1.3 Capturar as respostas de referência das três rotas, salvando o JSON exato: `POST` bem-sucedido, `POST` duplicado (409), `POST` com `@Valid` falhando (422 + `LayoutErrosApiValidationsResponse`), `PATCH /cancelar` (200), `PATCH /cancelar` já cancelada (422), `PATCH /decisao` com `APROVAR`, `REJEITAR` e `EXPIRAR`, e `/decisao` sobre status já resolvido (422)
- [x] 1.4 **Registrar a ordem efetiva das rules de cada validador logando a `List<Rule>` injetada em runtime** — não por leitura das anotações `@Order`, que já divergiram do comportamento real nesta app
- [x] 1.5 Capturar os message attributes publicados no SNS (`tipoEvento`, `tipoProduto`, `tipoJornada`) para criação de `PIX_AUTO`, criação de `DDA_AUTO`, cancelamento e as três ações de decisão
- [x] 1.6 Reler o `design.md` de `hexagonal-classico-contratoquery`, em especial as respostas das questões abertas (record × classe, `IdAutorizacao` × `UUID`) e o que ele registrou sobre o que o command deve fazer diferente
- [x] 1.7 Decidir a questão aberta `*Command` × `*Context` e registrar no `design.md` antes de começar a etapa 5

## 2. Domínio — exceções, enums e evento

- [x] 2.1 Mover `shared/exceptions/{BusinessException,ApplicationException,RecursoJaExisteException}` para `domain/exception/`
- [x] 2.2 Deixar `domain/enums/*` onde estão
- [x] 2.3 Mover `application/eventos/AutorizacaoPersistidaEvent` para `domain/event/` (D3)
- [x] 2.4 Confirmar que nenhuma dessas classes importa framework

## 3. Domínio — porta de saída e adaptador de persistência

- [x] 3.1 Criar `domain/port/out/AutorizacaoRepository.java` como interface própria, expressa em `domain/model/Autorizacao` — **não** estende `JpaRepository`
- [x] 3.2 Expor na porta `transferirParaExpurgo(...)` em vez de `moverParaParticao(id, particaoAtual, novaParticao)` (D4), preservando o contrato de retorno (quantidade de linhas afetadas, chamador trata valor ≠ 1)
- [x] 3.3 Criar `infrastructure/persistence/SpringDataAutorizacaoRepository.java` a partir do `application/AutorizacaoRepository` atual, **sem `public`** (D6), preservando literalmente as três JPQL, o `existsByIdAutorizacao_IdParticaoContaAndIdAutorizacaoEmpresa` e o `@Modifying` nativo
- [x] 3.4 Criar `infrastructure/persistence/AutorizacaoJpaAdapter.java` implementando a porta
- [x] 3.5 Mover para o adaptador o cálculo da partição de destino do expurgo (hoje em `ExpurgoAutorizacaoService` via `ControleExpurgoAutorizacao`) e a checagem de linhas afetadas ≠ 1 (D4)
- [x] 3.6 Confirmar que `ControleExpurgoAutorizacao` continua em `domain/utilities/` nesta mudança (migra na seguinte) e que o adaptador o chama de lá

## 4. Domínio — regras de negócio

- [x] 4.1 Mover `shared/validationsetup/{Rule,Validator}` para `domain/service/` (D2)
- [x] 4.2 Mover `ContratacaoRule`, `CancelamentoRule` e `DecisaoRule` (marcadores) para `domain/service/{contratacao,cancelamento,decisao}/`
- [x] 4.3 Mover `ContratacaoValidator`, `CancelamentoValidator` e `DecisaoValidator` para os mesmos pacotes
- [x] 4.4 Mover as dez rules concretas para `domain/service/<feature>/rules/`, **preservando cada `@Order` exatamente** — incluindo os implícitos
- [x] 4.5 Confirmar que `domain/service/` só importa `org.springframework.stereotype.Component` e `org.springframework.core.annotation.Order` — nenhuma outra classe do Spring (**divergência registrada**: os três Validators usam `@Service`, herdado sem alteração do código pré-migração; ver nota em `design.md`)
- [x] 4.6 Confirmar que `domain/model/`, `domain/port/`, `domain/enums/`, `domain/exception/` e `domain/event/` seguem 100% livres de framework (a exceção de D2 é só para `domain/service/`)
- [x] 4.7 **Comparar a ordem efetiva das rules com a registrada em 1.4**, logando de novo a lista injetada — divergência bloqueia

## 5. Domínio — portas de entrada e comandos

- [x] 5.1 Criar `domain/port/in/{CriarAutorizacaoUseCase,CancelarAutorizacaoUseCase,DecidirAutorizacaoUseCase}.java` como interfaces, devolvendo `domain/model/Autorizacao`
- [x] 5.2 Mover os três records de contexto para `domain/port/in/`, com o nome decidido em 1.7 (D1)
- [x] 5.3 **Substituir o campo `dados` (hoje o `CriarAutorizacaoRequest`, DTO de web com `@Valid`) pelos 15 campos explícitos no comando de criação** (D1)
- [x] 5.4 Fazer o mesmo para os comandos de cancelamento e decisão, que também carregam o record de request
- [x] 5.5 Confirmar que nenhum comando importa `jakarta.validation.*`, Jackson ou tipo de `infrastructure/web` (metadados trafega como `String` JSON pré-serializado no comando; `MetadadoRule`, em `domain/service/`, reparseia)
- [x] 5.6 Conferir **campo a campo** que nenhum dos 15 campos da criação se perdeu na tradução

## 6. Application

- [x] 6.1 Mover os três use cases para `application/usecase/`, renomeando para `*Service` e implementando as portas
- [x] 6.2 Fazer os três **retornarem `domain/model/Autorizacao`** em vez de `AutorizacaoCompletaResponseDto`
- [x] 6.3 Remover de todos os três o import de `entrypoint.contratosrest.*`
- [x] 6.4 Mover `application/AutorizacaoMapper` para `application/usecase/`, mapeando agora do **comando** para o modelo (D5); manter o `@AfterMapping` que chama `inicializaCriacao()` como está
- [x] 6.5 Mover `application/ExpurgoAutorizacaoService` para `application/usecase/`, agora chamando `transferirParaExpurgo` da porta em vez de calcular partição
- [x] 6.6 Manter `@Transactional` no método público de entrada de cada use case — nunca em método privado
- [x] 6.7 Manter `ApplicationEventPublisher` injetado, publicando `AutorizacaoPersistidaEvent` (D3)
- [x] 6.8 Confirmar que nenhuma classe de `application/` importa `org.springframework.data.*`, `software.amazon.awssdk.*` nem de `infrastructure`
- [x] 6.9 Remover os pacotes `application/{contratacao,cancelamento,decisao,eventos}/`, agora vazios

## 7. Infrastructure — web

- [x] 7.1 Mover `entrypoint/AutorizacaoController` para `infrastructure/web/` e `entrypoint/contratosrest/*` para `infrastructure/web/contratosrest/`
- [x] 7.2 Fazer o controller traduzir cada request nos campos do comando (D1) e montar `AutorizacaoCompletaResponseDto` a partir do modelo devolvido
- [x] 7.3 Trocar os tipos injetados no controller para as **interfaces** das portas de entrada
- [x] 7.4 Manter `@Valid` no parâmetro do controller — é o que produz `MethodArgumentNotValidException` → 422
- [x] 7.5 Mover `shared/interceptors/api/{ApiExceptionHandler,LayoutErrosApiResponse,LayoutErrosApiValidationsResponse,BodyOcorrenciasErrosValidations}` para `infrastructure/web/`
- [x] 7.6 Confirmar que o `ApiExceptionHandler` continua sendo o **único** lugar que monta `ResponseEntity` de erro, e que todos os mapeamentos seguem: 422 `@Valid`, 422 `BusinessException`, 409 `RecursoJaExisteException`, 409 `ObjectOptimisticLockingFailureException`, 409 `ConcurrencyFailureException`, 409 `StaleStateException`/`DataIntegrityViolationException`, 500 `ApplicationException`, 500 catch-all
- [x] 7.7 Confirmar que nenhuma resposta expõe nome de classe, stack trace ou nome de tabela/coluna/constraint

## 8. Infrastructure — messaging e config

- [x] 8.1 Mover `application/eventos/AutorizacaoEventoPublisher` para `infrastructure/messaging/` (D3)
- [x] 8.2 **Confirmar que ele continua anotado `@TransactionalEventListener(phase = AFTER_COMMIT)`** e não virou `@EventListener` comum
- [x] 8.3 Mover `application/eventos/AutorizacaoEventoPayload` para `infrastructure/messaging/`
- [x] 8.4 Mover `shared/config/{AwsProperties,SnsClientConfig}` para `infrastructure/config/`
- [x] 8.5 Confirmar que o `publish()` continua capturando qualquer exceção e só logando — falha de SNS não pode afetar a resposta HTTP já commitada
- [x] 8.6 Remover os pacotes `entrypoint/` e `shared/`, agora vazios
- [x] 8.7 Rodar a skill `remover-imports-nao-usados`

## 9. Testes

- [x] 9.1 Mover os 37 arquivos de teste para os pacotes espelhados, renomeando os `*UseCaseTest` para `*ServiceTest`
- [x] 9.2 Mover os testes de rules e validators para `domain/service/`, sem alterar conteúdo
- [x] 9.3 Ajustar `AutorizacaoControllerTest`: o controller monta o DTO e traduz o request em comando
- [x] 9.4 Ajustar `AutorizacaoMapperTest` para partir do comando
- [x] 9.5 Confirmar que `ConcorrenciaOptimisticaIntegrationTest` continua exercitando `@Version` e que `DockerDisponivelCondition` continua reportando skip **visível** — nunca "Tests run: 0"
- [x] 9.6 Adicionar teste que provoca rollback (`BusinessException` de validação) e afirma que **nenhum** evento é publicado no SNS (proteção de D3)
- [x] 9.7 Confirmar que nenhum teste foi removido e que a contagem não caiu

## 10. Verificação

- [x] 10.1 `mvn clean compile` sem erros nem warnings novos
- [x] 10.2 `mvn test` com Postgres no ar, com a contagem de 1.2 mais o teste novo de 9.6
- [x] 10.3 Comparar as respostas das oito chamadas de 1.3 — devem ser **byte a byte idênticas**
- [x] 10.4 Comparar a ordem efetiva das rules com 1.4
- [x] 10.5 Comparar os message attributes publicados com 1.5, nos sete cenários
- [x] 10.6 Inspeção: nenhuma classe de `application/` importa de `infrastructure`, `org.springframework.data.*` ou SDK AWS
- [x] 10.7 Inspeção: `SpringDataAutorizacaoRepository` não é `public` e não é referenciada fora de `infrastructure/persistence/`
- [x] 10.8 Inspeção: `domain/` fora de `service/` está 100% livre de framework; `domain/service/` só tem `@Component` e `@Order` (mais `@Service` nos três Validators, ver nota da 4.5)
- [x] 10.9 Teste ponta a ponta local: criar `PIX_AUTO`, confirmar status `RECEBIDA` e evento `RECEPCAO`; aprovar via `/decisao` e confirmar `ATIVA` + `ATIVACAO`; criar `DDA_AUTO` e confirmar `ATIVA` + `ATIVACAO` direto
- [x] 10.10 Confirmar que a app continua sendo acionável pelo `temporiza-autorizacao` (`PATCH /decisao` com `acao: EXPIRAR`), sem alteração naquela app
- [x] 10.11 Confirmar que `Autorizacao` **ainda é** `@Entity` em `domain/model/` — a mudança seguinte é que fecha isso; terminar esta etapa com ela já partida é sinal de que o escopo vazou

## 11. Documentação

- [x] 11.1 Atualizar a seção "Arquitetura" de `apps/contratocommand/CLAUDE.md` — hoje descreve quatro camadas
- [x] 11.2 Atualizar os caminhos de arquivo citados em "Comece por aqui", nos diagramas de fluxo e nas armadilhas
- [x] 11.3 Atualizar a armadilha nº 4 ("`Autorizacao` está em `domain/entities/`")
- [x] 11.4 Replicar **idêntico** em `apps/contratocommand/AGENTS.md`
- [x] 11.5 Registrar no `design.md` o desfecho de 1.7 (`*Command` × `*Context`) e qualquer divergência entre D1–D6 e o que foi implementado — a mudança seguinte herda
