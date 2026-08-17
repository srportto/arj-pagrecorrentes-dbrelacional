## ADDED Requirements

### Requirement: Modelo de domínio expõe comportamento, não apenas campos mutáveis

Uma transição de estado de negócio SHALL ser expressa como um método do modelo de domínio nomeado
pela ação (`aprovar()`, `cancelar(...)`, `expirar...()`), não como uma sequência de setters chamada
de dentro de `application/usecase`. O método do modelo SHALL encapsular todo par de campos que muda
junto (ex.: `status` e o `motivoStatus` correspondente), de modo que seja estruturalmente impossível
gravar um `status` sem o `motivoStatus` que o acompanha.

Um caso de uso em `application/usecase` MAY decidir **qual** transição chamar (a partir da ação
recebida), mas NÃO SHALL montar o novo estado campo a campo.

#### Scenario: Aplicação de decisão delega ao modelo

- **WHEN** um caso de uso de decisão sobre autorização (aprovar, rejeitar, expirar) é inspecionado
- **THEN** ele chama um método do modelo de domínio nomeado pela ação de negócio
- **AND** não atribui `status`/`motivoStatus`/campo equivalente diretamente

#### Scenario: Par status+motivo não se dissocia

- **WHEN** o modelo de domínio é inspecionado
- **THEN** não existe um setter público de `status` que possa ser chamado sem o motivo
  correspondente

### Requirement: DTO de resposta não embute tipo de domain/model diretamente

Um DTO de resposta (HTTP em `infrastructure/web` ou payload em `infrastructure/messaging`) MUST NOT declarar um campo cujo tipo seja uma classe de `domain/model`. Todo dado do domínio exposto na borda SHALL passar por um tipo próprio da borda (DTO aninhado ou campo achatado), mapeado explicitamente.

#### Scenario: Campo composto do domínio vira DTO próprio na resposta

- **WHEN** o modelo de domínio tem um campo cujo tipo é outro objeto de domínio (ex.:
  `Autorizacao.cancelamento: Cancelamento`)
- **THEN** o DTO de resposta que expõe esse dado declara um tipo próprio de
  `infrastructure/web`/`infrastructure/messaging`, não o tipo de `domain/model`

#### Scenario: Renomear campo do domínio não quebra o contrato em silêncio

- **WHEN** um campo de `domain/model` é renomeado
- **THEN** a mudança não compila até o mapeamento explícito da borda ser atualizado — não é
  detectável apenas em runtime pela serialização
