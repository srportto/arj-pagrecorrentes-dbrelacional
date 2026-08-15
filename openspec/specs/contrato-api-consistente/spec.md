# contrato-api-consistente Specification

## Purpose
TBD - created by archiving change reconciliar-contrato-spec-doc. Update Purpose after archive.
## Requirements
### Requirement: Nomenclatura idêntica para o mesmo dado entre serviços

Os contratos REST do `contratocommand` e do `contratoquery` SHALL usar o mesmo nome de
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

- erro de formato (`@Valid`/`MethodArgumentNotValidException`) ou violação de regra de negócio:
  **422**, distinguidos pelo **shape** da resposta (`LayoutErrosApiValidationsResponse` com
  `occurrences` vs `LayoutErrosApiResponse` sem), não pelo status HTTP
- conflito de estado ou recurso já existente: **409**
- recurso não encontrado: **404**

> **Decisão D3 (2026-08-09, change `reconciliar-contrato-spec-doc`):** entrada inválida do
> cliente — tanto falha de formato quanto violação de regra de negócio — retorna 422. Esta spec
> chegou a documentar 400 para falha de formato; corrigido em 2026-08-11 (`enxugar-documentacao-repo`)
> após verificar por código e teste que os dois `ApiExceptionHandler`
> (`contratocommand`/`contratoquery`) mapeiam `MethodArgumentNotValidException` para
> `HttpStatus.UNPROCESSABLE_CONTENT` — `BAD_REQUEST` não aparece em nenhum dos dois arquivos.

#### Scenario: Violação de Bean Validation retorna 422

- **WHEN** uma requisição é rejeitada por violação de constraint de `@Valid`
  (`MethodArgumentNotValidException`)
- **THEN** o status SHALL ser 422 em ambos os serviços, no formato `LayoutErrosApiValidationsResponse`

#### Scenario: Violação de regra de negócio retorna 422

- **WHEN** uma requisição é rejeitada por `BusinessException`
- **THEN** o status SHALL ser 422 em ambos os serviços, no formato `LayoutErrosApiResponse`

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

### Requirement: Contrato de API não é documentado dentro do código dos serviços

Os dois serviços REST MUST NOT publicar contrato OpenAPI gerado a partir de anotações no código
de produção. A documentação de contrato (rotas, parâmetros, corpos de requisição e resposta,
status de erro por operação) pertence ao **gateway** — ver capacidade `doc-api-fora-do-codigo`.

> **Superseded (2026-08-11):** este requisito exigia publicação de OpenAPI derivado do código via
> springdoc. A change `limpar-codigo-das-apps` removeu springdoc e todas as anotações
> `io.swagger.v3.oas.*` dos dois serviços — decisão de que documentação de API não é
> responsabilidade do código de aplicação. Enquanto o gateway não absorve o contrato, o insumo de
> transição vive em `docs/contrato-api-para-gateway.md` (documento mantido manualmente, com prazo
> de validade — o oposto do que este requisito antes exigia). Reescrito para refletir a decisão
> vigente em vez de a anterior.

#### Scenario: Nenhum serviço expõe endpoint de documentação de API

- **WHEN** um dos dois serviços está em execução
- **THEN** `GET /v3/api-docs` e `GET /swagger-ui/**` SHALL responder 404

#### Scenario: Nenhuma anotação de documentação de API no código

- **WHEN** o código de produção dos dois serviços é inspecionado
- **THEN** nenhuma classe importa `io.swagger.v3.oas.annotations.*`
- **THEN** nenhum `pom.xml` declara dependência `org.springdoc`

#### Scenario: Status de erro documentados fora do código

- **WHEN** alguém precisa consultar os status de erro possíveis por operação
- **THEN** a fonte é o gateway (quando o contrato for absorvido) ou
  `docs/contrato-api-para-gateway.md` (enquanto não for), não uma anotação no controller

