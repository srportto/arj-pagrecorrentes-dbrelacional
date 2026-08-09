package br.com.srportto.temporizaautorizacao.entrypoint.health;

import io.awspring.cloud.sqs.listener.MessageListenerContainerRegistry;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Reflete no /actuator/health o estado do consumo da fila SQS (via
 * {@link MessageListenerContainerRegistry}, mesmo padrão do autorizacaostatus-producer) e a
 * conexão com o Valkey (via {@code PING}). Listener parado durante shutdown intencional não é
 * falha; falha de PING no Valkey é — sem ele, nem o agendamento nem a varredura funcionam.
 */
@Component("temporizacao")
public class TemporizacaoHealthIndicator implements HealthIndicator {

    private final MessageListenerContainerRegistry registry;
    private final RedisConnectionFactory redisConnectionFactory;

    public TemporizacaoHealthIndicator(MessageListenerContainerRegistry registry,
            RedisConnectionFactory redisConnectionFactory) {
        this.registry = registry;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Override
    public Health health() {
        var builder = Health.up();
        boolean saudavel = true;

        if (registry.isRunning()) {
            boolean algumContainerParado = registry.getListenerContainers().stream()
                    .anyMatch(container -> !container.isRunning());
            builder.withDetail("sqsListener", algumContainerParado ? "parado" : "em execucao");
            saudavel &= !algumContainerParado;
        } else {
            builder.withDetail("sqsListener", "parado (shutdown)");
        }

        saudavel &= pingValkey(builder);

        return (saudavel ? builder.up() : builder.down()).build();
    }

    private boolean pingValkey(Health.Builder builder) {
        try (var connection = redisConnectionFactory.getConnection()) {
            String pong = connection.ping();
            builder.withDetail("valkey", "PONG".equalsIgnoreCase(pong) ? "conectado" : "resposta inesperada: " + pong);
            return "PONG".equalsIgnoreCase(pong);
        } catch (Exception e) {
            builder.withDetail("valkey", "falha ao conectar: " + e.getMessage());
            return false;
        }
    }

}
