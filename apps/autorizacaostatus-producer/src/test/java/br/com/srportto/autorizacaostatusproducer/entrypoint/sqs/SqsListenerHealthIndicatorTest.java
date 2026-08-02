package br.com.srportto.autorizacaostatusproducer.entrypoint.sqs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do SqsListenerHealthIndicator")
class SqsListenerHealthIndicatorTest {

    @Mock
    private SqsEventoAutorizacaoListener listener;

    @Test
    @DisplayName("listener ativo com thread viva reporta UP")
    void ativoComThreadVivaReportaUp() {
        when(listener.isRunning()).thenReturn(true);
        when(listener.isThreadDePollingViva()).thenReturn(true);

        var health = new SqsListenerHealthIndicator(listener).health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("viva", health.getDetails().get("threadDePolling"));
    }

    @Test
    @DisplayName("listener ativo com thread morta reporta DOWN — outage não passa despercebido")
    void ativoComThreadMortaReportaDown() {
        when(listener.isRunning()).thenReturn(true);
        when(listener.isThreadDePollingViva()).thenReturn(false);

        var health = new SqsListenerHealthIndicator(listener).health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("morta", health.getDetails().get("threadDePolling"));
    }

    @Test
    @DisplayName("listener parado reporta UP — parada intencional (shutdown) não é falha")
    void paradoReportaUp() {
        when(listener.isRunning()).thenReturn(false);

        var health = new SqsListenerHealthIndicator(listener).health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("parado", health.getDetails().get("estado"));
    }

}
