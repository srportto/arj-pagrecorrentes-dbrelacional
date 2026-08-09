## Context

`Autorizacao.inicializaCriacao()` (`apps/arj-contratocommand/.../domain/entities/Autorizacao.java`) grava `status = StatusAutorizacao.ATIVA.getStatusAutorizacao()` de forma incondicional para qualquer produto. É chamado a partir de `AutorizacaoMapper.afterMapping()`, na seguinte ordem:

```
afterMapping(dados, tipoJornada, autorizacao):
    autorizacao.setTipoProduto(...)     // 1
    autorizacao.setMetadados(...)       // 2 (se houver)
    autorizacao.inicializaCriacao()     // 3 ← hoje grava status=ATIVA sempre
    autorizacao.setMotivoStatus(...)    // 4 (deriva de tipoJornada, não do status)
```

No passo 3, `this.tipoProduto` já está setado na entidade — `inicializaCriacao()` tem acesso direto ao produto sem precisar de parâmetro novo.

O evento SNS publicado após o commit (`AutorizacaoEventoPublisher`) deriva `tipoEvento` do status persistido via `TipoEventoAutorizacao.porStatus(status)` — bijeção já existente e testada (`RECEBIDA→RECEPCAO`, `ATIVA→ATIVACAO`, etc.). Nenhuma mudança é necessária ali: o tipo de evento correto sai "de graça" assim que o status persistido mudar.

O grafo de transições (`StatusAutorizacao.TRANSICOES`) já contém `RECEBIDA → EM_PROCESSO_ATIVACAO` e `EM_PROCESSO_ATIVACAO → ATIVA`, então o caminho `RECEBIDA → EM_PROCESSO_ATIVACAO → ATIVA` que o futuro endpoint de aprovação vai percorrer já é uma transição válida hoje, sem exigir mudança no enum.

## Goals / Non-Goals

**Goals:**
- Ao criar uma autorização `PIX_AUTO`, persistir `status = RECEBIDA`.
- Manter `DDA_AUTO` persistindo `status = ATIVA` na criação, sem nenhuma mudança de comportamento.
- Manter `motivoStatus` derivado exclusivamente da `tipoJornada`, como hoje.
- Confirmar (sem alterar) que `StatusAutorizacao.TRANSICOES` já cobre o caminho `RECEBIDA → EM_PROCESSO_ATIVACAO → ATIVA`.

**Non-Goals:**
- Desenhar ou implementar o endpoint de aprovação do cliente que transiciona `RECEBIDA → ATIVA` (ou `→ EM_PROCESSO_ATIVACAO`) para `PIX_AUTO`. Isso é mudança futura, ainda não planejada.
- Alterar `AutorizacaoEventoPublisher`, `AutorizacaoEventoPayload` ou o schema do tópico SNS — o efeito no `tipoEvento` publicado é automático via `TipoEventoAutorizacao.porStatus()`.
- Alterar `StatusAutorizacao.TRANSICOES` — o caminho necessário já existe.
- Alterar comportamento de `autorizacaostatus-producer` ou `eventos-consumer` — ambos já sabem processar o evento `RECEPCAO` (existe desde a criação do enum `TipoEventoAutorizacao`, mesmo nunca tendo sido exercitado em produção via fluxo de criação).
- Mudar a lógica de derivação de `motivoStatus` a partir de `tipoJornada`.

## Decisions

### 1. Onde decidir o status inicial: dentro de `Autorizacao.inicializaCriacao()`, não como `ContratacaoRule`

O CLAUDE.md do serviço documenta a convenção "variação por produto vive em rules, não em strategies", referindo-se a `ContratacaoRule`/`CancelamentoRule`. Essas rules rodam em `ContratacaoValidator.validar(context)`, **antes** de `AutorizacaoMapper.toDomain()` — ou seja, antes de a entidade `Autorizacao` existir. Rules só validam e rejeitam (lançam `BusinessException`); não inicializam estado da entidade.

Decisão: a lógica de status inicial por produto fica dentro de `inicializaCriacao()` (ou um método privado de apoio chamado por ele), consultando `this.tipoProduto`, que já está setado nesse ponto. Não é criada nenhuma `ContratacaoRule` nova.

Alternativa considerada e descartada: resolver o status no `AutorizacaoMapper.afterMapping()` (fora da entidade), com um `switch` sobre `dados.tipoProduto()` e um `autorizacao.setStatus(...)` explícito depois de `inicializaCriacao()`. Descartada porque duplica a leitura do produto (o mapper já delega toda a inicialização de criação para `inicializaCriacao()`) e espalha a regra "status por produto" para fora do método que é hoje a fonte única de verdade sobre o que significa "inicializar uma autorização para criação".

### 2. Estratégia de implementação: mapa produto→status inicial, não `if/else` solto

Seguindo o mesmo estilo do enum `StatusAutorizacao` (que já usa uma estrutura declarativa — `EnumMap`/`EnumSet` — para o grafo de transições em vez de `if/else`), a forma mais coerente é um mapeamento explícito produto→status inicial (ex.: `EnumMap<TipoProduto, StatusAutorizacao>`) consultado dentro de `inicializaCriacao()`, com `DDA_AUTO → ATIVA` e `PIX_AUTO → RECEBIDA`. Isso deixa o comportamento auditável num único lugar e failure-safe: qualquer `TipoProduto` novo adicionado no futuro sem entrada no mapa falha de forma explícita (em vez de silenciosamente herdar `ATIVA`).

Alternativa considerada: `if (tipoProduto == TipoProduto.PIX_AUTO) RECEBIDA else ATIVA`. Funciona para os 2 produtos atuais, mas erra silenciosamente para "ausência de tratamento explícito" caso um terceiro produto seja adicionado (herdaria `ATIVA` por ser o `else`, sem qualquer sinal de que ninguém decidiu isso conscientemente).

### 3. `motivoStatus` permanece desacoplado do `status`

Confirmado com o usuário: `motivoStatus` continua vindo só de `tipoJornada` (`MotivoStatusAutorizacao.obterMotivoStatusEnumPorIdMotivo(tipoJornada.getCodigoJornada())`), independente do novo status `RECEBIDA`. Não há necessidade de tocar em `AutorizacaoMapper.afterMapping()` nem na spec `motivo-status-por-jornada` — o mapeamento jornada→motivo já produz valores coerentes com "recebida, aguardando os próximos passos" (ex.: `RECEPCAO_SPI_J1`).

## Risks / Trade-offs

- **[Risco] Consumidores externos (parceiros, dashboards) que hoje assumem que toda resposta 201 de `POST /api/autorizacoes` para PIX_AUTO significa "autorização ativa"** → Mitigação: é justamente o comportamento que esta mudança corrige; a resposta HTTP já expõe o `status` real (`RECEBIDA`) no `AutorizacaoCompletaResponseDto`, então clientes que leem o campo já vão perceber a mudança de estado corretamente. Comunicar a mudança de contrato observável para quem consome a API, mesmo sem mudança de schema.
- **[Risco] Downstream (`autorizacaostatus-producer`, `eventos-consumer`) nunca processou em produção um evento `RECEPCAO` originado de uma criação de PIX_AUTO** (só existia teoricamente na bijeção do enum) → Mitigação: validar manualmente o fluxo ponta a ponta (SNS → SQS → Kafka → consumer) com uma criação PIX_AUTO em ambiente local/staging antes do deploy, já que o "caminho feliz" testado até hoje sempre envolvia `ATIVACAO`.
- **[Trade-off] Sem o endpoint de aprovação (fora de escopo), toda autorização PIX_AUTO criada após este deploy fica presa em `RECEBIDA` indefinidamente** → Aceito conscientemente pelo usuário; é o estado transitório correto até a próxima mudança implementar a aprovação.

## Migration Plan

Sem migração de schema ou de dados — `status` já é uma coluna `Integer` existente, e `RECEBIDA` (código 1) já é um valor válido nela. Nenhuma autorização `PIX_AUTO` existente é retroativamente alterada; a mudança afeta só criações novas a partir do deploy.

Deploy padrão (sem passo especial de rollback): reverter o deploy do `arj-contratocommand` volta o comportamento anterior (toda criação grava `ATIVA`) sem qualquer efeito colateral em dados já persistidos.

## Open Questions

Nenhuma pendente — decisões confirmadas com o usuário (DDA_AUTO inalterado, motivoStatus inalterado, grafo de transições já suficiente). O desenho do endpoint de aprovação (`RECEBIDA/EM_PROCESSO_ATIVACAO → ATIVA`) fica para uma mudança futura.
