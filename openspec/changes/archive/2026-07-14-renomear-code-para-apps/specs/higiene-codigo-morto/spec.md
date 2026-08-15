## MODIFIED Requirements

### Requirement: Código de produção não contém classes sem referência de produção
Toda classe em `src/main` das aplicações `apps/contratocommand` e `apps/contratoquery` SHALL ter ao menos uma referência a partir de outro código de `src/main` (de qualquer arquivo que não ela mesma) ou ser um ponto de entrada reconhecido (classe de aplicação Spring Boot, `@RestController`, `@Entity`/`@Embeddable`/`@Converter` registrados, configuração). Referências vindas exclusivamente de `src/test` MUST NOT contar como uso de produção.

#### Scenario: Classes fantasma do fluxo de escrita removidas da query
- **WHEN** o módulo `contratoquery` é inspecionado após a limpeza
- **THEN** as classes `ContratoBase`, `TipoJornadaAutorizacao`, `CanaisConhecidosEnum`, `TipoConta`, `MotivoStatusAutorizacao`, `AchaQtdeSemanas`, `ControleExpurgoAutorizacao` e `IdContaUUIDPartitionDistributor` não existem em `src/main`

#### Scenario: Enums sem uso removidos da command
- **WHEN** o módulo `contratocommand` é inspecionado após a limpeza
- **THEN** as classes `CanaisConhecidosEnum` e `TipoConta` não existem em `src/main`

#### Scenario: Compilação e testes permanecem verdes
- **WHEN** `mvn test` é executado em cada módulo após as remoções
- **THEN** a compilação conclui sem erros e todos os testes passam
