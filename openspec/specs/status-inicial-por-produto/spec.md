# status-inicial-por-produto

## Purpose

Capacidade criada a partir da mudança `pix-auto-status-recebida-na-criacao`. Define que
o status inicial gravado na criação de uma autorização, no `contratocommand`, depende
do `tipoProduto` — não é um valor fixo para todos os produtos.

## Requirements

### Requirement: Status inicial na criação depende do produto

Ao criar uma autorização (`POST /api/autorizacoes`) no `contratocommand`, o status inicial persistido na coluna `status` SHALL depender do `tipoProduto` da requisição: `PIX_AUTO` SHALL nascer com status `RECEBIDA`; `DDA_AUTO` SHALL nascer com status `ATIVA`. A decisão SHALL ser tomada dentro de `Autorizacao.inicializaCriacao()` (ou método de domínio por ele chamado), consultando o `tipoProduto` já setado na própria entidade — não SHALL ser implementada como uma `ContratacaoRule` nova, já que rules rodam antes da entidade existir e servem apenas para validar/rejeitar, não para inicializar estado.

#### Scenario: Criação de PIX_AUTO nasce como RECEBIDA
- **WHEN** o sistema processa `POST /api/autorizacoes` com `tipoProduto: PIX_AUTO` e os demais dados válidos
- **THEN** o registro persistido em `autorizacoes` tem `status` correspondente a `RECEBIDA` (código 1)
- **AND** o `AutorizacaoCompletaResponseDto` retornado no 201 expõe `status: "RECEBIDA"`

#### Scenario: Criação de DDA_AUTO nasce como ATIVA
- **WHEN** o sistema processa `POST /api/autorizacoes` com `tipoProduto: DDA_AUTO` e os demais dados válidos
- **THEN** o registro persistido em `autorizacoes` tem `status` correspondente a `ATIVA` (código 4)
- **AND** o `AutorizacaoCompletaResponseDto` retornado no 201 expõe `status: "ATIVA"`

#### Scenario: motivoStatus permanece derivado só da jornada, independente do status inicial
- **WHEN** uma autorização `PIX_AUTO` é criada com `tipoJornada: SPI_J1` e nasce com status `RECEBIDA`
- **THEN** o campo `motivo_status` persistido é `RECEPCAO_SPI_J1`, exatamente como seria para qualquer outro produto na mesma jornada
