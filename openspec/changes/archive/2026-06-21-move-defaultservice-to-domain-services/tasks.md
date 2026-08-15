## 1. Mover regras de negócio para `domain/services`

- [x] 1.1 Criar `src/main/.../domain/services/contratacao/rules` e `.../domain/services/cancelamento/rules`
- [x] 1.2 Mover de `application/defaultservice/contratacao` para `domain/services/contratacao`: `ContratacaoService`, `ContratacaoRule`, `ContratacaoValidator` e `rules/{ValorLimiteContrato, DataFimVigenciaInvalida, MetadadoRule}`
- [x] 1.3 Mover de `application/defaultservice/cancelamento` para `domain/services/cancelamento`: `CancelamentoService`, `CancelamentoRule`, `CancelamentoValidator`, `CancelamentoContext` e `rules/TipoProdutoCancelamento`

## 2. Mover orquestradores para `application/services`

- [x] 2.1 Criar `src/main/.../application/services/contratacao` e `.../application/services/cancelamento`
- [x] 2.2 Mover `ContratacaoOrquestradorService` → `application/services/contratacao` e `CancelamentoOrquestradorService` → `application/services/cancelamento`
- [x] 2.3 Remover os diretórios vazios `application/defaultservice/{contratacao,cancelamento}` e `application/defaultservice`

## 3. Mover classes de teste espelhadas

- [x] 3.1 Mover para `src/test/.../domain/services/contratacao`(`/rules`): `ContratacaoValidatorTest`, `rules/{ValorLimiteContratoTest, DataFimVigenciaInvalidaTest, MetadadoRuleTest}`
- [x] 3.2 Mover para `src/test/.../domain/services/cancelamento`(`/rules`): `CancelamentoValidatorTest`, `rules/TipoProdutoCancelamentoTest`
- [x] 3.3 Mover para `src/test/.../application/services/{contratacao,cancelamento}`: `ContratacaoOrquestradorServiceTest`, `CancelamentoOrquestradorServiceTest`
- [x] 3.4 Remover os diretórios de teste vazios sob `application/defaultservice`

## 4. Corrigir `package` e `import`

- [x] 4.1 Reescrever referências: `application.defaultservice.{contratacao|cancelamento}.{Contratacao|Cancelamento}OrquestradorService` → `application.services...`; todo o restante de `application.defaultservice` → `domain.services` (declarações `package` e `import`, em produção e teste)
- [x] 4.2 Ajustar explicitamente os 2 orquestradores: `package` para `application.services.{contratacao|cancelamento}` e adicionar import de `domain.services.contratacao.ContratacaoService` / `domain.services.cancelamento.{CancelamentoService, CancelamentoContext}`
- [x] 4.3 Ajustar explicitamente os 2 testes de orquestrador: `package` para `application.services.{...}` e adicionar os mesmos imports de `domain.services`
- [x] 4.4 Conferir consumidores: `AutorizacaoController`(+Test), `Criar/CancelarAutorizacaoUseCase`, `PixAutoService`, `DdaAutoService`, `TestFixtures`

## 5. Atualizar documentação

- [x] 5.1 Atualizar `docs/arquitetura/based-java-aplication.md` (árvore de pacotes e tabela/diagrama de camadas)
- [x] 5.2 Atualizar referências/links nos `README.md`, `CLAUDE.md` e `AGENTS.md` da app que citam `application/defaultservice/...`

## 6. Validação

- [x] 6.1 Rodar `mvn clean test` na app `contratocommand` e confirmar build verde com todos os testes passando (94 testes, 0 falhas)
- [x] 6.2 Buscar por `application\.defaultservice` em `src/` e confirmar zero ocorrências
- [x] 6.3 Confirmar que `application/defaultservice` não existe mais em `main` nem em `test`
