package br.com.srportto.temporizaautorizacao.entrypoint.health;

import br.com.srportto.temporizaautorizacao.shared.config.TemporizacaoProperties;
import io.awspring.cloud.sqs.listener.MessageListenerContainerRegistry;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Reflete no /actuator/health o estado do consumo da fila SQS (via
 * {@link MessageListenerContainerRegistry}, mesmo padrão do autorizacaostatus-producer), a
 * conexão com o Valkey (via {@code PING}) e a contagem de consumidores do consumer group do
 * stream de expirações (sinal operacional — ver change limpar-consumidores-orfaos-stream).
 * Listener parado durante shutdown intencional não é falha; falha de PING no Valkey é — sem ele,
 * nem o agendamento nem a varredura funcionam. Divergência entre consumidores e instâncias vivas
 * NÃO derruba o health-check: o número correto acompanha o autoscaling e não é conhecido de
 * antemão pela aplicação.
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

    /** Sinal, não alarme: consumer group ainda inexistente (nenhuma expiração disparou) não é falha. */
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
