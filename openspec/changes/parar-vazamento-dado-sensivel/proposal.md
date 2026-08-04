## Why

A auditoria multi-agente de 2026-08-04 encontrou dado sensível saindo do sistema por dois canais
distintos, em serviços que tratam autorizações de pagamento recorrente (PIX Automático /
DDA Automático):

**Para o log.** `ProcessarEventoAutorizacaoUseCase.java:17-18` do `eventos-consumer` interpola o
record Avro inteiro no `log.info` de cada mensagem consumida. O schema `EventoAutorizacao` carrega
`id_pessoa_pagadora`, `id_pessoa_devedora`, `id_pessoa_recebedora`, `valor`, `descricao` e
`metadados`. Cada evento processado grava esse conjunto em texto puro no agregador de logs, todos
os dias, indefinidamente. Dois agentes independentes — o revisor do app e o de segurança —
apontaram este mesmo ponto.

O que torna o achado inequívoco é que **o padrão correto já existe no monorepo e foi seguido no app
irmão**: o `CLAUDE.md` do `autorizacaostatus-producer` determina explicitamente que "nenhum log nem
mensagem de exceção carrega o body da mensagem", e o `AutorizacaoEventoPublisher` do
`arj-contratocommand` loga apenas `tipoEvento` e `idAutorizacao` em caso de falha. Não é ausência
de convenção — é uma convenção existente que um app não seguiu.

**Para a resposta HTTP.** Os `ApiExceptionHandler` do `arj-contratocommand` e do
`arj-contratoquery` devolvem `exception.getMessage()` diretamente no corpo das respostas 500 de
`ApplicationException`. Uma falha de acesso a dados pode assim expor nome de tabela, coluna,
constraint ou detalhe de infraestrutura a quem chama a API — que, sem camada de autenticação, é
qualquer cliente com acesso de rede.

Agravando o diagnóstico do lado do servidor: `ApplicationException` só tem construtor `(String)`,
sem `Throwable cause`. O `CancelarAutorizacaoUseCase` faz
`catch (Exception e) { throw new ApplicationException(e.getMessage()); }`, descartando a exceção
original. O log do handler registra o stack trace do `ApplicationException` recriado, não o da
falha real — então hoje o sistema **conta demais ao cliente e de menos ao operador**, exatamente
ao contrário do que deveria.

## What Changes

- Trocar o log de `ProcessarEventoAutorizacaoUseCase` no `eventos-consumer` para registrar apenas
  identificadores de negócio (`idAutorizacao`, `tipoEvento`), nunca o record Avro completo.
- Varrer os quatro apps por outras ocorrências do mesmo padrão — objeto de domínio, payload ou
  record interpolado em log — e corrigir as que existirem.
- Sanitizar a resposta de `ApplicationException` (500) nos dois `ApiExceptionHandler`: mensagem
  genérica ao cliente, detalhe completo apenas no log do servidor.
- Adicionar construtor `(String message, Throwable cause)` em `ApplicationException`,
  `BusinessException` e `ResourceNotFoundException` dos dois serviços, e passar a preservar a causa
  onde hoje ela é descartada — em especial no `catch` de `CancelarAutorizacaoUseCase`.
- Registrar a regra em documentação de forma que valha para os quatro apps, não apenas para os que
  já a seguem por acidente de histórico.
- **Fora de escopo (deliberado):** mascaramento automático de campos sensíveis via appender ou
  filtro de log (`logback` com conversor customizado). É a solução estrutural e vale a pena, mas é
  mudança de plataforma de observabilidade com raio próprio; esta proposta corrige os vazamentos
  conhecidos e fixa a regra.
- **Fora de escopo:** `@ExceptionHandler(Exception.class)` catch-all no `arj-contratoquery` e a
  sanitização da resposta de exceção **não mapeada**. Ambos pertencem a `blindar-superficie-leitura`,
  que trata o handler do query de forma abrangente. Esta proposta cobre especificamente o handler
  de `ApplicationException`, que existe nos dois serviços e hoje vaza `getMessage()`.
- **Fora de escopo:** correlação de logs via MDC/`traceId`, apontada pela auditoria como lacuna de
  observabilidade. É melhoria de diagnóstico, não contenção de vazamento.

## Capabilities

### New Capabilities

- `protecao-dado-sensivel`: o que nunca pode sair do sistema por log ou por resposta de erro —
  dado pessoal e financeiro em log, e detalhe interno de implementação em resposta HTTP — mais a
  contrapartida de que o diagnóstico completo seja preservado no servidor.

### Modified Capabilities

(nenhuma)

Nenhuma capacidade existente especifica hoje o que pode ou não ser registrado em log, nem o
conteúdo das respostas de erro `500`. Essa ausência é parte do problema: a regra existia apenas em
`CLAUDE.md` de um app, sem status normativo — o que permitiu que outro app divergisse sem que nada
acusasse.

## Impact

- **`eventos-consumer`:** `application/eventos/ProcessarEventoAutorizacaoUseCase.java` e o teste
  correspondente.
- **`arj-contratocommand`:** `shared/interceptors/api/ApiExceptionHandler.java`,
  `shared/exceptions/ApplicationException.java`, `application/cancelamento/CancelarAutorizacaoUseCase.java`.
- **`arj-contratoquery`:** `shared/interceptors/api/ApiExceptionHandler.java`,
  `shared/exceptions/{ApplicationException,BusinessException,ResourceNotFoundException}.java`.
- **Todos os apps:** varredura por ocorrências adicionais do padrão de log.
- **Documentação:** `CLAUDE.md`/`AGENTS.md` dos apps afetados, mantidos como espelhos idênticos.
- **Operação:** logs históricos já gravados continuam contendo os dados expostos — o expurgo ou
  retenção desse histórico é decisão operacional fora do código, sinalizada no `design.md`.
- **Diagnóstico:** a preservação da causa melhora a investigação de falhas em produção; nenhum
  contrato de API muda além do corpo das respostas 500.
