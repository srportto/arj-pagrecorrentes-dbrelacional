## 1. Preparar contratoquery para JPA

- [x] 1.1 Adicionar `spring-boot-starter-data-jpa` e driver `postgresql` ao `pom.xml` do `contratoquery` (excluindo `spring-boot-starter-tomcat` permanece; manter Jetty)
- [x] 1.2 Configurar datasource em `application.yaml` do `contratoquery` com variáveis de ambiente `DB_NAME`, `DB_USER_NAME`, `DB_PASSWORD` e `hibernate.ddl-auto=none`
- [x] 1.3 Anotar `@Disabled` em `ContratoqueryApplicationTests` (contexto Spring não sobe sem PostgreSQL)

## 2. Copiar domínio necessário para contratoquery

- [x] 2.1 Copiar entidade `Autorizacao` (ajustar package para `br.com.srportto.contratoquery.domain.entities`)
- [x] 2.2 Copiar `IdAutorizacao` e `Cancelamento` para o mesmo pacote de entidades
- [x] 2.3 Copiar `ContratoBase` para `domain/model/`
- [x] 2.4 Copiar enums `StatusAutorizacao`, `TipoProduto`, `TipoConta`, `CanaisConhecidosEnum`, `MotivoStatusAutorizacao` para `domain/enums/`
- [x] 2.5 Copiar `TipoProdutoConverter` para `domain/converters/`
- [x] 2.6 Copiar utilities `ReversibleUUIDv7`, `IdContaUUIDPartitionDistributor`, `AchaQtdeSemanas`, `ControleExpurgoAutorizacao` para `domain/utilities/`

## 3. Implementar camada de repositório no contratoquery

- [x] 3.1 Criar `AutorizacaoQueryRepository` em `application/autorizacao/` estendendo `JpaRepository<Autorizacao, IdAutorizacao>` com os dois métodos de paginação: `findByIdUnicoContaContratante` e `findByIdUnicoContaContratanteAndStatusIn`

## 4. Implementar camada de aplicação no contratoquery

- [x] 4.1 Copiar `AutorizacaoResumidaResponseDto` para `entrypoint/contratosrest/` (ajustar package)
- [x] 4.2 Copiar `PaginacaoResponseDto` para `entrypoint/contratosrest/` (ajustar package)
- [x] 4.3 Criar `ListarAutorizacoesService` em `application/autorizacao/` adaptando referências para `AutorizacaoQueryRepository` do `contratoquery`

## 5. Implementar endpoint REST no contratoquery

- [x] 5.1 Criar (ou expandir) `AutorizacaoController` em `entrypoint/` com `GET /api/autorizacoes/listar` delegando para `ListarAutorizacoesService`
- [x] 5.2 Manter o endpoint `GET /olamundo` existente intacto

## 6. Testes no contratoquery

- [x] 6.1 Adaptar `ListarAutorizacoesServiceTest` para o package `br.com.srportto.contratoquery` com mock de `AutorizacaoQueryRepository`
- [x] 6.2 Executar `mvn clean package` no `contratoquery` e confirmar que todos os testes passam

## 7. Remover feature de listagem do contratocommand

- [x] 7.1 Verificar que `findByIdUnicoContaContratante` e `findByIdUnicoContaContratanteAndStatusIn` do `PixAutoRepository` não são usados fora de `ListarAutorizacoesService`
- [x] 7.2 Remover os dois métodos de paginação do `PixAutoRepository`
- [x] 7.3 Remover `ListarAutorizacoesService` do `contratocommand`
- [x] 7.4 Remover endpoint `GET /api/autorizacoes/listar` de `AutorizacaoController` e a dependência `listarAutorizacoesService` injetada
- [x] 7.5 Remover `AutorizacaoResumidaResponseDto` do `contratocommand`
- [x] 7.6 Remover `PaginacaoResponseDto` do `contratocommand`
- [x] 7.7 Remover `StatusAutorizacao` do `contratocommand` (não é usado fora do contexto de listagem)
- [x] 7.8 Remover `ListarAutorizacoesServiceTest` do `contratocommand`

## 8. Validação final

- [x] 8.1 Executar `mvn clean package` no `contratocommand` e confirmar build verde
- [x] 8.2 Executar `mvn clean package` no `contratoquery` e confirmar build verde
- [x] 8.3 Conferir no `CLAUDE.md` do `contratocommand` que a tabela de endpoints e as referências a `ListarAutorizacoesService` foram atualizadas
