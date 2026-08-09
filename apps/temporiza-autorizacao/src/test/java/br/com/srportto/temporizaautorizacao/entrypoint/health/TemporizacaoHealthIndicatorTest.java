package br.com.srportto.temporizaautorizacao.entrypoint.health;

import io.awspring.cloud.sqs.listener.MessageListenerContainer;
import io.awspring.cloud.sqs.listener.MessageListenerContainerRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Status;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do TemporizacaoHealthIndicator")
class TemporizacaoHealthIndicatorTest {

    @Mock
    private MessageListenerContainerRegistry registry;
    @Mock
    private RedisConnectionFactory redisConnectionFactory;
    @Mock
    private RedisConnection redisConnection;
    @Mock
    private MessageListenerContainer container;

    @Test
    @DisplayName("listener em execução + Valkey conectado -> UP")
    void tudoSaudavelRetornaUp() {
        when(registry.isRunning()).thenReturn(true);
        when(registry.getListenerContainers()).thenReturn(List.of(container));
        when(container.isRunning()).thenReturn(true);
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");

        var indicator = new TemporizacaoHealthIndicator(registry, redisConnectionFactory);

        assertEquals(Status.UP, indicator.health().getStatus());
    }

    @Test
    @DisplayName("container parado com registry ativo -> DOWN")
    void containerParadoRetornaDown() {
        when(registry.isRunning()).thenReturn(true);
        when(registry.getListenerContainers()).thenReturn(List.of(container));
        when(container.isRunning()).thenReturn(false);
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");

        var indicator = new TemporizacaoHealthIndicator(registry, redisConnectionFactory);

        assertEquals(Status.DOWN, indicator.health().getStatus());
    }

    @Test
    @DisplayName("registry parado (shutdown intencional) não é falha, mas Valkey indisponível é -> DOWN")
    void registryParadoMasValkeyFalhaRetornaDown() {
        when(registry.isRunning()).thenReturn(false);
        when(redisConnectionFactory.getConnection()).thenThrow(new RuntimeException("conexão recusada"));

        var indicator = new TemporizacaoHealthIndicator(registry, redisConnectionFactory);

        assertEquals(Status.DOWN, indicator.health().getStatus());
    }

    @Test
    @DisplayName("registry parado + Valkey conectado -> UP (parada intencional não é falha)")
    void registryParadoEValkeyOkRetornaUp() {
        when(registry.isRunning()).thenReturn(false);
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");

        var indicator = new TemporizacaoHealthIndicator(registry, redisConnectionFactory);

        assertEquals(Status.UP, indicator.health().getStatus());
    }

}
