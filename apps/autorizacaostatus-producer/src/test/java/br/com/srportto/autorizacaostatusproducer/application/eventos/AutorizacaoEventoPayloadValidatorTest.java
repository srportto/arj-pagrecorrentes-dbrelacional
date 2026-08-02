package br.com.srportto.autorizacaostatusproducer.application.eventos;

import br.com.srportto.autorizacaostatusproducer.shared.exceptions.EventoAutorizacaoInvalidoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Testes do AutorizacaoEventoPayloadValidator")
class AutorizacaoEventoPayloadValidatorTest {

    private static final UUID ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final LocalDate DATA = LocalDate.of(2027, 1, 1);
    private static final LocalDateTime DATA_HORA = LocalDateTime.of(2026, 7, 26, 10, 0);

    private final AutorizacaoEventoPayloadValidator validator = new AutorizacaoEventoPayloadValidator();

    /** Monta um payload com os 8 campos obrigatórios do .avsc parametrizáveis; os anuláveis ficam nulos. */
    private AutorizacaoEventoPayload payload(UUID idAutorizacao, Integer idParticaoConta,
            LocalDate dataFimVigencia, Long tipoProduto, Integer status,
            LocalDateTime dataHoraInclusao, LocalDateTime dataHoraUltimaAtlz, String codigoCanalContratacao) {
        return new AutorizacaoEventoPayload(idAutorizacao, idParticaoConta, dataFimVigencia, tipoProduto,
                status, null, null, dataHoraInclusao, dataHoraUltimaAtlz, null, null, null, null, null,
                null, null, codigoCanalContratacao, null, null, null, null, null, null, null, null, null, null);
    }

    private AutorizacaoEventoPayload payloadCompleto() {
        return payload(ID, 950, DATA, 1L, 4, DATA_HORA, DATA_HORA, "canal");
    }

    @Test
    @DisplayName("payload com todos os campos obrigatórios passa na validação")
    void payloadCompletoPassa() {
        assertDoesNotThrow(() -> validator.validar(payloadCompleto()));
    }

    @Test
    @DisplayName("id_autorizacao nulo é rejeitado nomeando o campo")
    void idAutorizacaoNuloRejeitado() {
        var e = assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> validator.validar(payload(null, 950, DATA, 1L, 4, DATA_HORA, DATA_HORA, "canal")));

        assertTrue(e.getMessage().contains("id_autorizacao"));
    }

    @Test
    @DisplayName("id_particao_conta nulo é rejeitado nomeando o campo")
    void idParticaoContaNuloRejeitado() {
        var e = assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> validator.validar(payload(ID, null, DATA, 1L, 4, DATA_HORA, DATA_HORA, "canal")));

        assertTrue(e.getMessage().contains("id_particao_conta"));
    }

    @Test
    @DisplayName("data_fim_vigencia nula é rejeitada nomeando o campo")
    void dataFimVigenciaNulaRejeitada() {
        var e = assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> validator.validar(payload(ID, 950, null, 1L, 4, DATA_HORA, DATA_HORA, "canal")));

        assertTrue(e.getMessage().contains("data_fim_vigencia"));
    }

    @Test
    @DisplayName("tipo_produto nulo é rejeitado nomeando o campo")
    void tipoProdutoNuloRejeitado() {
        var e = assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> validator.validar(payload(ID, 950, DATA, null, 4, DATA_HORA, DATA_HORA, "canal")));

        assertTrue(e.getMessage().contains("tipo_produto"));
    }

    @Test
    @DisplayName("status nulo é rejeitado nomeando o campo")
    void statusNuloRejeitado() {
        var e = assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> validator.validar(payload(ID, 950, DATA, 1L, null, DATA_HORA, DATA_HORA, "canal")));

        assertTrue(e.getMessage().contains("status"));
    }

    @Test
    @DisplayName("data_hora_inclusao nula é rejeitada nomeando o campo")
    void dataHoraInclusaoNulaRejeitada() {
        var e = assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> validator.validar(payload(ID, 950, DATA, 1L, 4, null, DATA_HORA, "canal")));

        assertTrue(e.getMessage().contains("data_hora_inclusao"));
    }

    @Test
    @DisplayName("data_hora_ultima_atlz nula é rejeitada nomeando o campo")
    void dataHoraUltimaAtlzNulaRejeitada() {
        var e = assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> validator.validar(payload(ID, 950, DATA, 1L, 4, DATA_HORA, null, "canal")));

        assertTrue(e.getMessage().contains("data_hora_ultima_atlz"));
    }

    @Test
    @DisplayName("codigo_canal_contratacao nulo ou em branco é rejeitado nomeando o campo")
    void codigoCanalContratacaoNuloRejeitado() {
        var nulo = assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> validator.validar(payload(ID, 950, DATA, 1L, 4, DATA_HORA, DATA_HORA, null)));
        var branco = assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> validator.validar(payload(ID, 950, DATA, 1L, 4, DATA_HORA, DATA_HORA, "   ")));

        assertTrue(nulo.getMessage().contains("codigo_canal_contratacao"));
        assertTrue(branco.getMessage().contains("codigo_canal_contratacao"));
    }

    @Test
    @DisplayName("vários campos ausentes são reportados juntos, numa única exceção")
    void variosCamposAusentesReportadosJuntos() {
        var e = assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> validator.validar(payload(null, null, DATA, 1L, 4, DATA_HORA, DATA_HORA, null)));

        assertTrue(e.getMessage().contains("id_autorizacao"));
        assertTrue(e.getMessage().contains("id_particao_conta"));
        assertTrue(e.getMessage().contains("codigo_canal_contratacao"));
    }

    @Test
    @DisplayName("payload nulo é rejeitado sem NullPointerException")
    void payloadNuloRejeitado() {
        assertThrows(EventoAutorizacaoInvalidoException.class, () -> validator.validar(null));
    }

    /** Monta um payload completo trocando apenas valor e valor_limite. */
    private AutorizacaoEventoPayload comDecimais(BigDecimal valor, BigDecimal valorLimite) {
        return new AutorizacaoEventoPayload(ID, 950, DATA, 1L, 4, null, null, DATA_HORA, DATA_HORA,
                valor, null, valorLimite, null, null, null, null, "canal", null, null, null, null,
                null, null, null, null, null, null);
    }

    @Test
    @DisplayName("valor dentro de decimal(17,2) passa — 15 dígitos inteiros é o limite")
    void valorNoLimiteDaPrecisaoPassa() {
        assertDoesNotThrow(() -> validator.validar(
                comDecimais(new BigDecimal("999999999999999.99"), null)));
    }

    @Test
    @DisplayName("valor acima de decimal(17,2) é rejeitado antes do send — não vira SerializationException")
    void valorAcimaDaPrecisaoRejeitado() {
        var e = assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> validator.validar(comDecimais(new BigDecimal("1000000000000000.00"), null)));

        assertTrue(e.getMessage().contains("valor"));
        assertTrue(e.getMessage().contains("precisão"));
    }

    @Test
    @DisplayName("valor_limite acima de decimal(17,2) também é rejeitado")
    void valorLimiteAcimaDaPrecisaoRejeitado() {
        var e = assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> validator.validar(comDecimais(null, new BigDecimal("12345678901234567890.55"))));

        assertTrue(e.getMessage().contains("valor_limite"));
    }

    @Test
    @DisplayName("decimais nulos não disparam a validação de precisão (são campos anuláveis)")
    void decimaisNulosNaoDisparamValidacao() {
        assertDoesNotThrow(() -> validator.validar(comDecimais(null, null)));
    }

}
