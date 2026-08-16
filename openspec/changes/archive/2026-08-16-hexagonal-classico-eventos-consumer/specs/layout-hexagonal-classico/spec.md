## ADDED Requirements

### Requirement: Layout de pacotes em três camadas

Toda aplicação de `apps/` já migrada SHALL organizar seu código-fonte Java em exatamente três
pacotes de topo sob `br.com.srportto.<app>`: `domain`, `application` e `infrastructure`. Os pacotes
de topo `entrypoint` e `shared` do layout legado NÃO SHALL existir numa app migrada.

A distribuição SHALL seguir a skill `arquitetura-limpa-java`: `domain` para modelo, portas, enums,
serviços de domínio e exceções de negócio; `application` para as implementações de caso de uso; e
`infrastructure` para adaptadores (`web`, `messaging`, `persistence`, `external`) e configuração
(`config`).

#### Scenario: App migrada não tem pacote do layout legado

- **WHEN** a árvore de pacotes de uma app migrada é inspecionada
- **THEN** existem os pacotes de topo `domain`, `application` e `infrastructure`
- **AND** NÃO existem os pacotes de topo `entrypoint` nem `shared`

#### Scenario: Configuração de framework vive em infrastructure

- **WHEN** uma classe `@Configuration` ou `@ConfigurationProperties` de uma app migrada é localizada
- **THEN** ela reside em `infrastructure/config/`

### Requirement: Regra de dependência aponta sempre para dentro

As dependências entre camadas SHALL apontar exclusivamente para dentro: `infrastructure` pode
depender de `application` e `domain`; `application` pode depender de `domain`; `domain` NÃO SHALL
depender de nenhuma das outras duas.

Nenhuma classe de `domain` SHALL importar `org.springframework.*`, `jakarta.persistence.*`,
`org.apache.kafka.*`, SDK de nuvem ou biblioteca de serialização. Nenhuma classe de `application`
SHALL importar de `infrastructure`, nem tipos de transporte HTTP, JPA ou de SDK de broker.

#### Scenario: Domínio não conhece framework

- **WHEN** os imports de qualquer classe sob `domain/` de uma app migrada são inspecionados
- **THEN** nenhum deles referencia `org.springframework.*`, `jakarta.persistence.*` ou
  `org.apache.kafka.*`

#### Scenario: Application não conhece infraestrutura

- **WHEN** os imports de qualquer classe sob `application/` de uma app migrada são inspecionados
- **THEN** nenhum deles referencia o pacote `infrastructure` da própria app

#### Scenario: Application não devolve tipo de transporte

- **WHEN** a assinatura pública de uma implementação de caso de uso é inspecionada
- **THEN** nem os parâmetros nem o retorno são DTO de request/response HTTP, entidade JPA ou tipo de
  SDK de broker

### Requirement: Portas declaradas no domínio e adaptadores na infraestrutura

Todo caso de uso SHALL ser declarado como interface em `domain/port/in/` e implementado por uma
classe em `application/usecase/`. Todo recurso externo de que a aplicação precisa (repositório,
gateway, publicador, cliente HTTP) SHALL ser declarado como interface em `domain/port/out/` e
implementado por um adaptador em `infrastructure/`.

Um driving adapter SHALL injetar a interface da porta de entrada, nunca a classe que a implementa.
A interface e o adaptador que a implementa NÃO SHALL residir no mesmo pacote.

A exigência é estrutural e vale mesmo quando existe um único implementador: a uniformidade é o que
torna a regra de dependência verificável por inspeção, em vez de julgamento caso a caso.

#### Scenario: Caso de uso é interface no domínio

- **WHEN** um caso de uso de uma app migrada é localizado
- **THEN** existe uma interface correspondente em `domain/port/in/`
- **AND** a implementação reside em `application/usecase/`

#### Scenario: Driving adapter depende da porta

- **WHEN** um controller REST, listener SQS ou consumer Kafka de uma app migrada é inspecionado
- **THEN** o tipo declarado do campo injetado é a interface da porta de entrada
- **AND** não é a classe concreta que a implementa

#### Scenario: Porta de saída e adaptador ficam em pacotes distintos

- **WHEN** uma porta de saída de uma app migrada é localizada
- **THEN** a interface reside em `domain/port/out/`
- **AND** a implementação reside sob `infrastructure/`

### Requirement: Migração de layout preserva comportamento

Uma mudança cujo objetivo é migrar o layout de uma aplicação NÃO SHALL alterar comportamento
observável: rotas HTTP, portas de rede, tópicos, filas, group ids, contratos de mensagem, formato de
resposta ou chaves de configuração permanecem idênticos.

A suíte de testes da aplicação SHALL passar depois da migração com a **mesma contagem** de testes
executados de antes — nenhum teste é adicionado, removido ou silenciosamente pulado.

#### Scenario: Contagem de testes preservada

- **WHEN** `mvn test` é executado na app antes e depois da migração
- **THEN** a suíte passa nas duas execuções
- **AND** o número de testes executados é o mesmo

#### Scenario: Configuração externa não muda

- **WHEN** os arquivos `application.yaml` e `application-*.yaml` são comparados antes e depois
- **THEN** nenhuma chave de configuração foi renomeada, adicionada ou removida por causa da migração

### Requirement: eventos-consumer segue o layout hexagonal clássico

A aplicação `eventos-consumer` SHALL estar organizada em `domain` / `application` /
`infrastructure`, com o consumo do tópico Kafka atrás de uma porta de entrada.

#### Scenario: Árvore de pacotes do eventos-consumer

- **WHEN** `apps/eventos-consumer/src/main/java/br/com/srportto/eventosconsumer` é inspecionado
- **THEN** `domain/port/in/` contém a interface `ProcessarEventoAutorizacaoUseCase`
- **AND** `application/usecase/` contém `ProcessarEventoAutorizacaoService`, que a implementa
- **AND** `infrastructure/messaging/` contém `EventoAutorizacaoKafkaListener`
- **AND** `infrastructure/config/` contém `KafkaConsumerConfig` e `KafkaProperties`
- **AND** `domain/enums/` contém `StatusAutorizacao` e `TipoEventoAutorizacao`

#### Scenario: Listener Kafka depende da porta

- **WHEN** `EventoAutorizacaoKafkaListener` é inspecionado
- **THEN** o campo injetado é do tipo da interface `ProcessarEventoAutorizacaoUseCase`
- **AND** a classe `ProcessarEventoAutorizacaoService` não é referenciada por ele

#### Scenario: Consumo do tópico continua funcionando

- **WHEN** a app é executada contra o Kafka local e uma mensagem chega ao tópico configurado
- **THEN** o evento é processado e o `tipoEvento` derivado do status é logado
- **AND** o offset avança conforme o `AckMode.RECORD` já configurado
