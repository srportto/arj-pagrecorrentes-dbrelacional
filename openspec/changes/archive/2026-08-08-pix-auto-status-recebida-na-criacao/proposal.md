## Why

Hoje `Autorizacao.inicializaCriacao()` grava `status = ATIVA` para qualquer produto na criação (`POST /api/autorizacoes`). Para `PIX_AUTO`, isso está incorreto: a autorização só deve ser considerada ativa depois que o cliente pagador aprova o pedido — aprovação essa que ainda não tem endpoint (será planejado em mudança futura, fora de escopo aqui). Enquanto isso não existir, gravar `ATIVA` na criação mente sobre o estado real da autorização e já dispara evento `ATIVACAO` no SNS antes de qualquer aprovação.

## What Changes

- Ao criar uma autorização `PIX_AUTO`, o status inicial persistido passa a ser `RECEBIDA` (em vez de `ATIVA`).
- `DDA_AUTO` não muda: continua sendo criado direto como `ATIVA`.
- `motivoStatus` continua derivado apenas da `tipoJornada` do header, sem nenhuma mudança nessa lógica.
- Como efeito automático (nenhuma mudança de código adicional): o evento publicado no SNS na criação passa a carregar `tipoEvento=RECEPCAO` para `PIX_AUTO` (hoje sempre `ATIVACAO`), pois `TipoEventoAutorizacao.porStatus()` já deriva o tipo a partir do status persistido.
- O grafo de transições de `StatusAutorizacao` já suporta o caminho `RECEBIDA → EM_PROCESSO_ATIVACAO → ATIVA` que o futuro endpoint de aprovação vai precisar — confirmado por leitura do código, nenhuma alteração é necessária nele nesta mudança.
- **Fora de escopo**: o endpoint de aprovação do cliente que efetivamente transiciona `RECEBIDA → ATIVA` para `PIX_AUTO`. Esta mudança só garante que a autorização nasce no estado correto e que o caminho de transição já existe para quando aquele endpoint for desenhado.

## Capabilities

### New Capabilities
- `status-inicial-por-produto`: define que o status inicial gravado na criação de uma autorização depende do `tipoProduto` — `PIX_AUTO` nasce `RECEBIDA`, `DDA_AUTO` nasce `ATIVA` — decidido dentro de `Autorizacao.inicializaCriacao()`, sem introduzir uma `ContratacaoRule` nova.

### Modified Capabilities
- `publicacao-eventos-autorizacao`: o cenário "Criação publica tipo derivado do status ATIVA" assumia que toda criação bem-sucedida persiste `ATIVA`. Isso deixa de ser universal — passa a valer só para produtos cuja criação grava `ATIVA` (hoje, `DDA_AUTO`); para `PIX_AUTO` a criação publica `tipoEvento=RECEPCAO`. A regra geral de derivação (`tipoEvento` sempre igual a `TipoEventoAutorizacao.porStatus(status)`) não muda.

## Impact

- **Código afetado**: `apps/contratocommand/src/main/java/br/com/srportto/contratocommand/domain/entities/Autorizacao.java` (`inicializaCriacao()`).
- **Nenhuma mudança** em `AutorizacaoMapper`, `CriarAutorizacaoUseCase`, `AutorizacaoEventoPublisher`, `StatusAutorizacao` (grafo de transições) ou nas outras 3 aplicações do monorepo — o efeito no evento SNS é automático, não requer alteração no publisher.
- **Testes**: `AutorizacaoTest`, `AutorizacaoMapperTest`, `TipoEventoAutorizacaoTest`, `AutorizacaoEventoPublisherTest` (em `apps/contratocommand/src/test/java/...`) precisam de casos cobrindo os dois produtos.
- **Documentação**: `apps/contratocommand/CLAUDE.md` (e seu espelho `AGENTS.md`), seção "Mapeamento de status" — hoje afirma que "criação grava `ATIVA` (= 4)" para toda criação; passa a ser condicional ao produto.
- **Consumidores downstream** (`autorizacaostatus-producer`, `eventos-consumer`): nenhuma mudança de código exigida — já tratam o evento `RECEPCAO` desde que o enum `TipoEventoAutorizacao` foi criado — mas o comportamento observado muda (autorizações `PIX_AUTO` deixam de aparecer como "ativadas" no fluxo de eventos até a aprovação futura existir).
