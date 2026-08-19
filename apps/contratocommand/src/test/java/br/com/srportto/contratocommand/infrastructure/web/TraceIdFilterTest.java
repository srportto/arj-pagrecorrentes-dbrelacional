package br.com.srportto.contratocommand.infrastructure.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("Testes do TraceIdFilter")
class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void limpaMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("Gera traceId quando o header X-Trace-Id está ausente")
    void geraTraceIdQuandoAusente() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var traceIdDuranteChain = new AtomicReference<String>();
        FilterChain chain = (req, res) -> traceIdDuranteChain.set(MDC.get("traceId"));

        filter.doFilter(request, response, chain);

        assertNotNull(traceIdDuranteChain.get());
        assertNull(MDC.get("traceId"), "MDC deve ser limpo após a requisição");
    }

    @Test
    @DisplayName("Reaproveita o traceId recebido no header X-Trace-Id")
    void reaproveitaTraceIdRecebido() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "trace-recebido-123");
        var response = new MockHttpServletResponse();
        var traceIdDuranteChain = new AtomicReference<String>();
        FilterChain chain = (req, res) -> traceIdDuranteChain.set(MDC.get("traceId"));

        filter.doFilter(request, response, chain);

        assertEquals("trace-recebido-123", traceIdDuranteChain.get());
        assertNull(MDC.get("traceId"));
    }

    @Test
    @DisplayName("Duas requisições consecutivas na mesma thread não compartilham traceId")
    void naoVazaTraceIdEntreRequisicoes() throws Exception {
        var primeiroTraceId = new AtomicReference<String>();
        FilterChain primeiraChain = (req, res) -> primeiroTraceId.set(MDC.get("traceId"));
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), primeiraChain);

        var segundoTraceId = new AtomicReference<String>();
        FilterChain segundaChain = (req, res) -> segundoTraceId.set(MDC.get("traceId"));
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), segundaChain);

        assertNotNull(primeiroTraceId.get());
        assertNotNull(segundoTraceId.get());
        assertEquals(false, primeiroTraceId.get().equals(segundoTraceId.get()));
    }
}
