## Why

`GET /api/autorizacoes/{autorizacaoId}` no `contratoquery` promete no próprio Javadoc do DTO
("Representação completa de uma autorização") devolver o estado completo da autorização, mas
`AutorizacaoDetalheResponseDto` omite silenciosamente 6 campos que existem no modelo de domínio
e na entidade JPA: `frequenciaPagamento`, `quantidadeDividasCiclo`, `indicadorUsoLimiteConta`,
`indicadorTipoMensageria`, `codigoCanalContratacao` e os dados de `cancelamento`. Um cliente que
consulta uma autorização cancelada, por exemplo, não recebe nenhum dado do cancelamento (canal,
pessoa, data, motivo) mesmo a linha os tendo persistidos — e não há como descobrir isso sem ler
o código-fonte, já que a resposta 200 não sinaliza campo ausente.

## What Changes

- `AutorizacaoDetalheResponseDto` (`GET /api/autorizacoes/{autorizacaoId}`) passa a incluir os 6
  campos hoje ausentes: `frequenciaPagamento`, `quantidadeDividasCiclo`,
  `indicadorUsoLimiteConta`, `indicadorTipoMensageria`, `codigoCanalContratacao` e
  `cancelamento` (objeto aninhado, `null` quando a autorização nunca foi cancelada).
- Novo `CancelamentoResponseDto` em `contratoquery` (record, espelha a estrutura do
  `CancelamentoResponseDto` já existente no `contratocommand`, com os 4 campos de
  `domain/model/Cancelamento`).
- **Fora de escopo, deliberadamente**: `tipoJornada` continua não exposto — é uma decisão de
  contrato de API já registrada como questão em aberto (`design.md` da change
  `temporizacao-jornada-01-pix-auto`), não um esquecimento, e não faz parte do problema relatado.
- **Fora de escopo, deliberadamente**: `AutorizacaoResumidaResponseDto` (listagem paginada,
  `GET /api/autorizacoes`) não muda — seu shape reduzido é contrato intencional da spec
  `listar-autorizacoes`, não "representação completa".
- **Fora de escopo, deliberadamente**: nenhuma mudança nos nomes já divergentes entre
  `contratocommand` e `contratoquery` (`valor`/`valorAutorizacao`, `dataCriacao`/`dataHoraInclusao`,
  `status` como `String`/`Integer`) — essa divergência é dívida aceita, documentada no `CLAUDE.md`
  raiz, com correção condicionada a gatilhos específicos (change `reconciliar-contrato-spec-doc`,
  D1) que não se aplicam aqui.

## Capabilities

### New Capabilities
(nenhuma)

### Modified Capabilities
- `consultar-autorizacao-por-id`: o requirement "Estrutura do DTO de detalhe da autorização"
  passa a listar os 6 campos adicionais como parte da representação completa.

## Impact

- **Código afetado**: só `apps/contratoquery` — `infrastructure/web/contratosrest/`
  (`AutorizacaoDetalheResponseDto`, novo `CancelamentoResponseDto`).
- **Sem mudança de schema**: os 6 campos já existem em `AutorizacaoJpaEntity`/`domain/model/Autorizacao`
  desde a criação da tabela — só não eram lidos pelo DTO.
- **API**: aditivo — campos novos numa resposta já existente, não remove nem renomeia nenhum
  campo atual. Nenhum cliente que ignora campos desconhecidos quebra.
- **Sem mudança** em `contratocommand`, `autorizacaostatus-producer`, `eventos-consumer`,
  `temporiza-autorizacao` nem nos `.avsc` — o escopo é só o shape de leitura do `contratoquery`.
- **Dependências**: nenhuma nova biblioteca.
