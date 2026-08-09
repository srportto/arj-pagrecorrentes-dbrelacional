## ADDED Requirements

### Requirement: Nenhuma exceção escapa do contrato de erro

Todo serviço que expõe API REST SHALL possuir tratador para exceções não previstas
(`@ExceptionHandler(Exception.class)`), garantindo que qualquer falha inesperada resulte em
resposta no formato `LayoutErrosApiResponse`. Nenhuma exceção SHALL alcançar o tratamento default
do container.

#### Scenario: Exceção não prevista devolve corpo estruturado

- **WHEN** uma exceção não mapeada explicitamente ocorre durante o processamento de uma requisição
  no `arj-contratoquery`
- **THEN** a resposta SHALL ter status 500 e corpo no formato `LayoutErrosApiResponse`
- **AND** NÃO SHALL ser a página de erro default do container

#### Scenario: Cobertura presente nos dois serviços REST

- **WHEN** os `ApiExceptionHandler` do `arj-contratocommand` e do `arj-contratoquery` são
  inspecionados
- **THEN** ambos SHALL declarar tratador para exceção não prevista

### Requirement: Resposta de erro não expõe detalhe interno

A resposta de erro para exceção não prevista SHALL conter mensagem genérica, sem nome de classe de
exceção, stack trace, nome de tabela, nome de coluna, nome de constraint ou detalhe de
infraestrutura. O detalhe completo SHALL ser registrado em log do servidor, com stack trace, para
permitir diagnóstico.

#### Scenario: Mensagem ao cliente é genérica

- **WHEN** uma exceção inesperada de acesso a dados ocorre e a resposta é inspecionada
- **THEN** o corpo NÃO SHALL conter nome de classe de exceção, nome de tabela ou de coluna

#### Scenario: Diagnóstico preservado no servidor

- **WHEN** a mesma exceção ocorre
- **THEN** o log do servidor SHALL registrar a exceção completa com stack trace

#### Scenario: Erros de negócio mantêm mensagem útil

- **WHEN** uma `BusinessException` é lançada
- **THEN** sua mensagem SHALL continuar sendo devolvida ao cliente, sem alteração do comportamento
  atual, por ser mensagem de negócio deliberada
