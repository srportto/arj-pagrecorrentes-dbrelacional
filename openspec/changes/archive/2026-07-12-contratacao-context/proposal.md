# Proposal: contratacao-context

## Why

No `contratocommand`, o header `tipoJornada` é injetado dentro do record `CriarAutorizacaoRequest` reconstruindo-o com 16 argumentos posicionais no `AutorizacaoController` — código moroso, propenso a erro de posição e assimétrico com o cancelamento, que já resolve o mesmo problema (path + header + body) com o record imutável `CancelamentoContext`. Além disso, a spec `coesao-contratocommand` já exige que valores derivados de header viajem "como parâmetros/contexto explícitos entre as camadas, e não mutados dentro do DTO" — a contratação é hoje o único fluxo fora dessa forma.

## What Changes

- Criar o record imutável `ContratacaoContext` em `application/contratacao/`, espelhando `CancelamentoContext` (fábrica estática `doRequest`), carregando `tipoJornada` (header, já resolvido para enum) e `dados` (o `CriarAutorizacaoRequest` do body).
- Remover o componente `tipoJornada` de `CriarAutorizacaoRequest` — o record passa a representar exclusivamente o body (15 campos) e permanece imutável.
- Retipar o framework de validação da contratação: `ContratacaoRule extends Rule<ContratacaoContext>` e `ContratacaoValidator implements Validator<ContratacaoRule, ContratacaoContext>`; as 4 rules (`ProdutoSuportado`, `DataFimVigenciaInvalida`, `ValorLimiteContrato`, `MetadadoRule`) passam a receber o contexto e acessar o body via `contexto.dados()`.
- `CriarAutorizacaoUseCase.execute(ContratacaoContext)` — assinatura estável que não cresce com futuros dados de header.
- `AutorizacaoMapper` deixa de ler `request.tipoJornada()`; a jornada chega como parâmetro de origem explícito (decisão de assinatura detalhada no design).
- `AutorizacaoController.insert` monta o contexto e chama o use case — a cópia manual de 16 argumentos é eliminada.
- Testes ajustados: `TestFixtures` ganha `criarContext(...)` ao lado do `cancelarContext(...)` existente; testes de rules, use case, mapper e controller migram para o contexto.
- Documentação atualizada após o código: `CLAUDE.md` + `AGENTS.md` (espelhos) e `README.md` do `contratocommand` — fluxo do POST, seção do framework de validação (`Rule<CriarAutorizacaoRequest>` → `Rule<ContratacaoContext>`), convenções.

**Não é BREAKING para clientes da API**: o header `tipoJornada` continua obrigatório com a mesma validação e códigos HTTP; um campo `tipoJornada` enviado no body já era sobrescrito/ignorado hoje e passa a ser propriedade desconhecida ignorada pelo Jackson — comportamento observável idêntico.

## Capabilities

### New Capabilities

Nenhuma — a mudança é de coesão interna; nenhum comportamento observável novo é introduzido.

### Modified Capabilities

- `coesao-contratocommand`: dois requirements têm o texto/cenários atualizados:
  - **"DTOs de request são imutáveis e não carregam estado interno"** — passa a cobrir explicitamente a contratação: a jornada vinda do header MUST viajar em contexto imutável (`ContratacaoContext`), e o request de criação MUST NOT conter campo derivado de header.
  - **"Organização por feature na aplicação e domínio puro"** — o contexto deixa de ser exclusividade do cancelamento: ambas as features têm `Context` no seu pacote (`application/contratacao/ContratacaoContext`, `application/cancelamento/CancelamentoContext`).

## Impact

- **Código (main, ~9 arquivos)**: `entrypoint/contratosrest/CriarAutorizacaoRequest`, `entrypoint/AutorizacaoController`, `application/contratacao/ContratacaoContext` (novo), `application/contratacao/CriarAutorizacaoUseCase`, `application/contratacao/ContratacaoRule`, `application/contratacao/ContratacaoValidator`, `application/contratacao/rules/*` (4 rules), `application/AutorizacaoMapper`.
- **Não tocados**: `shared/validationsetup` (`Rule`/`Validator` já são genéricos), todo o lado do cancelamento, `domain/`, contratos REST, banco.
- **Testes**: `TestFixtures`, testes de rules de contratação, `CriarAutorizacaoUseCaseTest`, `AutorizacaoMapperTest`, `AutorizacaoControllerTest`.
- **Docs**: `aplicacoes/contratocommand/CLAUDE.md`, `AGENTS.md` (manter espelhados), `README.md`.
- **Specs**: delta em `coesao-contratocommand`.
- **Dependências/sistemas**: nenhuma dependência nova; sem mudança de API pública, banco ou mensageria.
