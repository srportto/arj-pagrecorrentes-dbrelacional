## ADDED Requirements

### Requirement: Dado pessoal e financeiro nunca é registrado em log

Nenhuma das quatro aplicações do monorepo SHALL registrar em log dado pessoal do titular ou valor
monetário. São dados não logáveis: `id_pessoa_pagadora`, `id_pessoa_devedora`,
`id_pessoa_recebedora`, `valor`, `valor_limite`, `descricao` e `metadados`.

Identificadores técnicos de agregado (`idAutorizacao`, `idAutorizacaoEmpresa`, `idParticaoConta`),
enums de negócio (`status`, `tipoEvento`, `tipoProduto`), contagens e durações SHALL permanecer
logáveis — são rastreamento, não dado do titular.

#### Scenario: Consumo de evento registra apenas identificadores

- **WHEN** o `eventos-consumer` processa um evento com sucesso
- **THEN** o log SHALL conter `idAutorizacao` e `tipoEvento`
- **AND** NÃO SHALL conter `valor`, `descricao`, `metadados` nem qualquer `id_pessoa_*`

#### Scenario: Nenhum campo sensível aparece nos logs dos quatro apps

- **WHEN** os pontos de log das quatro aplicações são inspecionados
- **THEN** nenhum deles SHALL registrar os campos listados como não logáveis

### Requirement: Objeto de domínio não é interpolado em log

Nenhuma chamada de log SHALL interpolar diretamente um objeto de domínio, record Avro, payload ou
DTO completo. Os campos a registrar SHALL ser citados nominalmente. Esta regra vale mesmo quando o
objeto, no momento da escrita, não contém campo sensível — um campo adicionado depois passaria a
vazar sem que ninguém reavaliasse a linha de log.

#### Scenario: Record Avro não é interpolado

- **WHEN** `ProcessarEventoAutorizacaoUseCase` do `eventos-consumer` é inspecionado
- **THEN** o log NÃO SHALL interpolar o record `EventoAutorizacao` completo
- **AND** SHALL citar nominalmente os campos registrados

#### Scenario: Campo novo no schema não vaza automaticamente

- **WHEN** um campo é adicionado ao schema `EventoAutorizacao`
- **THEN** ele NÃO SHALL passar a aparecer em log sem alteração explícita da linha de log

### Requirement: Resposta de erro interno não expõe detalhe de implementação

As respostas HTTP 500 de `ApplicationException` SHALL conter mensagem genérica nos serviços
`contratocommand` e `contratoquery`, sem nome de tabela, coluna, constraint, classe de
exceção, stack trace ou detalhe de infraestrutura. A mensagem da exceção NÃO SHALL ser
repassada diretamente ao cliente.

#### Scenario: Falha de acesso a dados não vaza estrutura do banco

- **WHEN** uma falha de acesso a dados resulta em `ApplicationException` e a resposta é
  inspecionada
- **THEN** o corpo NÃO SHALL conter nome de tabela, coluna ou constraint

#### Scenario: Erro de negócio mantém mensagem útil

- **WHEN** uma `BusinessException` é lançada
- **THEN** sua mensagem SHALL continuar sendo devolvida ao cliente sem alteração, por ser
  informação de negócio escrita para o cliente

### Requirement: Causa original preservada para diagnóstico

As exceções da aplicação SHALL oferecer construtor que aceite a causa (`Throwable`) — em
`contratocommand`: `ApplicationException` e `BusinessException` (não há
`ResourceNotFoundException` neste serviço); em `contratoquery`: `ApplicationException`,
`BusinessException` e `ResourceNotFoundException`. Todo ponto que encapsula uma exceção técnica
SHALL propagar a exceção original como causa, e o tratador SHALL registrar em log a cadeia
completa de causas com stack trace.

#### Scenario: Exceção encapsulada preserva a original

- **WHEN** uma falha inesperada ocorre no repositório durante o cancelamento e é encapsulada em
  `ApplicationException`
- **THEN** a exceção original SHALL constar como causa

#### Scenario: Log do servidor contém a falha real

- **WHEN** a `ApplicationException` encapsulada alcança o tratador
- **THEN** o log SHALL registrar o stack trace da falha original, não apenas o da exceção
  encapsuladora

#### Scenario: Sanitizar a resposta não reduz o diagnóstico

- **WHEN** a resposta ao cliente é sanitizada
- **THEN** o detalhe completo SHALL permanecer disponível no log do servidor
