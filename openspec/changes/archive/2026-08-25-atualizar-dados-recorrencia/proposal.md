## Why

Hoje o `contratocommand` só permite mudar o **estado** de uma autorização (`criar`,
`cancelar`, `decisao`) — não existe caminho para corrigir ou renegociar **dados** de uma
recorrência já `ATIVA` (limite de crédito, data fim de vigência, uso de limite da conta,
quantidade de dívidas por ciclo) sem cancelar e recriar o contrato inteiro. Isso é uma
lacuna operacional real: qualquer ajuste desses 4 campos hoje exige um novo `POST`, o que
descarta o histórico da autorização original e gera um novo `id_autorizacao_empresa`.

## What Changes

- Novo endpoint `PATCH /api/autorizacoes/{idAutorizacao}/atualizar` no `contratocommand`,
  para atualização **parcial** de 4 campos de uma autorização `ATIVA`: `valorLimite`,
  `dataFimVigencia`, `indicadorUsoLimiteConta`, `quantidadeDividasCiclo`.
- Novo comando/porta `AtualizarDadosRecorrenciaCommand` / `AtualizarDadosRecorrenciaUseCase`
  e serviço `AtualizarDadosRecorrenciaService`, seguindo o mesmo padrão estrutural de
  `cancelar`/`decisao` (carrega por partição, valida via rules, aplica com
  `AutorizacaoPersistenceMapper.aplicarEm`, publica evento após commit).
- Novo framework de regras `AtualizacaoRule`/`AtualizacaoValidator`
  (`domain/service/atualizacao/`), com as rules: `TipoProdutoAtualizacao` (header vs.
  produto persistido), `StatusPermiteAtualizacao` (só `ATIVA`),
  `DataFimVigenciaInvalidaAtualizacao` (não pode ser no passado — duplica a regra
  homônima de criação, não reaproveita a classe), `ValorLimiteAtualizacaoInvalido` (novo:
  deve ser `> 0` quando informado — `valorLimite` nunca teve regra de negócio antes).
- Semântica de PATCH parcial: `null`/campo ausente = não altera aquele campo. Sem wrapper
  `JsonNullable` — mantém o padrão de `record` simples de `contratosrest/`.
- Request carrega `codigoCanalAtualizacao` + `idPessoaAtualizacao` (obrigatórios) para
  auditoria, mesmo padrão de `Cancelamento` e `DecidirAutorizacaoCommand`.
- Reaproveita a publicação de evento existente: como o status não muda (permanece
  `ATIVA`), `TipoEventoAutorizacao.porStatus` deriva `ATIVACAO` automaticamente — **sem**
  criar um `tipoEvento` novo (decisão explícita, ver design.md).

## Capabilities

### New Capabilities
- `atualizacao-dados-recorrencia`: PATCH parcial de `valorLimite`, `dataFimVigencia`,
  `indicadorUsoLimiteConta` e `quantidadeDividasCiclo` de uma autorização `ATIVA`, com
  validação por rules e auditoria de canal/pessoa.

### Modified Capabilities
- `publicacao-eventos-autorizacao`: o requirement "Evento publicado após commit de cada
  persistência" hoje enumera só criação/cancelamento/decisão como gatilhos — passa a
  incluir a atualização de dados da recorrência como quarto gatilho, publicando
  `tipoEvento=ATIVACAO` (derivado do status inalterado, sem novo valor de enum).

## Impact

- **Código afetado**: só `apps/contratocommand` — `domain/port/in/`,
  `domain/service/atualizacao/` (novo pacote), `application/usecase/`,
  `infrastructure/web/` (`AutorizacaoController`, novo DTO em `contratosrest/`).
- **Sem mudanças** em `contratoquery` (mesma tabela/colunas já expostas na leitura),
  `autorizacaostatus-producer`, `eventos-consumer` ou nos `.avsc` — os 4 campos e o
  `tipoEvento` reaproveitado já existem em todos os espelhos.
- **API**: novo endpoint público, aditivo — não quebra contrato existente.
- **Dependências**: nenhuma nova biblioteca.
