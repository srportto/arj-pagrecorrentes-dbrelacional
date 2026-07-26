# consumo-eventos-autorizacao — Delta

## MODIFIED Requirements

### Requirement: Consumo da fila via long polling com SDK v2

A aplicação SHALL consumir a fila `SQS-eventos-autorizacao` com o AWS SDK v2
(`SqsClient`), sem Spring Cloud AWS, em um loop de long polling
(`WaitTimeSeconds = 20`) executado em virtual thread iniciada por um componente de
ciclo de vida do Spring (`SmartLifecycle`), com encerramento gracioso no stop da
aplicação. O `ReceiveMessage` NÃO SHALL solicitar message attributes: o tipo do evento
é derivado pela ponte a partir do campo `status` do payload JSON
(`TipoEventoAutorizacao.porStatus`), tornando o attribute SQS `tipoEvento`
desnecessário para o processamento. Erros consecutivos de recebimento (ex.: Floci fora
do ar) SHALL aplicar backoff entre tentativas, com log claro da causa.

#### Scenario: Loop inicia e para com a aplicação
- **WHEN** a aplicação inicia
- **THEN** o loop de polling começa a receber mensagens da fila
- **AND** no shutdown da aplicação o loop encerra sem deixar thread pendurada

#### Scenario: Emulador indisponível
- **WHEN** o Floci está fora do ar durante o polling
- **THEN** a aplicação não encerra: loga o erro e tenta novamente após backoff

#### Scenario: Processamento independe do attribute tipoEvento
- **WHEN** uma mensagem é recebida — com ou sem o attribute SQS `tipoEvento`
- **THEN** o processamento segue normalmente, com o tipo do evento derivado do campo
  `status` do body pela ponte
