## Why

A auditoria multi-agente de 2026-08-04 encontrou três lacunas no fluxo de escrita do
`arj-contratocommand` que, isoladas, parecem administráveis — mas combinadas produzem dois
cenários concretos de dano financeiro em autorizações de pagamento recorrente:

- **Cancelamento duplicado.** `Autorizacao` não tem `@Version`, o `CancelarAutorizacaoUseCase`
  lê por `findByIdAutorizacaoAndParticao` (SELECT sem lock) e a única rule de cancelamento
  (`TipoProdutoCancelamento`) compara apenas o produto — nunca o status atual. Dois PATCH
  concorrentes leem `ATIVA`, ambos passam, ambos gravam: o segundo commit sobrescreve os dados
  de cancelamento do primeiro (canal, pessoa, motivo trocados) e **dois eventos `CANCELAMENTO`
  são publicados no SNS** para a mesma autorização.

- **Autorização duplicada.** `id_autorizacao_empresa` é a chave de negócio vinda do sistema da
  empresa, mas está `unique = false` na entidade e sem constraint na migration, e o
  `CriarAutorizacaoUseCase` não checa duplicidade. Como o `idAutorizacao` é gerado internamente
  via UUIDv7, um retry de POST após timeout de rede — rotina em integração bancária — cria uma
  **segunda autorização ativa** para o mesmo contrato, com risco de débito duplicado.

O que torna isto tratável agora: **a proteção contra o primeiro cenário já existe no código.**
`StatusAutorizacao.podeTransicionarPara` está implementado nas 4 aplicações, testado, e
especificado em `maquina-estados-autorizacao` como fonte da verdade do ciclo de vida — mas não é
chamado por nenhuma linha de produção em nenhum dos serviços. Quatro agentes independentes da
auditoria chegaram a esse mesmo método por caminhos diferentes. Esta mudança é, em boa parte,
plugar uma proteção que já foi construída e nunca foi ligada.

## What Changes

- Adicionar `@Version` em `Autorizacao` (`arj-contratocommand`), habilitando lock otimista — duas
  escritas concorrentes sobre a mesma linha passam a resultar em `OptimisticLockException` para a
  segunda, em vez de sobrescrita silenciosa.
- Criar a rule `TransicaoStatusValida` no `CancelamentoValidator`, que consulta
  `podeTransicionarPara` antes de permitir o cancelamento — rejeita cancelar autorização já
  `CANCELADA`, `REJEITADA`, `EXPIRADA` ou `FINALIZADA`.
- Mapear `OptimisticLockException` e a violação de transição para respostas de erro do contrato
  (`LayoutErrosApiResponse`) em vez de 500 — conflito de concorrência é erro esperado, não falha
  interna.
- Adicionar constraint `UNIQUE` em `id_autorizacao_empresa` via migration, trocar
  `unique = false` para `unique = true` na entidade, e checar duplicidade no
  `CriarAutorizacaoUseCase` antes do `save` (`existsByIdAutorizacaoEmpresa`), devolvendo erro de
  contrato quando já existe.
- **BREAKING (dados):** a migration de `UNIQUE` falha se já existirem duplicatas na base. A
  detecção e o tratamento de duplicatas preexistentes fazem parte do escopo (ver `design.md`).
- **BREAKING (contrato):** POST com `id_autorizacao_empresa` já existente passa a ser rejeitado
  em vez de criar uma segunda autorização. Clientes que hoje dependem (mesmo sem saber) do
  comportamento de criar duplicata terão respostas diferentes.
- **Fora de escopo (deliberado):** header `Idempotency-Key` com armazenamento de resposta para
  replay. A constraint na chave de negócio já elimina o cenário de duplicação; o
  `Idempotency-Key` completo (que também devolve a resposta original no retry) é uma capacidade
  maior e independente, que pode vir depois sobre esta base.
- **Fora de escopo:** aplicar `podeTransicionarPara` no `CriarAutorizacaoUseCase`. A criação
  parte de estado inexistente, então não há transição a validar — o ganho seria marginal frente ao
  raio adicional.
- **Fora de escopo:** mover `TipoEventoAutorizacao` de `application/eventos/` para `domain/enums/`
  no command (drift real contra a spec, apontado na auditoria). Fica na proposta
  `reconciliar-contrato-spec-doc`, junto com os demais drifts de documentação e localização.

## Capabilities

### New Capabilities

- `concorrencia-otimista-autorizacao`: como escritas concorrentes sobre a mesma autorização são
  detectadas e rejeitadas — lock otimista via `@Version` e o contrato de erro correspondente.
- `idempotencia-criacao-autorizacao`: como a criação de autorização é protegida contra retry de
  rede — unicidade da chave de negócio no banco, checagem na aplicação e contrato de erro para
  tentativa duplicada.

### Modified Capabilities

- `maquina-estados-autorizacao`: a capacidade hoje especifica que o grafo de transições SHALL
  existir nas 4 aplicações, mas nada exige que ele seja **aplicado**. Passa a exigir que o fluxo
  de escrita do `arj-contratocommand` consulte `podeTransicionarPara` antes de persistir mudança
  de status, tornando o grafo normativo em runtime e não apenas declarativo.

## Impact

- **Código afetado (`arj-contratocommand`):**
  `domain/entities/Autorizacao.java` (campo `@Version`, `unique = true`),
  `application/cancelamento/CancelarAutorizacaoUseCase.java`,
  nova rule em `application/cancelamento/rules/TransicaoStatusValida.java`,
  `application/contratacao/CriarAutorizacaoUseCase.java`,
  `application/AutorizacaoRepository.java` (`existsByIdAutorizacaoEmpresa`),
  `shared/interceptors/api/ApiExceptionHandler.java` (mapeamento de `OptimisticLockException`).
- **Banco:** nova migration adicionando coluna de versão e constraint `UNIQUE` em
  `id_autorizacao_empresa`. Requer verificação prévia de duplicatas existentes.
- **`arj-contratoquery`:** a coluna de versão passa a existir na tabela compartilhada. A entidade
  de leitura precisa mapeá-la ou ignorá-la explicitamente — verificar que a leitura não quebra.
- **Contrato de API:** dois novos caminhos de erro (conflito de concorrência e criação duplicada)
  precisam de código de status e corpo definidos, e de documentação no `README.md`.
- **Eventos:** ao rejeitar o segundo cancelamento concorrente, o segundo evento `CANCELAMENTO`
  deixa de ser publicado no SNS — corrige também o efeito a jusante no `autorizacaostatus-producer`
  e no `eventos-consumer`.
- **Testes:** exigem cobertura de concorrência real (duas transações competindo), não apenas
  mocks — hoje `CancelarAutorizacaoUseCaseTest` mocka o repositório inteiro.
