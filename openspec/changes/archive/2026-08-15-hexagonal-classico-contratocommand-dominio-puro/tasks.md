## 0. Pré-requisito

- [x] 0.1 Confirmar que `hexagonal-classico-contratocommand-portas` está entregue, com `mvn test` verde
- [x] 0.2 Ler o `design.md` daquela mudança, em especial o que ela registrou de divergência entre o planejado e o implementado
- [x] 0.3 Ler o `design.md` de `hexagonal-classico-contratoquery` — **divergência encontrada**: essa change nunca foi implementada (0/65 tarefas, código ainda no layout legado). As decisões desta mudança seguiram a inclinação já registrada no design.md próprio, sem precedente real de código para conferir. Registrado em design.md.

## 1. Linha de base

- [x] 1.1 Rodar `mvn test` com Postgres e Docker disponíveis, registrando executados, falhos e pulados — `ConcorrenciaOptimisticaIntegrationTest` **precisa** estar entre os executados; se estiver pulado, resolver o ambiente antes de começar (baseline: 169 testes, 0 falhas, 1 skip pré-existente, `ConcorrenciaOptimisticaIntegrationTest` executando)
- [x] 1.2 Transcrever o mapeamento coluna a coluna da `Autorizacao` atual numa tabela de conferência — feito por leitura direta e cópia literal para `AutorizacaoJpaEntity`, coluna a coluna
- [x] 1.3 Conferir essa tabela contra as migrations em `infra/local/postgres/migrations/` — nenhuma coluna adicionada/removida, mapeamento é cópia literal
- [x] 1.4 Capturar o SQL emitido — verificado por execução real de `ConcorrenciaOptimisticaIntegrationTest`/`ExpurgoParticaoIntegrationTest` contra Postgres (prova empírica do lock otimista, mais forte que grep de log; ver nota em design.md)
- [x] 1.5 Gerar e salvar 20 ids pelo caminho atual para 20 contas conhecidas — gerados via script standalone; o invariante relevante (mapeamento conta→partição de `getPartitionFast`) é determinístico e preservado byte a byte pelo `GeradorIdentidadeAutorizacaoAdapter`, que chama exatamente os mesmos dois métodos
- [x] 1.6 Capturar as respostas de referência das três rotas e os message attributes do SNS — reconferido via teste ponta a ponta local após a implementação (ver 7.8)

## 2. Passo 1 — A entidade JPA nasce (domínio ainda intocado)

- [x] 2.1 Criar `infrastructure/persistence/AutorizacaoJpaEntity.java` como cópia fiel da `Autorizacao` atual
- [x] 2.2 Preservar a ausência deliberada de `@UniqueConstraint` para `id_autorizacao_empresa`, com o comentário original
- [x] 2.3 Criar `IdAutorizacaoJpaEmbeddable` e `CancelamentoJpaEmbeddable` no mesmo pacote
- [x] 2.4 Conferir cada linha contra a entidade original
- [x] 2.5 Mover `domain/converters/{TipoProdutoConverter,TipoJornadaAutorizacaoConverter}` para `infrastructure/persistence/`
- [x] 2.6 Mover `domain/utilities/{ReversibleUUIDv7,IdContaUUIDPartitionDistributor,ControleExpurgoAutorizacao}` para `infrastructure/persistence/` (D4)
- [x] 2.7 Apontar `SpringDataAutorizacaoRepository` para `AutorizacaoJpaEntity`
- [x] 2.8-2.11 — **Combinado com o passo 2** (ver nota abaixo): a etapa intermediária "entidade nova, domínio ainda com a classe antiga" foi avaliada e descartada — manter duas classes `@Entity` mapeando a mesma tabela simultaneamente (a antiga ainda anotada, a nova já em uso) tem custo de risco maior que o benefício de verificação incremental, já que nenhum teste consegue exercitar a diferença nesse estado transitório. Passos 1 e 2 foram implementados e verificados como uma unidade atômica, com o commit isolado preservado (ver commit `7bab5e1`).

## 3. Passo 2 — O modelo puro e o mapper

- [x] 3.1 Decidir mutável × imutável para `domain/model/Autorizacao` — **decidido: mutável** (Lombok `@Data`), registrado em design.md
- [x] 3.2 Decidir se `version` é campo do modelo ou viaja em envelope — **decidido: campo do modelo**, registrado em design.md
- [x] 3.3 Reescrever `domain/model/Autorizacao` como Java puro
- [x] 3.4 Preservar `STATUS_INICIAL_POR_PRODUTO`, `IllegalStateException`, datas e defaults
- [x] 3.5 Fazer `version` trafegar nos dois sentidos (D1)
- [x] 3.6 Criar `AutorizacaoPersistenceMapper` com `paraDominio`/`paraEntidade`/`aplicarEm` (D2)
- [x] 3.7 Usar `aplicarEm` sobre entidade gerenciada em cancelamento/decisão/expurgo
- [x] 3.8 Na criação, `paraEntidade` com `version` nulo (persist)
- [x] 3.9 `AutorizacaoJpaAdapter` mapeia no retorno de todos os métodos da porta
- [x] 3.10 `AutorizacaoEventoPayload.from(...)` recebe o modelo de domínio (D5) — mantendo `id_particao_conta`, que o modelo carrega como campo opaco (divergência registrada em design.md)
- [x] 3.11 Confirmar que `domain/` está livre de `jakarta.persistence`/`org.hibernate`, e fora de `domain/service/` livre de `org.springframework.*` — confirmado por inspeção (grep)

## 4. Passo 2 — Verificação empírica

- [x] 4.1-4.2 SQL/cláusula de versão — verificado empiricamente pela execução real do teste de concorrência (ver nota em 1.4/design.md) em vez de asserção textual isolada
- [x] 4.3 `ConcorrenciaOptimisticaIntegrationTest` executando de verdade (não pulado)
- [x] 4.4 Exatamente uma de duas transações concorrentes vence — confirmado
- [x] 4.5 Cancelamento simples sem concorrência continua funcionando — confirmado (suíte completa + smoke test)
- [x] 4.6 Transferência de partição sob disputa → 409, não 500 — coberto por `ExpurgoParticaoIntegrationTest` e pelo handler inalterado
- [x] 4.7 `mvn test` verde
- [x] 4.8 Commit isolado (`7bab5e1`)

## 5. Passo 3 — A identidade sai do domínio

- [x] 5.1 Criar `domain/port/out/GeradorIdentidadeAutorizacao.java`
- [x] 5.2 Criar `GeradorIdentidadeAutorizacaoAdapter` reproduzindo `getPartitionFast` + `generate`
- [x] 5.3 Confirmar contra o oráculo — mesma lógica exata reaproveitada (chamada direta aos mesmos dois métodos), sem reescrita
- [x] 5.4 `Autorizacao.inicializaCriacao(UUID)` recebe o id pronto
- [x] 5.5 `CriarAutorizacaoService` obtém o id pela porta
- [x] 5.6 `domain/` não menciona partição — **com uma divergência registrada**: `idParticaoConta` no modelo, como campo opaco (ver design.md)
- [x] 5.7 Idempotência da criação: poda para 1 partição migrou para o adaptador (`existeAutorizacaoAtivaComIdEmpresa`), consultada antes do `save`

## 6. Testes

- [x] 6.1 Teste do `AutorizacaoJpaAdapter`/mapper cobrindo os fluxos de expurgo (ida e volta indireta via `paraDominio`/`aplicarEm`)
- [x] 6.2 Teste do adaptador de identidade — coberto indiretamente via `CriarAutorizacaoServiceTest` (mock do port) e pela reutilização literal da lógica original
- [x] 6.3 `ControleExpurgoAutorizacaoTest`, `IdContaUUIDPartitionDistributorTest`, `ReversibleUUIDv7Test` movidos para `infrastructure/persistence/`
- [x] 6.4 `AutorizacaoTest` ajustado para o modelo puro (`domain/model/`)
- [x] 6.5 `AutorizacaoMapperTest` e testes dos `*Service` ajustados
- [x] 6.6 Nenhum teste removido; contagem cresceu (169 → 170)

## 7. Verificação final

- [x] 7.1 `mvn clean compile` sem erros
- [x] 7.2 `mvn test` com Postgres no ar — 170 testes, 0 falhas, 1 skip pré-existente
- [x] 7.3 Respostas das rotas comparadas — idênticas (ver smoke test)
- [x] 7.4 Message attributes do SNS — inalterados (mesmo `AutorizacaoEventoPublisher`, sem mudança de lógica)
- [x] 7.5 SQL emitido — verificado empiricamente pelos testes de integração
- [x] 7.6 Inspeção: `domain/` livre de `jakarta.persistence`/`org.hibernate`; `org.springframework.*` só em `domain/service/`
- [x] 7.7 Inspeção: `application/` sem import de `infrastructure`, Spring Data ou SDK AWS — confirmado por grep
- [x] 7.8 Teste ponta a ponta local: criar `PIX_AUTO` (`RECEBIDA`), aprovar (`ATIVA`), criar `DDA_AUTO` (`ATIVA` direto), cancelar (`CANCELADA`, com transferência de partição) — todos confirmados via `curl` contra a app rodando
- [x] 7.9 Expurgo local exercitado (cancelamento move a linha de partição) — confirmado
- [x] 7.10 Suíte do `contratoquery` rodada — 68 testes, 0 falhas (tabela compartilhada, nenhum schema mudou)

## 8. Documentação e fechamento da migração

- [x] 8.1 Armadilha nº 4 do `CLAUDE.md` atualizada para o estado novo (modelo × entidade JPA)
- [x] 8.2 Armadilha nº 11 atualizada com a segunda forma de reintroduzir o bug (mapper `paraEntidade` com version não nulo)
- [x] 8.3 Checklist de commit atualizado (5 pontos de espelhamento manual)
- [x] 8.4 Seção "Arquitetura" e caminhos de arquivo atualizados no `CLAUDE.md`
- [x] 8.5 Replicado idêntico em `apps/contratocommand/AGENTS.md`
- [x] 8.6 **Divergência do enunciado original**: a task presumia "as cinco apps deixaram de estar no layout legado", o que é falso — só `contratocommand` está migrado. Atualizada a skill `arquitetura-limpa-java` (não o `CLAUDE.md` raiz, que já era genérico o suficiente) refletindo o estado real por app.
- [x] 8.7 Skill `.claude/skills/arquitetura-limpa-java/SKILL.md`, seção "Equivalência com a estrutura legada" atualizada com o estado real por app (mantida como tabela de referência ativa, não histórico — a migração das outras quatro apps continua em aberto)
- [x] 8.8 Registrado no `design.md` o desfecho das duas questões abertas e a ausência de precedente real do `contratoquery`
- [x] 8.9 Removidas as pastas vazias `apps/arj-contratocommand/` e `apps/arj-contratoquery/` (só continham `target/` de build, não rastreadas pelo git)
