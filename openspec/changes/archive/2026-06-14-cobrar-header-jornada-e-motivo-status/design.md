## Context

O sistema possui dois microserviços: `contratocommand` (escrita, porta 8080) e `contratoquery` (leitura, porta 8081). Ambos acessam a mesma tabela particionada `autorizacoes` em PostgreSQL.

O endpoint `POST /api/autorizacoes` aceita contratações para PIX Automático e DDA Automático, mas não recebe nem persiste a jornada de origem da autorização. O campo `motivo_status` é preenchido com texto literal genérico (`"Autorizacao criada com sucesso"`), sem valor de negócio. A query app não expõe `motivoStatus` em nenhum DTO de resposta.

As 4 jornadas existentes (`SPI_J1`, `QRC_J2`, `QRC_J3`, `QRC_J4`) estão modeladas no enum `TipoJornadaAutorizacao` do command. O enum `MotivoStatusAutorizacao` possui entradas correspondentes com mesmos códigos numéricos (1–4).

## Goals / Non-Goals

**Goals:**
- Tornar o header `tipoJornada` obrigatório no `POST /api/autorizacoes`, rejeitando requisições inválidas com HTTP 422.
- Persistir em `motivo_status` o nome do enum `MotivoStatusAutorizacao` correto para a jornada recebida.
- Expor `motivoStatus` nos dois DTOs de resposta da query app (`AutorizacaoDetalheResponseDto` e `AutorizacaoResumidaResponseDto`).
- Manter cobertura de testes existente nas duas aplicações.

**Non-Goals:**
- Migrar dados históricos em `motivo_status` (registros existentes permanecem com texto antigo).
- Adicionar coluna `tipo_jornada` no banco de dados.
- Alterar o fluxo de cancelamento.
- Modificar o valor persistido de `status` (inteiro `1 = ATIVO`).

## Decisions

### 1. `tipoJornada` entra via header HTTP, não no body

**Decisão:** `@RequestHeader String tipoJornada` no controller, convertido para enum antes de passar adiante.

**Rationale:** O padrão já existe no endpoint `PATCH /cancelar` (`@RequestHeader String tipoProduto`). Metadados de roteamento/jornada pertencem ao cabeçalho, não ao payload de negócio.

**Alternativa descartada:** Campo no body — misturaria protocolo com domínio e obrigaria clients a replicar informação de contexto de chamada no payload.

---

### 2. `tipoJornada` é adicionado ao record `CriarAutorizacaoRequest`

**Decisão:** O controller parseia o header para `TipoJornadaAutorizacao` e recria o record com o valor enum, passando-o adiante no fluxo. O campo fica tipado como `TipoJornadaAutorizacao` (não `String`) no record.

```
Controller recebe: @RequestHeader String tipoJornada
Controller valida: TipoJornadaAutorizacao.obterJornadaAutorizacaoEnumPorNome(tipoJornada)
Controller recria: new CriarAutorizacaoRequest(...campos existentes..., jornadaEnum)
```

**Rationale:** Evita criar DTO intermediário, mantém o padrão de records imutáveis já estabelecido (mesmo padrão de `tipoProduto` no cancelamento). O record com 15 campos é verboso mas consistente com o existente.

**Alternativa descartada:** Passar `tipoJornada` como parâmetro separado por toda a cadeia de chamadas — quebraria a interface dos Use Cases sem ganho real.

---

### 3. `motivoStatus` é setado no `@AfterMapping` dos mappers, não em `inicializaCriacao()`

**Decisão:** `PixAutoMapper.afterMapping()` e `DdaAutoMapper.afterMapping()` chamam `inicializaCriacao()` (que seta o valor padrão) e, em seguida, sobrescrevem `motivoStatus` com o nome do enum correto.

```java
autorizacao.inicializaCriacao(autorizacao); // seta padrões (id, timestamps, status)
var motivo = MotivoStatusAutorizacao.obterMotivoStatusEnumPorIdMotivo(
    request.tipoJornada().getCodigoJornada());
autorizacao.setMotivoStatus(motivo.name()); // ex: "RECEPCAO_SPI_J1"
```

**Rationale:** `inicializaCriacao()` é um método de domínio que inicializa invariantes da entidade. Acoplá-lo a `TipoJornadaAutorizacao` criaria dependência entre entidade de domínio e enum de jornada, que já está no mesmo pacote mas representa uma preocupação de mapeamento, não de entidade.

**Alternativa descartada:** Modificar `inicializaCriacao(autorizacao, tipoJornada)` — mudaria assinatura usada em ambos os mappers e no teste de entidade, com mais impacto.

---

### 4. Valor persistido é o nome do enum, não a descrição

**Decisão:** `autorizacao.setMotivoStatus(motivo.name())` → persiste `"RECEPCAO_SPI_J1"`, não `"Recepcao de PAIN.009 , jornada 1"`.

**Rationale:** Consistente com como cancelamentos são persistidos (nomes de enum). Nomes de enum são estáveis; descrições podem mudar. A query app expõe o valor diretamente — o client faz a tradução para exibição.

---

### 5. `obterJornadaAutorizacaoEnumPorNome()` adicionado ao enum; `IllegalArgumentException` corrigida

**Decisão:** Adicionar método `obterJornadaAutorizacaoEnumPorNome(String nome)` que lança `BusinessException` (não `IllegalArgumentException`) quando o valor não é reconhecido.

**Rationale:** `TipoProduto` já segue este padrão. `BusinessException` é tratada pelo `ApiExceptionHandler` com HTTP 422; `IllegalArgumentException` resultaria em 500.

---

### 6. Query app: expor `motivoStatus` nos dois DTOs

**Decisão:** Adicionar campo `String motivoStatus` em `AutorizacaoDetalheResponseDto` e `AutorizacaoResumidaResponseDto`, populados diretamente de `autorizacao.getMotivoStatus()`.

**Rationale:** Ambos os DTOs já têm `status` (código inteiro mapeado para enum). `motivoStatus` complementa o `status` com o contexto de jornada, útil tanto na listagem quanto no detalhe.

## Risks / Trade-offs

- **[Breaking change de API]** Clientes que não enviam `tipoJornada` no header receberão 422. → Comunicar aos consumers antes do deploy; Spring retorna 400 por missing required header antes mesmo de chegar ao negócio (pode ser preferível deixar o Spring lidar com o erro, sem lógica extra no controller para header ausente).
- **[Dados históricos]** `motivo_status` antigo (`"Autorizacao criada com sucesso"`) coexiste com o novo formato. → Aceitável; a query expõe o valor bruto sem interpretação.
- **[Testes — impacto amplo]** Adicionar campo ao record quebra ~10 arquivos de teste que criam `CriarAutorizacaoRequest` via `TestFixtures`. → Atualizar `TestFixtures.criarRequest()` resolve a maioria em cascata; ajustes pontuais nos testes de mapper e controller.
