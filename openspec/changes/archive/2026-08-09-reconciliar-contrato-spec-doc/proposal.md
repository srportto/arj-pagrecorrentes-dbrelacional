## Why

A auditoria multi-agente de 2026-08-04 produziu ~60 achados, e a categoria mais numerosa não foi
"código errado" — foi **decisão que ninguém tomou** e **documentação que afirma o oposto do
código**. Os oito agentes, com escopos independentes, convergiram para o mesmo padrão: o sistema
resolveu sozinho, por omissão, questões que deveriam ter sido decididas; e vários `CLAUDE.md` e
specs descrevem um comportamento que o código não tem.

Isso é mais perigoso do que parece. Documentação incorreta não é neutra — ela **desliga a
desconfiança**. O caso mais caro da auditoria ilustra: o `CLAUDE.md` do `eventos-consumer` afirmava
que o tópico DLT "é criado sob demanda pelo auto-create padrão do broker local", enquanto o compose
desabilita auto-create. A afirmação falsa é a razão provável de a lacuna ter passado despercebida
por quem leu o arquivo justamente para saber onde estavam as armadilhas.

**Incoerências de contrato entre os dois serviços REST**, que representam a mesma autorização:

| Campo | `contratocommand` | `contratoquery` |
|---|---|---|
| `status` | `Integer` (código) | `String` (nome do enum) |
| valor | `valorAutorizacao` | `valor` |
| criação | `dataHoraInclusao` | `dataCriacao` |
| atualização | `dataHoraUltimaAtualizacao` | `dataAtualizacao` |

Quem cria pelo command e consulta pelo query precisa de dois mapeadores para a mesma entidade — e
comparar `status == 1` com `status == "ATIVA"` é um bug esperando acontecer. Não há versionamento
de API (`/api/autorizacoes`, sem `v1`) nem contrato OpenAPI em nenhum dos dois serviços, então hoje
não existe caminho de migração gradual: qualquer renomeação quebra todos os clientes de uma vez.

**Convenção de status HTTP que emergiu sem decisão:** violação de `@Valid` retorna 422 nos dois
serviços, mas o `README.md` e o `CLAUDE.md` do command prometem 400 — apontado por três agentes
distintos. O código é internamente coerente; a documentação discorda dele; e a convenção usual
reserva 422 para regra de negócio, não para formato inválido.

**Specs em contradição entre si:** `db-connection-pool-config` exige `maximum-pool-size = 5`,
`virtual-threads-config` exige `10`, e o código usa `10`. Duas capacidades especificam a mesma
propriedade com valores diferentes, e o código só pode cumprir uma.

**Drifts pontuais** entre documentação e realidade: o `CLAUDE.md` da raiz afirma que
`AutorizacaoEventoPayload` é espelhado em três apps quando existem duas cópias (o
`eventos-consumer` consome Avro direto); o `CLAUDE.md` do `autorizacaostatus-producer` lista Avro
1.11.3 e kafka-clients 3.7.1 enquanto o `pom.xml` traz 1.11.4 e 3.9.2; um comentário de proveniência
cita visibility timeout de 30s quando o valor real é 60s; o `CLAUDE.md` do command omite a rule
`ProdutoSuportadoCancelamento`, que roda antes da que está documentada.

**Código que desobedece a spec:** `TipoEventoAutorizacao` está em `application/eventos/` no
`contratocommand`, enquanto `maquina-estados-autorizacao` exige `domain/enums/` nas quatro
aplicações — e as outras três cumprem. Aqui a spec está correta e o código é que diverge.

**Drifts novos, introduzidos após a auditoria original (validados em 2026-08-09):** a mudança
`temporizacao-jornada-01-pix-auto` adicionou a rota `PATCH /decisao` e o status `RECEBIDA`, mas
não atualizou `apps/contratocommand/README.md` — a tabela de endpoints do README segue sem
`PATCH /{id}/decisao` e ainda anuncia `GET /api/autorizacoes/listar`, que **não existe** no
controller (a listagem vive no `contratoquery`). Os `CLAUDE.md`/`AGENTS.md` do command estão
corretos; o drift ficou isolado no README. Adicionalmente, os `README.md` dos dois serviços REST
usam nomes de status que não existem no enum: `statusAutorizacao` como nome de campo (o campo
real é `status`) e valores `ATIVO`/`CANCELADO` (os valores reais do enum de 8 posições são
`ATIVA`/`CANCELADA`).

**Contrato prometido e nunca implementado:** `publicacao-eventos-kafka` declara que a deduplicação
"SHALL ser responsabilidade do consumidor a jusante, pela key" — um contrato explícito. O único
consumidor do tópico não deduplica por nada. A promessa não tem quem a cumpra.

## What Changes

- Decidir e uniformizar a convenção de status HTTP entre formato inválido e regra de negócio,
  alinhando código, `README.md` e `CLAUDE.md`/`AGENTS.md` dos dois serviços.
- Padronizar o formato de `status` como nome do enum nos dois contratos, eliminando a divergência
  `Integer` vs `String`.
- Unificar a nomenclatura dos campos equivalentes entre command e query.
- **BREAKING (contrato):** as duas mudanças acima alteram o corpo de respostas existentes.
- Introduzir versionamento de API antes das renomeações, para que exista caminho de migração —
  ordem invertida quebraria todos os clientes de uma vez.
- Adicionar contrato OpenAPI aos dois serviços, tornando o contrato machine-readable e verificável
  contra o código.
- Reconciliar `db-connection-pool-config` com `virtual-threads-config` e com o código
  (`maximum-pool-size = 10`), eliminando a contradição entre specs.
- Mover `TipoEventoAutorizacao` para `domain/enums/` no `contratocommand`, alinhando o código à
  spec já existente e às outras três aplicações.
- Decidir o destino do contrato de deduplicação declarado em `publicacao-eventos-kafka`:
  implementá-lo no consumidor ou reescrever a spec para não prometer garantia que ninguém cumpre.
- Corrigir os drifts pontuais de documentação: cópias de `AutorizacaoEventoPayload` no `CLAUDE.md`
  da raiz, versões no `CLAUDE.md` do producer, comentário de visibility timeout, rule de
  cancelamento omitida no `CLAUDE.md` do command.
- Atualizar `apps/contratocommand/README.md` com a rota `PATCH /decisao` (ausente desde a
  implementação de `temporizacao-jornada-01-pix-auto`) e remover a menção ao endpoint inexistente
  `GET /api/autorizacoes/listar`.
- Corrigir os nomes de status inexistentes nos `README.md` do `contratocommand` e do
  `contratoquery` (`statusAutorizacao`/`ATIVO`/`CANCELADO` → `status`/`ATIVA`/`CANCELADA`,
  conforme o enum real de 8 valores).
- **Fora de escopo (deliberado):** migração da paginação offset para cursor e implementação de
  HATEOAS. São evoluções de contrato, não reconciliações.
- **Fora de escopo:** adoção de RFC 9457 Problem Details em substituição ao `LayoutErrosApiResponse`.
  O envelope atual é consistente entre os dois serviços; trocá-lo é decisão de contrato própria.
  Esta proposta apenas registra a escolha atual como deliberada, para que não seja revertida em
  apenas um dos serviços.
- **Fora de escopo:** a armadilha da DLT no `CLAUDE.md` do `eventos-consumer` e o caminho do compose
  Kafka, já corrigidos em `rede-seguranca-contrato-evento`.
- **Fora de escopo:** o drift do `idUnicoContaContratante` em `listar-autorizacoes`, já corrigido em
  `blindar-superficie-leitura`.

## Capabilities

### New Capabilities

- `contrato-api-consistente`: as regras que os contratos REST dos dois serviços SHALL respeitar em
  conjunto — nomenclatura idêntica para o mesmo dado, formato único por tipo de campo, convenção
  única de status HTTP, versionamento e contrato OpenAPI publicado.
- `documentacao-fiel-ao-codigo`: a exigência de que `CLAUDE.md`/`AGENTS.md` e specs descrevam o
  comportamento real, incluindo a regra de espelhamento entre os dois arquivos e a verificação
  periódica de drift.

### Modified Capabilities

- `db-connection-pool-config`: o requisito de `maximum-pool-size` padrão igual a 5 contradiz
  `virtual-threads-config` e o código, ambos em 10. O valor passa a ser 10, com referência
  explícita à razão (Virtual Threads).
- `publicacao-eventos-kafka`: o requisito que delega deduplicação ao consumidor a jusante declara
  um contrato que nenhum consumidor cumpre. Passa a explicitar que a garantia só vale quando o
  consumidor implementa a deduplicação, e que consumidor sem essa implementação NÃO SHALL aplicar
  efeito colateral persistente.

Não há delta para `maquina-estados-autorizacao`: a spec já exige `domain/enums/` nas quatro
aplicações e está correta — quem diverge é o código do `contratocommand`.

## Impact

- **Contrato de API (BREAKING):** DTOs de resposta dos dois serviços; exige versionamento antes das
  renomeações e comunicação a quem integra.
- **`contratocommand`:** `AutorizacaoCompletaResponseDto`, `ApiExceptionHandler`,
  `TipoEventoAutorizacao` (pacote), `README.md` (rota `/decisao` ausente, endpoint fantasma
  `/listar`, nomes de status incorretos), `CLAUDE.md`/`AGENTS.md`.
- **`contratoquery`:** `AutorizacaoDetalheResponseDto`, `AutorizacaoResumidaResponseDto`,
  `ApiExceptionHandler`, `README.md` (nomes de status incorretos), `CLAUDE.md`/`AGENTS.md`.
- **`autorizacaostatus-producer`:** `CLAUDE.md`/`AGENTS.md` (versões), comentário de proveniência em
  `KafkaProducerClientConfig`.
- **`eventos-consumer`:** possivelmente lógica de deduplicação, conforme a decisão sobre o contrato
  declarado.
- **Specs:** `db-connection-pool-config`, `publicacao-eventos-kafka`.
- **Raiz:** `CLAUDE.md` (número de cópias dos schemas), `README.md`.
- **Dependências:** `springdoc-openapi` nos dois serviços REST.
- **Ordem:** esta é a última das seis propostas da auditoria por depender das demais — ela encosta
  em DTOs, specs e documentação que as outras cinco alteram.
