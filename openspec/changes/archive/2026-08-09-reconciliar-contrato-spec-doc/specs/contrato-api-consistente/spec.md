## ADDED Requirements

### Requirement: Nomenclatura idêntica para o mesmo dado entre serviços

Os contratos REST do `arj-contratocommand` e do `arj-contratoquery` SHALL usar o mesmo nome de
campo para o mesmo dado da autorização. Um cliente que crie pelo command e consulte pelo query NÃO
SHALL precisar de mapeamentos distintos para a mesma entidade.

#### Scenario: Campos equivalentes têm nomes idênticos

- **WHEN** os DTOs de resposta dos dois serviços são comparados
- **THEN** valor, data de criação e data de atualização SHALL usar o mesmo nome nos dois contratos

#### Scenario: Novo campo é introduzido de forma coerente

- **WHEN** um campo é adicionado ao contrato de um dos serviços e o mesmo dado existe no outro
- **THEN** o nome SHALL ser idêntico nos dois

### Requirement: Formato único por tipo de campo

Um mesmo campo SHALL ter o mesmo tipo e formato em todos os contratos que o expõem. O campo
`status` SHALL ser exposto como nome do enum (`"ATIVA"`), nunca como código numérico, em ambos os
serviços.

#### Scenario: Status como nome do enum nos dois serviços

- **WHEN** uma autorização com status `ATIVA` é retornada por qualquer endpoint de qualquer um dos
  dois serviços
- **THEN** o campo `status` SHALL conter a string `"ATIVA"`

#### Scenario: Código numérico não é exposto

- **WHEN** os DTOs de resposta dos dois serviços são inspecionados
- **THEN** nenhum SHALL expor `status` como inteiro

### Requirement: Convenção única de status HTTP por origem de erro

Os dois serviços SHALL aplicar a mesma correspondência entre origem do erro e status HTTP, e a
documentação SHALL descrever exatamente o que o código faz:

- erro de formato ou violação de Bean Validation (`@Valid`): **400**
- violação de regra de negócio: **422**
- conflito de estado ou recurso já existente: **409**
- recurso não encontrado: **404**

#### Scenario: Violação de Bean Validation retorna 400

- **WHEN** uma requisição é rejeitada por violação de constraint de `@Valid`
- **THEN** o status SHALL ser 400 em ambos os serviços

#### Scenario: Violação de regra de negócio retorna 422

- **WHEN** uma requisição é rejeitada por `BusinessException`
- **THEN** o status SHALL ser 422 em ambos os serviços

#### Scenario: Documentação corresponde ao código

- **WHEN** o `README.md` e os `CLAUDE.md` são comparados ao comportamento dos handlers
- **THEN** os status documentados SHALL corresponder aos status efetivamente retornados

### Requirement: Versionamento de API antes de mudança incompatível

Os contratos REST SHALL adotar estratégia de versionamento explícita. Nenhuma mudança incompatível
de contrato SHALL ser aplicada sem que exista caminho de migração que permita ao cliente continuar
operando na versão anterior durante um período de convivência definido.

#### Scenario: Versão explícita no contrato

- **WHEN** os endpoints dos dois serviços são inspecionados
- **THEN** a versão do contrato SHALL ser identificável pelo cliente

#### Scenario: Renomeação não quebra cliente da versão anterior

- **WHEN** um campo é renomeado numa nova versão do contrato
- **THEN** clientes da versão anterior SHALL continuar recebendo o nome antigo durante o período de
  convivência

#### Scenario: Prazo de descontinuação definido

- **WHEN** uma nova versão é introduzida
- **THEN** o prazo de descontinuação da anterior SHALL estar documentado

### Requirement: Contrato OpenAPI publicado e derivado do código

Os dois serviços REST SHALL publicar contrato OpenAPI gerado a partir do código, não mantido como
documento independente. O contrato SHALL refletir rotas, parâmetros, corpos de requisição e
resposta, e os status de erro efetivamente retornados.

#### Scenario: Contrato disponível para os dois serviços

- **WHEN** cada serviço está em execução
- **THEN** SHALL expor contrato OpenAPI correspondente aos seus endpoints

#### Scenario: Contrato acompanha o código

- **WHEN** um endpoint ou DTO é alterado
- **THEN** o contrato gerado SHALL refletir a alteração sem edição manual de documento separado

#### Scenario: Status de erro documentados

- **WHEN** o contrato OpenAPI é inspecionado
- **THEN** SHALL declarar os status de erro possíveis por operação, coerentes com a convenção única
