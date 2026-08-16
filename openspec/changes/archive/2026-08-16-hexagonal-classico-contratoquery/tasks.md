## 1. Linha de base

- [x] 1.1 Rodar `mvn test` em `apps/contratoquery` e registrar a contagem exata de executados e **pulados** (`PostgresLocalDisponivelCondition` pula testes de integração sem Postgres) — 68 total, 7 pulados, 61 executados.
- [ ] 1.2 Subir o Postgres local e rodar de novo, registrando a contagem com os testes de integração realmente executando — é esta a linha de base que vale. **Não realizado**: Docker indisponível neste ambiente (`docker ps` falhou ao conectar ao daemon) e `DB_PASSWORD` não estava exportada. Sem Postgres local, esta etapa não pôde ser executada — não afirmo sucesso aqui.
- [ ] 1.3 Capturar as respostas de referência: `GET /api/autorizacoes/{id}` para uma autorização ativa e outra expurgada, e `GET /api/autorizacoes` paginado, salvando o JSON exato para comparação byte a byte no final. **Bloqueado pela mesma ausência de Postgres/API rodando.**
- [ ] 1.4 Contar as **queries disparadas** em cada um dos três níveis da cascata (achou no nível 1, no 2, no 3) — via log de SQL do Hibernate. **Bloqueado pela mesma ausência de Postgres.** Análise estática: o algoritmo dos três níveis foi movido linha a linha (mesma ordem, mesmas queries JPQL, mesmas condições de parada) de `ConsultarAutorizacaoService` para `AutorizacaoJpaAdapter` — não há motivo estrutural para o número de queries mudar, mas isso não substitui a medição real.
- [x] 1.5 Transcrever o mapeamento coluna a coluna da `domain/entities/Autorizacao` atual (nome de coluna, tipo, nullable, `@Convert`, precisão/escala) — feito por leitura direta do arquivo original antes da migração; toda anotação foi preservada 1:1 em `AutorizacaoJpaEntity` (etapa B), incluindo o espaço à direita em `"indicador_tipo_mensageria "` (preservado deliberadamente, não é typo desta migração).
- [x] 1.6 Reler as convenções herdadas das três mudanças anteriores (nomenclatura, categorias de adaptador, exceção de contrato de porta) — lidas as skills `arquitetura-limpa-java` e a estrutura já migrada de `contratocommand` (portas, mapper, `SpringDataAutorizacaoRepository` package-private) como precedente direto.

## 2. Etapa A — Domínio: portas e exceções

- [x] 2.1 Criar `domain/port/in/ConsultarAutorizacaoUseCase.java` (interface)
- [x] 2.2 Criar `domain/port/in/ListarAutorizacoesUseCase.java` (interface), recebendo `pagina`/`tamanho`/`ordenarPor` como tipos simples, nunca `Pageable` (D7)
- [x] 2.3 Criar `domain/port/out/AutorizacaoRepository.java` com `Optional<Autorizacao> buscarPorId(UUID)` e o método de listagem que devolve conteúdo + total (D3, D7) — **não** estende `JpaRepository`
- [x] 2.4 Mover `shared/exceptions/{BusinessException,ApplicationException,ResourceNotFoundException}` para `domain/exception/`
- [x] 2.5 Mover `domain/entities/*` para `domain/model/` (ainda anotadas com JPA nesta etapa)
- [x] 2.6 Deixar `domain/enums/*` onde estão

## 3. Etapa A — Application

- [x] 3.1 Mover `application/autorizacao/ConsultarAutorizacaoService` para `application/usecase/`, implementando a porta
- [x] 3.2 **Remover a cascata de partições do caso de uso** (D3): tirar `PARTICAO_MIN`, `PARTICAO_MAX`, `PRIMEIRA_PARTICAO_EXPURGO`, a flag `busca-em-particoes-inesperadas` e a lógica dos três níveis
- [x] 3.3 Deixar no caso de uso apenas: chamar `buscarPorId` e traduzir `Optional.empty()` em `ResourceNotFoundException`
- [x] 3.4 Mover `ListarAutorizacoesService` para `application/usecase/`, implementando a porta
- [x] 3.5 **Remover o import de `entrypoint.contratosrest.*`** dos dois casos de uso — passam a retornar `domain/model/Autorizacao` (D6)
- [x] 3.6 Confirmar que nenhum dos dois importa `org.springframework.data.*`

## 4. Etapa A — Infrastructure

- [x] 4.1 Criar `infrastructure/persistence/SpringDataAutorizacaoRepository.java` a partir do `AutorizacaoRepository` atual, **sem o modificador `public`** (D5), preservando as queries JPQL e nativas exatamente como estão
- [x] 4.2 Criar `infrastructure/persistence/AutorizacaoJpaAdapter.java` implementando a porta de saída
- [x] 4.3 **Mover a cascata de três níveis para o adapter** (D3), com as constantes e a flag `contratoquery.consulta.busca-em-particoes-inesperadas` — mesma ordem, mesmo algoritmo, mesmo comportamento quando a flag está desligada
- [x] 4.4 Mover `domain/utilities/ReversibleUUIDv7` para `infrastructure/persistence/` (D4)
- [x] 4.5 Mover `entrypoint/AutorizacaoController` para `infrastructure/web/` e `entrypoint/contratosrest/*` para `infrastructure/web/contratosrest/`
- [x] 4.6 Fazer o controller montar os DTOs a partir do modelo de domínio (D6, D7) — inclusive o envelope `PaginacaoResponseDto`
- [x] 4.7 Trocar os tipos injetados no controller para as **interfaces** das portas de entrada
- [x] 4.8 Mover `shared/interceptors/api/{ApiExceptionHandler,LayoutErrosApiResponse,LayoutErrosApiValidationsResponse,BodyOcorrenciasErrosValidations}` para `infrastructure/web/`
- [x] 4.9 Remover os pacotes `entrypoint/` e `shared/`, agora vazios
- [x] 4.10 Rodar a skill `remover-imports-nao-usados` — sem `google-java-format`/Spotless configurados no projeto; análise manual dos arquivos tocados (compilação limpa sem símbolo não resolvido, conferência de imports por arquivo nos pontos de maior risco) não encontrou import órfão.

## 5. Etapa A — Verificação intermediária (build verde obrigatório antes da etapa B)

- [x] 5.1 `mvn clean compile` sem erros
- [x] 5.2 `mvn test` — 69 total, 7 pulados (mesmos de sempre), 62 executados. **Sem Postgres no ar** (indisponível neste ambiente) — não é a linha de base "que vale" de 1.2, é a melhor evidência possível aqui.
- [ ] 5.3 Comparar as respostas com as capturadas em 1.3 — **bloqueado**, 1.3 não foi capturada (sem Postgres/API no ar).
- [ ] 5.4 Recontar as queries dos três níveis da cascata e comparar com 1.4 — **bloqueado**, mesma causa.
- [x] 5.5 Inspeção: nenhuma classe de `application/` importa `org.springframework.data.*` nem de `infrastructure`
- [x] 5.6 Inspeção: `SpringDataAutorizacaoRepository` não é `public` e não é referenciada fora de `infrastructure/persistence/`
- [ ] 5.7 Commitar a etapa A separadamente — **não realizado nesta sessão**: commits só são criados quando o usuário pede explicitamente (regra do harness); as etapas A e B ficaram como working tree não commitado ao final da execução, prontas para o usuário revisar e commitar.

## 6. Etapa B — Domínio puro

- [x] 6.1 Criar `infrastructure/persistence/AutorizacaoJpaEntity.java` com **todas** as anotações da entidade atual: `@Entity`, `@Table`, `@EmbeddedId`, cada `@Column` com nome/nullable/precisão/escala, `@Convert`, `@JdbcTypeCode(SqlTypes.JSON)` em `metadados`, `@Version` e `@Embedded` de cancelamento — **nota**: a entidade original **não tinha** `@Version` (a coluna já era mapeada só como `@Column(insertable=false, updatable=false)`, sem lock otimista, coerente com D2 — app somente leitura); preservei esse estado exatamente, sem introduzir `@Version` que não existia.
- [x] 6.2 Conferir cada linha contra a tabela transcrita em 1.5 **e** contra as migrations em `infra/local/postgres/migrations/` — conferido contra `v1.0.0`, `v1.0.2`, `v1.0.5`; `valor_limite` continua nullable no banco apesar de `nullable=false` na entidade (dívida pré-existente, documentada no `CLAUDE.md` do `contratocommand`, fora de escopo corrigir aqui).
- [x] 6.3 Criar os embeddables JPA de `IdAutorizacao` e `Cancelamento` no mesmo pacote — `IdAutorizacaoJpaEmbeddable`, `CancelamentoJpaEmbeddable`.
- [x] 6.4 Reescrever `domain/model/Autorizacao` como Java **puro**: sem `jakarta.persistence`, sem `@Data`, sem setter, imutável (D1). Resolvida a questão aberta record × classe (classe imutável `@Value @Builder`) e registrada no `design.md`.
- [x] 6.5 Decidido: o modelo carrega só `UUID` (não `IdAutorizacao`) — registrado no `design.md`.
- [x] 6.6 **Não** expor `@Version` no modelo de domínio (D2) — confirmado, `Autorizacao` (domínio) não tem campo `version`.
- [x] 6.7 Criar `AutorizacaoPersistenceMapper` com a conversão `AutorizacaoJpaEntity → Autorizacao` (sentido único).
- [x] 6.8 Mover `domain/converters/{TipoProdutoConverter,TipoJornadaAutorizacaoConverter}` para `infrastructure/persistence/`
- [x] 6.9 Fazer `AutorizacaoJpaAdapter` mapear no retorno de todos os métodos da porta (`buscarPorId`, `listarPorConta`)
- [x] 6.10 Confirmar que `domain/` inteiro está livre de `jakarta.persistence`, `org.hibernate.*`, `org.springframework.*` e Lombok de mutação — confirmado por inspeção (nenhum desses imports aparece em `domain/`).

## 7. Testes

- [x] 7.1 Mover os arquivos de teste para os pacotes espelhados
- [x] 7.2 Reescrever `ConsultarAutorizacaoServiceTest`: a cascata migrou para `AutorizacaoJpaAdapterTest` (7 cenários, mesmos de antes); o teste do caso de uso passa a cobrir só "achou" × "não achou → `ResourceNotFoundException`" (2 testes)
- [x] 7.3 Ajustar `ConsultaCascataIntegrationTest` para exercitar a cascata através da pilha real (`ConsultarAutorizacaoUseCase` → `ConsultarAutorizacaoService` → `AutorizacaoJpaAdapter` → Postgres), mantendo os mesmos três cenários e a flag desligada como quarto (coberto no `AutorizacaoJpaAdapterTest` unitário; o teste de integração cobre N1/N2/N3/duplicidade/id-inexistente). Movido para `infrastructure/persistence/` (precisa enxergar `SpringDataAutorizacaoRepository`, package-private).
- [x] 7.4 Ajustar `AutorizacaoControllerTest`: o controller agora monta o DTO a partir do modelo, injeta as portas de entrada (interfaces).
- [x] 7.5 Ajustar `AutorizacaoDetalheResponseDtoTest` e `AutorizacaoResumidaResponseDtoTest` para partir do modelo de domínio (builder, acesso plano a `idAutorizacao`).
- [x] 7.6 Mover `TipoProdutoConverterTest` e `TipoJornadaAutorizacaoConverterTest` junto com os converters (para `infrastructure/persistence/`).
- [x] 7.7 Adicionar teste do `AutorizacaoPersistenceMapper` cobrindo **todos** os campos, inclusive `metadados` (jsonb), `cancelamento` embutido e os dois enums convertidos, e o caso `cancelamento == null`.
- [x] 7.8 Confirmar que nenhuma cobertura desapareceu no caminho — linha de base 68 testes/7 pulados/61 executados → final 71 testes/7 pulados/64 executados (ganho líquido de 3 testes executados: 2 no `AutorizacaoPersistenceMapperTest` novo, 1 em `ListarAutorizacoesServiceTest` cobrindo o repasse do campo/direção de ordenação para a porta).

## 8. Verificação final

- [x] 8.1 `mvn clean compile` sem erros nem warnings novos
- [x] 8.2 `mvn test` — **sem Postgres no ar** (indisponível neste ambiente, ver 1.2). Resultado: 71 total, 7 pulados, 64 executados (mesmos 7 pulados de sempre — os 2 testes de integração que exigem Postgres).
- [ ] 8.3 Comparar de novo as respostas com as de 1.3 — **bloqueado**, 1.3 não capturada.
- [ ] 8.4 Recontar as queries dos três níveis e comparar com 1.4 — **bloqueado**, mesma causa.
- [x] 8.5 Conferido explicitamente: a resposta mantém `status` como **String** (`StatusAutorizacao.obterStatusEnumPorIdStatus(...).name()` nos dois DTOs) e os nomes curtos `valor`, `dataCriacao`, `dataAtualizacao` (inalterados nos DTOs) — nenhuma mudança de contrato REST.
- [x] 8.6 Inspeção final: `domain/` sem nenhum import de framework; `application/` sem import de `infrastructure`.
- [ ] 8.7 Consultar uma autorização em estado terminal (movida para a faixa 900–999) e confirmar 200, não 404 — **bloqueado**, exige Postgres local com partições físicas reais. Coberto indiretamente por `ConsultaCascataIntegrationTest.nivel2_AutorizacaoExpurgada` (roda quando há Postgres; pulado nesta execução).

## 9. Documentação

- [x] 9.1 Atualizar a seção de arquitetura de `apps/contratoquery/CLAUDE.md` com a árvore nova
- [x] 9.2 Replicar **idêntico** em `apps/contratoquery/AGENTS.md`
- [x] 9.3 Documentar nos dois a armadilha nova: coluna nova exige edição em `AutorizacaoJpaEntity` **e** em `domain/model/Autorizacao` **e** no mapper
- [x] 9.4 Registrar no `design.md` as respostas das duas questões abertas (record × classe; `IdAutorizacao` × `UUID`)
- [x] 9.5 Registrar no `design.md` se o padrão de mapper se mostrou adequado, e o que o `contratocommand` deve fazer diferente por causa da escrita e do `@Version`
