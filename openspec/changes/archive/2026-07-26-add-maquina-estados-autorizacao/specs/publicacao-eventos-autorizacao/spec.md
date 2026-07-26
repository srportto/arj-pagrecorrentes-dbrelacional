# publicacao-eventos-autorizacao — Delta

## MODIFIED Requirements

### Requirement: Payload com a representação exata da tabela

O corpo do evento SHALL ser um JSON cujas chaves são os nomes das colunas da tabela
`autorizacoes` (ex.: `id_autorizacao`, `id_particao_conta`, `data_fim_vigencia`,
`tipo_produto`, `status`, `motivo_status`, `data_inicio_vigencia`,
`data_hora_inclusao`, `data_hora_ultima_atlz`, `valor`, `id_autorizacao_empresa`,
`valor_limite`, `frequencia`, `quantidade_dividas_ciclo`,
`indicador_uso_limite_conta`, `indicador_tipo_mensageria`,
`codigo_canal_contratacao`, `descricao`, `id_unico_conta_contratante`,
`id_pessoa_pagadora`, `id_pessoa_devedora`, `id_pessoa_recebedora`, colunas embutidas
de cancelamento e `metadados`), com os valores como persistidos. O mapeamento SHALL ser
feito por um record dedicado de payload (não pela serialização direta da entidade JPA).
O tipo do evento SHALL ser **derivado do `status` persistido da autorização** via
`TipoEventoAutorizacao.porStatus(status)` — não informado pelo use case — e publicado
como message attribute SNS (`tipoEvento`), mantendo o body como representação pura da
linha. O evento interno de persistência (`AutorizacaoPersistidaEvent`) NÃO SHALL
carregar campo de tipo de evento.

#### Scenario: Chaves espelham as colunas
- **WHEN** um evento de criação é publicado
- **THEN** o JSON contém as chaves com os nomes das colunas da tabela e os valores da
  linha persistida
- **AND** `metadados` aparece como objeto JSON (não como string escapada)

#### Scenario: Criação publica tipo derivado do status ATIVA
- **WHEN** um POST `/api/autorizacoes` é concluído com sucesso (linha persistida com
  status `ATIVA`)
- **THEN** a mensagem SNS carrega o attribute `tipoEvento` com valor `ATIVACAO`

#### Scenario: Cancelamento publica tipo derivado do status CANCELADA
- **WHEN** um PATCH `/api/autorizacoes/{id}/cancelar` é concluído com sucesso (linha
  persistida com status `CANCELADA`)
- **THEN** a mensagem SNS carrega o attribute `tipoEvento` com valor `CANCELAMENTO`

#### Scenario: Attribute sempre coerente com o body
- **WHEN** qualquer evento é publicado
- **THEN** o valor do attribute `tipoEvento` é igual a
  `TipoEventoAutorizacao.porStatus(status)` para o campo `status` presente no body
