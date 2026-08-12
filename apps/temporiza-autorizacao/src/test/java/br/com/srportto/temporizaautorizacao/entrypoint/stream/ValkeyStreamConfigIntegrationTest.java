package br.com.srportto.temporizaautorizacao.entrypoint.stream;

import br.com.srportto.temporizaautorizacao.application.expiracao.ProcessarExpiracaoUseCase;
import br.com.srportto.temporizaautorizacao.shared.config.TemporizacaoProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Exige o Valkey local no ar (ver infra/local/redis). */
@DisplayName("Testes de integração: ValkeyStreamConfig contra o Valkey real")
class ValkeyStreamConfigIntegrationTest {

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    private final TemporizacaoProperties properties = new TemporizacaoProperties(
            10, 5000, 100, 120000,
            "test:agenda:" + UUID.randomUUID(), "test:stream:" + UUID.randomUUID(),
            "temporizaautorizacao", "worker-1", "http://localhost:8080", 5000, 600000);

    @BeforeAll
    static void subirConexao() {
        connectionFactory = new LettuceConnectionFactory("localhost", 6379);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void encerrarConexao() {
        connectionFactory.destroy();
    }

    @AfterEach
    void limparChaves() {
        redisTemplate.delete(properties.chaveStream());
    }

    @Test
    @DisplayName("cria o consumer group (idempotente) e registra a subscription, mesmo sem o stream existir ainda")
    void criaGrupoERegistraSubscription() {
        var config = new ValkeyStreamConfig(redisTemplate, new ConsumidorRemocaoService(redisTemplate, properties), properties);
        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                config.streamMessageListenerContainer(connectionFactory);

        try {
            var listener = new ExpiracaoStreamListener(
                    new ProcessarExpiracaoUseCase(id -> { }), redisTemplate, properties);

            var subscription = config.expiracaoStreamSubscription(container, redisTemplate, listener, properties);
            assertNotNull(subscription);

            // idempotente: criar de novo não lança (caminho BUSYGROUP)
            assertDoesNotThrow(() -> config.expiracaoStreamSubscription(container, redisTemplate, listener, properties));
        } finally {
            container.stop();
        }
    }

}
