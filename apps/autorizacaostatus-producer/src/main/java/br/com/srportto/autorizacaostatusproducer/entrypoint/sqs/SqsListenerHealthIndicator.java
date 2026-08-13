package br.com.srportto.autorizacaostatusproducer.entrypoint.sqs;

import io.awspring.cloud.sqs.listener.MessageListenerContainerRegistry;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reflete no /actuator/health o estado real do consumo da fila.
 *
 * <p>Registry parado = shutdown intencional → UP. Container parado com registry ativo =
 * outage que não deve passar despercebido → DOWN.
 */
@Component("sqsListener")
public class SqsListenerHealthIndicator implements HealthIndicator {

    private final MessageListenerContainerRegistry registry;

    public SqsListenerHealthIndicator(MessageListenerContainerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Health health() {
        if (!registry.isRunning()) {
            return Health.up().withDetail("estado", "parado").build();
        }

        boolean algumContainerParado = registry.getListenerContainers().stream()
                .anyMatch(container -> !container.isRunning());

        if (algumContainerParado) {
            return Health.down()
                    .withDetail("estado", "ativo")
                    .withDetail("container", "parado")
                    .build();
        }

        return Health.up()
                .withDetail("estado", "ativo")
                .withDetail("container", "em execucao")
                .build();
    }

}
