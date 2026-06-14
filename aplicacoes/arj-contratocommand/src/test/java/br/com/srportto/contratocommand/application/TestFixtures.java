package br.com.srportto.contratocommand.application;

import br.com.srportto.contratocommand.domain.enums.TipoJornadaAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CancelarAutorizacaoRequestDto;
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
            LocalDate dataFimVigencia, JsonNode metadados, TipoJornadaAutorizacao tipoJornada) {
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
                metadados,
                tipoJornada);
    }

    public static CriarAutorizacaoRequest criarRequestPix() {
        return criarRequest("PIX_AUTO", new BigDecimal("1000.00"), LocalDate.now().plusDays(30), null,
                TipoJornadaAutorizacao.SPI_J1);
    }

    public static CriarAutorizacaoRequest criarRequestDda() {
        return criarRequest("DDA_AUTO", new BigDecimal("1000.00"), LocalDate.now().plusDays(30), null,
                TipoJornadaAutorizacao.SPI_J1);
    }

    public static CancelarAutorizacaoRequestDto cancelarRequest(String idAutorizacao, TipoProduto produtoHeader) {
        return CancelarAutorizacaoRequestDto.builder()
                .idAutorizacao(idAutorizacao)
                .codigoCanalCancelamento("C1")
                .idPessoaCancelamento(UUID.randomUUID())
                .motivoCancelamento("teste cancelamento")
                .produtoHeaderRequest(produtoHeader)
                .build();
    }
}
