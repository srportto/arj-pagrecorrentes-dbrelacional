package br.com.srportto.contratocommand.infrastructure.web;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** Popula traceId no MDC para toda a requisição; o campo aparece sozinho no log JSON (logstash). */
@Component
public class TraceIdFilter implements Filter {

    private static final String CABECALHO_TRACE_ID = "X-Trace-Id";
    private static final String CHAVE_MDC = "traceId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String recebido = ((HttpServletRequest) request).getHeader(CABECALHO_TRACE_ID);
        String traceId = (recebido != null && !recebido.isBlank()) ? recebido : UUID.randomUUID().toString();
        MDC.put(CHAVE_MDC, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            // MDC e por thread; sem clear() o valor vaza para a proxima requisicao no mesmo pool
            MDC.clear();
        }
    }
}
