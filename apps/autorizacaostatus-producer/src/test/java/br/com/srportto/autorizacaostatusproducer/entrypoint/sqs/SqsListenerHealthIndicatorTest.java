package br.com.srportto.autorizacaostatusproducer.entrypoint.sqs;

import io.awspring.cloud.sqs.listener.MessageListenerContainer;
import io.awspring.cloud.sqs.listener.MessageListenerContainerRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Status;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do SqsListenerHealthIndicator")
class SqsListenerHealthIndicatorTest {

    @Mock
    private MessageListenerContainerRegistry registry;

    @Mock
    private MessageListenerContainer<?> container;

    @Test
    @DisplayName("registry ativo com container em execução reporta UP")
    void ativoComContainerEmExecucaoReportaUp() {
        when(registry.isRunning()).thenReturn(true);
        when(container.isRunning()).thenReturn(true);
        when(registry.getListenerContainers()).thenReturn(List.of(container));

        var health = new SqsListenerHealthIndicator(registry).health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("em execucao", health.getDetails().get("container"));
    }

    @Test
    @DisplayName("registry ativo com container parado reporta DOWN — outage não passa despercebido")
    void ativoComContainerParadoReportaDown() {
        when(registry.isRunning()).thenReturn(true);
        when(container.isRunning()).thenReturn(false);
        when(registry.getListenerContainers()).thenReturn(List.of(container));

        var health = new SqsListenerHealthIndicator(registry).health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("parado", health.getDetails().get("container"));
    }

    @Test
    @DisplayName("registry parado reporta UP — parada intencional (shutdown) não é falha")
    void registryParadoReportaUp() {
        when(registry.isRunning()).thenReturn(false);

        var health = new SqsListenerHealthIndicator(registry).health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("parado", health.getDetails().get("estado"));
    }

}
