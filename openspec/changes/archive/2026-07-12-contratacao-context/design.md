# Design: contratacao-context

## Context

Hoje o fluxo de criação injeta o header `tipoJornada` dentro do próprio DTO do body: o `AutorizacaoController` recebe o `CriarAutorizacaoRequest` (16 componentes, com `tipoJornada` vindo `null` do body), resolve o enum a partir do header e **reconstrói o record inteiro com 16 argumentos posicionais** só para preencher esse campo. A jornada é consumida por um único ponto downstream: o `@AfterMapping` do `AutorizacaoMapper` (derivação do `motivoStatus`). Nenhuma das 4 `ContratacaoRule` a utiliza.

O cancelamento já resolveu o mesmo problema (path + header + body) com o record imutável `CancelamentoContext` (`application/cancelamento/`), com fábrica `doRequest(...)` e wither `comProdutoAutorizacao(...)`. A spec `coesao-contratocommand` exige que valores derivados de header viajem como "parâmetros/contexto explícitos entre as camadas, e não mutados dentro do DTO".

Restrições:
- O framework genérico `shared/validationsetup` (`Rule<T>`, `Validator<R,T>`) não deve ser alterado — ele já suporta qualquer `T`.
- Contrato REST público preservado (spec `coesao-contratocommand`, requirement "Contratos REST públicos preservados").
- `CLAUDE.md` e `AGENTS.md` do `contratocommand` são espelhos e devem permanecer idênticos.

## Goals / Non-Goals

**Goals:**
- Eliminar a reconstrução manual de 16 argumentos no controller.
- Uniformizar a forma das duas features: *controller resolve headers → monta contexto imutável → use case → rules sobre o contexto*.
- `CriarAutorizacaoRequest` volta a representar exclusivamente o body (15 campos), imutável.
- Rules da contratação passam a enxergar a jornada (habilita regras futuras body+header sem novo refactor).
- Documentação (`CLAUDE.md`/`AGENTS.md`/`README.md` do módulo) fiel ao código resultante.

**Non-Goals:**
- Nenhuma mudança de comportamento observável da API (endpoints, headers, códigos HTTP, payloads de resposta, valores persistidos).
- Nenhuma mudança no lado do cancelamento, no `domain/` ou no `shared/validationsetup`.
- Não introduzir wither/enriquecimento no `ContratacaoContext` — hoje não há dado carregado do banco pré-validação na contratação (o wither do cancelamento existe por necessidade real de lá).
- Não converter DTOs para classes mutáveis — a convenção "requests são records imutáveis" permanece.

## Decisions

### D1 — `ContratacaoContext` como record em `application/contratacao/`

Espelha `CancelamentoContext` em localização (pacote da feature), forma (record imutável) e idioma (fábrica estática `doRequest`):

```java
public record ContratacaoContext(
        TipoJornadaAutorizacao tipoJornada,   // header, já resolvido para enum no controller
        CriarAutorizacaoRequest dados) {      // body, 15 campos, intocado

    public static ContratacaoContext doRequest(
            TipoJornadaAutorizacao tipoJornada, CriarAutorizacaoRequest dados) {
        return new ContratacaoContext(tipoJornada, dados);
    }
}
```

Campo do body chama-se `dados`, como no cancelamento. Sem wither (ver Non-Goals).

*Alternativas consideradas*: (a) DTO mutável com setter — resolve o sintoma mas quebra a convenção documentada e abre mutação silenciosa nas rules; (b) wither `comTipoJornada` no record — barato, mas mantém dado de header dentro do DTO do body e a assimetria entre features; (c) parâmetro solto `execute(request, jornada)` — limpa o contrato mas a assinatura cresce a cada novo dado de contexto e as rules não enxergam a jornada.

### D2 — Retipagem do framework de validação da contratação

`ContratacaoRule extends Rule<ContratacaoContext>` e `ContratacaoValidator implements Validator<ContratacaoRule, ContratacaoContext>`. As 4 rules mudam apenas a assinatura (`aceita(ContratacaoContext)` / `validar(ContratacaoContext)`) e o acesso ao body (`contexto.dados().tipoProduto()` etc.). O `@Order(Ordered.HIGHEST_PRECEDENCE)` de `ProdutoSuportado` e a semântica de todas as regras permanecem idênticos. `shared/validationsetup` não é tocado — é exatamente o mesmo mecanismo que o cancelamento já usa com `Rule<CancelamentoContext>`.

### D3 — Mapper recebe origens explícitas, não o contexto

Assinatura: `toDomain(CriarAutorizacaoRequest dados, TipoJornadaAutorizacao tipoJornada)`, com o use case desembrulhando: `mapper.toDomain(context.dados(), context.tipoJornada())`.

Racional: `AutorizacaoMapper` vive na **raiz** de `application/` como componente compartilhado; recebê-lo tipado com `ContratacaoContext` criaria dependência da raiz para dentro do subpacote da feature (inversão do sentido de dependência atual, em que a feature importa a raiz). Com duas origens explícitas o mapper permanece neutro.

Consequências no MapStruct:
- Os `@Mapping(source = ...)` existentes passam a ser qualificados (`source = "dados.valor"` etc.) para eliminar ambiguidade entre parâmetros de origem.
- O `@AfterMapping` declara os dois parâmetros de origem (`CriarAutorizacaoRequest dados, TipoJornadaAutorizacao tipoJornada, @MappingTarget Autorizacao autorizacao`) — MapStruct injeta parâmetros de origem em métodos `@AfterMapping` por tipo. A derivação do `motivoStatus` troca `request.tipoJornada().getCodigoJornada()` por `tipoJornada.getCodigoJornada()`.

*Alternativa considerada*: `toDomain(ContratacaoContext contexto)` com `@Mapping(source = "dados.x", ...)` — funciona, mas acopla o mapper compartilhado ao tipo interno da feature.

### D4 — `tipoJornada` sai do `CriarAutorizacaoRequest`

O record fica com 15 componentes, todos do body. Efeito no contrato: um cliente que enviava `"tipoJornada"` no JSON tinha o valor **silenciosamente sobrescrito** pelo header; passa a ser propriedade desconhecida ignorada pelo Jackson (comportamento padrão do Spring). Resultado líquido idêntico (o header sempre venceu), semântica honesta.

### D5 — Ordem segura de implementação

Migração em passos compiláveis: criar o contexto → retipar `ContratacaoRule`/`ContratacaoValidator` e as 4 rules → use case e mapper → controller e remoção do campo no record → fixtures e testes → docs. Cada passo intermediário mantém `mvn clean compile` possível ao final do bloco (rules + validator + use case + mapper + controller + record precisam migrar juntos dentro do mesmo commit lógico, pois a retipagem é atômica do ponto de vista de compilação).

### D6 — Documentação como parte da change, após o código

`CLAUDE.md` e `AGENTS.md` (espelhos idênticos) e `README.md` do módulo são atualizados **depois** que o código compila e os testes passam, para descrever o estado final: diagrama do fluxo POST (controller monta `ContratacaoContext`), seção do framework de validação (`ContratacaoRule → extends Rule<ContratacaoContext>`), instruções de "adicionar regra" (`aceita(contexto)`), e convenções (contexto imutável em ambas as features).

## Risks / Trade-offs

- **[Generalidade especulativa]** O contexto nasce com um único campo além do body, não usado por nenhuma rule atual → Mitigação: o custo já pago habilita regras body+header sem refactor futuro, e o ganho principal (simetria entre features, eliminação da cópia de 16 args) é imediato e independente disso.
- **[Ambiguidade MapStruct com duas origens]** Propriedades não qualificadas podem gerar erro/aviso de compilação do processor → Mitigação: qualificar todos os `source` com o nome do parâmetro (`dados.*`); o build falha em tempo de compilação, não em runtime, então qualquer esquecimento aparece no `mvn clean compile`.
- **[Retipagem atômica]** A troca do `T` de `Rule` quebra compilação até todos os consumidores migrarem → Mitigação: ordem de implementação D5; a mudança inteira é um bloco de compilação único e os testes só rodam ao final dele.
- **[Docs espelhadas divergirem]** `CLAUDE.md` e `AGENTS.md` devem permanecer idênticos → Mitigação: task explícita de sincronização com verificação por diff entre os dois arquivos.
- **[Cliente que envia tipoJornada no body]** Passa de "sobrescrito" para "ignorado" → Mitigação: nenhum comportamento observável muda (o header sempre prevaleceu); registrado na proposal como não-BREAKING.

## Migration Plan

Refactor interno sem migração de dados, sem mudança de API e sem coordenação com consumidores. Deploy normal. Rollback = revert do commit. Verificação: `mvn test` no módulo `contratocommand` verde e diff vazio entre `CLAUDE.md` e `AGENTS.md`.

## Open Questions

Nenhuma — as decisões D1–D6 cobrem os pontos que estavam em aberto na exploração (assinatura do mapper, localização do contexto, escopo das rules).
