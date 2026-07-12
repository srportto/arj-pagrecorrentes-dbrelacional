package br.com.srportto.contratocommand.application;

import br.com.srportto.contratocommand.application.cancelamento.CancelamentoContext;
import br.com.srportto.contratocommand.application.contratacao.ContratacaoContext;
import br.com.srportto.contratocommand.domain.enums.TipoJornadaAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CancelarAutorizacaoRequest;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CriarAutorizacaoRequest;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Fábricas de objetos para os testes unitários do contratocommand.
 */
public final class TestFixtures {

    private TestFixtures() {
    }

    public static CriarAutorizacaoRequest criarRequest(String tipoProduto, BigDecimal valor,
            LocalDate dataFimVigencia, JsonNode metadados) {
        return new CriarAutorizacaoRequest(
                dataFimVigencia,
                tipoProduto,
                valor,
                "EMP001",
                new BigDecimal("2000.00"),
                2,
                2,
                0,
                "C1",
                "descricao de teste",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                metadados);
    }

    public static CriarAutorizacaoRequest criarRequestPix() {
        return criarRequest("PIX_AUTO", new BigDecimal("1000.00"), LocalDate.now().plusDays(30), null);
    }

    public static CriarAutorizacaoRequest criarRequestDda() {
        return criarRequest("DDA_AUTO", new BigDecimal("1000.00"), LocalDate.now().plusDays(30), null);
    }

    public static ContratacaoContext criarContext(String tipoProduto, BigDecimal valor,
            LocalDate dataFimVigencia, JsonNode metadados, TipoJornadaAutorizacao tipoJornada) {
        return ContratacaoContext.doRequest(tipoJornada, criarRequest(tipoProduto, valor, dataFimVigencia, metadados));
    }

    public static ContratacaoContext criarContextPix() {
        return criarContext("PIX_AUTO", new BigDecimal("1000.00"), LocalDate.now().plusDays(30), null,
                TipoJornadaAutorizacao.SPI_J1);
    }

    public static ContratacaoContext criarContextDda() {
        return criarContext("DDA_AUTO", new BigDecimal("1000.00"), LocalDate.now().plusDays(30), null,
                TipoJornadaAutorizacao.SPI_J1);
    }

    public static CancelarAutorizacaoRequest cancelarDados() {
        return new CancelarAutorizacaoRequest("C1", UUID.randomUUID(), "teste cancelamento");
    }

    public static CancelamentoContext cancelarContext(String idAutorizacao, TipoProduto produtoHeader) {
        return CancelamentoContext.doRequest(idAutorizacao, produtoHeader, cancelarDados());
    }
}
