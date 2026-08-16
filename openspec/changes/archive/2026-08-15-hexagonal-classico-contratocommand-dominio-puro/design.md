## Context

Esta mudança fecha a migração da frota. Ela parte a classe mais carregada do monorepo: 25 colunas,
chave composta, coluna jsonb, dois `AttributeConverter`, `@Version`, e a geração de identidade que
embute partição.

O que torna isso perigoso não é o tamanho — é que **o JPA muda de mecanismo quando a entidade deixa de
ser o objeto que a aplicação manipula.**

```
HOJE — dirty checking sobre entidade gerenciada

  find() ──▶ Autorizacao (managed) ──▶ setStatus(CANCELADA) ──▶ commit
                                                                  │
                              o Hibernate compara com o snapshot ─┘
                              e emite  UPDATE ... WHERE id=? AND version=?


DEPOIS — o modelo de domínio não é a entidade

  find() ──▶ AutorizacaoJpaEntity ──▶ mapper ──▶ Autorizacao (domínio puro)
                                                       │
                                              cancelar() muta o modelo
                                                       │
             AutorizacaoJpaEntity ◀── mapper ◀─────────┘
                     │
                     └──▶ ??? save / merge / reaplicar sobre a managed
                          └── é AQUI que a armadilha nº 11 mora
```

Duas restrições vindas de incidentes já vividos nesta app:

1. **Armadilha nº 11 (`CLAUDE.md`):** *"Nunca submeta ao JPA uma instância detached cuja linha você
   mesmo apagou na mesma transação."* Com `@Version` presente, `AbstractEntityPersister.isTransient`
   deixa de responder "não sei" e passa a responder "é detached de verdade"; o `merge` conclui que
   outra transação apagou a linha e lança `StaleObjectStateException` → 409 determinístico, imune a
   retry. Funcionou por meses até `@Version` existir.

2. **Armadilha nº 12:** movimentar partição muda a forma do conflito. Sob disputa, a transação
   perdedora recebe `CannotAcquireLockException` (SQLSTATE 40001) em vez de conflito de versão — e sem
   o handler de `ConcurrencyFailureException` isso vira 500 em vez de 409.

Nenhuma das duas é hipotética. As duas já custaram uma change de correção cada.

## Goals / Non-Goals

**Goals**

- `domain/` do `contratocommand` 100% livre de `jakarta.persistence` e `org.hibernate`, exceto a
  exceção de anotação de injeção em `domain/service/` já registrada.
- Lock otimista funcionando **provadamente**, não presumidamente.
- Identidade do agregado sem conhecimento de partição no domínio.

**Non-Goals**

- Trocar o esquema de particionamento ou a geração de id. A porta esconde a estratégia atual; os ids
  gerados continuam byte a byte os mesmos.
- Mudar o contrato REST, o schema do banco ou o formato dos eventos.
- Enriquecer o modelo de domínio além do que ele já faz. Comportamento novo é outra proposta.

## Decisions

### D1 — O `version` trafega no modelo de domínio como dado opaco

O modelo puro carrega a versão. Não como conceito de negócio — ninguém consulta a versão de uma
autorização — mas como **token de concorrência** que o adaptador precisa devolver ao JPA para que o
`UPDATE` saia com `WHERE ... AND version = ?`.

Sem isso, o caminho de falha é o pior possível: o `UPDATE` sai sem cláusula de versão, nada estoura,
os testes unitários passam, e o cenário de cancelamento duplicado que `integridade-fluxo-escrita`
fechou volta **em silêncio**. Nenhum log, nenhuma exceção — só duas escritas concorrentes sobrescrevendo
uma à outra e dois eventos no SNS para a mesma autorização.

Por ser um caminho de falha silencioso, a verificação **tem de ser empírica**: o
`ConcorrenciaOptimisticaIntegrationTest` precisa rodar de verdade e falhar se a proteção sumir. Um
skip ali não conta como verde — foi exatamente o "Tests run: 0" indistinguível de sucesso que a task
3.7 daquela change teve de corrigir.

**Divergência deliberada do `contratoquery`:** lá (D2) a versão **não** sobe para o modelo, porque
aquela app não escreve. Aqui ela sobe. É a diferença central entre as duas apps.

### D2 — A escrita reaplica o modelo sobre a entidade gerenciada; não usa `merge` de detached

Três formas de gravar o modelo mutado:

| Forma | O que faz | Veredicto |
|---|---|---|
| **(a)** `save(mapper.paraEntidade(modelo))` | monta entidade nova e deixa o Spring Data decidir | **rejeitada** — entidade com `@Version` não-nulo é tratada como detached, e vai para `merge`. É literalmente o gatilho da armadilha nº 11 |
| **(b)** `merge()` explícito | idem, com o problema explícito | **rejeitada** pelo mesmo motivo |
| **(c)** carregar a entidade gerenciada, **reaplicar** os campos mutados do modelo sobre ela, deixar o dirty checking emitir o `UPDATE` | mantém o mecanismo que funciona hoje | **adotada** |

A forma (c) preserva exatamente a semântica atual: a entidade permanece gerenciada dentro da
transação, o Hibernate compara com o snapshot e emite o `UPDATE` com a cláusula de versão. O modelo de
domínio é a superfície onde a regra roda; a entidade continua sendo o veículo da escrita.

Custo: o mapper ganha um terceiro método (`aplicarEm(Autorizacao modelo, AutorizacaoJpaEntity alvo)`),
além dos dois de conversão. É mais código do que o exemplo da skill mostra — o exemplo assume
agregados que se gravam inteiros, e esta tabela é particionada com lock otimista.

**A criação é diferente e continua simples:** não há entidade prévia, então `save(paraEntidade(modelo))`
com `version` nulo é um `persist` normal. A forma (c) vale só para cancelamento, decisão e expurgo.

### D3 — A porta de identidade devolve o id pronto; o domínio não sabe o que há dentro dele

```java
// domain/port/out/GeradorIdentidadeAutorizacao.java
public interface GeradorIdentidadeAutorizacao {
    UUID gerarPara(UUID idUnicoContaContratante);
}
```

O adaptador em `infrastructure/persistence/` faz o que `inicializaCriacao()` faz hoje:
`IdContaUUIDPartitionDistributor.getPartitionFast(conta)` seguido de `ReversibleUUIDv7.generate(particao)`.
Mesmo algoritmo, mesmos ids — só que agora o domínio recebe um `UUID` e não sabe que ele carrega uma
partição.

**O que `Autorizacao.inicializaCriacao()` mantém:** status inicial por produto (o `EnumMap` com
`PIX_AUTO → RECEBIDA`, `DDA_AUTO → ATIVA` e o `IllegalStateException` para produto sem entrada), datas
de vigência e inclusão, e os defaults. Tudo isso é regra de negócio e continua no modelo.

**O que sai:** a geração do id e o cálculo da partição. O caso de uso obtém o id pela porta e o passa
ao modelo.

**Por que só o `contratocommand` tem esta porta:** o `contratoquery` só **extrai** partição de ids
existentes, e essa extração já ficou inteiramente dentro do adaptador dele (D4 daquela mudança). Só a
geração precisava de porta, e só o command gera.

**Ressalva honesta:** isto esconde a estratégia, não a elimina. O id continua carregando a partição, e
o expurgo continua movendo linhas entre partições. A porta faz com que o **domínio** deixe de saber
disso — o acoplamento entre identidade e layout físico segue existindo, agora confinado a
`infrastructure/persistence/`. Trocar o esquema ainda exigiria migração de dados; a diferença é que
não exigiria mais tocar em `domain/`.

### D4 — `ControleExpurgoAutorizacao` vai para `infrastructure/persistence/`

Ele calcula `900 + (semanas desde Epoch % 100)` e valida se uma partição é segura para drop. São
números de partição — layout físico puro. Vai para `infrastructure/persistence/`, onde o adaptador de
`transferirParaExpurgo` (introduzido na etapa anterior, D4) já o consome.

`ControleExpurgoAutorizacaoTest` é teste de lógica pura e continua válido no novo pacote. Ele lança
`BusinessException`, que fica em `domain/exception/` — infraestrutura pode depender do domínio, então
a seta continua correta.

### D5 — `AutorizacaoEventoPayload` passa a mapear do modelo de domínio

Hoje `AutorizacaoEventoPayload.from(autorizacao)` recebe a entidade JPA. Depois, recebe
`domain/model/Autorizacao`. O record continua em `infrastructure/messaging/` e continua com
`@JsonProperty` por **nome de coluna** — o contrato de fio não muda em nada.

Isso exige que o modelo de domínio exponha todos os campos que o payload publica. Como o payload
espelha a linha inteira, na prática o modelo precisa de todas as 25 colunas — o que já era o caso.

**Consequência para a checagem cruzada:** o `CLAUDE.md` manda replicar mudança de coluna em
`AutorizacaoEventoPayload` aqui e no `autorizacaostatus-producer`, mais os dois `.avsc`. Agora entra
mais um ponto: `AutorizacaoJpaEntity`, `domain/model/Autorizacao` e o mapper. O checklist de commit
precisa refletir isso.

### D6 — O `@Table` continua **não** declarando a unicidade de `id_autorizacao_empresa`

A entidade atual carrega um comentário explicando que a unicidade real é um índice único **parcial**
(só partições com `id_particao_conta < 900`), forma que o JPA não expressa, e que declará-la
prometeria garantia diferente da real.

Ao recriar as anotações em `AutorizacaoJpaEntity`, esse comentário e essa ausência devem ser
preservados. É o tipo de "melhoria" que um agente adiciona por reflexo ao ver uma constraint
documentada e não anotada — e que geraria DDL inválido em tabela particionada.

## Risks / Trade-offs

- **Risco crítico: perder o lock otimista em silêncio.** Se o `version` não fizer a ida e a volta pelo
  mapper (D1), nada falha visivelmente e a proteção some. Mitigação: `ConcorrenciaOptimisticaIntegrationTest`
  rodando de verdade (skip não conta), mais um teste que afirma explicitamente que o `UPDATE` emitido
  contém a cláusula de versão.
- **Risco crítico: reencontrar a armadilha nº 11.** Endereçado por D2 com a forma (c). Se a
  implementação escorregar para `save`/`merge` de detached, o sintoma é
  `StaleObjectStateException` → 409 determinístico e imune a retry, em operação que deveria ter
  sucesso. Mitigação: task explícita proibindo (a) e (b), mais teste de cancelamento simples
  bem-sucedido.
- **Risco alto: divergência silenciosa entre `AutorizacaoJpaEntity` e o schema.** 25 colunas recriadas
  à mão. Um `@Column(name=...)` errado quebra em runtime; um `@Convert` esquecido grava valor errado
  **sem erro**. Mitigação: conferência coluna a coluna contra a entidade atual e contra as migrations,
  mais teste de mapper cobrindo todos os campos, ida e volta.
- **Risco: mudar os ids gerados.** Se a porta de identidade não reproduzir exatamente
  `getPartitionFast` + `generate`, autorizações novas caem em partições diferentes das que o expurgo
  espera. Mitigação: teste que compara ids gerados pela porta com os gerados pelo caminho antigo, para
  o mesmo par de entradas.
- **Trade-off aceito: o mapper tem três métodos, não dois** (D2). Mais código que o exemplo da skill,
  justificado pelo lock otimista.
- **Trade-off aceito: a porta esconde, não elimina, o acoplamento identidade × partição** (D3), com a
  ressalva escrita.
- **Trade-off aceito: mais um ponto no espelhamento manual** (D5), refletido no checklist de commit.

## Migration Plan

Três passos, cada um com a suíte verde antes do próximo:

**Passo 1 — a entidade nasce, o modelo ainda não.** Criar `AutorizacaoJpaEntity` e os embeddables como
cópia fiel das classes atuais, mover converters e utilities de partição, e fazer
`AutorizacaoJpaAdapter` usar a entidade nova internamente, ainda devolvendo a classe antiga. A suíte
inteira deve passar sem que nada em `application` ou `domain` tenha mudado.

**Passo 2 — o modelo puro aparece e o mapper entra.** Reescrever `domain/model/Autorizacao` sem
anotações, criar `AutorizacaoPersistenceMapper` com os três métodos (D2), e fazer o adaptador mapear.
É aqui que o lock otimista pode quebrar em silêncio — a verificação empírica é obrigatória **antes** de
seguir.

**Passo 3 — a identidade sai do domínio.** Introduzir `GeradorIdentidadeAutorizacao`, mover a geração
para o adaptador, ajustar `inicializaCriacao()` para receber o id pronto.

Reverter é `git revert` em qualquer passo — não há migration de banco nem mudança de contrato. O que
não é reversível é dado corrompido por lock otimista que parou de funcionar sem avisar, e é por isso
que o passo 2 tem porta de verificação empírica.

## Open Questions

- **`domain/model/Autorizacao` deve ser mutável ou produzir cópias em cada transição?** O
  `contratoquery` optou por imutabilidade total, mas lá não há escrita. Aqui `cancelar()` e as três
  ações de decisão mudam status e motivo. Um modelo imutável que devolve nova instância é mais limpo,
  mas obriga o adaptador a reaplicar a instância nova sobre a gerenciada (D2 já faz isso, então o custo
  pode ser zero). Decidir no passo 2 e registrar aqui.
- **O `version` deve ser campo do modelo ou viajar por fora, num envelope?** D1 assume campo do modelo,
  o que é pragmático e põe um conceito de infraestrutura no domínio. A alternativa — a porta devolver
  `AutorizacaoComVersao(modelo, version)` — mantém o modelo limpo ao custo de um wrapper em toda
  assinatura. Decidir no passo 2. Não bloqueia o passo 1.

## Decisões tomadas na implementação

**Nenhum precedente real em `hexagonal-classico-contratoquery`:** ao contrário do assumido pela
proposta original ("o `contratoquery` já terá exercitado o padrão"), a change
`hexagonal-classico-contratoquery` nunca foi aplicada — o código de `contratoquery` seguia no layout
legado no momento desta implementação. As decisões abaixo foram tomadas sem esse precedente,
seguindo a inclinação já registrada no próprio design.md desta mudança.

- **`domain/model/Autorizacao` é classe mutável** (Lombok `@Data`), não record nem cópias por
  transição. Confirma a inclinação já registrada: `cancelar()` e as três ações de decisão mudam
  status/motivo/cancelamento em lugar, e o adaptador aplica essas mutações sobre a entidade
  gerenciada via `aplicarEm` — não haveria ganho em produzir cópias imutáveis só para descartá-las
  no passo seguinte.
- **`version` é campo do modelo** (`Long version`), não envelope separado. Populado exclusivamente
  por `AutorizacaoPersistenceMapper.paraDominio`, nunca setado por `domain/service/` ou
  `application/usecase/`.
- **`domain/model/Autorizacao` também carrega `idParticaoConta` como campo** — divergência
  consciente do objetivo "domínio não menciona partição em lugar nenhum" (spec
  `layout-hexagonal-classico`, requisito de identidade atrás de porta). Motivo: o payload do evento
  publicado no SNS (`AutorizacaoEventoPayload`, campo `id_particao_conta`) precisa refletir a
  localização física **atual** da linha, que após o expurgo mover a linha **diverge** da partição
  embutida no UUID (`ReversibleUUIDv7.extract` sempre devolve a partição de *criação*). Reextrair do
  UUID no adaptador de mensageria devolveria o valor errado nos eventos de cancelamento/rejeição/
  expiração — quebra de comportamento observável, que é proibida pela proposta.
  O campo é opaco: o domínio nunca o lê, decide ou calcula — só `AutorizacaoPersistenceMapper` o
  preenche (a partir da entidade) e só `AutorizacaoEventoPayload` o lê (para o payload). Nenhuma
  regra de negócio, validator ou rule toca nele. Se uma auditoria de arquitetura tratar isso como
  bloqueante, a correção exigiria mover a leitura de `id_particao_conta` para dentro do adaptador de
  persistência (ex.: o evento de domínio carregar a entidade gerenciada, não o modelo) — mudança de
  escopo maior, não feita aqui por ser: (a) fora do orçamento desta migração e (b) sem prejuízo de
  comportamento observável, que é o requisito mais forte e explícito da proposta.
- **Porta de saída ganhou dois métodos renomeados**, não previstos explicitamente pelas tasks mas
  necessários para o domínio deixar de tocar em `ReversibleUUIDv7`/`IdContaUUIDPartitionDistributor`
  (que migraram para `infrastructure/persistence/` nesta mesma mudança, tornando as chamadas diretas
  que `CriarAutorizacaoService` fazia antes impossíveis):
  - `existsByIdAutorizacao_IdParticaoContaAndIdAutorizacaoEmpresa(Integer, String)` virou
    `existeAutorizacaoAtivaComIdEmpresa(UUID idUnicoContaContratante, String idAutorizacaoEmpresa)` —
    a poda para a partição quente da conta passou a ser calculada dentro do adaptador.
  - `findByIdAutorizacaoAndParticao(UUID, Integer)` virou `findById(UUID)` — a extração da partição
    via `ReversibleUUIDv7.extract` passou a ser interna ao adaptador. Válida em todos os pontos de
    chamada porque só é usada antes do primeiro expurgo de cada autorização (a UUID-embutida ainda
    coincide com a física nesse momento, por invariante da máquina de estados).
- **Verificação do lock otimista:** em vez de uma asserção textual isolada sobre a cláusula SQL
  (task 4.2), a garantia foi verificada pela execução real de `ConcorrenciaOptimisticaIntegrationTest`
  contra Postgres — que prova empiricamente, não apenas textualmente, que exatamente uma de duas
  transações concorrentes vence. Julgada verificação mais forte que grep de log.
