## ADDED Requirements

### Requirement: Módulo Java puro sem dependência de framework

O módulo `libs/srportto-commons-java` SHALL depender apenas de `java.*` em escopo de compilação
(dependência de teste, como JUnit, é permitida). Ele NÃO SHALL declarar dependência de Spring,
Spring Boot, Jakarta/Servlet ou qualquer outro framework de aplicação.

#### Scenario: Nenhuma dependência de framework no classpath de compilação

- **WHEN** o `pom.xml` de `libs/srportto-commons-java` é inspecionado
- **THEN** nenhuma dependência com escopo `compile` SHALL pertencer a `org.springframework*`,
  `jakarta.*` ou qualquer outro grupo de framework
- **AND** dependências de teste (ex.: JUnit) SHALL estar restritas ao escopo `test`

### Requirement: Critério de elegibilidade de classe para a lib compartilhada

Uma classe SHALL ser elegível para viver em `libs/srportto-commons-java` somente se **todas** as
condições abaixo forem verdadeiras: (1) não carrega regra de negócio específica de domínio de
autorização; (2) não participa de contrato de rede entre serviços (payload de evento SNS/SQS/Kafka,
schema Avro, enum de máquina de estados); (3) hoje existe como cópia idêntica (ou quase idêntica,
diferindo só em comentário/formatação) em duas ou mais apps do monorepo.

Classe que viole qualquer uma das três condições NÃO SHALL ser movida para a lib nesta ou em
mudanças futuras sem uma decisão de arquitetura própria que revise esse critério.

#### Scenario: Classe utilitária sem contrato de rede é elegível

- **WHEN** uma classe é cópia idêntica entre duas apps, não lança nem consome mensagem de
  broker e não representa regra de negócio de autorização
- **THEN** ela É elegível para a lib compartilhada

#### Scenario: Contrato de evento não é elegível mesmo que duplicado

- **WHEN** uma classe representa payload de evento (ex.: `AutorizacaoEventoPayload`) ou enum de
  máquina de estados (ex.: `StatusAutorizacao`, `TipoEventoAutorizacao`), mesmo que duplicada
  entre apps
- **THEN** ela NÃO É elegível para a lib compartilhada — permanece espelhada à mão

#### Scenario: Classe com dependência de framework não é elegível

- **WHEN** uma classe depende de `jakarta.servlet` ou de qualquer anotação Spring, mesmo sendo
  idêntica entre apps
- **THEN** ela NÃO É elegível para esta lib (que é Java puro por requisito)

### Requirement: Distribuição via GitHub Packages com versão fixa

A lib SHALL ser publicada como artefato Maven versionado no GitHub Packages do repositório. Toda
app consumidora SHALL declarar a dependência com versão exata (sem `RELEASE`, `LATEST` ou range).
Quando a versão consumida por `contratocommand` e `contratoquery` divergir, isso SHALL ser
resultado de uma decisão explícita de atualização em cada `pom.xml`, nunca de resolução automática
de range.

#### Scenario: Versão fixa nas apps consumidoras

- **WHEN** o `pom.xml` de `contratocommand` ou `contratoquery` é inspecionado
- **THEN** a dependência de `br.com.srportto:srportto-commons-java` declara uma `<version>`
  numérica exata

#### Scenario: Publicação exige o workflow dedicado

- **WHEN** um commit altera `libs/srportto-commons-java/**`
- **THEN** o workflow de publicação da lib SHALL executar `mvn deploy` para o GitHub Packages
- **AND** nenhum outro workflow (`contratocommand`, `contratoquery` ou demais apps) SHALL
  executar esse publish

### Requirement: CI das apps consumidoras resolve a dependência do GitHub Packages

Os workflows de testes unitários de `contratocommand` e `contratoquery` SHALL configurar
autenticação de leitura ao GitHub Packages (via `GITHUB_TOKEN` do próprio Actions, sem exigir
segredo adicional) antes de executar `mvn test`, de modo que a resolução da dependência
`br.com.srportto:srportto-commons-java` não falhe por falta de credencial.

#### Scenario: Testes de contratocommand resolvem a dependência da lib

- **WHEN** o workflow de testes unitários de `contratocommand` executa em um runner limpo (sem
  cache prévio de `~/.m2`)
- **THEN** a resolução de `br.com.srportto:srportto-commons-java` SHALL ter sucesso
- **AND** `mvn test` SHALL prosseguir sem erro de dependência não encontrada

#### Scenario: Mudança na lib dispara CI de quem a consome

- **WHEN** um commit altera exclusivamente `libs/srportto-commons-java/**`
- **THEN** o workflow de publicação da lib SHALL executar
- **AND** essa alteração, por si só, NÃO SHALL disparar automaticamente os workflows de
  `contratocommand`/`contratoquery` — a atualização da versão consumida é um passo manual e
  explícito no `pom.xml` de cada app (ver Requirement anterior), não implícito pelo publish

### Requirement: Nenhuma mudança de comportamento observável nas apps consumidoras

A migração de uma classe elegível para a lib compartilhada NÃO SHALL alterar mensagem de exceção,
mapeamento HTTP, nem qualquer resultado observável de `contratocommand` ou `contratoquery`. É
refactoring de local do código-fonte, não mudança funcional.

#### Scenario: Mensagens de erro idênticas antes e depois da migração

- **WHEN** `contratocommand` ou `contratoquery` lança `BusinessException` ou `ApplicationException`
  após a migração
- **THEN** a mensagem e o status HTTP mapeado pelo `ApiExceptionHandler` de cada app SHALL ser
  idênticos aos observados antes da migração

#### Scenario: Extração/geração de UUID particionado idêntica antes e depois

- **WHEN** `ReversibleUUIDv7.generate`/`extract` é chamado após a migração, com a mesma entrada
  usada antes
- **THEN** o resultado SHALL ser byte-a-byte idêntico ao comportamento anterior à migração
