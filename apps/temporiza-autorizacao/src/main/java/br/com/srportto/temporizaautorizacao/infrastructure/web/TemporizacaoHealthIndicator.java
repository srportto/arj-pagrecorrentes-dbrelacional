package br.com.srportto.temporizaautorizacao.infrastructure.web;

import br.com.srportto.temporizaautorizacao.infrastructure.config.TemporizacaoProperties;
import io.awspring.cloud.sqs.listener.MessageListenerContainerRegistry;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Reflete o consumo SQS, a conexão Valkey (PING) e a contagem de consumidores do stream de
 * expirações. Shutdown intencional do listener não é falha; PING falho é. Divergência entre
 * consumidores e instâncias vivas não derruba o health-check (número esperado é desconhecido).
 */
@Component("temporizacao")
public class TemporizacaoHealthIndicator implements HealthIndicator {

    private final MessageListenerContainerRegistry registry;
    private final RedisConnectionFactory redisConnectionFactory;
    private final StringRedisTemplate redisTemplate;
    private final TemporizacaoProperties properties;

    public TemporizacaoHealthIndicator(MessageListenerContainerRegistry registry,
            RedisConnectionFactory redisConnectionFactory, StringRedisTemplate redisTemplate,
            TemporizacaoProperties properties) {
        this.registry = registry;
        this.redisConnectionFactory = redisConnectionFactory;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
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
        contarConsumidores(builder);

        return (saudavel ? builder.up() : builder.down()).build();
    }

    /** Consumer group ainda inexistente não é falha, só ausência de sinal. */
    private void contarConsumidores(Health.Builder builder) {
        try {
            int total = redisTemplate.opsForStream()
                    .consumers(properties.chaveStream(), properties.grupoConsumidor())
                    .getConsumerCount();
            builder.withDetail("consumidoresStream", total);
        } catch (Exception e) {
            builder.withDetail("consumidoresStream", "grupo ainda nao existe");
        }
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
