package br.com.srportto.temporizaautorizacao.entrypoint.health;

import br.com.srportto.temporizaautorizacao.shared.config.TemporizacaoProperties;
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
import org.springframework.data.redis.core.StringRedisTemplate;

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
    @Mock
    private StringRedisTemplate redisTemplate;

    private final TemporizacaoProperties properties = new TemporizacaoProperties(
            10, 5000, 100, 120000, "agenda:{pixauto:j1}", "stream:{pixauto:j1}:expiracoes",
            "temporizaautorizacao", "worker-1", "http://localhost:8080", 5000, 600000);

    @Test
    @DisplayName("listener em execução + Valkey conectado -> UP")
    void tudoSaudavelRetornaUp() {
        when(registry.isRunning()).thenReturn(true);
        when(registry.getListenerContainers()).thenReturn(List.of(container));
        when(container.isRunning()).thenReturn(true);
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");

        var indicator = new TemporizacaoHealthIndicator(registry, redisConnectionFactory, redisTemplate, properties);

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

        var indicator = new TemporizacaoHealthIndicator(registry, redisConnectionFactory, redisTemplate, properties);

        assertEquals(Status.DOWN, indicator.health().getStatus());
    }

    @Test
    @DisplayName("registry parado (shutdown intencional) não é falha, mas Valkey indisponível é -> DOWN")
    void registryParadoMasValkeyFalhaRetornaDown() {
        when(registry.isRunning()).thenReturn(false);
        when(redisConnectionFactory.getConnection()).thenThrow(new RuntimeException("conexão recusada"));

        var indicator = new TemporizacaoHealthIndicator(registry, redisConnectionFactory, redisTemplate, properties);

        assertEquals(Status.DOWN, indicator.health().getStatus());
    }

    @Test
    @DisplayName("registry parado + Valkey conectado -> UP (parada intencional não é falha)")
    void registryParadoEValkeyOkRetornaUp() {
        when(registry.isRunning()).thenReturn(false);
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");

        var indicator = new TemporizacaoHealthIndicator(registry, redisConnectionFactory, redisTemplate, properties);

        assertEquals(Status.UP, indicator.health().getStatus());
    }

    @Test
    @DisplayName("contagem de consumidores indisponível (grupo ainda não existe) não é falha -> UP")
    void grupoAindaInexistenteNaoDerrubaHealth() {
        when(registry.isRunning()).thenReturn(false);
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");
        when(redisTemplate.opsForStream()).thenThrow(new RuntimeException("NOGROUP"));

        var indicator = new TemporizacaoHealthIndicator(registry, redisConnectionFactory, redisTemplate, properties);

        assertEquals(Status.UP, indicator.health().getStatus());
    }

}
